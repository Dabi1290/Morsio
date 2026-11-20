import SwiftUI
import WidgetKit

struct SettingsView: View {
    @Environment(\.dismiss) var dismiss
    @AppStorage("goal", store: SharedStorage.defaults) var goal = 2000
    @AppStorage("theme", store: SharedStorage.defaults) var theme = "system"
    @AppStorage("geminiKey", store: SharedStorage.defaults) var geminiKey = ""
    @AppStorage("geminiModel", store: SharedStorage.defaults) var geminiModel = "gemini-flash-latest"
    @AppStorage("groqKey", store: SharedStorage.defaults) var groqKey = ""
    @AppStorage("groqModel", store: SharedStorage.defaults) var groqModel = Groq.defaultModel
    @AppStorage("serverURL", store: SharedStorage.defaults) var serverURL = ""
    @AppStorage("uploadKey", store: SharedStorage.defaults) var uploadKey = ""
    @State private var notificationsDenied = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Obiettivo giornaliero") {
                    Stepper(value: $goal, in: 1000...5000, step: 50) {
                        Label("\(goal) kcal", systemImage: "target")
                    }
                }
                Section("Aspetto") {
                    Picker("Tema", selection: $theme) {
                        Text("Sistema").tag("system")
                        Text("Chiaro").tag("light")
                        Text("Scuro").tag("dark")
                    }
                    .pickerStyle(.segmented)
                }
                Section {
                    ForEach(Slot.all, id: \.self) { ReminderRow(slot: $0, denied: $notificationsDenied) }
                } header: {
                    Text("Promemoria")
                } footer: {
                    Text(notificationsDenied
                         ? "Le notifiche sono disattivate: abilitale da Impostazioni › Morsio."
                         : "Se il pasto è già nel diario, il promemoria di quel giorno non arriva.")
                }
                Section {
                    LabeledContent("Chiave API") {
                        SecureField("Incolla la chiave", text: $geminiKey).multilineTextAlignment(.trailing).frame(maxWidth: 170)
                    }
                    LabeledContent("Modello") {
                        TextField("gemini-flash-latest", text: $geminiModel)
                            .multilineTextAlignment(.trailing)
                            .autocorrectionDisabled().textInputAutocapitalization(.never)
                    }
                } header: {
                    Text("Gemini")
                } footer: {
                    Text("Chiave gratuita da aistudio.google.com. Se il modello è sovraccarico si passa da soli a Flash-Lite.")
                }
                Section {
                    LabeledContent("Chiave API") {
                        SecureField("Incolla la chiave", text: $groqKey).multilineTextAlignment(.trailing).frame(maxWidth: 170)
                    }
                    LabeledContent("Modello") {
                        TextField(Groq.defaultModel, text: $groqModel)
                            .multilineTextAlignment(.trailing)
                            .autocorrectionDisabled().textInputAutocapitalization(.never)
                    }
                } header: {
                    Text("Groq")
                } footer: {
                    Text("Chiave gratuita da console.groq.com. Se c'è, si usa prima Groq e Gemini resta di riserva.")
                }
                Section {
                    LabeledContent("Indirizzo") {
                        TextField("https://cibo.esempio.it", text: $serverURL)
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.URL).autocorrectionDisabled().textInputAutocapitalization(.never)
                    }
                    LabeledContent("Chiave upload") {
                        SecureField("Da setup.sh", text: $uploadKey).multilineTextAlignment(.trailing).frame(maxWidth: 170)
                    }
                } header: {
                    Text("Server foto")
                } footer: {
                    Text("Lascia vuoto per non caricare le foto.")
                }
            }
            .navigationTitle("Impostazioni")
            .toolbar { Button("Fine") { dismiss() } }
            .onDisappear { WidgetCenter.shared.reloadAllTimelines() }
        }
    }
}

/// Interruttore e orario del promemoria di una fascia.
struct ReminderRow: View {
    @EnvironmentObject var store: MealStore
    let slot: String
    @Binding var denied: Bool
    @AppStorage private var on: Bool
    @AppStorage private var minutes: Int

    init(slot: String, denied: Binding<Bool>) {
        self.slot = slot
        _denied = denied
        _on = AppStorage(wrappedValue: false, Reminders.enabledKey(slot), store: SharedStorage.defaults)
        _minutes = AppStorage(wrappedValue: Reminders.defaultTimes[slot] ?? 12 * 60, Reminders.timeKey(slot), store: SharedStorage.defaults)
    }

    /// I minuti dalla mezzanotte come Date di oggi, per il DatePicker.
    private var time: Binding<Date> {
        Binding(
            get: { Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: .now) ?? .now },
            set: {
                let c = Calendar.current.dateComponents([.hour, .minute], from: $0)
                minutes = (c.hour ?? 0) * 60 + (c.minute ?? 0)
            }
        )
    }

    var body: some View {
        HStack {
            Label(slot, systemImage: Slot.icon(slot))
            Spacer()
            if on {
                DatePicker(slot, selection: time, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .transition(.opacity)
            }
            Toggle(slot, isOn: $on).labelsHidden()
        }
        .animation(.snappy, value: on)
        .onChange(of: on) {
            Task {
                if on, !(await Reminders.requestPermission()) { denied = true }
                Reminders.schedule(store.meals)
            }
        }
        .onChange(of: minutes) { Reminders.schedule(store.meals) }
    }
}
