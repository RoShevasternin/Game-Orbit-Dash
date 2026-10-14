// ─────────────────────────────────────────────────────────────────────────────
// [v47] ХВОСТИ М'ЯЧА — чистий модуль (спека prototype-v47 §5): каталог, межі читабельності, кадр шлейфу і 10 стилів.
// Стиль НЕ малює — віддає примітиви; ті самі примітиви малює JSX (paintTrail), міряє DEV trailInfo (trailInfoOf),
// стереже trails.test.mjs і пише trail-vectors.mjs → Orbit Dash/core/src/test/resources/trail/trail-vectors.json (Kotlin:
// game/actors/fx/trail — TrailHarness = trailFrame, TrailStyle = TRAIL_STYLES, ABallTrail — господар; каталог —
// game/content/TrailCatalog.kt). Модуль без DOM і без годинника: той самий вхід — той самий кадр, біт у біт.
//
// ОДИНИЦІ. Усе — юніти поля (юн., fu) від ЦЕНТРУ орбіти, вісі поля (до камери). Канва прототипу: px = CX + x·FX_K
// (FX_K = 2), у грі — юніти поля як є. У рівні «екранні» довжини стилів (R, R_MIN, DRIFT, SNAP, SIZES, W, PART_R_MAX) —
// одиниці екрана, поділені на зум z (F.scr = 1/z); у безкінечному — юніти поля (F.scr = 1).
//
// ВУЗЛИ — одна форма для обох режимів: { a — кут м'яча, °; r — радіус, юн. поля (= px канви / FX_K); t — час, с }.
//   LEVEL   — час треку E.t, pushLevelTrail v46 як був (кладемо, лише коли годинник пішов уперед; назад — з нуля;
//             старші за початок проміжку — геть, крім одного), лише r = st.radius / FX_K.
//   ENDLESS — годинник шлейфу st.trailT: іде на dt, лише коли м'яч рухається (wdt > 0); вузол щокадру; старші за
//             trailT − ENDLESS_SPAN − 1/60 — геть, стеля 120. Як ABallTrail гри: на 120 Гц шлейф той самий, що на 60.
//
// КАДР trailFrame(nodes, mode, o) → F (o = { L, z, beat, reduced, tH? }; L — годинник рівня: t0, beat, pulseAt(t)):
//   1. POINTS точок рівно по часу t_i = tH − span·(1 − i/23), між вузлами — по дузі орбіти (кут найкоротшим шляхом,
//      радіус лінійно), хвіст — не далі найстарішого вузла (v46 крок 1, обидва режими). span — ½ долі (LEVEL) або
//      ENDLESS_SPAN = 23/60 с (ENDLESS: 24 вузли v46 на 60 Гц).
//   2. Від голови: точки, що злиплись (< 1e-4 юн.), — одна (HOLD стягує шлейф у м'яч); LEVEL — ще й обрізка до
//      LEN_MAX / z (v46 крок 2). ok — є що малювати: LEVEL ≥ 2 точки і len·z ≥ MIN_LEN; ENDLESS ≥ 3 точки (як v46).
//   3. f — 0 хвіст … 1 голова: LEVEL за довжиною дуги, ENDLESS за індексом (як v46); s — дуга від хвоста; nrm — одиничні
//      нормалі з центральних різниць (ліва: L = P + n·w, R = P − n·w — як outline / trailPath v46).
//   Поля: pts, t, tLo (найстаріший час, який точка вміщує — злиплі), f, s, nrm, smp (ті самі точки зразками), len, M, ok,
//   env, span, tH, z, beat (ENDLESS — 0), mode, reduced; функції hw(f), alpha(f), grid(t, div), kick(t), level(t),
//   at(t) — зразок на мить t (позиція — з вузлів по дузі: мітки, прив'язані до часу, стоять на місці кадр у кадр),
//   atS(s) — зразок на дузі s від хвоста; step(divBeats, divE) — крок сітки, с; pick(levelVal, endlessVal).
//   Зразок: { x, y, nx, ny, f, s, t }.
//   env — дозвіл на півширину КОЖНОГО намальованого пікселя (зсув + радіус + пів штриха): LEVEL HW_MAX·(1 + BEAT_W)/z
//   (8.1 од. екрана), але не більше ENDLESS_ENV (9 юн. = 0.9 радіуса м'яча 10) — за малого зуму хвіст ширшим за м'яч не
//   стає (§5.3 «outer half-width < ball radius»); ENDLESS — ENDLESS_ENV.
//
// ПРИМІТИВИ TRAIL_STYLES[id].build(F, s = {}) → { style, mode, glow?, fills, strokes, rings, dots, metrics }:
//   fills   [{ paths: [полігон…], grad | solid, inner? }] — один fill = одне заливання (nonzero) усіх підшляхів;
//           полігон смуги — [L0 … L(m−1), R(m−1) … R0] (як outline v46), квадрат PIXEL — 4 кути тим самим порядком.
//   strokes [{ pts: [[x, y]…], w, grad | solid, inner? }] — ламана, round joins і caps.
//   rings   [{ x, y, r, w, mixK, alpha }] — коло-обвідка (r — до середини штриха, w — товщина).
//   dots    [{ x, y, r, mixK, alpha, inner?, spark? }] — круг.
//   glow?   { paths, solid } — лише ENDLESS: ореол (COMET — v46; решта — формою свого стилю, TRAIL_STYLE_E), малюється
//           ADDITIVE (lighter) першим одним заливанням; поза конвертом і «пеленою» (у безкінечному lighter дозволено, у
//           рівнях — ніколи). Підшляхи ореолу (крім COMET v46) — одного обходу, дірка обідка — протилежного.
//   grad  = { a: [x, y], b: [x, y], stops: [[offset 0…1, mixK, alpha]…] } — лінійний градієнт хорди хвіст → голова;
//   solid = { mixK, alpha }. Барва — ЛИШЕ гравцева: mixK → mix(player, #fff, mixK), плюс α. Своєї барви стиль не має.
//   inner — намальовано всередині попереднього шару (ядро в тілі, серцевина намистини): лише підіймає α, у «пелену» не
//   йде (як v46: пелена — лише тіло). spark — частинка (EMBERS): ліміт PARTS_MAX і PART_R_MAX. Маляр ці поля ігнорує.
//   s = { posMul, wMul } — прев'ю (SHOP, картка, LEVEL COMPLETE): позиції × posMul, розміри × posMul·wMul; метрики —
//   завжди кадру без масштабу.
//
// МАЛЯР (paintTrail — увесь «тонкий маляр» JSX; у грі — квади msdf.circle з field_page, без зміни blend у рівнях):
//   X = cx + x·k, Y = cy + y·k; барва stop/solid → rgba(mix(colour, #fff, mixK), α); порядок — glow (lighter) →
//   fills → strokes → rings → dots (source-over), у кожному масиві — за порядком (старші першими).
//
// МЕТРИКИ trailMetrics(F, prims) — вимір НАМАЛЬОВАНОГО (Kotlin міряє свої примітиви тією самою функцією):
//   lenFu — довжина обрізаного шляху, юн. (як trailInfo v46; на екрані — × z); outerHalfFu — найдальший намальований
//   піксель від осі шляху (вершини полігонів; центр + r кругів; центр + r + w/2 кілець; точки + w/2 штрихів), юн.;
//   veilU2 — «пелена» на екрані, од.²: площа примітивів (без inner) з власною α у [VEIL_A0, VEIL_A1] (чотирикутники смуг,
//   α градієнта в центрі; круги πr²; кільця 2πrw; штрихи довжина·w), × z²; parts — частинок; fills — заливань (з glow);
//   draws — дрібних малювань (штрихи + кільця + круги). Ліміти — §5.5: LEVEL lenFu·z ≤ 120.5, outerHalfFu·z ≤ 8.11,
//   ENDLESS outerHalfFu ≤ 9; veil ≤ 800; parts ≤ 14; fills ≤ 4; draws ≤ 30.
// ─────────────────────────────────────────────────────────────────────────────

// ================================================================ ЕФЕКТИ ЯК У ГРІ — шлейф (перенесено з JSX у v47, числа ті самі)

// ABallTrail — шлейф м'яча (GameScreen: безкінечний ран і туторіал; у рівнях — свій, LEVEL_TRAIL нижче).
// POINTS точок від хвоста до голови, тут — по точці на кадр (гра кладе вузол кожні 1/60 с
// руху: на 90–120 Гц шлейф той самий, що тут на 60). Півширина (HALF_W_TAIL + HALF_W_GROW·f)
// юн. поля, × FX_K — px канви (1.5 + 12·f); ореол — контур ×GLOW_MUL адитивно з α GLOW_A,
// ядро — градієнт хвіст → голова: стопи 0 / CORE_MID / 1 → α 0 / CORE_A_MID / CORE_A_HEAD.
// [v47] вузли — за часом (годинник шлейфу, ENDLESS_SPAN = 23/60 с), не по точці на кадр: на 120 Гц шлейф більше не вдвічі
// коротший; на 60 Гц — той самий полігон, що v46 (trails.test.mjs, «COMET ENDLESS»).
export const TRAIL_FX = {
  POINTS: 24, HALF_W_TAIL: 0.75, HALF_W_GROW: 6,
  GLOW_MUL: 2.1, GLOW_A: 0.14,
  CORE_MID: 0.55, CORE_A_MID: 0.38, CORE_A_HEAD: 0.8,
};
// [v46] Шлейф рівня — «смуга світла» (у грі — після порту ABallTrail з TrailStyle.LEVEL; безкінечний ран і туторіал — TRAIL_FX вище,
// без змін). Числа — не з Figma. Старий шлейф у рівнях ставав сірою плитою: канва прозора над небом DOM, тож
// lighter-ореол лягав пеленою, а зум камери роздував її на пів екрана. Тут: без lighter і без ореолу (source-over),
// довжина й ширина обмежені НА ЕКРАНІ за будь-якого зуму, маса — в яскравій голові.
//   Довжина — ½ долі часу треку: вузли кладуться за E.t (не за кадр — на 120 Гц та сама), у HOLD стоять в одній
//   точці — шлейф стягується в м'яч; POINTS точок рівно по часу; обрізка від голови до LEN_MAX / z юн. поля
//   (z — зум камери × удар фінішу), коротший за MIN_LEN на екрані — не малюється.
//   Півширина, юн. поля: HW_TAIL + (HW_HEAD − HW_TAIL)·f^W_EXP, не більше HW_MAX / z, потім долею ×(1 + BEAT_W·beat·f²).
//   Тіло: α(f) = min(A_MAX, (A_HEAD + BEAT_A·beat)·f^A_EXP), градієнт хвіст → голова у 5 стопах (f 0 … 1).
//   Ядро: та сама смуга × CORE_W, барва mix(player, #fff, CORE_MIX), α = CORE_A·f^CORE_EXP.
//   beat — пульс долі сцени (scene.beatPulse, той, що б'є активне кільце: дроп яскравіший, розминка тьмяніша);
//   барва — P().player (рівень + секція), скін гравця сюди не дістає. f — 0 хвіст … 1 голова, за довжиною дуги.
//   VEIL_MAX — приймання (DEV trailInfo): площа на екрані (u²) ділянок з α 0.05…0.35 — «пелени». FEATHER — лише гра.
//   DEV: window.__orbitTrail = false — вимкнути (у грі — RC lvl_trail, лабораторія `vis trail 0`).
//   Підібрано на знімках v46 (5 рівнів × зум 1 / пік / дроп): смуга лежить на активному кільці тієї ж барви і з числами
//   спеки губилась, тож HW_HEAD 4 → 4.5, A_EXP 1.6 → 1 (тіло щільніше в середині), ядро 0.35 / 0.6 / 0.55·f³ →
//   0.5 / 0.7 / 0.75·f^1.6 (біла нитка видно далі від м'яча). «Пелена» від цього лише менша (≤ 329 u² проти 513).
//   ПОРТ (TrailStyle.LEVEL): числа — ЗВІДСИ, не з тексту спеки v46 §6.2 / §6.3 (там 4 / 1.6 / 0.35 · 0.6 · 0.55·f³ і ядро
//   у 3 стопах f .5 / .75 / 1): ядро — 5 стопів f 0, .25, .5, .75, 1 з α = CORE_A·f^CORE_EXP, як тіло; вузли — полярні
//   {a, r, t}, NODES_MAX. Підібрані числа ще показати власнику на телефоні (разом із v46).
// [v47] це форма COMET у рівнях (trailFrame LEVEL + TRAIL_STYLES.comet); інші 9 стилів живуть у її межах (env, VEIL_MAX).
export const LEVEL_TRAIL = {
  ON: true, SPAN_BEATS: 0.5, POINTS: 24, NODES_MAX: 120,
  LEN_MAX: 120, MIN_LEN: 3,
  HW_TAIL: 0.3, HW_HEAD: 4.5, W_EXP: 0.8, HW_MAX: 6.0, BEAT_W: 0.35,
  A_HEAD: 0.85, A_EXP: 1.0, A_MAX: 0.95, BEAT_A: 0.12,
  CORE_W: 0.5, CORE_MIX: 0.7, CORE_A: 0.75, CORE_EXP: 1.6,
  FEATHER: 0.5, VEIL_MAX: 800, VEIL_A0: 0.05, VEIL_A1: 0.35,
};

