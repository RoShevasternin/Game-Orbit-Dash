#!/usr/bin/env python3
"""CPU-симуляція конвеєра BlurEffect: растр кола → піраміда ½ → ядро (пари, linear
sampling) → кубічний B-сплайн угору → білінійний семплінг екраном. Проти аналітики."""
import math, sys
exec(open(__file__.replace('sim.py', 'profile.py')).read().split('def main')[0])  # read_png, i0e, analytic, константи

BOX   = 236.0
SIGMA_WORK_MAX, KERNEL_SIGMAS, MAX_PAIRS = 12.0, 3.0, 18

def raster_circle(n):
    """Коло R=50 у центрі боксу 236, буфер n×n, покриття 4×4 суперсемплами."""
    t = BOX / n
    img = [[0.0]*n for _ in range(n)]
    c = BOX / 2
    for y in range(n):
        for x in range(n):
            cnt = 0
            for sy in range(4):
                for sx in range(4):
                    ux = (x + (sx + 0.5)/4) * t - c
                    uy = (y + (sy + 0.5)/4) * t - c
                    if ux*ux + uy*uy <= R*R: cnt += 1
            img[y][x] = cnt / 16.0
    return img

def down2(img):
    n = len(img) // 2
    return [[(img[2*y][2*x] + img[2*y][2*x+1] + img[2*y+1][2*x] + img[2*y+1][2*x+1]) / 4.0 for x in range(n)] for y in range(n)]

def build_kernel(s):
    r = max(1, min(int(math.ceil(KERNEL_SIGMAS * s)), 2 * MAX_PAIRS))
    raw = [math.exp(-(i*i) / (2*s*s)) for i in range(r + 1)]
    tot = raw[0] + 2 * sum(raw[1:])
    center = raw[0] / tot
    pairs = []
    i = 1
    while i <= r:
        w1 = raw[i] / tot
        w2 = raw[i+1] / tot if i + 1 <= r else 0.0
        ww = w1 + w2
        off = (i*w1 + (i+1)*w2) / ww if ww > 0 else float(i)
        pairs.append((off, ww))
        i += 2
    return center, pairs

def fetch_lin(row, x):
    """Білінійний семпл 1D на дробовому індексі текселя (clamp-to-edge)."""
    n = len(row)
    x = min(max(x, 0.0), n - 1.0)
    i = int(math.floor(x)); f = x - i
    j = min(i + 1, n - 1)
    return row[i] * (1 - f) + row[j] * f

def blur_1d(rows, center, pairs):
    out = []
    for row in rows:
        n = len(row)
        o = [0.0]*n
        for x in range(n):
            acc = row[x] * center
            for off, ww in pairs:
                acc += (fetch_lin(row, x + off) + fetch_lin(row, x - off)) * ww
            o[x] = acc
        out.append(o)
    return out

def transpose(img): return [list(c) for c in zip(*img)]

def blur_hv(img, s):
    center, pairs = build_kernel(s)
    return transpose(blur_1d(transpose(blur_1d(img, center, pairs)), center, pairs))

def sample_bilinear(img, x, y):
    """x,y — координати в текселях (центр текселя i на i+0.5), clamp-to-edge."""
    n = len(img)
    x = min(max(x - 0.5, 0.0), n - 1.0); y = min(max(y - 0.5, 0.0), n - 1.0)
    x0 = int(math.floor(x)); y0 = int(math.floor(y)); fx = x - x0; fy = y - y0
    x1 = min(x0 + 1, n - 1); y1 = min(y0 + 1, n - 1)
    return (img[y0][x0]*(1-fx) + img[y0][x1]*fx)*(1-fy) + (img[y1][x0]*(1-fx) + img[y1][x1]*fx)*fy

def upsample_cubic(src, n_dst):
    """Рівно як upsampleCubicFS: 4 білінійні семпли B-сплайна."""
    ns = len(src)
    def wts(f):
        f2, f3 = f*f, f*f*f
        w0 = (1 - 3*f + 3*f2 - f3)/6; w1 = (4 - 6*f2 + 3*f3)/6; w2 = (1 + 3*f + 3*f2 - 3*f3)/6; w3 = f3/6
        g0, g1 = w0 + w1, w2 + w3
        return g0, g1, w1/g0 - 1.0, w3/g1 + 1.0
    dst = [[0.0]*n_dst for _ in range(n_dst)]
    for y in range(n_dst):
        cy = (y + 0.5) / n_dst * ns - 0.5; fy = cy - math.floor(cy); iy = math.floor(cy)
        g0y, g1y, h0y, h1y = wts(fy)
        for x in range(n_dst):
            cx = (x + 0.5) / n_dst * ns - 0.5; fx = cx - math.floor(cx); ix = math.floor(cx)
            g0x, g1x, h0x, h1x = wts(fx)
            def S(hx, hy): return sample_bilinear(src, ix + hx + 0.5, iy + hy + 0.5)
            dst[y][x] = g0y*(g0x*S(h0x, h0y) + g1x*S(h1x, h0y)) + g1y*(g0x*S(h0x, h1y) + g1x*S(h1x, h1y))
    return dst

def upsample_bilinear(src, n_dst):
    ns = len(src)
    return [[sample_bilinear(src, (x + 0.5)/n_dst*ns, (y + 0.5)/n_dst*ns) for x in range(n_dst)] for y in range(n_dst)]

def run(name, density, cubic=True):
    n = int(math.ceil(BOX * density))
    img = raster_circle(n)
    s = SIGMA * density
    levels = 0; cur = img
    while s > SIGMA_WORK_MAX * 1.001 and len(cur) >= 16:
        cur = down2(cur); s /= 2; levels += 1
    nb = len(cur)
    cur = blur_hv(cur, s)
    if levels: cur = upsample_cubic(cur, n) if cubic else upsample_bilinear(cur, n)
    # екран: 3 px/юніт, профіль по 8 напрямках від центру
    ref = [analytic(float(r)) for r in range(RMAX + 1)]
    px_per_tex = n / BOX
    prof = []
    for d in range(8):
        a = 2*math.pi*d/8
        prof.append([sample_bilinear(cur, (BOX/2 + r*math.cos(a))*px_per_tex, (BOX/2 + r*math.sin(a))*px_per_tex) for r in range(RMAX + 1)])
    mean = [sum(p[r] for p in prof)/8 for r in range(RMAX + 1)]
    dev = [mean[r] - ref[r] for r in range(RMAX + 1)]
    rms = math.sqrt(sum(v*v for v in dev)/len(dev))
    iso = max(math.sqrt(sum((p[r]-mean[r])**2 for r in range(RMAX+1))/(RMAX+1)) for p in prof)
    # злами: друга різниця самого профілю по 1 юніту, проти аналітики (максимум надлишку)
    kink = max(abs((mean[r+1]-2*mean[r]+mean[r-1]) - (ref[r+1]-2*ref[r]+ref[r-1])) for r in range(1, RMAX))
    print(f"[{name}] buf {n}² → levels {levels} (bottom {nb}², s={s:.2f}, pairs={len(build_kernel(s)[1])})  "
          f"center {mean[0]:.4f}/{ref[0]:.4f}  RMS={rms:.4f}  maxdev={max(dev,key=abs):+.4f}  iso={iso:.4f}  kink={kink:.5f}")

if __name__ == "__main__":
    run("A VfxTexture auto d=0.414", 12.0 / SIGMA)
    run("B VfxGroup quant d=0.75", 0.75)
    run("C full d=3 (3 levels)", 3.0)
    run("B' d=0.75 bilinear up", 0.75, cubic=False)
