// ─────────────────────────────────────────────────────────────────────────────
// [THE FABULOUS] BARB — власний шипастий хвіст лорда ями, легендарна нагорода DEMON THE FABULOUS (Orbit Dash/docs/levels/the-fabulous/SHOW.md §6).
//
// Окремий модуль (не в trails.mjs: той файл правлять інші сесії). Без імпорту з trails.mjs — циклу немає: trails.mjs передає СВОЇ приватні
// помічники у фабрику barbMaker(H) і кладе результат у MAKERS (реєстраційні рядки — BARB_PATCH нижче, застосовує їх лише register.py до КОПІЇ
// прототипу, стенд перегляду; справжня інтеграція — головна сесія після слова власника).
//
// ПРЕДМЕТ (кадр вигляду; у безкінечному — юн. поля, у рівні — од. екрана кадру lookFrame; м'яч — r 10). [ART3, ART3.md §6 — вердикт арт-директора:
//   «вогник дрібний, тіло — гусениця з шевронами»; власник 14.11: «хвіст пекельно червоним з вогником палаючим на кінці»] — частки довжини u від
//   кореня (0) до кінчика (1); хвіст фіксованої довжини LEN росте з-під м'яча назад уздовж шляху (решту шляху не малює — пропорції ті самі на будь-якій
//   швидкості; коротший шлях — увесь предмет стискається вздовж):
//   u 0 → 0.56 — ТІЛО: гладка звужена «жила» без пластин, шевронів і швів (півширина W_ROOT → W_NECK з ease W_EXP), кров'яна рампа
//     SHADOW → FACE → EDGE (корінь темний, ближче до барба — світліша кров; корінь — барвою м'яча HEAD_BALL за HEAD_LEN). Об'єм — шарами:
//     ТЕМНИЙ КОНТУР OUTLINE (штрих по обох краях і довкола леза — відділяє червоне тіло від червоних стін і bgFlash), ПІДШКІРНИЙ ЖАР SUB
//     (смуга SUB_K·W уздовж осі, жар-вугілля під кров'ю), РОЗПЛАВЛЕНЕ ЯДРО (вузька стрічка CORE_K·W: вугілля → жар → лава → горно біля полум'я)
//     і ВІДБЛИСК ЛАВИ (тонка лінія на боці до центру ями — −barbSide, від RIM_FROM до леза). Жар бочки біжить ядром від кореня до барба (barbHeat).
//   u 0.56 → 0.66 — БАРБ: мале обсидіанове лезо з двома гачками, відкинутими до м'яча, і чашею (BARB_SHAPE), комір жару довкола гачків — всередині
//     контуру; чашу й роги накриває полум'я.
//   u ≈ 0.625 → 1.00 — ПОЛУМ'Я (≈ 0.38 LEN): 5 язиків зі спільною основою в чаші — головний FLAME_LF·LEN (≈ 29 од., мерехтіння FLICK), 2 бічні ×SIDE_K
//     віялом, 2 «лизки» ×LICK_K на зовнішніх боках, кінчики закручуються назовні (CURL); зовнішнє полум'я — жар → вугілля → іржа → кров → прозоро
//     (FLAME_STOPS), усередині — світлий ВНУТРІШНІЙ ЯЗИК (горно → лава → жар, INNER_K довжини головного), лінія ядра й крапка горна в основі.
//   Ореол — форма тіла, леза й полум'я ×GLOW_MUL + «лінза» жару під основою полум'я (BLOOM_*), адитивно, першим; [T1] градієнтом поперек осі полум'я:
//   жар на осі → кров HALO → прозоро на краю конверта (м'яке світло, не «вирізка»; одне заливання α не складає — м'якість дає лише градієнт).
// РУХ (чиста функція кадру; час — долі треку в рівні, с годинника шлейфу в ∞):
//   ХЛЬОСТ — біжуча S-хвиля від кореня (0) до барба (SWAY_MAX), фаза φ = barbPhase: раз на долю (у дропі — на вісімку, не частіше 6 Гц), у межах
//     періоду ease-out (зрив на долю — «ляск», далі осідає); ∞ — раз на WHIP_T. На долю барб КЛАЦАЄ (barbClick: поворот SNAP_DEG у бік долі, по черзі,
//     спалах SNAP_BEATS; сильна доля такту — повний, решта — SNAP_WEAK).
//   ЖАР — бочка треку (F.kick) біжить ядром від кореня до барба за HEAT_BEATS долі (точка k з HEAT_N — із затримкою k·HEAT_BEATS/HEAT_N); ∞ — раз на HEAT_T.
//   ДРОП (рівень такту ≥ DROP_LEVEL, barbDrop плавно за KD_IN долі): розплав — підшкірний жар і ядро світліші, кант суцільний, хльост на вісімку.
//   ЗАПАЛЕННЯ (вхід у дроп, рівень такту < 3 → ≥ 3): вогонь пробігає від кореня до барба за IGN_BEATS, тіло спалахує (HEATED), IGN_SPARKS жарин.
//   PULSE (F.L.pulseTimes, будь-який рівень): ЛЯСК — амплітуда ×PULSE_AMP (barbSnap), полум'я ×PULSE_LEN, ядро горном PULSE_CORE_BEATS, ореол
//     +GLOW_PULSE, сплеск PULSE_SPARKS жарин, жар пробігає тілом від кореня.
//   КОЛІНО СТРИБКА (поворот ≥ KNEE_DEG на KNEE_S од.): частина за коліном захльостується пружиною (τ KNEE_TAU, переліт KNEE_OVER) у бік повороту.
//   ПОЛУМ'Я (barbFlame): ключові пози довжин язиків на вісімках (рівень; 130 BPM → 4.3 Гц, не частіше FLICKER_HZ), ∞ — FLICK_HZ_E, плавно між ними,
//     кожен язик — свій хеш; доля — спалах ×BEAT_LEN (основа ×BEAT_W, спад FLASH_TAU_BEATS); дроп — рівний рев ×DROP_LEN (без ляску долі); запалення —
//     коли вогонь добіг до кінчика, ×IGN_LEN; PULSE — ×PULSE_LEN і горно в ядрі; довжина ніколи не виходить за LEN (м'яке коліно barbFlameLen).
//   «Менше руху» і знімок still: застигла S-крива (фаза STILL_PH), застиглі язики ×STILL_K, без клацання, ляску, коліна, мерехтіння й жарин;
//     світло ядра лишається («дихає» з бочкою).
// ЖАРИНИ (spark: true): ≤ SPARK_N разом, r ≤ PART_R_MAX, барви жару й лави, ніколи не білі; лише в грі (не прев'ю, не «менше руху», не знімок):
//   у СПОКОЇ безперервно зриваються з кінчика полум'я — REST_N на кожну REST_BEATS долі (∞ — REST_T с), живуть REST_LIFE_BEATS (3–4 живі), летять назад
//   по шляху REST_BACK і назовні від центру орбіти REST_OUT; ПОДІЇ — PULSE (12), запалення (10), кожна доля дропу (DROP_SPARKS); ∞ — удар жару
//   (HEAT_SPARKS). Події мають першість над спокоєм у стелі SPARK_N. [T1] Жарина — двобарвна: гаряче ядро-крапка (×SPARK_CORE_K) і прозорий обідок —
//   штрих-краплина вздовж руху (звідки летить → ядро), α від 0 у хвості.
// ПРЕВ'Ю (S.sm / S.pm > 1 — SHOP, картка, LEVEL COMPLETE): ті самі примітиви; лезо ×PREV_BARB, полум'я живе (×PREV_FLAME_W завширшки), без жарин.
//   Малий вигляд (barbSmall ≥ ½ — RANKS D ≤ 56): темно-червона лінія з контуром і жилою жару, краплина полум'я (лава → кров) замість леза, кант горна
//   довкола краплини; opaque.
// МЕЖІ (ворота трейлів, як решта стилів, trailEnvOf — без винятку): тіло + розгойдування ≤ ENV_SAFE, ореол і жарини — ≤ ENDLESS_ENV від осі; заливань 4
//   (ореол · тіло + лезо · підшкірний жар + 5 язиків · ядро + внутрішній язик); штрихи 4 (контур / відблиск лави — і на гачок освітленого боку /
//   [T1] «різьба» довкола гачків тоном контуру, гаряча лише на спалах / лінія ядра полум'я) + горно + жарини (крапка + штрих-обідок, spark: true)
//   ≤ 4 + 1 + 2·12 = 29 малювань (≤ 30 воріт); частинок ≤ 12.
// [відхилення від ART3 §6] r жарин 0.6–1.5 (не 0.8–2.0: на ×2 крупні читались «кульками»); штрихів 4, не 3 (відблиск лави окремо від коміра барба —
//   один штрих не обходить обидва гачки, не перетнувши полум'я); прев'ю з коротким шляхом — розхід і ширина язиків ×max(PREV_LAT, стиск), інакше «зірка».
// Порт — [421] TrailBarb (game/actors/fx/trail): ті самі числа BARB (BarbE), вершини 1:1; вектори — trail-vectors.mjs (BARB поза PORT_PENDING, clock.pulse).
// ─────────────────────────────────────────────────────────────────────────────

// Дзеркала чисел trails.mjs, потрібні чистим функціям без H (тест trail-fabulous.test.mjs звіряє їх із trails.mjs один в один)
const MODE_LEVEL = "level";      // TRAIL_MODE.LEVEL
const FLICKER_HZ = 6;            // TRAIL_RULES.FLICKER_HZ_MAX — зміна форми не частіше
const EPS_T = 1e-9;              // с — допуск часу на межах сітки (як trails.mjs)
const TAU = Math.PI * 2;
const D2R = Math.PI / 180;
const clamp01 = (x) => Math.min(1, Math.max(0, x));
const smooth01 = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));
/** Дробова частина хешу — той самий рецепт, що h01 trails.mjs (стала «випадковість» іскри від індексу сітки, без rng). */
const hash01 = (x) => { const v = Math.sin(x) * 43758.5453; return v - Math.floor(v); };
const SEED_G = 12.9898, SEED_K = 78.233;   // TRAIL_SEED.G / K

/**
 * Числа BARB (кадр вигляду; довжини — од., час — долі в рівні / с у ∞). Барви — від шипа рівня FF3B5C на RGB-відстані ≥ 90 (тест; яскраво-червоний
 * ближчий — тому кров тут темна, V ≤ 0.6, а світло дають жар, лава й горно — «кров у тіні, вогонь у світлі», ART3 §0).
 */
