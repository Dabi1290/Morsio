import SwiftUI

struct ContentView: View {
    @EnvironmentObject var store: MealStore
    @AppStorage("goal", store: SharedStorage.defaults) var goal = 2000
    @State private var day = Calendar.current.startOfDay(for: .now)
    @Binding var adding: Bool
    @State private var showSettings = false
    @State private var showCalendar = false
    @State private var editing: Meal?
    @State private var draft: Meal?  // pasto nuovo precompilato dal piano

    var body: some View {
        let meals = store.meals.on(day).sorted { $0.date > $1.date }
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    DayStrip(day: $day, meals: store.meals, goal: goal)
                    SummaryCard(meals: meals, goal: goal, adherence: store.adherence(on: day))
                        .padding(.horizontal)

                    let planDay = store.plan?.day(for: day)
                    if meals.isEmpty && planDay == nil {
                        ContentUnavailableView("Nessun pasto", systemImage: "fork.knife",
                                               description: Text("Tocca \"Aggiungi pasto\" qui sotto per iniziare."))
                            .padding(.top, 20)
                    } else {
                        VStack(alignment: .leading, spacing: 18) {
                            // fasce del piano + eventuali fasce non previste ma con pasti
                            ForEach(Slot.all.filter { s in planDay?.meal(s) != nil || meals.contains { $0.resolvedSlot == s } }, id: \.self) { slot in
                                slotSection(slot, logged: meals.filter { $0.resolvedSlot == slot }, planned: planDay?.meal(slot))
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .padding(.bottom, 24)
                .animation(.snappy, value: day)
                .animation(.snappy, value: store.meals)
            }
            .background { AppBackground() }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Impostazioni", systemImage: "gearshape") { showSettings = true }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Calendario", systemImage: "calendar") { showCalendar = true }
                }
            }
            .sheet(isPresented: $adding) { AddMealView() }
            .sheet(isPresented: $showSettings) { SettingsView() }
            .sheet(item: $editing) { AddMealView(editing: $0) }
            .sheet(item: $draft) { AddMealView(editing: $0) }
            .sheet(isPresented: $showCalendar) {
                DatePicker("Giorno", selection: $day, in: ...Date(), displayedComponents: .date)
                    .datePickerStyle(.graphical)
                    .padding()
                    .presentationDetents([.medium])
                    .onChange(of: day) { showCalendar = false }
            }
        }
    }

    private func slotSection(_ slot: String, logged: [Meal], planned: PlanMeal?) -> some View {
        let followed = logged.contains { $0.fromPlan == true }
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Label(slot, systemImage: Slot.icon(slot)).font(.headline)
                if followed {
                    Image(systemName: "checkmark.seal.fill").foregroundStyle(Palette.accent)
                        .accessibilityLabel("Piano seguito")
                }
                Spacer()
                if let planned, planned.kcal > 0 {
                    Text("piano \(Int(planned.kcal)) kcal").font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 4)
            ForEach(logged) { meal in
                Button { editing = meal } label: { MealCard(meal: meal) }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button("Modifica", systemImage: "pencil") { editing = meal }
                        if meal.analysis?.error != nil {
                            Button("Riprova analisi", systemImage: "arrow.clockwise") { store.retryAnalysis(meal) }
                        }
                        Button("Elimina", systemImage: "trash", role: .destructive) {
                            withAnimation { store.delete(meal) }
                            Task { await store.sync() }
                        }
                    }
            }
            if logged.isEmpty, let planned {
                Button { draft = .fromPlan(planned, on: day) } label: { PlanSuggestionCard(meal: planned) }
                    .buttonStyle(.plain)
            }
        }
    }

    private var title: String {
        let cal = Calendar.current
        if cal.isDateInToday(day) { return "Oggi" }
        if cal.isDateInYesterday(day) { return "Ieri" }
        return day.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(.it)).capitalized
    }
}

/// Ultimi 14 giorni, ognuno con un mini anello di avanzamento.
struct DayStrip: View {
    @Binding var day: Date
    let meals: [Meal]
    let goal: Int

    var body: some View {
        let cal = Calendar.current
        let today = cal.startOfDay(for: .now)
        let days = (0..<14).reversed().map { cal.date(byAdding: .day, value: -$0, to: today)! }
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(days, id: \.self) { d in
                        let selected = cal.isDate(d, inSameDayAs: day)
                        Button { day = d } label: {
                            VStack(spacing: 6) {
                                Text(d.formatted(.dateTime.weekday(.abbreviated).locale(.it)).uppercased())
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(selected ? .white.opacity(0.85) : .secondary)
                                Text(d.formatted(.dateTime.day())).font(.headline)
                                CalorieRing(progress: Double(meals.on(d).kcal) / Double(max(goal, 1)), lineWidth: 3)
                                    .frame(width: 20, height: 20)
                            }
                            .frame(width: 50)
                            .padding(.vertical, 10)
                            .foregroundStyle(selected ? .white : .primary)
                            .background {
                                RoundedRectangle(cornerRadius: 18)
                                    .fill(selected ? AnyShapeStyle(Palette.accent.gradient) : AnyShapeStyle(Color(.secondarySystemGroupedBackground)))
                            }
                        }
                        .buttonStyle(.plain)
                        .id(d)
                    }
                }
                .padding(.horizontal)
            }
            .onAppear { proxy.scrollTo(today, anchor: .trailing) }
        }
    }
}

