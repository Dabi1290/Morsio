package com.dlab.morsio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/** Pasti, piano e foto nella memoria dell'app; impostazioni nelle SharedPreferences. */
class Storage(context: Context) {
    private val dir: File = context.filesDir
    val prefs: SharedPreferences = context.getSharedPreferences("morsio", Context.MODE_PRIVATE)

    fun photoFile(id: String) = File(dir, "$id.jpg")
    /** Foto a risoluzione più alta usata solo dall'IA, cancellata a fine analisi. */
    fun aiPhotoFile(id: String) = File(dir, "$id-ai.jpg")

    fun loadMeals(): List<Meal> = runCatching { json.decodeFromString<List<Meal>>(File(dir, "meals.json").readText()) }.getOrDefault(emptyList())
    fun saveMeals(meals: List<Meal>) = writeAtomic("meals.json", json.encodeToString(meals))

    fun loadPlan(): DietPlan? = runCatching { json.decodeFromString<DietPlan>(File(dir, "plan.json").readText()) }.getOrNull()
    fun savePlan(plan: DietPlan?) {
        if (plan == null) File(dir, "plan.json").delete() else writeAtomic("plan.json", json.encodeToString(plan))
    }

    /** Prodotti col barcode inseriti o corretti a mano (valori per 100 g): hanno la precedenza su Open Food Facts. */
    var products: Map<String, FoodItem>
        get() = runCatching { json.decodeFromString<Map<String, FoodItem>>(prefs.getString("products", null)!!) }.getOrDefault(emptyMap())
        set(value) = prefs.edit().putString("products", json.encodeToString(value)).apply()

    var pendingDeletes: List<String>
        get() = prefs.getStringSet("pendingDeletes", emptySet())!!.toList()
        set(value) = prefs.edit().putStringSet("pendingDeletes", value.toSet()).apply()

    val goal get() = prefs.getInt("goal", 2000)
    fun string(key: String) = prefs.getString(key, "").orEmpty()

    private fun writeAtomic(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }
}
