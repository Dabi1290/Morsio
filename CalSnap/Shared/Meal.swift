import Foundation

/// Dati condivisi tra app e widget tramite App Group.
enum SharedStorage {
    static let appGroup = "group.com.dlab.calsnap"
    static let defaults = UserDefaults(suiteName: appGroup)!
    static let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)!
    private static let mealsURL = container.appendingPathComponent("meals.json")

    static func photoURL(_ id: UUID) -> URL {
        container.appendingPathComponent("\(id.uuidString).jpg")
    }

    /// Foto a risoluzione più alta usata solo dall'IA, cancellata a fine analisi.
    static func aiPhotoURL(_ id: UUID) -> URL {
        container.appendingPathComponent("\(id.uuidString)-ai.jpg")
    }

    static func loadMeals() -> [Meal] {
        guard let data = try? Data(contentsOf: mealsURL),
              let meals = try? JSONDecoder().decode([Meal].self, from: data) else {
            return []
        }
        return meals
    }

    static func saveMeals(_ meals: [Meal]) {
        if let data = try? JSONEncoder().encode(meals) {
            try? data.write(to: mealsURL, options: .atomic)
        }
    }

    /// Prodotti col barcode inseriti o corretti a mano (valori per 100 g): hanno la precedenza su Open Food Facts.
    static var products: [String: FoodItem] {
        get { defaults.data(forKey: "products").flatMap { try? JSONDecoder().decode([String: FoodItem].self, from: $0) } ?? [:] }
        set { defaults.set(try? JSONEncoder().encode(newValue), forKey: "products") }
    }

    static var goal: Int {
        let g = defaults.integer(forKey: "goal")
        return g > 0 ? g : 2000
    }
}

struct Meal: Codable, Identifiable, Equatable {
    var id = UUID()
    var date = Date()
    var name: String
    var kcal: Int
    var protein: Double
    var carbs: Double
    var fat: Double
    var hasPhoto = false
    var synced = false
    /// Presente finché l'IA non ha finito (o se ha fallito). Opzionale: i dati salvati prima restano leggibili.
    var analysis: PendingAnalysis?
    /// Alimenti con il loro peso (dall'IA o dal barcode). Se presenti, i totali del pasto sono la loro somma.
    var items: [FoodItem]?
    /// Fascia del piano (Colazione, Spuntini, Pranzo, Cena); se manca si deduce dall'orario.
    var slot: String?
    /// Pasto che segue il piano: conta per l'aderenza.
    var fromPlan: Bool?

    var resolvedSlot: String { slot ?? Slot.forTime(date) }

    mutating func setItems(_ items: [FoodItem]) {
        self.items = items.isEmpty ? nil : items
        guard !items.isEmpty else { return }
        kcal = Int(items.reduce(0) { $0 + $1.kcal }.rounded())
        protein = items.reduce(0) { $0 + $1.protein }
        carbs = items.reduce(0) { $0 + $1.carbs }
        fat = items.reduce(0) { $0 + $1.fat }
    }
}

/// Un alimento del pasto. I valori sono salvati per 100 g: cambiando i grammi si ricalcola tutto.
struct FoodItem: Codable, Equatable, Identifiable {
    var id = UUID()
    var name: String
    var grams: Double
    var kcal100: Double
    var protein100: Double
    var carbs100: Double
    var fat100: Double
    /// Codice a barre del prodotto: salvando il pasto, nome e valori si ricordano per quel codice.
    var barcode: String?

    var kcal: Double { kcal100 * grams / 100 }
    var protein: Double { protein100 * grams / 100 }
    var carbs: Double { carbs100 * grams / 100 }
    var fat: Double { fat100 * grams / 100 }
}

extension FoodItem {
    /// Da valori totali riferiti a `grams` grammi (come li stima l'IA).
    init(name: String, grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double) {
        let g = grams > 0 ? grams : 100
        self.init(name: name, grams: g, kcal100: kcal * 100 / g, protein100: protein * 100 / g,
                  carbs100: carbs * 100 / g, fat100: fat * 100 / g)
    }
}

struct PendingAnalysis: Codable, Equatable {
    var note: String
    var error: String?
}

extension Array where Element == Meal {
    func on(_ day: Date) -> [Meal] {
        filter { Calendar.current.isDate($0.date, inSameDayAs: day) }
    }

    var kcal: Int { reduce(0) { $0 + $1.kcal } }

    func sum(_ key: KeyPath<Meal, Double>) -> Double {
        reduce(0) { $0 + $1[keyPath: key] }
    }
}