/** Дробова частина хешу — стала «випадковість» штриха (без rng: кадр той самий на тій самій миті треку). */
export const h01 = (x) => { const v = Math.sin(x) * 43758.5453; return v - Math.floor(v); };

// ================================================================ каталог

/** [v47] Режими кадру: LEVEL — рівень (час треку, зум, доля), ENDLESS — безкінечний ран (годинник шлейфу, без долі). */
export const TRAIL_MODE = { LEVEL: "level", ENDLESS: "endless" };

// [v47] каталог хвостів: форма без власної барви. price — за ◆; level — лише за ПЕРШИЙ зарахований прохід рівня (REVIVE —
// теж), за ◆ не продається. Новий рівень — новий хвіст (тест: кожен рівень гортача має рівно один).
export const TRAIL_DEFAULT = "comet";
export const TRAILS = [
  { id: "comet",  name: "COMET",  price: 0,    desc: "The classic streak of light" },
  { id: "beads",  name: "BEADS",  price: 500,  desc: "Beads of light that tick on the beat" },
  { id: "dash",   name: "DASH",   price: 900,  desc: "A dashed line that ticks with the music" },
  { id: "helix",  name: "HELIX",  price: 1500, desc: "Two strands · one twist per beat" },
  { id: "embers", name: "EMBERS", price: 2500, desc: "A hot core shedding sparks · more on the drop" },
  { id: "pixel",  name: "PIXEL",  level: "first-light", desc: "8-bit squares that step in 16ths" },
  { id: "echo",   name: "ECHO",   level: "slow-orbit",  desc: "Three ghost rings follow your ball" },
  { id: "drift",  name: "DRIFT",  level: "neon-drift",  desc: "Twin skid marks · flare on the kick" },
  { id: "zap",    name: "ZAP",    level: "hyperlane",   desc: "A crackling bolt · re-strikes on 8ths" },
  { id: "nova",   name: "NOVA",   level: "starborn",    desc: "A ring of light on every beat" },
];
/** [v47] Хвіст, який дає рівень (перший зарахований прохід), або null. */
export const trailOfLevel = (levelId) => (typeof levelId === "string" && TRAILS.find((t) => t.level === levelId)) || null;
/** [v47] Опис хвоста за id, або null. */
export const trailDef = (id) => (typeof id === "string" && TRAILS.find((t) => t.id === id)) || null;

// [v47] Межі для КОЖНОГО стилю (§5.3): ENDLESS_SPAN — с хвоста в безкінечному (24 вузли v46 на 60 Гц); ENDLESS_ENV —
// конверт півширини там, юн. (0.9 радіуса м'яча 10 — хвіст ніколи не ширший за м'яч); частинки — ≤ PARTS_MAX, радіус
// ≤ PART_R_MAX (од. екрана), mixK ≤ MIX_MAX (чисто білих крапок немає: біла — іскра комбо); α голови ≤ HEAD_A_MAX;
// зміна форми в часі ≤ FLICKER_HZ_MAX (перебиває лише ZAP). Числа — не з Figma.
export const TRAIL_RULES = { ENDLESS_SPAN: 23 / 60, ENDLESS_ENV: 9, PARTS_MAX: 14, PART_R_MAX: 2.4, MIX_MAX: 0.6,
  HEAD_A_MAX: 0.95, FLICKER_HZ_MAX: 6 };
// [v47] Числа стилів У РІВНЯХ (§5.4; безкінечний — TRAIL_STYLE_E нижче). *_BEATS — частки долі. Довжини R, R_MIN, DRIFT,
// SNAP, SIZES, W — одиниці екрана (÷ z). *_K — частки hw(f). Числа — не з Figma.
// BEADS.CORE_A і ECHO.MIX — сталі зі спеки (α .9 серцевини, mix .2 кілець), названі тут, щоб у порті не було «магії».
export const TRAIL_STYLE = {
  BEADS:  { GRID_BEATS: 1 / 8, R_K: 1.0, R_MIN: 1.2, CORE_K: 0.45, CORE_MIX: 0.6, CORE_FROM: 0.5, POP: 1.3, N_MAX: 9,
    CORE_A: 0.9 },
  DASH:   { GRID_BEATS: 1 / 8, DUTY: 0.65 },
  HELIX:  { TWIST_BEATS: 1, AMP_K: 0.6, STRAND_K: 0.35, MIX2: 0.35 },
  EMBERS: { CORE_K: 0.45, CORE_MIX: 0.3, N: 7, N_DROP: 14, DROP_LEVEL: 2, R: [0.8, 2.2], DRIFT: 8, MIX: 0.5, A: 0.9 },
  PIXEL:  { GRID_BEATS: 1 / 16, SNAP: 3, SIZES: [2, 4, 6], A_STEPS: [0.25, 0.5, 0.75, 0.95], N_MAX: 12 },
  ECHO:   { GAP_BEATS: 1 / 6, R_K: [0.6, 0.5, 0.4], A: [0.35, 0.24, 0.12], W: 1.2, LINE_K: 0.25, LINE_A: 0.3, BALL_R: 10,
    MIX: 0.2 },
  DRIFT:  { OFF_K: 0.7, MARK_K: 0.3, BASE_A: 0.55, KICK_A: 0.45 },
  ZAP:    { VERTS: 10, JIT_K: 0.5, REROLL_BEATS: 0.5, WIDE_K: 0.5, WIDE_A: 0.5, CORE_K: 0.15, CORE_MIX: 0.6, CORE_A: 0.9,
    VEIL_K: 0.9 },   // [v47] широкий штрих — смуга сталої ширини з α ≤ .5: не ширший, ніж дає VEIL_K·VEIL_MAX «пелени» на всю ламану
  NOVA:   { CORE_K: 0.4, EVERY_BEATS: 1, R0: 0.3, R1: 1.0, W: 1.2, A: 0.5, MIX: 0.3, MAX: 2 },
};
/**
 * [v47] Форми стилів У БЕЗКІНЕЧНОМУ (полірування v47: на знімках кожен куплений чи зароблений хвіст був тоншим і блідішим
 * за безкоштовний COMET, кілька — ледь видні на кільці в крейсері; гравець, що віддав 2 500 ◆, мусить бачити щось
 * багатше). Рівні — НЕ тут: там лишаються межі читабельності v46 (TRAIL_STYLE вище). COMET безкінечного — v46 як був.
 *   LOOK — спільна «оболонка» всіх 9 стилів: півширина hwE(f) = HW_TAIL + (HW_HEAD − HW_TAIL)·f^W_EXP (голова 7.5 юн. —
 *     ширша за 6.75 COMET, але в конверті ENDLESS_ENV 9), α aE(f) = A_HEAD·f^A_EXP (у середині ≈ .56 проти .35 COMET).
 *     Кожен стиль має ореол COMET-а: один заливання lighter барвою гравця з α GLOW_A — але ФОРМОЮ свого стилю (кола
 *     намистин, риски, квадрати, диски відлунь, кільця NOVA…), не загальною смугою: стилі різні з першого погляду.
 *     ROUND — вершин у колі ореолу (многокутник), RING_N — у кільці.
 *   Довжина — та сама ENDLESS_SPAN, що в COMET (вузлів міст тримає рівно стільки). Часи — секунди годинника шлейфу.
 *   BEADS: намистини на сітці GRID с (N_MAX новіших), r R_TAIL → R_HEAD і α ≤ A_MAX (≈ ½ м'яча й напівпрозорі — не
 *     «другий м'яч»), серцевина
 *     CORE_K·r з CORE_MIX (не біла) — від f ≥ CORE_FROM, нитка THREAD_K·hwE з α THREAD_A·aE; ореол — кола GLOW_K·r.
 *   DASH: риски на всю hwE (DUTY кроку GRID), ядро CORE_K (CORE_MIX, CORE_A·f^CORE_EXP); ореол — риски ×GLOW_MUL.
 *   HELIX: два пасма STRAND_K·hwE, розмах AMP_K конверта (сходяться біля голови й хвоста), поворот за TWIST с, друге пасмо
 *     світліше (MIX2); ореол — смуга на весь розмах ×GLOW_MUL.
 *   EMBERS: гаряче ядро CORE_K·hwE (CORE_MIX), N іскор R[0]…R[1] (≤ PART_R_MAX), дрейф до DRIFT, α A·aE^SPARK_EXP (іскри
 *     видно далі в хвості); ореол — ядро ×GLOW_MUL + кожна іскра ×SPARK_GLOW_K.
 *   PIXEL: квадрати на сітці GRID с (N_MAX), центр — на сітці SNAP юн., сторона з SIZES ближча до SIZE_K·hwE, α —
 *     сходинка A_STEPS (3 — ще й ореол у межах 4 заливань); ореол — ті самі квадрати ×GLOW_K.
 *   ECHO: волосина LINE_K·hwE (α LINE_A·aE) і три кільця-відлуння R_K·BALL_R за GAP, 2·GAP, 3·GAP с, товщина W, α A[k];
 *     ореол — диски тих кілець («примари м'яча») і волосина ×GLOW_MUL.
 *   DRIFT: два сліди MARK_K·hwE на OFF_K·hwE від осі, α A·aE, барва MIX; ореол — смуга на обидва сліди ×GLOW_MUL.
 *   ZAP: VERTS вершин, стрибок JIT_K·hwE, перебиття раз на REROLL с; [v50] тіло — смуга ламаної 2·WIDE_K·hwE(f) (звужується
 *     до хвоста, як усі; була штрихом сталої ширини), α WIDE_A·aE (ширина ×fit: «пелена» тіла ≤ VEIL_K·VEIL_MAX — чотирикутники
 *     з α у смузі пелени), ядро — штрих CORE_K·hwE(1) (CORE_MIX, CORE_A·aE); ореол — та сама смуга ×GLOW_K (була сталої
 *     ширини й на хвості різалась рівним прямокутником — «обрізано»).
 *   NOVA: ядро CORE_K·hwE (CORE_MIX), кільця на сітці EVERY с (MAX новіших): ростуть R0 → R1 конверту й гаснуть
 *     A·(1 − q)^FADE_EXP, товщина W, барва MIX; ореол — ядро ×GLOW_MUL і обідки кілець ±RING_GLOW_W.
 * Підібрано на знімках безкінечного (хоп і крейсер, 10 стилів поряд). Числа — не з Figma.
 */