export const BARB = Object.freeze({
  // предмет. [ART3 §6.2] тіло 4.4 → 2.3 (гладка жила), лезо — u 0.56 → 0.66, полум'я — u ≈ 0.625 → 1 (0.375 LEN ≈ 36 од.)
  LEN: 96, HEAT_N: 8, W_ROOT: 4.4, W_NECK: 2.3, W_EXP: 0.9, W_BARB: 5.0, SWAY_MAX: 2.2, SWAY_EXP: 1.3, WAVE_K: 0.7, ENV_SAFE: 7.8,
  HEAD_BALL: 0.35, HEAD_LEN: 0.15, BODY_N: 22,
  // кров тіла (рампа barbFlashHue у точках RAMP: корінь → середина → барб → розжарене); обсидіан леза; контур; ореол; кров полум'я; жар · лава · горно
  SHADOW: "#8a2008", FACE: "#c04a0a", EDGE: "#ff7a14", HEATED: "#ffb02e", RAMP: Object.freeze([0, 0.45, 0.85, 1]),
  BODY_X: Object.freeze([0.15, 0.9]),   // місце тіла на рампі: корінь → шийка (спалах тягне до HEATED)
  OBSIDIAN: "#4a0e06", OUTLINE: "#14020a", HALO: "#c81e08", BLOOD: "#c0141c", RUST: "#a0300a", CHAR: "#c84a0a", MOLTEN: "#ffb02e", EMBER: "#ff7a14",
  CORE: "#ffe7a8", A: 0.95,
  // контур: штрих OUTLINE_W по обох краях тіла й довкола леза (без внутрішнього краю чаші — звідти б'є полум'я)
  OUTLINE_W: 0.7, OUTLINE_A: 0,
  // підшкірний жар: смуга SUB_K·W від SUB_FROM·LEN до шийки; барва — рампа barbSubHue (кров-вугілля SUB_LO → іржа RUST → жар-вугілля CHAR);
  //   спокій — SUB_BASE від кореня до шийки, жар бочки +SUB_HEAT, дроп +SUB_DROP; α SUB_A (корінь — SUB_A0)
  SUB_K: 0.7, SUB_FROM: 0.05, SUB_LO: "#c8380a", SUB_MID: "#c86a10", SUB_BASE: Object.freeze([0.2, 0.55]), SUB_HEAT: 0.4, SUB_DROP: 0.25, SUB_A: 0.85, SUB_A0: 0.45,
  // розплавлене ядро: стрічка CORE_K·W від CORE_FROM·LEN крізь лезо до основи полум'я; рампа barbCoreHue CHAR → EMBER → MOLTEN → CORE
  //   (спокій — CORE_BASE від кореня до шийки, жар +CORE_HEAT, дроп +CORE_DROP); α CORE_A0 → CORE_A
  CORE_K: 0.26, CORE_FROM: 0.08, CORE_BASE: Object.freeze([0.35, 0.8]), CORE_HEAT: 0.4, CORE_DROP: 0.15, CORE_A0: 0.6, CORE_A: 0.95,
  // відблиск лави RIM: тонка лінія на боці до центру ями (−barbSide), всередині контуру на KANT_IN, від RIM_FROM·LEN (α 0) до шийки (RIM_A);
  //   кант барба KANT: «комір» жару довкола гачків (форма леза ×KANT_GM)
  KANT_W: 0.6, KANT_A: 0.9, KANT_IN: 0.6, RIM_FROM: 0.16, KANT_GM: 0.78, RIM_W: 0.5, RIM_A: 0,
  // лезо: частки LEN — полум'я FLAME_U (u 0.64 → 1), лезо BLADE_U (u 0.56 → 0.66: заходить під полум'я на BLADE_OVER), основа полум'я — BLADE_WIDE леза;
  //   BARB_MAXF — стеля леза від шляху (прев'ю ×PREV_BARB, малий ×SM_BARB)
  FLAME_U: 0.96, BLADE_U: 0, BLADE_OVER: 0.02, BLADE_WIDE: 0.65, BARB_MAXF: 0.3, BARB_NECK: 0.95,
  // лезо: вершини (частка довжини bl уздовж від шийки, частка півширини W_BARB поперек) — від шийки по одному боці до краю чаші; другий бік — дзеркально.
  //   гачок відкинуто до м'яча (−a), талія, пузо, ріг-зубець за краєм чаші (a > 1 — обіймає основу полум'я), внутрішній край чаші
  BARB_SHAPE: Object.freeze([[-0.38, 0.84], [0.12, 0.44], [0.5, 0.72], [1.1, 1], [0.84, 0.5]]),
  // [ART3 §6.2] полум'я (од. LEN; на шляху — ×Lb / LEN): головний язик FLAME_LF·LEN (≈ 29 од., мерехтіння FLICK), бічні ×SIDE_K, лизки ×LICK_K;
  //   півширина головного FLAME_W (крапля barbTongueW: шийка NECK_K, пузо BUMP на BUMP_S, кінчик (1 − s)^FLAME_EXP); бічна хвиля
  //   A·sin 2π(s/WAVE_L − φ) (A ≤ WAVE_A, біля основи — ×min(1, s/WAVE_ANCHOR)); основа заходить у лезо на FLAME_IN; коліно довжини FLAME_KNEE
  // [18.11.2026, власник: «хвіст зроби, щоб кінчик палав»] полум'я кінчика більше й яскравіше: ширина 3.9 → 5.0 (у конверті ENV_SAFE; довжина — у межах LEN),
  //   внутрішній язик горна довший і ширший (0.7 → 0.8), ореол і «лінза» жару сильніші
  FLAME_LF: 0.7, FLAME_KNEE: 0.72, FLAME_IN: 0.9, FLAME_W: 5.0, BUMP: 0.12, BUMP_S: 0.28, NECK_K: 0.4, FLAME_EXP: 0.9, FLAME_SMP: 11,
  WAVE_A: 1.4, WAVE_L: 0.62, WAVE_ANCHOR: 0.25, WAVE_RATE: 0.25,
  // бічні: віяло ±(SIDE_OFF + SIDE_FAN·s), закрут назовні CURL·s²; лизки — ±(LICK_OFF + LICK_FAN·s), LICK_CURL·s²
  SIDE_K: 0.62, SIDE_OFF: 1.4, SIDE_FAN: 2.0, SIDE_W: 2.8, SIDE_WAVE: 1.0, SIDE_PH: 0.37, CURL: 0.8, FAN_S: 0.3,
  LICK_K: 0.36, LICK_OFF: 2.6, LICK_FAN: 0.8, LICK_W: 1.5, LICK_WAVE: 0.6, LICK_PH: 0.61, LICK_CURL: 0.9,
  // зупинки зовнішнього полум'я від основи (0) до кінчика головного язика (1): [s, барва, α] — жар → жар-вугілля → іржа → кров → прозоро
  // [480 INFERNO, власник: «більш пекельний вогонь»] жар → вугілля → багрянець → кров пекла → сажа → прозоро (темні — далеко від шипа FF3B5C)
//   [власник 21.11, пізно ввечері: «вогонь треба більш темний — кольору лави, такий темно-червоний»] гаряча лава → лава → темний багрянець → кров пекла → сажа
  FLAME_STOPS: Object.freeze([[0, "#c8380a", 1], [0.25, "#b0280a", 1], [0.45, "#8a1408", 0.97], [0.65, "#5a0806", 0.9], [0.85, "#2a0304", 0.7], [1, "#2a0304", 0]]),
  LAVA_HOT: "#c8380a", LAVA_MID: "#c86a10", LAVA: "#b0280a", CRIMSON: "#8a1408", HELLBLOOD: "#5a0806", SOOT: "#2a0304",
  // внутрішній язик (у заливанні ядра): довжина INNER_K головного, півширина INNER_W головного; зупинки — горно → лава → жар → прозоро
  INNER_K: 0.8, INNER_W: 0.6,   // [480b, власник 22.11: «подовжити»] розплавлене ядро — 0.8 головного (було 0.65): помітніше на темних стінах пекла
  INNER_STOPS: Object.freeze([[0, "#ffb02e", 0.92], [0.35, "#c86a10", 0.9], [0.7, "#c8380a", 0.85], [1, "#b0280a", 0]]),   // [480] розплавлене ядро лави (без білого горна)
  CORE_LINE_W: 0.9, CORE_LINE_TO: 0.3, CORE_LINE_A: 0.45,
  // мерехтіння: крок поз — FLICK_BEATS долі (рівень), FLICK_HZ_E Гц (∞); множники довжин — hash01(крок): головний FLICK, бічні й лизки FLICK_SIDE
  // [18.11] живе полум'я fireAt: три хвилі (Гц) з вагами — найвища 3.7 < FLICKER_HZ
  FIRE_HZ: Object.freeze([1.3, 2.3, 3.7]), FIRE_A: Object.freeze([0.55, 0.3, 0.15]),
  FLICK_BEATS: 0.5, FLICK_HZ_E: 5, FLICK: Object.freeze([0.85, 1.25]), FLICK_SIDE: Object.freeze([0.8, 1.3]), STILL_K: 0.85,
  BEAT_LEN: 1.3, BEAT_W: 1.1, PULSE_W: 1.25, FLASH_TAU_BEATS: 0.2, FLASH_T: 0.09, DROP_LEN: 1.2, IGN_LEN: 1.5, PULSE_LEN: 1.5, FLAME_MIX: 0.25,
  CORE_R: 1.1, CORE_DOT_A: 0.95, CORE_FLASH: 0.6, CORE_GROW: 0.45,
  // ореол: тіло й лезо ×GLOW_MUL, полум'я ×FLAME_GLOW; α GLOW_A (ART3: 0.4 → 0.35 — менше «червоне на червоному»), доля +GLOW_BEAT, спалах +GLOW_FLASH,
  //   PULSE — +GLOW_PULSE (абсолютно)
  GLOW_MUL: 1.5, GLOW_A: 0.5, GLOW_BEAT: 0.45, GLOW_FLASH: 0.5, GLOW_PULSE: 0.25, GLOW_N: 24, FLAME_GLOW: 2.5,
  // [ART3 r1 · T1] ЖАР ПІД ПОЛУМ'ЯМ (у тому самому заливанні ореолу — заливань не додає): «лінза» від BLOOM_BACK·bl перед основою до BLOOM_LEN
  //   довжини головного язика, пік півширини BLOOM_W (у конверті, мінус зсув осі) на BLOOM_PEAK; ореол — градієнтом ПОПЕРЕК осі полум'я (BLOOM_STOPS:
  //   жар на осі → кров HALO α 0 на краю конверта) — м'яке світло без різкого краю; α ×(ореол кадру / GLOW_A), спалах / PULSE +BLOOM_FLASH
  BLOOM_W: 11.5, BLOOM_BACK: 0.55, BLOOM_LEN: 0.8, BLOOM_PEAK: 0.3, BLOOM_EXP: 0.6, BLOOM_N: 12, BLOOM_HUE: "#c84a0a", BLOOM_A: 0.9, BLOOM_FLASH: 0.2,
  // поперечні зупинки ореолу [частка від краю (0) до осі (0.5), α, барва]; другий бік — дзеркально; α ×(ореол кадру / GLOW_A)
  BLOOM_STOPS: Object.freeze([[0, 0, "#5a0806"], [0.16, 0.04, "#5a0806"], [0.3, 0.17, "#8a1408"], [0.4, 0.34, "#b0280a"], [0.5, 0.45, "#c8380a"]]),   // [480] темна лава
  SPARK_GLOW_HUE: "#ff7a14", SPARK_RIM_A: 0.38,
  // [ART3 r1 · T1] жарина — двобарвна: ядро (крапка, r ×SPARK_CORE_K, барва гарячіша на SPARK_CORE_HEAT) + прозорий обідок — штрих SPARK_GLOW_HUE
  //   від місця SPARK_TRAIL_Q / REST_TRAIL_Q життя тому до ядра (витягнутий уздовж руху, α 0 у хвості → SPARK_RIM_A·життя біля ядра, круглі кінці),
  //   півширина rh = SPARK_HALO·r (≤ PART_R_MAX); слабша за SPARK_HALO_MIN α — без обідка
  SPARK_CORE_K: 0.72, SPARK_CORE_HEAT: 0.3, SPARK_HALO: 1.3, SPARK_TRAIL_Q: 0.32, REST_TRAIL_Q: 0.3, SPARK_HALO_MIN: 0.12,
  // [ART3 r1 · T1] гачки: комір довкола гачків — тоном контуру (BARB_GROOVE, «різьба» по крові леза), гарячий лише на спалах (×GROOVE_FLASH);
  //   відблиск лави продовжено на гачок освітленого боку (шийка → вістря гачка → талія) — гарячий обідок одного боку, не «Y»
  BARB_GROOVE: "#1e0308", GROOVE_A: 0, GROOVE_FLASH: 0.3,
  // рух
  WHIP_BEATS: 1, WHIP_DROP_BEATS: 0.5, WHIP_T: 0.45, WHIP_EXP: 1.8, WHIP_EXP_DROP: 2.4, DROP_LEVEL: 3, KD_IN: 0.25, STILL_PH: 0.25,
  SNAP_DEG: 10, SNAP_BEATS: 0.15, SNAP_TAU_BEATS: 0.18, SNAP_WEAK: 0.5, SNAP_T: 0.07, SNAP_E: 0.6,
  HEAT_BEATS: 0.25, HEAT_TAU_BEATS: 0.3, HEAT_T: 0.9, HEAT_RUN_T: 0.12, HEAT_TAU_T: 0.16, HEAT_E: 0.7, KICK_MIN: 0.05,
  IGN_BEATS: 0.5, IGN_TAU_BEATS: 0.6, IGN_WIN_BEATS: 2, IGN_SPARKS: 10,
  PULSE_AMP: 1.5, PULSE_TAU_BEATS: 0.35, PULSE_CORE_BEATS: 0.25, PULSE_WIN_BEATS: 1.5, PULSE_SPARKS: 12, PULSE_FLASH: 0.7,
  KNEE_DEG: 30, KNEE_S: 10, KNEE_STEP: 2, KNEE_TAU: 0.08, KNEE_OVER: 0.15, KNEE_AMP: 2.0, KNEE_RAMP: 18, KNEE_WIN: 0.5,
  // жарини подій
  SPARK_N: 12, SPARK_LIFE_BEATS: 0.4, SPARK_LIFE_T: 0.3, SPARK_R: Object.freeze([0.6, 1.5]), SPARK_BACK: Object.freeze([4, 16]),
  SPARK_OUT: Object.freeze([1.5, 6.5]), SPARK_MIX: Object.freeze([0.04, 0.22]), SPARK_A: 0.95, SPARK_HEAT: Object.freeze([0.5, 0.28]), DROP_SPARKS: 3, HEAT_SPARKS: 4,
  // [ART3 §6.4] жарини спокою: REST_N на кожну REST_BEATS долі (∞ REST_T с), життя REST_LIFE_BEATS (∞ REST_LIFE_T), назад REST_BACK, назовні REST_OUT,
  //   r REST_R; з'являються за REST_IN життя (без «вискакування»)
  REST_N: 2, REST_BEATS: 0.125, REST_T: 0.065, REST_LIFE_BEATS: 0.5, REST_LIFE_T: 0.26, REST_BACK: Object.freeze([6, 20]), REST_OUT: Object.freeze([2, 8]),
  REST_R: Object.freeze([0.6, 1.5]), REST_IN: 0.12, REST_HEAT: 0.45,
  // прев'ю й малий вигляд (краплина: півширина SM_DROP_W, пузо SM_DROP_BUMP)
  PREV_BARB: 1.2, PREV_BLADE_W: 1.1, PREV_FLAME_W: 1.4, PREV_LAT: 0.45, SM_LO: 0.45, SM_HI: 1.0, SM_BARB: 1.6, SM_W: 1.25, SM_EDGE_W: 0.5, MIN_U: 0.5, SM_DROP_W: 4.2, SM_DROP_BUMP: 0.35,
  // [480 INFERNO] краплина малого вигляду — язик полум'я: [s, барва, α] горно → лава → жар → вугілля, кінчик гасне; хвиля SM_WAVE; край — жар α SM_EDGE_A
  SM_DROP_STOPS: Object.freeze([[0, "#ffb02e", 0.95], [0.25, "#c86a10", 0.95], [0.5, "#c8380a", 0.92], [0.75, "#8a1408", 0.85], [1, "#2a0304", 0.2]]), SM_WAVE: 1.2, SM_EDGE_A: 0.5,
  WHIP_PREVIEW: 1,
  // [480 INFERNO] язики тіла: BL_N язиків від BL_U0 до BL_U1 шийки (по черзі на боки, перший — назовні від центру орбіти), назад по шляху: довжина
  //   BL_LEN·(Lb / LEN) × мерехтіння fireAt (сіль BL_SALT0 + BL_SALT_STEP·i, діапазон FLICK_SIDE) × (1 + BL_FLASH·спалах), півширина BL_W (крапля
  //   barbTongueW), корінь — BL_ROOT півширини тіла від осі, віяло назовні BL_OUT·s + BL_CURL·s², хвиля BL_WAVE; у заливанні підшкірного жару (без нових заливань)
  // [480 INFERNO, власник: «щоб частина м'яча була в лаві біля хвоста»] ЛАВОВА ЧАША: півкільце лави довкола спини м'яча (r CUP_IN → CUP_R, ±CUP_DEG від
  //   напряму назад; поперек — не далі конверта, тож чаша не виходить за ±ENV від осі й не лізе вперед) — у заливанні полум'я (барва — жар основи); гарячий
  //   обідок, де м'яч «тоне» в лаві (r CUP_HOT_IN → CUP_HOT_R), — у заливанні ядра (горно → лава); CUP_N відрізків дуги
  //   Чашу будуємо КОЛОМ довкола м'яча (r ×BALL_R·zs·bm — радіус м'яча на цьому екрані, як фібула CAPE), у прямій рамці голови, не вздовж шляху;
  //   по зовнішньому краю — темна кірка лави (штрих, що був контуром тіла: CRUST_W, α CRUST_A, барва SOOT)
  CUP_IN: 0.8, CUP_R: 1.26, CUP_DEG: 70, CUP_N: 14, CUP_LIM: 0.8, BALL_R: 10, CRUST_W: 0.9, CRUST_A: 0.85,
  BL_N: 4, BL_U0: 0.1, BL_U1: 0.55, BL_LEN: 16, BL_W: 1.9, BL_ROOT: 0.55, BL_OUT: 3.2, BL_CURL: 1.2, BL_WAVE: 0.6, BL_SALT0: 0.19, BL_SALT_STEP: 0.23, BL_FLASH: 0.4, BL_SMP: 9,
  // конверт: кадр вигляду; дзеркало TRAIL_RULES.ENDLESS_ENV (тест)
  ENV: 9,
});

