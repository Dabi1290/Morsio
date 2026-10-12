import SwiftUI
import UniformTypeIdentifiers

struct PlanView: View {
    @EnvironmentObject var store: MealStore
    @State private var weekday = Slot.weekdayCode(.now)
    @State private var importing = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if let plan = store.plan {
                        AdherenceCard(week: store.weeklyAdherence())
                        WeekdayPicker(selection: $weekday)
                        ForEach(plan.day(weekday)?.meals ?? []) { PlanMealCard(meal: $0) }
                    } else {
                        ContentUnavailableView {
                            Label("Nessun piano", systemImage: "list.bullet.clipboard")
                        } description: {
                            Text("Importa il JSON della tua dieta (lo stesso di Diet1): vedrai cosa prevede ogni pasto direttamente nel diario.")
                        } actions: {
                            Button("Importa piano") { importing = true }.buttonStyle(.glassProminent)
                        }
                        .padding(.top, 40)
                    }
                    if let error {
                        Text(error).font(.footnote).foregroundStyle(Palette.over)
                    }
                }
                .padding()
                .animation(.snappy, value: weekday)
            }
            .background { AppBackground() }
            .navigationTitle(store.plan?.name ?? "Piano")
            .toolbar {
                if store.plan != nil {
                    Menu("Opzioni", systemImage: "ellipsis") {
                        Button("Importa un altro piano", systemImage: "square.and.arrow.down") { importing = true }
                        Button("Rimuovi piano", systemImage: "trash", role: .destructive) { store.removePlan() }
                    }
                }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
                do {
                    let url = try result.get()
                    let accessing = url.startAccessingSecurityScopedResource()
                    defer { if accessing { url.stopAccessingSecurityScopedResource() } }
                    try store.importPlan(Data(contentsOf: url))
                    error = nil
                } catch {
                    self.error = "Import non riuscito: \(error.localizedDescription)"
                }
            }
        }
    }
}

struct AdherenceCard: View {
    let week: (done: Int, total: Int)

    var body: some View {
        let progress = week.total > 0 ? Double(week.done) / Double(week.total) : 0
        HStack(spacing: 18) {
            CalorieRing(progress: progress, lineWidth: 10)
                .overlay {
                    Text("\(Int((progress * 100).rounded()))%")
                        .font(.system(.headline, design: .rounded).bold())
                }
                .frame(width: 84, height: 84)
            VStack(alignment: .leading, spacing: 4) {
                Text("Aderenza al piano").font(.headline)
                Text("\(week.done) pasti su \(week.total) negli ultimi 7 giorni")
                    .font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 28))
        .shadow(color: .black.opacity(0.06), radius: 12, y: 4)
    }
}

struct WeekdayPicker: View {
    @Binding var selection: String
    private let days = ["Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"]

    var body: some View {
        HStack(spacing: 6) {
            ForEach(days, id: \.self) { day in
                let selected = day == selection
                let today = day == Slot.weekdayCode(.now)
                Button { selection = day } label: {
                    Text(day.uppercased())
                        .font(.caption.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .foregroundStyle(selected ? .white : today ? Palette.accent : .primary)
                        .background {
                            RoundedRectangle(cornerRadius: 14)
                                .fill(selected ? AnyShapeStyle(Palette.accent.gradient) : AnyShapeStyle(Color(.secondarySystemGroupedBackground)))
                        }
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct PlanMealCard: View {
    let meal: PlanMeal

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(meal.name, systemImage: Slot.icon(meal.name)).font(.headline)
                Spacer()
                if meal.kcal > 0 {
                    Text("\(Int(meal.kcal)) kcal").font(.subheadline.weight(.bold)).foregroundStyle(Palette.accent)
                }
            }
            if meal.kcal > 0 {
                MacroSplitBar(protein: meal.protein, carbs: meal.carbs, fat: meal.fat, height: 6)
            }
            ForEach(meal.items) { item in
                VStack(alignment: .leading, spacing: 3) {
                    let alts = item.alternatives
                    Text(alts.first ?? item.name).font(.subheadline.weight(.semibold))
                    ForEach(alts.dropFirst(), id: \.self) { alt in
                        Text("oppure \(alt)").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 24))
    }
}
