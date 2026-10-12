import SwiftUI

extension Locale {
    static let it = Locale(identifier: "it_IT")
}

enum Palette {
    static let accent = Color(red: 0.13, green: 0.74, blue: 0.56)
    static let lime = Color(red: 0.55, green: 0.85, blue: 0.30)
    static let protein = Color(red: 0.98, green: 0.38, blue: 0.50)
    static let carbs = Color(red: 1.00, green: 0.68, blue: 0.20)
    static let fat = Color(red: 0.45, green: 0.55, blue: 1.00)
    static let over = Color(red: 1.00, green: 0.33, blue: 0.30)
}

/// Anello delle calorie: verde fino all'obiettivo, arancio/rosso oltre.
struct CalorieRing: View {
    let progress: Double
    var lineWidth: CGFloat = 14

    var body: some View {
        let p = min(max(progress, 0), 1)
        let colors = progress > 1 ? [Palette.carbs, Palette.over] : [Palette.accent, Palette.lime]
        ZStack {
            Circle().stroke(Palette.accent.opacity(0.15), lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: p)
                .stroke(AngularGradient(colors: colors, center: .center, startAngle: .zero, endAngle: .degrees(360 * max(p, 0.01))),
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .padding(lineWidth / 2)
    }
}

/// Barra divisa in proporzione alle kcal di proteine, carboidrati e grassi.
struct MacroSplitBar: View {
    let protein: Double
    let carbs: Double
    let fat: Double
    var height: CGFloat = 8

    var body: some View {
        let parts = [(protein * 4, Palette.protein), (carbs * 4, Palette.carbs), (fat * 9, Palette.fat)].filter { $0.0 > 0 }
        let total = parts.reduce(0) { $0 + $1.0 }
        GeometryReader { geo in
            let available = geo.size.width - CGFloat(max(parts.count - 1, 0)) * 3
            HStack(spacing: 3) {
                if parts.isEmpty {
                    Capsule().fill(.quaternary)
                }
                ForEach(parts.indices, id: \.self) { i in
                    Capsule().fill(parts[i].1).frame(width: available * parts[i].0 / total)
                }
            }
        }
        .frame(height: height)
    }
}

struct MacroRow: View {
    let name: String
    let grams: Double
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 8, height: 8)
            Text(name).foregroundStyle(.secondary)
            Spacer(minLength: 4)
            Text("\(Int(grams.rounded())) g").fontWeight(.semibold).monospacedDigit()
        }
        .font(.caption)
    }
}

struct MealThumb: View {
    let meal: Meal
    var size: CGFloat = 60

    var body: some View {
        Group {
            if meal.hasPhoto, let image = UIImage(contentsOfFile: SharedStorage.photoURL(meal.id).path) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ZStack {
                    Palette.accent.opacity(0.15)
                    Image(systemName: "fork.knife").font(.system(size: size * 0.35)).foregroundStyle(Palette.accent)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(.rect(cornerRadius: size * 0.28))
    }
}

/// Kcal rimaste (o in eccesso) da mostrare al centro dell'anello.
struct RemainingLabel: View {
    let kcal: Int
    let goal: Int
    var size: CGFloat = 32

    var body: some View {
        VStack(spacing: 0) {
            Text("\(abs(goal - kcal))")
                .font(.system(size: size, weight: .bold, design: .rounded))
                .contentTransition(.numericText())
                .minimumScaleFactor(0.5).lineLimit(1)
            Text(kcal <= goal ? "kcal rimaste" : "kcal in più")
                .font(.system(size: max(size * 0.32, 10), weight: .medium))
                .foregroundStyle(kcal <= goal ? Color.secondary : Palette.over)
        }
        .padding(.horizontal, 6)
    }
}
