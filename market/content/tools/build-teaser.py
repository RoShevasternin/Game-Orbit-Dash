#!/usr/bin/env python3
# Монтаж devlog-ролика: *_raw.mp4 (уже обрізані 1080x1920) + PNG-підписи → фінал 9:16.
#   python3 build-teaser.py <capDir> <out.mp4>
import subprocess, sys, os
V  = os.path.dirname(os.path.abspath(__file__)) + "/video"
S  = V + "/seg"
CAP, OUT = sys.argv[1], sys.argv[2]
X  = ["-an", "-c:v", "libx264", "-crf", "16", "-preset", "fast"]
MENU_Y, GAME_Y, T = 1030, 1460, 0.35      # підпис у меню / у грі, тривалість xfade

def ff(*a):
    r = subprocess.run(["ffmpeg", "-v", "error", "-y", *a], capture_output=True, text=True)
    if r.returncode: sys.exit(f"FFMPEG: {' '.join(a)[:160]}\n{r.stderr[-400:]}")
def dur(p):
    return float(subprocess.run(["ffprobe","-v","error","-show_entries","format=duration",
                                 "-of","csv=p=0",p], capture_output=True, text=True).stdout.strip())

names = ["s1","s2","s3","s4","s5","s6","s7","s8"]
menu  = {"s1","s7","s8"}                   # решта — ігрові кадри
segs  = []
for n in names:
    raw, d = f"{S}/{n}_raw.mp4", dur(f"{S}/{n}_raw.mp4")
    y  = MENU_Y if n in menu else GAME_Y
    fc = (f"[1:v]format=rgba,fade=t=in:st=0.25:d=0.35:alpha=1,"
          f"fade=t=out:st={d-0.45:.2f}:d=0.4:alpha=1[c];"
          f"[0:v][c]overlay=0:{y}:shortest=1,format=yuv420p[o]")
    if n == "s1": fc = fc.replace("[0:v][c]", "[0:v]fade=t=in:st=0:d=0.5[b];[b][c]")
    out = f"{S}/{n}_{os.path.basename(CAP)}.mp4"
    ff("-i", raw, "-loop", "1", "-i", f"{CAP}/{n}.png", "-filter_complex", fc, "-map", "[o]", *X, out)
    segs.append(out)

# Кінцівки: затемнений кадр меню + текст; друга гасне в чорне
for k in (1, 2):
    extra = ",fade=t=out:st=2.6:d=0.6" if k == 2 else ""
    out = f"{S}/e{k}_{os.path.basename(CAP)}.mp4"
    ff("-loop","1","-t","3.2","-i",f"{S}/end_bg.png",
       "-f","lavfi","-t","3.2","-i","color=black@0.82:s=1080x1920,format=rgba",
       "-loop","1","-t","3.2","-i",f"{CAP}/e{k}.png",
       "-filter_complex", f"[0:v][1:v]overlay=0:0[d];[2:v]format=rgba,fade=t=in:st=0:d=0.5:alpha=1[c];"
                          f"[d][c]overlay=0:930,fps=30,format=yuv420p{extra}[o]", "-map","[o]", *X, out)
    segs.append(out)

# Склейка xfade-ланцюгом
durs, inputs, fc, prev, off = [dur(s) for s in segs], [], "", "[0:v]", 0.0
for s in segs: inputs += ["-i", s]
for i in range(1, len(segs)):
    off += durs[i-1] - T
    tag = f"[v{i}]" if i < len(segs) - 1 else "[o]"
    fc += f"{prev}[{i}:v]xfade=transition=fade:duration={T}:offset={off:.3f}{tag};"
    prev = tag
ff(*inputs, "-filter_complex", fc.rstrip(";"), "-map", "[o]", "-an",
   "-c:v","libx264","-crf","18","-preset","medium","-pix_fmt","yuv420p","-movflags","+faststart", OUT)
print(f"{os.path.basename(OUT)}: {dur(OUT):.1f} с, {os.path.getsize(OUT)/1e6:.1f} МБ")