export const TRAIL_STYLE_E = {
  LOOK:   { HW_TAIL: 1, HW_HEAD: 7.5, W_EXP: 0.85, A_HEAD: 0.95, A_EXP: 0.75, GLOW_A: 0.16, ROUND: 16, RING_N: 24 },
  BEADS:  { GRID: 0.028, N_MAX: 14, R_TAIL: 2, R_HEAD: 5.2, A_MAX: 0.8, CORE_K: 0.45, CORE_MIX: 0.35, CORE_A: 0.85, CORE_FROM: 0.3,
    THREAD_K: 0.2, THREAD_A: 0.75, GLOW_K: 2.1 },
  DASH:   { GRID: 0.05, DUTY: 0.62, CORE_K: 0.45, CORE_MIX: 0.7, CORE_A: 0.9, CORE_EXP: 1.2, GLOW_MUL: 1.9 },
  HELIX:  { TWIST: 0.16, AMP_K: 0.62, STRAND_K: 0.42, MIX2: 0.4, GLOW_MUL: 1.5 },
  EMBERS: { CORE_K: 0.6, CORE_MIX: 0.4, N: 14, R: [1.2, 2.4], DRIFT: 9, MIX: 0.55, A: 0.95, SPARK_EXP: 0.5, GLOW_MUL: 1.9,
    SPARK_GLOW_K: 2.6 },
  PIXEL:  { GRID: 0.018, N_MAX: 22, SNAP: 4, SIZES: [4, 6, 8], SIZE_K: 2, A_STEPS: [0.3, 0.6, 0.9], GLOW_K: 1.8 },
  ECHO:   { GAP: 0.1, R_K: [0.8, 0.68, 0.56], A: [0.85, 0.65, 0.45], W: 1.8, LINE_K: 0.42, LINE_A: 0.75, BALL_R: 10, MIX: 0.25,
    GLOW_MUL: 2.4 },
  DRIFT:  { OFF_K: 0.68, MARK_K: 0.4, A: 0.95, MIX: 0.15, GLOW_MUL: 1.3 },
  ZAP:    { VERTS: 12, JIT_K: 0.6, REROLL: 0.2, WIDE_K: 0.65, WIDE_A: 0.9, CORE_K: 0.2, CORE_MIX: 0.65, CORE_A: 0.95, VEIL_K: 0.9,
    GLOW_K: 2.0 },   // [v50] тіло й ореол звужуються до хвоста (makeZapE) — WIDE_K .55 → .65, GLOW_K 1.8 → 2.0 (ореол голови 9.75 < м'яча 10): хвіст — у точку
  NOVA:   { CORE_K: 0.55, CORE_MIX: 0.2, EVERY: 0.125, MAX: 3, R0: 0.45, R1: 1.0, W: 2.4, A: 0.95, FADE_EXP: 0.6, MIX: 0.45,
    GLOW_MUL: 2.0, RING_GLOW_W: 2.2 },
};
/**
 * [v47] Зерна h01 — ЛИШЕ від індексів сітки часу (ніколи Math.random чи годинник стіни): той самий кадр на тій самій
 * миті, після паузи, REVIVE і в автопрогоні. seed(g, k) = g·G + k·K; RADIUS — зсув зерна радіуса іскри EMBERS (§5.4 «+7»).
 */
export const TRAIL_SEED = { G: 12.9898, K: 78.233, RADIUS: 7 };
const seedOf = (g, k = 0) => g * TRAIL_SEED.G + k * TRAIL_SEED.K;

/** [v47] Годинник без треку (прев'ю SHOP: BPM теми меню або PREVIEW_BPM; DEV): доля, сітка від t0, бочки й дропу немає. */
export const PULSE_NONE = Object.freeze({ bp: 0, kp: 0, level: 0 });
export const trailClock = (bpm, t0 = 0) => ({ t0, beat: 60 / bpm, pulseAt: () => PULSE_NONE });

// ================================================================ кадр

const D = Math.PI / 180;
const TAU = Math.PI * 2;
const angDiff = (a, b) => ((a - b) % 360 + 540) % 360 - 180;   // найкоротший шлях кута, ° (як JSX)
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const EPS_T = 1e-9;        // с — допуск часу: межі сітки, хвіст і голова шляху
const EPS_D = 1e-4;        // юн. — точки ближче злиплись в одну (v46)
const ENDLESS_MIN_PTS = 3; // ENDLESS малює від 3 точок (v46: st.trail.length > 2)
const COMET_STOPS = [0, 0.25, 0.5, 0.75, 1];   // 5 стопів градієнтів COMET у рівні (v46: f 0 … 1)

/**
 * Зразок шляху: позиція, одинична нормаль, f, дуга від хвоста s, час t; dev — відстань позиції до осі (ламаної F.pts):
 * 0 на точках шляху; у мітки на мить t (позиція — з вузлів, по дузі) — стрілка дуги між точками: соті частки юн., у хопі
 * до ~0.2. Конверт мітки — env − dev, тож межа §5.5 тримається і для виміряного від ламаної.
 */
const sample = (x, y, nx, ny, f, s, t, dev = 0) => ({ x, y, nx, ny, f, s, t, dev });

/**
 * [v47] Кадр шлейфу — спільний для всіх стилів (див. шапку). nodes — [{a, r, t}] від старших до новіших; mode —
 * TRAIL_MODE; o = { L, z, beat, reduced, tH }. Чиста функція: вузли не змінює.
 */
