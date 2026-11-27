// trail-abra.mjs — [ABRACADABRA, 25.11.2026] хвіст MIRAGE рівня ABRACADABRA (вибір власника 25.11 з трьох: SHAPESHIFT / MORPH / MIRAGE —
// «хвіст міраж, але треба ще покращити, щоб було видніше зміну його форм»; Orbit Dash/docs/levels/abracadabra/LEVEL.md). НЕ легендарний (HARD).
// Лише прототип: у грі — з портом рівня (у trail-vectors.mjs — PORT_PENDING).
//   Образ — світла стрічка, у якої КРАЇ — хвиля тієї ж фігури, що й орбіти (orbit-shape.mjs shapeF): зубці трикутника, сходинки квадрата,
//   промені зірки, хвилі еліпса; довжина хвилі стала (видно і на короткому хвості рівня, і в прев'ю магазину / RANKS). На ABRACADABRA — форма
//   орбіт кадру (OS), деінде — свій цикл трикутник → квадрат → зірка → еліпс по такту рівня (2 с у безкінечному). Барва підказує фігуру
//   (золото / бірюза / рожевий / бузок); на зміні фігури — сплеск ширини зубців (чиста функція форми кадру). «Менше руху» — без плину й сплеску.
//   Межі §5.5: ширина в конверті env − dev, ореол + 2 заливання (стрічка, біле ядро), без частинок.
//   Порт: стрічка квадами (як DRIFT), ширина вздовж — та сама shapeF.
import { OS, shapeF } from "./orbit-shape.mjs";

const MODE_LEVEL = "level", EPS_T = 1e-9;
const smooth01 = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));

export const MIRAGE = Object.freeze({
  N: 72, LAMBDA: 2.6, AMP: 0.62, POP: 0.45, POP_DECAY: 7, BODY_K: 0.66, BODY_A: 0.92, BODY_MIX: 0.22, CORE_K: 0.2, CORE_A: 0.95, CORE_MIX: 0.75,
  HEAD_BALL: 1, HEAD_LEN: 0.3, SHIFT: 0.75, FLOW: 0.6, GLOW_A: 0.32, GLOW_MUL: 1.7, GLOW_HEAD_K: 1.2,
  // барва підказує фігуру (власник 25.11: «щоб було видніше зміну його форм»): трикутник — золото, квадрат — бірюза, зірка — рожевий, еліпс / коло — бузок
  HUE_TRI: [255, 211, 110], HUE_SQ: [111, 242, 255], HUE_STAR: [255, 122, 224], HUE_ELL: [198, 168, 255],
});
export const MIRAGE_DEF = Object.freeze({ id: "mirage", name: "MIRAGE", level: "abracadabra", desc: "Its edges take the shape of the orbit" });

const CYCLE = [{ we: 0, asp: 0, w3: 1, w4: 0, ws: 0 }, { we: 0, asp: 0, w3: 0, w4: 1, ws: 0 }, { we: 0, asp: 0, w3: 0, w4: 0, ws: 1 },
  { we: 1, asp: 0.4, w3: 0, w4: 0, ws: 0 }];
const SH = { we: 0, asp: 0, w3: 0, w4: 0, ws: 0 };
/** Форма хвоста кадру: на рівні з формою орбіт — форма орбіт (OS), інакше — цикл фігур по такту (рівень) / по 2 с (∞), плавний перехід. */
function trailShape(F, shiftBeats) {
  if (OS.on) { for (const k of ["we", "asp", "w3", "w4", "ws"]) SH[k] = OS[k]; return SH; }
  const T = F.mode === MODE_LEVEL ? 4 * F.unit : 2, x = (F.tH - F.t0) / T + EPS_T, n = Math.floor(x), u = x - n;
  const a = CYCLE[((n % 4) + 4) % 4], b = CYCLE[(((n + 1) % 4) + 4) % 4];
  const sh = (F.mode === MODE_LEVEL ? shiftBeats * F.unit : 0.5) / T, k = F.reduced ? 0 : smooth01((u - (1 - sh)) / sh);
  for (const q of ["we", "asp", "w3", "w4", "ws"]) SH[q] = a[q] + (b[q] - a[q]) * k;
  return SH;
}
/** Барва форми: суміш барв фігур за вагами (решта — бузок еліпса / кола). */
function shapeHue(sh) {
  const C = MIRAGE, w3 = sh.w3, w4 = sh.w4, ws = sh.ws, we = Math.max(0, 1 - w3 - w4 - ws), out = [0, 0, 0];
  for (let i = 0; i < 3; i++) out[i] = Math.round(C.HUE_TRI[i] * w3 + C.HUE_SQ[i] * w4 + C.HUE_STAR[i] * ws + C.HUE_ELL[i] * we);
  return "#" + out.map((v) => Math.max(0, Math.min(255, v)).toString(16).padStart(2, "0")).join("");
}
/** Сплеск на зміні фігури 0..1 — чиста функція форми кадру: під час перетікання ваги «змішані» (жодна не домінує) → зубці ширшають саме
 *  в мить зміни (на рівні з формою — 0.12–0.24 с «клацу», деінде — перехід циклу). Без стану між кадрами (вектори й прев'ю детерміновані). */
