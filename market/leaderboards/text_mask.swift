// ─────────────────────────────────────────────────────────────────────────────
// Маска тексту для іконки лідерборда: 512×512, 1 байт на піксель (покриття),
// шрифт — Inter ExtraBold (той самий, що в заголовку гри).
//
//   swift text_mask.swift "COMBO" 118 0.10 256 out.raw
//     текст, розмір (px), трекінг (частка розміру), базова лінія від низу (px), файл
// ─────────────────────────────────────────────────────────────────────────────
import Foundation
import CoreText
import CoreGraphics

let args = CommandLine.arguments
let text     = args[1]
let size     = CGFloat(Double(args[2])!)
let tracking = CGFloat(Double(args[3])!)
let centerY  = CGFloat(Double(args[4])!)
let outPath  = args[5]

let N = 512
let fontURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
    .appendingPathComponent("../content/tools/fonts/Inter_28pt-ExtraBold.ttf").standardized
var err: Unmanaged<CFError>?
CTFontManagerRegisterFontsForURL(fontURL as CFURL, .process, &err)

let descs = CTFontManagerCreateFontDescriptorsFromURL(fontURL as CFURL) as! [CTFontDescriptor]
let font = CTFontCreateWithFontDescriptor(descs[0], size, nil)

let attrs: [NSAttributedString.Key: Any] = [
    NSAttributedString.Key(kCTFontAttributeName as String): font,
    NSAttributedString.Key(kCTKernAttributeName as String): size * tracking,
    NSAttributedString.Key(kCTForegroundColorAttributeName as String): CGColor(gray: 1, alpha: 1),
]
let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
let bounds = CTLineGetImageBounds(line, nil)

var pixels = [UInt8](repeating: 0, count: N * N)
let ctx = CGContext(data: &pixels, width: N, height: N, bitsPerComponent: 8, bytesPerRow: N,
                    space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue)!
ctx.setAllowsAntialiasing(true)
ctx.setShouldSmoothFonts(false)
// центр рядка по X; по Y — центр видимих гліфів на centerY (від верху)
let x = (CGFloat(N) - bounds.width) / 2 - bounds.minX
let y = (CGFloat(N) - centerY) - bounds.height / 2 - bounds.minY
ctx.textPosition = CGPoint(x: x, y: y)
CTLineDraw(line, ctx)

// CGContext малює знизу вгору, а пікселі в буфері — згори вниз: уже правильний порядок рядків
FileManager.default.createFile(atPath: outPath, contents: Data(pixels))
print("ok", outPath, Int(bounds.width), "x", Int(bounds.height))
