package com.dlab.morsio

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt

/** Id in maiuscolo: il server accetta solo UUID maiuscoli (come quelli di iOS). */
fun newId(): String = UUID.randomUUID().toString().uppercase()

@Serializable
data class Meal(
    val id: String = newId(),
    /** Millisecondi dal 1970. */
    val time: Long = System.currentTimeMillis(),
    val name: String,
    val kcal: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val hasPhoto: Boolean = false,
    val synced: Boolean = false,
    /** Presente finché l'IA non ha finito (o se ha fallito). */
    val analysis: PendingAnalysis? = null,
    /** Alimenti con il loro peso: se presenti, i totali del pasto sono la loro somma. */
    val items: List<FoodItem>? = null,
    /** Fascia del piano; se manca si deduce dall'orario. */
    val slot: String? = null,
    /** Pasto che segue il piano: conta per l'aderenza. */
    val fromPlan: Boolean = false,
) {
    val dateTime: LocalDateTime get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
    val day: LocalDate get() = dateTime.toLocalDate()
    val resolvedSlot: String get() = slot ?: Slot.forTime(dateTime)

    fun withItems(items: List<FoodItem>): Meal =
        if (items.isEmpty()) copy(items = null)
        else copy(
            items = items,
            kcal = items.sumOf { it.kcal }.roundToInt(),
            protein = items.sumOf { it.protein },
            carbs = items.sumOf { it.carbs },
            fat = items.sumOf { it.fat },
        )
}

@Serializable
data class PendingAnalysis(val note: String, val error: String? = null)

/** Un alimento del pasto. I valori sono per 100 g: cambiando i grammi si ricalcola tutto. */
@Serializable
data class FoodItem(
    val id: String = newId(),
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    /** Codice a barre: salvando il pasto, nome e valori si ricordano per quel codice. */
    val barcode: String? = null,
) {
    val kcal get() = kcal100 * grams / 100
    val protein get() = protein100 * grams / 100
    val carbs get() = carbs100 * grams / 100
    val fat get() = fat100 * grams / 100

    companion object {
        /** Da valori totali riferiti a [grams] grammi (come li stima l'IA). */
        fun fromTotals(name: String, grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double): FoodItem {
            val g = if (grams > 0) grams else 100.0
            return FoodItem(name = name, grams = g, kcal100 = kcal * 100 / g, protein100 = protein * 100 / g,
                carbs100 = carbs * 100 / g, fat100 = fat * 100 / g)
        }

        fun empty(barcode: String? = null) =
            FoodItem(name = "", grams = 100.0, kcal100 = 0.0, protein100 = 0.0, carbs100 = 0.0, fat100 = 0.0, barcode = barcode)
    }
}

fun List<Meal>.on(day: LocalDate) = filter { it.day == day }
val List<Meal>.kcal get() = sumOf { it.kcal }

// Piano settimanale: stesso formato JSON di Diet1 e dell'app iOS, così si importa lo stesso file.

@Serializable
data class DietPlan(val id: String, val name: String, val days: List<PlanDay>) {
    fun day(weekday: String) = days.firstOrNull { it.weekday == weekday }
    fun day(date: LocalDate) = day(Slot.weekdayCode(date))
}

@Serializable
data class PlanDay(val id: String, val weekday: String, val meals: List<PlanMeal>) {
    fun meal(slot: String) = meals.firstOrNull { it.name == slot }
}

@Serializable
data class PlanMeal(val id: String, val name: String, val items: List<PlanItem>) {
    val kcal get() = items.sumOf { it.kcal }
    val protein get() = items.sumOf { it.protein }
    val carbs get() = items.sumOf { it.carbs }
    val fat get() = items.sumOf { it.fat }
}

@Serializable
data class PlanItem(
    val id: String,
    val name: String,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    /** "Riso 80 g oppure pasta 80 g" → ["Riso 80 g", "pasta 80 g"] */
    val alternatives: List<String>
        get() = name.split(" oppure ").flatMap { it.split("Oppure ") }.map { it.trim() }.filter { it.isNotEmpty() }

    /** Alimento per il diario: prima alternativa, grammi letti dal testo, valori del piano (nessuna IA). */
    fun toFoodItem(): FoodItem {
        val first = alternatives.firstOrNull() ?: name
        val grams = Regex("""(\d+)\s*g\b""").find(first)?.groupValues?.get(1)?.toDoubleOrNull() ?: 100.0
        return FoodItem.fromTotals(first, grams, kcal, protein, carbs, fat)
    }
}

/** Fasce dei pasti, con gli stessi nomi del piano. */
object Slot {
    val all = listOf("Colazione", "Spuntini", "Pranzo", "Cena")
    val weekdays = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

    /** Fascia proposta in base all'orario. */
    fun forTime(t: LocalDateTime): String {
        val m = t.hour * 60 + t.minute
        return when {
            m < 10 * 60 + 30 -> "Colazione"
            m < 12 * 60 -> "Spuntini"
            m < 15 * 60 -> "Pranzo"
            m < 18 * 60 + 30 -> "Spuntini"
            else -> "Cena"
        }
    }

    fun weekdayCode(date: LocalDate) = weekdays[date.dayOfWeek.value - DayOfWeek.MONDAY.value]
}

/** Orario di un pasto nuovo nel giorno scelto: oggi l'ora attuale, negli altri giorni mezzogiorno. */
fun mealTime(day: LocalDate): Long {
    val now = LocalDateTime.now()
    val at = if (day == now.toLocalDate()) now else day.atTime(12, 0)
    return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** Pasto del piano pronto da salvare nel giorno scelto. */
fun PlanMeal.toMeal(day: LocalDate): Meal =
    Meal(name = name, time = mealTime(day), slot = name, fromPlan = true).withItems(items.map { it.toFoodItem() })

/** Pasti già analizzati, il più recente per nome. */
fun pastMeals(meals: List<Meal>, query: String): List<Meal> {
    val seen = HashSet<String>()
    return meals.sortedByDescending { it.time }.filter {
        it.analysis == null && it.kcal > 0 && it.name.contains(query, ignoreCase = true) && seen.add(it.name.lowercase())
    }
}

/** Alimenti dei pasti già analizzati, il più recente per nome. */
fun pastItems(meals: List<Meal>, query: String): List<FoodItem> {
    val seen = HashSet<String>()
    return meals.sortedByDescending { it.time }.filter { it.analysis == null }.flatMap { it.items.orEmpty() }.filter {
        it.name.isNotEmpty() && it.name.contains(query, ignoreCase = true) && seen.add(it.name.lowercase())
    }
}
