#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# Пакувальник MSDF-полів у libGDX-атлас. БЕЗ TexturePacker.
#
# Чому не TexturePacker: його CLI — платна фіча Essential-режиму, і за її
# використання він ФАРБУЄ спрайти червоним (перевірено побайтово: 4087/4096
# текселів зіпсовано). GUI-Publish безкоштовний, але це зайвий клік. А наше
# пакування тривіальне: однакові квадрати, полиці, 2px відступи.
#
# Поля — ДАНІ, не картинки: копіюємо текселі байт-у-байт, жодного premultiply,
# жодного trim, жодного квантування. Вивід: msdf.png + msdf.atlas прямо в
# app/src/main/assets/atlas/.
# ─────────────────────────────────────────────────────────────────────────────
import os, struct, zlib, glob, sys

HERE    = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(HERE, "png")
# Конфіг з gen-msdf.command (env); дефолти — цей проєкт
_out    = os.environ.get("MSDF_OUT_DIR", os.path.join("..", "..", "Orbit Dash", "app", "src", "main", "assets", "atlas"))
OUT_DIR = _out if os.path.isabs(_out) else os.path.normpath(os.path.join(HERE, _out))
NAME    = os.environ.get("MSDF_ATLAS_NAME", "msdf")
PAD, BORDER = 2, 2
MAX_PAGE    = 4096   # стеля сторінки; не влізло — нова сторінка (msdf2.png, msdf3.png...)

def read_png(path):
    f = open(path, "rb").read()
    pos, idat, w, h = 8, b"", None, None
    while pos < len(f):
        ln  = struct.unpack(">I", f[pos:pos+4])[0]
        typ = f[pos+4:pos+8]
        d   = f[pos+8:pos+8+ln]
        if typ == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", d[:10])
            assert bd == 8 and ct == 6, f"{path}: очікую RGBA8 (bitdepth 8, colortype 6)"
        elif typ == b"IDAT":
            idat += d
        pos += 12 + ln
    raw = zlib.decompress(idat)
    stride = w * 4
    out, prev, p = bytearray(), bytearray(stride), 0
    for _ in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        for i in range(stride):
            a = line[i-4] if i >= 4 else 0
            b = prev[i]
            c = prev[i-4] if i >= 4 else 0
            if   ft == 1: line[i] = (line[i] + a) & 255
            elif ft == 2: line[i] = (line[i] + b) & 255
            elif ft == 3: line[i] = (line[i] + (a + b)//2) & 255
            elif ft == 4:
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out += line; prev = line
    return w, h, bytes(out)

def write_png(path, w, h, pixels):
    def chunk(typ, data):
        c = struct.pack(">I", len(data)) + typ + data
        return c + struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff)
    stride = w * 4
    raw = b"".join(b"\x00" + pixels[y*stride:(y+1)*stride] for y in range(h))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    open(path, "wb").write(png)

def main():
    files = sorted(glob.glob(os.path.join(SRC_DIR, "*.png")))
    if not files:
        sys.exit("png/ порожня — спершу запусти генерацію (gen-msdf.sh)")
    sprites = []
    for p in files:
        w, h, px = read_png(p)
        assert w <= MAX_PAGE - 2*BORDER and h <= MAX_PAGE - 2*BORDER, f"{p}: фігура більша за сторінку {MAX_PAGE}"
        sprites.append((os.path.splitext(os.path.basename(p))[0], w, h, px))
    # полиці: сортуємо за висотою; сторінка заповнилась до MAX_PAGE → нова
    sprites.sort(key=lambda s: -s[2])
    pages = [{"placed": [], "w": 0, "h": 0}]
    x, y, shelf_h = BORDER, BORDER, 0
    for name, w, h, px in sprites:
        if x + w + BORDER > MAX_PAGE and x > BORDER:            # ряд повний → нова полиця
            x, y, shelf_h = BORDER, y + shelf_h + PAD, 0
        if y + h + BORDER > MAX_PAGE:                           # сторінка повна → нова
            pages.append({"placed": [], "w": 0, "h": 0})
            x, y, shelf_h = BORDER, BORDER, 0
        pg = pages[-1]
        pg["placed"].append((name, x, y, w, h, px))
        pg["w"] = max(pg["w"], x + w + BORDER)
        shelf_h = max(shelf_h, h)
        pg["h"] = y + shelf_h + BORDER
        x += w + PAD

    # Кілька сторінок → усі ОДНАКОВОГО розміру: спільний ефект тримає один
    # u_unitRange = pxRange/розмір, і він мусить пасувати кожній сторінці.
    if len(pages) > 1:
        uw = max(pg["w"] for pg in pages)
        uh = max(pg["h"] for pg in pages)
        for pg in pages: pg["w"], pg["h"] = uw, uh
        print(f"! {len(pages)} сторінок по {uw}x{uh} — спільний u_unitRange збережено")

    os.makedirs(OUT_DIR, exist_ok=True)
    lines = []
    for i, pg in enumerate(pages):
        page_w, page_h = pg["w"], pg["h"]
        page = bytearray(page_w * page_h * 4)                   # прозорий фон
        for name, sx, sy, w, h, px in pg["placed"]:
            for row in range(h):
                dst = ((sy + row) * page_w + sx) * 4
                page[dst:dst + w*4] = px[row*w*4:(row+1)*w*4]
        png_name = NAME + ("" if i == 0 else str(i + 1)) + ".png"
        write_png(os.path.join(OUT_DIR, png_name), page_w, page_h, bytes(page))
        if i > 0: lines.append("")                              # порожній рядок між сторінками
        lines += [png_name, f"size: {page_w}, {page_h}",
                  "format: RGBA8888", "filter: Linear, Linear", "repeat: none"]
        for name, sx, sy, w, h, _ in pg["placed"]:
            lines += [name, "  rotate: false", f"  xy: {sx}, {sy}",
                      f"  size: {w}, {h}", f"  orig: {w}, {h}",
                      "  offset: 0, 0", "  index: -1"]
    open(os.path.join(OUT_DIR, NAME + ".atlas"), "w").write("\n".join(lines) + "\n")
    total = sum(len(pg["placed"]) for pg in pages)
    print(f"✓ {total} фігур → {len(pages)} стор. → {OUT_DIR}/{NAME}.atlas")

if __name__ == "__main__":
    main()
