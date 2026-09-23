#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# DEVLOG 04 для @lewydo_game — ЗВУК. Патчі 71 (синтез із рецептів) і 72 (мікшер).
#   python3 build-devlog-04.py <scratch> <out-nomusic.mp4> <out-phonk.mp4> <cover.png>
#
# ЧИМ ЦЕЙ ВИПУСК ІНШИЙ. Тема — звук, тобто те, чого не видно. Тому:
#
#   • ДОРІЖКА РОЛИКА — САМА ГРА. adb screenrecord звуку не пише, але лабораторна
#     збірка писала в лог мітку кожної події (OD_SFX) і «плеск» на старті рану
#     (OD_CLAP) — два білі кадри. Плеск знайдено в записі (2.9239 с), і кожна
#     подія лягла в ролик кадр у кадр, озвучена ТИМИ САМИМИ рецептами
#     SfxCatalog. Версія «без музики» — це не тиша, це гра як вона звучить.
#   • Монтаж мусить ПОКАЗУВАТИ звук: драбина гемів у записі (gem_0…gem_9,
#     73.9→81.2 с) іде крупним планом під власний підйом на півтони, а поруч —
#     ОСЦИЛОГРАМА тієї самої доріжки, намальована з тих самих семплів.
#   • Трек НЕ з бібліотеки і не ремікс: фонк згенеровано (devlog04-audio.py),
#     140 BPM, тому сітка монтажу — моя, і зрізи лягають на долі точно.
#
# Решта правил — як у DEVLOG 03: вступ одним планом до дропу, далі зрізи рівно
# по долях, геймплей 0.5× для ока, підписи окремим проходом за абсолютним часом,
# «DEVLOG 04» у вертикальному центрі кінцевої картки (сітка профілю ріже краї).
# ─────────────────────────────────────────────────────────────────────────────
import subprocess, sys, os, json

SCR, OUT_NM, OUT_PH, COVER = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
FF, FP = "/opt/homebrew/bin/ffmpeg", "/opt/homebrew/bin/ffprobe"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                                 # market/content
SRC  = f"{ROOT}/source"
SEG, CAP, AUD = f"{SCR}/video/seg", f"{SCR}/video/cap", f"{SCR}/dl04/aud"
CAPBIN, CODEBIN = f"{SCR}/video/caption", f"{SCR}/video/rcode"
for d in (SEG, CAP, AUD): os.makedirs(d, exist_ok=True)

# ── сировина ────────────────────────────────────────────────────────────────
REC   = f"{SCR}/dl04/raw.mp4"                                # 720×1650, 60 fps, 100 с, NEON, автопілот
LOG   = f"{SCR}/dl04/events.log"                             # OD_SFX / OD_CLAP
CLAP_V = 2.9239                                              # час плеску В ЗАПИСІ (перший білий кадр)
CARD_KT = f"{SRC}/cards/devlog-04-kotlin.txt"
CARD_MX = f"{SRC}/cards/devlog-04-mixer.txt"

FPS  = 30
BPM  = 146.0; BEAT = 60 / BPM; BAR = 4 * BEAT                # 0.41096 · 1.64384
BARS = 18; TRACK_LEN = BARS * BAR                            # 29.589 с
DROP = 4 * BAR                                               # 6.857 — вступ одним планом до сюди
ENC  = ["-an", "-c:v", "libx264", "-crf", "16", "-preset", "fast", "-pix_fmt", "yuv420p"]
PRE  = "fps=60,setpts=PTS-STARTPTS"
FULL = "crop=720:1280:0:0,scale=1080:1920:flags=lanczos"
SHARP = "scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"
FIELD = (360, 600)
P_NEON = "00E5FF"                                            # колір гравця NEON — трекер і цифри заставки
C_GEM, C_MAGNET = "FFD54A", "C07BFF"

def bar(n):  return n * BAR
def beat(n): return n * BEAT
def B(n):    return DROP + n * BEAT                           # доля n від дропу

def run(*a):
    r = subprocess.run([FF, "-v", "error", "-y", *a], capture_output=True, text=True)
    if r.returncode: sys.exit(f"FFMPEG FAIL: {' '.join(map(str, a))[:400]}\n{r.stderr[-700:]}")
