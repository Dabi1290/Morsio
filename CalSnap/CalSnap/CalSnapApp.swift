import SwiftUI
import Combine
import WidgetKit
import UserNotifications

@main
struct CalSnapApp: App {
    @StateObject var store = MealStore()
    @Environment(\.scenePhase) var scenePhase
    @AppStorage("theme", store: SharedStorage.defaults) var theme = "system"
    @State private var tab = 0
    @State private var adding = false
    @StateObject private var reminders = Reminders.shared

    init() {
        UNUserNotificationCenter.current().delegate = Reminders.shared
    }

    var body: some Scene {
        WindowGroup {
            TabView(selection: $tab) {
                Tab("Diario", systemImage: "fork.knife", value: 0) { ContentView(adding: $adding) }
                Tab("Piano", systemImage: "list.bullet.clipboard", value: 1) { PlanView() }
            }
            .tabBarMinimizeBehavior(.onScrollDown)
            .tabViewBottomAccessory {
                Button { addMeal() } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "camera.fill")
                        Text("Aggiungi pasto")
                    }
                    .font(.headline).foregroundStyle(Palette.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
            .onOpenURL { if $0.host == "add" { addMeal() } }
            .onChange(of: reminders.tapped) {
                if reminders.tapped { addMeal(); reminders.tapped = false }
            }
            .environmentObject(store)
            .environment(\.locale, .it)
            .tint(Palette.accent)
            .preferredColorScheme(theme == "light" ? .light : theme == "dark" ? .dark : nil)
        }
        .onChange(of: scenePhase) {
            if scenePhase == .active {
                store.resumeAnalyses()
                Reminders.schedule(store.meals)
                Task { await store.sync() }
            }
        }
    }

    /// Dal widget o dal pulsante: torna al diario e apre l'inserimento.
    private func addMeal() {
        tab = 0
        adding = true
    }
}

@MainActor
final class MealStore: ObservableObject {
    @Published var meals = SharedStorage.loadMeals()
    @Published var plan = SharedStorage.loadPlan()
    private var syncing = false
    private var syncAgain = false
    /// Analisi IA in corso (id dei pasti).
    private(set) var analyzing: Set<UUID> = []

    /// Aggiunge o aggiorna (stesso id). Il pasto torna "da inviare" così il server riceve la nuova versione.
    func save(_ meal: Meal, photo: Data?) {
        if let photo { try? photo.write(to: SharedStorage.photoURL(meal.id)) }
        var meal = meal
        meal.synced = false
        if meal.analysis == nil { try? FileManager.default.removeItem(at: SharedStorage.aiPhotoURL(meal.id)) }
        if !meal.hasPhoto { try? FileManager.default.removeItem(at: SharedStorage.photoURL(meal.id)) }
        if let i = meals.firstIndex(where: { $0.id == meal.id }) {
            meals[i] = meal
        } else {
            meals.append(meal)
        }
        persist()
    }

    func delete(_ meal: Meal) {
        try? FileManager.default.removeItem(at: SharedStorage.photoURL(meal.id))
        try? FileManager.default.removeItem(at: SharedStorage.aiPhotoURL(meal.id))
        meals.removeAll { $0.id == meal.id }
        // anche se non ancora "synced": l'upload potrebbe essere in volo (DELETE è idempotente)
        pendingDeletes.append(meal.id.uuidString)
        persist()
    }

    /// Salva subito il pasto e lascia lavorare l'IA in background: la scheda si aggiorna quando arriva la stima.
    func analyzeInBackground(_ meal: Meal, photo: Data?, aiPhoto: Data?, note: String) {
        var meal = meal
        meal.analysis = PendingAnalysis(note: note)
        if let aiPhoto { try? aiPhoto.write(to: SharedStorage.aiPhotoURL(meal.id)) }
        save(meal, photo: photo)
        runAnalysis(meal.id)
    }

    func retryAnalysis(_ meal: Meal) {
        guard let i = meals.firstIndex(where: { $0.id == meal.id }) else { return }
        meals[i].analysis?.error = nil
        persist()
        runAnalysis(meal.id)
    }

    /// Riprende le analisi interrotte (es. app chiusa mentre l'IA lavorava).
    func resumeAnalyses() {
        for meal in meals where meal.analysis != nil && meal.analysis?.error == nil {
            runAnalysis(meal.id)
        }
    }

