package com.dlab.morsio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AppError(message: String) : Exception(message)

data class AIMeal(val name: String, val items: List<FoodItem>)

private class Response(val status: Int, val body: String)

/** Chiamata HTTP bloccante, da usare su Dispatchers.IO. */
private fun http(url: String, method: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null, timeout: Int = 60_000): Response {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = timeout
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        val status = conn.responseCode
        val text = (if (status < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return Response(status, text)
    } finally {
        conn.disconnect()
    }
}

object Gemini {
    private const val PROMPT = "Sei un nutrizionista. Analizza il pasto nella foto e/o nella descrizione. " +
        "Elenca ogni alimento riconoscibile (es. pasta, sugo, olio, formaggio) con il peso stimato in grammi " +
        "e calorie e macronutrienti riferiti a quel peso. Dai al pasto un nome breve in italiano; " +
        "anche i nomi degli alimenti in italiano."

    /** Per i provider senza schema: il formato va scritto nel prompt. */
    const val JSON_PROMPT = PROMPT + " Rispondi solo con un oggetto JSON così: " +
        "{\"name\": \"...\", \"items\": [{\"name\": \"...\", \"grams\": 0, \"kcal\": 0, \"protein\": 0, \"carbs\": 0, \"fat\": 0}]}"

    private val schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("name") { put("type", "string") }
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string") }
                        for (k in listOf("grams", "kcal", "protein", "carbs", "fat")) putJsonObject(k) { put("type", "number") }
                    }
                    putJsonArray("required") { listOf("name", "grams", "kcal", "protein", "carbs", "fat").forEach { add(it) } }
                }
            }
        }
        putJsonArray("required") { add("name"); add("items") }
    }

    /** Se il modello scelto è sovraccarico (503) o fuori quota (429) si passa a Flash-Lite. */
    private const val FALLBACK = "gemini-flash-lite-latest"

    suspend fun analyze(key: String, model: String, photo: ByteArray?, note: String): AIMeal = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw AppError("Inserisci la chiave Gemini nelle impostazioni")
        val chosen = model.ifBlank { "gemini-flash-latest" }
        var lastError = AppError("Gemini non disponibile")
        for (m in if (chosen == FALLBACK) listOf(chosen) else listOf(chosen, FALLBACK)) {
            val body = buildJsonObject {
                put("model", m)
                putJsonArray("input") {
                    addJsonObject {
                        put("type", "text")
                        put("text", PROMPT + if (note.isEmpty()) "" else "\nDescrizione: $note")
                    }
                    if (photo != null) addJsonObject {
                        put("type", "image")
                        put("mime_type", "image/jpeg")
                        put("data", Base64.encodeToString(photo, Base64.NO_WRAP))
                    }
                }
                putJsonObject("response_format") {
                    put("type", "text")
                    put("mime_type", "application/json")
                    put("schema", schema)
                }
            }
            val r = http("https://generativelanguage.googleapis.com/v1beta/interactions", "POST", mapOf(
                "x-goog-api-key" to key,
                "Content-Type" to "application/json",
                "Api-Revision" to "2026-05-20",
            ), body.toString().toByteArray())
            if (r.status == 200) return@withContext parse(r.body)
            val message = runCatching {
                json.parseToJsonElement(r.body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.contentOrNull
            }.getOrNull()
            lastError = AppError("Gemini (${r.status}): ${message ?: "errore sconosciuto"}")
            if (r.status != 503 && r.status != 429) throw lastError
        }
        throw lastError
    }

    @Serializable private data class Content(val type: String, val text: String? = null)
    @Serializable private data class Step(val type: String, val content: List<Content>? = null)
    @Serializable private data class Steps(val steps: List<Step>)
    @Serializable private data class RawItem(val name: String, val grams: Double, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)
    @Serializable private data class Raw(val name: String, val items: List<RawItem>)

    /** Il testo JSON sta nello step "model_output" della risposta. */
    fun parse(body: String): AIMeal {
        val text = json.decodeFromString<Steps>(body).steps.firstOrNull { it.type == "model_output" }
            ?.content?.firstOrNull { it.type == "text" }?.text
            ?: throw AppError("Gemini non ha restituito una stima")
        return mealFromJson(text)
    }

    /** Il JSON del pasto, uguale per tutti i provider. */
    fun mealFromJson(text: String): AIMeal {
        val raw = json.decodeFromString<Raw>(text)
        if (raw.items.isEmpty()) throw AppError("L'IA non ha riconosciuto alimenti")
        return AIMeal(raw.name, raw.items.map { FoodItem.fromTotals(it.name, it.grams, it.kcal, it.protein, it.carbs, it.fat) })
    }
}

/** Groq, API compatibile OpenAI. Piano gratuito su console.groq.com. */
object Groq {
    const val DEFAULT_MODEL = "qwen/qwen3.8-27b"