def dur(p):
    return float(subprocess.run([FP, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                                capture_output=True, text=True).stdout.strip())
def cap(name, size, *lines, color=None, w=1080, h=None):
    h = h or int(size * 1.35 * len(lines))
    p = f"{CAP}/{name}.png"
    subprocess.run([CAPBIN, p, str(w), str(h), str(size)] + (["--color", color] if color else []) + list(lines),
                   check=True, capture_output=True)
    return p
def code_card(name, title, path, w=1000, h=520, size=27):
    p = f"{CAP}/{name}.png"
    subprocess.run([CODEBIN, p, str(w), str(h), str(size), title, path], check=True, capture_output=True)
    return p

# ── трекер м'яча (як у DEVLOG 02/03) ────────────────────────────────────────
TSC = 4; TW, TH = 720 // TSC, 1650 // TSC

def ball_path(src, ss, real, color):
    key = f"{CAP}/track-{ss:.2f}-{real:.2f}.json"
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
                if abs(r-pr) + abs(g-pg) + abs(bl-pb) > 150: continue
                cand.append((x, y, max(r, g, bl)))
        if not cand: pts.append(prev); continue
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
        w = [p for p in pts[max(0, i-4):i+5] if p] or [FIELD]
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
    return f"setpts={1/sp:g}*PTS,minterpolate=fps=60:mi_mode=blend"
def punch(z, cx, cy, W=720, H=1280):
    w, h = round(W / z), round(H / z)
    x, y = min(max(cx - w // 2, 0), W - w), min(max(cy - h // 2, 0), H - h)
    return f"crop={w}:{h}:{x}:{y},scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"
def whip(nf, zin=1.13, zout=1.13, k=4):
    """«Вдих-видих» на зрізі: кадр влітає з наїзду і вилітає в наїзд.

    Це і є той плавний перехід, якого бракувало: очі не бачать стрибка, бо рух
    не зупиняється на межі. k кадрів з кожного боку — приблизно 0.13 с,
    достатньо, щоб відчути, і замало, щоб помітити як ефект."""
    z = (f"if(lt(on,{k}), {zin}-({zin}-1)*pow(on/{k},0.6),"
         f" if(gt(on,{nf-k}), 1+({zout}-1)*pow((on-{nf-k})/{k},1.6), 1))")
    return (f"zoompan=z='{z}':d=1:s=1080x1920:fps=30"
            f":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'")

def zoom(z0, z1, cx, cy, nframes):
    p = f"min(in/{nframes},1)"; ss = f"(3*pow({p},2)-2*pow({p},3))"
    return (f"zoompan=z='{z0}+({z1}-{z0})*{ss}':x='min(max({cx}-iw/zoom/2,0),iw-iw/zoom)'"
            f":y='min(max({cy}-ih/zoom/2,0),ih-ih/zoom)':d=1:s=1080x1920:fps=30,unsharp=5:5:0.4:5:5:0")

_cursor = [0.0]
SEGMAP  = []                                                 # для звукової доріжки: що з чого зібрано

def frames_until(t_end):
    n = round(t_end * FPS) - round(_cursor[0] * FPS)
    _cursor[0] = t_end
    return n

def seg(name, src, ss, speed, mid, t_end, track=None, flash=False, dim=0.0,
        dip_in=False, dip_out=False, overlays=(), bg=None, blur=0, wh=False):
    out_in = _cursor[0]
    nf = frames_until(t_end); out_dur = nf / FPS
    real = out_dur * speed
    out = f"{SEG}/{name}.mp4"
    if src: SEGMAP.append(dict(src_in=ss, speed=speed, out_in=out_in, out_out=t_end))
    if bg:
        inputs = ["-f", "lavfi", "-i", f"color=c=0x{bg}:s=1080x1920:r=30:d={out_dur + 1:.2f}"]
        chain = "fps=30"
    else:
        inputs = ["-ss", f"{ss:.3f}", "-t", f"{real * 1.1 + 0.2:.3f}", "-i", src]
        chain = PRE
        if track: chain += f",{track}"
        chain += f",{slow(speed)},{mid},fps=30"
        if blur: chain += f",boxblur={blur}:1"       # картка лягає на живу, але розмиту гру
        if wh:   chain += f",{whip(nf)}"
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

# ── шоти: секунди ЗАПИСУ (з логу подій — точні, не на око) ──────────────────
#   бусти: 8.6 17.8 26.3 35.3 44.4 51.3 53.4 62.3 68.2 71.4 80.4 89.4
#   драбина гемів gem_0…gem_9: 73.9 → 81.2      комбо 4…7: 69.0 75.1 78.5 93.7
#
#  ДРАМАТУРГІЯ ПІД ТРЕК (146 BPM, 18 тактів):
#   такти 0–3  вступ одним планом, райзер у двох останніх тактах → дроп
#   такти 4–7  дроп: гра + картка «п'ять чисел» ПОВЕРХ розмитої гри
#   такти 8–11 драбина гемів крупно — звук лізе вгору разом із ланцюжком
#   такти 12–13 комбо; 14–15 мікшер (знову поверх гри) ; 16–17 кінцева картка
#  Кожен геймплейний зріз — whip: кадр влітає з наїзду й вилітає в наїзд.
CU  = dict(w=520, h=924)
CU2 = dict(w=400, h=711)

def cu(ss, t_end, box=CU, speed=0.5):
    return track_crop(REC, ss, (t_end - _cursor[0]) * speed + 0.3, P_NEON, **box)

card_kt = code_card("c04_kt", "SfxCatalog.kt", CARD_KT, h=560)
card_mx = code_card("c04_mx", "AudioMixer.kt", CARD_MX, h=440)

segs = []
# ── ВСТУП 0 → DROP: ОДИН план, повільний наїзд, звук гри з першої секунди ──
segs.append(seg("s00", REC, 65.00, 0.5, zoom(1.0, 1.18, *FIELD, round(DROP * FPS)), DROP))
# ── ДРОП B(0) ──────────────────────────────────────────────────────────────
segs.append(seg("s01", REC, 17.30, 0.5, FULL,  B(2), flash=True, wh=True))
segs.append(seg("s02", REC, 18.60, 0.5, SHARP, B(4), track=cu(18.60, B(4)), wh=True))
# такти 5–6: КАРТКА поверх живої гри — не виходимо з гри ні на кадр
segs.append(seg("s03", REC, 62.30, 0.5, FULL, B(8), blur=10, dim=0.52,
                overlays=[(card_kt, 620, 0.15, 1.0)]))
# такти 7–8: ДРАБИНА ГЕМІВ
segs.append(seg("s04", REC, 78.40, 0.5, SHARP, B(12), track=cu(78.40, B(12), box=CU2), flash=True, wh=True))
segs.append(seg("s05", REC, 80.10, 0.5, SHARP, B(16), track=cu(80.10, B(16)), wh=True))
# такт 9: КОМБО
segs.append(seg("s06", REC, 74.60, 0.5, FULL,  B(18), flash=True, wh=True))
segs.append(seg("s07", REC, 77.90, 0.5, SHARP, B(20), track=cu(77.90, B(20)), wh=True))
# такт 10: густо
segs.append(seg("s08", REC, 92.60, 0.5, FULL,  B(22), flash=True, wh=True))
segs.append(seg("s09", REC, 93.60, 0.5, SHARP, B(24), track=cu(93.60, B(24)), wh=True))
# такти 11–12: КАРТКА мікшера — теж поверх гри
segs.append(seg("s10", REC, 35.20, 0.5, FULL, B(32), blur=10, dim=0.52,
                overlays=[(card_mx, 680, 0.15, 1.0)]))
# такти 13–15: назад у гру на повну
segs.append(seg("s11", REC, 51.00, 0.5, FULL,  B(34), flash=True, wh=True))
segs.append(seg("s12", REC, 44.30, 0.5, SHARP, B(36), track=cu(44.30, B(36)), wh=True))
segs.append(seg("s13", REC, 26.20, 0.5, FULL,  B(38), wh=True))
segs.append(seg("s14", REC, 71.30, 0.5, SHARP, B(40), track=cu(71.30, B(40), box=CU2), wh=True))
segs.append(seg("s15", REC, 8.50,  0.5, FULL,  B(44), flash=True, wh=True))
segs.append(seg("s16", REC, 89.30, 0.5, SHARP, bar(16), track=cu(89.30, bar(16)), dip_out=True))
# ── КІНЦЕВА КАРТКА bar(16) → кінець ────────────────────────────────────────
segs.append(seg("s17", REC, 96.20, 0.5, SHARP, TRACK_LEN,
                track=cu(96.20, TRACK_LEN, box=CU2), dim=0.3, dip_in=True))
last = segs[-1]; d = dur(last)
run("-i", last, "-vf", f"fade=t=out:st={d-0.5:.2f}:d=0.5", *ENC, last.replace(".mp4", "_f.mp4"))
segs[-1] = last.replace(".mp4", "_f.mp4")

# ── склейка ────────────────────────────────────────────────────────────────
lst = f"{SEG}/list.txt"
open(lst, "w").write("".join(f"file '{s}'\n" for s in segs))
CUT = f"{SEG}/_cut.mp4"
run("-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", CUT)
NF = round(TRACK_LEN * FPS)

# ── ЗВУК: події запису → час ролика → доріжка тими самими рецептами ────────
MAP = f"{SCR}/dl04/map.json"
json.dump(dict(clap_video=CLAP_V, total=TRACK_LEN, segments=SEGMAP), open(MAP, "w"))
SFXW, PHONKW = f"{AUD}/sfx.wav", f"{AUD}/phonk.wav"
subprocess.run([sys.executable, f"{HERE}/devlog04-audio.py", "sfx", LOG, MAP, SFXW], check=True)
if not os.path.exists(PHONKW):
    subprocess.run([sys.executable, f"{HERE}/devlog04-audio.py", "phonk", f"{TRACK_LEN:.3f}", PHONKW], check=True)

# осцилограма ДРАБИНИ — намальована з тих самих семплів, що й чути
WAVE = f"{CAP}/wave.png"
run("-ss", f"{B(8):.3f}", "-t", f"{B(16)-B(8):.3f}", "-i", SFXW,
    "-filter_complex", f"volume=3.0,showwavespic=s=940x230:colors=0x{P_NEON}", "-frames:v", "1", WAVE)

# ── підписи — один прохід за абсолютним часом ──────────────────────────────
c_title = cap("t04",   44, "ORBIT DASH · DEVLOG 04")
c_snd   = cap("snd",  104, "THE GAME")
c_snd2  = cap("snd2",  92, "HAS A VOICE")
c_no    = cap("no",    76, "NOT ONE")
c_no2   = cap("no2",   76, "AUDIO FILE", color=P_NEON)
c_five  = cap("five",  58, "every sound is five numbers")
c_lad   = cap("lad",   82, "COLLECT IN A ROW", color=C_GEM)
c_lad2  = cap("lad2",  58, "+1 semitone per gem")
c_cmb   = cap("cmb",   82, "EVERY SPARK TOO")
c_cmb2  = cap("cmb2",  54, "950 Hz, climbing")
c_mix   = cap("mix",   76, "THEN WE FIXED")
c_mix2  = cap("mix2",  76, "THE VOLUME", color=C_MAGNET)
c_mix3  = cap("mix3",  52, "it was applied twice —")
c_mix4  = cap("mix4",  52, "50 % on the phone felt like 25 %")
c_now   = cap("now",   82, "NOW IT SOUNDS")
c_now2  = cap("now2",  56, "the way it plays")
c_end_dl = cap("e_dl", 150, "DEVLOG")
c_end_04 = cap("e_04", 480, "04", color=P_NEON, h=560)
c_end_g  = f"{CAP}/e_04g.png"
run("-i", c_end_04, "-vf", "gblur=sigma=30", c_end_g)
c_end_nx = cap("e_nx",  56, "Next: the PULSE explosion.")
c_end_od = cap("e_od",  62, "ORBIT DASH — free on Google Play")
c_end_wh = cap("e_wh",  46, "@lewydo_game · made with 💚 in Ukraine")

E = bar(16)
CAPS = [
    (c_title, 1560, 0.30,   1.90),
    (c_snd,   1330, bar(1), DROP - 0.06),
    (c_snd2,  1450, bar(1) + 0.35, DROP - 0.06),
    (c_no,    1330, DROP,   B(4) - 0.06),
    (c_no2,   1430, DROP + 0.25, B(4) - 0.06),
    (c_five,   330, B(4) + 0.2, B(8) - 0.06),
    (c_lad,   1330, B(8),   B(16) - 0.06),
    (c_lad2,  1450, B(9),   B(16) - 0.06),
    (WAVE,    1560, B(9),   B(16) - 0.06),
    (c_cmb,   1330, B(16),  B(20) - 0.06),
    (c_cmb2,  1440, B(17),  B(20) - 0.06),
    (c_mix,   1330, B(24) + 0.2, B(32) - 0.06),
    (c_mix2,  1430, B(24) + 0.45, B(32) - 0.06),
    (c_mix3,   300, B(26), B(32) - 0.06),
    (c_mix4,   370, B(26) + 0.25, B(32) - 0.06),
    (c_now,   1330, B(32), B(40) - 0.06),
    (c_now2,  1440, B(33), B(40) - 0.06),
    (c_end_dl, 540, E + 0.10, 99), (c_end_g, 660, E + 0.10, 99), (c_end_04, 660, E + 0.10, 99),
    (c_end_nx, 1230, E + 0.85, 99), (c_end_od, 1330, E + 1.10, 99), (c_end_wh, 1430, E + 1.35, 99),
]
END_CAPS = [c for c in CAPS if c[3] == 99]
inputs, fc, cur = ["-i", CUT], "", "0:v"
for k, (png, y, a, b) in enumerate(CAPS, 1):
    inputs += ["-loop", "1", "-i", png]
    fc += (f"[{k}:v]format=rgba,fade=t=in:st={a:.3f}:d=0.15:alpha=1,fade=t=out:st={b - 0.2:.3f}:d=0.2:alpha=1[c{k}];"
           f"[{cur}][c{k}]overlay=(W-w)/2:{y}:enable='between(t,{a:.3f},{b:.3f})'[v{k}];")
    cur = f"v{k}"
fc += f"[{cur}]format=yuv420p[o]"
VID = f"{SEG}/_capped.mp4"
run(*inputs, "-filter_complex", fc, "-map", "[o]", "-frames:v", str(NF), "-an",
    "-c:v", "libx264", "-crf", "18", "-preset", "medium", "-pix_fmt", "yuv420p", VID)

# ── два варіанти: тільки звук гри / звук гри + фонк ────────────────────────
def mux(out, *wavs):
    ins = sum([["-i", w] for w in wavs], [])
    # −14 LUFS — під що нормалізують TikTok і YouTube; alimiter із level=false,
    # інакше він сам підтягує гучність і ламає результат loudnorm.
    NORM = "loudnorm=I=-14:TP=-1.0:LRA=11,alimiter=limit=0.97:level=false,aformat=sample_fmts=fltp"
    if len(wavs) == 1:
        af = f"[1:a]{NORM}[a]"
    else:
        af = ("[1:a]volume=1.0[s];[2:a]volume=0.55[m];[s][m]amix=inputs=2:normalize=0,"
              f"{NORM}[a]")
    run("-i", VID, *ins, "-filter_complex", af, "-map", "0:v", "-map", "[a]",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", out)
    print(f"  {os.path.basename(out)}: {dur(out):.2f} с, {os.path.getsize(out)/1e6:.1f} МБ")

mux(OUT_NM, SFXW)
mux(OUT_PH, SFXW, PHONKW)

# ── обкладинка = кінцева картка ────────────────────────────────────────────
def ball_still(t):
    raw = subprocess.run([FF, "-v", "error", "-ss", f"{t:.3f}", "-i", REC, "-frames:v", "1",
                          "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True).stdout
    sx = sy = n = 0
    for y in range(240, 1000, 2):
        for x in range(4, 716, 2):
            i = (y * 720 + x) * 3
            if raw[i] < 90 and raw[i+1] > 200 and raw[i+2] > 220: sx += x; sy += y; n += 1
    return (sx / n, sy / n) if n else FIELD
cov_t = 98.60                                                # combo_6 98.4 — у м'яча є супутники комбо
cx, cy = ball_still(cov_t)
cw, ch = CU2['w'], CU2['h']
cxp = min(max(cx - cw / 2, 0), 720 - cw); cyp = min(max(cy - ch / 2, 0), 1650 - ch)
run("-ss", f"{cov_t:.3f}", "-i", REC,
    *sum([["-loop", "1", "-i", p] for p, *_ in END_CAPS], []),
    "-filter_complex",
    f"[0:v]crop={cw}:{ch}:{cxp:.0f}:{cyp:.0f},{SHARP},drawbox=c=black@0.3:t=fill,format=rgba[g0];"
    + "".join(f"[g{i}][{i+1}:v]overlay=(W-w)/2:{y}[g{i+1}];" for i, (_, y, *_r) in enumerate(END_CAPS))
    + f"[g{len(END_CAPS)}]format=rgb24[o]",
    "-map", "[o]", "-frames:v", "1", COVER)
print("cover:", COVER, os.path.getsize(COVER) // 1000, "КБ")
t = 0
for s in segs:
    print(f"  {os.path.basename(s):12s} {t:6.2f} → {t+dur(s):6.2f}"); t += dur(s)