/** Рядок каталогу (поля — рівно ті, що вимагає trails.test: desc, id, level, name). Легендарний — автоматично (рівень DEMON, dots 5). */
export const BARB_DEF = Object.freeze({ id: "inferno", name: "INFERNO", level: "the-fabulous", desc: "The pit lord's tail of living fire · it lashes on every beat" });
/** Поза сцени TRAILS у SHOP (LEGEND_BY.barb): хвіст звисає під м'ячем, як плащ CAPE. */
export const BARB_SHOP = Object.freeze({ ZOOM: 1.4, AT: 200, CX: 290, CY: 96, HOP_BEATS: 2 });


// ================================================================ час: хльост, клацання, жар, дроп, запалення, PULSE (чисті функції кадру F)

const isLevel = (F) => F.mode === MODE_LEVEL;
/** Рівень такту посередині періоду n (як capeBeat: мить t0 + n·T може округлитись у попередній такт). */
const levelMid = (F, n, T) => F.level(F.t0 + (n + 0.5) * T);
/**
 * Частка дропу 0…1 (рівень такту ≥ DROP_LEVEL): за KD_IN долі від першої долі дропу 0 → 1, на виході 1 → 0 — неперервно. ∞ / «менше руху» — 0
 * (у «менше руху» ядро однаково світить — розплав лише яскравішає, тож 0 — спокійніше).
 */
export const barbDrop = (F) => {
  if (!isLevel(F) || F.reduced) return 0;
  const C = BARB, x = (F.tH - F.t0) / F.unit, n = Math.floor(x + EPS_T), fr = clamp01(x - n);
  const cur = levelMid(F, n, F.unit) >= C.DROP_LEVEL, prev = levelMid(F, n - 1, F.unit) >= C.DROP_LEVEL, k = smooth01(fr / C.KD_IN);
  return cur ? (prev ? 1 : k) : prev ? 1 - k : 0;
};
/** Період хльосту, с: рівень — доля (у дропі — вісімка), не частіше FLICKER_HZ; ∞ — WHIP_T. Дроп — за рівнем посередині поточної долі. */
export const barbWhipT = (F) => {
  const C = BARB;
  if (!isLevel(F)) return C.WHIP_T;
  const n = Math.floor((F.tH - F.t0) / F.unit + EPS_T), drop = levelMid(F, n, F.unit) >= C.DROP_LEVEL;
  return Math.max((drop ? C.WHIP_DROP_BEATS : C.WHIP_BEATS) * F.unit, 1 / FLICKER_HZ);
};
/**
 * Фаза хльосту φ (у періодах): n + 1 − (1 − fr)^EXP — ease-out у межах періоду (зрив на долю, далі осідає), неперервна за значенням sin(2πφ);
 * у дропі EXP більший (різкіше). «Менше руху» — STILL_PH (застигла S-крива). Межі дропу — на межах тактів, де обидва періоди починаються разом.
 */
export const barbPhase = (F) => {
  const C = BARB;
  if (F.reduced) return C.STILL_PH;
  const T = barbWhipT(F), x = (F.tH - F.t0) / T, n = Math.floor(x + EPS_T), fr = clamp01(x - n);
  const drop = isLevel(F) && T < C.WHIP_BEATS * F.unit - 1e-9;
  return n + 1 - Math.pow(1 - fr, drop ? C.WHIP_EXP_DROP : C.WHIP_EXP);
};
/**
 * Клацання барба на долю: { ang — поворот, рад (знак — по черзі: парна доля вліво, непарна вправо), flash — спалах ядра 0…1 }.
 * Рівень: кожна доля, сильна (перша такту) — повна, решта — SNAP_WEAK; поворот гасне за SNAP_TAU_BEATS, спалах — за SNAP_BEATS. ∞ — кожен WHIP_T,
 * сила SNAP_E, згасання SNAP_T. «Менше руху» — 0.
 */
export const barbClick = (F) => {
  const C = BARB;
  if (F.reduced) return { ang: 0, flash: 0 };
  const LV = isLevel(F), step = LV ? F.unit : C.WHIP_T, x = (F.tH - F.t0) / step, n = Math.floor(x + EPS_T), age = Math.max(0, F.tH - (F.t0 + n * step));
  const k = LV ? (((n % 4) + 4) % 4 === 0 ? 1 : C.SNAP_WEAK) : C.SNAP_E;
  const tau = LV ? C.SNAP_TAU_BEATS * F.unit : C.SNAP_T, fl = LV ? C.SNAP_BEATS * F.unit : C.SNAP_T;
  const sgn = n % 2 === 0 ? 1 : -1;
  return { ang: sgn * k * C.SNAP_DEG * D2R * Math.exp(-age / tau), flash: k * clamp01(1 - age / fl) };
};
/** Остання мить PULSE ≤ tH з F.L.pulseTimes (двійковий пошук; порт — курсор) → { i, t } або null. Лише рівень. */
const lastPulse = (F) => {
  const P = isLevel(F) && F.L && F.L.pulseTimes;
  if (!P || !P.length) return null;
  let lo = 0, hi = P.length;
  while (lo < hi) { const m = (lo + hi) >> 1; if (P[m] <= F.tH + EPS_T) lo = m + 1; else hi = m; }
  return lo > 0 ? { i: lo - 1, t: P[lo - 1] } : null;
};
/**
 * Сила ляску PULSE 0…1: exp(−вік / PULSE_TAU_BEATS долі) у вікні PULSE_WIN_BEATS після миті PULSE (F.L.pulseTimes), будь-який рівень такту.
 * «Менше руху» — 0 (рух); світло ядра — barbPulseCore.
 */
export const barbSnap = (F) => {
  if (F.reduced) return 0;
  const p = lastPulse(F), C = BARB;
  if (!p) return 0;
  const age = F.tH - p.t;
  return age >= 0 && age < C.PULSE_WIN_BEATS * F.unit ? Math.exp(-age / (C.PULSE_TAU_BEATS * F.unit)) : 0;
};
/** Біле ядро на PULSE 0…1: перші PULSE_CORE_BEATS долі — 1, далі за стільки ж — до 0 (світло: діє й у «менше руху»). */
export const barbPulseCore = (F) => {
  const p = lastPulse(F), C = BARB;
  if (!p) return 0;
  const age = (F.tH - p.t) / F.unit;
  return age < 0 ? 0 : age < C.PULSE_CORE_BEATS ? 1 : 1 - smooth01((age - C.PULSE_CORE_BEATS) / C.PULSE_CORE_BEATS);
};
/**
 * Запалення — вхід у дроп (рівень такту посередині такту n ≥ DROP_LEVEL, у такті n − 1 — ні): { age — с від першої долі такту, n } або null
 * (вікно IGN_WIN_BEATS долі). Лише рівень.
 */
export const barbIgnite = (F) => {
  if (!isLevel(F)) return null;
  const C = BARB, bar = 4 * F.unit, nb = Math.floor((F.tH - F.t0) / bar + EPS_T);
  for (let n = nb; n >= nb - 1; n--) {
    const t = F.t0 + n * bar, age = F.tH - t;
    if (age < 0 || age >= C.IGN_WIN_BEATS * F.unit) continue;
    if (levelMid(F, n, bar) >= C.DROP_LEVEL && levelMid(F, n - 1, bar) < C.DROP_LEVEL) return { age, n };
  }
  return null;
};
/** Спалах точки тіла k (0 — біля кореня) від запалення: фронт вогню доходить до неї за (k + ½)/HEAT_N·IGN_BEATS, далі гасне за IGN_TAU_BEATS. */
const igniteAt = (F, ig, k) => {
  if (!ig) return 0;
  const C = BARB, tk = ((k + 0.5) / C.HEAT_N) * C.IGN_BEATS * F.unit, a = ig.age - tk;
  return a < 0 ? 0 : Math.exp(-a / (C.IGN_TAU_BEATS * F.unit));
};
/**
 * Жар точки ядра k (0 — біля кореня … HEAT_N − 1 — шийка) 0…1: рівень — бочка треку F.kick у мить tH − k·HEAT_BEATS/HEAT_N долі (жар біжить ядром від
 * кореня до барба за HEAT_BEATS), max із запаленням і білим ядром PULSE; ∞ — удар раз на HEAT_T, біжить за HEAT_RUN_T, гасне за HEAT_TAU_T.
 * Світло (не рух): діє й у «менше руху».
 */
