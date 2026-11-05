import SwiftUI
import PhotosUI
import VisionKit

/// Crea un pasto nuovo, oppure modifica `editing` (se non è ancora salvato è un pasto nuovo precompilato, es. dal piano).
struct AddMealView: View {
    @EnvironmentObject var store: MealStore
    @Environment(\.dismiss) var dismiss
    private let editing: Meal?

    @State private var image: UIImage?
    @State private var photoChanged = false
    @State private var date: Date
    @State private var pickerItem: PhotosPickerItem?
    @State private var showCamera = false
    @State private var showScanner = false
    @State private var showHistory = false
    @State private var notice: String?
    @State private var note = ""
    @State private var values: Nutrition
    @State private var items: [FoodItem]
    @State private var slot: String
    @State private var fromPlan: Bool
    @State private var busy = false
    @State private var error: String?

    /// [day]: per un pasto nuovo, il giorno aperto nel diario (anche futuro).
    init(editing meal: Meal? = nil, on day: Date = Date()) {
        editing = meal
        _date = State(initialValue: meal?.date ?? Meal.time(on: day))
        _values = State(initialValue: Nutrition(name: meal?.name ?? "", kcal: Double(meal?.kcal ?? 0),
                                                protein: meal?.protein ?? 0, carbs: meal?.carbs ?? 0, fat: meal?.fat ?? 0))
        _items = State(initialValue: meal?.items ?? [])
        _slot = State(initialValue: meal?.resolvedSlot ?? Slot.forTime(Meal.time(on: day)))
        _fromPlan = State(initialValue: meal?.fromPlan ?? false)
        // piano senza valori: la descrizione è già pronta se si vuole far calcolare l'IA
        if let meal, meal.fromPlan == true, meal.kcal == 0, let items = meal.items {
            _note = State(initialValue: items.map(\.name).joined(separator: ", "))
        }
        if let meal, meal.hasPhoto {
            _image = State(initialValue: UIImage(contentsOfFile: SharedStorage.photoURL(meal.id).path))
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                // foto e sorgenti: una sola riga trasparente, allineata ai bordi delle card
                Section {
                    VStack(spacing: 12) {
                        if let image {
                            Image(uiImage: image).resizable().scaledToFill()
                                .frame(height: 220).frame(maxWidth: .infinity)
                                .clipShape(.rect(cornerRadius: 24))
                                .overlay(alignment: .topTrailing) {
                                    Button("Rimuovi foto", systemImage: "xmark") {
                                        withAnimation { self.image = nil }
                                    }
                                    .labelStyle(.iconOnly)
                                    .font(.subheadline.weight(.bold))
                                    .buttonStyle(.glass)
                                    .buttonBorderShape(.circle)
                                    .padding(10)
                                }
                                .transition(.opacity)
                        }
                        HStack(spacing: 10) {
                            Button { showCamera = true } label: { tile("Foto", "camera.fill") }
                                .disabled(!UIImagePickerController.isSourceTypeAvailable(.camera))
                            PhotosPicker(selection: $pickerItem, matching: .images) { tile("Libreria", "photo.fill") }
                            Button { showScanner = true } label: { tile("Barcode", "barcode.viewfinder") }
                                .disabled(!DataScannerViewController.isSupported)
                            Button { showHistory = true } label: { tile("Storico", "clock.arrow.circlepath") }
                                .disabled(store.meals.isEmpty)
                        }
                        .buttonStyle(.plain)
                    }
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                Section {
                    TextField("Dettagli, es. porzione da 150 g", text: $note, axis: .vertical)
                        .lineLimit(1...4)
                } header: {
                    Text("Descrizione (opzionale)")
                }

                Section {
                    Button(action: analyzeInBackground) {
                        Label("Analizza con IA", systemImage: "sparkles")
                            .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 4)
                    }
                    .buttonStyle(.glassProminent)
                    .disabled(busy || (image == nil && note.isEmpty))
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                } footer: {
                    Text("Il pasto viene salvato subito e l'analisi continua in background: potrai controllare e modificare i valori dopo.")
                }

                if !items.isEmpty {
                    Section {
                        ForEach($items) { $item in
                            ItemRow(item: $item)
                        }
                        .onDelete { items.remove(atOffsets: $0) }
                        addItemButton
                    } header: {
                        Text("Alimenti")
                    } footer: {
                        Text(notice ?? "Correggi i grammi e calorie e macro si ricalcolano. Tocca le kcal per correggere i valori, scorri a sinistra per eliminare.")
                    }
                }

                Section(items.isEmpty ? "Valori" : "Totale") {
                    TextField("Nome del pasto", text: $values.name)
                    if items.isEmpty {
                        field("Kcal", $values.kcal, Palette.accent)
                        field("Proteine (g)", $values.protein, Palette.protein)
                        field("Carboidrati (g)", $values.carbs, Palette.carbs)
                        field("Grassi (g)", $values.fat, Palette.fat)
                        addItemButton
                    } else {
                        total("Kcal", items.reduce(0) { $0 + $1.kcal }, Palette.accent)
                        total("Proteine", items.reduce(0) { $0 + $1.protein }, Palette.protein, unit: "g")
                        total("Carboidrati", items.reduce(0) { $0 + $1.carbs }, Palette.carbs, unit: "g")
                        total("Grassi", items.reduce(0) { $0 + $1.fat }, Palette.fat, unit: "g")
                    }
                    DatePicker("Quando", selection: $date)
                    Picker("Pasto", selection: $slot) {
                        ForEach(Slot.all, id: \.self) { Label($0, systemImage: Slot.icon($0)).tag($0) }
                    }
                    if store.plan != nil {
                        Toggle("Segue il piano", isOn: $fromPlan)
                    }
                }

                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
            }
            .listSectionSpacing(.compact)
            .navigationTitle(isNew ? "Nuovo pasto" : "Modifica pasto")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annulla") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salva", action: save).disabled(values.name.isEmpty)
                }
            }
            .sheet(isPresented: $showCamera) { CameraPicker(image: $image).ignoresSafeArea() }
            .sheet(isPresented: $showScanner) {
                BarcodeScanner { code in
                    showScanner = false
                    Task { await lookup(code) }
                }
                .ignoresSafeArea()
            }
            .sheet(isPresented: $showHistory) {
                HistoryView(meals: store.meals, pickMeal: reuse) { addItem(FoodItem.copy($0)) }
            }
            .onChange(of: pickerItem) {
                Task {
                    if let data = try? await pickerItem?.loadTransferable(type: Data.self) {
                        image = UIImage(data: data)
                    }
                }
            }
            .onChange(of: image) { photoChanged = true }
        }
    }

    private func field(_ label: String, _ value: Binding<Double>, _ color: Color) -> some View {
        LabeledContent {
            TextField(label, value: value, format: .number.precision(.fractionLength(0...1)))
                .keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                .fontWeight(.semibold)
        } label: {
            HStack(spacing: 8) {
                Circle().fill(color).frame(width: 8, height: 8)
                Text(label)
            }
        }
    }

    private func total(_ label: String, _ value: Double, _ color: Color, unit: String = "") -> some View {
        LabeledContent {
            Text("\(Int(value.rounded()))\(unit.isEmpty ? "" : " \(unit)")").fontWeight(.semibold).monospacedDigit()
                .contentTransition(.numericText())
        } label: {
            HStack(spacing: 8) {
                Circle().fill(color).frame(width: 8, height: 8)
                Text(label)
            }
        }
    }

    private func tile(_ title: String, _ icon: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon).font(.title2)
            Text(title).font(.caption.weight(.semibold))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Palette.accent.opacity(0.14), in: .rect(cornerRadius: 16))
        .foregroundStyle(Palette.accent)
    }

    private func analyzeInBackground() {
        var meal = editing ?? Meal(name: "", kcal: 0, protein: 0, carbs: 0, fat: 0)
        if meal.name.isEmpty { meal.name = values.name.isEmpty ? "Nuovo pasto" : values.name }
        meal.date = date
        meal.hasPhoto = image != nil
        meal.slot = slot
        meal.fromPlan = fromPlan ? true : nil
        store.analyzeInBackground(meal,
                                  photo: photoChanged ? image?.jpeg(maxSide: 512, quality: 0.4) : nil,
                                  aiPhoto: image?.jpeg(maxSide: 1024, quality: 0.7),
                                  note: note)
        dismiss()
    }

    private func lookup(_ code: String) async {
        busy = true
        defer { busy = false }
        do {
            var product = SharedStorage.products[code].map(FoodItem.copy)
            if product == nil { product = try await OpenFoodFacts.product(barcode: code) }
            if let product {
                addItem(product)
                if values.name.isEmpty { values.name = product.name }
                notice = nil
            } else {
                // non è nel database: lo compila l'utente e al salvataggio resta in memoria per questo codice
                addItem(FoodItem(name: "", grams: 100, kcal100: 0, protein100: 0, carbs100: 0, fat100: 0, barcode: code))
                notice = "Prodotto \(code) non trovato: scrivi nome e valori dell'etichetta per 100 g. Salvando il pasto, la prossima scansione lo riconosce."
            }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private var isNew: Bool { !store.meals.contains { $0.id == editing?.id } }

    private var addItemButton: some View {
        Button("Aggiungi alimento", systemImage: "plus.circle.fill") {
            addItem(FoodItem(name: "", grams: 100, kcal100: 0, protein100: 0, carbs100: 0, fat100: 0))
        }
    }

    private func addItem(_ item: FoodItem) {
        // valori inseriti a mano prima del primo alimento: diventano un alimento, così non si perdono
        if items.isEmpty && values.kcal > 0 {
            items.append(FoodItem(name: values.name.isEmpty ? "Altro" : values.name, grams: 100, kcal: values.kcal,
                                  protein: values.protein, carbs: values.carbs, fat: values.fat))
        }
        withAnimation { items.append(item) }
    }

    /// Ricarica un pasto dello storico: stessi alimenti e valori, senza IA (foto e orario restano quelli attuali).
    private func reuse(_ past: Meal) {
        values = Nutrition(name: past.name, kcal: Double(past.kcal), protein: past.protein, carbs: past.carbs, fat: past.fat)
        withAnimation { items = (past.items ?? []).map(FoodItem.copy) }
    }

    private func save() {
        for item in items where !item.name.isEmpty {
            if let code = item.barcode { SharedStorage.products[code] = item }
        }
        var meal = editing ?? Meal(name: "", kcal: 0, protein: 0, carbs: 0, fat: 0)
        meal.name = values.name
        meal.kcal = Int(values.kcal.rounded())
        meal.protein = values.protein
        meal.carbs = values.carbs
        meal.fat = values.fat
        meal.setItems(items)  // se ci sono alimenti, i totali sono la loro somma
        meal.date = date
        meal.slot = slot
        meal.fromPlan = fromPlan ? true : nil
        meal.hasPhoto = image != nil
        meal.analysis = nil  // salvataggio manuale: i valori dell'utente vincono sull'IA
        // in modifica la foto salvata è già compressa: la riscrivo solo se è cambiata
        store.save(meal, photo: photoChanged ? image?.jpeg(maxSide: 512, quality: 0.4) : nil)
        Task { await store.sync() }
        dismiss()
    }
}

/// Riga di un alimento: nome, grammi modificabili e, aprendo i valori, kcal e macro correggibili.
struct ItemRow: View {
    @Binding var item: FoodItem
    @State private var expanded: Bool

    init(item: Binding<FoodItem>) {
        _item = item
        // un alimento aggiunto a mano (ancora senza nome) si apre già sui valori
        _expanded = State(initialValue: item.wrappedValue.name.isEmpty)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextField("Alimento", text: $item.name).font(.body.weight(.semibold))
            HStack(spacing: 8) {
                Button {
                    withAnimation(.snappy) { expanded.toggle() }
                } label: {
                    HStack(spacing: 6) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text("\(Int(item.kcal.rounded())) kcal").font(.subheadline.weight(.bold)).foregroundStyle(Palette.accent)
                            Text("P \(Int(item.protein.rounded())) · C \(Int(item.carbs.rounded())) · G \(Int(item.fat.rounded()))")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        .monospacedDigit()
                        .contentTransition(.numericText())
                        Image(systemName: "chevron.down")
                            .font(.caption.weight(.bold)).foregroundStyle(.tertiary)
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(expanded ? "Chiudi valori" : "Modifica valori")
                Spacer(minLength: 4)
                HStack(spacing: 2) {
                    TextField("0", value: $item.grams, format: .number.precision(.fractionLength(0)))
                        .keyboardType(.numberPad).multilineTextAlignment(.trailing)
                        .fontWeight(.semibold).frame(width: 48)
                    Text("g").foregroundStyle(.secondary)
                }
                .padding(.horizontal, 8).padding(.vertical, 6)
                .background(Palette.accent.opacity(0.12), in: .rect(cornerRadius: 10))
                Stepper("Grammi", value: $item.grams, in: 0...5000, step: 10).labelsHidden()
            }
            if expanded {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        macroField("Kcal", portion(\.kcal100), Palette.accent)
                        macroField("Proteine", portion(\.protein100), Palette.protein)
                        macroField("Carbo", portion(\.carbs100), Palette.carbs)
                        macroField("Grassi", portion(\.fat100), Palette.fat)
                    }
                    Text("Valori per \(Int(item.grams)) g. Cambiando i grammi si riscalano.")
                        .font(.caption2).foregroundStyle(.secondary)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(.vertical, 2)
        .animation(.snappy, value: item.grams)
    }

    /// Mostra e modifica il valore della porzione, ma salva per 100 g (così i grammi continuano a riscalare).
    private func portion(_ per100: WritableKeyPath<FoodItem, Double>) -> Binding<Double> {
        Binding(
            get: { item[keyPath: per100] * item.grams / 100 },
            set: { item[keyPath: per100] = item.grams > 0 ? $0 * 100 / item.grams : $0 }
        )
    }

    private func macroField(_ label: String, _ value: Binding<Double>, _ color: Color) -> some View {
        VStack(spacing: 4) {
            Text(label).font(.caption2.weight(.bold)).foregroundStyle(color)
            TextField(label, value: value, format: .number.precision(.fractionLength(0...1)))
                .keyboardType(.decimalPad).multilineTextAlignment(.center)
                .fontWeight(.semibold).monospacedDigit()
                .padding(.vertical, 7)
                .background(color.opacity(0.12), in: .rect(cornerRadius: 10))
        }
        .frame(maxWidth: .infinity)
    }
}

extension FoodItem {
    /// Stesso alimento con un nuovo id, per riusarlo in un altro pasto.
    static func copy(_ item: FoodItem) -> FoodItem {
        var copy = item
        copy.id = UUID()
        return copy
    }
}

/// Pasti e alimenti già inseriti, da riusare senza chiamare l'IA.
struct HistoryView: View {
    let meals: [Meal]
    let pickMeal: (Meal) -> Void
    let pickItem: (FoodItem) -> Void
    @Environment(\.dismiss) var dismiss
    @State private var query = ""
    @AppStorage("historyShowsItems") private var showsItems = false

    /// Il più recente per ogni nome, solo pasti già analizzati.
    private var pastMeals: [Meal] {
        var seen = Set<String>()
        return meals.sorted { $0.date > $1.date }.filter {
            $0.analysis == nil && $0.kcal > 0 && matches($0.name) && seen.insert($0.name.lowercased()).inserted
        }
    }

    private var pastItems: [FoodItem] {
        var seen = Set<String>()
        return meals.sorted { $0.date > $1.date }.filter { $0.analysis == nil }.flatMap { $0.items ?? [] }.filter {
            !$0.name.isEmpty && matches($0.name) && seen.insert($0.name.lowercased()).inserted
        }
    }

    private func matches(_ name: String) -> Bool {
        query.isEmpty || name.localizedCaseInsensitiveContains(query)
    }

    var body: some View {
        let meals = showsItems ? [] : pastMeals, items = showsItems ? pastItems : []
        NavigationStack {
            List {
                if !meals.isEmpty {
                    Section {
                        ForEach(meals) { meal in
                            Button {
                                pickMeal(meal)
                                dismiss()
                            } label: {
                                HStack(spacing: 12) {
                                    MealThumb(meal: meal, size: 44)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(meal.name).font(.body.weight(.semibold)).lineLimit(1)
                                        Text(meal.date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(.it)).capitalized)
                                            .font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer(minLength: 8)
                                    Text("\(meal.kcal) kcal").font(.subheadline.weight(.bold)).foregroundStyle(Palette.accent)
                                }
                            }
                            .tint(.primary)
                        }
                    }
                }
                if !items.isEmpty {
                    Section {
                        ForEach(items) { item in
                            Button {
                                pickItem(item)
                                dismiss()
                            } label: {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.name).font(.body.weight(.semibold)).lineLimit(1)
                                        Text("\(Int(item.grams)) g · P \(Int(item.protein.rounded())) · C \(Int(item.carbs.rounded())) · G \(Int(item.fat.rounded()))")
                                            .font(.caption).foregroundStyle(.secondary).monospacedDigit()
                                    }
                                    Spacer(minLength: 8)
                                    Text("\(Int(item.kcal.rounded())) kcal").font(.subheadline.weight(.bold)).foregroundStyle(Palette.accent)
                                }
                            }
                            .tint(.primary)
                        }
                    }
                }
            }
            .safeAreaInset(edge: .top) {
                Picker("Mostra", selection: $showsItems) {
                    Text("Pasti").tag(false)
                    Text("Alimenti").tag(true)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            .overlay {
                if meals.isEmpty && items.isEmpty {
                    if query.isEmpty {
                        ContentUnavailableView(showsItems ? "Nessun alimento" : "Nessun pasto", systemImage: "clock.arrow.circlepath",
                                               description: Text("Quello che salvi compare qui, pronto da riusare."))
                    } else {
                        ContentUnavailableView.search(text: query)
                    }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Cerca pasto o alimento")
            .navigationTitle("Storico")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Chiudi") { dismiss() } }
            }
        }
    }
}
