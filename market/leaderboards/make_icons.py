#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# Іконки лідербордів Orbit Dash, 512×512 PNG, без сторонніх бібліотек.
#
#   python3 make_icons.py [імена]  → best.png, combo.png, crashes.png, rich.png
#   (combo потребує swift і market/INTER/Inter_28pt-ExtraBold.ttf — див. text_mask.swift)
#
# Палітра — тема NEON з ThemeManager: фон 0E1024, кільце 2B3060, гравець 00E5FF,
# гем FFD54A, шип FF3D68. Шип — восьмипроменева зірка з assets/msdf/svg/spike.svg
# (вістря під 22.5° + k·45°, западини на 0.452 довжини).
# Малюємо полями відстаней: згладжений край і світіння — з тієї самої d.
# ─────────────────────────────────────────────────────────────────────────────
import math, os, struct, zlib

N = 512
HERE = os.path.dirname(os.path.abspath(__file__))


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


BG_EDGE = hexrgb("0B0D1F")
BG_MID  = hexrgb("1B2150")
RING    = hexrgb("2B3060")
RING_HI = hexrgb("5A63C8")
PLAYER  = hexrgb("00E5FF")
GEM     = hexrgb("FFD54A")
SPIKE   = hexrgb("FF3D68")
WHITE   = (1.0, 1.0, 1.0)
DARK    = (0.0, 0.0, 0.0)


# ── поля відстаней (у пікселях, <0 усередині) ───────────────────────────────
def sd_circle(x, y, cx, cy, r):
    return math.hypot(x - cx, y - cy) - r


def sd_ring(x, y, cx, cy, r, w):
    return abs(math.hypot(x - cx, y - cy) - r) - w / 2


def sd_arc(x, y, cx, cy, r, w, a0, a1):
    """Дуга кільця між кутами a0..a1 (радіани, проти год. стрілки, y — вгору)."""
    ang = math.atan2(cy - y, x - cx)
    mid, half = (a0 + a1) / 2, (a1 - a0) / 2
    d_ang = (ang - mid + math.pi) % (2 * math.pi) - math.pi
    if abs(d_ang) <= half:
        return abs(math.hypot(x - cx, y - cy) - r) - w / 2
    # за межами дуги — відстань до найближчого кінця
    best = 1e9
    for a in (a0, a1):
        ex, ey = cx + r * math.cos(a), cy - r * math.sin(a)
        best = min(best, math.hypot(x - ex, y - ey) - w / 2)
    return best


def sd_gem(x, y, cx, cy, r, rot=0.0):
    """Ромб (квадрат на куті) з ледь заокругленими кутами."""
    dx, dy = x - cx, y - cy
    c, s = math.cos(rot), math.sin(rot)
    dx, dy = dx * c - dy * s, dx * s + dy * c
    k = r * 0.12
    return (abs(dx) + abs(dy)) / math.sqrt(2) - (r / math.sqrt(2) - k) - k


HALF = math.pi / 8                      # 22.5°
INNER = 0.452


def sd_spike(x, y, cx, cy, tip, rot=math.pi / 8):
    """Восьмипроменева зірка як у spike.svg: радіус вістря tip."""
    dx, dy = x - cx, cy - y
    r = math.hypot(dx, dy)
    if r < 1e-6:
        return -tip * INNER
    ang = math.atan2(dy, dx) - rot
    sec = math.pi / 4
    k = math.floor(ang / sec + 0.5)
    a = ang - k * sec
    qx, qy = math.cos(a) * r, abs(math.sin(a)) * r
    rin = tip * INNER
    vx, vy = math.cos(HALF) * rin, math.sin(HALF) * rin
    ex, ey = vx - tip, vy
    ln = math.hypot(ex, ey)
    nx, ny = ey / ln, -ex / ln
    return (qx - tip) * nx + qy * ny


# ── композиція ───────────────────────────────────────────────────────────────
def cover(d):
    """Згладжене покриття з відстані в пікселях."""
    return max(0.0, min(1.0, 0.5 - d))


def glow(d, radius, strength):
    if d <= 0:
        return strength
    return strength * math.exp(-(d / radius) ** 2)


