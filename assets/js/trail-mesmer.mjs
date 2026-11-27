// ─────────────────────────────────────────────────────────────────────────────
// trail-mesmer.mjs — [HYPNOTIC, 24.11.2026] MESMER — легендарний хвіст рівня HYPNOTIC (id hypnotic, DEMON; легендарний — сам, бо рівень DEMON:
// legendTrail JSX рахує його з полиці, не полем каталогу). Специфікація — Orbit Dash/docs/levels/hypnotic/SPEC-PROTO.md («Рівень», хвіст-нагорода);
// еталон вигляду й масштабу — KIT Orbit Dash/docs/levels/illusion/listen6/index.html, масив MIX, варіант F3 «СПІРАЛЬ-ХВИЛЯ» (блок «MESMER × SPIRAL»).
// Назва — від Месмера (mesmerize — заворожити); платівка рівня вже зветься SPIRAL.
//
// [v2, власник 24.11: «хвіст якось на прев'ю рівня негарний, не відчуваю ефекту»] v1 (дві тонкі нитки в конверті 9 і кільце ≤ 9 од., що жило ½ долі)
//   на картці рівня читався блідою ниткою — гіпнозу не видно. Тепер — масштаб KIT і виняток із конверта, як у CAPE (рішення власника 24.11 — дозвіл).
//
// ОБРАЗ — стрічка гіпнотизера, що закручується, і гіпно-диск на кожну долю:
//   [v3, власник 24.11 (v241): «те, що навкруги м'яча — крутий ефект (ЛИШИТИ), але сам хвіст негарний і НЕАНІМОВАНИЙ … хочу, щоб він обвивався
//     анімовано, дійсно як 3D-спіраль, біло-червоним»] тіло — ПОДВІЙНА 3D-СПІРАЛЬ («ДНК-цукерка»), що ПОСТІЙНО обертається:
//   НИТКИ — біла WHITE і червона RED навколо осі шляху в протифазі: φ(d, t) = 2π·d / WAVE_L − mesmerTwist(t), d — дуга від голови; біла — зсув
//     +amp·cos φ уздовж нормалі й глибина z = +sin φ, червона — −amp·cos φ і z = −sin φ. На екрані вони перехрещуються (cos φ = 0) і на кожному
//     перехресті одна — спереду, друга — позаду (|z| = 1); «спереду / позаду» міняється там, де вони найдалі одна від одної (sin φ = 0) — тож
//     перекриття на перехрестях чергується, як у справжньої спіралі. Радіус спіралі amp = AMP_K·f^AMP_EXP спадає до хвоста, у голові за PINCH од.
//     дуги — до нуля (нитки сходяться під м'яч); товщина нитки w = min(TH_MAX, TH_K·оболонка LOOK) × (W_BACK … 1 за глибиною).
//   ГЛИБИНА — ДВА ШАРИ: спершу ЗАДНІ відрізки обох ниток (одне заливання: червона позаду — BACK_RED, біла позаду — BACK_WHITE, тонші й темніші),
//     потім ПЕРЕДНІ (друге: RED з бліком HL·z² до білого на гребені, WHITE). Барва шару міняється вздовж хорди рівно в точках sin φ = 0 (стопи
//     градієнта парами на тому самому місці): у кожному перерізі спереду рівно одна нитка, тож одне заливання на шар вистачає.
//   ОБЕРТАННЯ (mesmerTwist) — ПОСТІЙНЕ: TURN оберту на долю рівно (1 оберт — спіраль видимо крутиться й «пливе» до хвоста, бо точка сталої фази
//     d = WAVE_L·twist / 2π росте з часом), плюс легкий «клац» SNAP на долю (expo-спад, як розквіт PETAL); годинник — доля треку в рівні (сітка
//     частини концерту — як решта), у безкінечному — своя доля BEAT_T (як PRISM). Рух неперервний (не мерехтіння).
//   КАНТ — темний DEEP під обома нитками (ширший на OUT_W): спіраль не губиться на білих кільцях HYPNOTIC; ОРЕОЛ — червоний, м'яке сяйво.
//   ГІПНО-ДИСК (mesmerWaves) — на кожну долю (WAVE_BEATS; ∞ — WAVE_T) від м'яча розходиться червоно-біла спіраль: ARMS рукавів по черзі білий /
//     червоний (спіраль Архімеда від ARM_IN·r до r, ARM_SWEEP оберту), обідок і внутрішнє кільце різних барв; крутиться (SPIN обертів / с), росте
//     R0 → R1 (KIT: 8 → 42 px при м'ячі 10; тут до ×3.4 радіуса м'яча), живе LIFE_BEATS долі в межах [LIFE_MIN, LIFE_MAX] с (≈ 0,8–1 с, KIT 0,9 с),
//     одночасно ≤ MAX (брижі). Центр — м'яч (у KIT мітка лишалась на місці долі, але кадр хвоста знає лише ½ долі шляху — тут диск «дихає» від м'яча).
//     ГАСНЕ ТОНШАННЯМ: α ≥ A_MIN до кінця (вище смуги «пелени» 0.05…0.35), товщина рукавів і кілець ×(1 − q)^W_FADE → 0 — диск розчиняється, не
//     стає сірою плівкою (VEIL_MAX).
//   ОРЕОЛ — спіраль ×GLOW_MUL і обідки дисків, адитивно, червоний (біля голови — барва м'яча), α GLOW_A — легке сяйво.
//   [v2, власник 24.11, знімок у рівні: «ледь видна тонка лінія»] на білому кільці HYPNOTIC червоне домінує: темний кант DEEP, білі рукави диска
//     тонші (ARM_W_WHITE), голова лише частково барви м'яча (HEAD_BALL 0.4 — м'яч-гарбуз / білий не «вибілює» голову). Знімок still (SHOP, RANKS) —
//     один диск у фазі STILL_Q. [v3] Поперечні «сходинки» ДНК — ні: малювань і так 27 з 30 (диски), а п'яте заливання — поза межею §5.5.
//   «Менше руху»: спіраль не крутиться, дисків немає (і в знімку still).
// ВИНЯТОК З КОНВЕРТА (власник 24.11, як CAPE — trailEnvOf): півширина намальованого ≤ ENV·min(1, z) (решта стилів — 9), усе — у смузі ±ENV довкола
//   шляху (LEN_MAX 400 — увесь шлях: стрічка завдовжки як у всіх, ∞ на 302 °/с ≈ 320 од.; виняток лише вшир), попереду центру м'яча — не далі AHEAD_MAX·BALL_R (диск і його сяйво). Хвіст малюється ДО сутностей (шипи, геми й
//   іскри — над диском, trails.test «CAPE під шипами»), диск — лінії, не заливка: перешкоди лишаються видно. Спіраль — ≤ AMP_K + TH_MAX + OUT_W.
// МЕЖІ §5.5 (решта — як у всіх): заливань 4 (ореол, кант, задній шар, передній шар) ≤ 4; малювань ≤ MAX·(3 + ARMS) = 27 ≤ 30 (підкладка DEEP, обідок, внутрішнє, рукави); частинок 0; пелена ≤ 800.
//   Барви — свої (OWN_HUE trails.test: WHITE, RED, BACK_RED, BACK_WHITE, DEEP; голова — м'яча).
// 0 АЛОКАЦІЙ КАДРУ (порт): уся математика спіралі — у буферах BUF на N зразків наперед (Float64Array, ті самі щокадру; trails.test стереже, що
//   вони не ростуть і не підміняються). Примітиви (полігони, стопи) — формат маляра, як у решти стилів; у грі — квади в ті самі FloatArray.
//   Порт: TrailMesmer (game/actors/fx/trail) — ті самі числа MESMER, відрізки ниток квадами (задні, потім передні), кільця й рукави — msdf.circle / полілінії, виняток конверта —
//   як CAPE; вектори — trail-vectors.mjs (поки PORT_PENDING — рівня hypnotic у грі ще немає).
// Без імпорту з trails.mjs (циклу немає): trails.mjs передає свої помічники у фабрику mesmerMaker(H), як BARB і PRISM.
// ─────────────────────────────────────────────────────────────────────────────

