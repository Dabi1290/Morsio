import SwiftUI
import WidgetKit

struct DayEntry: TimelineEntry {
    let date: Date
    let meals: [Meal]  // di oggi, dal più recente
    let goal: Int

    var kcal: Int { meals.kcal }
    var progress: Double { Double(kcal) / Double(max(goal, 1)) }

    static func now() -> DayEntry {
        DayEntry(date: .now, meals: SharedStorage.loadMeals().on(.now).sorted { $0.date > $1.date }, goal: SharedStorage.goal)
    }

    static let sample = DayEntry(date: .now, meals: [
        Meal(name: "Spaghetti al pomodoro", kcal: 510, protein: 15, carbs: 78, fat: 15),
        Meal(name: "Yogurt greco e miele", kcal: 210, protein: 18, carbs: 22, fat: 5),
        Meal(name: "Cappuccino e cornetto", kcal: 380, protein: 9, carbs: 48, fat: 17),
    ], goal: 2000)
}

struct WidgetHeader: View {
    var body: some View {
        HStack {
            Label("Oggi", systemImage: "flame.fill").font(.caption.weight(.bold)).foregroundStyle(Palette.accent)
            Spacer()
            Image(systemName: "plus.circle.fill").font(.title3).foregroundStyle(Palette.accent)
        }
    }
}

struct SmallWidgetView: View {
    let entry: DayEntry

    var body: some View {
        VStack(spacing: 6) {
            WidgetHeader()
            CalorieRing(progress: entry.progress, lineWidth: 11)
                .overlay { RemainingLabel(kcal: entry.kcal, goal: entry.goal, size: 24) }
                .widgetAccentable()
        }
    }
}

struct MediumWidgetView: View {
    let entry: DayEntry

    var body: some View {
        HStack(spacing: 16) {
            CalorieRing(progress: entry.progress, lineWidth: 12)
                .overlay { RemainingLabel(kcal: entry.kcal, goal: entry.goal, size: 26) }
                .widgetAccentable()
                .frame(width: 124)
            VStack(alignment: .leading, spacing: 7) {
                WidgetHeader()
                Text("\(entry.kcal) / \(entry.goal) kcal").font(.subheadline.weight(.bold)).monospacedDigit()
                MacroSplitBar(protein: entry.meals.sum(\.protein), carbs: entry.meals.sum(\.carbs), fat: entry.meals.sum(\.fat))
                MacroRow(name: "Proteine", grams: entry.meals.sum(\.protein), color: Palette.protein)
                MacroRow(name: "Carboidrati", grams: entry.meals.sum(\.carbs), color: Palette.carbs)
                MacroRow(name: "Grassi", grams: entry.meals.sum(\.fat), color: Palette.fat)
            }
        }
    }
}

struct LargeWidgetView: View {
    let entry: DayEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            MediumWidgetView(entry: entry).frame(height: 140)
            Divider()
            if entry.meals.isEmpty {
                Spacer()
                Text("Nessun pasto oggi.\nTocca per aggiungerne uno.")
                    .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            } else {
                ForEach(entry.meals.prefix(4)) { meal in
                    HStack(spacing: 10) {
                        MealThumb(meal: meal, size: 38)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(meal.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                            Text(meal.date, style: .time).font(.caption2).foregroundStyle(.secondary)
                        }
                        Spacer()
                        if meal.analysis == nil {
                            Text("\(meal.kcal) kcal").font(.subheadline.weight(.bold)).monospacedDigit()
                        } else {
                            Image(systemName: "sparkles").foregroundStyle(Palette.accent)
                        }
                    }
                }
            }
            Spacer(minLength: 0)
        }
    }
}

struct WidgetBackground: View {
    var body: some View {
        LinearGradient(colors: [Color(.systemBackground), Palette.accent.opacity(0.14)],
                       startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}