    private func runAnalysis(_ id: UUID) {
        guard !analyzing.contains(id), let note = meals.first(where: { $0.id == id })?.analysis?.note else { return }
        analyzing.insert(id)
        Task {
            // qualche secondo extra se l'utente esce dall'app durante l'analisi
            var bg = UIBackgroundTaskIdentifier.invalid
            bg = UIApplication.shared.beginBackgroundTask { UIApplication.shared.endBackgroundTask(bg) }
            defer {
                analyzing.remove(id)
                UIApplication.shared.endBackgroundTask(bg)
            }
            let url = SharedStorage.aiPhotoURL(id)
            let photo = (try? Data(contentsOf: url)) ?? (try? Data(contentsOf: SharedStorage.photoURL(id)))
            let result: Result<AIMeal, Error>
            do { result = .success(try await Gemini.analyze(photo: photo, note: note)) } catch { result = .failure(error) }

            // se nel frattempo è stato eliminato o modificato a mano, il risultato si scarta
            guard let i = meals.firstIndex(where: { $0.id == id }), meals[i].analysis != nil else { return }
            switch result {
            case .success(let ai):
                meals[i].name = ai.name
                meals[i].setItems(ai.items)
                meals[i].analysis = nil
                meals[i].synced = false
                try? FileManager.default.removeItem(at: url)
                persist()
                await sync()
            case .failure(let error):
                meals[i].analysis?.error = error.localizedDescription
                persist()
            }
        }
    }

    // MARK: - Piano

    func importPlan(_ data: Data) throws {
        let plan = try JSONDecoder().decode(DietPlan.self, from: data)
        guard !plan.days.isEmpty else { throw AppError("Il file non contiene giorni") }
        self.plan = plan
        SharedStorage.savePlan(plan)
    }

    func removePlan() {
        plan = nil
        SharedStorage.savePlan(nil)
    }

    /// Fasce del piano seguite in un giorno (nil se quel giorno non ha piano).
    func adherence(on day: Date) -> (done: Int, total: Int)? {
        guard let planDay = plan?.day(for: day), !planDay.meals.isEmpty else { return nil }
        let logged = meals.on(day).filter { $0.fromPlan == true }
        let done = planDay.meals.filter { pm in logged.contains { $0.resolvedSlot == pm.name } }.count
        return (done, planDay.meals.count)
    }

    /// Aderenza sugli ultimi 7 giorni (oggi compreso).
    func weeklyAdherence() -> (done: Int, total: Int) {
        let today = Calendar.current.startOfDay(for: .now)
        return (0..<7).compactMap { adherence(on: Calendar.current.date(byAdding: .day, value: -$0, to: today)!) }
            .reduce((0, 0)) { ($0.0 + $1.done, $0.1 + $1.total) }
    }

    /// Cancellazioni da propagare al server (sopravvivono al riavvio dell'app).
    private var pendingDeletes: [String] {
        get { SharedStorage.defaults.stringArray(forKey: "pendingDeletes") ?? [] }
        set { SharedStorage.defaults.set(newValue, forKey: "pendingDeletes") }
    }

    /// Allinea il server: carica i pasti non ancora inviati (ultimi 7 giorni) e propaga le cancellazioni.
    /// Chiamato all'apertura dell'app e dopo ogni modifica; quello che fallisce viene ritentato al giro dopo.
    func sync() async {
        guard !syncing else { syncAgain = true; return }
        syncing = true
        defer { syncing = false }
        repeat {
            syncAgain = false
            await syncPass()
        } while syncAgain
    }

    private func syncPass() async {
        let weekAgo = Date().addingTimeInterval(-7 * 86400)
        // i pasti ancora in analisi non vanno all'ospite: partono quando l'IA ha finito
        for meal in meals where !meal.synced && meal.analysis == nil && meal.date > weekAgo {
            guard (try? await Uploader.upload(meal)) != nil else { return }
            // se nel frattempo è stato modificato, resta da inviare
            if let i = meals.firstIndex(where: { $0.id == meal.id }), meals[i] == meal { meals[i].synced = true }
            SharedStorage.saveMeals(meals)
        }
        for id in pendingDeletes {
            guard let uuid = UUID(uuidString: id), (try? await Uploader.delete(uuid)) != nil else { return }
            pendingDeletes.removeAll { $0 == id }
        }
    }

    private func persist() {
        SharedStorage.saveMeals(meals)
        WidgetCenter.shared.reloadAllTimelines()
        Reminders.schedule(meals)
    }
}
