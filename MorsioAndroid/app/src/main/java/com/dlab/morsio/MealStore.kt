package com.dlab.morsio

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class MorsioApp : Application() {
    val store by lazy { MealStore(this) }
}

val Context.store get() = (applicationContext as MorsioApp).store

/** Stato dell'app: pasti, piano, analisi IA in corso e sincronizzazione col server. */
class MealStore(private val context: Context) {
    val storage = Storage(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _meals = MutableStateFlow(storage.loadMeals())
    val meals: StateFlow<List<Meal>> = _meals
    private val _plan = MutableStateFlow(storage.loadPlan())
    val plan: StateFlow<DietPlan?> = _plan
    private val analyzing = mutableSetOf<String>()
    private val syncLock = Mutex()

    private fun update(change: (List<Meal>) -> List<Meal>) {
        _meals.value = change(_meals.value)
        storage.saveMeals(_meals.value)
        MorsioWidget.refresh(context)
    }

    /** Inserisce o aggiorna; [photo] (già compressa) sostituisce quella salvata. */
    fun save(meal: Meal, photo: ByteArray? = null) {
        val m = meal.copy(synced = false)
        photo?.let { storage.photoFile(m.id).writeBytes(it) }
        if (m.analysis == null) storage.aiPhotoFile(m.id).delete()
        if (!m.hasPhoto) storage.photoFile(m.id).delete()
        // prodotti col barcode: nome e valori si ricordano per la prossima scansione
        m.items?.filter { it.barcode != null && it.name.isNotEmpty() }?.takeIf { it.isNotEmpty() }?.let { coded ->
            storage.products = storage.products + coded.associateBy { it.barcode!! }
        }
        update { list -> if (list.any { it.id == m.id }) list.map { if (it.id == m.id) m else it } else list + m }
        sync()
    }

    fun delete(meal: Meal) {
        storage.photoFile(meal.id).delete()
        storage.aiPhotoFile(meal.id).delete()
        update { list -> list.filter { it.id != meal.id } }
        storage.pendingDeletes = storage.pendingDeletes + meal.id
        sync()
    }

    // Analisi IA in background: il pasto si salva subito e si completa quando arriva la risposta.

    fun analyzeInBackground(meal: Meal, photo: ByteArray?, aiPhoto: ByteArray?, note: String) {
        aiPhoto?.let { storage.aiPhotoFile(meal.id).writeBytes(it) }
        save(meal.copy(analysis = PendingAnalysis(note)), photo)
        runAnalysis(meal.id)
    }

    fun retryAnalysis(meal: Meal) {
        save(meal.copy(analysis = PendingAnalysis(meal.analysis?.note.orEmpty())))
        runAnalysis(meal.id)
    }

    /** All'avvio riprende le analisi interrotte (app chiusa a metà). */
    fun resumeAnalyses() {
        _meals.value.filter { it.analysis != null && it.analysis.error == null }.forEach { runAnalysis(it.id) }
    }

    private fun runAnalysis(id: String) {
        if (!analyzing.add(id)) return
        scope.launch {
            val note = _meals.value.firstOrNull { it.id == id }?.analysis?.note.orEmpty()
            val aiFile = storage.aiPhotoFile(id)
            val photo = aiFile.takeIf { it.exists() }?.readBytes()
            val result = runCatching {
                analyzeMeal(storage, photo, note)
            }
            analyzing.remove(id)
            // pasto cancellato o modificato a mano nel frattempo: vince l'utente
            val current = _meals.value.firstOrNull { it.id == id }?.takeIf { it.analysis != null } ?: return@launch
            result.onSuccess { ai ->
                aiFile.delete()
                update { list -> list.map { if (it.id == id) current.copy(name = ai.name, analysis = null, synced = false).withItems(ai.items) else it } }
                sync()
            }.onFailure { e ->
                update { list -> list.map { if (it.id == id) current.copy(analysis = current.analysis!!.copy(error = e.message ?: "Errore")) else it } }
            }
        }
    }

    // Piano

    fun importPlan(text: String) {
        val p = json.decodeFromString<DietPlan>(text)
        storage.savePlan(p)
        _plan.value = p
    }

    fun removePlan() {
        storage.savePlan(null)
        _plan.value = null
    }

    /** Fasce del piano seguite in un giorno (pasti segnati "segue il piano"). */
    fun adherence(day: LocalDate): Pair<Int, Int>? {
        val planned = _plan.value?.day(day)?.meals?.map { it.name } ?: return null
        val followed = _meals.value.on(day).filter { it.fromPlan }.map { it.resolvedSlot }.toSet()
        return planned.count { it in followed } to planned.size
    }

    fun weeklyAdherence(): Pair<Int, Int> {
        val today = LocalDate.now()
        return (0L until 7L).mapNotNull { adherence(today.minusDays(it)) }
            .fold(0 to 0) { acc, (d, t) -> (acc.first + d) to (acc.second + t) }
    }

    // Sincronizzazione: carica i pasti degli ultimi 7 giorni non ancora inviati, poi le cancellazioni.

    fun sync() {
        scope.launch { syncPass() }
    }

    suspend fun syncPass() = syncLock.withLock {
        val base = storage.string("serverURL")
        val key = storage.string("uploadKey")
        if (base.isBlank()) return@withLock
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        for (meal in _meals.value.filter { !it.synced && it.analysis == null && it.time > weekAgo }) {
            val photo = if (meal.hasPhoto) storage.photoFile(meal.id).takeIf { it.exists() }?.readBytes() else null
            if (runCatching { Uploader.upload(base, key, meal, photo) }.isFailure) return@withLock  // offline: si riprova dopo
            // segna inviato solo se nel frattempo non è cambiato
            if (_meals.value.firstOrNull { it.id == meal.id } == meal) {
                update { list -> list.map { if (it.id == meal.id) it.copy(synced = true) else it } }
            }
        }
        for (id in storage.pendingDeletes) {
            if (runCatching { Uploader.delete(base, key, id) }.isFailure) return@withLock
            storage.pendingDeletes = storage.pendingDeletes - id
        }
    }
}
