// ─────────────────────────────────────────────────────────────────────────────
// Магазин на сайті: сцена хвоста й картки хвостів — СПРАВЖНІЙ рушій хвостів гри (prototype/trails.mjs, той самий, що
// в грі: Kotlin TrailHarness / TrailStyle), не малюнок. Числа сцени й карток — з прототипу (SHOP_STAGE, TRAIL_PREVIEW,
// SHOP_THUMB.TRAIL у orbit-dash-tutorial.jsx), «тонкий маляр» — paintTrail рушія.
//   ∞ — хвіст у безкінечному рані (форма ENDLESS, барва скіна); ♪ — у музичному рівні (форма LEVEL, барва рівня).
//   Зароблений рівнем хвіст відкривається у ♪ свого рівня (як у грі); купований — у ∞; ♪ ще раз — наступний рівень.
//   Скін (8 палітр гри) перефарбовує сцену й картки — як надягнутий скін у грі.
// page.js вантажить цей модуль, коли до розділу догорнули; рух — лише поки сцену видно, «менше руху» — кадр стоїть.
// ─────────────────────────────────────────────────────────────────────────────
import { trailFrame, trailStyle, trailClock, paintTrail, TRAIL_MODE, TRAIL_RULES, LEVEL_TRAIL, h01 } from "./trails.mjs";

// = TRAIL_PREVIEW прототипу (RING_FU — кільця поля LAYOUT2 [320, 190] / FX_K 2)
const P = { PREVIEW_BPM: 120, HOP_K: 14, RING_FU: [160, 95], BALL_FU: 10, NODE_DT: 1 / 60, EPS_T: 1e-9, BALL_GLOW_K: 3.2, BALL_GLOW_A: 0.45, START_DEG: -90, SNAP_BEATS: 1.15 };
// = SHOP_STAGE прототипу (сцена 320×184 → тут у пропорції полотна)
const S = { DEG_BEAT: 140, HOP_BEATS: 2, RING_W: 2, LEVEL_Z: 0.5, LEVEL_GAIN: 0.5, BG_LIFT: 0.16, BG_MID: 0.45, BG_MID_LIFT: 0.03, BG_EDGE: 0.25, BG_R: 0.75,
  STARS: 65, STAR_A: 0.45, TICKS: 24, TICK_IN: 6, TICK_OUT: 8, TICK_MAJOR_OUT: 11, TICK_MAJOR: 6, TICK_A: 0.5, TICK_MAJOR_A: 0.9, R0: 62, BALL_R: 8, H: 184 };
// = SHOP_THUMB.TRAIL прототипу (картка хвоста)
const C = { R_K: 0.95, CX_K: 0.42, CY_K: 0.93, AT: -62, ARC: 130, ARC_AT: -85, RING_A: 0.9, RING_W: 1.2, BALL_K: 0.095, GLOW_K: 3.2, W_K: 1.7, DEG_BEAT: 90, GAIN: 0.45 };
const D = Math.PI / 180;

const hx = (h) => { h = String(h).replace("#", ""); return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]; };
const rgba = (h, a) => { const c = hx(h); return `rgba(${c[0]},${c[1]},${c[2]},${a})`; };
const mix = (a, b, t) => { const x = hx(a), y = hx(b); return "#" + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, "0")).join(""); };
const pal6 = (p) => Object.fromEntries(Object.entries(p).map(([k, v]) => [k, typeof v === "string" && !v.startsWith("#") ? "#" + v : v]));

// м'яке світло (softGlow прототипу: профіль erfc, як ARoundRectGlow гри)
const erfc = (x) => { const t = 1 / (1 + 0.3275911 * Math.abs(x)); const y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x); return x >= 0 ? 1 - y : 1 + y; };
const glowCache = {};
function softGlow(color) {
  if (glowCache[color]) return glowCache[color];
  const N = 128, c = document.createElement("canvas"); c.width = c.height = N;
  const g = c.getContext("2d"), img = g.createImageData(N, N), d = img.data, r0 = 0.238 * N, sg = 0.112 * N, [cr, cg, cb] = hx(color);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const r = Math.hypot(x + 0.5 - N / 2, y + 0.5 - N / 2), o = (y * N + x) * 4;
    d[o] = cr; d[o + 1] = cg; d[o + 2] = cb; d[o + 3] = Math.round(0.5 * erfc((r - r0) / (sg * Math.SQRT2)) * 255);
  }
  g.putImageData(img, 0, 0);
  return (glowCache[color] = c);
}
const fxGlow = (ctx, x, y, d, color, a) => { if (a <= 0.004 || d <= 0.2) return; ctx.globalAlpha = Math.min(1, a); ctx.drawImage(softGlow(color), x - d / 2, y - d / 2, d, d); ctx.globalAlpha = 1; };
const fxDot = (ctx, x, y, d, color, a) => { ctx.fillStyle = rgba(color, Math.min(1, a)); ctx.beginPath(); ctx.arc(x, y, d / 2, 0, Math.PI * 2); ctx.fill(); };
const fxRing = (ctx, x, y, r, w, color, a) => { ctx.strokeStyle = rgba(color, Math.min(1, a)); ctx.lineWidth = w; ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2); ctx.stroke(); };