export const barbHeat = (F, k) => {
  const C = BARB;
  if (!isLevel(F)) {
    const d = (k / C.HEAT_N) * C.HEAT_RUN_T, x = (F.tH - d) / C.HEAT_T, n = Math.floor(x + EPS_T), age = F.tH - d - n * C.HEAT_T;
    return n < 0 || age < 0 ? 0 : C.HEAT_E * Math.exp(-age / C.HEAT_TAU_T);
  }
  const kp = F.kick(F.tH - (k / C.HEAT_N) * C.HEAT_BEATS * F.unit);
  return Math.min(1, Math.max(kp > C.KICK_MIN ? kp : 0, igniteAt(F, barbIgnite(F), k), C.PULSE_FLASH * barbPulseCore(F)));
};
/** Бік спини: −1 — зовнішній бік орбіти на −n (м'яч іде проти годинникової в полі — кут росте), +1 — на +n; за T × H (на стрибку не перескакує). */
export const barbSide = (F) => { const T = F.pts[0], H = F.pts[F.M - 1]; return T[0] * H[1] - T[1] * H[0] > 1e-9 ? -1 : 1; };

/** Крок ключових поз полум'я, с: рівень — FLICK_BEATS долі (вісімка), не частіше FLICKER_HZ; ∞ — 1 / FLICK_HZ_E. */
export const barbFlickT = (F) => (isLevel(F) ? Math.max(BARB.FLICK_BEATS * F.unit, 1 / FLICKER_HZ) : 1 / BARB.FLICK_HZ_E);
/** Поза кроку n (сіль — язик): множник у [lo, hi] від hash01 сітки — стала «випадковість», без rng. */
const flickPose = (n, salt, [lo, hi]) => lo + (hi - lo) * hash01(n * SEED_G + salt * SEED_K);
/** Плавна поза між ключовими: smoothstep від пози кроку n до n + 1 (неперервна за значенням). */
const flickAt = (x, salt, rng) => { const n = Math.floor(x + EPS_T), fr = clamp01(x - n); return flickPose(n, salt, rng) + (flickPose(n + 1, salt, rng) - flickPose(n, salt, rng)) * smooth01(fr); };
/**
 * [18.11.2026, власник: «кінчик — гарна анімація вогню»] Живе полум'я: довжина язика — сума трьох плавних хвиль (FIRE_HZ, кожна нижча за
 * FLICKER_HZ — правило студії «форма не частіше 6 Гц»), фази — свої на язик (сіль), у [lo, hi] того самого діапазону, що й пози FLICK; разом із
 * хвилею бічних язиків (ph) це «дихання» вогню без стрибків поз. t — с годинника (F.tH).
 */
const fireAt = (t, salt, [lo, hi]) => {
  const W = BARB.FIRE_HZ, A = BARB.FIRE_A, p = 6.2832 * hash01(salt * 97.13 + 0.5);
  const v = A[0] * Math.sin(6.2832 * W[0] * t + p) + A[1] * Math.sin(6.2832 * W[1] * t + 2.1 * p + 1.3) + A[2] * Math.sin(6.2832 * W[2] * t + 3.7 * p + 0.4);
  return lo + (hi - lo) * (0.5 + 0.5 * v / (A[0] + A[1] + A[2]));
};
/**
 * Спалах долі 0…1 (форма полум'я): рівень — exp(−вік / FLASH_TAU_BEATS долі) від кожної долі; ∞ — від кожного WHIP_T (FLASH_T с). «Менше руху» — 0.
 */
export const barbBeatFlash = (F) => {
  const C = BARB;
  if (F.reduced) return 0;
  const LV = isLevel(F), step = LV ? F.unit : C.WHIP_T, x = (F.tH - F.t0) / step, n = Math.floor(x + EPS_T), age = Math.max(0, F.tH - (F.t0 + n * step));
  return n < 0 ? 0 : Math.exp(-age / (LV ? C.FLASH_TAU_BEATS * F.unit : C.FLASH_T));
};
/**
 * Полум'я кадру (чиста функція F): { k — множник довжини головного язика, kL / kR — бічних, jL / jR — лизків, w — множник півширини основи, ph — фаза бічної хвилі
 * (у періодах), flash — спалах долі 0…1, core — біле ядро PULSE 0…1, kd — дроп }. Довжина: поза мерехтіння (FLICK, плавно між вісімками) × доля
 * (×BEAT_LEN) × дроп (×DROP_LEN) × max(PULSE ×PULSE_LEN, запалення ×IGN_LEN — коли вогонь добіг до кінчика); стелю LEN тримає збірка.
 * «Менше руху» — застигла поза STILL_K, фаза STILL_PH, без спалахів (світло ядра PULSE лишається).
 */
export const barbFlame = (F) => {
  const C = BARB, core = barbPulseCore(F);
  if (F.reduced) {
    const s = C.STILL_K;
    return { k: s, kL: s * C.SIDE_K, kR: s * C.SIDE_K * 0.9, jL: s * C.LICK_K, jR: s * C.LICK_K * 0.85, w: 1, ph: C.STILL_PH, flash: 0, core, kd: 0 };
  }
  const x = (F.tH - F.t0) / barbFlickT(F), flash = barbBeatFlash(F), kd = barbDrop(F), snap = barbSnap(F);
  const ig = isLevel(F) ? igniteAt(F, barbIgnite(F), C.HEAT_N) : 0;
  // дроп — рівний рев: ×DROP_LEN замість ляску долі (мерехтіння на вісімках лишається); ляск долі — поза дропом
  const beat = flash * (1 - kd), boost = (1 + (C.BEAT_LEN - 1) * beat) * (1 + (C.DROP_LEN - 1) * kd) * Math.max(1 + (C.PULSE_LEN - 1) * snap, 1 + (C.IGN_LEN - 1) * ig);
  return {
    k: fireAt(F.tH, 0.11, C.FLICK) * boost, kL: C.SIDE_K * fireAt(F.tH, 0.53, C.FLICK_SIDE) * boost, kR: C.SIDE_K * fireAt(F.tH, 0.97, C.FLICK_SIDE) * boost,
    jL: C.LICK_K * fireAt(F.tH, 0.31, C.FLICK_SIDE) * boost, jR: C.LICK_K * fireAt(F.tH, 0.77, C.FLICK_SIDE) * boost,
    w: Math.max(1 + (C.BEAT_W - 1) * beat, 1 + (C.PULSE_W - 1) * snap), ph: x * C.WAVE_RATE, flash: Math.max(beat, snap, ig), core, kd,
  };
};
/**
 * Довжина язика (од.) із сирої raw = FLAME_LF·Lb·k у місці fl до кінчика: до FLAME_KNEE·fl — як є, далі м'яке коліно tanh до fl (ніколи не за LEN; PULSE
 * усе одно довший за рев дропу — без пласкої стелі).
 */
export const barbFlameLen = (raw, fl) => {
  const kn = BARB.FLAME_KNEE * fl, room = fl - kn;
  return raw <= kn ? Math.max(0, raw) : room > 0 ? kn + room * Math.tanh((raw - kn) / room) : kn;
};
/**
 * Півширина язика на s ∈ [0, 1] від основи: w0·(NECK_K + (1 − NECK_K)·sin(½π·x))·(1 + BUMP·sin(π·x))·(1 − s)^FLAME_EXP, x = min(1, s/BUMP_S) —
 * крапля: вужча шийка в чаші леза, пузо на BUMP_S, гострий кінчик (HELL2 §6.3 + шийка NECK_K: без неї основа — блідий брусок у чаші).
 */
export const barbTongueW = (w0, s, bump = BARB.BUMP, neck = BARB.NECK_K) => {
  const C = BARB, x = Math.min(1, s / C.BUMP_S);
  return w0 * (neck + (1 - neck) * Math.sin(0.5 * Math.PI * x)) * (1 + bump * Math.sin(Math.PI * x)) * Math.pow(Math.max(0, 1 - s), C.FLAME_EXP);
};

// ================================================================ геометрія

/** Частка малого вигляду прев'ю (0…1, smoothstep): S.sm ≤ SM_LO — 1, ≥ SM_HI — 0; у грі (S.sm / S.pm = 1) — 0. */
export const barbSmall = (S) => (S.sm / S.pm > 1 + 1e-9 ? smooth01((BARB.SM_HI - S.sm) / (BARB.SM_HI - BARB.SM_LO)) : 0);
/**
 * Розкладка предмета на кадрі (довжини — од. кадру від ГОЛОВИ, d = len − s): us — множник довжин уздовж (прев'ю — S.sm / S.pm), bk — лезо (прев'ю
 * ×PREV_BARB, малий ×SM_BARB). → { Lb — намальована довжина, k — стиск, bl — лезо, dN — шийка (u 0.56), dF — основа полум'я (u ≈ 0.625), fl — місце
 * полум'я до кінчика (Lb − dF) }. Лезо кінчається на u 1 − FLAME_U + BLADE_OVER (0.66; роги чаші — трохи далі).
 */
export const barbGeom = (F, us = 1, bk = 1) => {
  const C = BARB, full = C.LEN * us, Lb = Math.max(0, Math.min(F.len, full)), k = full > 0 ? Lb / full : 0;
  const bl = Math.min(C.BLADE_U * Lb * bk, C.BARB_MAXF * Lb), dE = Lb * (1 - C.FLAME_U + C.BLADE_OVER), dN = Math.max(0, dE - bl);
  const dF = dN + C.BLADE_WIDE * bl, fl = Math.max(0, Lb - dF);
  return { Lb, k, bl, dN, dF, fl, us };
};
/** Півширина тіла на d від голови (од. кадру, без прев'ю): W_ROOT → W_NECK до шийки (^W_EXP), далі — W_NECK. */
export const barbHalfW = (G, d) => {
  const C = BARB, x = G.dN > 0 ? clamp01(d / G.dN) : 1;
  return C.W_NECK + (C.W_ROOT - C.W_NECK) * Math.pow(1 - x, C.W_EXP);
};
/** Знайти коліно стрибка в межах предмета: { d, sgn, age } або null (поворот дотичної ≥ KNEE_DEG на KNEE_S од.; вікно KNEE_WIN с). */
export const barbKnee = (F, Lb) => {
  const C = BARB;
  if (F.reduced || !(Lb > C.KNEE_S)) return null;
  let best = null, bestA = C.KNEE_DEG * D2R;
  for (let d = 0; d + C.KNEE_S <= Lb + 1e-9; d += C.KNEE_STEP) {
    const a = F.atS(F.len - d), b = F.atS(F.len - d - C.KNEE_S);
    const turn = Math.atan2(b.nx * a.ny - b.ny * a.nx, b.nx * a.nx + b.ny * a.ny);   // від старішої (b) до новішої (a): > 0 — поворот уліво (+n)
    if (Math.abs(turn) >= bestA) { bestA = Math.abs(turn); best = { d: d + C.KNEE_S / 2, sgn: Math.sign(turn), t: F.atS(F.len - d - C.KNEE_S / 2).t }; }
  }
  if (!best) return null;
  const age = F.tH - best.t;
  return age >= 0 && age < C.KNEE_WIN ? { d: best.d, sgn: best.sgn, age } : null;
};
/** Пружина коліна: e(a) = exp(−a/τ)·sin(ω·a), ω·τ так, щоб другий півперіод був −KNEE_OVER від першого; нормовано до піку 1. */
const KNEE_W = Math.PI / (BARB.KNEE_TAU * -Math.log(BARB.KNEE_OVER));   // ω: exp(−π/(ω·τ)) = KNEE_OVER
const KNEE_PEAK = (() => { const t = Math.atan(KNEE_W * BARB.KNEE_TAU) / KNEE_W; return Math.exp(-t / BARB.KNEE_TAU) * Math.sin(KNEE_W * t); })();
export const barbKneeSpring = (a) => (a < 0 ? 0 : (Math.exp(-a / BARB.KNEE_TAU) * Math.sin(KNEE_W * a)) / KNEE_PEAK);
/**
 * Бічний зсув осі хвоста (од. кадру, по +n шляху) на d від голови: S-хвиля A·u^SWAY_EXP·sin 2π(φ − WAVE_K·u), u = d / Lb (0 — корінь, 1 — вістря),
 * A = SWAY_MAX·(1 + (PULSE_AMP − 1)·snap) + коліно (за ним — у бік повороту, пружина); не далі ENV_SAFE − півширини тут (wHere).
 */