/// Sfondo comune alle schede: grigio di sistema con una sfumatura verde in alto.
struct AppBackground: View {
    var body: some View {
        ZStack(alignment: .top) {
            Color(.systemGroupedBackground)
            LinearGradient(colors: [Palette.accent.opacity(0.22), .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: 360)
        }
        .ignoresSafeArea()
    }
}

/// Fascia ancora vuota: cosa prevede il piano. Toccandola si apre il pasto già compilato.
struct PlanSuggestionCard: View {
    let meal: PlanMeal

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("DAL PIANO").font(.caption2.weight(.bold)).foregroundStyle(Palette.accent)
                ForEach(meal.items) { item in
                    Text(item.alternatives.first ?? item.name).font(.subheadline).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "plus.circle.fill").font(.title2).foregroundStyle(Palette.accent)
        }
        .padding(14)
        .background {
            RoundedRectangle(cornerRadius: 22)
                .strokeBorder(Palette.accent.opacity(0.45), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
                .background(Palette.accent.opacity(0.06), in: .rect(cornerRadius: 22))
        }
        .contentShape(.rect(cornerRadius: 22))
    }
}

extension Meal {
    /// Pasto nuovo con gli alimenti del piano: valori del piano, nessuna IA.
    static func fromPlan(_ planned: PlanMeal, on day: Date) -> Meal {
        let cal = Calendar.current
        // giorno scelto nel diario, orario attuale (o mezzogiorno per i giorni passati)
        let date = cal.isDateInToday(day) ? Date() : cal.date(bySettingHour: 12, minute: 0, second: 0, of: day)!
        var meal = Meal(date: date, name: planned.name, kcal: 0, protein: 0, carbs: 0, fat: 0)
        meal.slot = planned.name
        meal.fromPlan = true
        meal.setItems(planned.items.map(\.foodItem))
        return meal
    }
}

struct SummaryCard: View {
    let meals: [Meal]
    let goal: Int
    var adherence: (done: Int, total: Int)?

    var body: some View {
        let kcal = meals.kcal
        HStack(spacing: 20) {
            CalorieRing(progress: Double(kcal) / Double(max(goal, 1)), lineWidth: 16)
                .overlay { RemainingLabel(kcal: kcal, goal: goal, size: 30) }
                .frame(width: 140, height: 140)
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("\(kcal)").font(.system(.title2, design: .rounded).bold()).contentTransition(.numericText())
                    Text("/ \(goal) kcal").font(.subheadline).foregroundStyle(.secondary)
                }
                MacroSplitBar(protein: meals.sum(\.protein), carbs: meals.sum(\.carbs), fat: meals.sum(\.fat))
                MacroRow(name: "Proteine", grams: meals.sum(\.protein), color: Palette.protein)
                MacroRow(name: "Carboidrati", grams: meals.sum(\.carbs), color: Palette.carbs)
                MacroRow(name: "Grassi", grams: meals.sum(\.fat), color: Palette.fat)
                if let adherence {
                    Label("Piano \(adherence.done)/\(adherence.total)", systemImage: "checklist")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Palette.accent)
                        .padding(.top, 2)
                }
            }
        }
        .padding(20)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 28))
        .shadow(color: .black.opacity(0.06), radius: 12, y: 4)
    }
}

struct MealCard: View {
    let meal: Meal

    var body: some View {
        HStack(spacing: 14) {
            MealThumb(meal: meal, size: 64)
            VStack(alignment: .leading, spacing: 5) {
                Text(meal.name).font(.headline).lineLimit(1)
                Text(meal.date, style: .time).font(.caption).foregroundStyle(.secondary)
                if let analysis = meal.analysis {
                    if analysis.error != nil {
                        Label("Analisi non riuscita · tieni premuto per riprovare", systemImage: "exclamationmark.triangle.fill")
                            .font(.caption2.weight(.semibold)).foregroundStyle(Palette.over).lineLimit(1)
                    } else {
                        HStack(spacing: 6) {
                            ProgressView().controlSize(.mini)
                            Text("Analisi in corso…")
                        }
                        .font(.caption2.weight(.semibold)).foregroundStyle(Palette.accent)
                    }
                } else {
                    HStack(spacing: 5) {
                        chip("P", meal.protein, Palette.protein)
                        chip("C", meal.carbs, Palette.carbs)
                        chip("G", meal.fat, Palette.fat)
                    }
                }
            }
            Spacer(minLength: 0)
            if meal.analysis == nil {
                VStack(alignment: .trailing, spacing: 0) {
                    Text("\(meal.kcal)").font(.system(.title3, design: .rounded).bold())
                    Text("kcal").font(.caption2).foregroundStyle(.secondary)
                }
            } else {
                Image(systemName: "sparkles").font(.title2).foregroundStyle(Palette.accent)
                    .symbolEffect(.pulse, isActive: meal.analysis?.error == nil)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: 22))
        .contentShape(.rect(cornerRadius: 22))
    }

    private func chip(_ label: String, _ grams: Double, _ color: Color) -> some View {
        Text("\(label) \(Int(grams.rounded()))")
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 7).padding(.vertical, 3)
            .background(color.opacity(0.15), in: .capsule)
            .foregroundStyle(color)
    }
}
