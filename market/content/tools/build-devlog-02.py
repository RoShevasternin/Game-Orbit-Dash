#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# DEVLOG 02 для @lewydo_game — монтаж із сирих записів телефона (720×1650, 60 fps).
#   python3 build-devlog-02.py <scratch> <out.mp4> <cover.png>
#
# Сітка: 130.0 BPM, доля 0.4615 с, такт 1.8462 с — усі тривалості в тактах.
# Ролик — 16 тактів = 29.54 с; трек ріжеться з 20.769 с (такт 11), бас входить
# рівно на 1 такт пізніше, тобто на 1.846 с ролика — там і монтажний дроп.
#
# Що нового проти DEVLOG 01:
#   • ТРЕКЕР М'ЯЧА. Крупні плани не «зум у центр поля», а crop, що їде за м'ячем:
#     позиція шукається по кольору гравця теми, згладжується і виливається
#     в кусково-лінійний вираз для фільтра crop (сума обрізаних пандусів).
#   • КОЛЬОРОВІ ПІДПИСИ. render-caption приймає --color: назва буста йде
#     кольором буста з BoostCatalog — підпис і шестикутник в кадрі одного кольору.
# ─────────────────────────────────────────────────────────────────────────────
import subprocess, sys, os, json

SCR, OUT, COVER = sys.argv[1], sys.argv[2], sys.argv[3]
FF, FP = "/opt/homebrew/bin/ffmpeg", "/opt/homebrew/bin/ffprobe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # market/content
SRC  = f"{ROOT}/source"
SEG, CAP, CAPBIN = f"{SCR}/video/seg", f"{SCR}/video/cap", f"{SCR}/video/caption"
os.makedirs(SEG, exist_ok=True); os.makedirs(CAP, exist_ok=True)

# ── сировина ────────────────────────────────────────────────────────────────
OLD   = f"{SRC}/footage/gameplay-90s-combo-x5.mp4"          # збірка DEVLOG 01: кільце комбо БІЛЕ, правого HUD немає
NEON  = f"{SRC}/footage/gameplay-90s-neon-boosts.mp4"       # патчі 55–64, тема NEON, автопілот, буст кожні 8 с
SYNTH = f"{SRC}/footage/gameplay-75s-synth-boosts.mp4"      # те саме, тема SYNTH
STAND = f"{SRC}/footage/stand-progress-bar.mp4"             # TestScreen: ABarProgress із ковзною текстурою

BEAT = 60 / 130.0; BAR = 4 * BEAT
ENC  = ["-an", "-c:v", "libx264", "-crf", "16", "-preset", "fast", "-pix_fmt", "yuv420p"]
PRE  = "fps=60,setpts=PTS-STARTPTS"
FULL = "crop=720:1280:0:0,scale=1080:1920:flags=lanczos"
HUD  = "crop=480:853:240:0,scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"   # рахунок + щит + панель буста
FIELD = (360, 600)                                          # центр поля в кадрі 720×1650
BG    = "0E0F23"                                            # фон стенда прогресу

# кольори з BoostCatalog — підпис носить колір буста
C_MAGNET, C_FRENZY, C_SLOW, C_SHIELD = "C07BFF", "FFD54A", "FFFFFF", "4DD9FF"
# кольори гравця з ThemeManager: ними трекер упізнає м'яч
P_NEON, P_SYNTH = "00E5FF", "FF3EC8"

def run(*a):
    r = subprocess.run([FF, "-v", "error", "-y", *a], capture_output=True, text=True)
    if r.returncode: sys.exit(f"FFMPEG FAIL: {' '.join(map(str, a))[:400]}\n{r.stderr[-700:]}")
