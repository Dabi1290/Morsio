import WidgetKit
import SwiftUI

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> DayEntry { .sample }

    func getSnapshot(in context: Context, completion: @escaping (DayEntry) -> Void) {
        let entry = DayEntry.now()
        completion(context.isPreview && entry.meals.isEmpty ? .sample : entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DayEntry>) -> Void) {
        let midnight = Calendar.current.startOfDay(for: .now.addingTimeInterval(86400))
        completion(Timeline(entries: [.now()], policy: .after(midnight)))
    }
}

struct CalSnapWidgetView: View {
    @Environment(\.widgetFamily) var family
    let entry: DayEntry

    var body: some View {
        switch family {
        case .accessoryCircular:
            Gauge(value: min(entry.progress, 1)) {
                Image(systemName: "flame.fill")
            } currentValueLabel: {
                Text("\(entry.kcal)")
            }
            .gaugeStyle(.accessoryCircular)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Label("\(entry.kcal) kcal", systemImage: "flame.fill").font(.headline)
                Text(entry.kcal <= entry.goal ? "\(entry.goal - entry.kcal) rimaste" : "\(entry.kcal - entry.goal) in più")
                    .font(.caption)
                Gauge(value: min(entry.progress, 1)) { EmptyView() }.gaugeStyle(.accessoryLinearCapacity)
            }
        case .accessoryInline:
            Label("\(entry.kcal) / \(entry.goal) kcal", systemImage: "flame.fill")
        case .systemMedium:
            MediumWidgetView(entry: entry)
        case .systemLarge:
            LargeWidgetView(entry: entry)
        default:
            SmallWidgetView(entry: entry)
        }
    }
}

@main
struct CalSnapWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "CalSnap", provider: Provider()) { entry in
            CalSnapWidgetView(entry: entry)
                .environment(\.locale, .it)
                .containerBackground(for: .widget) { WidgetBackground() }
                .widgetURL(URL(string: "calsnap://add"))
        }
        .configurationDisplayName("Calorie di oggi")
        .description("Kcal e macro di oggi. Tocca per aggiungere un pasto.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge,
                            .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}