    suspend fun analyze(key: String, model: String, photo: ByteArray?, note: String): AIMeal = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model.ifBlank { DEFAULT_MODEL })
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", Gemini.JSON_PROMPT + if (note.isEmpty()) "" else "\nDescrizione: $note")
                        }
                        if (photo != null) addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:image/jpeg;base64," + Base64.encodeToString(photo, Base64.NO_WRAP)) }
                        }
                    }
                }
            }
            putJsonObject("response_format") { put("type", "json_object") }
        }
        val r = http("https://api.groq.com/openai/v1/chat/completions", "POST", mapOf(
            "Authorization" to "Bearer $key",
            "Content-Type" to "application/json",
        ), body.toString().toByteArray())
        if (r.status != 200) {
            val message = runCatching {
                json.parseToJsonElement(r.body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.contentOrNull
            }.getOrNull()
            throw AppError("Groq (${r.status}): ${message ?: "errore sconosciuto"}")
        }
        parse(r.body)
    }

    @Serializable private data class Message(val content: String? = null)
    @Serializable private data class Choice(val message: Message)
    @Serializable private data class Completion(val choices: List<Choice>)

    fun parse(body: String): AIMeal {
        val text = json.decodeFromString<Completion>(body).choices.firstOrNull()?.message?.content
            ?: throw AppError("Groq non ha restituito una stima")
        return Gemini.mealFromJson(text)
    }
}

/** Con la chiave Groq si prova prima Groq (veloce, raramente saturo), poi Gemini. */
suspend fun analyzeMeal(storage: Storage, photo: ByteArray?, note: String): AIMeal {
    val gemini = suspend { Gemini.analyze(storage.string("geminiKey"), storage.string("geminiModel"), photo, note) }
    val groqKey = storage.string("groqKey")
    if (groqKey.isBlank()) return gemini()
    return try {
        Groq.analyze(groqKey, storage.string("groqModel"), photo, note)
    } catch (groqError: Exception) {
        try { gemini() } catch (e: Exception) { throw AppError("${groqError.message}\n${e.message}") }
    }
}

object OpenFoodFacts {
    /** Il prodotto come alimento da 100 g; null se non è nel database. */
    suspend fun product(barcode: String): FoodItem? = withContext(Dispatchers.IO) {
        if (barcode.isEmpty() || !barcode.all { it in '0'..'9' }) throw AppError("Codice a barre non valido")
        val r = http("https://world.openfoodfacts.org/api/v2/product/$barcode?fields=product_name,nutriments", "GET",
            mapOf("User-Agent" to "Morsio/1.0 (uso personale)"), timeout = 20_000)
        val root = runCatching { json.parseToJsonElement(r.body).jsonObject }.getOrElse { throw AppError("Risposta di Open Food Facts non valida") }
        val product = root["product"] as? JsonObject ?: return@withContext null
        val n = product["nutriments"] as? JsonObject
        fun value(k: String) = n?.get(k)?.jsonPrimitive?.doubleOrNull ?: 0.0
        FoodItem(name = product["product_name"]?.jsonPrimitive?.contentOrNull ?: "Prodotto", grams = 100.0,
            kcal100 = value("energy-kcal_100g"), protein100 = value("proteins_100g"),
            carbs100 = value("carbohydrates_100g"), fat100 = value("fat_100g"), barcode = barcode)
    }
}

/** Stesso protocollo del server usato dall'app iOS. */
object Uploader {
    private fun url(base: String, query: List<Pair<String, String>>): String {
        val q = query.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }  // lo spazio diventa +, il + diventa %2B
        return base.trim().trimEnd('/') + "/upload?" + q
    }

    suspend fun upload(base: String, key: String, meal: Meal, photo: ByteArray?) = withContext(Dispatchers.IO) {
        if (base.isBlank()) throw AppError("Server non configurato")
        val r = http(url(base, listOf("id" to meal.id, "name" to meal.name, "kcal" to meal.kcal.toString(), "ts" to (meal.time / 1000).toString())),
            "POST", mapOf("X-Key" to key, "Content-Type" to "image/jpeg"), photo ?: ByteArray(0))
        if (r.status != 201) throw AppError("Upload fallito (${r.status})")
    }

    suspend fun delete(base: String, key: String, id: String) = withContext(Dispatchers.IO) {
        if (base.isBlank()) throw AppError("Server non configurato")
        val r = http(url(base, listOf("id" to id)), "DELETE", mapOf("X-Key" to key))
        if (r.status != 204) throw AppError("Cancellazione fallita (${r.status})")
    }
}

/** Carica un'immagine ridimensionata (lato lungo ≤ maxSide) e la comprime in JPEG. */
fun Context.jpeg(uri: Uri, maxSide: Int, quality: Int): ByteArray {
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
        val scale = min(1.0, maxSide.toDouble() / max(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}

fun ByteArray.toBitmap(): Bitmap? = BitmapFactory.decodeByteArray(this, 0, size)
