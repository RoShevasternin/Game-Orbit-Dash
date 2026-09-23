#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# Аудіо для DEVLOG 04. Два незалежні шари, обидва — синтез, жодного ассета.
#
#   python3 devlog04-audio.py sfx   <events.log> <map.json> <out.wav>
#   python3 devlog04-audio.py phonk <секунди> <out.wav>
#
# 1) SFX — ЗВУК САМОЇ ГРИ. Ті самі п'ять чисел із SfxCatalog.kt і та сама
#    математика, що в SoundSynth.kt (експоненційні рампи частоти й гучності,
#    band-limited square/saw через polyBLEP). adb screenrecord звуку не пише,
#    тож доріжка збирається з логу OD_SFX: кожна подія лягає рівно на свою
#    мітку, перекладену з часу ЗАПИСУ в час РОЛИКА (map.json — сегменти
#    монтажу, бо геймплей іде 0.5×). Це не озвучка «схоже» — це той самий звук.
#
# 2) PHONK — окремий варіант ролика. Мемфіс-фонк: 140 BPM, halftime-барабани,
#    перевантажений 808-каубел мінорною мелодією, суб-бас із глісандо, хети
#    шістнадцятими з ролами. Усе синтезоване тут, тому Content ID неможливий
#    у принципі — на відміну від реміксу в DEVLOG 03.
# ─────────────────────────────────────────────────────────────────────────────
import sys, json, math, struct, random, re

SR = 44100

# ═══════════════════════════════════════════════════════════════════ утиліти ══

def blank(sec): return [0.0] * int(sec * SR + 0.5)

def mix(dst, src, at, gain=1.0):
    i = int(at * SR + 0.5)
    if i < 0: src = src[-i:]; i = 0
    n = min(len(src), len(dst) - i)
    for k in range(n): dst[i + k] += src[k] * gain

def write_wav(path, data, peak_target=0.89):
    p = max(abs(v) for v in data) or 1.0
    g = peak_target / p
    b = bytearray()
    for v in data:
        s = int(max(-1.0, min(1.0, v * g)) * 32767)
        b += struct.pack("<h", s)
    hdr = (b"RIFF" + struct.pack("<I", 36 + len(b)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, SR, SR * 2, 2, 16)
           + b"data" + struct.pack("<I", len(b)))
    open(path, "wb").write(hdr + bytes(b))
    print(f"  {path.split('/')[-1]}: {len(data)/SR:.2f} с, пік {p:.3f} → {peak_target}")

def blep(t, dt):
    if t < dt:        x = t / dt;        return x + x - x * x - 1.0
    if t > 1.0 - dt:  x = (t - 1.0) / dt; return x * x + x + x + 1.0
    return 0.0

# ═════════════════════════════════════════════════════ 1 · звуки гри (SfxCatalog) ══
# f, dur, wave, vol, slide — з app/.../content/SfxCatalog.kt, один в один.
SFX = {
    "ui_tick":     [(500, 0.05, "square", 0.06,    0)],
    "check_box":   [(760, 0.08, "sine",   0.08,  200)],
    "ready":       [(660, 0.14, "sine",   0.09,  260)],
    "menu_in":     [(520, 0.12, "sine",   0.10,  260)],
    "run_start":   [(440, 0.10, "sine",   0.08,  220)],
    "tap_out":     [(300, 0.06, "square", 0.07,  200)],
    "tap_in":      [(340, 0.06, "square", 0.07,  200)],
    "boost":       [(700, 0.18, "sine",   0.11,  350)],
    "shield_up":   [(600, 0.15, "sine",   0.10,  200)],
    "shield_save": [(360, 0.15, "square", 0.10, -120)],
    "pulse":       [(120, 0.35, "saw",    0.16,  -60)],
    "orbit3":      [(500, 0.40, "sine",   0.12,  500), (90, 0.50, "sine", 0.20, -30)],
    "death":       [(220, 0.50, "saw",    0.15, -170)],
    "revive":      [(520, 0.25, "sine",   0.12,  420)],
}
def semitone(base, n): return base * (2 ** (n / 12))
for n in range(9):  SFX[f"combo_{n}"] = [(semitone(950, n), 0.07, "square", 0.09, 250)]
for n in range(13): SFX[f"gem_{n}"]   = [(semitone(700, n), 0.08, "sine",   0.10, 250)]