/** = previewNodes прототипу: вузли симульованого м'яча на мить t (кут a0 + degBeat °/долю, стрибок кожні hopBeats долі). */
function previewNodes(t, o) {
  const beat = 60 / o.bpm, degS = o.degBeat / beat;
  const span = o.mode === TRAIL_MODE.LEVEL ? LEVEL_TRAIL.SPAN_BEATS * beat : TRAIL_RULES.ENDLESS_SPAN;
  const hopP = o.hopBeats > 0 ? o.hopBeats * beat : 0;
  const ringAt = (x) => {
    if (!hopP) return P.RING_FU[0];
    const n = Math.floor(x / hopP), last = n * hopP, to = P.RING_FU[n & 1], from = P.RING_FU[(n + 1) & 1];
    return to + (from - to) * Math.exp(-P.HOP_K * (x - last));
  };
  const at = (x) => ({ a: o.a0 + degS * x, r: ringAt(x), t: x });
  const g = Math.floor(t / P.NODE_DT + P.EPS_T), nodes = [];
  for (let k = Math.ceil(span / P.NODE_DT) + 1; k >= 0; k--) nodes.push(at((g - k) * P.NODE_DT));
  if (t - g * P.NODE_DT > P.EPS_T) nodes.push(at(t));
  return nodes;
}

/** = paintTrailPreview прототипу (без DEV-метрик): кільця-дуга, хвіст рушієм, другий прохід gain, м'яч з ореолом. */
function paintPreview(ctx, id, colour, nodes, o, reduced) {
  const F = trailFrame(nodes, o.mode, o.mode === TRAIL_MODE.LEVEL ? { L: trailClock(o.bpm), z: o.z || 1, beat: 0, reduced } : { reduced });
  const head = nodes[nodes.length - 1], rot = o.follow == null ? 0 : o.follow - head.a;
  if (o.rings > 0 && o.ringColour) {
    ctx.save(); ctx.strokeStyle = rgba(o.ringColour, o.ringA == null ? 1 : o.ringA); ctx.lineWidth = o.ringW || 1;
    const a0 = (o.arcAt - o.arc / 2) * D, a1 = (o.arcAt + o.arc / 2) * D;
    ctx.beginPath(); ctx.arc(o.cx, o.cy, P.RING_FU[0] * o.K, a0, a1); ctx.stroke(); ctx.restore();
  }
  if (F.ok) {
    const prims = trailStyle(id).build(F, { posMul: o.K, wMul: o.wK });
    ctx.save(); ctx.translate(o.cx, o.cy); ctx.rotate(rot * D);
    paintTrail(ctx, prims, colour, { k: 1 });
    if (o.gain > 0) { ctx.globalAlpha = o.gain; paintTrail(ctx, prims, colour, { k: 1 }); }
    ctx.restore();
  }
  if (o.ball) {
    const a = (head.a + rot) * D, x = o.cx + Math.cos(a) * head.r * o.K, y = o.cy + Math.sin(a) * head.r * o.K;
    const d = 2 * P.BALL_FU * o.K * (o.ballK == null ? o.wK : o.ballK);
    fxGlow(ctx, x, y, d * (o.glowK == null ? P.BALL_GLOW_K : o.glowK), colour, P.BALL_GLOW_A);
    fxDot(ctx, x, y, d, colour, 1);
  }
}

const STARS = Array.from({ length: S.STARS }, (_, i) => ({ x: h01(i * 1.3 + 3.1), y: h01(i * 2.7 + 12.4), r: 0.35 + h01(i * 5.1 + 3.7) * 0.75, a: 0.12 + h01(i * 7.3 + 3.3) * S.STAR_A }));