def over(dst, rgb, a):
    return tuple(dst[i] * (1 - a) + rgb[i] * a for i in range(3))


def add(dst, rgb, a):
    return tuple(min(1.0, dst[i] + rgb[i] * a) for i in range(3))


def background(x, y):
    t = min(1.0, math.hypot(x - N / 2, y - N / 2) / (N * 0.72))
    t = t * t * (3 - 2 * t)
    return tuple(BG_MID[i] * (1 - t) + BG_EDGE[i] * t for i in range(3))


def stars(x, y, seed):
    """Кілька дрібних зірок, як у AStarField."""
    out = 0.0
    for i in range(14):
        h = math.sin((i + 1) * 12.9898 + seed * 78.233) * 43758.5453
        sx = (h - math.floor(h)) * N
        h2 = math.sin((i + 1) * 39.3468 + seed * 11.135) * 24634.6345
        sy = (h2 - math.floor(h2)) * N
        d = math.hypot(x - sx, y - sy)
        if d < 4:
            out = max(out, (1 - d / 4) * 0.55)
    return out


# ── іконки ───────────────────────────────────────────────────────────────────
C = N / 2


def icon_best(x, y):
    col = background(x, y)
    col = add(col, WHITE, stars(x, y, 1))
    # орбіти
    col = over(col, RING, cover(sd_ring(x, y, C, C, 150, 7)))
    d_act = sd_ring(x, y, C, C, 150, 9)
    col = add(col, PLAYER, glow(d_act, 16, 0.10))
    col = over(col, RING, cover(sd_ring(x, y, C, C, 92, 6)))
    # гем на внутрішній, м'яч на зовнішній
    gx, gy = C - 92 * math.cos(math.radians(40)), C + 92 * math.sin(math.radians(40))
    dg = sd_gem(x, y, gx, gy, 26)
    col = add(col, GEM, glow(sd_circle(x, y, gx, gy, 16), 18, 0.55))
    col = over(col, GEM, cover(dg))
    bx, by = C + 150 * math.cos(math.radians(38)), C - 150 * math.sin(math.radians(38))
    db = sd_circle(x, y, bx, by, 38)
    col = add(col, PLAYER, glow(db, 30, 0.75))
    col = over(col, PLAYER, cover(db))
    col = over(col, WHITE, cover(sd_circle(x, y, bx - 11, by - 11, 9)) * 0.9)
    return col


# ── COMBO: текст Inter ExtraBold (маска з text_mask.swift) + світіння ─────────
def _mask(text, size, tracking, center_y):
    raw = os.path.join(HERE, "_" + text.lower() + ".raw")
    import subprocess
    subprocess.run(["swift", os.path.join(HERE, "text_mask.swift"), text, str(size), str(tracking),
                    str(center_y), raw], check=True)
    data = open(raw, "rb").read()
    os.remove(raw)
    return [[data[y * N + x] / 255 for x in range(N)] for y in range(N)]


def _blur(m, r, passes=3):
    """Box blur ×3 ≈ гаус; окремо по X і Y."""
    w = 2 * r + 1
    for _ in range(passes):
        out = []
        for row in m:
            acc, line = 0.0, [0.0] * N
            ext = [row[0]] * r + row + [row[-1]] * r
            acc = sum(ext[:w])
            for x in range(N):
                line[x] = acc / w
                acc += ext[x + w] - ext[x] if x + w < len(ext) else -ext[x]
            out.append(line)
        m = out
        cols = []
        for x in range(N):
            col = [m[y][x] for y in range(N)]
            ext = [col[0]] * r + col + [col[-1]] * r
            acc = sum(ext[:w])
            c2 = [0.0] * N
            for y in range(N):
                c2[y] = acc / w
                acc += ext[y + w] - ext[y] if y + w < len(ext) else -ext[y]
            cols.append(c2)
        m = [[cols[x][y] for x in range(N)] for y in range(N)]
    return m


_COMBO = {}


def _combo_layers():
    if not _COMBO:
        _COMBO["t"] = _mask("COMBO", 96, 0.06, 240)
        _COMBO["tg"] = _blur(_COMBO["t"], 8)
        _COMBO["x"] = _mask("x5", 62, 0.02, 338)
        _COMBO["xg"] = _blur(_COMBO["x"], 7)
    return _COMBO