_cache = {}
def render_sfx(name):
    """PCM одного звуку. Та сама математика, що SoundSynth.renderBeep()."""
    if name in _cache: return _cache[name]
    layers = SFX[name]
    out = [0.0] * max(int(d * SR + 0.5) for _, d, _, _, _ in layers)
    for f0, dur, wave, vol, slide in layers:
        n = int(dur * SR + 0.5)
        f1 = max(40.0, f0 + slide)
        fstep = (f1 / f0) ** (1.0 / n)
        gstep = (0.001 / vol) ** (1.0 / n)
        fade = int(0.001 * SR)
        f, g, ph = float(f0), float(vol), 0.0
        for i in range(n):
            dt = f / SR
            if wave == "sine":     y = math.sin(2 * math.pi * ph)
            elif wave == "square": y = (1.0 if ph < 0.5 else -1.0) + blep(ph, dt) - blep((ph + 0.5) % 1.0, dt)
            else:                  y = 2.0 * ph - 1.0 - blep(ph, dt)
            tail = (n - i - 1) / fade if n - i <= fade else 1.0
            out[i] += g * y * tail
            ph += dt
            if ph >= 1.0: ph -= 1.0
            f *= fstep; g *= gstep
    _cache[name] = out
    return out

def build_sfx(log_path, map_path, out_path):
    M = json.load(open(map_path))
    clap_v = M["clap_video"]                 # час плеску у ЗАПИСІ, с
    segs   = M["segments"]                   # [{src_in, speed, out_in, out_out}]
    total  = M["total"]

    clap_ms, events = None, []
    for line in open(log_path, encoding="utf-8", errors="ignore"):
        m = re.search(r"OD_(SFX|CLAP)\s+(\d+)(?:\s+(\S+))?", line)
        if not m: continue
        if m.group(1) == "CLAP":
            if clap_ms is None: clap_ms = int(m.group(2))
        elif clap_ms is not None:
            events.append(((int(m.group(2)) - clap_ms) / 1000.0 + clap_v, m.group(3)))

    dst, placed = blank(total), 0
    for t_src, name in events:
        if name not in SFX: continue
        for s in segs:
            span = (s["out_out"] - s["out_in"]) * s["speed"]
            if s["src_in"] <= t_src < s["src_in"] + span:
                t_out = s["out_in"] + (t_src - s["src_in"]) / s["speed"]
                # Затримка на подію 0 — звук різкий; гучність як у міксі гри
                mix(dst, render_sfx(name), t_out, 1.0)
                placed += 1
                break
    print(f"  подій у логу {len(events)}, лягло в монтаж {placed}")
    write_wav(out_path, dst, 0.72)

# ══════════════════════════════════════════════════════════════ 2 · трек ══
#  Не «біт під відео», а пісня на 30 с: прогресія, лід-хук, сайдчейн, райзери.
#  Стиль — мелодійний фонк/дрифт-фонк, те, що зараз крутиться в коротких відео:
#  146 BPM, halftime-барабани, перевантажений 808-каубел, але зверху ЛІД —
#  мелодія, яку можна наспівати. Саме її бракувало першій версії: був ритм без
#  пісні, тому й «немелодійно».
#
#  Гармонія — F#m → D → A → E (vi-IV-I-V). Це найспівучіша послідовність
#  масової музики; на ній тримається половина того, що чіпляє з першого разу.
BPM  = 146.0
BEAT = 60.0 / BPM
BAR  = 4 * BEAT

# F#m
F3, G3, A3, B3 = 184.997, 207.652, 220.0, 246.942
C4, D4, E4, F4 = 277.183, 293.665, 329.628, 369.994
A4, B4, C5, D5, E5 = 440.0, 493.883, 554.365, 587.330, 659.255

CHORDS = [                                   # 4 такти: акорд = (бас, тріада)
    (F3 / 2, [F3, A3, C4]),                  # F#m
    (D4 / 4, [D4, F4, A4]),                  # D
    (A3 / 2, [A3, C4, E4]),                  # A
    (E4 / 4, [E4, G3 * 2, B3]),              # E
]
# Лід-хук: (доля, нота, довжина в долях). Чотири такти, кінець фрази «питає».
LEAD = [
    [(0, A4, .75), (.75, C5, .25), (1.5, B4, .5), (2.5, A4, .75), (3.25, F4, .75)],
    [(0, D5, .75), (.75, C5, .25), (1.5, A4, 1.0), (3.0, F4, 1.0)],
    [(0, C5, .75), (.75, E5, .25), (1.5, D5, .5), (2.5, C5, .75), (3.25, A4, .75)],
    [(0, B4, .5), (.5, C5, .5), (1.5, D5, 1.0), (3.0, E5, 1.0)],
]

