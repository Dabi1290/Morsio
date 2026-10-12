import Foundation
import UIKit

struct AppError: LocalizedError {
    let errorDescription: String?
    init(_ message: String) { errorDescription = message }
}

/// Valori inseriti a mano (pasti senza elenco di alimenti).
struct Nutrition {
    var name: String
    var kcal: Double
    var protein: Double
    var carbs: Double
    var fat: Double
}

struct AIMeal {
    var name: String
    var items: [FoodItem]
}

enum Gemini {
    private static let prompt = """
    Sei un nutrizionista. Analizza il pasto nella foto e/o nella descrizione. \
    Elenca ogni alimento riconoscibile (es. pasta, sugo, olio, formaggio) con il peso stimato in grammi \
    e calorie e macronutrienti riferiti a quel peso. Dai al pasto un nome breve in italiano; \
    anche i nomi degli alimenti in italiano.
    """

    private static let schema: [String: Any] = [
        "type": "object",
        "properties": [
            "name": ["type": "string"],
            "items": [
                "type": "array",
                "items": [
                    "type": "object",
                    "properties": [
                        "name": ["type": "string"], "grams": ["type": "number"], "kcal": ["type": "number"],
                        "protein": ["type": "number"], "carbs": ["type": "number"], "fat": ["type": "number"],
                    ],
                    "required": ["name", "grams", "kcal", "protein", "carbs", "fat"],
                ],
            ],
        ],
        "required": ["name", "items"],
    ]

    /// Se il modello scelto è sovraccarico (503) o fuori quota (429) si passa a Flash-Lite.
    private static let fallbackModel = "gemini-flash-lite-latest"

    static func analyze(photo: Data?, note: String) async throws -> AIMeal {
        let d = SharedStorage.defaults
        guard let key = d.string(forKey: "geminiKey"), !key.isEmpty else {
            throw AppError("Inserisci la chiave Gemini nelle impostazioni")
        }
        let model = d.string(forKey: "geminiModel").flatMap { $0.isEmpty ? nil : $0 } ?? "gemini-flash-latest"

        var input: [[String: Any]] = [["type": "text", "text": prompt + (note.isEmpty ? "" : "\nDescrizione: \(note)")]]
        if let photo {
            input.append(["type": "image", "mime_type": "image/jpeg", "data": photo.base64EncodedString()])
        }

        var lastError = AppError("Gemini non disponibile")
        for m in model == fallbackModel ? [model] : [model, fallbackModel] {
            let body: [String: Any] = [
                "model": m,
                "input": input,
                "response_format": ["type": "text", "mime_type": "application/json", "schema": schema],
            ]
            var req = URLRequest(url: URL(string: "https://generativelanguage.googleapis.com/v1beta/interactions")!)
            req.httpMethod = "POST"
            req.timeoutInterval = 60
            req.setValue(key, forHTTPHeaderField: "x-goog-api-key")
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.setValue("2026-05-20", forHTTPHeaderField: "Api-Revision")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)

            let (data, resp) = try await URLSession.shared.data(for: req)
            let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
            if status == 200 { return try parse(data) }
            struct ErrorBody: Decodable { struct E: Decodable { let message: String? }; let error: E }
            let message = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.error.message
            lastError = AppError("Gemini (\(status)): \(message ?? "errore sconosciuto")")
            guard status == 503 || status == 429 else { throw lastError }
        }
        throw lastError
    }

    /// Il testo JSON sta nello step "model_output" della risposta.
    static func parse(_ data: Data) throws -> AIMeal {
        struct Response: Decodable {
            struct Step: Decodable {
                struct Content: Decodable { let type: String; let text: String? }
                let type: String
                let content: [Content]?
            }
            let steps: [Step]
        }
        let steps = try JSONDecoder().decode(Response.self, from: data).steps
        guard let text = steps.first(where: { $0.type == "model_output" })?.content?.first(where: { $0.type == "text" })?.text else {
            throw AppError("Gemini non ha restituito una stima")
        }
        struct Raw: Decodable {
            struct Item: Decodable { let name: String; let grams, kcal, protein, carbs, fat: Double }
            let name: String
            let items: [Item]
        }
        let raw = try JSONDecoder().decode(Raw.self, from: Data(text.utf8))
        guard !raw.items.isEmpty else { throw AppError("L'IA non ha riconosciuto alimenti") }
        return AIMeal(name: raw.name, items: raw.items.map {
            FoodItem(name: $0.name, grams: $0.grams, kcal: $0.kcal, protein: $0.protein, carbs: $0.carbs, fat: $0.fat)
        })
    }
}

