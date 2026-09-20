#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# DEVLOG 01 для @lewydo_game — монтаж із сирих записів телефона (720×1650, 60 fps).
#   python3 build-devlog-01.py <scratch> <out.mp4> <cover.png>
#
# Сітка: 140 BPM (фонк), доля 0.4286 с, такт 1.714 с — усі тривалості в долях.
# База геймплею — 0.5× (60 fps → чисті 30 без інтерполяції), 0.25× лише на дропі
# (minterpolate). Підписи — PNG з render-caption.swift (Inter ExtraBold), overlay + fade.
# Без звуку: музику автор додає в TikTok; перший дроп треку — на 3.43 с (білий спалах).
# ─────────────────────────────────────────────────────────────────────────────
import subprocess, sys, os

SCR, OUT, COVER = sys.argv[1], sys.argv[2], sys.argv[3]
FF, FP = "/opt/homebrew/bin/ffmpeg", "/opt/homebrew/bin/ffprobe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))   # market/content
SRC  = f"{ROOT}/source"
SEG, CAP, CAPBIN = f"{SCR}/video/seg", f"{SCR}/video/cap", f"{SCR}/video/caption"
R1, R3 = f"{SRC}/footage/gameplay-90s-combo-x5.mp4", f"{SRC}/footage/gameplay-48s-combo-ladder.mp4"   # 19.09, збірка з патчем 53: спалах іскри
O3, MENU = f"{SRC}/footage/teaser-orbit3-50s.mp4", f"{SRC}/footage/teaser-menu-themes.mp4"   # ролик #1: ВИДАЛЕНІ з диска, лежать у git-історії
STILL_A, STILL_B = f"{SRC}/tuner/tuner-53-spark-default.png", f"{SRC}/tuner/tuner-53-spark-tuned.png"   # headless Chrome з docs/tuners/53-spark.html
os.makedirs(SEG, exist_ok=True); os.makedirs(CAP, exist_ok=True)

BEAT = 60 / 140; BAR = 4 * BEAT
ENC = ["-an", "-c:v", "libx264", "-crf", "16", "-preset", "fast", "-pix_fmt", "yuv420p"]
PRE_GAME = "fps=60,setpts=PTS-STARTPTS,crop=720:1280:0:40"          # HUD + поле, без банера
PRE_MENU = "fps=60,setpts=PTS-STARTPTS,crop=514:914:103:110"        # міні-орбіта + лого + PLAY, без «· AD»
FIELD = (360, 587)                                                  # центр поля в координатах кропу гри
SPARK = (545, 560)                                                  # точка іскри дропу (rec1 @ 23.97)

def run(*a):
    r = subprocess.run([FF, "-v", "error", "-y", *a], capture_output=True, text=True)
    if r.returncode: sys.exit(f"FFMPEG FAIL: {' '.join(map(str, a))[:300]}\n{r.stderr[-600:]}")
