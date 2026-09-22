import AppKit
import CoreText

// render-code <out.png> <width> <height> <fontsize> <title> <file.txt>
// Картка з кодом для девлогу: темна панель зі скругленням, заголовок-файл,
// моноширинний Menlo Bold, проста підсвітка (ключові слова, числа, коментарі).
// Прозорий фон навколо панелі — кладеться overlay-ем на будь-який кадр.
let a = CommandLine.arguments
let out = a[1]; let W = Int(a[2])!; let H = Int(a[3])!; let size = CGFloat(Double(a[4])!)
let title = a[5]
let lines = (try! String(contentsOfFile: a[6], encoding: .utf8)).components(separatedBy: "\n")

let font  = NSFont(name: "Menlo-Bold", size: size) ?? NSFont.monospacedSystemFont(ofSize: size, weight: .bold)
let tfont = NSFont(name: "Menlo-Regular", size: size * 0.78) ?? font
let cText = NSColor(srgbRed: 0.90, green: 0.92, blue: 1.00, alpha: 1)
let cKey  = NSColor(srgbRed: 0.75, green: 0.48, blue: 1.00, alpha: 1)      // C07BFF — колір магніта
let cNum  = NSColor(srgbRed: 1.00, green: 0.84, blue: 0.29, alpha: 1)      // FFD54A — колір gem ×2
let cCom  = NSColor(srgbRed: 0.45, green: 0.50, blue: 0.62, alpha: 1)
let cFn   = NSColor(srgbRed: 0.30, green: 0.90, blue: 1.00, alpha: 1)      // 00E5FF — м'яч NEON
let keywords: Set<String> = ["uniform", "varying", "float", "vec2", "vec3", "vec4", "int", "void", "for", "if", "break",
                             "return", "const", "val", "var", "fun", "class", "override", "private", "true", "false"]
let fns: Set<String> = ["length", "mix", "clamp", "max", "smoothstep", "stop", "VfxTexture", "RadialGradientEffect"]

let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: W, pixelsHigh: H, bitsPerSample: 8, samplesPerPixel: 4,
                           hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: W, height: H)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSColor.clear.set(); NSRect(x: 0, y: 0, width: W, height: H).fill()

// панель
let pad: CGFloat = 36
let panel = NSRect(x: pad, y: pad, width: CGFloat(W) - 2 * pad, height: CGFloat(H) - 2 * pad)
let shadow = NSShadow(); shadow.shadowColor = NSColor.black.withAlphaComponent(0.7); shadow.shadowBlurRadius = 30; shadow.shadowOffset = NSSize(width: 0, height: -8)
shadow.set()
NSColor(srgbRed: 0.055, green: 0.06, blue: 0.14, alpha: 0.96).setFill()
NSBezierPath(roundedRect: panel, xRadius: 28, yRadius: 28).fill()
NSShadow().set()
// заголовок-файл і три «кнопки»
let barH: CGFloat = size * 1.9
NSColor(srgbRed: 0.09, green: 0.10, blue: 0.20, alpha: 1).setFill()
let barPath = NSBezierPath(roundedRect: NSRect(x: panel.minX, y: panel.maxY - barH, width: panel.width, height: barH), xRadius: 28, yRadius: 28)
barPath.appendRect(NSRect(x: panel.minX, y: panel.maxY - barH, width: panel.width, height: barH / 2))
barPath.fill()
for (i, c) in [NSColor(srgbRed: 1, green: 0.37, blue: 0.34, alpha: 1), NSColor(srgbRed: 1, green: 0.74, blue: 0.18, alpha: 1), NSColor(srgbRed: 0.16, green: 0.78, blue: 0.25, alpha: 1)].enumerated() {
    c.setFill()
    NSBezierPath(ovalIn: NSRect(x: panel.minX + 26 + CGFloat(i) * (size * 0.9), y: panel.maxY - barH / 2 - size * 0.28, width: size * 0.56, height: size * 0.56)).fill()
}
let tAttrs: [NSAttributedString.Key: Any] = [.font: tfont, .foregroundColor: cCom]
let ts = NSAttributedString(string: title, attributes: tAttrs)
ts.draw(at: NSPoint(x: panel.midX - ts.size().width / 2, y: panel.maxY - barH / 2 - ts.size().height / 2))

// рядки коду
func colored(_ line: String) -> NSAttributedString {
    let s = NSMutableAttributedString(string: line, attributes: [.font: font, .foregroundColor: cText])
    let ns = line as NSString
    if let r = line.range(of: "//") {
        let loc = line.distance(from: line.startIndex, to: r.lowerBound)
        s.addAttribute(.foregroundColor, value: cCom, range: NSRange(location: loc, length: ns.length - loc))
        return s
    }
    let re = try! NSRegularExpression(pattern: "[A-Za-z_][A-Za-z0-9_]*|[0-9]+(\\.[0-9]+)?f?")
    for m in re.matches(in: line, range: NSRange(location: 0, length: ns.length)) {
        let w = ns.substring(with: m.range)
        if keywords.contains(w) { s.addAttribute(.foregroundColor, value: cKey, range: m.range) }
        else if fns.contains(w) { s.addAttribute(.foregroundColor, value: cFn, range: m.range) }
        else if w.first!.isNumber { s.addAttribute(.foregroundColor, value: cNum, range: m.range) }
    }
    return s
}
let lineH = size * 1.42
var y = panel.maxY - barH - size * 1.3
for line in lines {
    colored(line).draw(at: NSPoint(x: panel.minX + size * 1.2, y: y - lineH * 0.75))
    y -= lineH
}
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: out))
print("ok", out, "lines:", lines.count)