export function trailFrame(nodes, mode, o = {}) {
  const LV = mode === TRAIL_MODE.LEVEL, T = LEVEL_TRAIL, E = TRAIL_FX, R = TRAIL_RULES;
  const L = LV ? o.L : null;
  if (LV && !(L && L.beat > 0 && typeof L.pulseAt === "function")) throw new Error("trailFrame(LEVEL): o.L — годинник рівня { t0, beat, pulseAt }");
  const z = LV && o.z > 0 ? o.z : 1;
  const beat = LV ? clamp01(o.beat || 0) : 0;
  const span = LV ? T.SPAN_BEATS * L.beat : R.ENDLESS_SPAN;
  const N = nodes.length;
  const tH = N ? (typeof o.tH === "number" ? o.tH : nodes[N - 1].t) : 0;
  const env = LV ? Math.min((T.HW_MAX * (1 + T.BEAT_W)) / z, R.ENDLESS_ENV) : R.ENDLESS_ENV;
  // півширина й α: LEVEL — v46 drawLevelTrail (стеля HW_MAX / z, потім доля), ENDLESS — v46 trailPath і стопи ядра
  const capW = T.HW_MAX / z, swell = T.BEAT_W * beat, aHead = T.A_HEAD + T.BEAT_A * beat;
  const hw = LV
    ? (q) => Math.min(T.HW_TAIL + (T.HW_HEAD - T.HW_TAIL) * Math.pow(q, T.W_EXP), capW) * (1 + swell * q * q)
    : (q) => E.HALF_W_TAIL + E.HALF_W_GROW * q;
  const alpha = LV
    ? (q) => Math.min(T.A_MAX, aHead * Math.pow(q, T.A_EXP))
    : (q) => (q <= E.CORE_MID ? (E.CORE_A_MID * q) / E.CORE_MID : E.CORE_A_MID + ((E.CORE_A_HEAD - E.CORE_A_MID) * (q - E.CORE_MID)) / (1 - E.CORE_MID));
  const t0 = LV ? L.t0 : 0, unit = LV ? L.beat : 1;
  /** Позиція м'яча на мить t з вузлів — по дузі орбіти (як крок 1); поза вузлами — найближчий край. */
  const nodeAt = (t) => {
    let j = 0;
    while (j < N - 2 && nodes[j + 1].t <= t) j++;
    const a = nodes[j], b = nodes[j + 1], u = b.t > a.t ? clamp01((t - a.t) / (b.t - a.t)) : 1;
    const ang = (a.a + angDiff(b.a, a.a) * u) * D, r = a.r + (b.r - a.r) * u;
    return { x: Math.cos(ang) * r, y: Math.sin(ang) * r, t: a.t + (b.t - a.t) * u };
  };
  const F = {
    mode, z, beat, reduced: !!o.reduced, span, tH, t0, unit, scr: LV ? 1 / z : 1, env, L, n: N,
    M: 0, ok: false, len: 0, pts: [], t: [], tLo: [], f: [], s: [], nrm: [], smp: [],
    hw, alpha,
    /** Індекс сітки: LEVEL — floor((t − t0)/(beat·div)), div у долях; ENDLESS — floor(t/div), div у с. */
    grid: (t, div) => Math.floor((t - t0) / (unit * div) + EPS_T),
    /** Крок сітки, с: LEVEL — divBeats долі, ENDLESS — divE с. */
    step: (divBeats, divE) => (LV ? divBeats * L.beat : divE),
    pick: (levelVal, endlessVal) => (LV ? levelVal : endlessVal),
    /** Справжня бочка треку на мить t (LEVEL, відбиток: pulseAt.kp) — ENDLESS 0. */
    kick: (t) => (LV ? L.pulseAt(t).kp : 0),
    /** Рівень такту на мить t (0 розминка … 3 дроп) — ENDLESS 0. */
    level: (t) => (LV ? L.pulseAt(t).level : 0),
    at: () => null,
    atS: () => null,
  };
  if (N < 2) return F;                                         // v46: менше 2 вузлів — нічого
  // 1. POINTS точок рівно по часу; te — мить, яку точка справді показує (хвіст, притиснутий до найстарішого вузла, — його t)
  const P = LV ? T.POINTS : E.POINTS, pts = [], te = [];
  for (let i = 0, j = 0; i < P; i++) {
    const t = tH - span * (1 - i / (P - 1));
    while (j < N - 2 && nodes[j + 1].t <= t) j++;
    const a = nodes[j], b = nodes[j + 1], u = b.t > a.t ? Math.min(1, Math.max(0, (t - a.t) / (b.t - a.t))) : 1;
    const ang = (a.a + angDiff(b.a, a.a) * u) * D, r = a.r + (b.r - a.r) * u;
    pts.push([Math.cos(ang) * r, Math.sin(ang) * r]);
    te.push(a.t + (b.t - a.t) * u);
  }
  // 2. від голови: злиплі точки — одна (tLo — найстаріша мить, яку вона вміщує); LEVEL — обрізка до LEN_MAX / z
  const lim = LV ? T.LEN_MAX / z : Infinity, path = [pts[P - 1]], pt = [te[P - 1]], plo = [te[P - 1]];
  let len = 0;
  for (let i = P - 2; i >= 0; i--) {
    const q = path[path.length - 1], r = pts[i], d = Math.hypot(r[0] - q[0], r[1] - q[1]);
    if (d < EPS_D) { plo[plo.length - 1] = te[i]; continue; }
    if (len + d >= lim) {
      const u = (lim - len) / d, tq = plo[plo.length - 1], tc = tq + (te[i] - tq) * u;
      path.push([q[0] + (r[0] - q[0]) * u, q[1] + (r[1] - q[1]) * u]); pt.push(tc); plo.push(tc);
      len = lim; break;
    }
    len += d; path.push(r); pt.push(te[i]); plo.push(te[i]);
  }
  path.reverse(); pt.reverse(); plo.reverse();                 // хвіст → голова
  const M = path.length;
  F.M = M; F.len = len; F.pts = path; F.t = pt; F.tLo = plo;
  if (M < 2) return F;                                         // HOLD: стягнувся в м'яч
  // 3. f (LEVEL — за дугою, ENDLESS — за індексом), дуга від хвоста, нормалі з центральних різниць
  const f = [0], s = [0];
  for (let i = 1, acc = 0; i < M; i++) {
    acc += Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
    s.push(acc); f.push(LV ? Math.min(1, acc / len) : i / (M - 1));
  }
  f[M - 1] = 1;
  const nrm = [];
  for (let i = 0; i < M; i++) {
    const a = path[Math.max(0, i - 1)], b = path[Math.min(M - 1, i + 1)];
    let dx = b[0] - a[0], dy = b[1] - a[1];
    const l = Math.hypot(dx, dy) || 1;
    dx /= l; dy /= l;
    nrm.push([-dy, dx]);
  }
  F.f = f; F.s = s; F.nrm = nrm;
  F.smp = path.map((p, i) => sample(p[0], p[1], nrm[i][0], nrm[i][1], f[i], s[i], pt[i]));
  F.ok = LV ? len * z >= T.MIN_LEN : M >= ENDLESS_MIN_PTS;
  /** Зразок між точками i і j (частка u): позиція — з вузлів (at) чи ламаної (atS), нормаль — інтерпольована. */
  const between = (i, j, u, pos) => {
    let nx = nrm[i][0] + (nrm[j][0] - nrm[i][0]) * u, ny = nrm[i][1] + (nrm[j][1] - nrm[i][1]) * u;
    const l = Math.hypot(nx, ny);
    if (l > 1e-9) { nx /= l; ny /= l; } else { nx = nrm[j][0]; ny = nrm[j][1]; }
    const x = pos ? pos.x : path[i][0] + (path[j][0] - path[i][0]) * u, y = pos ? pos.y : path[i][1] + (path[j][1] - path[i][1]) * u;
    return sample(x, y, nx, ny, f[i] + (f[j] - f[i]) * u, s[i] + (s[j] - s[i]) * u, pos ? pos.t : pt[i] + (pt[j] - pt[i]) * u, pos ? axisDist(F, x, y) : 0);
  };
  /** Зразок на мить t обрізаного шляху (або null): позиція — з вузлів, f / s / нормаль — з точок шляху за часом. */
  F.at = (t) => {
    if (t < plo[0] - EPS_T || t > pt[M - 1] + EPS_T) return null;
    const pos = nodeAt(Math.min(Math.max(t, plo[0]), pt[M - 1]));
    for (let k = 0; k < M; k++) {
      if (t > pt[k] + EPS_T) continue;
      if (k === 0 || t >= plo[k] - EPS_T) return between(k, k, 0, pos);   // у межах злиплої точки k
      return between(k - 1, k, (t - pt[k - 1]) / (plo[k] - pt[k - 1]), pos);
    }
    return between(M - 1, M - 1, 0, pos);
  };
  /** Зразок на дузі sv від хвоста (по ламаній — рухається разом із м'ячем). */
  F.atS = (sv) => {
    const v = Math.min(Math.max(sv, 0), len);
    for (let k = 1; k < M; k++) if (v <= s[k] || k === M - 1) return between(k - 1, k, s[k] > s[k - 1] ? (v - s[k - 1]) / (s[k] - s[k - 1]) : 1, null);
    return between(M - 1, M - 1, 0, null);
  };
  return F;
}

// ================================================================ збірка примітивів

const UNIT = { pm: 1, sm: 1 };                                 // масштаб кадру (без прев'ю)

/** Полігон смуги по зразках: центр P + n·off, півширина w — [L0 … L(m−1), R(m−1) … R0] (як outline v46). */
const ribbon = (S, smp, wOf, offOf) => {
  const Lp = [], Rp = [];
  for (let i = 0; i < smp.length; i++) {
    const p = smp[i], w = wOf(p, i), o = offOf ? offOf(p, i) : 0, cx = p.x * S.pm, cy = p.y * S.pm;
    Lp.push([cx + p.nx * (o + w) * S.sm, cy + p.ny * (o + w) * S.sm]);
    Rp.push([cx + p.nx * (o - w) * S.sm, cy + p.ny * (o - w) * S.sm]);
  }
  for (let i = Rp.length - 1; i >= 0; i--) Lp.push(Rp[i]);
  return Lp;
};

/** Градієнт хорди шляху хвіст → голова з готовими стопами [o, mixK, α]. */
const chordGrad = (F, S, stops) => ({
  a: [F.pts[0][0] * S.pm, F.pts[0][1] * S.pm], b: [F.pts[F.M - 1][0] * S.pm, F.pts[F.M - 1][1] * S.pm], stops,
});
/** Частка проєкції точки (x, y) на хорду шляху, 0…1. */
const chordU = (F, x, y) => {
  const a = F.pts[0], b = F.pts[F.M - 1], dx = b[0] - a[0], dy = b[1] - a[1], l2 = dx * dx + dy * dy;
  return l2 > 0 ? clamp01(((x - a[0]) * dx + (y - a[1]) * dy) / l2) : 1;
};
/** Градієнт хорди зі стопом на кожен зразок (у проєкції зразка на хорду, неспадно): α = alphaOf(p), барва mixK. */
const pointGrad = (F, S, smp, mixK, alphaOf) => {
  const stops = [];
  let last = 0;
  for (const p of smp) { last = Math.max(last, chordU(F, p.x, p.y)); stops.push([last, mixK, alphaOf(p)]); }
  return chordGrad(F, S, stops);
};
/** Часи сітки (крок step, с, від F.t0) у [tH − span, tH] і на обрізаному шляху, новіші першими: [{ g, t }]. */
const gridTimes = (F, step, max) => {
  const out = [];
  if (!(step > 0)) return out;
  for (let g = Math.floor((F.tH - F.t0) / step + EPS_T); out.length < max; g--) {
    const t = F.t0 + g * step;
    if (t < F.tH - F.span - EPS_T || t < F.tLo[0] - EPS_T) break;
    if (t <= F.tH + EPS_T) out.push({ g, t });
  }
  return out;
};
/** Порожні примітиви кадру (стиль нічого не малює). */
const empty = () => ({ fills: [], strokes: [], rings: [], dots: [] });
const dot = (S, x, y, r, mixK, alpha, extra) => ({ x: x * S.pm, y: y * S.pm, r: r * S.sm, mixK, alpha, ...extra });

// ================================================================ стилі (§5.4)

/** COMET — LEVEL: тіло + ядро v46 (5 стопів); ENDLESS: ореол lighter + ядро v46 (стопи 0 / .55 / 1). Байт у байт v46. */
function makeComet(F, S) {
  if (F.mode === TRAIL_MODE.LEVEL) {
    const T = LEVEL_TRAIL;
    return { fills: [
      { paths: [ribbon(S, F.smp, (p) => F.hw(p.f))], grad: chordGrad(F, S, COMET_STOPS.map((q) => [q, 0, F.alpha(q)])) },
      { paths: [ribbon(S, F.smp, (p) => F.hw(p.f) * T.CORE_W)],
        grad: chordGrad(F, S, COMET_STOPS.map((q) => [q, T.CORE_MIX, T.CORE_A * Math.pow(q, T.CORE_EXP)])), inner: true },
    ], strokes: [], rings: [], dots: [] };
  }
  return makeCometE(F, S);
}

/**
 * [v51] COMET у безкінечному — «крапля світла» (власник 10.10.2026: «хвіст не красивий… перший хвіст на безкінечності треба,
 * щоб це гарно було»). v46 була смугою: ореол сталої α ×2.1 ширший за м'яч і рівний зріз біля голови («плечі»). Тепер:
 *   форма — півширина тіла w(u) = W_TAIL + (W_MAX − W_TAIL)·u^W_EXP, від PINCH_FROM до голови плавно (smoothstep) звужується до
 *   W_HEAD: голова входить під м'яч (r 10) краплею, найширше — трохи позаду м'яча; точок N рівно по дузі (atS) — крива гладка;
 *   світло трьома шарами: ореол lighter ×GLOW_K (у голові ×GLOW_HEAD_K — під м'ячем; поза «пеленою», як ореол v46), тіло ×1
 *   (≤ ENDLESS_ENV), біле ядро ×CORE_K з CORE_FROM (inner); α уздовж — u^A_EXP (кінчик згасає м'яко, маса — біля м'яча), стопи A_STOPS.
 * fills 2 + glow 1 = 3 (ліміт §5.5 — 4); «пелена» — як v46 (лише тіло). Порт: ABallTrail COMET ENDLESS — ті самі смуги квадами.
 */