def dur(p):
    return float(subprocess.run([FP, "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p],
                                capture_output=True, text=True).stdout.strip())
def cap(name, size, *lines, w=1080, h=None):
    h = h or int(size * 1.35 * len(lines))
    p = f"{CAP}/{name}.png"
    subprocess.run([CAPBIN, p, str(w), str(h), str(size), *lines], check=True, capture_output=True)
    return p

def slow(speed):
    if speed == 0.25: return "minterpolate=fps=120:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1,setpts=4*PTS"
    if speed == 0.5:  return "setpts=2*PTS"
    return "setpts=PTS"
def punch(z, cx, cy, W=720, H=1280):
    w, h = round(W / z), round(H / z)
    x, y = min(max(cx - w // 2, 0), W - w), min(max(cy - h // 2, 0), H - h)
    return f"crop={w}:{h}:{x}:{y},scale=1080:1920:flags=lanczos,unsharp=5:5:0.5:5:5:0"
def zoom(z0, z1, cx, cy, nframes):
    p = f"min(in/{nframes},1)"; ss = f"(3*pow({p},2)-2*pow({p},3))"
    return (f"zoompan=z='{z0}+({z1}-{z0})*{ss}':x='min(max({cx}-iw/zoom/2,0),iw-iw/zoom)'"
            f":y='min(max({cy}-ih/zoom/2,0),ih-ih/zoom)':d=1:s=1080x1920:fps=30,unsharp=5:5:0.4:5:5:0")
FULL = "scale=1080:1920:flags=lanczos"

def seg(name, src, ss, real, speed, mid, out_dur, pre=PRE_GAME, caps=(), flash=False, flash_at=None,
        dip_in=False, dip_out=False, bloom=0.0, vignette=False, dim=0.0, freeze=0.0, still=False):
    """Один сегмент 1080×1920 30 fps. caps: (png, y, t_in, t_out). flash — білий спалах на старті."""
    out = f"{SEG}/{name}.mp4"
    inputs = (["-loop", "1", "-t", f"{out_dur:.3f}", "-i", src] if still
              else ["-ss", f"{ss:.3f}", "-t", f"{real:.3f}", "-i", src])
    chain = ("fps=30,setpts=PTS-STARTPTS" if still else f"{pre},{slow(speed)}")
    if freeze: chain += f",tpad=stop_mode=clone:stop_duration={freeze:.3f}"
    chain += f",{mid},fps=30"
    fc = f"[0:v]{chain}[v0];"; cur = "v0"; n = 1
    if bloom:   # світяться лише яскраві місця (хвиля, крапки, м'яч), лише luma; blend мішає з ДРУГИМ входом — оригінал туди
        fc += (f"[{cur}]split[a][b];[b]lutyuv=y='if(gt(val,150),val,0)':u=128:v=128,gblur=sigma=16[g];"
               f"[g][a]blend=c0_mode=screen:c0_opacity={bloom}:c1_mode=normal:c1_opacity=0:c2_mode=normal:c2_opacity=0[v{n}];"); cur = f"v{n}"; n += 1
    if vignette:
        fc += f"[{cur}]vignette=angle=PI/5[v{n}];"; cur = f"v{n}"; n += 1
    if dim:
        fc += f"[{cur}]drawbox=c=black@{dim}:t=fill[v{n}];"; cur = f"v{n}"; n += 1
    for i, (png, y, t_in, t_out) in enumerate(caps):
        inputs += ["-loop", "1", "-i", png]
        k = i + 1
        fc += (f"[{k}:v]format=rgba,fade=t=in:st={t_in:.2f}:d=0.15:alpha=1,fade=t=out:st={t_out:.2f}:d=0.2:alpha=1[c{k}];"
               f"[{cur}][c{k}]overlay=0:{y}:shortest=1[v{n}];"); cur = f"v{n}"; n += 1
    post = []
    if flash:    post.append("fade=t=in:st=0:d=0.067:color=white")
    if flash_at is not None: post.append(f"drawbox=c=white@0.55:t=fill:enable='between(t,{flash_at:.2f},{flash_at+0.05:.2f})'")
    if dip_in:   post.append("fade=t=in:st=0:d=0.3")
    if dip_out:  post.append(f"fade=t=out:st={out_dur-0.3:.2f}:d=0.3")
    post.append("format=yuv420p")
    fc += f"[{cur}]{','.join(post)}[o]"
    run(*inputs, "-filter_complex", fc, "-map", "[o]", "-t", f"{out_dur:.3f}", *ENC, out)
    return out

# ── підписи ────────────────────────────────────────────────────────────────
c_tap    = cap("tap",    150, "ONE TAP.")
c_spark  = cap("spark",  120, "CATCH", "THE SPARK")
c_x      = {k: cap(f"x{k}", 270 if k == 5 else 240, f"×{k}") for k in (2, 3, 4, 5)}
c_boost  = cap("boost",   90, "BOOSTS")
c_orbit3 = cap("orbit3", 100, "30 SECONDS IN", "THIRD ORBIT")
c_themes = cap("themes",  96, "9 THEMES")
c_tuner1 = cap("tuner1",  96, "WE BUILT A TUNER")
c_tuner2 = cap("tuner2",  96, "SLIDERS BECOME", "GAME CODE")
c_end1   = cap("end1",   200, "DEVLOG 01")
c_end2   = cap("end2",    80, "Next: sound. Follow.")
c_end3   = cap("end3",    52, "He codes. She designs. · @lewydo_game", "made with 💚 in Ukraine")

# ── шоти ───────────────────────────────────────────────────────────────────
segs = []
# 1 · ХУК (1 такт): дубль 3, перша іскра рану — чисте поле, підхід, спалах, стрибок на зовнішнє кільце
segs.append(seg("s01", R3, 4.02, BAR / 2, 0.5, FULL, BAR,
                caps=[(c_tap, 1400, 0.55, BAR - 0.15)], flash_at=0.56))
# 2 · БІЛД-АП (1 такт): підхід до іскри, зум 1→1.5 на точку іскри, стоп-кадр на останню долю
segs.append(seg("s02", R1, 23.97 - 0.60, 0.60, 0.5, zoom(1.0, 1.5, *SPARK, 36), BAR,
                freeze=BAR - 1.20, vignette=True))
# 3 · ДРОП (1.5 такту): спалах іскри в 0.25× з інтерполяцією, punch 1.5×, bloom; далі ramp 0.5×
segs.append(seg("s03a", R1, 23.97, 0.535, 0.25, punch(1.5, *SPARK), 2.14,
                caps=[(c_spark, 1330, BEAT, 2.14)], flash=True, bloom=0.5, vignette=True))
segs.append(seg("s03b", R1, 23.97 + 0.535, BEAT / 2, 0.5, punch(1.5, *SPARK), BEAT, vignette=True))
# 4 · ВИДИХ (пів такту): єдиний чесний 1× — від'їзд до повного поля
segs.append(seg("s04", R1, 23.97 + 0.535 + BEAT / 2, BAR / 2, 1.0, zoom(1.5, 1.0, *SPARK, 18), BAR / 2))
# 5 · ДРАБИНА (2 такти): ×2 ×3 ×4 ×5 із дубля 3 — по дві долі на кожне спіймання, той самий кроп
for k, t in zip((2, 3, 4, 5), (4.30, 9.14, 10.95, 14.85)):
    segs.append(seg(f"s05_{k}", R3, t - 0.07, BEAT, 0.5, punch(1.3, *FIELD), 2 * BEAT,
                    caps=[(c_x[k], 1250, 0.05, 2 * BEAT - 0.1)], flash=True, bloom=0.3 if k == 5 else 0.0))
# 6 · ТРОФЕЙ (1 такт): м'яч ×5 із чотирма супутниками ковзає без тапу
segs.append(seg("s06", R1, 3.0, BAR / 2, 0.5, punch(1.3, *FIELD), BAR, bloom=0.25, vignette=True))
# 7 · МАГНІТ (1 такт): геми летять до м'яча (ролик #1, третя орбіта)
segs.append(seg("s07", O3, 11.85, BAR / 2, 0.5, FULL, BAR, caps=[(c_boost, 1400, 0.2, BAR - 0.15)]))
# 8 · ТРЕТЯ ОРБІТА (2 такти): поле від'їжджає, проявляється третє кільце
segs.append(seg("s08", O3, 28.6, BAR, 0.5, zoom(1.1, 1.0, *FIELD, 60), 2 * BAR,
                caps=[(c_orbit3, 1330, BAR, 2 * BAR - 0.15)], vignette=True, dip_out=True))
# 9 · 9 ТЕМ (2 такти): меню, 7 фліпів по долях, остання — зелена, тримається долю
segs.append(seg("s09", MENU, 3.1, 9.8, 1 / 3.267, "scale=1080:1920:flags=lanczos", 2 * BAR, pre=PRE_MENU,
                freeze=BEAT, caps=[(c_themes, 1400, 0.2, 2 * BAR - 0.15)], dip_in=True, dip_out=True))
# 10 · ТЮНЕР (2 такти): сторінка тюнера — спалах на канвасі; потім підкручені повзунки й більша хвиля
segs.append(seg("s10a", STILL_A, 0, 0, 1, zoom(1.0, 1.12, 720, 560, 51), BAR, still=True,
                caps=[(c_tuner1, 1330, 0.15, BAR - 0.1)], dip_in=True))
segs.append(seg("s10b", STILL_B, 0, 0, 1, zoom(1.12, 1.0, 720, 560, 51), BAR, still=True,
                caps=[(c_tuner2, 1290, 0.1, BAR - 0.1)], flash=True, dip_out=True))
# 11 · КІНЦЕВА КАРТКА (2 такти): м'яч ×5 під димом, серія, CTA, бренд; у чорне
segs.append(seg("s11", R1, 4.0, BAR, 0.5, punch(1.3, *FIELD), 2 * BAR, dim=0.5, vignette=True, dip_in=True,
                caps=[(c_end1, 1100, 0.2, 2 * BAR), (c_end2, 1380, 0.7, 2 * BAR), (c_end3, 1520, 1.1, 2 * BAR)]))
# fade у чорне наприкінці — окремим проходом по останньому сегменту
last = segs[-1]; d = dur(last)
run("-i", last, "-vf", f"fade=t=out:st={d-0.5:.2f}:d=0.5", *ENC, last.replace(".mp4", "_f.mp4")); segs[-1] = last.replace(".mp4", "_f.mp4")

# ── склейка: усі сегменти однакового формату → concat з перекодуванням ──────
lst = f"{SEG}/list.txt"
with open(lst, "w") as f:
    for s in segs: f.write(f"file '{s}'\n")
run("-f", "concat", "-safe", "0", "-i", lst, "-an", "-c:v", "libx264", "-crf", "18", "-preset", "medium",
    "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUT)
print(f"{os.path.basename(OUT)}: {dur(OUT):.2f} с, {os.path.getsize(OUT)/1e6:.1f} МБ, сегментів {len(segs)}")
for s in segs: print(f"  {os.path.basename(s):14s} {dur(s):5.2f}")

# ── обкладинка: кадр дропу через 0.15 с (хвиля + крапки), зум 1.6× нижче HUD, градієнт, серія ──
#    «DEVLOG 01» — 165 px: у 210 не вміщалось у 1080 і різало «01»; той самий розмір — на всі випуски.
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
grad = gradient_png(f"{CAP}/grad.png")
c_cov1 = cap("cov1", 90, "ORBIT DASH"); c_cov2 = cap("cov2", 165, "DEVLOG 01")
run("-ss", f"{23.97 + 0.15:.3f}", "-i", R1, "-loop", "1", "-i", grad, "-loop", "1", "-i", c_cov1, "-loop", "1", "-i", c_cov2,
    "-filter_complex",
    f"[0:v]crop=720:1280:0:40,{punch(1.6, SPARK[0], SPARK[1] + 60)},format=rgba[g];"
    "[g][1:v]overlay=0:0[g2];[g2][2:v]overlay=0:1420[g3];[g3][3:v]overlay=0:1530,format=rgb24[o]",
    "-map", "[o]", "-frames:v", "1", COVER)
print("cover:", COVER, os.path.getsize(COVER) // 1000, "КБ")
