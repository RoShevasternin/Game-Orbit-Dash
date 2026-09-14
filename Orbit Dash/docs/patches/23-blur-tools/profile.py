#!/usr/bin/env python3
"""Радіальний профіль плям зі знімка проти аналітичного гауса Figma.

  python3 profile.py shot.png  name:cx:cy  [name:cx:cy ...]
    cx, cy — центр плями в px екрана (y зверху). Коло R=50 юнітів, blur 68 →
    σ = 0.426·68 = 28.97 юнітів, 3 px/юніт. Профіль 0..118 юнітів по 8 напрямках.
"""
import sys, struct, zlib, math

PX     = 3.0            # px на юніт (1080/360)
R      = 50.0           # радіус кола, юнітів
SIGMA  = 0.426 * 68.0   # σ гауса, юнітів
RMAX   = 118            # до межі bounds (236/2)
DIRS   = 8

def read_png(path):
    f = open(path, "rb").read()
    pos, idat, w, h, ct = 8, b"", None, None, None
    while pos < len(f):
        ln  = struct.unpack(">I", f[pos:pos+4])[0]
        typ = f[pos+4:pos+8]
        d   = f[pos+8:pos+8+ln]
        if typ == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", d[:10])
            assert bd == 8 and ct in (2, 6)
        elif typ == b"IDAT":
            idat += d
        pos += 12 + ln
    bpp = 4 if ct == 6 else 3
    raw = zlib.decompress(idat)
    stride = w * bpp
    out, prev, p = bytearray(), bytearray(stride), 0
    for _ in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if ft == 1:
            for i in range(bpp, stride): line[i] = (line[i] + line[i-bpp]) & 255
        elif ft == 2:
            for i in range(stride): line[i] = (line[i] + prev[i]) & 255
        elif ft == 3:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                line[i] = (line[i] + (a + prev[i])//2) & 255
        elif ft == 4:
            for i in range(stride):
                a = line[i-bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i-bpp] if i >= bpp else 0
                pa, pb, pc = abs(b-c), abs(a-c), abs(a+b-2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out += line; prev = line
    return w, h, bpp, bytes(out)

def i0e(x):
    """exp(-x)·I0(x) — рядом, стабільно до x≈700 не треба: тут x ≤ 10."""
    s, t, k = 1.0, 1.0, 1
    while True:
        t *= (x * x / 4.0) / (k * k)
        s += t
        if t < 1e-12 * s: break
        k += 1
    return s * math.exp(-x)

def analytic(r):
    """Диск радіуса R ⊛ гаус σ: f(r) = ∫0^R ρ/σ² · exp(−(r−ρ)²/2σ²) · e^{−x}I0(x) dρ, x = rρ/σ²."""
    n = 600
    dr = R / n
    s2 = SIGMA * SIGMA
    acc = 0.0
    for k in range(n):
        rho = (k + 0.5) * dr
        x = r * rho / s2
        acc += rho / s2 * math.exp(-((r - rho) ** 2) / (2 * s2)) * i0e(x) * dr
    return acc

def main():
    shot = sys.argv[1]
    stands = [a.split(":") for a in sys.argv[2:]]
    w, h, bpp, px = read_png(shot)

    def pixel(x, y):
        x = min(max(int(round(x)), 0), w - 1); y = min(max(int(round(y)), 0), h - 1)
        i = (y * w + x) * bpp
        return px[i], px[i+1], px[i+2]

    # фон — медіана патча біля лівого краю, посередині
    bg = [sorted(pixel(30 + dx, h // 2 + dy)[c] for dx in range(6) for dy in range(6))[18] for c in range(3)]
    print(f"shot {w}×{h}  bg={bg}")

    def alpha_at(x, y):
        p = pixel(x, y)
        # білий над фоном: a = (p−bg)/(255−bg); канал із найбільшим запасом
        best, bw = 0.0, 0.0
        for c in range(3):
            span = 255 - bg[c]
            if span > bw: bw, best = span, (p[c] - bg[c]) / span
        return best

    ref = [analytic(float(r)) for r in range(RMAX + 1)]
    print(f"analytic: center={ref[0]:.3f}  r=50 {ref[50]:.3f}  r=100 {ref[100]:.3f}  r=118 {ref[118]:.4f}")

    for name, cx, cy in stands:
        cx, cy = float(cx), float(cy)
        prof = []
        for d in range(DIRS):
            ang = 2 * math.pi * d / DIRS
            prof.append([alpha_at(cx + r * PX * math.cos(ang), cy - r * PX * math.sin(ang)) for r in range(RMAX + 1)])
        mean = [sum(prof[d][r] for d in range(DIRS)) / DIRS for r in range(RMAX + 1)]
        dev  = [mean[r] - ref[r] for r in range(RMAX + 1)]
        rms  = math.sqrt(sum(v * v for v in dev) / len(dev))
        rms_dir = [math.sqrt(sum((prof[d][r] - ref[r]) ** 2 for r in range(RMAX + 1)) / (RMAX + 1)) for d in range(DIRS)]
        spread  = max(rms_dir) - min(rms_dir)
        iso     = max(math.sqrt(sum((prof[d][r] - mean[r]) ** 2 for r in range(RMAX + 1)) / (RMAX + 1)) for d in range(DIRS))
        maxdev  = max(dev, key=abs)
        tail    = max((dev[r] for r in range(90, RMAX + 1)), key=abs)
        # злами: друга різниця відхилення (юніт²)
        kink    = max(abs(dev[r+1] - 2*dev[r] + dev[r-1]) for r in range(1, RMAX))
        print(f"\n[{name}] center={mean[0]:.3f} (ref {ref[0]:.3f})  RMS={rms:.4f}  maxdev={maxdev:+.4f}  tail(r>90)={tail:+.4f}")
        print(f"     iso(max dir vs mean)={iso:.4f}  spread(rms dirs)={spread:.4f}  kink={kink:.4f}")
        print("     r:   " + " ".join(f"{r:5d}" for r in range(0, RMAX + 1, 10)))
        print("     ref: " + " ".join(f"{ref[r]:5.3f}" for r in range(0, RMAX + 1, 10)))
        print("     got: " + " ".join(f"{mean[r]:5.3f}" for r in range(0, RMAX + 1, 10)))

main()
