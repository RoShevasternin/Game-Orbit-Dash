import AppKit

// render-svg <in.svg> <out.png> <size>
// SVG із Figma → PNG з ПРОЗОРИМ тлом, вписаний у квадрат size. qlmanage кладе
// SVG на біле — для карток на темному тлі не годиться; NSImage читає SVG сам.
let a = CommandLine.arguments
let size = Int(a[3])!
guard let img = NSImage(contentsOf: URL(fileURLWithPath: a[1])) else { fatalError("SVG не читається: \(a[1])") }
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8,
                           samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                           bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: size, height: size)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSGraphicsContext.current?.imageInterpolation = .high
NSColor.clear.set(); NSRect(x: 0, y: 0, width: size, height: size).fill()
let s = img.size
let k = min(CGFloat(size) / s.width, CGFloat(size) / s.height)
let w = s.width * k, h = s.height * k
img.draw(in: NSRect(x: (CGFloat(size) - w) / 2, y: (CGFloat(size) - h) / 2, width: w, height: h),
         from: NSRect(origin: .zero, size: s), operation: .sourceOver, fraction: 1)
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: a[2]))
print("ok", a[2], Int(w), "×", Int(h))
