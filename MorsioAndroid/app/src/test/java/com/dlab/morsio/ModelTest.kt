package com.dlab.morsio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ModelTest {
    private fun at(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun changingGramsRecomputes() {
        val pasta = FoodItem.fromTotals("Pasta", 80.0, kcal = 288.0, protein = 10.0, carbs = 60.0, fat = 1.2)
        assertEquals(360.0, pasta.kcal100, 0.01)
        val bigger = pasta.copy(grams = 120.0)
        assertEquals(432.0, bigger.kcal, 0.01)
        assertEquals(15.0, bigger.protein, 0.01)
        val meal = Meal(name = "Pranzo").withItems(listOf(bigger, FoodItem.fromTotals("Olio", 10.0, 90.0, 0.0, 0.0, 10.0)))
        assertEquals(522, meal.kcal)
        assertEquals(11.8, meal.fat, 0.01)
        assertNull(meal.withItems(emptyList()).items)
    }

    @Test
    fun geminiParse() {
        val text = """{"name":"Pasta al pomodoro","items":[{"name":"Pasta","grams":80,"kcal":288,"protein":10,"carbs":60,"fat":1.2},{"name":"Sugo","grams":100,"kcal":40,"protein":1,"carbs":7,"fat":1}]}"""
        val body = """{"steps":[{"type":"thought"},{"type":"model_output","content":[{"type":"text","text":${kotlinx.serialization.json.JsonPrimitive(text)}}]}]}"""
        val ai = Gemini.parse(body)
        assertEquals("Pasta al pomodoro", ai.name)
        assertEquals(2, ai.items.size)
        assertEquals(80.0, ai.items[0].grams, 0.0)
        assertEquals(288.0, ai.items[0].kcal, 0.01)
    }

    @Test
    fun groqParse() {
        val text = """{"name":"Mela","items":[{"name":"Mela","grams":150,"kcal":78,"protein":0.4,"carbs":21,"fat":0.3}]}"""
        val body = """{"choices":[{"index":0,"message":{"role":"assistant","content":${kotlinx.serialization.json.JsonPrimitive(text)}}}]}"""
        val ai = Groq.parse(body)
        assertEquals("Mela", ai.name)
        assertEquals(78.0, ai.items[0].kcal, 0.01)
    }

    @Test
    fun planItemUsesFirstAlternativeAndItsGrams() {
        val item = PlanItem("1", "Riso 70 g crudo oppure pasta 80 g", kcal = 245.0, protein = 4.9, carbs = 54.6, fat = 0.4)
        assertEquals(listOf("Riso 70 g crudo", "pasta 80 g"), item.alternatives)
        val food = item.toFoodItem()
        assertEquals("Riso 70 g crudo", food.name)
        assertEquals(70.0, food.grams, 0.0)
        assertEquals(245.0, food.kcal, 0.01)  // valori del piano, nessuna IA
        assertEquals(100.0, PlanItem("2", "Verdure abbondanti").toFoodItem().grams, 0.0)
    }

    @Test
    fun importRealDietJSON() {
        // il piano d'esempio del repository, lo stesso usato dai test iOS
        val file = File("../../esempio_piano.json")
        val plan = json.decodeFromString<DietPlan>(file.readText())
        assertEquals(7, plan.days.size)
        val monday = plan.day("Lun")!!
        assertTrue(monday.meals.map { it.name }.containsAll(listOf("Colazione", "Pranzo", "Cena")))
        assertTrue(monday.meals.sumOf { it.kcal } > 1000)
    }

    @Test
    fun slotFromTime() {
        val d = LocalDate.of(2026, 10, 12)
        assertEquals("Colazione", Slot.forTime(d.atTime(8, 0)))
        assertEquals("Spuntini", Slot.forTime(d.atTime(11, 0)))
        assertEquals("Pranzo", Slot.forTime(d.atTime(13, 30)))
        assertEquals("Spuntini", Slot.forTime(d.atTime(17, 0)))
        assertEquals("Cena", Slot.forTime(d.atTime(20, 0)))
        assertEquals("Lun", Slot.weekdayCode(d))
        assertEquals("Dom", Slot.weekdayCode(d.plusDays(6)))
    }

    @Test
    fun historyKeepsMostRecentPerNameAndSkipsPending() {
        val day = LocalDate.of(2026, 10, 12)
        val old = Meal(name = "Riso e tonno", time = at(day.atTime(13, 0)), kcal = 400)
            .withItems(listOf(FoodItem.fromTotals("Riso", 70.0, 245.0, 5.0, 55.0, 0.4)))
        val new = Meal(name = "riso e tonno", time = at(day.plusDays(1).atTime(13, 0)), kcal = 450)
            .withItems(listOf(FoodItem.fromTotals("Riso", 80.0, 280.0, 6.0, 62.0, 0.5)))
        val pending = Meal(name = "Pizza", time = at(day.atTime(20, 0)), analysis = PendingAnalysis("pizza"))
        val meals = listOf(old, new, pending)
        assertEquals(listOf(new.id), pastMeals(meals, "").map { it.id })
        assertEquals(listOf(80.0), pastItems(meals, "").map { it.grams })
        assertTrue(pastMeals(meals, "pizza").isEmpty())
        assertEquals(1, pastItems(meals, "RIS").size)
    }

    @Test
    fun remindersSkipSlotAlreadyLogged() {
        val now = LocalDate.of(2026, 10, 12).atTime(9, 0)
        assertEquals(now.withHour(13), Reminders.nextFire(now, 13 * 60))
        assertEquals(now.plusDays(1).withHour(8).withMinute(30), Reminders.nextFire(now, 8 * 60 + 30))
        val lunch = Meal(name = "Pasta", time = at(now), slot = "Pranzo")
        assertFalse(Reminders.shouldNotify(listOf(lunch), "Pranzo", now.toLocalDate()))
        assertTrue(Reminders.shouldNotify(listOf(lunch), "Cena", now.toLocalDate()))
        assertTrue(Reminders.shouldNotify(listOf(lunch), "Pranzo", now.toLocalDate().plusDays(1)))
    }

    /** Stesso server dell'app iOS: avvialo e passa MORSIO_SERVER, MORSIO_KEY, MORSIO_GUEST (vedi run_tests.sh). */
    @Test
    fun pastoNuovoNelGiornoScelto() {
        val later = LocalDate.now().plusDays(3)
        val at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(mealTime(later)), ZoneId.systemDefault())
        assertEquals(later.atTime(12, 0), at)  // giorno futuro: mezzogiorno di quel giorno
        assertEquals(LocalDate.now(), Meal(name = "", time = mealTime(LocalDate.now())).day)
    }

    @Test
    fun uploadAndDeleteOnServer() = runBlocking {
        val server = System.getenv("MORSIO_SERVER")
        assumeTrue(server != null)
        val key = System.getenv("MORSIO_KEY")!!
        val guest = "$server/g/${System.getenv("MORSIO_GUEST")}/"
        fun page() = (URL(guest).openConnection() as HttpURLConnection).inputStream.bufferedReader().readText()

        val meal = Meal(name = "Pane & olio + sale", kcal = 321)
        Uploader.upload(server!!, key, meal, ByteArray(2000) { 0x42 })
        assertTrue(page().contains("Pane &amp; olio + sale"))
        assertTrue(page().contains("${meal.id}.jpg"))
        Uploader.delete(server, key, meal.id)
        assertFalse(page().contains(meal.id))
    }
}
