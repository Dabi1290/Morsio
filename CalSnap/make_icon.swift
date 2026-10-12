// Disegna l'icona Morsio nelle varianti chiara, scura e colorata, come nel logo scelto.
// Uso: swift make_icon.swift CalSnap/Assets.xcassets/AppIcon.appiconset [../MorsioAndroid/app/src/main/res/drawable-nodpi]
// Il secondo percorso (facoltativo) riceve i livelli dell'icona adattiva Android.
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let space = CGColorSpace(name: CGColorSpace.sRGB)!
func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

/// Tela quadrata di `size` px in cui il logo (viewBox 100×100) occupa `glyph` px al centro.
struct Canvas {
    let size: Int
    let glyph: CGFloat
    var s: CGFloat { glyph / 100 }
    var off: CGFloat { (CGFloat(size) - glyph) / 2 }

    func context() -> CGContext {
        let c = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        // coordinate come nell'SVG: origine in alto a sinistra
        c.translateBy(x: 0, y: CGFloat(size)); c.scaleBy(x: 1, y: -1)
        return c
    }
    func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: off + x * s, y: off + y * s) }
    func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> CGRect { CGRect(x: off + (x - r) * s, y: off + (y - r) * s, width: 2 * r * s, height: 2 * r * s) }

    /// Piatto con anello delle calorie (75%) e morso in alto a destra.
    /// Con `cutRing` l'anello è ritagliato dal disco invece che disegnato sopra (icona a una sola tinta).
    func glyph(disc: (CGContext) -> Void, track: CGColor, arc: CGColor, cutRing: Bool = false) -> CGImage {
        let c = context()
        c.saveGState(); c.addEllipse(in: circle(50, 50, 32)); c.clip(); disc(c); c.restoreGState()
        c.setLineWidth(6 * s); c.setLineCap(.round)
        if cutRing { c.setBlendMode(.destinationOut) }  // il colore dice quanto togliere
        c.setStrokeColor(track); c.strokeEllipse(in: circle(50, 50, 17))
        c.setStrokeColor(arc)
        c.addArc(center: p(50, 50), radius: 17 * s, startAngle: -.pi / 2, endAngle: -.pi / 2 + 2 * .pi * 0.75, clockwise: false)
        c.strokePath()
        c.setBlendMode(.clear)
        for (x, y, r) in [(74.0, 28.0, 10.0), (66.0, 20.0, 7.0), (81.0, 38.0, 7.0)] { c.fillEllipse(in: circle(x, y, r)) }
        return c.makeImage()!
    }

    func write(_ dir: String, _ name: String, background: ((CGContext) -> Void)?, glyph g: CGImage) {
        let c = context()
        background?(c)
        c.saveGState(); c.translateBy(x: 0, y: CGFloat(size)); c.scaleBy(x: 1, y: -1)
        c.draw(g, in: CGRect(x: 0, y: 0, width: size, height: size)); c.restoreGState()
        let url = URL(fileURLWithPath: dir).appendingPathComponent(name)
        let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(dest, c.makeImage()!, nil)
        CGImageDestinationFinalize(dest)
    }
}

func gradient(_ c: CGContext, _ from: UInt32, _ to: UInt32, start: CGPoint, end: CGPoint) {
    let g = CGGradient(colorsSpace: space, colors: [rgb(from), rgb(to)] as CFArray, locations: [0, 1])!
    c.drawLinearGradient(g, start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
}

let white = { (c: CGContext) in c.setFillColor(rgb(0xFFFFFF)); c.fill(CGRect(x: 0, y: 0, width: 4096, height: 4096)) }

// iOS: 1024 px, il logo riempie la tela
let ios = Canvas(size: 1024, glyph: 1024)
let dir = CommandLine.arguments[1]
// gradiente a 160°: dall'alto (leggermente a destra) verso il basso
let top = CGPoint(x: 0.67 * 1024, y: 0), bottom = CGPoint(x: 0.33 * 1024, y: 1024)
ios.write(dir, "AppIcon.png", background: { gradient($0, 0x2FCB9C, 0x17A67D, start: top, end: bottom) },
          glyph: ios.glyph(disc: white, track: rgb(0xDCEFE7), arc: rgb(0x17A67D)))
// scuro: sfondo trasparente, lo disegna iOS
ios.write(dir, "AppIcon-Dark.png", background: nil,
          glyph: ios.glyph(disc: { gradient($0, 0x3FE0AE, 0x9BE35A, start: ios.p(18, 18), end: ios.p(82, 82)) },
                           track: rgb(0x0D1311, 0.18), arc: rgb(0x0D1311)))
// colorato: scala di grigi su nero, iOS applica la tinta
ios.write(dir, "AppIcon-Tinted.png", background: { $0.setFillColor(rgb(0x000000)); $0.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024)) },
          glyph: ios.glyph(disc: { $0.setFillColor(rgb(0xD6D6DB)); $0.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024)) },
                           track: rgb(0x000000, 0.16), arc: rgb(0x000000)))

// Android: livelli 108 dp (432 px); il logo sta nei 76 dp centrali, dentro la zona sicura della maschera
if CommandLine.arguments.count > 2 {
    let android = Canvas(size: 432, glyph: 304)
    let out = CommandLine.arguments[2]
    android.write(out, "ic_launcher_foreground.png", background: nil,
                  glyph: android.glyph(disc: white, track: rgb(0xDCEFE7), arc: rgb(0x17A67D)))
    // icone a tema (Android 13+): una sola tinta, l'anello resta trasparente
    android.write(out, "ic_launcher_monochrome.png", background: nil,
                  glyph: android.glyph(disc: white, track: rgb(0x000000, 0.45), arc: rgb(0x000000), cutRing: true))
}