const swayAt = (K, d, wHere) => {
  const C = BARB, u = K.Lb > 0 ? clamp01(d / K.Lb) : 0;
  let o = K.amp * Math.pow(u, C.SWAY_EXP) * Math.sin(TAU * (K.ph - C.WAVE_K * u));
  if (K.knee && d > K.knee.d) o += K.knee.sgn * C.KNEE_AMP * K.kneeE * clamp01((d - K.knee.d) / C.KNEE_RAMP);
  const lim = Math.max(0, C.ENV_SAFE - wHere);
  return Math.max(-lim, Math.min(lim, o));
};
/** Кадр руху (без прев'ю): { Lb, ph, amp, knee, kneeE }. */
const swayFrame = (F, Lb) => {
  const C = BARB, snap = barbSnap(F), knee = barbKnee(F, Lb);
  return { Lb, ph: barbPhase(F), amp: C.SWAY_MAX * (1 + (C.PULSE_AMP - 1) * snap), knee, kneeE: knee ? barbKneeSpring(knee.age) : 0 };
};
/** Бічний зсув осі в дузі s від хвоста шляху (кадр гри, us = 1): 0 поза предметом (тест і порт). */
export const barbSway = (F, s) => {
  if (!F.ok || !(F.len > 0)) return 0;
  const G = barbGeom(F), d = F.len - s;
  if (d < 0 || d > G.Lb) return 0;
  return swayAt(swayFrame(F, G.Lb), d, d <= G.dN ? barbHalfW(G, d) : BARB.W_BARB);
};

/**
 * Жарини кадру (лише гра: не прев'ю, не «менше руху»): ≤ SPARK_N; sTip — дуга кінчика полум'я (звідси зриваються), sd — бік «назовні від центру
 * орбіти» (barbSide; 0 — по черзі). ПОДІЇ (першість, найсвіжіші першими): рівень — PULSE (PULSE_SPARKS), запалення (IGN_SPARKS), кожна доля дропу
 * (DROP_SPARKS); ∞ — удар жару HEAT_T (HEAT_SPARKS); летять з полум'я назад і на обидва боки. СПОКІЙ (решта стелі): REST_N на кожну REST_BEATS долі
 * (∞ — REST_T), з кінчика назад REST_BACK і назовні REST_OUT. Іскра j джерела g: зерно hash01(g·G + j·K + сіль); q — вік / життя.
 * → [{ s, v, r, a, mix, heat, src }] (v — по +n; heat — місце на рампі barbLavaHue: молода — лава, стара — жар; src — pulse · ign · drop · heat · rest).
 */
export const barbSparks = (F, sTip, sd = 0) => {
  const C = BARB, out = [];
  if (F.reduced) return out;
  const LV = isLevel(F), life = LV ? C.SPARK_LIFE_BEATS * F.unit : C.SPARK_LIFE_T, src = [];
  // місце іскри події на частці життя q (дуга s, бік v) — те саме й для «звідки летить» (q − SPARK_TRAIL_Q: краплина обідка витягнута вздовж руху)
  const evS = (back, spread, q) => sTip - back * spread * (0.25 + 0.75 * q);
  const evV = (side, out_, spread, q) => side * out_ * spread * Math.pow(0.3 + 0.7 * q, 0.8);
  if (LV) {
    const p = lastPulse(F);
    if (p && F.tH - p.t >= 0 && F.tH - p.t < life) src.push({ t: p.t, n: C.PULSE_SPARKS, g: p.i, salt: 0.37, spread: 1.25, src: "pulse" });
    const ig = barbIgnite(F);
    if (ig && ig.age < life) src.push({ t: F.tH - ig.age, n: C.IGN_SPARKS, g: ig.n, salt: 0.71, spread: 1.1, src: "ign" });
    const n = Math.floor((F.tH - F.t0) / F.unit + EPS_T), tb = F.t0 + n * F.unit;
    if (F.tH - tb < life && levelMid(F, n, F.unit) >= C.DROP_LEVEL && !(ig && ig.age < life)) src.push({ t: tb, n: C.DROP_SPARKS, g: n, salt: 0.13, spread: 0.8, src: "drop" });
  } else {
    const n = Math.floor(F.tH / C.HEAT_T + EPS_T), tb = n * C.HEAT_T;
    if (n >= 0 && F.tH - tb < life) src.push({ t: tb, n: C.HEAT_SPARKS, g: n, salt: 0.53, spread: 0.85, src: "heat" });
  }
  src.sort((a, b) => b.t - a.t);
  for (const e of src) {
    const q = clamp01((F.tH - e.t) / life);
    for (let j = 0; j < e.n && out.length < C.SPARK_N; j++) {
      const seed = e.g * SEED_G + j * SEED_K + e.salt, h1 = hash01(seed), h2 = hash01(seed + 7), h3 = hash01(seed + 13);
      const back = C.SPARK_BACK[0] + (C.SPARK_BACK[1] - C.SPARK_BACK[0]) * h1, s = evS(back, e.spread, q);
      if (s < 0) continue;
      const side = j % 2 === 0 ? 1 : -1, ou = C.SPARK_OUT[0] + (C.SPARK_OUT[1] - C.SPARK_OUT[0]) * h2, v = evV(side, ou, e.spread, q);
      const r = (C.SPARK_R[0] + (C.SPARK_R[1] - C.SPARK_R[0]) * h3) * (1 - 0.45 * q), a = C.SPARK_A * (1 - q * q);
      if (a < 0.02) continue;
      const qp = Math.max(0, q - C.SPARK_TRAIL_Q);
      out.push({ s, v, r, a, mix: C.SPARK_MIX[0] + (C.SPARK_MIX[1] - C.SPARK_MIX[0]) * (1 - q), heat: (j % 3 === 0 ? C.SPARK_HEAT[0] : C.SPARK_HEAT[1]) * (1 - q), src: e.src,
        ps: Math.max(0, evS(back, e.spread, qp)), pv: evV(side, ou, e.spread, qp) });
    }
  }
  // спокій: безперервно з кінчика (сітка REST_BEATS долі / REST_T с; найсвіжіші першими), у залишок стелі
  const step = LV ? C.REST_BEATS * F.unit : C.REST_T, rl = LV ? C.REST_LIFE_BEATS * F.unit : C.REST_LIFE_T, t0 = LV ? F.t0 : 0;
  for (let g = Math.floor((F.tH - t0) / step + EPS_T); out.length < C.SPARK_N; g--) {
    const tb = t0 + g * step, q = (F.tH - tb) / rl;
    if (q >= 1 || (!LV && g < 0)) break;
    if (q < 0) continue;
    for (let j = 0; j < C.REST_N && out.length < C.SPARK_N; j++) {
      const seed = g * SEED_G + j * SEED_K + 0.29, h1 = hash01(seed), h2 = hash01(seed + 7), h3 = hash01(seed + 13);
      // короткий шлях (хвіст стиснутий до шляху) — жарина лишається біля його кінця, летить лише назовні (не зникає)
      const back = C.REST_BACK[0] + (C.REST_BACK[1] - C.REST_BACK[0]) * h1, rs = (x) => Math.max(0, sTip - back * (0.12 + 0.88 * x));
      const side = sd !== 0 ? (h3 < 0.8 ? sd : -sd) : (g + j) % 2 === 0 ? 1 : -1, ou = C.REST_OUT[0] + (C.REST_OUT[1] - C.REST_OUT[0]) * h2;
      const rv = (x) => side * ou * Math.pow(x, 0.85), s = rs(q), v = rv(q);
      const r = (C.REST_R[0] + (C.REST_R[1] - C.REST_R[0]) * hash01(seed + 19)) * (1 - 0.5 * q), a = C.SPARK_A * Math.min(1, q / C.REST_IN) * (1 - q * q);
      if (a < 0.02) continue;
      const qp = Math.max(0, q - C.REST_TRAIL_Q);
      out.push({ s, v, r, a, mix: C.SPARK_MIX[0] + (C.SPARK_MIX[1] - C.SPARK_MIX[0]) * 0.5 * (1 - q), heat: C.REST_HEAT * (1 - q), src: "rest", ps: rs(qp), pv: rv(qp) });
    }
  }
  return out;
};

// ================================================================ збірка примітивів

const hexRgb = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const rgbHex = (v) => "#" + v.map((c) => Math.round(c).toString(16).padStart(2, "0")).join("");
/** Таблиця рампи: барви hexes у точках xs (0…1) на n кроків (#rrggbb; раз на модуль). Порт — 1D-таблиця. */
const rampLut = (hexes, xs, n) => {
  const P = hexes.map(hexRgb), out = [];
  for (let i = 0; i < n; i++) {
    const x = i / (n - 1);
    let j = 0;
    while (j < xs.length - 2 && x > xs[j + 1]) j++;
    const t = clamp01((x - xs[j]) / (xs[j + 1] - xs[j]));
    out.push(rgbHex(P[j].map((v, c) => v + (P[j + 1][c] - v) * t)));
  }
  return out;
};
/** Вибір з таблиці: індекс floor(x·(n − 1) + ½) — порт НЕ kotlin.math.round. */
const pick = (L, x) => L[Math.floor(clamp01(x) * (L.length - 1) + 0.5)];
const LUT_N = 16;
let FLASH_LUT = null, LAVA_LUT = null, SUB_LUT = null, CORE_LUT = null;
/** Кров тіла за x ∈ [0, 1]: тінь SHADOW → лице FACE → край EDGE → розжарена HEATED у точках RAMP. */
export const barbFlashHue = (x) => pick(FLASH_LUT || (FLASH_LUT = rampLut([BARB.SHADOW, BARB.FACE, BARB.EDGE, BARB.HEATED], BARB.RAMP, LUT_N)), x);
/** Жар за x ∈ [0, 1]: жар EMBER (0) → лава MOLTEN (½) → горно CORE (1) — жарини, кант. */
export const barbLavaHue = (x) => pick(LAVA_LUT || (LAVA_LUT = rampLut([BARB.EMBER, BARB.MOLTEN, BARB.CORE], [0, 0.5, 1], LUT_N)), x);
/** Підшкірний жар за x ∈ [0, 1]: [480 INFERNO] [480] гаряча лава SUB_LO (0) → розплав SUB_MID (1) — темна лава (сусідні стопи з різним жаром — не ближче 90 до шипа) */
export const barbSubHue = (x) => pick(SUB_LUT || (SUB_LUT = rampLut([BARB.SUB_LO, BARB.SUB_MID], [0, 1], LUT_N)), x);
/** Розплавлене ядро за x ∈ [0, 1]: жар-вугілля CHAR (0) → жар EMBER (⅓) → лава MOLTEN (⅔) → горно CORE (1). */
export const barbCoreHue = (x) => pick(CORE_LUT || (CORE_LUT = rampLut([BARB.CHAR, BARB.EMBER, BARB.MOLTEN, BARB.CORE], [0, 1 / 3, 2 / 3, 1], LUT_N)), x);
/** Подвійна площа многокутника (shoelace); > 0 — додатний обхід. */
const area2 = (poly) => { let a = 0; for (let i = 0; i < poly.length; i++) { const p = poly[i], q = poly[(i + 1) % poly.length]; a += p[0] * q[1] - q[0] * p[1]; } return a; };
/** Підшлях ореолу — додатного обходу (одне заливання nonzero не вирізає перекриття). */
const posPoly = (poly) => (area2(poly) < 0 ? poly.slice().reverse() : poly);

const REQ = ["chordGrad", "chordU", "empty", "TRAIL_RULES", "TRAIL_MODE"];
/**
 * Фабрика форми: trails.mjs передає СВОЇ приватні помічники (циклу імпорту немає) — H = { ribbon, gradE, glowOf, empty, chordGrad, chordU, pointGrad,
 * LOOK_E, headBallOf, h01, TRAIL_RULES, TRAIL_MODE, TRAIL_STYLE_E }; BARB бере chordGrad, chordU, empty, TRAIL_RULES, TRAIL_MODE (решта — про запас
 * порту). Повертає makeBarbE(F, S) → { glow, fills, strokes, rings, dots, opaque? } у форматі шапки trails.mjs.
 * Шари: ореол (lighter) → [0] тіло + лезо (кров, обсидіан) → [1] підшкірний жар + 5 язиків зовнішнього полум'я → [2] ядро + внутрішній язик (inner) →
 * штрихи: контур · відблиск лави · комір жару барба · лінія ядра полум'я → горно → жарини.
 */
