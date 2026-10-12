import Foundation
import UserNotifications

/// Promemoria per inserire i pasti, uno per fascia, con orario scelto nelle impostazioni.
@MainActor
final class Reminders: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    static let shared = Reminders()
    /// Toccando una notifica l'app apre l'inserimento del pasto.
    @Published var tapped = false

    static let defaultTimes = ["Colazione": 8 * 60, "Spuntini": 16 * 60 + 30, "Pranzo": 13 * 60, "Cena": 20 * 60]
    static func enabledKey(_ slot: String) -> String { "reminder.\(slot)" }
    static func timeKey(_ slot: String) -> String { "reminderTime.\(slot)" }

    static func minutes(_ slot: String) -> Int {
        SharedStorage.defaults.object(forKey: timeKey(slot)) as? Int ?? defaultTimes[slot] ?? 12 * 60
    }

    private static let days = 10

    /// Notifiche singole per i prossimi giorni, così si salta una fascia già inserita oggi.
    /// ponytail: se l'app non si apre per `days` giorni i promemoria finiscono; ogni apertura li riprogramma.
    static func schedule(_ meals: [Meal]) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        let cal = Calendar.current
        for (id, slot, fire) in fireDates(meals, now: .now) {
            let content = UNMutableNotificationContent()
            content.title = slot
            content.body = "Hai mangiato? Aggiungi il pasto a Morsio."
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: cal.dateComponents([.year, .month, .day, .hour, .minute], from: fire), repeats: false)
            center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        }
    }

    static func fireDates(_ meals: [Meal], now: Date) -> [(id: String, slot: String, date: Date)] {
        let cal = Calendar.current
        let slots = Slot.all.filter { SharedStorage.defaults.bool(forKey: enabledKey($0)) }
        let loggedToday = Set(meals.on(now).map(\.resolvedSlot))
        return (0..<days).flatMap { offset in
            slots.compactMap { slot -> (String, String, Date)? in
                let m = minutes(slot)
                guard let day = cal.date(byAdding: .day, value: offset, to: cal.startOfDay(for: now)),
                      let fire = cal.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: day), fire > now,
                      !(offset == 0 && loggedToday.contains(slot)) else { return nil }
                return ("\(slot)-\(offset)", slot, fire)
            }
        }
    }

    /// Chiede il permesso (solo la prima volta mostra l'avviso); false se l'utente l'ha negato.
    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        await MainActor.run { tapped = true }
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}
