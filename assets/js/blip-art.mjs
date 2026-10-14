// Бліп — код мультика (market/content/cartoon/ep01-first-light/js: util.js + draw.js), без змін; згенеровано site/build.py
// ─────────────────────────────────────────────────────────────────────────────
// util.js — математика мультика: ізинги, ключові кадри, кольори, детермінований «шум» (жодного Math.random:
// кадр — функція часу, перезапис дає ті самі кадри).
// ─────────────────────────────────────────────────────────────────────────────

const W = 1080, H = 1920, FPS = 60;
const D2R = Math.PI / 180, TAU = Math.PI * 2;

// темп партитури: 104 BPM (як рівень FIRST LIGHT), такт = 2.308 с; S(такт, доля) — секунда фільму
const BPM = 104, BEAT = 60 / BPM, BAR = 4 * BEAT, STEP = BEAT / 4;
const S = (bar, beat = 0) => (bar + beat / 4) * BAR;

const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
const lerp = (a, b, k) => a + (b - a) * k;
const inv = (a, b, x) => clamp((x - a) / (b - a));
const smooth = (k) => { k = clamp(k); return k * k * (3 - 2 * k); };
const smoother = (k) => { k = clamp(k); return k * k * k * (k * (k * 6 - 15) + 10); };
const easeOut = (k) => 1 - Math.pow(1 - clamp(k), 3);
const easeOut5 = (k) => 1 - Math.pow(1 - clamp(k), 5);
const easeIn = (k) => Math.pow(clamp(k), 3);
const easeIn2 = (k) => Math.pow(clamp(k), 2);
const easeInOut = (k) => { k = clamp(k); return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2; };
const easeInOut2 = (k) => { k = clamp(k); return k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2; };
const easeBack = (k, s = 1.70158) => { k = clamp(k) - 1; return 1 + (s + 1) * k * k * k + s * k * k; };
const easeElastic = (k) => { k = clamp(k); if (k === 0 || k === 1) return k; return Math.pow(2, -10 * k) * Math.sin((k * 10 - 0.75) * (TAU / 3)) + 1; };
// згасаюча пружина: 0 → 1 з перельотом (для «приземлень» і вистрибувань)
const spring = (k, freq = 3.2, damp = 5.5) => { if (k <= 0) return 0; return 1 - Math.exp(-damp * k) * Math.cos(freq * TAU * k); };
const pulse = (x, w) => (Math.abs(x) < w ? 0.5 + 0.5 * Math.cos(Math.PI * x / w) : 0);   // дзвін ширини w навколо 0
const bump = (k) => (k <= 0 || k >= 1 ? 0 : Math.sin(Math.PI * k));
const angDiff = (a, b) => { let d = (a - b) % 360; if (d > 180) d -= 360; if (d < -180) d += 360; return d; };

// детермінований шум
const hash = (i) => { const x = Math.sin(i * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
const hash2 = (i, j) => hash(i * 57.13 + j * 113.7 + 0.31);
function noise1(x) { const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f); return lerp(hash(i), hash(i + 1), u) * 2 - 1; }
// плавний «ручний» дрейф камери: сума трьох октав
const wobble = (t, seed = 0) => noise1(t * 0.9 + seed * 17.3) * 0.6 + noise1(t * 2.1 + seed * 5.1) * 0.3 + noise1(t * 4.7 + seed * 9.7) * 0.1;
let _seed = 1;
const srand = (s) => { _seed = s >>> 0 || 1; };
const rnd = () => ((_seed = (_seed * 16807) % 2147483647) / 2147483647);

// ключові кадри: [[t, v], …] → значення в t; ease — ізинг відрізка (за замовчуванням easeInOut)
function key(track, t, ease = easeInOut) {
  if (t <= track[0][0]) return track[0][1];
  for (let i = 1; i < track.length; i++) {
    if (t <= track[i][0]) {
      const [ta, va] = track[i - 1], [tb, vb] = track[i];
      const e = track[i][2] || ease;
      const k = tb > ta ? e((t - ta) / (tb - ta)) : 1;
      if (Array.isArray(va)) return va.map((a, j) => lerp(a, vb[j], k));
      return lerp(va, vb, k);
    }
  }
  return track[track.length - 1][1];
}
// ступінчасте значення (останнє ≤ t)
function step(track, t) { let v = track[0][1]; for (const [a, b] of track) if (a <= t) v = b; return v; }

// кольори
const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const rgba = (h, a = 1) => { const [r, g, b] = typeof h === "string" ? hex(h) : h; return `rgba(${r | 0},${g | 0},${b | 0},${clamp(a)})`; };
const mixc = (a, b, k) => { const A = typeof a === "string" ? hex(a) : a, B = typeof b === "string" ? hex(b) : b; return A.map((v, i) => v + (B[i] - v) * clamp(k)); };
const tohex = (c) => "#" + c.map((v) => Math.round(clamp(v, 0, 255)).toString(16).padStart(2, "0")).join("");
const mixh = (a, b, k) => tohex(mixc(a, b, k));
const c01 = (h) => hex(h).map((v) => v / 255);
const grey = (h, k) => { const c = typeof h === "string" ? hex(h) : h; const l = c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11; return tohex(c.map((v) => lerp(v, l, k))); };

const bez = (p0, p1, p2, p3, k) => { const u = 1 - k; return [0, 1].map((i) => u * u * u * p0[i] + 3 * u * u * k * p1[i] + 3 * u * k * k * p2[i] + k * k * k * p3[i]); };
const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);

// ─────────────────────────────────────────────────────────────────────────────
// draw.js — усе, що малюється на шарі сцени: світіння, кільця орбіт (плоскі й у перспективі), шипи (фігура SPIKE
// гри), кристали (GEM), іскри, корона (CROWN), зорі-персонажі з обличчям і БЛІП — тіло, очі з повіками й сльозами,
// брови, рот, рум'янець; міміка — набір чисел, які плавно змішуються між позами.
// ─────────────────────────────────────────────────────────────────────────────