export function barbMaker(H) {
  for (const k of REQ) if (!H || H[k] == null) throw new Error(`barbMaker: помічник trails.mjs «${k}» не передано`);
  const R = H.TRAIL_RULES;
  if (R.FLICKER_HZ_MAX !== FLICKER_HZ || R.ENDLESS_ENV !== BARB.ENV || H.TRAIL_MODE.LEVEL !== MODE_LEVEL) throw new Error("barbMaker: дзеркала TRAIL_RULES / TRAIL_MODE розійшлися з trails.mjs");
  return function makeBarbE(F, S) {
    const C = BARB, out = H.empty();
    if (!F.ok || !(F.len > 0)) return out;
    const pm = S.pm, sm = S.sm, us = sm / pm, prev = us > 1 + 1e-9, small = barbSmall(S) >= 0.5, calm = F.reduced || !!S.still, LV = F.mode === H.TRAIL_MODE.LEVEL;
    const Fm = calm && !F.reduced ? Object.create(F, { reduced: { value: true } }) : F;   // знімок still — спокій (як «менше руху»), кадр не мутуємо
    const minW = prev ? C.MIN_U : 0, env = F.env;
    const kd = barbDrop(Fm), bk = small ? C.SM_BARB : prev ? C.PREV_BARB : 1;
    const G = barbGeom(F, us, bk);
    if (!(G.Lb > 0.5)) return out;
    const K = swayFrame(Fm, G.Lb), click = barbClick(Fm), core = barbPulseCore(F), ig = LV ? barbIgnite(F) : null, sd = barbSide(F), fm = barbFlame(Fm);
    // прев'ю: лезо довше ×PREV_BARB, але ширше лише ×PREV_BLADE_W (герой полиці — полум'я, ×PREV_FLAME_W завширшки)
    const wk = small ? C.SM_W : 1, W = C.W_BARB * (prev && !small ? C.PREV_BLADE_W : bk), lk = G.Lb / C.LEN, fw = prev && !small ? C.PREV_FLAME_W : 1;
    const wAt = (d) => Math.max(minW / sm, barbHalfW(G, d) * wk);
    // точка на d від голови з бічним v (од. кадру, по +n): позиція ×pm, усе поперек (зсув осі + v) ×sm; lim — стеля |зсув + v| (конверт)
    // зсув осі на d — стеля ENV_SAFE − півширини тіла тут (за шийкою — леза): уся смуга / лезо / язик зсувається цілим, без перекосу
    const offAt = (d) => swayAt(K, d, d <= G.dN ? barbHalfW(G, d) * wk : W);
    const P = (d, v, lim = C.ENV_SAFE) => {
      const p = F.atS(F.len - Math.min(Math.max(d, 0), F.len)), o = Math.max(-lim, Math.min(lim, offAt(d) + v));
      return [p.x * pm + p.nx * o * sm, p.y * pm + p.ny * o * sm];
    };
    // частка хорди — за віссю шляху (стопи градієнта; хорда хвіст → голова)
    const U = (d) => { const p = F.atS(F.len - Math.min(Math.max(d, 0), F.len)); return H.chordU(F, p.x, p.y); };
    // монотонні стопи (хорда хвіст → голова: d росте — u спадає)
    const mono = (stops) => { stops.sort((a, b) => a[0] - b[0]); for (let i = 1; i < stops.length; i++) if (stops[i][0] < stops[i - 1][0]) stops[i][0] = stops[i - 1][0]; return stops; };
    const two = (st) => (st.length >= 2 ? st : [...st, ...st]);
    /** Смуга [L0 … Ln, Rn … R0] від d0 до d1 (n відрізків), півширина hw(d), центр — вісь. */
    const ribbon = (d0, d1, n, hw, lim = C.ENV_SAFE) => {
      const Lp = [], Rp = [];
      for (let i = 0; i <= n; i++) { const d = d0 + ((d1 - d0) * i) / n, w = hw(d); Lp.push(P(d, w, lim)); Rp.push(P(d, -w, lim)); }
      return [...Lp, ...Rp.reverse()];
    };
    const heat = [];
    for (let k = 0; k < C.HEAT_N; k++) heat.push(barbHeat(F, k));
    /** Жар у точці тіла d (лінійно між точками ядра). */
    const heatAt = (d) => { const x = clamp01(d / Math.max(1e-6, G.dN)) * (C.HEAT_N - 1), i = Math.min(C.HEAT_N - 2, Math.floor(x)); return heat[i] + (heat[i + 1] - heat[i]) * (x - i); };
    const flashAt = (d) => Math.max(ig ? igniteAt(F, ig, clamp01(d / Math.max(1e-6, G.dN)) * (C.HEAT_N - 1)) : 0, C.PULSE_FLASH * core);
    const flashAll = Math.max(click.flash, core, ig ? igniteAt(F, ig, C.HEAT_N - 1) : 0, 0.5 * fm.flash);
    const ballOf = (d) => C.HEAD_BALL * (1 - smooth01(d / Math.max(1e-6, C.HEAD_LEN * G.Lb)));

    // ── лезо (локально: a — від шийки до краю чаші, v — поперек; поворот клацання довкола шийки; кожна вершина — на своїй дузі, з хвилею)
    const bl = G.bl, wn = barbHalfW(G, G.dN) * wk * C.BARB_NECK, ca = Math.cos(click.ang), sa = Math.sin(click.ang);
    const bladeLocal = (gm) => {
      const side = [[0, wn / W], ...C.BARB_SHAPE];
      const half = side.map(([a, v]) => [a * bl * (gm > 1 ? 1.08 : 1), v * W * gm]);
      return [...half, ...half.slice().reverse().map(([a, v]) => [a, -v])];   // 12 вершин: шийка+ … край+, край− … шийка−
    };
    const bladeAt = ([a, v], lim) => { const a2 = a * ca - v * sa, v2 = a * sa + v * ca; return P(G.dN + a2, v2, lim); };
    const bladePoly = (gm, lim) => bladeLocal(gm).map((x) => bladeAt(x, lim));
    // кант леза: та сама форма ×KANT_GM, центр — середина леза (вужча лінія всередині контуру)
    const bladeInset = (lim) => bladeLocal(1).map(([a, v]) => bladeAt([0.5 * bl + (a - 0.5 * bl) * C.KANT_GM, v * C.KANT_GM], lim));

    // ── полум'я: язики від основи dF (у чаші леза, з поворотом клацання vb) назад по шляху; не довші за залишок шляху fl.
    //    Малий вигляд — краплина лави від шийки до кінчика (замість леза й язиків)
    const vb = C.BLADE_WIDE * bl * sa, d0 = small ? G.dN - C.FLAME_IN * lk : G.dF - C.FLAME_IN * lk, inL = C.FLAME_IN * lk;
    const lenOf = (k) => barbFlameLen(C.FLAME_LF * G.Lb * k, G.fl), Lm = small ? G.Lb - G.dN : lenOf(fm.k);
    // прев'ю: поперек усе ×wMul, а вздовж хвіст стиснутий до шляху (k) — розхід язиків і хвилі ×max(PREV_LAT, k), інакше полум'я — «зірка»
    const lat = prev && !small ? Math.max(C.PREV_LAT, Math.min(1, G.k)) : 1;
    const wave = (s, amp, ph) => lat * amp * Math.min(1, s / C.WAVE_ANCHOR) * Math.sin(TAU * (s / C.WAVE_L - ph));
    /** Язик-смуга [L0 … L(m−1), R(m−1) … R0]: d = d0 + s·(len + inL), півширина barbTongueW, центр cen(s). */
    const tongue = (len, w0, bump, cen, lim = C.ENV_SAFE) => {
      const Lp = [], Rp = [], n = C.FLAME_SMP, Lt = len + inL;
      for (let i = 0; i < n; i++) {
        const s = i / (n - 1), d = d0 + s * Lt, w = barbTongueW(w0, s, bump), c = cen(s);
        Lp.push(P(d, c + w, lim)); Rp.push(P(d, c - w, lim));
      }
      return [...Lp, ...Rp.reverse()];
    };
    const base = (s) => vb * (1 - 0.5 * s);
    /** Розхід від спільного кореня: 0 в основі (язики виходять з однієї точки чаші — крапля, не пласка «цеглина»), 1 від FAN_S. */
    const fan = (s) => Math.pow(Math.min(1, s / C.FAN_S), 0.7);
    const cMain = (s) => base(s) + wave(s, C.WAVE_A, fm.ph);
    const cSide = (sg) => (s) => base(s) + sg * lat * (C.SIDE_OFF * fan(s) + C.SIDE_FAN * s + C.CURL * s * s) + wave(s, C.SIDE_WAVE, fm.ph + sg * C.SIDE_PH);
    const cLick = (sg) => (s) => base(s) + sg * lat * (C.LICK_OFF * fan(s) + C.LICK_FAN * s + C.LICK_CURL * s * s) + wave(s, C.LICK_WAVE, fm.ph + sg * C.LICK_PH);
    const fwl = fw * Math.max(lat, 0.6), w0 = C.FLAME_W * fm.w * fwl;   // прев'ю: язики вужчі разом із розходом (без пласких зрізів конверта)
    const flames = [];   // порядок: лизок +, лизок −, бічний +, бічний −, головний (головний — останній)
    const cSm = (s) => wave(s, C.SM_WAVE, fm.ph);   // [480] краплина малого вигляду хвилюється, як язик
    if (small) flames.push(tongue(Lm, C.SM_DROP_W * wk, C.SM_DROP_BUMP, cSm));
    else if (Lm > 0.2) {
      const jL = lenOf(fm.jL), jR = lenOf(fm.jR), kL = lenOf(fm.kL), kR = lenOf(fm.kR);
      if (jL > 0.2) flames.push(tongue(jL, C.LICK_W * fm.w * fwl, C.BUMP, cLick(1)));
      if (jR > 0.2) flames.push(tongue(jR, C.LICK_W * fm.w * fwl, C.BUMP, cLick(-1)));
      if (kL > 0.2) flames.push(tongue(kL, C.SIDE_W * fm.w * fwl, C.BUMP, cSide(1)));
      if (kR > 0.2) flames.push(tongue(kR, C.SIDE_W * fm.w * fwl, C.BUMP, cSide(-1)));
      flames.push(tongue(Lm, w0, C.BUMP, cMain));
    }

    // ── ореол: смуга тіла ×GLOW_MUL від кореня до шийки + лезо ×GLOW_MUL + полум'я ×FLAME_GLOW — HALO, α GLOW_A (доля, спалах, PULSE — більше); у конверті ENV
    const glowPaths = [];
    {
      const Lp = [], Rp = [], n = C.GLOW_N;
      for (let i = 0; i <= n; i++) {
        const d = (G.dN * i) / n, w = wAt(d), sw = offAt(d), gw = Math.max(0, Math.min(w * C.GLOW_MUL, env - 0.02 - Math.abs(sw)));
        Lp.push(P(d, gw, env - 0.02)); Rp.push(P(d, -gw, env - 0.02));
      }
      glowPaths.push(posPoly([...Lp, ...Rp.reverse()]));
    }
    if (!small && bl > 0.2) glowPaths.push(posPoly(bladePoly(C.GLOW_MUL, env - 0.02)));
    if (small) glowPaths.push(posPoly(tongue(Lm, C.SM_DROP_W * wk * C.GLOW_MUL, C.SM_DROP_BUMP, cSm, env - 0.02)));
    else if (Lm > 0.2) {
      glowPaths.push(posPoly(tongue(Lm, (w0 + C.SIDE_OFF) * C.FLAME_GLOW, C.BUMP, base, env - 0.02)));
      // [T1] жар під основою полум'я: лінза від BLOOM_BACK·bl перед основою до BLOOM_LEN·Lm, пік BLOOM_W (у конверті мінус зсув осі) на BLOOM_PEAK
      const bA = G.dF - C.BLOOM_BACK * bl, bZ = G.dF + C.BLOOM_LEN * Lm, Lp = [], Rp = [], n = C.BLOOM_N;
      for (let i = 0; i <= n; i++) {
        const x = i / n, d = bA + (bZ - bA) * x, k = x < C.BLOOM_PEAK ? x / C.BLOOM_PEAK : (1 - x) / (1 - C.BLOOM_PEAK);
        const c = base(clamp01((d - d0) / Math.max(1e-6, Lm + inL))), lim = env - 0.02, room = Math.max(0, lim - Math.abs(offAt(d) + c));
        const w = Math.min(room, C.BLOOM_W * lat * Math.pow(Math.sin(0.5 * Math.PI * clamp01(k)), C.BLOOM_EXP) + 0.3);
        Lp.push(P(d, c + w, lim)); Rp.push(P(d, c - w, lim));
      }
      glowPaths.push(posPoly([...Lp, ...Rp.reverse()]));
    }
    // жарини (лише гра): ядро — крапкою, прозорий обідок — штрихом-краплиною вздовж руху (нижче, після штрихів предмета)
    // [18.11, власник: «кінчик — гарна анімація вогню»] жарини спокою — і в прев'ю (картка рівня, SHOP): вогник живий, а не намальований
    const sparks = !calm && !small ? barbSparks(F, F.len - (G.dF + 0.85 * Lm), sd) : [];
    const swT = sparks.length ? swayAt(K, G.dF + 0.85 * Lm, 0) : 0;
    const sparkPt = (s, v, lim) => { const p = F.atS(Math.min(F.len, Math.max(0, s))), o = Math.max(-lim, Math.min(lim, v + swT)); return [(p.x + p.nx * o) * pm, (p.y + p.ny * o) * pm]; };
    const ga = Math.min(R.HEAD_A_MAX, C.GLOW_A * (1 + C.GLOW_BEAT * (LV ? F.beat : 0) + C.GLOW_FLASH * Math.max(click.flash, ig ? igniteAt(F, ig, C.HEAT_N - 1) : 0, 0.5 * fm.flash)) + C.GLOW_PULSE * core);
    if (small || !(Lm > 0.2)) out.glow = { paths: glowPaths, solid: { mixK: 0, alpha: ga, hue: C.HALO, ball: 0 } };
    else {
      // [T1] ореол — градієнтом ПОПЕРЕК осі полум'я (хорда основа → BLOOM_LEN·Lm, центр — основа з поворотом клацання): α спадає від осі до краю конверта —
      //   м'яке світло без різкого краю (одне заливання не складає α, тож м'якість дає лише градієнт). Вісь — жар BLOOM_CORE (спалах / PULSE — більше),
      //   середина — BLOOM_HUE, край — HALO α 0. Тіло біля барба лежить на осі (ореол як був), на крутій орбіті біля м'яча — тьмяніє.
      const fl = Math.max(fm.flash, core, click.flash), k = ga / C.GLOW_A, c0 = P(G.dF, vb), c1 = P(G.dF + C.BLOOM_LEN * Lm, base(C.BLOOM_LEN));
      let tx = c1[0] - c0[0], ty = c1[1] - c0[1], tl = Math.hypot(tx, ty);
      if (tl < 1e-6) { tx = 1; ty = 0; tl = 1; }
      const hw = env * pm, nx = -ty / tl, ny = tx / tl, am = (x) => Math.min(R.HEAD_A_MAX, x);
      const bA = am(C.BLOOM_A * Math.min(1.6, k) + C.BLOOM_FLASH * fl), st = [];
      for (const [o, a, hue] of C.BLOOM_STOPS) { const al = o === 0.5 ? bA : am(a * (bA / C.BLOOM_A)); st.push([o, 0, al, hue, 0]); if (o < 0.5) st.push([1 - o, 0, al, hue, 0]); }
      out.glow = { paths: glowPaths, grad: { a: [c0[0] - nx * hw, c0[1] - ny * hw], b: [c0[0] + nx * hw, c0[1] + ny * hw], stops: mono(st) } };
    }

    // ── [0] тіло (гладка звужена жила, від кореня трохи заходить під лезо) + лезо: кров від кореня (тінь, барва м'яча) до шийки (край);
    //     спалах (запалення, PULSE) тягне до розжареної; лезо — обсидіан, клацання й PULSE його розжарюють
    const body = [], bStops = [];
    const dB = small ? G.dN : Math.min(G.dN + 0.3 * bl, G.Lb);
    body.push(ribbon(0, dB, small ? 16 : C.BODY_N, wAt));
    for (let i = 0; i <= 8; i++) {
      const d = (G.dN * i) / 8, x0 = C.BODY_X[0] + (C.BODY_X[1] - C.BODY_X[0]) * (i / 8), fl = flashAt(d);
      bStops.push([U(d), 0, C.A, small ? barbFlashHue(0.45) : barbFlashHue(x0 + (1 - x0) * fl), small ? 0 : ballOf(d)]);
    }
    if (!small && bl > 0.2) {
      body.push(bladePoly(1, C.ENV_SAFE));
      const bf = Math.max(click.flash * 0.5, core, fm.flash * 0.3);
      // гачки — кров краю EDGE (читаються на темному тлі, контур і комір тримають силует), до чаші темніє в обсидіан — полум'я на ньому горить
      bStops.push([U(G.dN + 0.3 * bl), 0, C.A, barbFlashHue(C.RAMP[2] + (1 - C.RAMP[2]) * bf), 0], [U(G.dN + bl), 0, C.A, bf > 0.05 ? barbFlashHue(0.45 * bf) : C.OBSIDIAN, 0]);
    }
    out.fills.push({ paths: body, grad: H.chordGrad(F, S, mono(bStops)) });

    // ── [1] підшкірний жар (смуга SUB_K·W уздовж осі; на корені й шийці звужується) + 5 язиків зовнішнього полум'я
    const fire = [], fStops = [];
    if (!small) {
      const d1 = G.dN + 0.15 * bl, dA = C.SUB_FROM * G.Lb;
      fire.push(ribbon(dA, d1, 18, (d) => wAt(Math.min(d, G.dN)) * C.SUB_K * Math.min(1, (d - dA) / (0.12 * G.Lb) + 0.25) * (d > G.dN ? 0.7 : 1)));
      for (let i = 0; i <= C.HEAT_N; i++) {
        const d = dA + ((G.dN - dA) * i) / C.HEAT_N, x = C.SUB_BASE[0] + (C.SUB_BASE[1] - C.SUB_BASE[0]) * (i / C.HEAT_N);
        fStops.push([U(d), 0, C.SUB_A0 + (C.SUB_A - C.SUB_A0) * smooth01(i / 3), barbSubHue(x + C.SUB_HEAT * heatAt(d) + C.SUB_DROP * kd + 0.5 * flashAt(d)), 0]);
      }
    } else {
      // малий вигляд: жила жару по осі (вузька), лише SUB барви
      fire.push(ribbon(0.12 * G.dN, G.dN, 8, (d) => wAt(d) * 0.4));
      fStops.push([U(0.12 * G.dN), 0, C.A, barbSubHue(0.6), 0], [U(G.dN), 0, C.A, barbSubHue(1), 0]);
    }
    // [480 INFERNO] язики вздовж полум'я (власник 21.11: «щоб не було палочки — увесь хвіст був вогнем») — у тому самому заливанні (барви —
    //   стопи полум'я на своїй дузі): корінь — на осі головного язика (cMain), на BL_ROOT його півширини; малий вигляд — без них
    if (!small && Lm > 0.2) for (let i = 0; i < C.BL_N; i++) {
      const sg = i % 2 === 0 ? sd : -sd, dS = G.dF + Lm * (C.BL_U0 + ((C.BL_U1 - C.BL_U0) * i) / (C.BL_N - 1));
      const kb = Fm.reduced ? C.STILL_K : fireAt(F.tH, C.BL_SALT0 + C.BL_SALT_STEP * i, C.FLICK_SIDE) * (1 + C.BL_FLASH * fm.flash);
      const len = C.BL_LEN * lk * kb, n = C.BL_SMP, Lp = [], Rp = [];
      for (let j = 0; j < n; j++) {
        const s = j / (n - 1), d = dS + s * len, w = barbTongueW(C.BL_W * fwl, s, C.BUMP);
        const sm_ = clamp01((d - d0) / Math.max(1e-6, Lm + inL));
        const c = cMain(sm_) + sg * (barbTongueW(w0, sm_) * C.BL_ROOT + lat * (C.BL_OUT * s + C.BL_CURL * s * s)) + wave(s, C.BL_WAVE, fm.ph + 0.5 * i);
        Lp.push(P(d, c + w)); Rp.push(P(d, c - w));
      }
      fire.push([...Lp, ...Rp.reverse()]);
    }
    // [480] лавова чаша довкола спини м'яча (малий вигляд — без неї); перед язиками кінчика (порядок підшляхів: смуга · язики тіла · чаша · 5 язиків).
    //   Коло м'яча: центр — голова (×pm), назад — від голови до точки шляху CUP_BACK од. позаду, радіус rB = BALL_R·zs·bm·pm (м'яч на екрані);
    //   поперек — не далі (env − CUP_LIM)·sm від осі: зовнішній радіус стискається, щоб чаша лишалась у конверті
    //   Точка (ρ, θ) кола м'яча — через вісь шляху P: уздовж d = ρ·cos θ / pm, поперек v = ρ·sin θ / sm (ρ — px екрана), тож на вигині чаша йде за віссю
    //   й не виходить за конверт (lim — у тих самих од., що P)
    const cupPoly = (kIn, kOut) => {
      const Lp = [], Rp = [], n = C.CUP_N, rB = C.BALL_R * (F.zs || 1) * (S.bm || 1) * pm, lim = Math.max(0, env - C.CUP_LIM);
      for (let i = 0; i <= n; i++) {
        const th = (-1 + (2 * i) / n) * C.CUP_DEG * D2R, sn = Math.sin(th), cs = Math.cos(th), as = Math.abs(sn);
        const ro = as > 1e-6 ? Math.min(kOut * rB, (lim * sm) / as) : kOut * rB, ri = Math.min(kIn * rB, ro);
        Lp.push(P((ro * cs) / pm, (ro * sn) / sm, lim)); Rp.push(P((ri * cs) / pm, (ri * sn) / sm, lim));
      }
      return [...Lp, ...Rp.reverse()];
    };
    if (!small) fire.push(cupPoly(C.CUP_IN, C.CUP_R));
    for (const f of flames) fire.push(f);
    // міст жару між смугою й основою полум'я (у лезі, під чашею): жар-вугілля CHAR — інакше кров-жар → жар проходить ближче 90 до шипа
    if (!small && flames.length) fStops.push([U(G.dN + 0.35 * bl), 0, C.SUB_A, C.CHAR, 0]);
    if (small) for (const [f, hue, a] of C.SM_DROP_STOPS) fStops.push([U(G.dN + f * (G.Lb - G.dN)), f === 0 ? 0.12 : 0, a, hue, 0]);
    else if (flames.length) {
      const mixBase = Math.min(R.MIX_MAX, C.FLAME_MIX * fm.flash + R.MIX_MAX * core);
      for (const [f, hue, a] of C.FLAME_STOPS) fStops.push([U(G.dF + f * Lm), f === 0 ? mixBase : 0, Math.min(R.HEAD_A_MAX, a), hue, 0]);
    }
    out.fills.push({ paths: fire, grad: H.chordGrad(F, S, mono(two(fStops))) });

    // ── [2] ядро (inner): стрічка CORE_K·W від CORE_FROM крізь лезо до основи полум'я + внутрішній язик (горно → лава → жар → прозоро)
    const coreP = [], cStops = [];
    {
      const dA = C.CORE_FROM * G.Lb, dZ = small ? G.dN : Math.min(G.dF + 0.5 * inL, G.Lb);
      coreP.push(ribbon(dA, dZ, 20, (d) => (d <= G.dN ? wAt(d) * C.CORE_K * Math.min(1, (d - dA) / (0.1 * G.Lb) + 0.3) : wn * C.CORE_K * 1.1)));
      const mx = Math.min(R.MIX_MAX, 0.45 * core);
      for (let i = 0; i <= C.HEAT_N; i++) {
        const d = dA + ((G.dN - dA) * i) / C.HEAT_N, x = C.CORE_BASE[0] + (C.CORE_BASE[1] - C.CORE_BASE[0]) * (i / C.HEAT_N);
        cStops.push([U(d), mx, C.CORE_A0 + (C.CORE_A - C.CORE_A0) * smooth01(i / 3), barbCoreHue(x + C.CORE_HEAT * heatAt(d) + C.CORE_DROP * kd + flashAt(d)), 0]);
      }
      if (!small) cStops.push([U(G.dF), mx, C.CORE_A, barbCoreHue(0.85 + 0.15 * Math.max(fm.flash, core)), 0]);
    }
    if (!small && Lm > 0.2) {
      const li = Lm * C.INNER_K, wi = w0 * C.INNER_W;
      coreP.push(tongue(li, wi, C.BUMP, (s) => cMain((s * (li + inL)) / (Lm + inL))));
      const mixI = Math.min(R.MIX_MAX, 0.2 * fm.flash + R.MIX_MAX * core);
      for (const [f, hue, a] of C.INNER_STOPS) cStops.push([U(G.dF + f * li), f < 0.5 ? mixI : 0, a, hue, 0]);
    }
    out.fills.push({ paths: coreP, grad: H.chordGrad(F, S, mono(two(cStops))), inner: true });

    // ── штрихи (4): контур (обидва краї тіла + зовнішні краї леза, без чаші), відблиск лави, комір жару барба, лінія ядра полум'я
    {
      const oW = Math.max(minW, C.OUTLINE_W * sm), oLim = Math.max(0, Math.min(C.ENV_SAFE, env - oW / 2 / sm - 0.02)), n = C.BODY_N, ol = [];
      const edge = (sg) => { const e = []; for (let i = n; i >= 0; i--) { const d = (G.dN * i) / n; e.push(P(d, sg * wAt(d), oLim)); } return e; };
      if (!small && bl > 0.2) {
        const bp = bladeLocal(1).map((x) => bladeAt(x, oLim));   // 0 шийка+, 1 гачок+, 2 талія+, 3 пузо+, 4 ріг+, 5 чаша+, 6 чаша−, 7 ріг−, … 11 шийка−
        ol.push(bp[2], bp[1], bp[0], ...edge(1).slice(1), ...edge(-1).reverse().slice(0, -1), bp[11], bp[10], bp[9]);   // пузо, чашу й роги накриває полум'я
      } else if (!small) {
        // [480] кірка лави — зовнішній край чаші (замість контуру тіла: «палочки» немає)
        const cp = cupPoly(C.CUP_IN, C.CUP_R);
        out.strokes.push({ pts: cp.slice(0, C.CUP_N + 1), w: Math.max(minW, C.CRUST_W * sm), grad: H.chordGrad(F, S, [[0, 0, C.CRUST_A, C.SOOT, 0], [1, 0, C.CRUST_A, C.SOOT, 0]]) });
      } else ol.push(...edge(1), ...edge(-1).reverse());
      if (ol.length) out.strokes.push({ pts: ol, w: oW, grad: H.chordGrad(F, S, [[0, 0, C.OUTLINE_A, C.OUTLINE, 0], [1, 0, C.OUTLINE_A, C.OUTLINE, 0]]) });
    }
    if (small) {
      // малий вигляд: кант горна довкола краплини + тонкий відблиск уздовж жили (кількості — як у грі: 4 штрихи)
      const edge = flames[0].slice();
      edge.push(edge[0]);
      out.strokes.push({ pts: edge, w: Math.max(minW, C.SM_EDGE_W * sm), grad: H.chordGrad(F, S, [[0, 0, C.SM_EDGE_A, C.LAVA, 0], [1, 0, C.SM_EDGE_A, C.LAVA, 0]]), inner: true });   // [480] м'який край жару (було — кремовий кант горна: «листок»)
      const sp = [];
      for (let i = 0; i <= 8; i++) { const d = G.dN * (0.2 + (0.8 * i) / 8); sp.push(P(d, 0)); }
      out.strokes.push({ pts: sp, w: Math.max(minW, 0.35 * sm), grad: H.chordGrad(F, S, [[0, 0, 0.8, C.CHAR, 0], [1, 0, 0.8, C.CHAR, 0]]), inner: true });
    } else {
      const kh = barbLavaHue(0.05 + 0.35 * Math.max(flashAll, kd));
      // відблиск лави: бік до центру ями (−sd: орбіта довкола ями, лава світить звідти), всередині контуру на KANT_IN, від RIM_FROM (α 0) до шийки
      const rim = [], dR = C.RIM_FROM * G.Lb, ks = -sd;
      for (let i = 0; i <= 14; i++) { const d = dR + ((G.dN - dR) * i) / 14; rim.push(P(d, ks * Math.max(0, wAt(d) - C.KANT_IN))); }
      // [T1] далі — на гачок освітленого боку: шийка → вістря гачка → талія (усередині контуру, ×KANT_GM) — гарячий обідок одного боку барба
      const bi = bl > 0.2 ? bladeInset(C.ENV_SAFE) : null;
      if (bi) rim.push(...(ks > 0 ? [bi[1], bi[2]] : [bi[10], bi[9]]));
      out.strokes.push({ pts: rim, w: Math.max(minW, C.RIM_W * sm),
        grad: H.chordGrad(F, S, mono([[U(dR), 0, 0, kh, 0], [U(dR + 0.45 * (G.dN - dR)), 0, 0.5 * C.RIM_A, kh, 0], [U(G.dN), 0, C.RIM_A, kh, 0], [1, 0, C.RIM_A, kh, 0]])), inner: true });
      // комір довкола гачків — «різьба» тоном контуру по крові леза: шийка+ → гачок+ → талія+ → (поперек шийки) → талія− → гачок− → шийка− (×KANT_GM);
      //   гарячий лише на спалах (клацання, PULSE, запалення) — у спокої гачки читаються темними зубцями, не жовтим «Y»
      const kant = [];
      if (bi) kant.push(bi[0], bi[1], bi[2], bi[9], bi[10], bi[11]);
      else kant.push(P(G.dN, wn * 0.8), P(G.dN, -wn * 0.8));
      const gh = rgbHex(hexRgb(C.BARB_GROOVE).map((v, i) => v + (hexRgb(kh)[i] - v) * C.GROOVE_FLASH * flashAll));
      out.strokes.push({ pts: kant, w: Math.max(minW, C.KANT_W * sm), grad: H.chordGrad(F, S, [[0, 0, C.GROOVE_A, gh, 0], [1, 0, C.GROOVE_A, gh, 0]]), inner: true });
    }
    // лінія ядра полум'я: горно по осі головного язика від основи до CORE_LINE_TO, гасне до кінця (PULSE — біліє)
    {
      const ln = [], n = 7, cen = small ? () => 0 : cMain, to = C.CORE_LINE_TO;
      for (let i = 0; i <= n; i++) { const s = (to * i) / n; ln.push(P(d0 + s * (Lm + inL), cen(s))); }
      const mx = Math.min(R.MIX_MAX, 0.6 * core);
      out.strokes.push({ pts: ln, w: Math.max(minW, C.CORE_LINE_W * (small ? 0.8 : fm.w) * sm),
        grad: H.chordGrad(F, S, mono([[U(d0), mx, C.CORE_LINE_A, C.CORE, 0], [U(d0 + to * (Lm + inL)), mx, 0, C.CORE, 0]])), inner: true });
    }

    // [T1] обідки жарин: штрих від місця «звідки» (SPARK_TRAIL_Q життя тому) до ядра, круглі кінці, ширина 2·rh (rh ≤ PART_R_MAX); α від 0 у хвості до
    //   SPARK_RIM_A біля ядра (×життя) — прозорий обідок, витягнутий уздовж руху; ядро — крапкою зверху
    for (const sp of sparks) {
      if (sp.a < C.SPARK_HALO_MIN) continue;
      const rh = Math.min(R.PART_R_MAX, C.SPARK_HALO * Math.min(sp.r, R.PART_R_MAX)), lim = Math.max(0, env - rh - 0.08), ra = Math.min(R.HEAD_A_MAX, C.SPARK_RIM_A * sp.a / C.SPARK_A);
      const t = sparkPt(sp.ps, sp.pv, lim), h = sparkPt(sp.s, sp.v, lim);
      if (Math.hypot(h[0] - t[0], h[1] - t[1]) < 0.05 * pm) t[0] -= 0.05 * pm;
      out.strokes.push({ pts: [t, h], w: 2 * rh * sm, spark: true,
        grad: { a: t, b: h, stops: [[0, 0, 0, C.SPARK_GLOW_HUE, 0], [0.55, 0, 0.55 * ra, C.SPARK_GLOW_HUE, 0], [1, 0, ra, C.SPARK_GLOW_HUE, 0]] } });
    }

    // ── крапки: горно в основі полум'я (спалах долі й PULSE — до білого, ширше); жарини — лише гра (не прев'ю, не спокій)
    {
      const dc = small ? G.dN + 0.12 * Lm : G.dF, c = P(dc, small ? 0 : vb), fl = Math.max(click.flash, fm.flash, core);
      out.dots.push({ x: c[0], y: c[1], r: Math.max(minW / 2, C.CORE_R * (small ? bk : 1) * (1 + C.CORE_GROW * fl) * sm),
        mixK: Math.min(R.MIX_MAX, C.CORE_FLASH * Math.max(click.flash, fm.flash) * 0.5 + R.MIX_MAX * core), alpha: C.CORE_DOT_A, hue: C.CORE, ball: 0, inner: true });
    }
    for (const sp of sparks) {
      // ядро жарини — менше й гарячіше (обідок — в ореолі вище)
      const r = Math.min(sp.r, R.PART_R_MAX) * C.SPARK_CORE_K, lim = Math.max(0, env - r - 0.02), c = sparkPt(sp.s, sp.v, lim);
      out.dots.push({ x: c[0], y: c[1], r: r * sm, mixK: Math.min(R.MIX_MAX, sp.mix), alpha: Math.min(R.HEAD_A_MAX, sp.a),
        hue: barbLavaHue(Math.min(1, sp.heat + C.SPARK_CORE_HEAT)), ball: 0, spark: true });
    }
    if (small) out.opaque = true;
    return out;
  };
}

