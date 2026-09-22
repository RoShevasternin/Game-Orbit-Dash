#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# DEVLOG 03 для @lewydo_game — віньєтка буста й хвилі магніта, під власний ремікс
# (Katy Perry — Tucked, 30.14 с).
#   python3 build-devlog-03.py <scratch> <out.mp4> <cover.png>
#
# Сітка: 122.95 BPM, доля 0.4880 с, такт 1.9520 с, такт 0 на 0.128 с треку.
# Ролик = трек: 30.14 с, 904 кадри. Тарілка на 7.44, сильна доля дропу на 7.94,
# брейкдаун без басу 23.55–27.46, кінцева картка з 27.46.
#
# ДРАМАТУРГІЯ (його правка 21.09: «спочатку словлі, потім на-на-на-на в біт»):
#   • ВСТУП 0 → 7.94 — ОДИН безперервний план без зрізів: старт рану, м'яч на
#     внутрішньому кільці, попереду з'являється буст, повільний наїзд; з такту 3
#     швидкість 0.25× — м'яч підповзає до буста; тарілка 7.44 = торкання (білий
#     спалах); сильна доля 7.94 = колір заливає екран, зріз на загальний план.
#   • ДРОП 7.94 → 23.55 — зрізи РІВНО по долях (0.488 с): де спокійніше — по дві
#     долі, «на-на-на-на» — по одній. Жодного зрізу поза сіткою долей.
#   • БРЕЙКДАУН 23.55 → 27.46 — картки «як зроблено», КІНЕЦЬ — «DEVLOG 03».
#
# Що ще нового проти DEVLOG 02:
#   • ШВИДКІСТЬ ДЛЯ ОКА: весь геймплей 0.5× (60 → 30 fps без інтерполяції),
#     розгін 0.25× (blend), 1× лише на картках.
#   • БЕЗ BLOOM І БЕЗ ffmpeg-ВІНЬЄТКИ: розмита яскравість поверх темно-синього
#     тла давала блакитну імлу («поганий синій ефект»). Світіння дає сама гра.
#   • ПІДПИСИ — ОКРЕМИЙ ПРОХІД поверх склейки, за абсолютним часом: при зрізах
#     по долі підпис, прив'язаний до сегмента, блимав би на кожному зрізі.
#   • ЗАСТАВКА В БЕЗПЕЧНІЙ ЗОНІ: «DEVLOG 03» у вертикальному центрі кадру —
#     сітка профілю ріже ~12 % зверху й знизу і кладе лічильник у лівий низ.
#   • КАРТКИ «ЯК ЗРОБЛЕНО»: SVG із Figma (render-svg.swift, прозоре тло) і код
#     (render-code.swift).
# ─────────────────────────────────────────────────────────────────────────────
import subprocess, sys, os, json

SCR, OUT, COVER = sys.argv[1], sys.argv[2], sys.argv[3]
FF, FP = "/opt/homebrew/bin/ffmpeg", "/opt/homebrew/bin/ffprobe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # market/content
REPO = os.path.dirname(os.path.dirname(ROOT))                        # Game Orbit Dash
SRC  = f"{ROOT}/source"
SEG, CAP = f"{SCR}/video/seg", f"{SCR}/video/cap"
CAPBIN, CODEBIN, SVGBIN = f"{SCR}/video/caption", f"{SCR}/video/rcode", f"{SCR}/video/rsvg"
os.makedirs(SEG, exist_ok=True); os.makedirs(CAP, exist_ok=True)

# ── сировина ────────────────────────────────────────────────────────────────
NEON  = f"{SRC}/footage/gameplay-100s-neon-vignette.mp4"    # патчі 66–70, NEON, автопілот, буст кожні 9 с
SYNTH = f"{SRC}/footage/gameplay-48s-synth-vignette.mp4"    # те саме, SYNTH
SVG   = f"{REPO}/design/ball_magnet.svg"                     # макет м'яча з хвилями магніта
CARD_SH = f"{SRC}/cards/devlog-03-shader.txt"
CARD_KT = f"{SRC}/cards/devlog-03-kotlin.txt"

