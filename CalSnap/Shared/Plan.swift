import Foundation

/// Piano settimanale della dieta: stesso formato JSON di Diet1, così si importa lo stesso file.
struct DietPlan: Codable, Equatable {
    var id: UUID
    var name: String
    var days: [PlanDay]

    func day(_ weekday: String) -> PlanDay? { days.first { $0.weekday == weekday } }
    func day(for date: Date) -> PlanDay? { day(Slot.weekdayCode(date)) }
}

struct PlanDay: Codable, Equatable {
    var id: UUID
    var weekday: String  // "Lun" … "Dom"
    var meals: [PlanMeal]

    func meal(_ slot: String) -> PlanMeal? { meals.first { $0.name == slot } }
}

struct PlanMeal: Codable, Equatable, Identifiable {
    var id: UUID
    var name: String  // Colazione, Spuntini, Pranzo, Cena
    var items: [PlanItem]

    var kcal: Double { items.reduce(0) { $0 + $1.kcal } }
    var protein: Double { items.reduce(0) { $0 + $1.protein } }
    var carbs: Double { items.reduce(0) { $0 + $1.carbs } }
    var fat: Double { items.reduce(0) { $0 + $1.fat } }
}

struct PlanItem: Codable, Equatable, Identifiable {
    var id: UUID
    var name: String
    var kcal: Double
    var protein: Double
    var carbs: Double
    var fat: Double

    /// "Riso 80 g oppure pasta 80 g" → ["Riso 80 g", "pasta 80 g"]
    var alternatives: [String] {
        name.components(separatedBy: " oppure ")
            .flatMap { $0.components(separatedBy: "Oppure ") }
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    /// Alimento per il diario: prima alternativa, grammi letti dal testo, valori del piano (nessuna IA).
    var foodItem: FoodItem {
        let first = alternatives.first ?? name
        // ponytail: prende il primo "NN g" del testo; senza grammi usa 100 come porzione
        let grams = first.firstMatch(of: #/(\d+)\s*g\b/#).flatMap { Double($0.1) } ?? 100
        return FoodItem(name: first, grams: grams, kcal: kcal, protein: protein, carbs: carbs, fat: fat)
    }
}

/// Fasce dei pasti, con gli stessi nomi del piano.
enum Slot {
    static let all = ["Colazione", "Spuntini", "Pranzo", "Cena"]

    static func icon(_ slot: String) -> String {
        switch slot {
        case "Colazione": "sunrise.fill"
        case "Spuntini": "leaf.fill"
        case "Pranzo": "fork.knife"
        default: "moon.stars.fill"
        }
    }

    /// Fascia proposta in base all'orario.
    static func forTime(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        return switch (c.hour ?? 0) * 60 + (c.minute ?? 0) {
        case ..<(10 * 60 + 30): "Colazione"
        case ..<(12 * 60): "Spuntini"
        case ..<(15 * 60): "Pranzo"
        case ..<(18 * 60 + 30): "Spuntini"
        default: "Cena"
        }
    }

    static func weekdayCode(_ date: Date) -> String {
        ["Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab"][Calendar.current.component(.weekday, from: date) - 1]
    }
}

extension SharedStorage {
    private static var planURL: URL { container.appendingPathComponent("plan.json") }

    static func loadPlan() -> DietPlan? {
        guard let data = try? Data(contentsOf: planURL) else { return nil }
        return try? JSONDecoder().decode(DietPlan.self, from: data)
    }

    static func savePlan(_ plan: DietPlan?) {
        if let plan, let data = try? JSONEncoder().encode(plan) {
            try? data.write(to: planURL, options: .atomic)
        } else {
            try? FileManager.default.removeItem(at: planURL)
        }
    }
}