export const COMET_E = {
  N: 40, W_TAIL: 0.3, W_MAX: 6.6, W_EXP: 1.1, PINCH_FROM: 0.8, W_HEAD: 5.4,
  GLOW_K: 1.9, GLOW_HEAD_K: 1.4, GLOW_A: 0.3,
  BODY_A: 0.92, A_EXP: 1.8,
  CORE_K: 0.36, CORE_MIX: 0.78, CORE_A: 0.95, CORE_FROM: 0.3,
  A_STOPS: [0, 0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1],
};
const smooth01 = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));
function makeCometE(F, S) {
  const C = COMET_E, env = TRAIL_RULES.ENDLESS_ENV;
  const smp = [];
  for (let i = 0; i < C.N; i++) smp.push(F.atS((F.len * i) / (C.N - 1)));
  const pin = (u) => smooth01((u - C.PINCH_FROM) / (1 - C.PINCH_FROM));
  const body = (u) => { const w = C.W_TAIL + (C.W_MAX - C.W_TAIL) * Math.pow(u, C.W_EXP); return w + (C.W_HEAD - w) * pin(u); };
  const layer = (k, kHead) => (p) => body(p.f) * (k + (kHead - k) * pin(p.f));
  const along = (a0, from = 0) => C.A_STOPS.map((q) => [q, 0, q < from ? 0 : a0 * Math.pow((q - from) / (1 - from), C.A_EXP)]);
  const tinted = (stops, mixK) => stops.map(([q, , a]) => [q, mixK, a]);
  return {
    glow: { paths: [ribbon(S, smp, layer(C.GLOW_K, C.GLOW_HEAD_K))], grad: chordGrad(F, S, along(C.GLOW_A)) },
    fills: [
      { paths: [ribbon(S, smp, (p) => Math.min(env, body(p.f)))], grad: chordGrad(F, S, along(C.BODY_A)) },
      { paths: [ribbon(S, smp, layer(C.CORE_K, C.CORE_K))], grad: chordGrad(F, S, tinted(along(C.CORE_A, C.CORE_FROM), C.CORE_MIX)), inner: true },
    ],
    strokes: [], rings: [], dots: [],
  };
}

/** Градієнти «як у COMET» для DASH: тіло — COMET режиму; ядро — гаряча нитка LEVEL_TRAIL (у ENDLESS свого ядра немає). */
const cometBodyGrad = (F, S) => (F.mode === TRAIL_MODE.LEVEL
  ? chordGrad(F, S, COMET_STOPS.map((q) => [q, 0, F.alpha(q)]))
  : chordGrad(F, S, [[0, 0, 0], [TRAIL_FX.CORE_MID, 0, TRAIL_FX.CORE_A_MID], [1, 0, TRAIL_FX.CORE_A_HEAD]]));
const cometCoreGrad = (F, S) => chordGrad(F, S, COMET_STOPS.map((q) => [q, LEVEL_TRAIL.CORE_MIX, LEVEL_TRAIL.CORE_A * Math.pow(q, LEVEL_TRAIL.CORE_EXP)]));

/**
 * BEADS — намистини на сітці часу (1/8 долі | GRID_E): стоять, як тики метронома, + голова; новіші N_MAX. r = max(R_MIN,
 * R_K·hw), ≤ env; α(f); серцевина r·CORE_K (mix CORE_MIX, α CORE_A) — у новішій половині. Пульс долі: найновіша намистина
 * сітки ×(1 + (POP − 1)·beat) — голова лежить під м'ячем (r ≤ env < 10), її «тик» не видно (рішення v47); reduced — без.
 */
function makeBeads(F, S) {
  const C = TRAIL_STYLE.BEADS, out = empty();
  const grid = gridTimes(F, F.step(C.GRID_BEATS), C.N_MAX - 1);
  const beads = [{ t: F.tH, pop: false }, ...grid.map((g, i) => ({ t: g.t, pop: i === 0 }))].slice(0, C.N_MAX).reverse();   // голова + сітка
  const rMin = C.R_MIN * F.scr;
  for (const b of beads) {
    const p = F.at(b.t);
    if (!p) continue;
    let r = Math.max(rMin, C.R_K * F.hw(p.f));
    if (b.pop && !F.reduced) r *= 1 + (C.POP - 1) * F.beat;
    r = Math.min(r, F.env - p.dev);
    out.dots.push(dot(S, p.x, p.y, r, 0, F.alpha(p.f)));
    if (p.f >= C.CORE_FROM) out.dots.push(dot(S, p.x, p.y, r * C.CORE_K, C.CORE_MIX, C.CORE_A, { inner: true }));
  }
  return out;
}

/** DASH — стрічка COMET, порізана на риски [g, g + DUTY·крок] сітки часу (1/8 долі | GRID_E): одне заливання тіла, одне — ядра. */
function makeDash(F, S) {
  const C = TRAIL_STYLE.DASH, step = F.step(C.GRID_BEATS), tTail = F.tLo[0];
  const dashes = [];
  for (const { t: g } of gridTimes(F, step, Infinity).concat(gridTail(F, step))) {
    const ta = Math.max(g, tTail), tb = Math.min(g + C.DUTY * step, F.tH);
    if (tb - ta <= EPS_T) continue;
    const smp = [F.at(ta)];
    for (let k = 0; k < F.M; k++) if (F.tLo[k] > ta + EPS_T && F.t[k] < tb - EPS_T) smp.push(F.smp[k]);
    smp.push(F.at(tb));
    const uniq = smp.filter((p, i) => p && (i === 0 || Math.hypot(p.x - smp[i - 1].x, p.y - smp[i - 1].y) >= EPS_D));
    if (uniq.length >= 2) dashes.unshift(uniq);                // старші першими
  }
  if (!dashes.length) return empty();
  return { fills: [
    { paths: dashes.map((d) => ribbon(S, d, (p) => Math.min(F.hw(p.f), F.env - p.dev))), grad: cometBodyGrad(F, S) },
    { paths: dashes.map((d) => ribbon(S, d, (p) => Math.min(F.hw(p.f), F.env - p.dev) * LEVEL_TRAIL.CORE_W)), grad: cometCoreGrad(F, S), inner: true },
  ], strokes: [], rings: [], dots: [] };
}
/** Риска, що почалась до хвоста (її клітинка сітки старша за span), але ще лежить на шляху. */
const gridTail = (F, step) => {
  if (!(step > 0)) return [];
  const g = Math.floor((Math.max(F.tLo[0], F.tH - F.span) - F.t0) / step + EPS_T), t = F.t0 + g * step;
  return t < Math.max(F.tLo[0], F.tH - F.span) - EPS_T ? [{ g, t }] : [];
};

/** HELIX — два пасма навколо осі, зустрічаються на хвості й голові; фаза — від часу (поворот на долю | TWIST_E). */
function makeHelix(F, S) {
  const C = TRAIL_STYLE.HELIX, period = F.step(C.TWIST_BEATS), h1 = F.hw(1);
  const w = (p) => C.STRAND_K * F.hw(p.f);
  const off = (p) => {
    const A = Math.max(0, Math.min(C.AMP_K * h1 * 4 * p.f * (1 - p.f), F.env - C.STRAND_K * F.hw(p.f)));
    return A * Math.sin((TAU * (p.t - F.t0)) / period);
  };
  const a = (p) => F.alpha(p.f);
  return { fills: [
    { paths: [ribbon(S, F.smp, w, (p) => -off(p))], grad: pointGrad(F, S, F.smp, C.MIX2, a) },
    { paths: [ribbon(S, F.smp, w, off)], grad: pointGrad(F, S, F.smp, 0, a) },
  ], strokes: [], rings: [], dots: [] };
}

/** EMBERS — тонке ядро й іскри, що народжуються на сітці span/N (не ковзають), відпливають убік і гаснуть; дроп — N_DROP. */
function makeEmbers(F, S) {
  const C = TRAIL_STYLE.EMBERS, out = empty();
  out.fills.push({ paths: [ribbon(S, F.smp, (p) => C.CORE_K * F.hw(p.f))], grad: pointGrad(F, S, F.smp, C.CORE_MIX, (p) => F.alpha(p.f)) });
  const n = F.level(F.tH) >= C.DROP_LEVEL ? C.N_DROP : C.N;
  const rMax = TRAIL_RULES.PART_R_MAX * F.scr;
  for (const { g, t } of gridTimes(F, F.span / n, n).reverse()) {
    const p = F.at(t);
    if (!p) continue;
    const seed = seedOf(g), r = Math.min((C.R[0] + (C.R[1] - C.R[0]) * h01(seed + TRAIL_SEED.RADIUS)) * F.scr, rMax, F.env - p.dev);
    const room = Math.max(0, F.env - p.dev - r);
    const d = F.reduced ? 0 : Math.max(-room, Math.min(room, (1 - p.f) * C.DRIFT * F.scr * (2 * h01(seed) - 1)));
    out.dots.push({ x: p.x * S.pm + p.nx * d * S.sm, y: p.y * S.pm + p.ny * d * S.sm, r: r * S.sm, mixK: C.MIX, alpha: C.A * F.alpha(p.f), spark: true });
  }
  return out;
}

/** PIXEL — квадрати на сітці 1/16 долі | GRID_E, центр на сітці SNAP поля, розмір — найближчий із SIZES, α — сходинкою вниз. */
function makePixel(F, S) {
  const C = TRAIL_STYLE.PIXEL, snap = C.SNAP * F.scr, cells = new Set(), byA = new Map();
  for (const { t } of gridTimes(F, F.step(C.GRID_BEATS), C.N_MAX)) {
    const p = F.at(t);
    if (!p) continue;
    const av = F.alpha(p.f);
    let a = 0;
    for (const st of C.A_STEPS) if (st <= av) a = st;            // α — униз до сходинки; нижче першої — не малюємо
    if (!a) continue;
    const ix = Math.floor(p.x / snap + 0.5), iy = Math.floor(p.y / snap + 0.5), key = ix + "," + iy;
    if (cells.has(key)) continue;                                // клітинка вже має новіший квадрат
    const cx = ix * snap, cy = iy * snap, room = F.env - p.dev - Math.hypot(cx - p.x, cy - p.y);
    let side = 0;
    for (const sz of C.SIZES) {                                  // найближчий до 2·hw, що влазить у конверт кутом
      const v = sz * F.scr;
      if (v * Math.SQRT1_2 <= room && v <= 2 * F.env && (!side || Math.abs(v - 2 * F.hw(p.f)) < Math.abs(side - 2 * F.hw(p.f)))) side = v;
    }
    if (!side) continue;
    cells.add(key);
    if (!byA.has(a)) byA.set(a, []);
    byA.get(a).unshift([cx, cy, side / 2]);                      // старші першими
  }
  const fills = [...byA.keys()].sort((x, y) => x - y).map((a) => ({
    paths: byA.get(a).map(([cx, cy, h]) => {
      const X = cx * S.pm, Y = cy * S.pm, H = h * S.sm;
      return [[X - H, Y - H], [X + H, Y - H], [X + H, Y + H], [X - H, Y + H]];
    }),
    solid: { mixK: 0, alpha: a },
  }));
  return { fills, strokes: [], rings: [], dots: [] };
}

/** ECHO — волосина й три порожні кільця за ⅙, ⅓, ½ долі (тріоль) | GAP_E позаду; у HOLD — разом із кадром нічого. */
function makeEcho(F, S) {
  const C = TRAIL_STYLE.ECHO, out = empty(), gap = F.step(C.GAP_BEATS), W = C.W * F.scr;
  out.fills.push({ paths: [ribbon(S, F.smp, (p) => C.LINE_K * F.hw(p.f))], grad: pointGrad(F, S, F.smp, 0, (p) => C.LINE_A * F.alpha(p.f)) });
  for (let k = C.R_K.length; k >= 1; k--) {                    // дальнє першим
    const p = F.at(F.tH - k * gap);
    if (!p) continue;
    const r = Math.min(C.R_K[k - 1] * C.BALL_R, F.env - p.dev - W / 2);
    if (r > 0) out.rings.push({ x: p.x * S.pm, y: p.y * S.pm, r: r * S.sm, w: W * S.sm, mixK: C.MIX, alpha: C.A[k - 1] });
  }
  return out;
}