const SPIKE = new Path2D("M63.7838 45.4054L43.027 43.027L44.973 64L31.7838 47.5676L18.5946 63.7838L20.973 43.027L0 44.973L16.4324 31.7838L0.216229 18.5946L20.973 20.973L19.027 0L32.2162 16.4324L45.4054 0.216229L43.027 20.973L64 19.027L47.5676 32.2162L63.7838 45.4054Z");
const GEM = new Path2D("M61.5518 26.0896L37.9104 2.44816C34.6462 -0.816052 29.3538 -0.816052 26.0896 2.44816L2.44816 26.0896C-0.816052 29.3538 -0.816052 34.6462 2.44815 37.9104L26.0896 61.5518C29.3538 64.816 34.6462 64.816 37.9104 61.5518L61.5518 37.9104C64.8161 34.6462 64.816 29.3538 61.5518 26.0896Z");
const CROWN = new Path2D("M4 36L0 8L16 20L30 0L44 20L60 8L56 36ZM4 42L56 42L56 50L4 50Z");
const INK = "#0B1030";

// ── світіння: кеш радіальних спрайтів ──
function sprite(size, fn) { const c = document.createElement("canvas"); c.width = c.height = size; fn(c.getContext("2d"), size); return c; }
const _glow = {};
function glowOf(col) {
  const key = typeof col === "string" ? col : tohex(col);
  return _glow[key] || (_glow[key] = sprite(256, (g, s) => {
    const gr = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
    gr.addColorStop(0, rgba(key, 1)); gr.addColorStop(0.18, rgba(key, 0.55)); gr.addColorStop(0.45, rgba(key, 0.16));
    gr.addColorStop(0.75, rgba(key, 0.04)); gr.addColorStop(1, rgba(key, 0));
    g.fillStyle = gr; g.fillRect(0, 0, s, s);
  }));
}
function glow(ctx, x, y, r, col, a) {
  if (a <= 0.002 || r <= 0.5) return;
  const p = ctx.globalAlpha, op = ctx.globalCompositeOperation;
  ctx.globalCompositeOperation = "lighter"; ctx.globalAlpha = clamp(a);
  ctx.drawImage(glowOf(col), x - r, y - r, r * 2, r * 2);
  ctx.globalAlpha = p; ctx.globalCompositeOperation = op;
}

// ── кільце орбіти (плоске): lit(φ°) → 0..1 — підсвітка доріжки; hue(φ°) → колір підсвітки ──
function ring(ctx, cx, cy, r, o) {
  const base = o.color || "#8FA8FF", a = o.alpha === undefined ? 1 : o.alpha, wz = o.wz || 1;
  if (a <= 0.003 || r < 2) return;
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  ctx.lineCap = "butt";
  // основа: тьмяна доріжка
  const dim = o.dim === undefined ? 0.35 : o.dim;
  if (dim > 0.002) {
    ctx.strokeStyle = rgba(base, 0.10 * a * dim); ctx.lineWidth = 26 * wz; ctx.beginPath(); ctx.arc(cx, cy, r, 0, TAU); ctx.stroke();
    ctx.strokeStyle = rgba(base, 0.22 * a * dim); ctx.lineWidth = 9 * wz; ctx.beginPath(); ctx.arc(cx, cy, r, 0, TAU); ctx.stroke();
    ctx.strokeStyle = rgba(mixh(base, "#FFFFFF", 0.5), 0.75 * a * dim); ctx.lineWidth = Math.max(1.2, 2.4 * wz); ctx.beginPath(); ctx.arc(cx, cy, r, 0, TAU); ctx.stroke();
  }
  // підсвічені ділянки: один конічний градієнт на кільце (без швів між сегментами)
  if (o.lit) {
    const N = o.segs || 180, rot = (o.rot || 0) * D2R;
    let any = 0;
    const ks = new Float32Array(N + 1), cols = [];
    for (let i = 0; i <= N; i++) { const phi = (i % N) / N * 360; ks[i] = clamp(o.lit(phi), 0, 3); any = Math.max(any, ks[i]); cols.push(o.hue ? o.hue(phi) : (o.litColor || base)); }
    if (any > 0.004) {
      const pass = (wMul, aMul, whiten) => {
        const g = ctx.createConicGradient(rot, cx, cy);
        for (let i = 0; i <= N; i++) g.addColorStop(i / N, rgba(whiten ? mixh(cols[i], "#FFFFFF", whiten) : cols[i], clamp(ks[i] * aMul * a)));
        ctx.strokeStyle = g; ctx.lineWidth = wMul; ctx.beginPath(); ctx.arc(cx, cy, r, 0, TAU); ctx.stroke();
      };
      const G = o.litGain || 1;
      pass(46 * wz * Math.sqrt(G), 0.16 * G, 0); pass(15 * wz * Math.sqrt(G), 0.42 * G, 0); pass(Math.max(1.6, 4.2 * wz), 0.95, 0.65);
    }
  }
  ctx.restore();
}