export function mount({ root, T, reduced, OD }) {
  const cv = root.querySelector("#stageCv"), ctx = cv.getContext("2d");
  const cards = [...root.querySelectorAll(".tc")], skins = [...root.querySelectorAll(".sk")], worlds = [...root.querySelectorAll(".wsw")];
  const nameEl = root.querySelector("#stName"), descEl = root.querySelector("#stDesc"), stateEl = root.querySelector("#stState");
  const TRAILS = OD.trails, PALS = OD.palettes.map(pal6), LEVELS = (window.OD_LEVELS || []).map((l) => ({ id: l.id, name: l.name, bpm: l.bpm, pal: pal6(l.palette || {}) }));
  const st = { trail: "comet", world: "endless", lvl: 0, skin: 0, t0: performance.now(), vis: true };
  const trail = () => TRAILS.find((t) => t.id === st.trail) || TRAILS[0];
  const scenePal = () => (st.world === "level" ? (LEVELS[st.lvl] || {}).pal || PALS[st.skin] : PALS[st.skin]);
  const GEM = '<svg class="gem" viewBox="0 0 64 64" aria-hidden="true"><path fill="currentColor" d="M61.55 26.09 37.91 2.45a8.37 8.37 0 0 0-11.82 0L2.45 26.09a8.37 8.37 0 0 0 0 11.82l23.64 23.64a8.37 8.37 0 0 0 11.82 0l23.64-23.64a8.37 8.37 0 0 0 0-11.82z"/></svg>';
  const NOTE = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M9 17.5V5.8c0-.5.3-.9.8-1l8.5-1.9c.6-.1 1.2.3 1.2 1v11.6a3 3 0 1 1-2-2.8V8l-6.5 1.5v8a3 3 0 1 1-2-2.8z"/></svg>';
  const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;");

  function info() {
    const t = trail();
    nameEl.textContent = t.name;
    descEl.textContent = T("g:" + t.desc);
    stateEl.className = "stage-state";
    if (t.level) { const L = LEVELS.find((l) => l.id === t.level); stateEl.classList.add("lv"); stateEl.innerHTML = NOTE + esc(L ? L.name : t.level); }
    else if (t.price) { stateEl.classList.add("pr"); stateEl.innerHTML = t.price.toLocaleString("en-US").replace(/,/g, " ") + GEM; }
    else { stateEl.classList.add("fr"); stateEl.textContent = T("chip.free"); }
    worlds.forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.mode === st.world)));
    cards.forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.trail === st.trail)));
  }

  // сцена: тло-світло гравця, зорі, два кільця з рисками, хвіст рушієм, м'яч
  function frame(now) {
    const dpr = Math.min(2, window.devicePixelRatio || 1), pw = Math.max(1, Math.round(cv.offsetWidth * dpr)), ph = Math.max(1, Math.round(cv.offsetHeight * dpr));
    if (cv.width !== pw || cv.height !== ph) { cv.width = pw; cv.height = ph; }
    const k = ph / S.H, W0 = pw / k, p = scenePal(), wide = W0 > 260;
    ctx.setTransform(k, 0, 0, k, 0, 0);
    const v = { CX: wide ? W0 * 0.66 : W0 * 0.5, CY: S.H * (wide ? 0.54 : 0.6), R0: S.R0 };
    const g = ctx.createRadialGradient(v.CX, v.CY, 0, v.CX, v.CY, Math.max(W0, S.H) * S.BG_R);
    g.addColorStop(0, mix(p.bg, p.player, S.BG_LIFT)); g.addColorStop(S.BG_MID, mix(p.bg, "#ffffff", S.BG_MID_LIFT)); g.addColorStop(1, mix(p.bg, "#000000", S.BG_EDGE));
    ctx.fillStyle = g; ctx.fillRect(0, 0, W0, S.H);
    ctx.fillStyle = "#ffffff";
    for (const s of STARS) { ctx.globalAlpha = s.a; ctx.beginPath(); ctx.arc(s.x * W0, s.y * S.H, s.r, 0, Math.PI * 2); ctx.fill(); }
    ctx.globalAlpha = 1;
    const K = v.R0 / P.RING_FU[0], wK = S.BALL_R / (P.BALL_FU * K), R = P.RING_FU.map((r) => r * K);
    for (const r of R) fxRing(ctx, v.CX, v.CY, r, S.RING_W, p.ring, 1);
    ctx.lineWidth = 1;
    for (let i = 0; i < S.TICKS; i++) {
      const a = (i * 360 / S.TICKS) * D, major = i % S.TICK_MAJOR === 0, r0 = R[0] + S.TICK_IN, r1 = R[0] + (major ? S.TICK_MAJOR_OUT : S.TICK_OUT);
      ctx.strokeStyle = rgba(p.ring, major ? S.TICK_MAJOR_A : S.TICK_A);
      ctx.beginPath(); ctx.moveTo(v.CX + Math.cos(a) * r0, v.CY + Math.sin(a) * r0); ctx.lineTo(v.CX + Math.cos(a) * r1, v.CY + Math.sin(a) * r1); ctx.stroke();
    }
    const L = st.world === "level" ? LEVELS[st.lvl] : null, bpm = (L && L.bpm) || P.PREVIEW_BPM;
    const t = reduced ? 1.15 * 60 / bpm : (now - st.t0) / 1000;
    const o = { mode: st.world === "level" ? TRAIL_MODE.LEVEL : TRAIL_MODE.ENDLESS, bpm, degBeat: S.DEG_BEAT, hopBeats: S.HOP_BEATS, a0: P.START_DEG,
      cx: v.CX, cy: v.CY, K, wK, follow: null, rings: 0, ball: false, z: S.LEVEL_Z, gain: st.world === "level" ? S.LEVEL_GAIN : 0 };
    const nodes = previewNodes(t, o);
    paintPreview(ctx, st.trail, p.player, nodes, o, reduced);
    const h = nodes[nodes.length - 1], bx = v.CX + Math.cos(h.a * D) * h.r * K, by = v.CY + Math.sin(h.a * D) * h.r * K;
    fxGlow(ctx, bx, by, 2 * S.BALL_R * P.BALL_GLOW_K, p.player, P.BALL_GLOW_A);
    fxDot(ctx, bx, by, 2 * S.BALL_R, p.player, 1);
  }

  // картки: знімок хвоста (форма ENDLESS, барва скіна) — як картки SHOP гри; перемальовуються, коли міняється скін
  function thumbs() {
    const p = PALS[st.skin];
    cards.forEach((b) => {
      const c = b.querySelector("canvas"), dpr = Math.min(2, window.devicePixelRatio || 1);
      const w = c.offsetWidth || 60, hh = c.offsetHeight || 60;
      c.width = Math.round(w * dpr); c.height = Math.round(hh * dpr);
      const x = c.getContext("2d"); x.setTransform(dpr, 0, 0, dpr, 0, 0);
      const bg = x.createRadialGradient(w / 2, hh * 0.3, 0, w / 2, hh * 0.3, w * 0.9);
      bg.addColorStop(0, mix(p.bg, p.player, 0.14)); bg.addColorStop(1, mix(p.bg, "#000000", 0.2));
      x.fillStyle = bg; x.fillRect(0, 0, w, hh);
      const Rr = w * C.R_K, K = Rr / P.RING_FU[0];
      const o = { mode: TRAIL_MODE.ENDLESS, bpm: P.PREVIEW_BPM, degBeat: C.DEG_BEAT, hopBeats: 0, a0: 0, cx: w * C.CX_K, cy: hh / 2 + Rr * C.CY_K, K, wK: C.W_K,
        follow: C.AT, arcAt: C.ARC_AT, arc: C.ARC, rings: 1, ringColour: p.ring, ringA: C.RING_A, ringW: C.RING_W, ball: true,
        ballK: (C.BALL_K * w) / (P.BALL_FU * K), glowK: C.GLOW_K, gain: C.GAIN };
      paintPreview(x, b.dataset.trail, p.player, previewNodes(P.SNAP_BEATS * 60 / P.PREVIEW_BPM, o), o, reduced);
    });
  }

  cards.forEach((b) => b.addEventListener("click", () => {
    const t = TRAILS.find((x) => x.id === b.dataset.trail);
    st.trail = t.id;
    if (t.level) { st.world = "level"; st.lvl = Math.max(0, LEVELS.findIndex((l) => l.id === t.level)); } else st.world = "endless";
    st.t0 = performance.now(); info(); if (reduced) frame(performance.now());
  }));
  worlds.forEach((b) => b.addEventListener("click", () => {
    const m = b.dataset.mode;
    if (m === "level" && st.world === "level" && !trail().level) st.lvl = (st.lvl + 1) % LEVELS.length;   // ♪ ще раз — наступний рівень
    else if (m === "level" && st.world !== "level") { const t = trail(); st.lvl = t.level ? Math.max(0, LEVELS.findIndex((l) => l.id === t.level)) : st.lvl; }
    st.world = m; info(); if (reduced) frame(performance.now());
  }));
  skins.forEach((b) => b.addEventListener("click", () => {
    st.skin = +b.dataset.skin;
    skins.forEach((x) => x.setAttribute("aria-pressed", String(x === b)));
    if (st.world === "level" && !trail().level) st.world = "endless";
    info(); thumbs(); if (reduced) frame(performance.now());
  }));

  if ("IntersectionObserver" in window) new IntersectionObserver((es) => { st.vis = es[0].isIntersecting; }, { threshold: 0 }).observe(cv);
  const loop = (now) => { if (st.vis && !document.hidden) frame(now); requestAnimationFrame(loop); };
  info(); thumbs(); frame(performance.now());
  if (!reduced) requestAnimationFrame(loop);
  let rt; addEventListener("resize", () => { clearTimeout(rt); rt = setTimeout(() => { thumbs(); frame(performance.now()); }, 150); });
  return { lang: info };
}