/** DRIFT — два паралельні сліди шин без ядра; LEVEL — спалах на справжній бочці треку kick(t_i), ENDLESS — рівна α. */
function makeDrift(F, S) {
  const C = TRAIL_STYLE.DRIFT;
  const w = (p) => C.MARK_K * F.hw(p.f), off = (p) => C.OFF_K * F.hw(p.f);
  const a = (p) => F.alpha(p.f) * (C.BASE_A + C.KICK_A * F.kick(p.t));
  // один градієнт на обидва сліди: одне заливання двох підшляхів (сліди не перетинаються: OFF_K ≥ MARK_K)
  return { fills: [{ paths: [ribbon(S, F.smp, w, off), ribbon(S, F.smp, w, (p) => -off(p))], grad: pointGrad(F, S, F.smp, 0, a) }],
    strokes: [], rings: [], dots: [] };
}

/** [v47] Період перебиття ZAP, с: LEVEL — ½ долі, але не частіше FLICKER_HZ_MAX; ENDLESS — REROLL_E. */
export const zapPeriod = (F) => (F.mode === TRAIL_MODE.LEVEL
  ? Math.max(TRAIL_STYLE.ZAP.REROLL_BEATS * F.L.beat, 1 / TRAIL_RULES.FLICKER_HZ_MAX) : TRAIL_STYLE_E.ZAP.REROLL);

/**
 * ZAP — блискавка: VERTS вершин рівними кроками дуги з поперечним стрибком h01(перебиття, k); широкий штрих + ядро.
 * Широкий штрих — смуга СТАЛОЇ ширини 2·WIDE_K·hw(1) з α WIDE_A·α(f): на довгому шляху (безкінечний, 280 °/с — 300 юн.)
 * чи великому зумі вона ставала «пеленою» > VEIL_MAX, тож ширина ≤ VEIL_K·VEIL_MAX / (довжина ламаної·z²); ядро — у тій
 * самій пропорції (CORE_K / WIDE_K).
 */
function makeZap(F, S) {
  const C = TRAIL_STYLE.ZAP, h1 = F.hw(1), roll = F.reduced ? 0 : Math.floor((F.tH - F.t0) / zapPeriod(F) + EPS_T);
  const room = Math.max(0, F.env - C.WIDE_K * h1), smp = [], off = [];
  let polyLen = 0, px = 0, py = 0;
  for (let k = 0; k < C.VERTS; k++) {
    const p = F.atS((F.len * k) / (C.VERTS - 1));
    const j = k < C.VERTS - 1 ? C.JIT_K * F.hw(p.f) * (2 * h01(seedOf(roll, k)) - 1) : 0;   // голова — без стрибка
    const d = Math.max(-room, Math.min(room, j)), x = p.x + p.nx * d, y = p.y + p.ny * d;
    if (k) polyLen += Math.hypot(x - px, y - py);
    px = x; py = y;
    smp.push(p); off.push(d);
  }
  const wide = 2 * C.WIDE_K * h1, fit = polyLen > 0 ? Math.min(1, (C.VEIL_K * LEVEL_TRAIL.VEIL_MAX) / (polyLen * F.z * F.z * wide)) : 1;
  const pts = smp.map((p, k) => [p.x * S.pm + p.nx * off[k] * S.sm, p.y * S.pm + p.ny * off[k] * S.sm]);
  return { fills: [], strokes: [
    { pts, w: wide * fit * S.sm, grad: pointGrad(F, S, smp, 0, (p) => C.WIDE_A * F.alpha(p.f)) },
    { pts: pts.map((q) => q.slice()), w: 2 * C.CORE_K * h1 * fit * S.sm, grad: pointGrad(F, S, smp, C.CORE_MIX, (p) => C.CORE_A * F.alpha(p.f)), inner: true },
  ], rings: [], dots: [] };
}

/** NOVA — тонке ядро й кільце-штамп, де м'яч був на сильній долі (| EVERY_E): росте R0 → R1 і гасне за span; reduced — без кілець. */
function makeNova(F, S) {
  const C = TRAIL_STYLE.NOVA, out = empty(), W = C.W * F.scr;
  out.fills.push({ paths: [ribbon(S, F.smp, (p) => C.CORE_K * F.hw(p.f))], grad: pointGrad(F, S, F.smp, 0, (p) => F.alpha(p.f)) });
  if (F.reduced) return out;
  for (const { t } of gridTimes(F, F.step(C.EVERY_BEATS), C.MAX).reverse()) {
    const p = F.at(t);
    if (!p) continue;
    const q = clamp01((F.tH - t) / F.span), r = Math.min((C.R0 + (C.R1 - C.R0) * q) * (F.env - W / 2), F.env - p.dev - W / 2);
    if (r > 0) out.rings.push({ x: p.x * S.pm, y: p.y * S.pm, r: r * S.sm, w: W * S.sm, mixK: C.MIX, alpha: C.A * (1 - q) * (1 - q) });
  }
  return out;
}

// ================================================================ безкінечний: форми TRAIL_STYLE_E (полірування v47)

/** Оболонка безкінечного (TRAIL_STYLE_E.LOOK): півширина hw(f) і α a(f), юн. поля. */
const LOOK_E = (() => {
  const C = TRAIL_STYLE_E.LOOK;
  return { hw: (q) => C.HW_TAIL + (C.HW_HEAD - C.HW_TAIL) * Math.pow(q, C.W_EXP), a: (q) => C.A_HEAD * Math.pow(q, C.A_EXP) };
})();
/** Подвійна площа многокутника (shoelace): > 0 — «додатний» обхід. */
const area2 = (poly) => { let a = 0; for (let i = 0; i < poly.length; i++) { const p = poly[i], q = poly[(i + 1) % poly.length]; a += p[0] * q[1] - q[0] * p[1]; } return a; };
/**
 * Ореол — ОДНЕ заливання (nonzero) багатьох підшляхів: щоб смуга й коло, що перекриваються, не «вирізали» одне одного,
 * усі підшляхи — одного обходу (додатного); дірка кільця — протилежного (annulus).
 */
const pos = (poly) => (area2(poly) < 0 ? poly.slice().reverse() : poly);
/** Коло ореолу: ROUND вершин, додатний обхід; (x, y, r) — кадру, масштаб прев'ю S. */
const circlePoly = (S, x, y, r, n = TRAIL_STYLE_E.LOOK.ROUND) => {
  const out = [];
  for (let k = 0; k < n; k++) { const a = (TAU * k) / n; out.push([x * S.pm + Math.cos(a) * r * S.sm, y * S.pm + Math.sin(a) * r * S.sm]); }
  return out;
};
/** Обідок r0…r1 одним підшляхом «замкова щілина»: зовнішнє коло додатно, внутрішнє — навпаки (2n + 2 вершин). */
const annulusPoly = (S, x, y, r0, r1, n = TRAIL_STYLE_E.LOOK.RING_N) => {
  const O = circlePoly(S, x, y, r1, n), I = circlePoly(S, x, y, Math.max(0, r0), n);
  return [...O, O[0], I[0], ...I.slice(1).reverse(), I[0]];
};
const glowOf = (paths) => ({ paths: paths.map(pos), solid: { mixK: 0, alpha: TRAIL_STYLE_E.LOOK.GLOW_A } });
/** Градієнт зі стопом на кожну точку шляху: α = aOf(f) оболонки безкінечного. */
const gradE = (F, S, mixK, aOf) => pointGrad(F, S, F.smp, mixK, (p) => aOf(p.f));

/** BEADS ∞ — нитка з намистинами на сітці GRID; ореол — кола довкола намистин. */
function makeBeadsE(F, S) {
  const C = TRAIL_STYLE_E.BEADS, X = LOOK_E, out = empty(), halo = [];
  out.fills.push({ paths: [ribbon(S, F.smp, (p) => C.THREAD_K * X.hw(p.f))], grad: gradE(F, S, 0, (q) => C.THREAD_A * X.a(q)) });
  for (const { t } of gridTimes(F, C.GRID, C.N_MAX).reverse()) {
    const p = F.at(t);
    if (!p) continue;
    const r = Math.min(C.R_TAIL + (C.R_HEAD - C.R_TAIL) * p.f, F.env - p.dev), a = Math.min(C.A_MAX, X.a(p.f));
    if (!(r > 0) || !(a > 0)) continue;
    halo.push(circlePoly(S, p.x, p.y, r * C.GLOW_K));
    out.dots.push(dot(S, p.x, p.y, r, 0, a));
    if (p.f >= C.CORE_FROM) out.dots.push(dot(S, p.x, p.y, r * C.CORE_K, C.CORE_MIX, C.CORE_A * Math.sqrt(p.f), { inner: true }));
  }
  if (halo.length) out.glow = glowOf(halo);
  return out;
}

/** DASH ∞ — риски на всю оболонку з гарячим ядром; ореол — ті самі риски, ширші. */
function makeDashE(F, S) {
  const C = TRAIL_STYLE_E.DASH, X = LOOK_E, tTail = F.tLo[0], dashes = [];
  for (const { t: g } of gridTimes(F, C.GRID, Infinity).concat(gridTail(F, C.GRID))) {
    const ta = Math.max(g, tTail), tb = Math.min(g + C.DUTY * C.GRID, F.tH);
    if (tb - ta <= EPS_T) continue;
    const smp = [F.at(ta)];
    for (let k = 0; k < F.M; k++) if (F.tLo[k] > ta + EPS_T && F.t[k] < tb - EPS_T) smp.push(F.smp[k]);
    smp.push(F.at(tb));
    const uniq = smp.filter((p, i) => p && (i === 0 || Math.hypot(p.x - smp[i - 1].x, p.y - smp[i - 1].y) >= EPS_D));
    if (uniq.length >= 2) dashes.unshift(uniq);
  }
  if (!dashes.length) return empty();
  const w = (p) => Math.min(X.hw(p.f), F.env - p.dev);
  return {
    glow: glowOf(dashes.map((d) => ribbon(S, d, (p) => X.hw(p.f) * C.GLOW_MUL))),
    fills: [
      { paths: dashes.map((d) => ribbon(S, d, w)), grad: gradE(F, S, 0, X.a) },
      { paths: dashes.map((d) => ribbon(S, d, (p) => w(p) * C.CORE_K)), grad: gradE(F, S, C.CORE_MIX, (q) => C.CORE_A * Math.pow(q, C.CORE_EXP)), inner: true },
    ], strokes: [], rings: [], dots: [] };
}

/** HELIX ∞ — два товсті пасма в межах оболонки, поворот за TWIST с; ореол — на весь розмах. */
function makeHelixE(F, S) {
  const C = TRAIL_STYLE_E.HELIX, X = LOOK_E;
  const w = (p) => C.STRAND_K * X.hw(p.f);
  const amp = (p) => Math.max(0, Math.min(C.AMP_K * F.env * Math.sqrt(4 * p.f * (1 - p.f)), F.env - w(p)));
  const off = (p) => amp(p) * Math.sin((TAU * p.t) / C.TWIST);
  return {
    glow: glowOf([ribbon(S, F.smp, (p) => (amp(p) + w(p)) * C.GLOW_MUL)]),
    fills: [
      { paths: [ribbon(S, F.smp, w, (p) => -off(p))], grad: gradE(F, S, C.MIX2, X.a) },
      { paths: [ribbon(S, F.smp, w, off)], grad: gradE(F, S, 0, X.a) },
    ], strokes: [], rings: [], dots: [] };
}