// ── кільце в перспективі (проліт): 3D-коло → полілінія; cam = {x,y,z,f,cx,cy} ──
function ring3d(ctx, C, R, nrm, cam, col, a, pulses, t) {
  // базис площини кільця
  const n = nrm, up = Math.abs(n[1]) < 0.9 ? [0, 1, 0] : [1, 0, 0];
  const u = norm3(cross3(up, n)), v = cross3(n, u);
  const N = 160, pts = [];
  for (let i = 0; i <= N; i++) {
    const th = i / N * TAU;
    const P = [C[0] + R * (Math.cos(th) * u[0] + Math.sin(th) * v[0]), C[1] + R * (Math.cos(th) * u[1] + Math.sin(th) * v[1]), C[2] + R * (Math.cos(th) * u[2] + Math.sin(th) * v[2])];
    pts.push(proj3(P, cam));
  }
  ctx.save(); ctx.globalCompositeOperation = "lighter"; ctx.lineCap = "round";
  for (let i = 0; i < N; i++) {
    const p = pts[i], q = pts[i + 1];
    if (!p || !q) continue;
    const s = (p[2] + q[2]) / 2;                              // масштаб (≈ f / z)
    const fade = clamp(s * 2.2) * clamp((4 - s) / 2.5);
    if (fade < 0.01) continue;
    let k = 1;
    if (pulses) for (const pp of pulses) { const d = ((i / N - pp(t)) % 1 + 1) % 1; k += 2.4 * Math.exp(-Math.pow(Math.min(d, 1 - d) * 18, 2)); }
    const w = Math.max(0.8, 5 * s);
    ctx.strokeStyle = rgba(col, 0.08 * a * fade * k); ctx.lineWidth = w * 7; ctx.beginPath(); ctx.moveTo(p[0], p[1]); ctx.lineTo(q[0], q[1]); ctx.stroke();
    ctx.strokeStyle = rgba(col, 0.3 * a * fade * k); ctx.lineWidth = w * 2.2; ctx.beginPath(); ctx.moveTo(p[0], p[1]); ctx.lineTo(q[0], q[1]); ctx.stroke();
    ctx.strokeStyle = rgba(mixh(col, "#FFFFFF", 0.6), 0.85 * a * fade * Math.min(k, 1.8)); ctx.lineWidth = w * 0.6; ctx.beginPath(); ctx.moveTo(p[0], p[1]); ctx.lineTo(q[0], q[1]); ctx.stroke();
  }
  ctx.restore();
}
const cross3 = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm3 = (a) => { const l = Math.hypot(a[0], a[1], a[2]) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
function proj3(P, cam) {
  const z = P[2] - cam.z; if (z < 20) return null;
  const s = cam.f / z;
  return [cam.cx + (P[0] - cam.x) * s, cam.cy + (P[1] - cam.y) * s, s];
}

// ── шип: фігура SPIKE гри; frozen — «замерзла зоря» (темне ядро, червоний край) ──
const _spk = {};
function spikeSprite(col) {
  return _spk[col] || (_spk[col] = sprite(160, (g) => {
    g.translate(16, 16); g.scale(2, 2);
    const gr = g.createRadialGradient(32, 32, 2, 32, 32, 36);
    gr.addColorStop(0, mixh(col, "#FFFFFF", 0.3)); gr.addColorStop(0.45, col); gr.addColorStop(1, mixh(col, "#300010", 0.55));
    g.fillStyle = gr; g.fill(SPIKE);
    g.lineWidth = 1.2; g.strokeStyle = rgba(mixh(col, "#FFFFFF", 0.6), 0.8); g.stroke(SPIKE);
  }));
}
function spike(ctx, x, y, s, rot, o = {}) {
  const col = o.color || "#F0284F", a = o.alpha === undefined ? 1 : o.alpha;
  if (a <= 0.003 || s < 1) return;
  glow(ctx, x, y, s * 1.9, col, 0.32 * a * (o.glow === undefined ? 1 : o.glow));
  ctx.save(); ctx.globalAlpha = clamp(a); ctx.translate(x, y); ctx.rotate(rot);
  const k = s / 64; ctx.drawImage(spikeSprite(col), -80 * k, -80 * k, 160 * k, 160 * k);
  ctx.restore();
}

// ── кристал (GEM) і іскра ──
const _gem = {};
function gemSprite(col) {
  return _gem[col] || (_gem[col] = sprite(160, (g) => {
    g.translate(16, 16); g.scale(2, 2);
    const gr = g.createLinearGradient(0, 0, 64, 64);
    gr.addColorStop(0, mixh(col, "#FFFFFF", 0.75)); gr.addColorStop(0.5, col); gr.addColorStop(1, mixh(col, "#002030", 0.35));
    g.fillStyle = gr; g.fill(GEM);
    g.save(); g.translate(32, 32); g.scale(0.5, 0.5); g.translate(-32, -32); g.globalAlpha = 0.55; g.fillStyle = "#FFFFFF"; g.fill(GEM); g.restore();
    g.lineWidth = 1.5; g.strokeStyle = "rgba(255,255,255,0.7)"; g.stroke(GEM);
  }));
}
function gem(ctx, x, y, s, rot, col, a = 1) {
  if (a <= 0.003 || s < 1) return;
  glow(ctx, x, y, s * 2.2, col, 0.55 * a);
  ctx.save(); ctx.globalAlpha = clamp(a); ctx.translate(x, y); ctx.rotate(rot);
  const k = s / 64; ctx.drawImage(gemSprite(col), -80 * k, -80 * k, 160 * k, 160 * k); ctx.restore();
}
function twinkle(ctx, x, y, s, rot, col = "#FFFFFF", a = 1) {
  if (a <= 0.003 || s < 0.5) return;
  glow(ctx, x, y, s * 4.5, col, 0.6 * a);
  ctx.save(); ctx.globalCompositeOperation = "lighter"; ctx.globalAlpha = clamp(a); ctx.translate(x, y); ctx.rotate(rot);
  for (const ang of [0, Math.PI / 2]) {
    ctx.rotate(ang);
    const g = ctx.createLinearGradient(-s * 3, 0, s * 3, 0);
    g.addColorStop(0, rgba(col, 0)); g.addColorStop(0.5, rgba("#FFFFFF", 1)); g.addColorStop(1, rgba(col, 0));
    ctx.fillStyle = g; ctx.beginPath(); ctx.ellipse(0, 0, s * 3, s * 0.32, 0, 0, TAU); ctx.fill();
  }
  ctx.restore();
}
// ── корона рівня ──
function crown(ctx, x, y, s, rot, a = 1, shine = 0) {
  if (a <= 0.003) return;
  glow(ctx, x, y, s * 1.6, "#FFC857", 0.55 * a);
  ctx.save(); ctx.globalAlpha = clamp(a); ctx.translate(x, y); ctx.rotate(rot); ctx.scale(s / 60, s / 60); ctx.translate(-30, -25);
  const g = ctx.createLinearGradient(0, 0, 0, 50); g.addColorStop(0, "#FFF6C2"); g.addColorStop(0.5, "#FFC857"); g.addColorStop(1, "#E08A1E");
  ctx.fillStyle = g; ctx.fill(CROWN);
  ctx.strokeStyle = "rgba(120,60,0,0.45)"; ctx.lineWidth = 2.2; ctx.stroke(CROWN);
  ctx.fillStyle = "#FF4D8D"; ctx.beginPath(); ctx.arc(30, 14, 4.2, 0, TAU); ctx.fill();
  ctx.fillStyle = "#6FE3FF"; ctx.beginPath(); ctx.arc(12, 30, 3.2, 0, TAU); ctx.arc(48, 30, 3.2, 0, TAU); ctx.fill();
  if (shine > 0 && shine < 1) {                               // відблиск, що пробігає короною
    ctx.clip(CROWN);
    const sx = -20 + 100 * shine;
    const sg = ctx.createLinearGradient(sx - 12, 0, sx + 12, 50); sg.addColorStop(0, "rgba(255,255,255,0)"); sg.addColorStop(0.5, "rgba(255,255,255,0.85)"); sg.addColorStop(1, "rgba(255,255,255,0)");
    ctx.fillStyle = sg; ctx.fillRect(-10, -10, 80, 70);
  }
  ctx.restore();
}

// ── крапля сльози (верх — гострий), x,y — центр кульки ──
function teardrop(ctx, x, y, s, a = 1, rot = 0, col = "#BDEFFF") {
  if (a <= 0.003) return;
  ctx.save(); ctx.globalAlpha = clamp(a); ctx.translate(x, y); ctx.rotate(rot);
  const g = ctx.createRadialGradient(-s * 0.3, -s * 0.2, s * 0.1, 0, 0, s);
  g.addColorStop(0, "#FFFFFF"); g.addColorStop(0.5, col); g.addColorStop(1, mixh(col, "#3A8FD0", 0.5));
  ctx.fillStyle = g; ctx.beginPath();
  ctx.moveTo(0, -s * 2.0); ctx.bezierCurveTo(s * 0.35, -s * 1.2, s, -s * 0.6, s, 0); ctx.arc(0, 0, s, 0, Math.PI);
  ctx.bezierCurveTo(-s, -s * 0.6, -s * 0.35, -s * 1.2, 0, -s * 2.0); ctx.fill();
  ctx.fillStyle = "rgba(255,255,255,0.9)"; ctx.beginPath(); ctx.ellipse(-s * 0.35, -s * 0.25, s * 0.22, s * 0.32, -0.4, 0, TAU); ctx.fill();
  ctx.restore();
  glow(ctx, x, y, s * 3, col, 0.35 * a);
}

// ═════════════════════════════════════════════════════════ ОБЛИЧЧЯ ══
// Міміка — числа (змішуються лінійно): око (розмір, повіки, нахил повік), брови, рот (усмішка, відкритість, «о»,
// тремтіння), рум'янець, щасливі очі ^^, примруження > <, сльози, зірочки в очах, підморгування, поворот обличчя.
const POSES = {
  neutral: {},
  sleep: { closed: 1, smile: 0.25, browA: 0.35, blush: 0.25 },
  curious: { browY: -0.06, browTilt: -0.25, smile: 0.12, eyeH: 1.04 },
  surprised: { eyeW: 1.12, eyeH: 1.1, browY: -0.16, browTilt: -0.1, open: 0.5, round: 1, smile: 0 },
  scared: { eyeW: 1.06, eyeH: 1.05, browY: -0.1, browTilt: -0.7, smile: -0.35, wobble: 0.6, open: 0.12 },
  sad: { lid: 0.24, lidTilt: -0.7, browY: -0.04, browTilt: -0.85, browCurve: 0.3, smile: -0.55, wobble: 0.25, tears: 0.45 },
  cry: { lid: 0.3, lidTilt: -0.8, browY: -0.06, browTilt: -1, browCurve: 0.4, smile: -0.8, wobble: 1, tears: 1, open: 0.18 },
  happy: { happy: 1, smile: 0.9, open: 0.45, blush: 0.65, browY: -0.08, browTilt: -0.15 },
  joy: { happy: 1, smile: 1, open: 0.85, blush: 0.85, browY: -0.12, browTilt: -0.2 },
  smile: { smile: 0.75, blush: 0.4, lidBot: 0.18, browTilt: -0.1 },
  grin: { smile: 0.9, open: 0.3, blush: 0.4, lid: 0.12, lidTilt: 0.35, browTilt: 0.45 },
  determined: { lid: 0.28, lidTilt: 0.6, browY: 0.04, browTilt: 1, smile: 0.12, mouthW: 0.85 },
  focus: { lid: 0.34, lidTilt: 0.7, browY: 0.05, browTilt: 1.1, smile: -0.05, mouthW: 0.7 },
  squeeze: { squeeze: 1, smile: -0.4, wobble: 0.4, browTilt: 0.6, mouthW: 1.1, open: 0.15 },
  wink: { winkR: 1, smile: 0.85, open: 0.25, blush: 0.5, browTilt: -0.1 },
  hopeful: { smile: 0.35, tears: 0.65, browTilt: -0.45, browY: -0.06, lidBot: 0.12, blush: 0.25 },
  touched: { smile: 0.55, tears: 0.85, browTilt: -0.55, browY: -0.05, lidBot: 0.2, blush: 0.35, lid: 0.1, lidTilt: 0.15 },
  resolve: { smile: 0.45, tears: 0.5, lid: 0.2, lidTilt: 0.45, browTilt: 0.75, lidBot: 0.12, blush: 0.2 },
  starry: { starEyes: 1, eyeW: 1.12, eyeH: 1.08, smile: 0.7, open: 0.5, blush: 0.6, browY: -0.12 },
  ooh: { open: 0.55, round: 1, eyeW: 1.08, eyeH: 1.06, browY: -0.1, smile: 0.1 },
  ouch: { squeeze: 1, open: 0.55, round: 0.4, smile: -0.6, browTilt: 0.2, wobble: 0.3 },
  dazed: { lid: 0.45, lidTilt: -0.2, smile: -0.2, wobble: 0.3, browTilt: -0.5, eyeH: 0.92 },
};
const FACE0 = { eyeW: 1, eyeH: 1, lid: 0, lidTilt: 0, lidBot: 0, closed: 0, happy: 0, squeeze: 0, winkR: 0, starEyes: 0,
  browY: 0, browTilt: 0, browCurve: 0, browA: 1, smile: 0.3, open: 0, round: 0, mouthW: 1, wobble: 0, blush: 0.2, tears: 0 };
function pose(name) { return Object.assign({}, FACE0, POSES[name] || {}); }
function mixPose(a, b, k) { const o = {}; for (const key in FACE0) o[key] = lerp(a[key], b[key], k); return o; }
// трек поз: [[t, "назва"], …] — перехід за blend с
function poseAt(track, t, blend = 0.18) {
  let i = 0; while (i < track.length - 1 && track[i + 1][0] <= t) i++;
  const cur = pose(track[i][1]);
  if (i === 0) return cur;
  const k = smooth((t - track[i][0]) / (track[i][2] || blend));
  return k >= 1 ? cur : mixPose(pose(track[i - 1][1]), cur, k);
}

// ═════════════════════════════════════════════════════════ БЛІП ══
// st: { face, look:[x,y] (−1..1), turn (−1..1), blink (0..1), sx, sy, sa (розтяг уздовж кута sa), glow, heart (0..1),
//       dim (0..1 — сірий), body (кольори), rim:[кут°, колір, сила], t (час — тремтіння), tearPhase }
const BLIP_BODY = { c0: "#FFFFFF", c1: "#DDFBFF", c2: "#6FE7FA", c3: "#17AFD8", c4: "#0B7AAB", glow: "#00E5FF" };
function blip(ctx, x, y, r, st) {
  const F = st.face || pose("neutral");
  const body = st.body || BLIP_BODY;
  const dim = clamp(st.dim || 0);
  const col = (h) => dim > 0 ? mixh(h, grey(h, 1), dim * 0.85) : h;
  const t = st.t || 0;
  // світіння
  const gA = (st.glow === undefined ? 1 : st.glow) * (1 - dim * 0.8);
  if (gA > 0.01) {
    glow(ctx, x, y, r * 3.4, body.glow, 0.42 * gA);
    glow(ctx, x, y, r * 1.6, "#FFFFFF", 0.22 * gA);
  }
  ctx.save(); ctx.translate(x, y);
  // розтяг / стиск (об'єм сталий)
  const sx = st.sx || 1, sy = st.sy || 1, sa = (st.sa || 0) * D2R;
  ctx.rotate(sa); ctx.scale(sx, sy); ctx.rotate(-sa);
  if (st.tilt) ctx.rotate(st.tilt * D2R);
  // тіло
  const g = ctx.createRadialGradient(-r * 0.32, -r * 0.38, r * 0.04, 0, 0, r * 1.02);
  g.addColorStop(0, col(body.c0)); g.addColorStop(0.3, col(body.c1)); g.addColorStop(0.64, col(body.c2)); g.addColorStop(0.9, col(body.c3)); g.addColorStop(1, col(body.c4));
  ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.fill();
  // серце-світло всередині (пульсує в біт)
  const heart = clamp(st.heart || 0);
  if (heart > 0.01) {
    const hg = ctx.createRadialGradient(0, r * 0.12, 0, 0, r * 0.12, r * 0.8);
    hg.addColorStop(0, `rgba(255,255,255,${0.75 * heart})`); hg.addColorStop(0.4, `rgba(190,250,255,${0.35 * heart})`); hg.addColorStop(1, "rgba(120,230,255,0)");
    ctx.fillStyle = hg; ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.fill();
  }
  // контровий світ (від зорі / сцени)
  if (st.rim) {
    const [ang, rc, ra] = st.rim;
    ctx.save(); ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.clip();
    // серп на освітленому боці: центр градієнта зсунуто від світла — край з боку світла далі від центру
    const a = ang * D2R, ox = -Math.cos(a) * r * 0.22, oy = -Math.sin(a) * r * 0.22;
    const rg = ctx.createRadialGradient(ox, oy, 0, ox, oy, r * 1.22);
    rg.addColorStop(0, rgba(rc, 0)); rg.addColorStop(0.8, rgba(rc, 0)); rg.addColorStop(0.92, rgba(rc, 0.8 * ra)); rg.addColorStop(1, rgba(rc, 0.9 * ra));
    ctx.globalCompositeOperation = "lighter"; ctx.fillStyle = rg; ctx.fillRect(-r * 1.2, -r * 1.2, r * 2.4, r * 2.4);
    ctx.restore();
  }
  // край і відблиск
  ctx.strokeStyle = `rgba(255,255,255,${0.55 * (1 - dim * 0.6)})`; ctx.lineWidth = Math.max(1, r * 0.035); ctx.beginPath(); ctx.arc(0, 0, r * 0.985, 0, TAU); ctx.stroke();
  ctx.fillStyle = `rgba(255,255,255,${0.7 * (1 - dim * 0.5)})`;
  ctx.beginPath(); ctx.ellipse(-r * 0.42, -r * 0.52, r * 0.2, r * 0.11, -0.65, 0, TAU); ctx.fill();
  ctx.beginPath(); ctx.ellipse(-r * 0.62, -r * 0.24, r * 0.05, r * 0.035, -1.1, 0, TAU); ctx.fill();
  face(ctx, r, F, st, g, t, dim);
  ctx.restore();
}

// обличчя на кулі радіуса r (у системі координат тіла); bodyFill — той самий градієнт (повіки кольору тіла)
function face(ctx, r, F, st, bodyFill, t, dim = 0, ink = INK, lashes = false) {
  const look = st.look || [0, 0], turn = clamp(st.turn || 0, -1, 1);
  const fx = turn * r * 0.3 + look[0] * r * 0.08, fy = look[1] * r * 0.09;
  const ex = r * 0.33, ey = -r * 0.06;
  const ew0 = r * 0.175 * F.eyeW, eh0 = r * 0.305 * F.eyeH;
  let blink = clamp(st.blink || 0);
  ctx.lineCap = "round"; ctx.lineJoin = "round";
  const inkC = dim > 0 ? mixh(ink, "#2A3040", dim * 0.5) : ink;
  for (const side of [-1, 1]) {
    // поворот: дальнє око вужче й ближче до центру
    const far = turn * side < 0 ? Math.abs(turn) : 0, near = turn * side > 0 ? Math.abs(turn) : 0;
    const cx = side * ex * (1 - 0.32 * far + 0.06 * near) + fx, cy = ey + fy;
    const ew = ew0 * (1 - 0.38 * far + 0.08 * near), eh = eh0;
    const inner = -side;                                        // напрям до носа (+x для лівого ока)
    const wink = side > 0 ? F.winkR : 0;
    const closedK = Math.max(F.closed, wink, blink);
    // ── закриті очі (сон / підморгування / кліпання): дуга ──
    if (F.happy > 0.5 || closedK > 0.85 || F.squeeze > 0.5) {
      ctx.strokeStyle = inkC; ctx.lineWidth = r * 0.075;
      if (F.squeeze > 0.5) {                                    // > <
        ctx.beginPath(); ctx.moveTo(cx - inner * ew * 1.05, cy - eh * 0.42); ctx.lineTo(cx + inner * ew * 0.75, cy); ctx.lineTo(cx - inner * ew * 1.05, cy + eh * 0.42); ctx.stroke();
      } else if (F.happy > 0.5 || (wink > 0.5 && F.happy <= 0.5 && side > 0 && F.winkR > 0.5)) {   // ^^
        ctx.beginPath(); ctx.arc(cx, cy + eh * 0.28, ew * 1.05, Math.PI * 1.12, Math.PI * 1.88); ctx.stroke();
      } else {                                                  // сон / кліп: дуга донизу
        ctx.beginPath(); ctx.arc(cx, cy - eh * 0.05, ew * 0.95, Math.PI * 0.12, Math.PI * 0.88); ctx.stroke();
        if (lashes) for (let i = 0; i < 3; i++) {
          const a = Math.PI * (0.28 + i * 0.2) * (side < 0 ? 1 : 1), lx = cx + Math.cos(a) * ew * 0.95 * (side < 0 ? 1 : 1), ly = cy - eh * 0.05 + Math.sin(a) * ew * 0.95;
          const dx = Math.cos(a), dy = Math.sin(a);
          ctx.lineWidth = r * 0.04; ctx.beginPath(); ctx.moveTo(lx, ly); ctx.lineTo(lx + dx * r * 0.07, ly + dy * r * 0.07); ctx.stroke(); ctx.lineWidth = r * 0.075;
        }
      }
      continue;
    }
    const open = 1 - closedK;
    const h = eh * Math.max(0.06, open) * (1 - 0.45 * F.happy);
    // ── око: темний еліпс ──
    ctx.save();
    ctx.beginPath(); ctx.ellipse(cx, cy, ew, h, 0, 0, TAU); ctx.fillStyle = inkC; ctx.fill();
    ctx.clip();
    // сльози, що набігають: світла смуга знизу з хвилястим краєм
    if (F.tears > 0.02) {
      const lvl = cy + h - 2 * h * 0.42 * F.tears;
      ctx.fillStyle = `rgba(150,225,255,${0.55 * F.tears})`;
      ctx.beginPath(); ctx.moveTo(cx - ew * 1.2, cy + h * 1.2);
      for (let i = 0; i <= 12; i++) { const xx = cx - ew * 1.2 + i / 12 * ew * 2.4; ctx.lineTo(xx, lvl + Math.sin(i * 1.3 + t * 9) * h * 0.04); }
      ctx.lineTo(cx + ew * 1.2, cy + h * 1.2); ctx.closePath(); ctx.fill();
    }
    // відблиски (тремтять, коли сльози)
    const tw = F.tears * Math.sin(t * 23 + side) * ew * 0.06;
    ctx.fillStyle = "#FFFFFF";
    ctx.beginPath(); ctx.arc(cx + ew * 0.32 - look[0] * ew * 0.12 + tw, cy - h * 0.4, ew * (0.36 + 0.1 * F.tears), 0, TAU); ctx.fill();
    ctx.beginPath(); ctx.arc(cx - ew * 0.3 + tw, cy + h * 0.38, ew * (0.15 + 0.06 * F.tears), 0, TAU); ctx.fill();
    if (F.tears > 0.3) { ctx.globalAlpha = (F.tears - 0.3) * 1.2; ctx.beginPath(); ctx.arc(cx + ew * 0.05, cy + h * 0.66, ew * 0.1, 0, TAU); ctx.fill(); ctx.globalAlpha = 1; }
    if (F.starEyes > 0.02) {                                    // зірочки в очах
      ctx.save(); ctx.globalAlpha = F.starEyes; ctx.translate(cx, cy + h * 0.05); ctx.rotate(t * 2.2);
      const s = ew * 0.72 * (0.85 + 0.15 * Math.sin(t * 9)); ctx.fillStyle = "#FFE08A"; ctx.beginPath();
      for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4, rr = i % 2 ? s * 0.38 : s; ctx.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); }
      ctx.closePath(); ctx.fill(); ctx.restore();
    }
    // ── повіки кольору тіла: верхня (з нахилом), нижня (усмішка очима) ──
    const lid = clamp(F.lid + (1 - open) * 0.9);
    if (lid > 0.005) {
      const top = cy - h * 1.05, yl = top + 2.1 * h * lid, k = F.lidTilt * h * 0.9 / ew;
      ctx.fillStyle = bodyFill; ctx.beginPath();
      ctx.moveTo(cx - ew * 1.3, cy - h * 1.4); ctx.lineTo(cx + ew * 1.3, cy - h * 1.4);
      ctx.lineTo(cx + ew * 1.3, yl + k * inner * (ew * 1.3)); ctx.lineTo(cx - ew * 1.3, yl - k * inner * (ew * 1.3)); ctx.closePath(); ctx.fill();
      ctx.strokeStyle = rgba(inkC, 0.85); ctx.lineWidth = r * 0.03;
      ctx.beginPath(); ctx.moveTo(cx - ew * 1.3, yl - k * inner * (ew * 1.3)); ctx.lineTo(cx + ew * 1.3, yl + k * inner * (ew * 1.3)); ctx.stroke();
    }
    if (F.lidBot > 0.005) {
      ctx.fillStyle = bodyFill; ctx.beginPath();
      ctx.ellipse(cx, cy + h * (1.9 - 1.1 * F.lidBot), ew * 1.6, h, 0, 0, TAU); ctx.fill();
    }
    ctx.restore();
    // брови
    if (F.browA > 0.01) {
      const by = cy - eh * 1.12 - r * 0.07 + F.browY * r - (st.browLift || 0) * r * 0.05;
      const xi = cx + inner * ew * 0.95, xo = cx - inner * ew * 1.05;
      const yi = by + F.browTilt * r * 0.075, yo = by - F.browTilt * r * 0.02;
      ctx.strokeStyle = rgba(inkC, F.browA); ctx.lineWidth = r * 0.05;
      ctx.beginPath(); ctx.moveTo(xo, yo); ctx.quadraticCurveTo((xi + xo) / 2, (yi + yo) / 2 - r * (0.035 + F.browCurve * 0.05), xi, yi); ctx.stroke();
    }
  }
  // ── рот ──
  const mx = fx * 0.85, my = r * 0.36 + fy * 0.6;
  const mw = r * 0.17 * F.mouthW * (1 + 0.25 * F.open * (1 - F.round));
  const wob = F.wobble * Math.sin(t * 31) * r * 0.012;
  ctx.strokeStyle = inkC; ctx.fillStyle = inkC; ctx.lineWidth = r * 0.062;
  if (F.open > 0.06) {
    if (F.round > 0.5) {                                      // «о»
      const rx = r * (0.07 + 0.05 * F.open), ry = r * (0.06 + 0.11 * F.open);
      ctx.beginPath(); ctx.ellipse(mx, my + ry * 0.4, rx, ry, 0, 0, TAU); ctx.fill();
    } else {
      const sm = F.smile;
      const topY = my - sm * r * 0.035, botY = my + sm * r * 0.1 + F.open * r * 0.2;
      ctx.beginPath(); ctx.moveTo(mx - mw, my - sm * r * 0.06 + wob);
      ctx.quadraticCurveTo(mx, topY + r * 0.02, mx + mw, my - sm * r * 0.06 - wob);
      ctx.quadraticCurveTo(mx, botY + r * 0.12 * F.open + sm * r * 0.06, mx - mw, my - sm * r * 0.06 + wob);
      ctx.closePath(); ctx.fill();
      if (F.open > 0.3) {                                      // язичок
        ctx.save(); ctx.clip();
        ctx.fillStyle = "#FF6F92"; ctx.beginPath(); ctx.ellipse(mx, botY + r * 0.07, mw * 0.55, r * 0.07 * F.open, 0, 0, TAU); ctx.fill();
        ctx.restore();
      }
    }
  } else {
    const sm = F.smile;
    ctx.beginPath(); ctx.moveTo(mx - mw, my - sm * r * 0.05 + wob);
    if (F.wobble > 0.05) {
      for (let i = 1; i <= 8; i++) {
        const k = i / 8, xx = mx - mw + 2 * mw * k;
        const yy = my - sm * r * 0.05 + sm * r * 0.17 * Math.sin(Math.PI * k) + Math.sin(k * 12 + t * 28) * r * 0.012 * F.wobble;
        ctx.lineTo(xx, yy);
      }
    } else ctx.quadraticCurveTo(mx, my + sm * r * 0.2, mx + mw, my - sm * r * 0.05 - wob);
    ctx.stroke();
  }
  // рум'янець
  if (F.blush > 0.02 && dim < 0.8) {
    ctx.save(); ctx.globalAlpha = 0.42 * F.blush * (1 - dim); ctx.fillStyle = "#FF6BA8";
    for (const side of [-1, 1]) { ctx.beginPath(); ctx.ellipse(side * r * 0.57 + fx * 0.9, r * 0.2 + fy, r * 0.12, r * 0.065, 0, 0, TAU); ctx.fill(); }
    ctx.restore();
  }
}

