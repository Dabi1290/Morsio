package com.dlab.morsio

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Promemoria per inserire i pasti, uno per fascia, con orario scelto nelle impostazioni.
 * Ogni allarme, quando scatta, controlla se il pasto è già nel diario e poi si riprogramma per il giorno dopo.
 */
object Reminders {
    const val ACTION_ADD = "com.dlab.morsio.ADD"
    private const val CHANNEL = "promemoria"
    val defaultTimes = mapOf("Colazione" to 8 * 60, "Spuntini" to 16 * 60 + 30, "Pranzo" to 13 * 60, "Cena" to 20 * 60)

    fun enabledKey(slot: String) = "reminder.$slot"
    fun timeKey(slot: String) = "reminderTime.$slot"
    fun minutes(storage: Storage, slot: String) = storage.prefs.getInt(timeKey(slot), defaultTimes[slot] ?: 12 * 60)

    /** Prossimo orario utile: oggi se non è ancora passato, altrimenti domani. */
    fun nextFire(now: LocalDateTime, minutes: Int): LocalDateTime {
        val today = now.toLocalDate().atTime(minutes / 60, minutes % 60)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun shouldNotify(meals: List<Meal>, slot: String, day: LocalDate) = meals.on(day).none { it.resolvedSlot == slot }

    fun schedule(context: Context) {
        val storage = context.store.storage
        val alarms = context.getSystemService(AlarmManager::class.java)
        for ((i, slot) in Slot.all.withIndex()) {
            val intent = PendingIntent.getBroadcast(context, i,
                Intent(context, ReminderReceiver::class.java).putExtra("slot", slot),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (storage.prefs.getBoolean(enabledKey(slot), false)) {
                val at = nextFire(LocalDateTime.now(), minutes(storage, slot)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                // ponytail: allarme inesatto (può tardare qualche minuto), niente permesso per gli allarmi esatti
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.cancel(intent)
            }
        }
    }

    fun notify(context: Context, slot: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria pasti", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setAction(ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(slot)
            .setContentText("Hai mangiato? Aggiungi il pasto a Morsio.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(slot.hashCode(), notification) }  // permesso negato: niente notifica
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) return Reminders.schedule(context)
        val slot = intent.getStringExtra("slot") ?: return
        if (Reminders.shouldNotify(context.store.meals.value, slot, LocalDate.now())) Reminders.notify(context, slot)
        Reminders.schedule(context)
    }
}