def icon_combo(x, y):
    L = _combo_layers()
    px, py = min(N - 1, max(0, int(x + 0.5))), min(N - 1, max(0, int(y + 0.5)))
    col = background(x, y)
    col = add(col, WHITE, stars(x, y, 2))
    # орбіта позаду — впізнавано «наша» гра, але не заважає тексту
    col = over(col, RING, cover(sd_ring(x, y, C, C, 210, 6)) * 0.8)
    col = add(col, PLAYER, min(1.0, L["tg"][py][px] * 1.6) * 0.75)
    col = over(col, PLAYER, L["t"][py][px])
    col = add(col, GEM, min(1.0, L["xg"][py][px] * 1.6) * 0.7)
    col = over(col, GEM, L["x"][py][px])
    return col


SHARDS = [(-95, -60, 16, 0.3), (-120, 10, 12, 1.1), (-70, 75, 10, 0.7), (-30, -110, 9, 2.0), (-140, -90, 7, 0.4)]


def icon_crashes(x, y):
    col = background(x, y)
    col = add(col, WHITE, stars(x, y, 3))
    sx, sy = C + 60, C + 40
    ds = sd_spike(x, y, sx, sy, 125)
    col = add(col, SPIKE, glow(sd_circle(x, y, sx, sy, 70), 40, 0.55))
    col = over(col, SPIKE, cover(ds))
    col = over(col, DARK, cover(sd_circle(x, y, sx, sy, 22)) * 0.4)
    # спалах удару
    fx, fy = C - 70, C - 40
    dfl = sd_spike(x, y, fx, fy, 62, rot=0.3)
    col = add(col, WHITE, glow(sd_circle(x, y, fx, fy, 34), 26, 0.45))
    col = over(col, WHITE, cover(dfl) * 0.8)
    # уламки м'яча
    for (ox, oy, r, rot) in SHARDS:
        dsh = sd_gem(x, y, fx + ox, fy + oy, r, rot)
        col = add(col, PLAYER, glow(sd_circle(x, y, fx + ox, fy + oy, r * 0.5), 10, 0.45))
        col = over(col, PLAYER, cover(dsh))
    db = sd_circle(x, y, fx, fy, 30)
    col = add(col, PLAYER, glow(db, 24, 0.6))
    col = over(col, PLAYER, cover(db))
    return col


def icon_rich(x, y):
    col = background(x, y)
    col = add(col, WHITE, stars(x, y, 4))
    for (ox, oy, r, a) in ((-150, 120, 38, 0.85), (150, 110, 32, 0.8), (125, -150, 24, 0.65), (-140, -135, 20, 0.6)):
        d = sd_gem(x, y, C + ox, C + oy, r)
        col = add(col, GEM, glow(sd_circle(x, y, C + ox, C + oy, r * 0.55), 18, 0.45 * a))
        col = over(col, GEM, cover(d) * a)
    dg = sd_gem(x, y, C, C - 6, 128)
    col = add(col, GEM, glow(sd_circle(x, y, C, C - 6, 80), 50, 0.6))
    col = over(col, GEM, cover(dg))
    # грань і відблиск
    col = over(col, WHITE, cover(sd_gem(x, y, C - 30, C - 40, 34)) * 0.35)
    return col


def write_png(path, fn, ss=2):
    rows = []
    step = 1 / ss
    for py in range(N):
        row = bytearray([0])
        for px in range(N):
            r = g = b = 0.0
            for sy in range(ss):
                for sx in range(ss):
                    c = fn(px + (sx + 0.5) * step - 0.5, py + (sy + 0.5) * step - 0.5)
                    r += c[0]; g += c[1]; b += c[2]
            k = ss * ss
            row += bytes((int(r / k * 255 + .5), int(g / k * 255 + .5), int(b / k * 255 + .5)))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 2, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    print("ok", path)


if __name__ == "__main__":
    import sys
    only = set(sys.argv[1:])
    for name, fn in (("best", icon_best), ("combo", icon_combo), ("crashes", icon_crashes), ("rich", icon_rich)):
        if not only or name in only:
            write_png(os.path.join(HERE, name + ".png"), fn)