// ═════════════════════════════════════════════════════════ ЗОРІ-ПЕРСОНАЖІ ══
// st: { pal, warm (0 — крига … 1 — сяє), ice, rays, rot, pulse, face, faceA, look, blink, thorns (0..1), thornR, thornN,
//       heart (серцебиття в кризі), sing (відкритий рот), t }
const PAL_GREAT = { core: "#FFF3D6", c1: "#FFE0A0", c2: "#FFC15E", c3: "#F39436", c4: "#CF5E18", halo: "#FFA640", ray: "#FFE2A0" };
const PAL_DAWN = { core: "#FFF1E6", c1: "#FFD6C2", c2: "#FFA582", c3: "#F76E7A", c4: "#C93F86", halo: "#FF8F6B", ray: "#FFD0B0" };
const PAL_ICE = { core: "#8A98C4", c1: "#5D6A98", c2: "#3B4572", c3: "#262E54", c4: "#141A36", halo: "#5C6FB8", ray: "#9FB0E0" };
const STAR_BODY_ICE = PAL_ICE;
function bigStar(ctx, x, y, r, st) {
  const P = st.pal || PAL_GREAT, warm = clamp(st.warm === undefined ? 1 : st.warm), t = st.t || 0;
  const cc = (k) => mixh(PAL_ICE[k], P[k], warm);
  const pulse = st.pulse || 0;
  // ореол і промені
  glow(ctx, x, y, r * 5.2 * (1 + 0.08 * pulse), P.halo, 0.22 * warm * (st.haloK || 1));
  glow(ctx, x, y, r * 2.4, P.halo, 0.32 * warm * (st.haloK || 1));
  glow(ctx, x, y, r * 2.4, PAL_ICE.halo, 0.18 * (1 - warm) * (st.iceGlow === undefined ? 1 : st.iceGlow));
  const rays = (st.rays === undefined ? 1 : st.rays) * warm;
  if (rays > 0.01) {
    ctx.save(); ctx.globalCompositeOperation = "lighter";
    const N = 16;
    for (let i = 0; i < N; i++) {
      const a = (i / N) * TAU + (st.rot || 0) + 0.15 * Math.sin(t * 0.7 + i);
      const len = r * (2.1 + 1.1 * hash(i * 3.3)) * (1 + 0.12 * pulse) * (0.75 + 0.25 * Math.sin(t * 1.3 + i * 2.1));
      const w = (0.05 + 0.04 * hash(i * 7.1)) * (1 + 0.3 * pulse);
      const g = ctx.createRadialGradient(x, y, r * 0.8, x, y, len);
      g.addColorStop(0, rgba(P.ray, 0.42 * rays)); g.addColorStop(1, rgba(P.ray, 0));
      ctx.fillStyle = g; ctx.beginPath(); ctx.moveTo(x, y); ctx.arc(x, y, len, a - w, a + w); ctx.closePath(); ctx.fill();
    }
    const cross = st.cross === undefined ? 1 : st.cross;
    if (cross > 0.01) for (const [ang, len0] of [[0, 3.6], [90, 3.6], [45, 2.0], [135, 2.0]]) {   // хрест дифракції
      const len = len0 * cross;
      ctx.save(); ctx.translate(x, y); ctx.rotate(ang * D2R + (st.rot || 0) * 0.2);
      const l = r * len * (1 + 0.15 * pulse);
      const g = ctx.createLinearGradient(-l, 0, l, 0); g.addColorStop(0, rgba("#FFFFFF", 0)); g.addColorStop(0.5, rgba(P.ray, 0.55 * rays)); g.addColorStop(1, rgba("#FFFFFF", 0));
      ctx.fillStyle = g; ctx.beginPath(); ctx.ellipse(0, 0, l, r * 0.035, 0, 0, TAU); ctx.fill(); ctx.restore();
    }
    ctx.restore();
  }
  // куля
  ctx.save(); ctx.translate(x, y);
  if (st.scale) ctx.scale(st.scale[0], st.scale[1]);
  const g = ctx.createRadialGradient(-r * 0.25, -r * 0.3, r * 0.05, 0, 0, r);
  g.addColorStop(0, cc("core")); g.addColorStop(0.35, cc("c1")); g.addColorStop(0.68, cc("c2")); g.addColorStop(0.9, cc("c3")); g.addColorStop(1, cc("c4"));
  ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.fill();
  // «плазма» — повільні світлі плями на поверхні
  if (warm > 0.05) {
    ctx.save(); ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.clip(); ctx.globalCompositeOperation = "lighter";
    for (let i = 0; i < 7; i++) {
      const a = hash(i + 1) * TAU + t * (0.1 + 0.05 * i), d = r * (0.2 + 0.6 * hash(i + 9));
      glow(ctx, Math.cos(a) * d, Math.sin(a) * d, r * (0.35 + 0.3 * hash(i + 4)), "#FFFFFF", 0.1 * warm);
    }
    ctx.restore();
  }
  // крига: тріщини
  const ice = clamp(st.ice || 0);
  if (ice > 0.01) {
    ctx.save(); ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.clip();
    const fg = ctx.createRadialGradient(0, 0, r * 0.2, 0, 0, r); fg.addColorStop(0, `rgba(90,110,180,${0.05 * ice})`); fg.addColorStop(0.8, `rgba(130,155,225,${0.12 * ice})`); fg.addColorStop(1, `rgba(170,195,255,${0.26 * ice})`);
    ctx.fillStyle = fg; ctx.fillRect(-r, -r, 2 * r, 2 * r);
    ctx.strokeStyle = `rgba(200,220,255,${0.5 * ice})`; ctx.lineWidth = Math.max(1, r * 0.012);
    srand(77);
    for (let i = 0; i < 9; i++) {
      let a = rnd() * TAU, px = Math.cos(a) * r * 1.02, py = Math.sin(a) * r * 1.02;
      ctx.beginPath(); ctx.moveTo(px, py);
      const segs = 5 + Math.floor(rnd() * 4);
      for (let j = 0; j < segs * ice; j++) { a += (rnd() - 0.5) * 1.1; const step = r * (0.12 + rnd() * 0.12); px -= Math.cos(a) * step; py -= Math.sin(a) * step; ctx.lineTo(px, py); }
      ctx.stroke();
    }
    ctx.restore();
  }
  // серце в кризі
  if (st.heart > 0.01) {
    const hg = ctx.createRadialGradient(0, r * 0.05, 0, 0, r * 0.05, r * 0.7);
    hg.addColorStop(0, `rgba(255,230,160,${0.85 * st.heart})`); hg.addColorStop(0.35, `rgba(255,180,80,${0.35 * st.heart})`); hg.addColorStop(1, "rgba(255,160,60,0)");
    ctx.globalCompositeOperation = "lighter"; ctx.fillStyle = hg; ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); ctx.fill(); ctx.globalCompositeOperation = "source-over";
  }
  // обличчя
  if ((st.faceA === undefined ? 1 : st.faceA) > 0.01 && st.face) {
    ctx.globalAlpha = st.faceA === undefined ? 1 : st.faceA;
    const inkStar = mixh("#5A2A0A", "#151A33", 1 - warm);
    face(ctx, r * (st.faceScale || 0.62), st.face, { look: st.look, blink: st.blink, turn: st.turn, browLift: 0 }, g, t, 0, inkStar, true);
    ctx.globalAlpha = 1;
  }
  ctx.restore();
  // вінець шипів: виростають по одному
  const th = clamp(st.thorns || 0);
  if (th > 0.01) {
    const N = st.thornN || 12, R = r * (st.thornR || 1.35);
    for (let i = 0; i < N; i++) {
      const j = (i * 5) % N;                                   // порядок появи — урозкид
      const a = (i / N) * TAU + (st.thornRot || 0);
      const k = smooth((th - j / N * 0.75) / 0.25);
      if (k <= 0) continue;
      const s = r * (st.thornS || 0.27) * (0.85 + 0.3 * hash(i)) * easeBack(k, 2.2);
      spike(ctx, x + Math.cos(a) * R, y + Math.sin(a) * R, s, a + t * 0.25 + i, { alpha: k, glow: 0.45, color: st.thornCol || "#E8203F" });
    }
  }
}

export { blip, pose, mixPose, bigStar, glow, ring, spike, gem, crown, twinkle, teardrop, POSES, PAL_GREAT, PAL_DAWN, PAL_ICE, BLIP_BODY };
