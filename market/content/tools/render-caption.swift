import AppKit
import CoreText

// render-caption <out.png> <width> <height> <fontsize> [--color RRGGBB] <line1> [line2] [line3]
// Прозорий PNG з текстом по центру: Inter ExtraBold, м'яка тінь. Емодзі — системний шрифт.
// --color — колір підпису (за умовчанням білий): так підпис буста носить колір буста.
let a = CommandLine.arguments
let out = a[1]; let W = Int(a[2])!; let H = Int(a[3])!; let size = CGFloat(Double(a[4])!)
var rest = Array(a[5...])
var tint = NSColor.white
if rest.count >= 2, rest[0] == "--color" {
    let h = rest[1]
    let v = UInt32(h, radix: 16) ?? 0xFFFFFF
    tint = NSColor(srgbRed: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255,
                   blue: CGFloat(v & 0xFF) / 255, alpha: 1)
    rest = Array(rest[2...])
}
let lines = rest
let fontURL = URL(fileURLWithPath: "/Users/admin/Apps/Game Orbit Dash/market/content/tools/fonts/Inter_28pt-ExtraBold.ttf")
CTFontManagerRegisterFontsForURL(fontURL as CFURL, .process, nil)
let font = NSFont(name: "Inter28pt-ExtraBold", size: size) ?? NSFont(name: "Inter 28pt ExtraBold", size: size) ?? NSFont.boldSystemFont(ofSize: size)
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: W, pixelsHigh: H, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: W, height: H)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSColor.clear.set(); NSRect(x: 0, y: 0, width: W, height: H).fill()
let shadow = NSShadow(); shadow.shadowColor = NSColor.black.withAlphaComponent(0.85); shadow.shadowBlurRadius = 14; shadow.shadowOffset = NSSize(width: 0, height: -3)
let para = NSMutableParagraphStyle(); para.alignment = .center
let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: tint, .shadow: shadow, .paragraphStyle: para]
let lineH = size * 1.22
let total = lineH * CGFloat(lines.count)
var y = (CGFloat(H) + total) / 2 - lineH
for line in lines {
    let s = NSAttributedString(string: line, attributes: attrs)
    s.draw(in: NSRect(x: 0, y: y, width: CGFloat(W), height: lineH))
    y -= lineH
}
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
print("ok", out, "font:", font.fontName)
