// Disegna l'icona Morsio (1024 px) nelle varianti chiara, scura e colorata, come nel logo scelto.
// Uso: swift make_icon.swift CalSnap/Assets.xcassets/AppIcon.appiconset
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let size = 1024
let s = CGFloat(size) / 100  // il logo è disegnato in un viewBox 100×100
func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}
let space = CGColorSpace(name: CGColorSpace.sRGB)!
func context() -> CGContext {
    let c = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    // coordinate come nell'SVG: origine in alto a sinistra
    c.translateBy(x: 0, y: CGFloat(size)); c.scaleBy(x: 1, y: -1)
    return c
}
func gradient(_ c: CGContext, _ from: UInt32, _ to: UInt32, start: CGPoint, end: CGPoint) {
    let g = CGGradient(colorsSpace: space, colors: [rgb(from), rgb(to)] as CFArray, locations: [0, 1])!
    c.drawLinearGradient(g, start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
}
func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> CGRect { CGRect(x: (x - r) * s, y: (y - r) * s, width: 2 * r * s, height: 2 * r * s) }

/// Piatto con anello delle calorie (75%) e morso in alto a destra.
func glyph(disc: (CGContext) -> Void, track: CGColor, arc: CGColor) -> CGImage {
    let c = context()
    c.saveGState(); c.addEllipse(in: circle(50, 50, 32)); c.clip(); disc(c); c.restoreGState()
    c.setLineWidth(6 * s); c.setLineCap(.round)
    c.setStrokeColor(track); c.strokeEllipse(in: circle(50, 50, 17))
    c.setStrokeColor(arc)
    c.addArc(center: CGPoint(x: 50 * s, y: 50 * s), radius: 17 * s, startAngle: -.pi / 2, endAngle: -.pi / 2 + 2 * .pi * 0.75, clockwise: false)
    c.strokePath()
    c.setBlendMode(.clear)
    for (x, y, r) in [(74.0, 28.0, 10.0), (66.0, 20.0, 7.0), (81.0, 38.0, 7.0)] { c.fillEllipse(in: circle(x, y, r)) }
    return c.makeImage()!
}

func icon(_ name: String, background: ((CGContext) -> Void)?, glyph g: CGImage) {
    let c = context()
    background?(c)
    c.saveGState(); c.translateBy(x: 0, y: CGFloat(size)); c.scaleBy(x: 1, y: -1)
    c.draw(g, in: CGRect(x: 0, y: 0, width: size, height: size)); c.restoreGState()
    let url = URL(fileURLWithPath: CommandLine.arguments[1]).appendingPathComponent(name)
    let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(dest, c.makeImage()!, nil)
    CGImageDestinationFinalize(dest)
}

// gradiente a 160°: dall'alto (leggermente a destra) verso il basso
let top = CGPoint(x: 0.67 * Double(size), y: 0), bottom = CGPoint(x: 0.33 * Double(size), y: Double(size))
icon("AppIcon.png", background: { gradient($0, 0x2FCB9C, 0x17A67D, start: top, end: bottom) },
     glyph: glyph(disc: { $0.setFillColor(rgb(0xFFFFFF)); $0.fill(circle(50, 50, 32)) }, track: rgb(0xDCEFE7), arc: rgb(0x17A67D)))
// scuro: sfondo trasparente, lo disegna iOS
icon("AppIcon-Dark.png", background: nil,
     glyph: glyph(disc: { gradient($0, 0x3FE0AE, 0x9BE35A, start: CGPoint(x: 18 * s, y: 18 * s), end: CGPoint(x: 82 * s, y: 82 * s)) },
                  track: rgb(0x0D1311, 0.18), arc: rgb(0x0D1311)))
// colorato: scala di grigi su nero, iOS applica la tinta
icon("AppIcon-Tinted.png", background: { $0.setFillColor(rgb(0x000000)); $0.fill(CGRect(x: 0, y: 0, width: size, height: size)) },
     glyph: glyph(disc: { $0.setFillColor(rgb(0xD6D6DB)); $0.fill(circle(50, 50, 32)) }, track: rgb(0x000000, 0.16), arc: rgb(0x000000)))