// ================================================================ реєстрація для стенда перегляду (SHOW §7.6 Б)

const MARK = "// [FAB-PREVIEW]";
/**
 * Вставки в КОПІЮ прототипу (register.py: якір рівно один; from — попередній якір; повторний запуск нічого не дублює). Рядки — SHOW §7.6 Б один в один,
 * плюс три вставки в trails.test.mjs, яких немає в §7.6 (інакше повний тест у стенді червоний): OWN_HUE (стиль зі своєю барвою, §11), регулярка
 * пози SHOP §19 (знала лише LEGEND_BY.keys — тепер і barb перед нею) і [ART3] §9 «прев'ю — ті самі примітиви»: крапки без жарин, як у CAPE
 * (жарини спокою зриваються з кінчика лише в грі, ART3 §6.4), а [ART3 r1 T1] — і штрихи без обідків жарин (spark).
 */
export const BARB_PATCH = Object.freeze([
  { file: "trails.mjs", anchor: "// ================================================================ ЕФЕКТИ ЯК У ГРІ", mode: "before",
    text: `import { BARB_DEF, barbMaker } from "./trail-fabulous.mjs";   ${MARK}` },
  { file: "trails.mjs", from: '{ id: "keys", name: "KEYS",', anchor: "\n];", mode: "before",
    text: `  { ...BARB_DEF },   ${MARK} легендарний хвіст THE FABULOUS` },
  { file: "trails.mjs", anchor: "const MAKERS = {", mode: "replace",
    text: `const MAKERS = { inferno: barbMaker({ ribbon, gradE, glowOf, empty, chordGrad, chordU, pointGrad, LOOK_E, headBallOf, h01, TRAIL_RULES, TRAIL_MODE, TRAIL_STYLE_E }),   ${MARK}\n` },
  { file: "trails.test.mjs", anchor: '["keys", "KEYS", null, "ludovivo",', mode: "after",
    text: `  ["inferno", "INFERNO", null, "the-fabulous", "The pit lord's tail of living fire · it lashes on every beat"],   ${MARK}` },
  { file: "trails.test.mjs", anchor: 'const OWN_HUE = new Set(["flare", "wisp", "cape", "fern", "keys"]);', mode: "replace",
    text: 'const OWN_HUE = new Set(["flare", "wisp", "cape", "fern", "keys", "inferno"]);   /* [FAB-PREVIEW] BARB — обсидіан, жар, лава, горно */' },
  { file: "trails.test.mjs", anchor: 'const dotsOf = (P) => (id === "cape" ? P.dots.filter((d) => !d.spark) : P.dots).length;', mode: "replace",
    text: 'const dotsOf = (P) => (id === "cape" || id === "inferno" ? P.dots.filter((d) => !d.spark) : P.dots).length;   /* [FAB-PREVIEW] BARB: жарини спокою — лише в грі (ART3 §6.4) */' },
  { file: "trails.test.mjs", anchor: "a.strokes.length === b.strokes.length, `прев'ю ${id}: ті самі примітиви`", mode: "replace",
    text: "a.strokes.filter((x) => !x.spark).length === b.strokes.filter((x) => !x.spark).length, `прев'ю ${id}: ті самі примітиви` /* [FAB-PREVIEW] BARB: обідки жарин (spark) — лише в грі (ART3 r1 T1) */" },
  { file: "trails.test.mjs", anchor: "&& /LEGEND_BY: \\{ keys: \\{[^}]*\\} \\}/.test(jsx),", mode: "replace",
    text: "&& /LEGEND_BY: \\{ (inferno: \\{[^}]*\\}, )?keys: \\{[^}]*\\} \\}/.test(jsx),   /* [FAB-PREVIEW] поза BARB — LEGEND_BY.barb (SHOW §6.3) */" },
  { file: "ranks.mjs", anchor: '"strum", "keys"]', mode: "replace", text: '"strum", "keys", "inferno"]' },
  // [18.11 · порт 421] рядок trail-vectors.mjs (PORT_PENDING ["barb"]) прибрано: BARB перенесено в гру (TrailBarb), черга порту порожня
  { file: "orbit-dash-tutorial.jsx", anchor: 'N_("A grand piano keyboard · it plays along with the music"),', mode: "after",
    text: `  N_("The pit lord's tail of living fire · it lashes on every beat"),   ${MARK} INFERNO` },
  { file: "orbit-dash-tutorial.jsx", anchor: "LEGEND_BY: { keys: {", mode: "replace",
    text: `LEGEND_BY: { inferno: { ZOOM: ${BARB_SHOP.ZOOM}, AT: ${BARB_SHOP.AT}, CX: ${BARB_SHOP.CX}, CY: ${BARB_SHOP.CY}, HOP_BEATS: ${BARB_SHOP.HOP_BEATS} }, keys: {` },
]);