/** EMBERS ∞ — гаряче ядро й N іскор, що відпливають і гаснуть; ореол — ядро й кожна іскра. */
function makeEmbersE(F, S) {
  const C = TRAIL_STYLE_E.EMBERS, X = LOOK_E, out = empty(), rMax = TRAIL_RULES.PART_R_MAX;
  const halo = [ribbon(S, F.smp, (p) => C.CORE_K * X.hw(p.f) * C.GLOW_MUL)];
  out.fills.push({ paths: [ribbon(S, F.smp, (p) => C.CORE_K * X.hw(p.f))], grad: gradE(F, S, C.CORE_MIX, X.a) });
  for (const { g, t } of gridTimes(F, F.span / C.N, C.N).reverse()) {
    const p = F.at(t);
    if (!p) continue;
    const seed = seedOf(g), r = Math.min(C.R[0] + (C.R[1] - C.R[0]) * h01(seed + TRAIL_SEED.RADIUS), rMax, F.env - p.dev);
    const room = Math.max(0, F.env - p.dev - r);
    const d = F.reduced ? 0 : Math.max(-room, Math.min(room, (1 - p.f) * C.DRIFT * (2 * h01(seed) - 1)));
    const x = p.x + p.nx * d, y = p.y + p.ny * d;
    halo.push(circlePoly(S, x, y, r * C.SPARK_GLOW_K));
    out.dots.push({ x: x * S.pm, y: y * S.pm, r: r * S.sm, mixK: C.MIX, alpha: C.A * Math.pow(X.a(p.f), C.SPARK_EXP), spark: true });
  }
  out.glow = glowOf(halo);
  return out;
}

/** PIXEL ∞ — великі квадрати на сітці SNAP, α сходинкою; ореол — ті самі квадрати, більші. */
function makePixelE(F, S) {
  const C = TRAIL_STYLE_E.PIXEL, X = LOOK_E, cells = new Set(), byA = new Map(), halo = [];
  const sq = (cx, cy, h) => { const Xc = cx * S.pm, Yc = cy * S.pm, H = h * S.sm; return [[Xc - H, Yc - H], [Xc + H, Yc - H], [Xc + H, Yc + H], [Xc - H, Yc + H]]; };
  for (const { t } of gridTimes(F, C.GRID, C.N_MAX)) {
    const p = F.at(t);
    if (!p) continue;
    const av = X.a(p.f);
    let a = 0;
    for (const st of C.A_STEPS) if (st <= av) a = st;
    if (!a) continue;
    const ix = Math.floor(p.x / C.SNAP + 0.5), iy = Math.floor(p.y / C.SNAP + 0.5), key = ix + "," + iy;
    if (cells.has(key)) continue;
    const cx = ix * C.SNAP, cy = iy * C.SNAP, room = F.env - p.dev - Math.hypot(cx - p.x, cy - p.y), want = C.SIZE_K * X.hw(p.f);
    let side = 0;
    for (const v of C.SIZES) if (v * Math.SQRT1_2 <= room && (!side || Math.abs(v - want) < Math.abs(side - want))) side = v;
    if (!side) continue;
    cells.add(key);
    if (!byA.has(a)) byA.set(a, []);
    byA.get(a).unshift([cx, cy, side / 2]);
    halo.unshift(sq(cx, cy, (side / 2) * C.GLOW_K));
  }
  const fills = [...byA.keys()].sort((x, y) => x - y).map((a) => ({ paths: byA.get(a).map(([cx, cy, h]) => sq(cx, cy, h)), solid: { mixK: 0, alpha: a } }));
  return { ...(halo.length ? { glow: glowOf(halo) } : {}), fills, strokes: [], rings: [], dots: [] };
}

/** ECHO ∞ — волосина й три кільця-відлуння; ореол — диски кілець («примари м'яча») і волосина. */
function makeEchoE(F, S) {
  const C = TRAIL_STYLE_E.ECHO, X = LOOK_E, out = empty(), halo = [ribbon(S, F.smp, (p) => C.LINE_K * X.hw(p.f) * C.GLOW_MUL)];
  out.fills.push({ paths: [ribbon(S, F.smp, (p) => C.LINE_K * X.hw(p.f))], grad: gradE(F, S, 0, (q) => C.LINE_A * X.a(q)) });
  for (let k = C.R_K.length; k >= 1; k--) {
    const p = F.at(F.tH - k * C.GAP);
    if (!p) continue;
    const r = Math.min(C.R_K[k - 1] * C.BALL_R, F.env - p.dev - C.W / 2);
    if (!(r > 0)) continue;
    halo.push(circlePoly(S, p.x, p.y, r + C.W / 2));
    out.rings.push({ x: p.x * S.pm, y: p.y * S.pm, r: r * S.sm, w: C.W * S.sm, mixK: C.MIX, alpha: C.A[k - 1] });
  }
  out.glow = glowOf(halo);
  return out;
}

/** DRIFT ∞ — два яскраві сліди шин; ореол — смуга на обидва. */
function makeDriftE(F, S) {
  const C = TRAIL_STYLE_E.DRIFT, X = LOOK_E;
  const w = (p) => C.MARK_K * X.hw(p.f), off = (p) => C.OFF_K * X.hw(p.f);
  return {
    glow: glowOf([ribbon(S, F.smp, (p) => (off(p) + w(p)) * C.GLOW_MUL)]),
    fills: [{ paths: [ribbon(S, F.smp, w, off), ribbon(S, F.smp, w, (p) => -off(p))], grad: gradE(F, S, C.MIX, (q) => C.A * X.a(q)) }],
    strokes: [], rings: [], dots: [] };
}

/**
 * ZAP ∞ — блискавка: тіло + біле ядро, перебиття раз на REROLL с; ореол — тією самою ламаною.
 * [v50] Власник: «HYPERLANE (ZAP) на безкінечному виглядає обрізано». Причина — дві смуги СТАЛОЇ ширини: тіло-штрих 2·WIDE_K·hwE(1)
 * і ореол-смуга сталої ширини довкола ламаної (нормалі ламаної, суцільна α GLOW_A lighter) — тіло до хвоста гасне, а ореол ні: на хвості він
 * закінчувався рівним прямокутним зрізом на всю ширину (і з «зазубринами» на зламах: нормалі ламаної перехрещувались). Тепер
 * блискавка — в оболонці LOOK, як решта 8 стилів: тіло — смуга ламаної завширшки 2·WIDE_K·hwE(f) (звужується до хвоста в
 * точку), ореол — та сама смуга ×GLOW_K (теж звужується), обидві — зсувом уздовж нормалі осі (ribbon з offOf: краї не
 * перехрещуються на зламах); ядро — штрих, як був. «Пелена» — площа чотирикутників тіла з α у смузі [VEIL_A0, VEIL_A1]
 * (той самий вимір, що trailMetrics), ширина тіла й ядра — ×fit, щоб її було ≤ VEIL_K·VEIL_MAX.
 */
function makeZapE(F, S) {
  const C = TRAIL_STYLE_E.ZAP, X = LOOK_E, T = LEVEL_TRAIL, roll = F.reduced ? 0 : Math.floor(F.tH / C.REROLL + EPS_T);
  const wide = 2 * C.WIDE_K * X.hw(1), room = Math.max(0, F.env - wide / 2), smp = [], off = [];
  for (let k = 0; k < C.VERTS; k++) {
    const p = F.atS((F.len * k) / (C.VERTS - 1));
    const j = k < C.VERTS - 1 ? C.JIT_K * X.hw(p.f) * (2 * h01(seedOf(roll, k)) - 1) : 0;
    smp.push(p); off.push(Math.max(-room, Math.min(room, j)));
  }
  const offOf = (p, k) => off[k], half = (p) => C.WIDE_K * X.hw(p.f);
  const grad = pointGrad(F, UNIT, smp, 0, (p) => C.WIDE_A * X.a(p.f));
  // «пелена» тіла на fit = 1 — чотирикутники смуги, α градієнта в їхньому центрі (центр від ширини не залежить)
  const band = ribbon(UNIT, smp, half, offOf), m = band.length / 2;
  let veil = 0;
  for (let k = 0; k + 1 < m; k++) {
    const q = [band[k], band[k + 1], band[band.length - 2 - k], band[band.length - 1 - k]];
    let area = 0, cx = 0, cy = 0;
    for (let i = 0; i < 4; i++) { const p = q[i], r = q[(i + 1) % 4]; area += p[0] * r[1] - r[0] * p[1]; cx += p[0] / 4; cy += p[1] / 4; }
    const a = gradAlpha(grad, cx, cy);
    if (a >= T.VEIL_A0 && a <= T.VEIL_A1) veil += Math.abs(area) / 2;
  }
  const fit = veil > 0 ? Math.min(1, (C.VEIL_K * T.VEIL_MAX) / veil) : 1;
  const pts = smp.map((p, k) => [p.x * S.pm + p.nx * off[k] * S.sm, p.y * S.pm + p.ny * off[k] * S.sm]);
  return {
    glow: glowOf([ribbon(S, smp, (p) => half(p) * fit * C.GLOW_K, offOf)]),
    fills: [{ paths: [ribbon(S, smp, (p) => half(p) * fit, offOf)], grad: pointGrad(F, S, smp, 0, (p) => C.WIDE_A * X.a(p.f)) }],
    strokes: [
      { pts, w: 2 * C.CORE_K * X.hw(1) * fit * S.sm, grad: pointGrad(F, S, smp, C.CORE_MIX, (p) => C.CORE_A * X.a(p.f)), inner: true },
    ], rings: [], dots: [] };
}

/** NOVA ∞ — яскраве ядро й кільця-штампи на сітці EVERY, що ростуть і гаснуть; ореол — ядро й обідки кілець. */
function makeNovaE(F, S) {
  const C = TRAIL_STYLE_E.NOVA, X = LOOK_E, out = empty(), halo = [ribbon(S, F.smp, (p) => C.CORE_K * X.hw(p.f) * C.GLOW_MUL)];
  out.fills.push({ paths: [ribbon(S, F.smp, (p) => C.CORE_K * X.hw(p.f))], grad: gradE(F, S, C.CORE_MIX, X.a) });
  if (!F.reduced) {
    for (const { t } of gridTimes(F, C.EVERY, C.MAX).reverse()) {
      const p = F.at(t);
      if (!p) continue;
      const q = clamp01((F.tH - t) / F.span), r = Math.min((C.R0 + (C.R1 - C.R0) * q) * (F.env - C.W / 2), F.env - p.dev - C.W / 2);
      if (!(r > 0)) continue;
      const a = C.A * Math.pow(1 - q, C.FADE_EXP);
      halo.push(annulusPoly(S, p.x, p.y, r - C.RING_GLOW_W, r + C.RING_GLOW_W));
      out.rings.push({ x: p.x * S.pm, y: p.y * S.pm, r: r * S.sm, w: C.W * S.sm, mixK: C.MIX, alpha: a });
    }
  }
  out.glow = glowOf(halo);
  return out;
}