export const MESMER = Object.freeze({
  WHITE: "#f4ede3", RED: "#d8173a", DEEP: "#3a0010", HEAD_BALL: 0.4, HEAD_LEN: 0.2,
  BACK_RED: "#6a0a1c", BACK_WHITE: "#8c8680",
  N: 72,                                                  // зразків спіралі рівно по дузі (atS) — розмір буферів BUF (≈ 18 на виток)
  // подвійна спіраль
  AMP_K: 7.4, AMP_EXP: 0.5, PINCH: 12, WAVE_L: 32, TH_K: 0.65, TH_MAX: 4.6, W_BACK: 0.6, A_F: 0.95, A_B: 0.85, A_TAIL: 0.5, HL: 0.45,
  OUT_W: 1.1, OUT_A: 0.85,
  // обертання
  TURN: 1, SNAP: 0.25, BEAT_T: 0.5,
  GLOW_MUL: 1.3, GLOW_A: 0.26, GLOW_HEAD_K: 1.15,
  // гіпно-диск
  WAVE_BEATS: 1, WAVE_T: 0.5, LIFE_BEATS: 2.2, LIFE_MIN: 0.8, LIFE_MAX: 1, MAX: 3,
  R0: 11, R1: 34, ARMS: 6, ARM_N: 10, ARM_IN: 0.16, ARM_SWEEP: 0.55, ARM_W: 3.6, ARM_W_WHITE: 0.7, RIM_W: 2.4, BACK_W: 2.2, IN_K: 0.55, IN_W: 1.8,
  A0: 0.95, A_MIN: 0.4, W_FADE: 0.9, SPIN: 0.6, DISK_GLOW_W: 3.5, W_EPS: 0.08, STILL_Q: 0.2,
  // виняток конверта (власник 24.11, як CAPE): од. кадру вигляду / частка BALL_R
  ENV: 37, LEN_MAX: 400, AHEAD_MAX: 3.7, BALL_R: 10,
  // [v3.1] малий вигляд прев'ю (орба RANKS 34–56, рядок таблиці, картки): λ = mesmerSmall(S) — менше витків, товщі нитки, один диск з SM_ARMS рукавами
  SM_LO: 0.45, SM_HI: 1.0, SM_WAVE: 1.2, SM_TH: 0.6, SM_R: 0.3, SM_ARMS: 4, SM_W: 0.8,
  // [485h] прев'ю: зразків спіралі на виток і найменше (mesmerN) — на картці полиці хвіст ≈ 1 виток, N 72 — утричі густіше за потрібне
  PV_SPT: 18, PV_N_MIN: 24,
});
/** Рядок каталогу (поля — рівно ті, що вимагає trails.test: desc, id, level, name). Легендарний — автоматично (рівень DEMON, dots 5). */
export const MESMER_DEF = Object.freeze({ id: "mesmer", name: "MESMER", level: "hypnotic", desc: "A hypnotist's ribbon · it twists on every beat" });