def env(n, a, d, curve=2.0):
    na, nd = max(1, int(a * SR)), max(1, int(d * SR))
    e = []
    for i in range(n):
        if i < na: e.append(i / na)
        else:
            x = min(1.0, (i - na) / nd)
            e.append((1.0 - x) ** curve)
    return e

def sat(x, k=3.0): return math.tanh(x * k) / math.tanh(k)

def pluck(freq, dur, detune=1.004, bright=0.6):
    """Лід: дві розстроєні пилки — «широкий» звук, що чути поверх барабанів."""
    n = int(dur * SR); e = env(n, 0.006, dur * 0.85, 1.7)
    o = []; p1 = p2 = 0.0
    d1, d2 = freq / SR, freq * detune / SR
    lp = 0.0
    for i in range(n):
        raw = (2 * p1 - 1) * 0.5 + (2 * p2 - 1) * 0.5
        lp += (raw - lp) * bright                     # однополюсний ФНЧ: менше різі
        o.append(sat(lp * 1.2, 1.5) * e[i])
        p1 = (p1 + d1) % 1.0; p2 = (p2 + d2) % 1.0
    return o

def pad(freqs, dur):
    """Підкладка-акорд: тихі синуси, тримають гармонію під лідом."""
    n = int(dur * SR); e = env(n, 0.08, dur * 0.9, 1.2)
    o = [0.0] * n
    for k, f in enumerate(freqs):
        ph = 0.0; d = f / SR
        for i in range(n):
            o[i] += math.sin(2 * math.pi * ph) * (0.34 - 0.06 * k)
            ph = (ph + d) % 1.0
    return [v * e[i] * 0.5 for i, v in enumerate(o)]

def cowbell(freq, dur, drive=4.0):
    n = int(dur * SR); e = env(n, 0.001, dur * 0.9, 2.2)
    o = []; p1 = p2 = 0.0
    d1, d2 = freq / SR, freq * 1.4854 / SR
    for i in range(n):
        y = (1.0 if p1 < 0.5 else -1.0) * 0.6 + (1.0 if p2 < 0.5 else -1.0) * 0.4
        o.append(sat(y * e[i], drive) * e[i])
        p1 = (p1 + d1) % 1.0; p2 = (p2 + d2) % 1.0
    return o

def sub808(f0, f1, dur):
    n = int(dur * SR); e = env(n, 0.004, dur * 0.95, 1.6)
    o = []; ph = 0.0
    step = (f1 / f0) ** (1.0 / n); f = f0
    for i in range(n):
        o.append(sat(math.sin(2 * math.pi * ph) * 1.1, 1.8) * e[i])
        ph = (ph + f / SR) % 1.0; f *= step
    return o

def kick(dur=0.34):
    n = int(dur * SR); e = env(n, 0.001, dur * 0.8, 2.6)
    o = []; ph = 0.0; f = 165.0
    for i in range(n):
        o.append(sat(math.sin(2 * math.pi * ph), 2.4) * e[i])
        ph = (ph + f / SR) % 1.0
        f = max(45.0, f * 0.99982)
    return o

def noise(dur, hp=0.0, decay=None, seed=1):
    r = random.Random(seed)
    n = int(dur * SR); e = env(n, 0.0005, decay or dur * 0.7, 2.0)
    o, prev = [], 0.0
    for i in range(n):
        w = r.uniform(-1, 1)
        if hp: o.append((w - prev) * hp * e[i]); prev = w
        else:  o.append(w * e[i])
    return o

def snare():
    body = noise(0.24, hp=0.9, decay=0.14, seed=7)
    n = len(body); o = []; ph = 0.0
    for i in range(n):
        t = math.sin(2 * math.pi * ph) * 0.35 * ((1 - i / n) ** 2)
        o.append(sat(body[i] * 0.8 + t, 1.6)); ph = (ph + 185.0 / SR) % 1.0
    return o

def riser(dur, seed=3):
    """Шумовий підйом у дроп — те, що робить перехід «зрозумілим» на слух."""
    r = random.Random(seed)
    n = int(dur * SR); o = []; prev = 0.0; lp = 0.0
    for i in range(n):
        x = i / n
        w = r.uniform(-1, 1)
        hp = 0.2 + 0.75 * x                         # смуга повзе вгору
        v = (w - prev) * hp; prev = w
        lp += (v - lp) * (0.15 + 0.8 * x)
        o.append(lp * (x ** 2) * 0.9)
    return o

