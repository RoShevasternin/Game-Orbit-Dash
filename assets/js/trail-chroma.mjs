// trail-chroma.mjs — [CHROMA, 21.11.2026] хвіст PRISM рівня CHROMA (робочий id nu-drop; Orbit Dash/docs/levels/nu-drop/LEVEL.md «Музика рівня,
// коло 4», розділ «Хвіст PRISM»). НЕ легендарний (легендарні — лише DEMON). Лише прототип: у грі — порт пізніше (TrailStyles PRISM разом
// із портом рівня; у trail-vectors.mjs — PORT_PENDING).
//   Образ — червоний неон з RGB-розщепом рівня: гаряча червона стрічка з білою жилою; дві примари — БІРЮЗОВА й ЧЕРВОНА — відходять від неї
//   в боки на кожну долю (у грі долі = тапи) і сходяться назад (expo-спад), біля голови сходяться в м'яч; примари мерехтять на пів долі;
//   у дропі (рівень такту ≥ DROP_LEVEL) — ширше розщеплення і ГЛІТЧ: на сітці GLITCH_BEATS (≤ 6 Гц) GLITCH_N відрізків хвоста зсуваються
//   вбік (рвані смуги, як розщеп кадру). «Менше руху» — примари стоять на OFF0, без мерехтіння й глітчу.
//   Межі §5.5: усе в конверті env − dev (зсув + півширина примари обрізані), заливань 4 з ореолом (ореол, примара бірюзова, примара червона,
//   стрічка — до голови біліша, «гаряча жила»), без частинок; зміна форми (глітч, мерехтіння) — не частіше FLICKER_HZ.
//   Порт: смуги квадами (як DRIFT / ECHO), барви — hue стопів, голова — барва м'яча (ball).
export const PRISM = Object.freeze({
  RED: "#ff1f4b", CYAN: "#19e6ff", HOT: "#ff3a5c",
  CORE_K: 0.34, CORE_A: 0.95, CORE_MIX: 0.18, HEAD_BALL: 1, HEAD_LEN: 0.3,
  VEIN_MIX: 0.7,
  GHOST_K: 0.2, GHOST_A: 0.85, GHOST_MIX: 0.1, GHOST_TAIL: 0.6, ENDLESS_K: 0.6,
  OFF0: 0.3, OFF_BEAT: 0.75, OFF_DROP: 0.3, BEAT_DECAY: 9, PINCH: 14, BEAT_T: 0.5,
  FLICK_BEATS: 1 / 2, FLICK_LO: 0.55, FLICK_T: 0.25,
  DROP_LEVEL: 2, GLITCH_BEATS: 1 / 2, GLITCH_N: 3, GLITCH_LEN: 0.11, GLITCH_SHIFT: 0.55,
  GLOW_A: 0.3, GLOW_MUL: 2.1, GLOW_HEAD_K: 1.3, N: 34,
});
export const PRISM_DEF = Object.freeze({ id: "prism", name: "PRISM", level: "nu-drop", desc: "Red and cyan ghosts split off on every beat" });

const MODE_LEVEL = "level", FLICKER_HZ = 6, EPS_T = 1e-9;
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const smooth01 = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));
const hash01 = (x) => { const v = Math.sin(x) * 43758.5453; return v - Math.floor(v); };

/** [PRISM] Частка долі 0…1 від останньої долі (рівень — долі треку, ∞ — BEAT_T с). */
export const prismBeatU = (F) => {
  const T = F.mode === MODE_LEVEL ? F.unit : PRISM.BEAT_T, x = (F.tH - F.t0) / T;
  return clamp01(x - Math.floor(x + EPS_T));
};
/** [PRISM] Розщеп на долю 0…1: 1 щойно після долі → 0 (expo); «менше руху» — 0. */
export const prismSplit = (F) => (F.reduced ? 0 : Math.exp(-PRISM.BEAT_DECAY * prismBeatU(F)));
/** [PRISM] Дроп зараз (рівень, такт ≥ DROP_LEVEL; ∞ — ніколи). */
export const prismDrop = (F) => F.mode === MODE_LEVEL && F.level(F.tH) >= PRISM.DROP_LEVEL;
/** [PRISM] Період глітчу й мерехтіння, с (рівень — частка долі, але не частіше FLICKER_HZ; ∞ — FLICK_T). */
export const prismFlickT = (F) => (F.mode === MODE_LEVEL ? Math.max(PRISM.GLITCH_BEATS * F.unit, 1 / FLICKER_HZ) : Math.max(PRISM.FLICK_T, 1 / FLICKER_HZ));