const MODE_LEVEL = "level", EPS_T = 1e-9, TAU = Math.PI * 2;
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const smooth01 = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));
const easeOut3 = (u) => 1 - (1 - u) * (1 - u) * (1 - u);
const EXPO_K = 1 - Math.pow(2, -10);
const easeOutExpo = (u) => (1 - Math.pow(2, -10 * u)) / EXPO_K;   // 0 → 1 рівно на межах (неперервно через долю)

/** Буфери спіралі на N зразків — ті самі щокадру (0 алокацій у порті; тест стереже незмінність). */
const BUF = Object.freeze({ ph: new Float64Array(MESMER.N), amp: new Float64Array(MESMER.N), w: new Float64Array(MESMER.N), a: new Float64Array(MESMER.N) });
export const mesmerScratch = () => BUF;

/**
 * [v3.1, власник 24.11 (v242): «щоб цей хвіст і те, що крутиться над м'ячем, гарно виглядали в РЕЙТИНГАХ і всюди»] Частка малого вигляду прев'ю
 * (0…1, smoothstep, як barbSmall / keysSmall): S.sm ≤ SM_LO — 1, ≥ SM_HI — 0; у грі (S.sm = S.pm) — 0. Справжні кадри RANKS: на орбі 34 / 46 спіраль
 * була «волосиною» з десятком витків, а три диски по 9 ліній — кашею.
 */
export const mesmerSmall = (S) => (S.sm / S.pm > 1 + 1e-9 ? smooth01((MESMER.SM_HI - S.sm) / (MESMER.SM_HI - MESMER.SM_LO)) : 0);
/** [v3.1] Множник довжин уздовж у прев'ю: us = S.sm / S.pm (ширини там ×sm, позиції ×pm — без нього виток стискається вздовж у «пружинку»). */
export const mesmerUs = (S) => (S.sm / S.pm > 1 + 1e-9 ? S.sm / S.pm : 1);
/**
 * [485h] Зразків спіралі: у грі (S.sm = S.pm) — N; у прев'ю — PV_SPT на виток (len / waveL), не менше PV_N_MIN і не більше N. Порт —
 * TrailMesmer.mesmerN (знімок хвоста емблеми картки полиці пече ≈ 3 мс на Redmi 10C — спіраль 72 зразки на ≈ 1 виток).
 */