enum OpenFoodFacts {
    /// Il prodotto come alimento da 100 g (i grammi si cambiano poi nell'editor); nil se non è nel database.
    static func product(barcode: String) async throws -> FoodItem? {
        guard !barcode.isEmpty, barcode.allSatisfy(\.isASCII), barcode.allSatisfy(\.isNumber) else {
            throw AppError("Codice a barre non valido")
        }
        var req = URLRequest(url: URL(string: "https://world.openfoodfacts.org/api/v2/product/\(barcode)?fields=product_name,nutriments")!)
        req.setValue("Morsio/1.0 (uso personale)", forHTTPHeaderField: "User-Agent")
        let (data, _) = try await URLSession.shared.data(for: req)
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw AppError("Risposta di Open Food Facts non valida")
        }
        guard let product = json["product"] as? [String: Any] else { return nil }
        let n = product["nutriments"] as? [String: Any] ?? [:]
        func value(_ key: String) -> Double { (n[key] as? NSNumber)?.doubleValue ?? 0 }
        return FoodItem(name: product["product_name"] as? String ?? "Prodotto", grams: 100,
                        kcal100: value("energy-kcal_100g"), protein100: value("proteins_100g"),
                        carbs100: value("carbohydrates_100g"), fat100: value("fat_100g"), barcode: barcode)
    }
}

enum Uploader {
    /// nil se il server non è configurato.
    private static func request(_ id: UUID, method: String, query: [URLQueryItem] = []) -> URLRequest? {
        let d = SharedStorage.defaults
        guard let base = d.string(forKey: "serverURL"), !base.isEmpty,
              var comps = URLComponents(string: base.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/upload") else { return nil }
        comps.queryItems = [URLQueryItem(name: "id", value: id.uuidString)] + query
        comps.percentEncodedQuery = comps.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        guard let url = comps.url else { return nil }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue(d.string(forKey: "uploadKey"), forHTTPHeaderField: "X-Key")
        return req
    }

    static func upload(_ meal: Meal) async throws {
        guard var req = request(meal.id, method: "POST", query: [
            URLQueryItem(name: "name", value: meal.name),
            URLQueryItem(name: "kcal", value: String(meal.kcal)),
            URLQueryItem(name: "ts", value: String(Int(meal.date.timeIntervalSince1970))),
        ]) else { throw AppError("Server non configurato") }
        req.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        let photo = meal.hasPhoto ? (try? Data(contentsOf: SharedStorage.photoURL(meal.id))) : nil
        let (_, resp) = try await URLSession.shared.upload(for: req, from: photo ?? Data())
        guard (resp as? HTTPURLResponse)?.statusCode == 201 else { throw AppError("Upload fallito") }
    }

    static func delete(_ id: UUID) async throws {
        guard let req = request(id, method: "DELETE") else { throw AppError("Server non configurato") }
        let (_, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 204 else { throw AppError("Cancellazione fallita") }
    }
}

extension UIImage {
    /// Ridimensiona il lato lungo a `maxSide` px e comprime in JPEG.
    func jpeg(maxSide: CGFloat, quality: CGFloat) -> Data {
        let scale = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).jpegData(withCompressionQuality: quality) { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}