/** Маляр PRISM (хелпери trails.mjs — ті самі, що в BARB). */
export const prismMaker = ({ ribbon, chordGrad, chordU, glowOf, empty, LOOK_E, headBallOf, TRAIL_RULES }) => (F, S) => {
  const C = PRISM, X = LOOK_E, out = empty(), env = F.env, still = F.reduced;
  const smp = [];
  for (let i = 0; i < C.N; i++) smp.push(F.atS((F.len * i) / (C.N - 1)));
  if (smp.some((p) => !p)) return out;
  const ball = (f) => headBallOf(C, f);
  const stopsOf = (fn) => { const st = []; let last = 0; for (const p of smp) { last = Math.max(last, chordU(F, p.x, p.y)); st.push([last, ...fn(p)]); } return st; };
  const drop = prismDrop(F), split = prismSplit(F), per = prismFlickT(F), n = Math.floor((F.tH - F.t0) / per + EPS_T);
  // глітч (лише дроп, не «менше руху»): GLITCH_N відрізків по GLITCH_LEN дуги зсуваються вбік ± GLITCH_SHIFT·hw — новий набір раз на per
  const cuts = [];
  if (drop && !still) for (let k = 0; k < C.GLITCH_N; k++) {
    const c = 0.12 + 0.72 * hash01(n * 12.9898 + k * 78.233), side = hash01(n * 4.1 + k * 9.7) < 0.5 ? -1 : 1;
    cuts.push([c, c + C.GLITCH_LEN, side * (0.4 + 0.6 * hash01(n * 2.3 + k * 5.1))]);
  }
  const pinch = (p) => smooth01((F.len - p.s) / C.PINCH);                       // біля голови — усе сходиться в м'яч
  const lim = (p) => Math.max(0, env - p.dev - 1e-6);
  const shift = (p) => { let g = 0; for (const [a, b, s] of cuts) if (p.f >= a && p.f <= b) g = s; return g * C.GLITCH_SHIFT * X.hw(p.f) * pinch(p); };
  const coreW = (p) => Math.min(lim(p), C.CORE_K * X.hw(p.f));
  const ghostW = (p) => Math.min(lim(p), C.GHOST_K * X.hw(p.f) * F.pick(1, 0.8));
  const offOf = (p) => (C.OFF0 + C.OFF_BEAT * split + (drop && !still ? C.OFF_DROP : 0)) * X.hw(p.f) * pinch(p) * F.pick(1, C.ENDLESS_K);   // ∞ — вужче (пелена ≤ 800)
  // зсув стрічки (глітч) і примари — у межах конверту: |shift| + w ≤ lim, |shift ± off| + gw ≤ lim
  const sh = (p) => { const m = Math.max(0, lim(p) - coreW(p)); return Math.max(-m, Math.min(m, shift(p))); };
  const gOff = (p, side) => { const m = Math.max(0, lim(p) - ghostW(p)); return Math.max(-m, Math.min(m, sh(p) + side * offOf(p))); };
  const flick = still ? 1 : (n % 2 ? C.FLICK_LO + (1 - C.FLICK_LO) * split : 1);
  const ghostA = (p) => C.GHOST_A * flick * (C.GHOST_TAIL + (1 - C.GHOST_TAIL) * X.a(p.f)) * (0.6 + 0.4 * smooth01(offOf(p) / (C.OFF0 * X.hw(p.f) * F.pick(1, C.ENDLESS_K) + 1e-9)));   // ≥ 0.6: примари — не «пелена»
  const halo = [ribbon(S, smp, (p) => Math.min(lim(p), (offOf(p) + ghostW(p)) * X.pk(C.GLOW_MUL / 2, C.GLOW_HEAD_K, F, p)), sh)];
  out.glow = { ...glowOf(halo), grad: chordGrad(F, S, stopsOf((p) => [0, Math.min(TRAIL_RULES.HEAD_A_MAX, C.GLOW_A * (1 + 0.5 * split)) * X.a(p.f), C.RED, ball(p.f)])) };
  delete out.glow.solid;
  out.fills.push({ paths: [ribbon(S, smp, ghostW, (p) => gOff(p, 1))], grad: chordGrad(F, S, stopsOf((p) => [C.GHOST_MIX, ghostA(p), C.CYAN, 0])) });
  out.fills.push({ paths: [ribbon(S, smp, ghostW, (p) => gOff(p, -1))], grad: chordGrad(F, S, stopsOf((p) => [C.GHOST_MIX, ghostA(p), C.RED, 0])) });
  out.fills.push({ paths: [ribbon(S, smp, coreW, sh)], grad: chordGrad(F, S, stopsOf((p) => [C.CORE_MIX + (C.VEIN_MIX - C.CORE_MIX) * p.f * p.f, C.CORE_A * X.a(p.f), C.HOT, ball(p.f)])) });   // до голови — біліша (гаряча жила)
  return out;
};