export const mesmerN = (S, len, waveL) =>
  !(S.sm / S.pm > 1 + 1e-9) || !(waveL > 0) ? MESMER.N : Math.min(MESMER.N, Math.max(MESMER.PV_N_MIN, Math.ceil((MESMER.PV_SPT * len) / waveL) + 1));
/** Доля хвоста, с: рівень — доля треку (сітка частини), ∞ — BEAT_T. */
export const mesmerBeatT = (F) => (F.mode === MODE_LEVEL ? F.unit : MESMER.BEAT_T);
/** Хід у долях (неперервний і зростає щомиті; частка SNAP — «клац» на долю); «менше руху» — 0. */
export const mesmerBeats = (F) => {
  if (F.reduced) return 0;
  const C = MESMER, x = (F.tH - F.t0) / mesmerBeatT(F), g = Math.floor(x + EPS_T), u = clamp01(x - g);
  return g + (1 - C.SNAP) * u + C.SNAP * easeOutExpo(u);
};
/** Поворот спіралі, рад: TURN оберту на долю (постійне обертання). */
export const mesmerTwist = (F) => TAU * MESMER.TURN * mesmerBeats(F);
/** Глибина білої нитки в зразку з фазою ph: z = sin φ (червона — −z); > 0 — спереду. */
export const mesmerDepth = (ph) => Math.sin(ph);
/** Життя диска, с: LIFE_BEATS долі в [LIFE_MIN, LIFE_MAX]. */
export const mesmerLife = (F) => Math.min(MESMER.LIFE_MAX, Math.max(MESMER.LIFE_MIN, MESMER.LIFE_BEATS * mesmerBeatT(F)));
/** Диски кадру, новіші першими: [{ g — номер долі, t — мить, q — вік 0…1 }]; «менше руху» — []. */
export const mesmerWaves = (F) => {
  const C = MESMER, out = [];
  if (F.reduced) return out;
  const step = F.mode === MODE_LEVEL ? C.WAVE_BEATS * F.unit : C.WAVE_T, life = mesmerLife(F);
  if (!(step > 0)) return out;
  for (let g = Math.floor((F.tH - F.t0) / step + EPS_T); out.length < C.MAX; g--) {
    const t = F.t0 + g * step, age = F.tH - t;
    if (age >= life - EPS_T) break;
    if (age >= -EPS_T) out.push({ g, t, q: clamp01(age / life) });
  }
  return out;
};