const MAKERS = { comet: makeComet, beads: makeBeads, dash: makeDash, helix: makeHelix, embers: makeEmbers,
  pixel: makePixel, echo: makeEcho, drift: makeDrift, zap: makeZap, nova: makeNova };
/** [v47] Безкінечний: свої форми (TRAIL_STYLE_E); COMET — v46 (makeComet, гілка ENDLESS). */
const MAKERS_E = { comet: makeComet, beads: makeBeadsE, dash: makeDashE, helix: makeHelixE, embers: makeEmbersE,
  pixel: makePixelE, echo: makeEchoE, drift: makeDriftE, zap: makeZapE, nova: makeNovaE };
const makerOf = (F, id) => (F.mode === TRAIL_MODE.ENDLESS ? MAKERS_E : MAKERS)[id];

/**
 * [v47] Стилі: TRAIL_STYLES[id].build(F, s = {}) → примітиви + metrics (шапка). Кадр не ok — порожньо (HOLD, < 2 вузлів).
 * Метрики — завжди кадру без масштабу прев'ю (s лише масштабує вихід).
 */
export const TRAIL_STYLES = Object.fromEntries(TRAILS.map(({ id }) => [id, {
  id,
  build(F, s = {}) {
    const head = { style: id, mode: F.mode };
    const base = F.ok ? makerOf(F, id)(F, UNIT) : empty();
    const metrics = trailMetrics(F, base);
    const pm = typeof s.posMul === "number" ? s.posMul : 1, wm = typeof s.wMul === "number" ? s.wMul : 1;
    const out = pm === 1 && wm === 1 ? base : F.ok ? makerOf(F, id)(F, { pm, sm: pm * wm }) : empty();
    return { ...head, ...(out.glow ? { glow: out.glow } : {}), fills: out.fills, strokes: out.strokes, rings: out.rings, dots: out.dots, metrics };
  },
}]));
/** [v47] Стиль за id; невідомий — COMET (TRAIL_DEFAULT). */
export const trailStyle = (id) => TRAIL_STYLES[id] || TRAIL_STYLES[TRAIL_DEFAULT];

// ================================================================ метрики

/** Відстань точки до осі шляху (ламана F.pts), юн. */
const axisDist = (F, x, y) => {
  const P = F.pts;
  if (!P.length) return 0;
  let best = Math.hypot(x - P[0][0], y - P[0][1]);
  for (let i = 1; i < P.length; i++) {
    const ax = P[i - 1][0], ay = P[i - 1][1], dx = P[i][0] - ax, dy = P[i][1] - ay, l2 = dx * dx + dy * dy;
    const u = l2 > 0 ? clamp01(((x - ax) * dx + (y - ay) * dy) / l2) : 0;
    best = Math.min(best, Math.hypot(x - ax - dx * u, y - ay - dy * u));
  }
  return best;
};
/** α лінійного градієнта в точці (x, y): проєкція на a → b, стопи лінійно, за краями — крайній стоп (як canvas). */
const gradAlpha = (g, x, y) => {
  const dx = g.b[0] - g.a[0], dy = g.b[1] - g.a[1], l2 = dx * dx + dy * dy, S = g.stops;
  const u = l2 > 0 ? clamp01(((x - g.a[0]) * dx + (y - g.a[1]) * dy) / l2) : 0;
  if (u <= S[0][0]) return S[0][2];
  for (let i = 1; i < S.length; i++) {
    if (u <= S[i][0]) { const o0 = S[i - 1][0], o1 = S[i][0]; return o1 > o0 ? S[i - 1][2] + ((S[i][2] - S[i - 1][2]) * (u - o0)) / (o1 - o0) : S[i][2]; }
  }
  return S[S.length - 1][2];
};
const paintAlpha = (p, x, y) => (p.solid ? p.solid.alpha : gradAlpha(p.grad, x, y));

/**
 * [v47] Метрики намальованого (шапка): { lenFu, outerHalfFu, veilU2, parts, fills, draws }. prims — кадру без масштабу.
 * Kotlin TrailStyleTest міряє свої примітиви цією ж функцією (те саме визначення в trail-vectors.json).
 */
export function trailMetrics(F, prims) {
  const T = LEVEL_TRAIL, z2 = F.z * F.z, inVeil = (a) => a >= T.VEIL_A0 && a <= T.VEIL_A1;
  let outer = 0, veil = 0, parts = 0;
  for (const fl of prims.fills) {
    for (const poly of fl.paths) {
      for (const v of poly) outer = Math.max(outer, axisDist(F, v[0], v[1]));
      if (fl.inner) continue;
      // смуга [L0 … L(m−1), R(m−1) … R0] → m − 1 чотирикутників (квадрат PIXEL — один)
      const m = poly.length / 2;
      for (let k = 0; k + 1 < m; k++) {
        const q = [poly[k], poly[k + 1], poly[poly.length - 2 - k], poly[poly.length - 1 - k]];
        let area = 0, cx = 0, cy = 0;
        for (let i = 0; i < 4; i++) { const p = q[i], r = q[(i + 1) % 4]; area += p[0] * r[1] - r[0] * p[1]; cx += p[0] / 4; cy += p[1] / 4; }
        if (inVeil(paintAlpha(fl, cx, cy))) veil += (Math.abs(area) / 2) * z2;
      }
    }
  }
  for (const st of prims.strokes) {
    for (const v of st.pts) outer = Math.max(outer, axisDist(F, v[0], v[1]) + st.w / 2);
    if (st.inner) continue;
    for (let i = 1; i < st.pts.length; i++) {
      const a = st.pts[i - 1], b = st.pts[i], mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2;
      if (inVeil(paintAlpha(st, mx, my))) veil += Math.hypot(b[0] - a[0], b[1] - a[1]) * st.w * z2;
    }
  }
  for (const rg of prims.rings) {
    outer = Math.max(outer, axisDist(F, rg.x, rg.y) + rg.r + rg.w / 2);
    if (inVeil(rg.alpha)) veil += TAU * rg.r * rg.w * z2;
  }
  for (const d of prims.dots) {
    outer = Math.max(outer, axisDist(F, d.x, d.y) + d.r);
    if (d.spark) parts++;
    if (!d.inner && inVeil(d.alpha)) veil += Math.PI * d.r * d.r * z2;
  }
  return { lenFu: F.len, outerHalfFu: outer, veilU2: veil, parts,
    fills: prims.fills.length + (prims.glow ? 1 : 0), draws: prims.strokes.length + prims.rings.length + prims.dots.length };
}

/** [v47] Чи стиль щось малює цього кадру. */
export const trailDrawn = (prims) => !!(prims.glow || prims.fills.length || prims.strokes.length || prims.rings.length || prims.dots.length);

/**
 * [v47] DEV trailInfo кадру — поля v46 (shown, n, spanS, lenFu, z, headHalfFu, veilU2, beat, pts) + style, mode,
 * outerHalfFu, parts, fills, draws. headHalfFu — півширина голови hw(1) з набуханням долі, лише коли намальовано (v46).
 */
export function trailInfoOf(F, prims) {
  const m = prims.metrics, shown = F.ok && trailDrawn(prims);
  return { shown, n: F.n, spanS: F.span, lenFu: F.len, z: F.z, headHalfFu: shown ? F.hw(1) : 0, veilU2: m.veilU2, beat: F.beat,
    pts: F.M, style: prims.style, mode: F.mode, outerHalfFu: m.outerHalfFu, parts: m.parts, fills: m.fills, draws: m.draws };
}

// ================================================================ маляр (тонкий: лише фарбує примітиви)

const hx = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const rgba = (hex, a) => { const c = hx(hex); return `rgba(${c[0]},${c[1]},${c[2]},${a})`; };
const mixHex = (a, b, t) => {
  const x = hx(a), y = hx(b);
  return "#" + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, "0")).join("");
};
const WHITE = "#ffffff";

/**
 * [v47] Малює примітиви на canvas 2D: colour — гравцева барва світу (#rrggbb: скін у безкінечному, рівень/секція в рівні);
 * o = { k — px на юніт (FX_K), cx, cy — центр орбіти, px }. glow — lighter, решта — source-over (кінцевий стан — source-over).
 * Тут нема правил — лише фарба (у грі те саме робить ABallTrail.draw квадами).
 */
export function paintTrail(ctx, prims, colour, o = {}) {
  const k = typeof o.k === "number" ? o.k : 1, cx = o.cx || 0, cy = o.cy || 0, tints = new Map();
  const tint = (mixK) => {
    if (!(mixK > 0)) return colour;
    if (!tints.has(mixK)) tints.set(mixK, mixHex(colour, WHITE, mixK));
    return tints.get(mixK);
  };
  const paint = (p) => {
    if (p.solid) return rgba(tint(p.solid.mixK), p.solid.alpha);
    const g = ctx.createLinearGradient(cx + p.grad.a[0] * k, cy + p.grad.a[1] * k, cx + p.grad.b[0] * k, cy + p.grad.b[1] * k);
    for (const [off, mixK, a] of p.grad.stops) g.addColorStop(off, rgba(tint(mixK), a));
    return g;
  };
  const trace = (paths) => {
    ctx.beginPath();
    for (const poly of paths) {
      ctx.moveTo(cx + poly[0][0] * k, cy + poly[0][1] * k);
      for (let i = 1; i < poly.length; i++) ctx.lineTo(cx + poly[i][0] * k, cy + poly[i][1] * k);
      ctx.closePath();
    }
  };
  if (prims.glow) {
    ctx.globalCompositeOperation = "lighter";
    trace(prims.glow.paths); ctx.fillStyle = paint(prims.glow); ctx.fill();
  }
  ctx.globalCompositeOperation = "source-over";
  for (const fl of prims.fills) { const st = paint(fl); trace(fl.paths); ctx.fillStyle = st; ctx.fill(); }
  if (prims.strokes.length || prims.rings.length) {
    ctx.save();
    ctx.lineJoin = "round"; ctx.lineCap = "round";
    for (const st of prims.strokes) {
      ctx.beginPath();
      ctx.moveTo(cx + st.pts[0][0] * k, cy + st.pts[0][1] * k);
      for (let i = 1; i < st.pts.length; i++) ctx.lineTo(cx + st.pts[i][0] * k, cy + st.pts[i][1] * k);
      ctx.lineWidth = st.w * k; ctx.strokeStyle = paint(st); ctx.stroke();
    }
    for (const rg of prims.rings) {
      ctx.beginPath(); ctx.arc(cx + rg.x * k, cy + rg.y * k, rg.r * k, 0, TAU);
      ctx.lineWidth = rg.w * k; ctx.strokeStyle = rgba(tint(rg.mixK), rg.alpha); ctx.stroke();
    }
    ctx.restore();
  }
  for (const d of prims.dots) {
    ctx.beginPath(); ctx.arc(cx + d.x * k, cy + d.y * k, d.r * k, 0, TAU);
    ctx.fillStyle = rgba(tint(d.mixK), d.alpha); ctx.fill();
  }
}