FPS  = 30
BPM  = 122.95; BEAT = 60 / BPM; BAR = 4 * BEAT; T0 = 0.128
TRACK_LEN = 30.14
ENC  = ["-an", "-c:v", "libx264", "-crf", "16", "-preset", "fast", "-pix_fmt", "yuv420p"]
PRE  = "fps=60,setpts=PTS-STARTPTS"
FULL = "crop=720:1280:0:0,scale=1080:1920:flags=lanczos"
FIELD = (360, 600)                                          # центр поля в кадрі 720×1650

C_MAGNET, C_FRENZY, C_SLOW = "C07BFF", "FFD54A", "FFFFFF"   # BoostCatalog
P_NEON, P_SYNTH = "00E5FF", "FF3EC8"                        # гравець із ThemeManager — для трекера

def bar(n):  return T0 + n * BAR                            # абсолютний час такту n
def beat(n): return T0 + n * BEAT                           # абсолютний час долі n
DROP = beat(16)                                             # 7.936 — сильна доля дропу
def B(n):    return DROP + n * BEAT                         # доля n від дропу

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
def code_card(name, title, txt, w=1000, h=820, size=27):
    p = f"{CAP}/{name}.png"
    subprocess.run([CODEBIN, p, str(w), str(h), str(size), title, txt], check=True, capture_output=True)
    return p
def svg_png():
    """Макет із Figma → PNG 1080 з прозорим тлом (render-svg.swift; qlmanage кладе SVG на біле)."""
    p = f"{CAP}/ball_magnet_1080.png"
    subprocess.run([SVGBIN, SVG, p, "1080"], check=True, capture_output=True)
    return p

# ── трекер м'яча (як у DEVLOG 02) ───────────────────────────────────────────
TSC = 4
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
        for y in range(60, 260):
            row = base + y * TW * 3
            for x in range(2, TW - 2):
                i = row + x * 3
                r, g, bl = b[i], b[i+1], b[i+2]
                if max(r, g, bl) < 200: continue
                if abs(r-pr) + abs(g-pg) + abs(bl-pb) > 150: continue      # лише колір гравця: білі хвилі SLOW-MO не рахуються
                cand.append((x, y, max(r, g, bl)))
        if not cand:
            pts.append(prev); continue
        if prev is None: cand.sort(key=lambda c: -c[2])
        else:            cand.sort(key=lambda c: (c[0] - prev[0]/TSC)**2 + (c[1] - prev[1]/TSC)**2)
        cx, cy = cand[0][0], cand[0][1]
        sx = sy = sw = 0
        for x, y, v in cand:
            if (x - cx)**2 + (y - cy)**2 <= 100:
                w = v - 150; sx += x * w; sy += y * w; sw += w
        if sw: cx, cy = sx / sw, sy / sw
        prev = (cx * TSC, cy * TSC); pts.append(prev)
    out = []
    for i in range(len(pts)):
        w = [p for p in pts[max(0, i-4):i+5] if p] or [(FIELD[0], FIELD[1])]
        out.append((sum(p[0] for p in w) / len(w), sum(p[1] for p in w) / len(w)))
    json.dump(out, open(key, "w")); return out

def ramp(vals, step):
    s = f"{vals[0]:.1f}"
    for i in range(1, len(vals)):
        d = vals[i] - vals[i-1]
        if abs(d) < 0.5: continue
        s += f"+({d:.1f})*clip((t-{(i-1)*step:.3f})/{step:.3f},0,1)"
    return f"'{s}'"

def track_crop(src, ss, real, color, w, h, every=6):
    pts = ball_path(src, ss, real, color)
    xs = [min(max(p[0] - w / 2, 0), 720 - w)  for p in pts[::every]]
    ys = [min(max(p[1] - h / 2, 0), 1650 - h) for p in pts[::every]]
    return f"crop={w}:{h}:x={ramp(xs, every/60)}:y={ramp(ys, every/60)}"