/** Маляр MESMER (помічники trails.mjs — ті самі, що в PRISM, + annulusPoly і pos для обідків дисків в ореолі). */
export const mesmerMaker = ({ ribbon, chordGrad, chordU, empty, LOOK_E, headBallOf, annulusPoly, pos }) => (F, S) => {
  const C = MESMER, X = LOOK_E, out = empty(), B = BUF;
  const twist = mesmerTwist(F), lam = mesmerSmall(S), us = mesmerUs(S), small = lam > 0.5;
  const waveL = C.WAVE_L * us * (1 + C.SM_WAVE * lam), pinch = C.PINCH * us, thK = 1 + C.SM_TH * lam;   // [v3.1] прев'ю: виток — у пропорції, малий — менше витків, товщі
  const N = mesmerN(S, F.len, waveL);                                           // [485h] прев'ю — зразків за витками
  const smp = [];
  for (let i = 0; i < N; i++) smp.push(F.atS((F.len * i) / (N - 1)));
  if (smp.some((p) => !p)) return out;
  // спіраль у буфери: фаза, радіус (спадає до хвоста, у голові — 0), півширина нитки, α уздовж (частка)
  for (let i = 0; i < N; i++) {
    const p = smp[i], d = F.len - p.s;
    B.ph[i] = (TAU * d) / waveL - twist;
    B.amp[i] = C.AMP_K * Math.pow(p.f, C.AMP_EXP) * smooth01(d / pinch);
    B.w[i] = Math.min(C.TH_MAX, C.TH_K * X.hw(p.f)) * thK;
    B.a[i] = C.A_TAIL + (1 - C.A_TAIL) * smooth01(p.f / 0.5);
  }
  const ball = (f) => headBallOf(C, f);
  const stops = (fn) => { const st = []; let last = 0; for (let i = 0; i < N; i++) { const p = smp[i]; last = Math.max(last, chordU(F, p.x, p.y)); st.push([last, ...fn(p, i)]); } return st; };
  const halo = [ribbon(S, smp, (p, i) => (B.amp[i] + B.w[i]) * X.pk(C.GLOW_MUL, C.GLOW_HEAD_K, F, p))];
  // точка нитки side (+1 біла, −1 червона): зсув уздовж нормалі, півширина за глибиною, глибина
  const pt = (p, ph, amp, w, side) => { const z = side * Math.sin(ph); return { p, off: side * amp * Math.cos(ph), w: w * (C.W_BACK + (1 - C.W_BACK) * 0.5 * (1 + z)), z }; };
  // корінь sin φ = 0 між зразками i − 1 та i (лінійно за φ): точка на дузі й частка u
  const rootU = (i) => { const a = Math.sin(B.ph[i - 1]), b = Math.sin(B.ph[i]); return a / (a - b); };
  const lerp = (arr, i, u) => arr[i - 1] + (arr[i] - arr[i - 1]) * u;
  const back = [], front = [], kant = [], roots = [];
  for (let i = 1; i < N; i++) if ((Math.sin(B.ph[i]) > 0) !== (Math.sin(B.ph[i - 1]) > 0)) { const u = rootU(i); roots.push([i, u, F.atS(smp[i - 1].s + (smp[i].s - smp[i - 1].s) * u)]); }
  for (const side of [1, -1]) {
    let cur = [], full = [], k = 0;
    for (let i = 0; i < N; i++) {
      if (k < roots.length && roots[k][0] === i) {
        const [, u, pr] = roots[k++], r = pt(pr, lerp(B.ph, i, u), lerp(B.amp, i, u), lerp(B.w, i, u), side), was = cur[cur.length - 1].z > 0;
        cur.push(r); (was ? front : back).push(cur); cur = [r]; full.push(r);
      }
      const q = pt(smp[i], B.ph[i], B.amp[i], B.w[i], side);
      cur.push(q); full.push(q);
    }
    (cur[cur.length - 1].z > 0 ? front : back).push(cur);                       // останній відрізок — до голови (≥ 2 точки: корінь + зразок)
    kant.push(full);
  }
  // смуга відрізка нитки: [L…, R…] (як ribbon), extra — ширше (кант)
  const band = (run, extra) => {
    const Lp = [], Rp = [];
    for (const q of run) {
      const o = q.off, w = q.w + extra(q), cx = q.p.x * S.pm, cy = q.p.y * S.pm;
      Lp.push([cx + q.p.nx * (o + w) * S.sm, cy + q.p.ny * (o + w) * S.sm]); Rp.push([cx + q.p.nx * (o - w) * S.sm, cy + q.p.ny * (o - w) * S.sm]);
    }
    for (let j = Rp.length - 1; j >= 0; j--) Lp.push(Rp[j]);
    return Lp;
  };
  // стопи шарів уздовж хорди: у кожному зразку спереду біла (sin φ > 0) чи червона; на кореневій точці — пара стопів (стара / нова барва)
  const layerStops = (of) => {
    const st = [];
    let last = 0, k = 0;
    for (let i = 0; i < N; i++) {
      if (k < roots.length && roots[k][0] === i) {
        const pr = roots[k++][2], wf = Math.sin(B.ph[i - 1]) > 0, a = C.A_TAIL + (1 - C.A_TAIL) * smooth01(pr.f / 0.5);
        last = Math.max(last, chordU(F, pr.x, pr.y));
        st.push([last, ...of(wf, 0, a, pr.f)], [last, ...of(!wf, 0, a, pr.f)]);
      }
      const p = smp[i], z = Math.sin(B.ph[i]);
      last = Math.max(last, chordU(F, p.x, p.y));
      st.push([last, ...of(z > 0, Math.abs(z), B.a[i], p.f)]);
    }
    return st;
  };
  // кант (обидві нитки повністю, ширші на OUT_W) → задній шар → передній шар
  out.fills.push({ paths: kant.map((r) => band(r, (q) => C.OUT_W * smooth01(q.p.f / 0.15))), grad: chordGrad(F, S, stops((p, i) => [0, C.OUT_A * B.a[i], C.DEEP, 0])) });
  out.fills.push({ paths: back.map((r) => band(r, () => 0)),
    grad: chordGrad(F, S, layerStops((wf, az, a, f) => [0, C.A_B * a * (0.85 + 0.15 * az), wf ? C.BACK_RED : C.BACK_WHITE, 0])) });
  out.fills.push({ paths: front.map((r) => band(r, () => 0)),
    grad: chordGrad(F, S, layerStops((wf, az, a, f) => [wf ? 0 : C.HL * az * az, C.A_F * a * (0.8 + 0.2 * az), wf ? C.WHITE : C.RED, ball(f)])) });
  // гіпно-диски від м'яча: обідок, внутрішнє кільце, ARMS рукавів-спіралей; гаснуть тоншанням (α ≥ A_MIN)
  const H = F.pts[F.M - 1], hx = H[0] * S.pm, hy = H[1] * S.pm, aheadR = C.AHEAD_MAX * C.BALL_R;
  // знімок still (картки SHOP, орбіта RANKS без годинника): один диск у знаковій фазі STILL_Q — легендарний хвіст впізнається й на застиглому кадрі
  const all = S.still ? (F.reduced ? [] : [{ g: 0, t: F.tH - C.STILL_Q * mesmerLife(F), q: C.STILL_Q }]) : mesmerWaves(F).reverse();
  // [v3.1] малий вигляд: лише найновіший диск, SM_ARMS рукавів однакової товщини, без внутрішнього кільця, менший і товщий
  const waves = small ? all.slice(-1) : all, arms = small ? C.SM_ARMS : C.ARMS, rK = 1 - C.SM_R * lam, wK = 1 + C.SM_W * lam;
  for (const v of waves) {
    const r = (C.R0 + (C.R1 - C.R0) * easeOut3(v.q)) * rK, thin = Math.pow(1 - v.q, C.W_FADE) * wK, a = C.A_MIN + (C.A0 - C.A_MIN) * (1 - v.q);
    if (thin / wK * C.IN_W < C.W_EPS) continue;
    const odd = ((v.g % 2) + 2) % 2 === 1, hRim = odd ? C.RED : C.WHITE, hIn = odd ? C.WHITE : C.RED;
    halo.push(annulusPoly(S, H[0], H[1], Math.max(0, r - C.DISK_GLOW_W * thin), Math.min(aheadR, r + C.DISK_GLOW_W * thin)));
    out.rings.push({ x: hx, y: hy, r: r * S.sm, w: (C.RIM_W + C.BACK_W) * thin * S.sm, mixK: 0, alpha: a, hue: C.DEEP });   // темна підкладка обідка — диск видно й на білому
    out.rings.push({ x: hx, y: hy, r: r * S.sm, w: C.RIM_W * thin * S.sm, mixK: 0, alpha: a, hue: hRim });
    if (!small) out.rings.push({ x: hx, y: hy, r: r * C.IN_K * S.sm, w: C.IN_W * thin * S.sm, mixK: 0, alpha: a, hue: hIn });
    const rot = TAU * (v.g * 0.17 - C.SPIN * (F.tH - v.t));                      // крутиться; кожна доля — свій кут
    for (let k = 0; k < arms; k++) {
      const pts = [], th0 = rot + (TAU * k) / arms;
      for (let j = 0; j < C.ARM_N; j++) {
        const u = j / (C.ARM_N - 1), rr = r * (C.ARM_IN + (1 - C.ARM_IN) * u), th = th0 + TAU * C.ARM_SWEEP * u;
        pts.push([hx + Math.cos(th) * rr * S.sm, hy + Math.sin(th) * rr * S.sm]);
      }
      const hue = k % 2 ? C.RED : C.WHITE;
      out.strokes.push({ pts, w: C.ARM_W * (k % 2 || small ? 1 : C.ARM_W_WHITE) * thin * S.sm, grad: chordGrad(F, S, [[0, 0, a, hue, 0], [1, 0, a, hue, 0]]) });
    }
  }
  out.glow = { paths: halo.map(pos), grad: chordGrad(F, S, stops((p) => [0, C.GLOW_A * Math.max(0.35, X.a(p.f)), C.RED, ball(p.f)])) };
  return out;
};
