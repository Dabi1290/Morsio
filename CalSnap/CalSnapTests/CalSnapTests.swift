import XCTest
@testable import CalSnap

/// Esegui con ./run_tests.sh: avvia il server locale sulla porta 8766 prima dei test.
@MainActor
final class CalSnapTests: XCTestCase {
    private let server = "http://localhost:8766"
    private let keys = ["serverURL", "uploadKey", "geminiKey"]
    private var saved: [String: Any] = [:]

    override func setUp() async throws {
        for k in keys { saved[k] = SharedStorage.defaults.object(forKey: k) }
        SharedStorage.defaults.set(server, forKey: "serverURL")
        SharedStorage.defaults.set("test-upload", forKey: "uploadKey")
    }

    override func tearDown() async throws {
        for k in keys { SharedStorage.defaults.set(saved[k], forKey: k) }
    }

    /// Immagine 3000x4000 "rumorosa", simile a una foto vera per il JPEG.
    private func photo() -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 3000, height: 4000), format: format).image { ctx in
            for x in stride(from: 0, to: 3000, by: 20) {
                for y in stride(from: 0, to: 4000, by: 20) {
                    UIColor(hue: .random(in: 0...1), saturation: 0.7, brightness: .random(in: 0.3...1), alpha: 1).setFill()
                    ctx.fill(CGRect(x: x, y: y, width: 20, height: 20))
                }
            }
        }
    }

    private func guestPage() async throws -> String {
        let req = URLRequest(url: URL(string: "\(server)/g/test-guest/")!, cachePolicy: .reloadIgnoringLocalCacheData)
        let (data, _) = try await URLSession.shared.data(for: req)
        return String(decoding: data, as: UTF8.self)
    }

    func testCompression() {
        let data = photo().jpeg(maxSide: 512, quality: 0.4)
        let image = UIImage(data: data)!
        XCTAssertEqual(max(image.size.width, image.size.height), 512)
        XCTAssertLessThan(data.count, 80_000)
        print("JPEG 512px q0.4: \(data.count / 1024) KB")
    }

    func testOpenFoodFacts() async throws {
        let found = try await OpenFoodFacts.product(barcode: "3017620422003")
        let nutella = try XCTUnwrap(found)
        XCTAssertEqual(nutella.barcode, "3017620422003")
        let missing = try await OpenFoodFacts.product(barcode: "0000000000017")
        XCTAssertNil(missing)
        XCTAssertTrue(nutella.name.localizedCaseInsensitiveContains("nutella"), nutella.name)
        XCTAssertEqual(nutella.kcal100, 539, accuracy: 20)
        XCTAssertEqual(nutella.grams, 100)
        do {
            _ = try await OpenFoodFacts.product(barcode: "123/../x")
            XCTFail("barcode non valido accettato")
        } catch {}
    }

    func testSyncUploadAndDelete() async throws {
        let store = MealStore()
        let meal = Meal(name: "Pasta + tonno \(UUID().uuidString.prefix(4))", kcal: 650, protein: 30, carbs: 80, fat: 15, hasPhoto: true)
        store.save(meal, photo: photo().jpeg(maxSide: 512, quality: 0.4))
        await store.sync()
        XCTAssertEqual(store.meals.first { $0.id == meal.id }?.synced, true)

        var page = try await guestPage()
        XCTAssertTrue(page.contains(meal.name), "pasto non visibile all'ospite")
        XCTAssertTrue(page.contains("\(meal.id.uuidString).jpg"), "foto non caricata")

        var edited = meal
        edited.name = "Modificato \(UUID().uuidString.prefix(4))"
        edited.kcal = 700
        store.save(edited, photo: nil)
        XCTAssertEqual(store.meals.filter { $0.id == meal.id }.count, 1, "la modifica deve sostituire, non duplicare")
        XCTAssertNotNil(UIImage(contentsOfFile: SharedStorage.photoURL(meal.id).path), "foto persa in modifica")
        await store.sync()
        page = try await guestPage()
        XCTAssertTrue(page.contains(edited.name) && page.contains("700 kcal"), "modifica non arrivata al server")
        XCTAssertFalse(page.contains(meal.name))
        XCTAssertTrue(page.contains("\(meal.id.uuidString).jpg"), "foto persa sul server dopo la modifica")

        store.delete(edited)
        await store.sync()
        page = try await guestPage()
        XCTAssertFalse(page.contains(edited.name), "pasto non cancellato dal server")
    }

    func testRetryWhenOffline() async throws {
        SharedStorage.defaults.set("http://localhost:1", forKey: "serverURL")  // porta chiusa
        let store = MealStore()
        let meal = Meal(name: "Offline \(UUID().uuidString.prefix(4))", kcal: 100, protein: 0, carbs: 25, fat: 0)
        store.save(meal, photo: nil)
        await store.sync()
        XCTAssertEqual(store.meals.first { $0.id == meal.id }?.synced, false)
        XCTAssertEqual(MealStore().meals.first { $0.id == meal.id }?.synced, false, "stato non persistito")

        SharedStorage.defaults.set(server, forKey: "serverURL")  // torna online
        await store.sync()
        XCTAssertEqual(store.meals.first { $0.id == meal.id }?.synced, true)
        let page = try await guestPage()
        XCTAssertTrue(page.contains(meal.name))

        store.delete(meal)
        await store.sync()
    }

    private func waitForAnalyses(_ store: MealStore) async {
        let deadline = Date().addingTimeInterval(90)
        while !store.analyzing.isEmpty && Date() < deadline {
            try? await Task.sleep(for: .milliseconds(100))
        }
    }

    private var hasGeminiKey: Bool { !(ProcessInfo.processInfo.environment["GEMINI_KEY"] ?? "").isEmpty }

    func testOldDataStillDecodes() throws {
        let old = #"[{"fat":4,"id":"CC3E4B32-E93A-4D93-A11B-9CC9C247A350","carbs":65,"hasPhoto":false,"date":813232044.5,"synced":true,"name":"Riso e tonno","kcal":415,"protein":28}]"#
        let meals = try JSONDecoder().decode([Meal].self, from: Data(old.utf8))
        XCTAssertEqual(meals.first?.name, "Riso e tonno")
        XCTAssertNil(meals.first?.analysis)
    }

    func testBackgroundAnalysisFailureIsShownAndNotUploaded() async throws {
        SharedStorage.defaults.set("", forKey: "geminiKey")
        let store = MealStore()
        let meal = Meal(name: "Nuovo pasto", kcal: 0, protein: 0, carbs: 0, fat: 0)
        store.analyzeInBackground(meal, photo: nil, aiPhoto: nil, note: "una mela \(UUID().uuidString.prefix(4))")
        XCTAssertNotNil(store.meals.first { $0.id == meal.id }?.analysis, "il pasto deve comparire subito")
        await waitForAnalyses(store)
        XCTAssertNotNil(store.meals.first { $0.id == meal.id }?.analysis?.error)

        await store.sync()
        XCTAssertEqual(store.meals.first { $0.id == meal.id }?.synced, false, "un pasto in analisi non va all'ospite")
        store.delete(meal)
        await store.sync()
    }

    func testBackgroundAnalysis() async throws {
        guard hasGeminiKey else { throw XCTSkip("GEMINI_KEY non impostata") }
        SharedStorage.defaults.set(ProcessInfo.processInfo.environment["GEMINI_KEY"], forKey: "geminiKey")
        let store = MealStore()
        let meal = Meal(name: "Nuovo pasto", kcal: 0, protein: 0, carbs: 0, fat: 0)
        store.analyzeInBackground(meal, photo: nil, aiPhoto: nil, note: "una banana media")
        await waitForAnalyses(store)
        let done = try XCTUnwrap(store.meals.first { $0.id == meal.id })
        print("Analisi in background: \(done.name) \(done.kcal) kcal")
        XCTAssertNil(done.analysis)
        XCTAssertGreaterThan(done.kcal, 50)
        XCTAssertFalse(done.items?.isEmpty ?? true, "l'IA deve restituire gli alimenti con i grammi")
        XCTAssertTrue(done.synced, "dopo l'analisi il pasto va inviato al server")
        store.delete(done)
        await store.sync()
    }

    func testManualEditWinsOverAI() async throws {
        guard hasGeminiKey else { throw XCTSkip("GEMINI_KEY non impostata") }
        SharedStorage.defaults.set(ProcessInfo.processInfo.environment["GEMINI_KEY"], forKey: "geminiKey")
        let store = MealStore()
        var meal = Meal(name: "Nuovo pasto", kcal: 0, protein: 0, carbs: 0, fat: 0)
        store.analyzeInBackground(meal, photo: nil, aiPhoto: nil, note: "una pizza intera")
        meal.name = "Corretto a mano"
        meal.kcal = 123
        store.save(meal, photo: nil)  // come fa "Salva" nell'editor: analysis = nil
        await waitForAnalyses(store)
        XCTAssertEqual(store.meals.first { $0.id == meal.id }?.kcal, 123)
        XCTAssertEqual(store.meals.first { $0.id == meal.id }?.name, "Corretto a mano")
        store.delete(meal)
        await store.sync()
    }

    // MARK: - Piano

    /// Il JSON vero della dieta (quello di Diet1), letto dalla cartella del progetto.
    private func realPlanData() throws -> Data {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("dieta_settimanale.json")
        return try Data(contentsOf: url)
    }

    func testImportRealDietJSON() throws {
        let plan = try JSONDecoder().decode(DietPlan.self, from: realPlanData())
        XCTAssertEqual(plan.days.count, 7)
        XCTAssertEqual(plan.day("Lun")?.meals.map(\.name), Slot.all)
        let riso = try XCTUnwrap(plan.day("Lun")?.meal("Pranzo")?.items.first)
        XCTAssertGreaterThan(riso.alternatives.count, 1)
        XCTAssertEqual(riso.foodItem.name, "Riso 80 g")
        XCTAssertEqual(riso.foodItem.grams, 80)
    }

    func testPlanValuesAreUsedWithoutAI() {
        let item = PlanItem(id: UUID(), name: "Hamburger bovino magro 150 g", kcal: 225, protein: 30, carbs: 0, fat: 11)
        let planned = PlanMeal(id: UUID(), name: "Cena", items: [item])
        let meal = Meal.fromPlan(planned, on: .now)
        XCTAssertEqual(meal.kcal, 225)
        XCTAssertEqual(meal.items?.first?.grams, 150)
        XCTAssertEqual(meal.items?.first?.protein ?? 0, 30, accuracy: 0.001)
        XCTAssertEqual(meal.slot, "Cena")
        XCTAssertEqual(meal.fromPlan, true)
        XCTAssertNil(meal.analysis, "dal piano non si chiama l'IA")
    }

    @MainActor
    func testRemindersSkipSlotAlreadyLogged() {
        let d = SharedStorage.defaults
        let keys = Slot.all.flatMap { [Reminders.enabledKey($0), Reminders.timeKey($0)] }
        let saved = keys.map { d.object(forKey: $0) }
        defer { for (k, v) in zip(keys, saved) { d.set(v, forKey: k) } }
        for slot in Slot.all { d.set(slot == "Pranzo" || slot == "Cena", forKey: Reminders.enabledKey(slot)) }
        d.set(13 * 60, forKey: Reminders.timeKey("Pranzo"))
        d.set(20 * 60 + 30, forKey: Reminders.timeKey("Cena"))

        let now = Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: .now)!
        var lunch = Meal(name: "Pasta", kcal: 500, protein: 0, carbs: 0, fat: 0)
        lunch.date = now
        lunch.slot = "Pranzo"
        let ids = Reminders.fireDates([lunch], now: now).map(\.id)
        XCTAssertFalse(ids.contains("Pranzo-0"), "pranzo già inserito oggi")
        XCTAssertTrue(ids.contains("Cena-0"))
        XCTAssertTrue(ids.contains("Pranzo-1"))
        XCTAssertEqual(ids.count, 19)  // 10 giorni × 2 fasce, meno il pranzo di oggi
        let dinner = Reminders.fireDates([], now: now).first { $0.id == "Cena-0" }!.date
        XCTAssertEqual(Calendar.current.dateComponents([.hour, .minute], from: dinner), DateComponents(hour: 20, minute: 30))
    }

    func testSlotFromTime() {
        func at(_ h: Int, _ m: Int) -> Date { Calendar.current.date(bySettingHour: h, minute: m, second: 0, of: .now)! }
        XCTAssertEqual(Slot.forTime(at(8, 0)), "Colazione")
        XCTAssertEqual(Slot.forTime(at(11, 0)), "Spuntini")
        XCTAssertEqual(Slot.forTime(at(13, 30)), "Pranzo")
        XCTAssertEqual(Slot.forTime(at(17, 0)), "Spuntini")
        XCTAssertEqual(Slot.forTime(at(20, 45)), "Cena")
    }

    func testAdherence() throws {
        let store = MealStore()
        let savedPlan = store.plan
        defer { savedPlan.map { try? store.importPlan(JSONEncoder().encode($0)) } ?? store.removePlan() }

        try store.importPlan(realPlanData())
        let today = try XCTUnwrap(store.plan?.day(for: .now))
        let before = try XCTUnwrap(store.adherence(on: .now))
        XCTAssertEqual(before.total, today.meals.count)

        var fromPlan = Meal.fromPlan(try XCTUnwrap(today.meal("Pranzo")), on: .now)
        fromPlan.date = Calendar.current.date(bySettingHour: 13, minute: 0, second: 0, of: .now)!
        store.save(fromPlan, photo: nil)
        var offPlan = Meal(name: "Pizza", kcal: 800, protein: 30, carbs: 90, fat: 30)
        offPlan.slot = "Cena"
        store.save(offPlan, photo: nil)
        defer { store.delete(fromPlan); store.delete(offPlan) }

        let after = try XCTUnwrap(store.adherence(on: .now))
        XCTAssertEqual(after.done, min(before.done + 1, after.total), "solo il pasto dal piano conta")
    }

    func testGeminiParse() throws {
        let json = #"{"steps":[{"type":"thought","signature":"x"},{"type":"model_output","content":[{"type":"text","text":"{\"name\":\"Pasta al sugo\",\"items\":[{\"name\":\"Spaghetti\",\"grams\":100,\"kcal\":360,\"protein\":12,\"carbs\":72,\"fat\":1.5},{\"name\":\"Sugo\",\"grams\":80,\"kcal\":40,\"protein\":1,\"carbs\":6,\"fat\":1}]}"}]}]}"#
        let ai = try Gemini.parse(Data(json.utf8))
        XCTAssertEqual(ai.name, "Pasta al sugo")
        XCTAssertEqual(ai.items.map(\.name), ["Spaghetti", "Sugo"])
        XCTAssertEqual(ai.items[1].kcal, 40, accuracy: 0.001)
    }

    func testChangingGramsRecomputes() {
        var pasta = FoodItem(name: "Spaghetti", grams: 100, kcal: 360, protein: 12, carbs: 72, fat: 1.5)
        let sugo = FoodItem(name: "Sugo", grams: 80, kcal: 40, protein: 1, carbs: 6, fat: 1)
        pasta.grams = 150
        XCTAssertEqual(pasta.kcal, 540, accuracy: 0.001)
        XCTAssertEqual(pasta.carbs, 108, accuracy: 0.001)

        var meal = Meal(name: "Pasta", kcal: 0, protein: 0, carbs: 0, fat: 0)
        meal.setItems([pasta, sugo])
        XCTAssertEqual(meal.kcal, 580)
        XCTAssertEqual(meal.protein, 19, accuracy: 0.001)

        pasta.grams = 0  // azzerare e poi ripristinare non perde i valori per 100 g
        pasta.grams = 100
        XCTAssertEqual(pasta.kcal, 360, accuracy: 0.001)

        // correggere le kcal della porzione (come fa l'editor) e poi cambiare grammi: il valore corretto si riscala
        var burro = FoodItem(name: "Burro", grams: 20, kcal: 147, protein: 0.2, carbs: 0, fat: 16.2)
        burro.kcal100 = 100 * 100 / burro.grams  // l'utente scrive 100 kcal per 20 g
        burro.grams = 10
        XCTAssertEqual(burro.kcal, 50, accuracy: 0.001)

        meal.setItems([])  // senza alimenti restano i totali manuali
        XCTAssertNil(meal.items)
        XCTAssertEqual(meal.kcal, 580)
    }

    func testGemini() async throws {
        guard let key = ProcessInfo.processInfo.environment["GEMINI_KEY"], !key.isEmpty else {
            throw XCTSkip("GEMINI_KEY non impostata")
        }
        SharedStorage.defaults.set(key, forKey: "geminiKey")
        let photo = ProcessInfo.processInfo.environment["FOOD_PHOTO"].flatMap { UIImage(contentsOfFile: $0) }
        let ai = try await Gemini.analyze(photo: photo?.jpeg(maxSide: 1024, quality: 0.7),
                                          note: photo == nil ? "un piatto di 100 g di pasta al pomodoro" : "")
        print("Gemini: \(ai.name) → " + ai.items.map { "\($0.name) \(Int($0.grams)) g \(Int($0.kcal)) kcal" }.joined(separator: ", "))
        XCTAssertFalse(ai.name.isEmpty)
        XCTAssertFalse(ai.items.isEmpty)
        XCTAssertTrue(ai.items.allSatisfy { $0.grams > 0 })
        XCTAssertGreaterThan(ai.items.reduce(0) { $0 + $1.kcal }, 100)
    }
}