function shapePop(sh) {
  const w = [sh.w3, sh.w4, sh.ws, Math.max(0, sh.we)], top = Math.max(...w), sum = w.reduce((x, y) => x + y, 0);
  return sum > 0.2 ? Math.min(1, 2.5 * (1 - top / sum)) : 0;
}

/** Маляр MIRAGE. */
export const mirageMaker = ({ ribbon, chordGrad, chordU, glowOf, empty, LOOK_E, headBallOf, TRAIL_RULES }) => (F, S) => {
  const C = MIRAGE, X = LOOK_E, out = empty(), env = F.env, still = F.reduced;
  const smp = [];
  for (let i = 0; i < C.N; i++) smp.push(F.atS((F.len * i) / (C.N - 1)));
  if (smp.some((p) => !p)) return out;
  const ball = (f) => headBallOf(C, f);
  const stopsOf = (fn) => { const st = []; let last = 0; for (const p of smp) { last = Math.max(last, chordU(F, p.x, p.y)); st.push([last, ...fn(p)]); } return st; };
  const lim = (p) => Math.max(0, env - p.dev - 1e-6);
  const shp = trailShape(F, C.SHIFT), hue = shapeHue(shp), pop = still ? 0 : shapePop(shp);
  // хвиля фігури вздовж шляху зі сталою довжиною хвилі (λ = LAMBDA × півширина голови): зубці видно і на короткому хвості рівня, і в прев'ю;
  // плин від м'яча назад; біля голови гасне (м'яч круглий, голова плавна); на зміні фігури — сплеск ширини
  const lam = Math.max(1e-3, C.LAMBDA * X.hw(1)), flow = still ? 0 : (F.tH - F.t0) * C.FLOW * Math.PI * 2;
  const wave = (p) => shapeF(shp, ((F.len - p.s) / lam) * Math.PI * 2 + flow) - 1;
  const calm = (p) => smooth01((F.len - p.s) / (0.16 * F.len + 1e-6));
  const bodyW = (p) => Math.min(lim(p), Math.max(0.05, C.BODY_K * (1 + (C.AMP + C.POP * pop) * wave(p) * calm(p) * 2.2)) * X.hw(p.f));
  const coreW = (p) => Math.min(lim(p), C.CORE_K * X.hw(p.f));
  const halo = [ribbon(S, smp, (p) => Math.min(lim(p), bodyW(p) * X.pk(C.GLOW_MUL, C.GLOW_HEAD_K, F, p)))];
  out.glow = { ...glowOf(halo), grad: chordGrad(F, S, stopsOf((p) => [0, Math.min(TRAIL_RULES.HEAD_A_MAX, C.GLOW_A * (1 + 0.6 * pop)) * X.a(p.f), hue, ball(p.f)])) };
  delete out.glow.solid;
  out.fills.push({ paths: [ribbon(S, smp, bodyW)], grad: chordGrad(F, S, stopsOf((p) => [C.BODY_MIX, C.BODY_A * X.a(p.f), hue, ball(p.f)])) });
  out.fills.push({ paths: [ribbon(S, smp, coreW)], grad: chordGrad(F, S, stopsOf((p) => [C.CORE_MIX, C.CORE_A * X.a(p.f), "#ffffff", ball(p.f)])) });
  return out;
};