# ── сегмент ─────────────────────────────────────────────────────────────────
def slow(sp):
    if sp == 1: return "setpts=PTS"
    if sp >= 0.5: return f"setpts={1/sp:g}*PTS"
    # 0.25× — між кадрами домальовуємо змішуванням (blend): без деформацій mci
    return f"setpts={1/sp:g}*PTS,minterpolate=fps=60:mi_mode=blend"
def punch(z, cx, cy, W=720, H=1280):
    w, h = round(W / z), round(H / z)
    x, y = min(max(cx - w // 2, 0), W - w), min(max(cy - h // 2, 0), H - h)
    return f"crop={w}:{h}:{x}:{y},scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"
def zoom(z0, z1, cx, cy, nframes):
    """Наїзд. zoompan бере кадр за кадром із 60 fps і віддає 30 — тобто сам дає 0.5×."""
    p = f"min(in/{nframes},1)"; ss = f"(3*pow({p},2)-2*pow({p},3))"
    return (f"zoompan=z='{z0}+({z1}-{z0})*{ss}':x='min(max({cx}-iw/zoom/2,0),iw-iw/zoom)'"
            f":y='min(max({cy}-ih/zoom/2,0),ih-ih/zoom)':d=1:s=1080x1920:fps=30,unsharp=5:5:0.4:5:5:0")

_cursor = [0.0]                                             # позиція монтажу, секунди треку

def frames_until(t_end):
    """Ціла кількість кадрів до абсолютного часу t_end — межа зрізу не пливе."""
    n = round(t_end * FPS) - round(_cursor[0] * FPS)
    _cursor[0] = t_end
    return n

def seg(name, src, ss, speed, mid, t_end, track=None, flash=False, dim=0.0, freeze=0.0,
        dip_in=False, dip_out=False, overlays=(), bg=None):
    """Один сегмент 1080×1920 30 fps до абсолютного часу t_end. Без підписів — вони окремим проходом.
       overlays: (png, y, t_in, scale) — картки на суцільному тлі."""
    nf = frames_until(t_end); out_dur = nf / FPS
    real = out_dur * speed
    out = f"{SEG}/{name}.mp4"
    if bg:                                                  # картка на суцільному тлі, без відео
        inputs = ["-f", "lavfi", "-i", f"color=c=0x{bg}:s=1080x1920:r=30:d={out_dur + 1:.2f}"]
        chain = "fps=30"
    else:
        inputs = ["-ss", f"{ss:.3f}", "-t", f"{real * 1.1 + 0.2:.3f}", "-i", src]
        chain = PRE
        if track: chain += f",{track}"
        chain += f",{slow(speed)}"
        if freeze: chain += f",tpad=stop_mode=clone:stop_duration={freeze:.3f}"
        chain += f",{mid},fps=30"
    fc, cur, n = f"[0:v]{chain}[v0];", "v0", 1
    if dim:
        fc += f"[{cur}]drawbox=c=black@{dim}:t=fill[v{n}];"; cur = f"v{n}"; n += 1
    k = 0
    for png, y, t_in, scale in overlays:
        inputs += ["-loop", "1", "-i", png]; k += 1
        sc = f"scale=iw*{scale}:-1," if scale != 1 else ""
        fc += (f"[{k}:v]format=rgba,{sc}fade=t=in:st={t_in:.2f}:d=0.2:alpha=1[o{k}];"
               f"[{cur}][o{k}]overlay=(W-w)/2:{y}:shortest=1[v{n}];"); cur = f"v{n}"; n += 1
    post = []
    if flash:   post.append("fade=t=in:st=0:d=0.067:color=white")
    if dip_in:  post.append("fade=t=in:st=0:d=0.3")
    if dip_out: post.append(f"fade=t=out:st={out_dur-0.3:.2f}:d=0.3")
    post.append("format=yuv420p")
    fc += f"[{cur}]{','.join(post)}[o]"
    run(*inputs, "-filter_complex", fc, "-map", "[o]", "-frames:v", str(nf), *ENC, out)
    return out

# ── підписи ─────────────────────────────────────────────────────────────────
c_title  = cap("title",   44, "ORBIT DASH · DEVLOG 03")
c_every  = cap("every",   96, "EVERY BOOST")
c_paints = cap("paints",  70, "NOW PAINTS THE SCREEN")
c_magnet = cap("magnet", 110, "MAGNET",  color=C_MAGNET)
c_frenzy = cap("frenzy", 110, "GEM ×2",  color=C_FRENZY)
c_slow   = cap("slow",   110, "SLOW-MO", color=C_SLOW)
c_waves  = cap("waves",   64, "ATTRACTION WAVES", color=C_MAGNET)
c_wavesd = cap("wavesd",  44, "they pull the gems in")
c_slowd  = cap("slowd",   44, "the whole world at 0.45×")
c_themes = cap("themes",  80, "THEMES CHANGE")
c_colors = cap("colors",  80, "BOOST COLOURS DON'T")
c_figma  = cap("figma",   76, "DRAWN IN FIGMA")
c_shader = cap("shader",  76, "ONE SHADER")
c_shaderd = cap("shaderd", 44, "gradient stops from Figma, 1:1")
c_baked  = cap("baked",   70, "BAKED INTO ONE TEXTURE")
c_zero   = cap("zero",    54, "zero extra draw calls")
c_end_dl = cap("end_dl", 150, "DEVLOG")
c_end_03 = cap("end_03", 480, "03", color=C_MAGNET, h=560)
c_end_03g = f"{CAP}/end_03g.png"                            # сяйво під «03»: та сама картинка, розмита разом з альфою
run("-i", c_end_03, "-vf", "gblur=sigma=30", c_end_03g)
c_end_nx = cap("end_nx",  56, "Next: sound.")
c_end_od = cap("end_od",  62, "ORBIT DASH — free on Google Play")
c_end_who = cap("end_who", 46, "@lewydo_game · made with 💚 in Ukraine")
card_svg = svg_png()
card_sh  = code_card("card_sh", "radialGradientFS.glsl", CARD_SH)
card_kt  = code_card("card_kt", "ABall.kt", CARD_KT, h=700)

# ── шоти: секунди ДЖЕРЕЛА — з analyze по запису (панель + тінт краю), 0.1 с ─
#   NEON:  MAGNET 4.2→12.2 · FRENZY 13.2→15.9 · MAGNET 15.9→22.0 · SLOW 22.0→28.0 ·
#          MAGNET 31.0→39.0 · FRENZY 40.0→48.8 · SLOW 48.9→54.9
#   SYNTH: MAGNET 4.3→11.9 · FRENZY 13.9→22.1 · SLOW 22.1→28.1 · MAGNET 31.1→39.1
S = dict(
    mag1 = 4.2,     # NEON: перший MAGNET — торкання (вступ: старт рану → буст попереду → підбір)
    mag2 = 31.0,    # NEON: другий MAGNET (8 с)
    mag3 = 15.9,    # NEON: магніт 15.9→22.0 (6.1 с)
    fr2  = 40.0,    # NEON: GEM ×2 (8.8 с)
    sl2  = 48.9,    # NEON: SLOW-MO (6 с)
    smag = 4.3,     # SYNTH: MAGNET
    sfr  = 13.9,    # SYNTH: GEM ×2
    ssl  = 22.1,    # SYNTH: SLOW-MO
)
CU  = dict(w=520, h=924)                                    # крупний план: трекер, 1.38× (м'яч і хвилі 115 цілком)
CU2 = dict(w=400, h=711)                                    # дуже крупно: 1.8× — хвилі на весь кадр
SHARP = "scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"
def cu(src, ss, t_end, color=P_NEON, box=CU, speed=0.5):
    real = (t_end - _cursor[0]) * speed
    return track_crop(src, ss, real + 0.3, color, **box)

segs = []
# ── ВСТУП 0 → 7.94: один план ──
#  Торкання буста в записі — S['mag1']. Назад від нього: розгін 0.25× займає
#  (7.44 − такт 3) відео = ¼ того в джерелі, наїзд 0.5× — половину.
PICK  = S['mag1']
src_b = PICK - (7.44 - bar(3)) * 0.25                       # початок розгону в джерелі
src_a = src_b - bar(3) * 0.5                                # початок ролика в джерелі (≈ 0.84 с — старт рану)
segs.append(seg("s00", NEON, src_a, 0.5, zoom(1.0, 1.18, *FIELD, round(bar(3) * FPS)), bar(3)))
segs.append(seg("s01", NEON, src_b, 0.25, punch(1.18, *FIELD), 7.44))            # розгін: та сама рамка, 0.25×
segs.append(seg("s02", NEON, PICK,  0.25, punch(1.18, *FIELD), DROP, flash=True))  # тарілка = торкання, спалах
# ── ДРОП: зрізи по долях ──
# такт 4: колір заливає екран (загальний) → хвилі на м'ячі
segs.append(seg("s03", NEON, PICK + (DROP - 7.44) * 0.25, 0.5, FULL, B(2), flash=True))
segs.append(seg("s04", NEON, PICK + 1.0, 0.5, SHARP, B(4), track=cu(NEON, PICK + 1.0, B(4), box=CU2)))
# такт 5: GEM ×2 — по долі: загальний / крупний / загальний / крупний
segs.append(seg("s05", NEON, S['fr2'],       0.5, FULL,  B(5), flash=True))
segs.append(seg("s06", NEON, S['fr2'] + 1.0, 0.5, SHARP, B(6), track=cu(NEON, S['fr2'] + 1.0, B(6))))
segs.append(seg("s07", NEON, S['fr2'] + 2.0, 0.5, FULL,  B(7)))
segs.append(seg("s08", NEON, S['fr2'] + 3.0, 0.5, SHARP, B(8), track=cu(NEON, S['fr2'] + 3.0, B(8))))
# такт 6: SLOW-MO — по дві долі
segs.append(seg("s09", NEON, S['sl2'],       0.5, FULL,  B(10), flash=True))
segs.append(seg("s10", NEON, S['sl2'] + 1.2, 0.5, SHARP, B(12), track=cu(NEON, S['sl2'] + 1.2, B(12))))
# такт 7: три кольори по долі — «на-на-на-на»
segs.append(seg("s11", NEON, PICK + 2.4,     0.5, SHARP, B(13), track=cu(NEON, PICK + 2.4, B(13), box=CU2)))
segs.append(seg("s12", NEON, S['fr2'] + 4.0, 0.5, SHARP, B(14), track=cu(NEON, S['fr2'] + 4.0, B(14))))
segs.append(seg("s13", NEON, S['sl2'] + 2.6, 0.5, SHARP, B(15), track=cu(NEON, S['sl2'] + 2.6, B(15))))
segs.append(seg("s14", NEON, S['mag2'] + 2.0, 0.5, FULL, B(16)))
# такт 8: теми по долі — SYNTH / SYNTH крупно / NEON / NEON крупно
segs.append(seg("s15", SYNTH, S['smag'] + 1.7, 0.5, FULL,  B(17)))
segs.append(seg("s16", SYNTH, S['smag'] + 2.7, 0.5, SHARP, B(18), track=cu(SYNTH, S['smag'] + 2.7, B(18), color=P_SYNTH)))
segs.append(seg("s17", NEON,  S['mag2'] + 5.5, 0.5, FULL,  B(19)))
segs.append(seg("s18", NEON,  S['mag2'] + 6.5, 0.5, SHARP, B(20), track=cu(NEON, S['mag2'] + 6.5, B(20))))
# такт 9: SYNTH — жовтий і білий ті самі, по дві долі
segs.append(seg("s19", SYNTH, S['sfr'] + 0.6, 0.5, FULL, B(22)))
segs.append(seg("s20", SYNTH, S['ssl'] + 0.5, 0.5, FULL, B(24)))
# такт 10: магніт тягне геми крупно / комбо загальним
segs.append(seg("s21", NEON, S['mag2'] + 3.4, 0.5, SHARP, B(26), track=cu(NEON, S['mag2'] + 3.4, B(26))))
segs.append(seg("s22", NEON, S['mag3'] + 1.0, 0.5, FULL,  B(28)))
# такт 11: до великого удару 22.336 — спалах, крупно, і в темряву перед брейкдауном
segs.append(seg("s23", NEON, S['mag3'] + 3.0, 0.5, FULL,  22.336))
segs.append(seg("s24", NEON, S['mag3'] + 4.2, 0.5, SHARP, bar(12), track=cu(NEON, S['mag3'] + 4.2, bar(12), box=CU2),
                flash=True, dip_out=True))
# ── БРЕЙКДАУН 23.55 → 27.46: як зроблено ──
segs.append(seg("s25", None, 0, 1, "", 24.536, bg="0B0C1A", overlays=[(card_svg, 420, 0.0, 0.8)]))
segs.append(seg("s26", None, 0, 1, "", 25.056, bg="0B0C1A", overlays=[(card_sh, 380, 0.0, 1.0)]))
segs.append(seg("s27", None, 0, 1, "", 26.232, bg="0B0C1A", overlays=[(card_kt, 460, 0.0, 1.0)]))
segs.append(seg("s28", NEON, PICK + 2.0, 0.5, SHARP, bar(14), track=cu(NEON, PICK + 2.0, bar(14), box=CU2), flash=True))
# ── КІНЦЕВА КАРТКА 27.456 → 30.14: м'яч із хвилями крупно під «DEVLOG 03» ──
segs.append(seg("s29", NEON, S['mag2'] + 6.0, 0.5, SHARP, TRACK_LEN, track=cu(NEON, S['mag2'] + 6.0, TRACK_LEN, box=CU2),
                dim=0.25, dip_in=True))
last = segs[-1]; d = dur(last)
run("-i", last, "-vf", f"fade=t=out:st={d-0.5:.2f}:d=0.5", *ENC, last.replace(".mp4", "_f.mp4"))
segs[-1] = last.replace(".mp4", "_f.mp4")

# ── склейка без перекодування ───────────────────────────────────────────────
lst = f"{SEG}/list.txt"
with open(lst, "w") as f:
    for s in segs: f.write(f"file '{s}'\n")
CUT = f"{SEG}/_cut.mp4"
run("-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", CUT)
NF = round(TRACK_LEN * FPS)

# ── підписи — один прохід за абсолютним часом ───────────────────────────────
E = 27.456                                                  # кінцева картка
CAPS = [
    (c_title,  1560, 0.30,          1.95),
    (c_every,  1380, bar(1),        bar(3) - 0.25),
    (c_paints, 1515, bar(2),        bar(3) - 0.25),
    (c_magnet, 1420, DROP,          B(2)  - 0.05),
    (c_waves,  1400, B(2),          B(4)  - 0.05),
    (c_frenzy, 1420, B(4),          B(8)  - 0.05),
    (c_slow,   1420, B(8),          B(10) - 0.05),
    (c_slowd,  1470, B(10),         B(12) - 0.05),
    (c_themes, 1380, B(16),         B(20) - 0.05),
    (c_colors, 1380, B(20),         B(24) - 0.05),
    (c_wavesd, 1470, B(24),         B(26) - 0.05),
    (c_figma,  1380, bar(12) + 0.2, 24.536 - 0.05),
    (c_shader, 1300, 24.536,        25.60),
    (c_shaderd, 1300, 25.65,        26.232 - 0.05),
    (c_baked,  1330, 26.232,        bar(14) - 0.05),
    (c_zero,   1440, 26.232 + 0.3,  bar(14) - 0.05),
    (c_end_dl, 540,  E + 0.10, 99), (c_end_03g, 660, E + 0.10, 99), (c_end_03, 660, E + 0.10, 99),
    (c_end_nx, 1230, E + 0.90, 99), (c_end_od, 1330, E + 1.15, 99), (c_end_who, 1430, E + 1.40, 99),
]
END_CAPS = [c for c in CAPS if c[3] == 99]
inputs, fc, cur = ["-i", CUT], "", "0:v"
for k, (png, y, a, b) in enumerate(CAPS, 1):
    inputs += ["-loop", "1", "-i", png]
    fc += (f"[{k}:v]format=rgba,fade=t=in:st={a:.3f}:d=0.15:alpha=1,fade=t=out:st={b - 0.2:.3f}:d=0.2:alpha=1[c{k}];"
           f"[{cur}][c{k}]overlay=0:{y}:enable='between(t,{a:.3f},{b:.3f})'[v{k}];")
    cur = f"v{k}"
fc += f"[{cur}]format=yuv420p[o]"
run(*inputs, "-filter_complex", fc, "-map", "[o]", "-frames:v", str(NF), "-an", "-c:v", "libx264", "-crf", "18",
    "-preset", "medium", "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUT)
print(f"{os.path.basename(OUT)}: {dur(OUT):.2f} с, {os.path.getsize(OUT)/1e6:.1f} МБ, сегментів {len(segs)}, підписів {len(CAPS)}")
t = 0
for s in segs:
    print(f"  {os.path.basename(s):12s} {t:6.2f} → {t+dur(s):6.2f}"); t += dur(s)

# ── обкладинка = кінцева картка: м'яч із хвилями крупно під «DEVLOG 03» ─────
#  «DEVLOG 03» у вертикальному центрі — сітка профілю (обрізає ~240 px зверху
#  й знизу, лічильник переглядів у лівому низу) його не зачепить.
def ball_still(src, t):
    """М'яч на ОДНОМУ кадрі: центроїд пікселів кольору гравця NEON (трекеру тут нема від чого відштовхнутись)."""
    raw = subprocess.run([FF, "-v", "error", "-ss", f"{t:.3f}", "-i", src, "-frames:v", "1",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True).stdout
    sx = sy = n = 0
    for y in range(240, 1000, 2):
        for x in range(4, 716, 2):
            i = (y * 720 + x) * 3
            if raw[i] < 90 and raw[i+1] > 200 and raw[i+2] > 220: sx += x; sy += y; n += 1
    return (sx / n, sy / n) if n else FIELD
cov_t = S['mag2'] + 6.5
cx, cy = ball_still(NEON, cov_t)
cw, ch = CU2['w'], CU2['h']
cxp = min(max(cx - cw / 2, 0), 720 - cw); cyp = min(max(cy - ch / 2, 0), 1650 - ch)
run("-ss", f"{cov_t:.3f}", "-i", NEON,
    *sum([["-loop", "1", "-i", p] for p, *_ in END_CAPS], []),
    "-filter_complex",
    f"[0:v]crop={cw}:{ch}:{cxp:.0f}:{cyp:.0f},{SHARP},drawbox=c=black@0.25:t=fill,format=rgba[g0];"
    + "".join(f"[g{i}][{i+1}:v]overlay=0:{y}[g{i+1}];" for i, (_, y, *_r) in enumerate(END_CAPS))
    + f"[g{len(END_CAPS)}]format=rgb24[o]",
    "-map", "[o]", "-frames:v", "1", COVER)
print("cover:", COVER, os.path.getsize(COVER) // 1000, "КБ")