def build_phonk(total, out_path):
    dst = blank(total)
    nbars = int(total / BAR) + 1
    END_BAR   = max(6, round(total / BAR) - 2)      # два останні такти — кінцева картка
                                                    # round, не int: 17.9997 давало зайвий такт тиші
    BREAK_BAR = END_BAR - 2
    DROP_BAR  = 4

    kick_hits = []                                   # для сайдчейну
    for b in range(nbars):
        t0 = b * BAR
        if t0 >= total: break
        intro = b < DROP_BAR
        brk   = BREAK_BAR <= b < END_BAR
        endc  = b >= END_BAR
        ch    = CHORDS[b % 4]

        # ── барабани ──────────────────────────────────────────────────────
        if not endc:
            for bt in ([0] if intro else [0, 2.5]):
                mix(dst, kick(), t0 + bt * BEAT, 0.95 if not brk else 0.5)
                kick_hits.append(t0 + bt * BEAT)
        if not intro and not endc:
            mix(dst, snare(), t0 + 2 * BEAT, 0.50 if not brk else 0.28)
        if not brk and not endc:
            for s in range(16):
                t = t0 + s * BEAT / 4
                if t >= total: break
                if intro and s % 2: continue
                mix(dst, noise(0.035, hp=0.95, decay=0.02, seed=100 + s), t,
                    0.19 if s % 4 == 0 else 0.12)
            if b % 4 == 3:                            # рол перед зміною фрази
                for k in range(6):
                    mix(dst, noise(0.03, hp=0.95, decay=0.018, seed=200 + k),
                        t0 + 3 * BEAT + k * BEAT / 6, 0.15)

        # ── бас і гармонія ────────────────────────────────────────────────
        if not endc:
            g = 0.55 if brk else (0.5 if intro else 0.85)
            mix(dst, sub808(ch[0], ch[0], BEAT * 2.2), t0, g)
            if not intro and not brk:
                mix(dst, sub808(ch[0], ch[0] * 0.945, BEAT * 1.4), t0 + 2.5 * BEAT, g * 0.9)
        mix(dst, pad(ch[1], BAR * 0.98), t0, 0.16 if not endc else 0.22)

        # ── каубел: ритмічна підкладка фонку ──────────────────────────────
        cb = [(0, ch[1][0], .75), (.75, ch[1][0], .25), (1.5, ch[1][1], .5), (2.5, ch[1][2], .75)]
        if intro:  notes, g = cb[:2], 0.13
        elif brk:  notes, g = cb[:1], 0.18
        elif endc: notes, g = cb[:1], 0.22
        else:      notes, g = cb,    0.26
        for bt, f, ln in notes:
            mix(dst, cowbell(f, BEAT * ln * 0.95), t0 + bt * BEAT, g)

        # ── ЛІД — головна мелодія; у вступі натяк, у дропі на повну ───────
        lead = LEAD[b % 4]
        if intro and b < 2:   notes, g = [], 0
        elif intro:           notes, g = lead[:2], 0.20      # за два такти до дропу — «зараз щось буде»
        elif brk:             notes, g = lead[:2], 0.24
        elif endc:            notes, g = lead[:1], 0.26
        else:                 notes, g = lead,    0.40
        for bt, f, ln in notes:
            mix(dst, pluck(f, BEAT * ln * 0.92), t0 + bt * BEAT, g)

    # ── райзер у дроп і в фінал ──────────────────────────────────────────
    mix(dst, riser(BAR * 2), (DROP_BAR - 2) * BAR, 0.40)
    mix(dst, riser(BAR, seed=9), (END_BAR - 1) * BAR, 0.30)

    # ── сайдчейн: усе присідає під бочку. Класика, від якої трек «дихає» ──
    duck = [1.0] * len(dst)
    hold, rel = int(0.012 * SR), int(0.20 * SR)
    for t in kick_hits:
        i0 = int(t * SR)
        for k in range(hold + rel):
            i = i0 + k
            if i >= len(duck): break
            v = 0.32 if k < hold else 0.32 + 0.68 * ((k - hold) / rel) ** 0.6
            if v < duck[i]: duck[i] = v
    for i in range(len(dst)): dst[i] *= duck[i]

    mix(dst, noise(total, hp=0.3, decay=total, seed=42), 0.0, 0.010)
    write_wav(out_path, dst, 0.92)

# ═════════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    if sys.argv[1] == "sfx":   build_sfx(sys.argv[2], sys.argv[3], sys.argv[4])
    elif sys.argv[1] == "phonk": build_phonk(float(sys.argv[2]), sys.argv[3])
    else: sys.exit("sfx | phonk")