def dur(p):
    return float(subprocess.run([FP, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                                capture_output=True, text=True).stdout.strip())
def cap(name, size, *lines, color=None, w=1080, h=None):
    h = h or int(size * 1.35 * len(lines))
    p = f"{CAP}/{name}.png"
    args = [CAPBIN, p, str(w), str(h), str(size)] + (["--color", color] if color else []) + list(lines)
    subprocess.run(args, check=True, capture_output=True)
    return p

# ── трекер м'яча ────────────────────────────────────────────────────────────
#  Шукаємо найяскравіші пікселі кольору гравця у смузі поля, беремо кластер,
#  найближчий до попереднього кадру (м'яч рухається плавно, геми й шипи — ні),
#  і згладжуємо ковзним середнім. Результат кешуємо: декод 60 fps небезкоштовний.
TSC = 4                                                     # даунскейл пошуку
TW, TH = 720 // TSC, 1650 // TSC

def ball_path(src, ss, real, color):
    key = f"{CAP}/track-{os.path.basename(src)}-{ss:.2f}-{real:.2f}.json"
    if os.path.exists(key): return json.load(open(key))
    pr, pg, pb = (int(color[i:i+2], 16) for i in (0, 2, 4))
    p = subprocess.run([FF, "-v", "error", "-ss", f"{ss:.3f}", "-t", f"{real:.3f}", "-i", src,
                        "-vf", f"fps=60,scale={TW}:{TH}", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                       capture_output=True)
    b, fs = p.stdout, TW * TH * 3
    pts, prev = [], None
    for f in range(len(b) // fs):
        base, cand = f * fs, []
        for y in range(60, 260):                            # смуга поля, без HUD і без порожнього низу
            row = base + y * TW * 3
            for x in range(2, TW - 2):
                i = row + x * 3
                r, g, bl = b[i], b[i+1], b[i+2]
                if max(r, g, bl) < 200: continue
                if abs(r-pr) + abs(g-pg) + abs(bl-pb) > 150 and not (r > 200 and g > 200 and bl > 200): continue
                cand.append((x, y, max(r, g, bl)))
        if not cand:
            pts.append(prev); continue
        if prev is None: cand.sort(key=lambda c: -c[2])
        else:            cand.sort(key=lambda c: (c[0] - prev[0]/TSC)**2 + (c[1] - prev[1]/TSC)**2)
        cx, cy = cand[0][0], cand[0][1]
        sx = sy = sw = 0
        for x, y, v in cand:                                # центроїд навколо обраного
            if (x - cx)**2 + (y - cy)**2 <= 100:
                w = v - 150; sx += x * w; sy += y * w; sw += w
        if sw: cx, cy = sx / sw, sy / sw
        prev = (cx * TSC, cy * TSC); pts.append(prev)
    out = []
    for i in range(len(pts)):                               # згладжування ±4 кадри
        w = [p for p in pts[max(0, i-4):i+5] if p] or [(FIELD[0], FIELD[1])]
        out.append((sum(p[0] for p in w) / len(w), sum(p[1] for p in w) / len(w)))
    json.dump(out, open(key, "w")); return out

def ramp(vals, step):
    """Кусково-лінійний вираз для crop: сума обрізаних пандусів, без вкладених if."""
    s = f"{vals[0]:.1f}"
    for i in range(1, len(vals)):
        d = vals[i] - vals[i-1]
        if abs(d) < 0.5: continue
        s += f"+({d:.1f})*clip((t-{(i-1)*step:.3f})/{step:.3f},0,1)"
    return f"'{s}'"

def track_crop(src, ss, real, color, w, h, every=6):
    """crop w×h, що їде за м'ячем. Контрольні точки — кожні `every` кадрів (0.1 с)."""
    pts = ball_path(src, ss, real, color)
    xs = [min(max(p[0] - w / 2, 0), 720 - w)  for p in pts[::every]]
    ys = [min(max(p[1] - h / 2, 0), 1650 - h) for p in pts[::every]]
    return f"crop={w}:{h}:x={ramp(xs, every/60)}:y={ramp(ys, every/60)}"

# ── сегмент ─────────────────────────────────────────────────────────────────
def slow(sp): return "setpts=PTS" if sp == 1 else f"setpts={1/sp:g}*PTS"
def punch(z, cx, cy, W=720, H=1280):
    w, h = round(W / z), round(H / z)
    x, y = min(max(cx - w // 2, 0), W - w), min(max(cy - h // 2, 0), H - h)
    return f"crop={w}:{h}:{x}:{y},scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"
def zoom(z0, z1, cx, cy, nframes):
    p = f"min(in/{nframes},1)"; ss = f"(3*pow({p},2)-2*pow({p},3))"
    return (f"zoompan=z='{z0}+({z1}-{z0})*{ss}':x='min(max({cx}-iw/zoom/2,0),iw-iw/zoom)'"
            f":y='min(max({cy}-ih/zoom/2,0),ih-ih/zoom)':d=1:s=1080x1920:fps=30,unsharp=5:5:0.4:5:5:0")

FPS = 30
_cursor = [0.0]                                             # позиція монтажу в тактах

def bars_to_frames(bars):
    """Ціла кількість кадрів так, щоб межа такту НЕ пливла: такт = 55.3846 кадру."""
    a, b = _cursor[0], _cursor[0] + bars
    n = round(b * BAR * FPS) - round(a * BAR * FPS)
    _cursor[0] = b
    return n

def seg(name, src, ss, real, speed, mid, bars, track=None, caps=(), flash=False,
        bloom=0.0, vignette=False, dim=0.0, desat=0.0, freeze=0.0, pad=None, dip_in=False, dip_out=False):
    """Один сегмент 1080×1920 30 fps. caps: (png, y, t_in, t_out)."""
    nf = bars_to_frames(bars); out_dur = nf / FPS
    out = f"{SEG}/{name}.mp4"
    # джерела беремо з запасом 10 %: ріже не `-t`, а рівно nf кадрів
    inputs = ["-ss", f"{ss:.3f}", "-t", f"{real * 1.1 + 0.1:.3f}", "-i", src]
    chain = PRE
    if track: chain += f",{track}"                          # трекінг рахується у часі ДЖЕРЕЛА, тому до сповільнення
    chain += f",{slow(speed)}"
    if freeze: chain += f",tpad=stop_mode=clone:stop_duration={freeze:.3f}"
    if desat:  chain += f",eq=saturation={1-desat:.2f}"
    chain += f",{mid},fps=30"
    fc, cur, n = f"[0:v]{chain}[v0];", "v0", 1
    if pad:                                                 # смужка на тлі кольору фону стенда
        y, col = pad
        fc += (f"color=c=0x{col}:s=1080x1920:d=99,fps=30[bgp];[bgp][{cur}]overlay=0:{y}:shortest=1[v{n}];")
        cur = f"v{n}"; n += 1
    if bloom:
        fc += (f"[{cur}]split[a][b];[b]lutyuv=y='if(gt(val,150),val,0)':u=128:v=128,gblur=sigma=16[g];"
               f"[g][a]blend=c0_mode=screen:c0_opacity={bloom}:c1_mode=normal:c1_opacity=0:c2_mode=normal:c2_opacity=0[v{n}];")
        cur = f"v{n}"; n += 1
    if vignette:
        fc += f"[{cur}]vignette=angle=PI/5[v{n}];"; cur = f"v{n}"; n += 1
    if dim:
        fc += f"[{cur}]drawbox=c=black@{dim}:t=fill[v{n}];"; cur = f"v{n}"; n += 1
    for i, (png, y, t_in, t_out) in enumerate(caps):
        inputs += ["-loop", "1", "-i", png]; k = i + 1
        fc += (f"[{k}:v]format=rgba,fade=t=in:st={t_in:.2f}:d=0.15:alpha=1,fade=t=out:st={t_out:.2f}:d=0.2:alpha=1[c{k}];"
               f"[{cur}][c{k}]overlay=0:{y}:shortest=1[v{n}];"); cur = f"v{n}"; n += 1
    post = []
    if flash:   post.append("fade=t=in:st=0:d=0.067:color=white")
    if dip_in:  post.append("fade=t=in:st=0:d=0.3")
    if dip_out: post.append(f"fade=t=out:st={out_dur-0.3:.2f}:d=0.3")
    post.append("format=yuv420p")
    fc += f"[{cur}]{','.join(post)}[o]"
    run(*inputs, "-filter_complex", fc, "-map", "[o]", "-frames:v", str(nf), *ENC, out)
    return out

# ── підписи ─────────────────────────────────────────────────────────────────
c_before = cap("before", 130, "BEFORE")
c_after  = cap("after",  130, "AFTER")
c_afters = cap("afters",  50, "the ring takes the ball's colour")
c_timer  = cap("timer",   72, "BOOSTS GOT A TIMER")
c_magnet = cap("magnet", 104, "MAGNET",  color=C_MAGNET)
c_frenzy = cap("frenzy", 104, "GEM ×2",  color=C_FRENZY)
c_slow   = cap("slow",   104, "SLOW-MO", color=C_SLOW)
c_shield = cap("shield", 104, "SHIELD",  color=C_SHIELD)
c_theme  = cap("theme",   76, "EVERY THEME", "ITS OWN COLOUR")
c_shader = cap("shader",  84, "ONE SHADER", "FOR EVERY BAR")
c_shaders = cap("shaders", 44, "boost timer · combo ring · any shape")
c_live   = cap("live",    76, "ALREADY IN THE GAME")
c_end1   = cap("end1",   190, "DEVLOG 02")
c_end2   = cap("end2",    68, "Next: sound.")
c_end3   = cap("end3",    52, "ORBIT DASH — free on Google Play")
c_end4   = cap("end4",    44, "@lewydo_game · made with 💚 in Ukraine")

# ── шоти ────────────────────────────────────────────────────────────────────
#  Крупний план — 520×924 (пунш 1.38): ширший за м'яч настільки, щоб трекер
#  ніколи не впирався в край кадру, коли м'яч виходить на зовнішнє кільце.
CU  = dict(w=520, h=924)
CU2 = dict(w=460, h=818)                                    # SYNTH: м'яч там на внутрішньому кільці
SHARP = "scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"
segs = []
# 0 · ДО (такт): стара збірка — кільце комбо БІЛЕ, правого HUD узагалі немає
segs.append(seg("s00", OLD, 2.90, BAR/2, 0.5, SHARP, 1,
                track=track_crop(OLD, 2.90, BAR/2, P_NEON, **CU), vignette=True,
                caps=[(c_before, 1430, 0.15, BAR - 0.1)]))
# 1 · ПІСЛЯ (такт) — ДРОП: кільце й супутники взяли колір м'яча (патч 55)
segs.append(seg("s01", NEON, 41.90, BAR/2, 0.5, SHARP, 1,
                track=track_crop(NEON, 41.90, BAR/2, P_NEON, **CU), flash=True, bloom=0.35,
                caps=[(c_after, 1430, 0.05, BAR - 0.1), (c_afters, 1620, 0.5, BAR - 0.1)]))
# 2 · ПАНЕЛЬ З'ЯВЛЯЄТЬСЯ (такт): буст підібрано на 38.00 — рівно за долю після зрізу
segs.append(seg("s02", NEON, 37.77, BAR/2, 0.5, zoom(1.0, 1.22, 520, 430, 55), 1,
                caps=[(c_timer, 1480, 0.55, BAR - 0.1)]))
# 3–5 · ТРИ ТАЙМЕРНІ БУСТИ (по такту): той самий кадр HUD, 1× — видно, як смуга збігає
segs.append(seg("s03", NEON, 38.60, BAR, 1.0, HUD, 1, caps=[(c_magnet, 1420, 0.1, BAR - 0.1)]))
segs.append(seg("s04", NEON, 22.60, BAR, 1.0, HUD, 1, caps=[(c_frenzy, 1420, 0.1, BAR - 0.1)]))
segs.append(seg("s05", NEON, 30.50, BAR, 1.0, HUD, 1, caps=[(c_slow,   1420, 0.1, BAR - 0.1)]))
# 6 · ЩИТ (такт): кільце щита на м'ячі — кольором бустера, а не білим
segs.append(seg("s06", NEON, 14.22, BAR/2, 0.5, SHARP, 1,
                track=track_crop(NEON, 14.22, BAR/2, P_NEON, **CU), bloom=0.2,
                caps=[(c_shield, 1430, 0.15, BAR - 0.1)]))
# 7 · NEON ЗАГАЛЬНИМ (такт): усе разом — комбо, щит, панель буста
segs.append(seg("s07", NEON, 44.00, BAR/2, 0.5, FULL, 1, vignette=True))
# 8 · ПЕРЕХІД НА SYNTH (такт): та сама гра, інша тема — зріз зі спалахом
segs.append(seg("s08", SYNTH, 19.40, BAR/2, 0.5, FULL, 1, flash=True,
                caps=[(c_theme, 1400, 0.2, BAR - 0.1)]))
# 9 · SYNTH КРУПНО (такт): рожевий м'яч, кільце комбо того ж кольору
segs.append(seg("s09", SYNTH, 38.75, BAR/2, 0.5, SHARP, 1,
                track=track_crop(SYNTH, 38.75, BAR/2, P_SYNTH, **CU2), bloom=0.2))
# 10 · СТЕНД ПРОГРЕСУ (2 такти): смужка зі стенда на тлі кольору фону — «як це зроблено»
segs.append(seg("s10", STAND, 8.00, 2*BAR, 1.0, "crop=560:120:80:790,scale=1080:232:flags=lanczos", 2,
                pad=(820, BG), dip_in=True,
                caps=[(c_shader, 1130, 0.3, 2*BAR - 0.15), (c_shaders, 1380, 0.9, 2*BAR - 0.15)]))
# 11 · ГРА ЖИВЕ (2 такти): від'їзд, гем-френзі, комбо ×5
segs.append(seg("s11", NEON, 25.40, 2*BAR, 1.0, zoom(1.22, 1.0, *FIELD, 40), 2,
                caps=[(c_live, 1480, 1.2, 2*BAR - 0.15)]))
# 12 · КІНЦЕВА КАРТКА (2 такти): під димом — серія, що далі, де взяти, хто робить
segs.append(seg("s12", NEON, 4.00, BAR, 0.5, punch(1.3, *FIELD), 2, dim=0.5, vignette=True, dip_in=True,
                caps=[(c_end1, 1030, 0.15, 2*BAR), (c_end2, 1300, 0.70, 2*BAR),
                      (c_end3, 1430, 1.10, 2*BAR), (c_end4, 1540, 1.50, 2*BAR)]))
# останній сегмент — у чорне
last = segs[-1]; d = dur(last)
run("-i", last, "-vf", f"fade=t=out:st={d-0.5:.2f}:d=0.5", *ENC, last.replace(".mp4", "_f.mp4"))
segs[-1] = last.replace(".mp4", "_f.mp4")

# ── склейка ─────────────────────────────────────────────────────────────────
lst = f"{SEG}/list.txt"
with open(lst, "w") as f:
    for s in segs: f.write(f"file '{s}'\n")
run("-f", "concat", "-safe", "0", "-i", lst, "-an", "-c:v", "libx264", "-crf", "18", "-preset", "medium",
    "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUT)
print(f"{os.path.basename(OUT)}: {dur(OUT):.2f} с, {os.path.getsize(OUT)/1e6:.1f} МБ, сегментів {len(segs)}")
t = 0
for s in segs:
    print(f"  {os.path.basename(s):12s} {t:6.2f} → {t+dur(s):6.2f}  (такт {t/BAR:4.1f})"); t += dur(s)

# ── обкладинка: крупний план м'яча з кільцем комбо + градієнт + назва серії ──
def gradient_png(path, w=1080, h=1920, y0=1150, a1=235):
    import zlib, struct
    rows = bytearray()
    for y in range(h):
        a = 0 if y < y0 else min(a1, int((y - y0) * a1 / (h - 1 - y0)))
        rows += b"\x00" + bytes([0, 0, 0, a]) * w
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(rows), 6)) + chunk(b"IEND", b""))
    return path
grad   = gradient_png(f"{CAP}/grad.png")
c_cov1 = cap("cov1", 90, "ORBIT DASH"); c_cov2 = cap("cov2", 165, "DEVLOG 02")
cov_t  = 43.45                                              # найчистіший кадр: яскравий м'яч, кільце комбо й чотири супутники

def ball_still(src, t):
    """М'яч на ОДНОМУ кадрі: центроїд майже білих пікселів з високими G і B (ядро м'яча).
       Трекер тут не годиться — на першому кадрі йому нема від чого відштовхнутись."""
    raw = subprocess.run([FF, "-v", "error", "-ss", f"{t:.3f}", "-i", src, "-frames:v", "1",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True).stdout
    sx = sy = n = 0
    for y in range(240, 1000, 2):
        for x in range(4, 716, 2):
            i = (y * 720 + x) * 3
            if raw[i] > 170 and raw[i+1] > 230 and raw[i+2] > 230: sx += x; sy += y; n += 1
    return (sx / n, sy / n) if n else FIELD

cx, cy = ball_still(NEON, cov_t)
cw, ch = 380, 676                                           # тісніше: м'яч із кільцем комбо — головне на обкладинці
cxp = min(max(cx - cw / 2, 0), 720 - cw)
cyp = min(max(cy - ch * 0.38, 0), 1650 - ch)                # м'яч вище центру — під нього лягає назва серії
run("-ss", f"{cov_t:.3f}", "-i", NEON, "-loop", "1", "-i", grad, "-loop", "1", "-i", c_cov1, "-loop", "1", "-i", c_cov2,
    "-filter_complex",
    f"[0:v]crop={cw}:{ch}:{cxp:.0f}:{cyp:.0f},scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0,format=rgba[g];"
    "[g][1:v]overlay=0:0[g2];[g2][2:v]overlay=0:1420[g3];[g3][3:v]overlay=0:1530,format=rgb24[o]",
    "-map", "[o]", "-frames:v", "1", COVER)
print("cover:", COVER, os.path.getsize(COVER) // 1000, "КБ")
