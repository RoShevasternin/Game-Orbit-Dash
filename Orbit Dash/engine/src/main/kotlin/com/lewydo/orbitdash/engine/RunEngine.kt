package com.lewydo.orbitdash.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

// ═════════════════════════════════════════════════════════════════════════════
//  RunEngine — увесь ран як чиста логіка. Нуль scene2d, нуль Gdx.
//
//  ПОРТ ПРОТОТИПУ 1:1. Кожна формула звірена з orbit-dash.jsx построчно, імена
//  полів збережені (angle, ringIndex, rr, prevRel...) — щоб diff з прототипом
//  читався очима. Змінюєш баланс — міняй ту саму константу, що міняв би там.
//
//  ОДИНИЦІ. Рушій працює в «одиницях прототипу»: радіуси кілець [190, 320].
//  Це збігається з ДІАМЕТРАМИ поля в новому дизайні, тож усі лінійні константи
//  (вікно попадання, зсув іскри, поріг «доїхав») діють дослівно.
//  Межа з видом: fieldRadius = engineRadius * TO_FIELD (0.5).
//
//  НАПРЯМОК. У прототипі Y-вниз: кут зростає = за годинниковою на екрані.
//  У LibGDX Y-вгору, тож вид малює ДЗЕРКАЛО: screenAngle = -engine.angle.
//  Уся внутрішня логіка («спавн попереду», знак prevRel у near-miss) тримається
//  на знаку кута — тому рушій НЕ чіпаємо, мінус живе лише на межі з видом.
//
//  ДЕТЕРМІНІЗМ. Єдине джерело випадковості — Random(seed). Той самий сід +
//  та сама послідовність (dt, tap) = ідентичний ран до останнього гема.
//  Це безкоштовно дає: реплеї, привида «твій рекорд», тести балансу
//  (тисяча симуляцій у юніт-тесті без емулятора).
//
//  РАДІУСИ КІЛЕЦЬ ЖИВУТЬ ТУТ, а не в AOrbitField: колізії залежать від
//  поточного радіуса ПІД ЧАС роз'їзду (ORBIT III вмикається на 30-й секунді
//  посеред рану). Поле щокадру читає engine.ringR — інакше два лерпи з
//  різними фазами: колізії на одних колах, картинка на інших.
// ═════════════════════════════════════════════════════════════════════════════
class RunEngine(
    private val config: Config,
    val seed: Long = System.nanoTime(),
) {

    companion object {
        /** engine units → design-юнітів AOrbitField (радіус поля 160 = 320 тут). */
        const val TO_FIELD = 0.5f

        /** Радіуси кілець (units прототипу = діаметри поля в новому дизайні). */
        val LAYOUT_2 = floatArrayOf(190f, 320f, 320f)
        val LAYOUT_3 = floatArrayOf(130f, 225f, 320f)

        const val MAX_COMBO = 8

        // ── іскра комбо ──
        /**
         * Іскра висить за ORB_OFF ПЕРЕД шипом по ходу руху (engine units по дузі),
         * вікно підбору — ±ORB_PICK по дузі. Менший ORB_OFF притискає іскру до
         * шипа (страшніше), більший ORB_PICK — щедріший підбір. Вікно ПРОСТОРОВЕ:
         * однакове на будь-якій швидкості й будь-якому кільці.
         */
        const val ORB_OFF  = 52f
        const val ORB_PICK = 32f

        // ── debug-розстановка ──
        /**
         * Мінімальна дуга між debug-сутностями, engine units. 80 = 40 design поля
         * ≈ діаметр glow бустера: сусіди не накладаються навіть світінням.
         * Дуга, а не кут: на внутрішньому кільці той самий кут — коротший відрізок.
         */
        internal const val DEBUG_GAP = 80f

        /** Вікно розстановки попереду м'яча, градуси. Ближче за 30° — не встигнеш
         *  побачити; далі за 170° сутність стає «позаду» й її прибирає rel < −60. */
        private const val DEBUG_ARC_START = 90f
        private const val DEBUG_ARC_MIN   = 30f
        private const val DEBUG_ARC_MAX   = 170f

        // ── кути ──
        // Тут, а не в game/utils: рушій не імпортує з проєкту нічого (CLAUDE.md,
        // «Розміщення файлів»). Користувач у констант один — цей клас.
        private const val RAD_TO_DEG = (180.0 / PI).toFloat()
        private const val TWO_PI     = (PI * 2).toFloat()

        private fun norm(a: Float): Float { var x = a % 360f; if (x < 0f) x += 360f; return x }
        /** Різниця кутів у (-180, 180] — знак каже «попереду/позаду». */
        private fun angDiff(a: Float, b: Float): Float = ((a - b) % 360f + 540f) % 360f - 180f
    }

    // ------------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------------

    /**
     * Знімок мета-стану на вході в ран. Рушій НЕ читає сейв — усе, що впливає
     * на ран, приходить сюди явно. Тому ран відтворюваний: Config + seed + тапи.
     */
    data class Config(
        val upMagnet  : Int = 0,      // 0..5  ширше вікно підбору гемів
        val upShield  : Int = 0,      // 0..3  стартові заряди щита
        val upSlow    : Int = 0,      // 0..5  повільніший старт (-6%/рівень)
        val upBdur    : Int = 0,      // 0..5  +10% тривалості бустів
        val upBfreq   : Int = 0,      // 0..5  бусти спавняться частіше
        val upKeeper  : Int = 0,      // 0..5  комбо тримається довше
        val upValue   : Int = 0,      // 0..10 +5% цінності гема
        val orbit3    : Boolean = false,
        val startBoost: Boost? = null,   // буст з рекламної кнопки меню
        val mercy     : Boolean = false,  // після 2 швидких смертей: -10% старт + доріжка гемів
    )

    enum class Phase { RUN, DEAD }
    enum class Kind  { GEM, SPIKE, BOOST }

    /**
     * Буст і його правила. Тут лише чисті числа: рушій не знає ні кольорів, ні
     * іконок — вигляд і назва для гравця живуть у BoostCatalog.
     *
     *   dur        — скільки триває, секунди; 0 = миттєвий (SHIELD, PULSE)
     *   weight     — скільки разів буст стоїть у пулі спавну
     *   startOffer — чи може випасти в рекламному офері меню
     */
    enum class Boost(
        val dur       : Float,
        val weight    : Int,
        val startOffer: Boolean,
    ) {
        SHIELD( 0f, 1, true),
        MAGNET( 8f, 2, true),
        FRENZY(10f, 2, true),
        SLOW  ( 4f, 1, true),
        PULSE ( 0f, 2, false);

        companion object {
            /**
             * Пул спавну: кожен буст повторено weight разів, у порядку enum.
             * Виходить рівно той масив, що стояв руками в spawnBoost(), — тож
             * той самий seed дає ту саму послідовність. Рахується один раз,
             * а не на кожен спавн.
             */
            val SPAWN_POOL: List<Boost> = entries.flatMap { b -> List(b.weight) { b } }

            /** Що може запропонувати рекламна кнопка меню. */
            val START_OFFERS: List<Boost> = entries.filter { it.startOffer }
        }
    }

    /**
     * Сутність на орбіті. МУТАБЕЛЬНА і перевикористовується логікою щокадру —
     * вид мапить її на актора за [id] (стабільний, унікальний у межах рану).
     */
    class Entity(
        val id: Int,
        val kind: Kind,
        val boost: Boost?,
        val ring: Int,
        var a: Float,          // кут, одиниці прототипу
        var rr: Float,         // поточний радіус (лерп до кільця / тяга магніта)
    ) {
        var s = 0f             // spawn-scale 0→1 (виду для появи, логіці для «дозрів»)
        var pulled  = false    // магніт захопив — інші правила зникнення
        var prevRel: Float? = null   // rel минулого кадру: детектор проходу повз (DEBUG · EZ COMBO)

        /**
         * Іскра комбо ще висить перед шипом. Іскра — НЕ сутність: її кут рахується
         * від шипа щокадру ([sparkAngle]), а стан — цей прапорець. Спіймали або
         * зарахували EZ COMBO — false, і вдруге за той самий шип не спрацює.
         */
        var orb = kind == Kind.SPIKE
    }

    /** Підсумок рану — все для GameOver-екрана й аналітики (run_end). */
    data class RunResult(
        val score      : Int,
        val gems       : Int,
        val durationSec: Int,
        val deathRing  : Int,      // 1-базований, як в аналітиці
        val nearMisses : Int,      // спійманих іскор = комбо за ран
        val boostsUsed : Int,
        val revived    : Boolean,
    )

    /** Події для звуку/партиклів/вібро/аналітики. Рушій сам по собі німий. */
    interface Listener {
        fun onTap() {}
        /** Іскру шипа [e] спіймано. [sparkAngle] — де вона була: ефекти в точці іскри, не шипа. */
        fun onNearMiss(e: Entity, sparkAngle: Float, bonus: Int) {}
        fun onGemPicked(e: Entity, value: Float) {}
        fun onBoostApplied(boost: Boost, e: Entity?) {}
        fun onShieldSaved(e: Entity) {}
        fun onPulseKill(e: Entity) {}
        fun onOrbit3Online() {}
        fun onDied(result: RunResult) {}
        fun onRevived() {}
    }

    // ------------------------------------------------------------------------
    // RNG — єдине джерело випадковості
    // ------------------------------------------------------------------------
    private val rng = Random(seed)
    private fun rnd(a: Float, b: Float) = a + rng.nextFloat() * (b - a)

    // ------------------------------------------------------------------------
    // State (імена = st.* прототипу)
    // ------------------------------------------------------------------------
    var listener: Listener? = null

    var phase = Phase.RUN;  private set
    var time  = 0f;         private set

    var angle     = -90f;   private set   // старт «вгорі» (вид покаже -angle = 90)
    var ringIndex = 0;      private set   // ран починається з ВНУТРІШНЬОГО
    var dir       = 1;      private set
    var radius    = LAYOUT_2[0]; private set

    var ringCount = 2;      private set
    val ringR     = LAYOUT_2.copyOf()
    private var target = LAYOUT_2
    /** Альфа третього кільця 0→1 після активації — виду. */
    var ring3Alpha = 0f;    private set

    private val baseSpeed =
        100f * (1f - 0.06f * config.upSlow) * (if (config.mercy) 0.9f else 1f)

    var shield = config.upShield; private set

    /** Максимум, досягнутий у ЦЬОМУ рані. Визначає, скільки слотів існує:
     *  витрачений щит лишає порожню капсулу, а не прибирає її. */
    var shieldMax = config.upShield; private set

    var invuln = 0f;        private set

    var gemsRun  = 0f;      private set   // float: value-апгрейд дає дроби
    var gemCount = 0;       private set
    private var banked  = false
    var revived  = false;   private set
    private var bonus = 0
    var score = 0;          private set

    /**
     * Геми, зібрані за ран, — ціле, яке бачить гравець (HUD, RunResult) і яке
     * віддасть bank(). Не обнуляється після bank(): екран смерті показує суму
     * рану, навіть якщо її вже зараховано.
     */
    val gemsCollected: Int get() = floor(gemsRun).toInt()

    // ------------------------------------------------------------------------
    //  КОМБО — СПІЙМАНА ІСКРА, не near-miss.
    //
    //  Біля кожного шипа за ORB_OFF попереду висить видима іскра. Гравець у
    //  вікні ±ORB_PICK по дузі та ±45 по радіусу — іскра спіймана, комбо +1.
    //  Іскра стоїть на кільці шипа, ближче за зону зіткнення (ORB_OFF − ORB_PICK
    //  = 20 < 37), тож узяти її можна лише проходячи впритул — ризик той самий,
    //  що був, але тепер він ВИДИМИЙ, а вікно не залежить від швидкості.
    //
    //  Старе вікно було часовим (тап за 16–84 мс до перетину) і на внутрішньому
    //  кільці на старті не існувало фізично — див. docs/decisions.md.
    // ------------------------------------------------------------------------
    var combo    = 0f;      private set
    var comboT   = 0f;      private set
    val comboWin = 4f + 0.5f * config.upKeeper

    var magnetT   = 0f;       private set
    var frenzyT   = 0f;       private set
    var slowT     = 0f;       private set
    var boostTot  = 1f;      private set   // тривалість останнього буста — для прогрес-дуги HUD
    var waveT     = 0f;     private set   // PULSE-хвиля, виду
    var announceT = 0f;     private set   // «ORBIT III ONLINE», виду
    var shake     = 0f;     private set   // сила трясіння камери, виду

    private val durMul = 1f + 0.1f * config.upBdur
    private var spawnT = 0.8f
    private var boostT = max(7f, rnd(12f, 18f) - config.upBfreq)

    private var mercyDone = !config.mercy
    private var nearCount = 0
    private var boostUsed = 0

    // Буст, підібраний у цьому кадрі. Застосовується ПІСЛЯ обходу сутностей:
    // ефекти на кшталт PULSE видаляють спайки, а робити це під час ітерації
    // того самого списку — ConcurrentModificationException.
    private var pendingBoost : Boost? = null
    private var pendingBoostE: Entity? = null

    private var nextId = 0
    private val _entities = ArrayList<Entity>(24)
    /** Живі сутності. Вид мапить на акторів за Entity.id. */
    val entities: List<Entity> get() = _entities

    init {
        // Стартовий буст з рекламної кнопки — застосовується до першого кадру,
        // гравець стартує вже під ефектом.
        config.startBoost?.let { applyBoost(it, null) }
    }

    // ------------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------------

    /** Тап = сусіднє кільце; на краю напрямок розвертається. */
    fun tap() {
        if (phase != Phase.RUN) return
        var next = ringIndex + dir
        if (next > ringCount - 1 || next < 0) { dir = -dir; next = ringIndex + dir }
        ringIndex = next
        listener?.onTap()
    }

    /**
     * Забрати геми рану в сейв. mult — множник (x2 за рекламу).
     * Повертає суму РІВНО ОДИН РАЗ: повторний виклик = 0, захист від
     * подвійного нарахування (кнопка + автобанк при рестарті).
     *
     * floor, а не round: гравець бачив floor (HUD, RunResult), і нарахувати
     * треба рівно те, що він бачив, — без «+1 нізвідки».
     */
    fun bank(mult: Float = 1f): Int {
        if (banked) return 0
        banked = true
        return floor(gemsRun * mult).toInt()
    }

    /**
     * Ревайв за рекламу. Дозволений один раз. Чистить спайки ±90° навколо
     * гравця — інакше він воскресає в ту саму колючку. Якщо геми вже
     * забанкані x2 — обнуляємо лічильник рану, щоб їх не нарахувало вдруге.
     */
    fun revive() {
        if (phase != Phase.DEAD || revived) return

        _entities.removeAll { e ->
            e.kind == Kind.SPIKE && abs(angDiff(e.a, angle)) < 90f
        }
        if (banked) { gemsRun = 0f; banked = false }

        phase   = Phase.RUN
        revived = true
        invuln  = 2.2f
        listener?.onRevived()
    }

    val canRevive: Boolean get() = phase == Phase.DEAD && !revived

    // ------------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------------
    fun update(dt: Float) {
        if (phase != Phase.RUN) return
        if (debugFrozen) { updateFrozen(dt); return }

        // SLOW-MO уповільнює СВІТ (wdt), але не таймери ефектів (dt) —
        // інакше буст тривав би довше просто тому, що він активний.
        val ts  = if (slowT > 0f) 0.6f else 1f
        val wdt = dt * ts

        time += wdt

        // Асимптотична швидкість: старт ~100°/с, стеля 300°/с, вихід за ~40с.
        // Синус — «дихання» ±8%: темп трохи гуляє, мозок не звикає до ритму.
        var v = baseSpeed + (300f - baseSpeed) * (1f - exp(-time / 40f))
        v *= 1f + 0.08f * sin(TWO_PI * time / 20f)
        angle = norm(angle + v * wdt)

        // Перехід між кільцями — рух, не телепорт
        radius += (ringR[ringIndex] - radius) * min(1f, 14f * wdt)

        invuln = max(0f, invuln - dt)

        comboT -= dt
        if (comboT <= 0f) combo = 0f
        magnetT   = max(0f, magnetT - dt)
        frenzyT   = max(0f, frenzyT - dt)
        slowT     = max(0f, slowT - dt)
        waveT     = max(0f, waveT - dt)
        announceT = max(0f, announceT - dt)
        shake     = max(0f, shake - dt * 1.6f)

        // ORBIT III вмикається з 30-ї секунди — посеред рану, звідси й лерп.
        // Debug-перемикач забирає це рішення собі до кінця рану
        if (!debugOrbit3Set && config.orbit3 && ringCount == 2 && time >= 30f) orbit3Online()
        stepRings(dt, wdt)

        // Mercy: доріжка гемів на старті після двох швидких смертей —
        // гарантований ранній успіх, щоб не зневіритись
        if (!mercyDone && time > 1f) {
            mercyDone = true
            for (k in 0 until 6) spawn(forceGem = true, ringOpt = 0, angOpt = angle + 120f + k * 15f)
        }

        // Спавн: інтервал стискається з часом (складність), frenzy пришвидшує
        spawnT -= wdt * (if (frenzyT > 0f) 1.5f else 1f)
        if (spawnT <= 0f) { spawn(); spawnT = max(0.5f, 1.05f - time * 0.011f)
        }
        boostT -= wdt
        if (boostT <= 0f) { spawnBoost(); boostT = max(7f, rnd(12f, 18f) - config.upBfreq)
        }

        updateEntities(dt, wdt)

        score = floor(time * 12f).toInt() + floor(gemsRun).toInt() * 10 + bonus
    }

    // ------------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------------

    private fun spawn(forceGem: Boolean = false, ringOpt: Int? = null, angOpt: Float? = null) {
        val ring = ringOpt ?: rng.nextInt(ringCount)
        val a    = if (angOpt != null) norm(angOpt) else norm(angle + rnd(120f, 170f))

        // Мінімальна кутова відстань на кільці — без злипання
        for (e in _entities) if (e.ring == ring && abs(angDiff(e.a, a)) < 20f) return

        // Шанс спайка росте з часом: 42% → стеля 62%
        val spikeCh = min(0.62f, 0.42f + time * 0.004f)
        val gem = forceGem || (if (frenzyT > 0f) rng.nextFloat() < 0.85f else rng.nextFloat() >= spikeCh)

        _entities.add(Entity(
            id    = nextId++,
            kind  = if (gem) Kind.GEM else Kind.SPIKE,
            boost = null,
            ring  = ring,
            a     = a,
            rr    = ringR[ring],
        ))
    }

    private fun spawnBoost() {
        // Зважений пул — ваги стоять у самому enum (Boost.weight)
        val pool = Boost.SPAWN_POOL
        val ring = rng.nextInt(ringCount)
        _entities.add(Entity(
            id    = nextId++,
            kind  = Kind.BOOST,
            boost = pool[rng.nextInt(pool.size)],
            ring  = ring,
            a     = norm(angle + rnd(130f, 170f)),
            rr    = ringR[ring],
        ))
    }

    // ------------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------------

    /**
     * DEBUG-пауза м'яча. Кут не росте, час, спавн, таймери й колізії стоять —
     * можна підкидати шипи просто перед м'ячем. Але те, що вже є на полі,
     * дограє появу (e.s) і посадку на кільце, а тап переводить м'яч на інше
     * кільце: так видно щойно підкинуте й можна відійти від шипа.
     */
    var debugFrozen = false

    /**
     * DEBUG · EZ COMBO: комбо за будь-який прохід повз шип (rel змінив знак)
     * у радіусі 145 — накриває сусіднє кільце, іскру ловити не треба. Гасить
     * іскру шипа, тож двічі за один шип не зарахує. Лише тест.
     */
    var debugEzCombo = false

    /** true — третю орбіту вже перемкнув debug, правило «з 30-ї секунди» мовчить. */
    private var debugOrbit3Set = false

    /**
     * DEBUG: третя орбіта зараз, без 30-ї секунди. Увімкнення — той самий
     * роз'їзд і анонс, що й у грі. Вимкнення — кільця сходяться назад, третє
     * гасне, його сутності зникають, м'яч із нього переходить на друге.
     */
    fun debugSetOrbit3(on: Boolean) {
        debugOrbit3Set = true
        if (on == (ringCount == 3)) return

        if (on) { orbit3Online(); return }

        ringCount = 2
        target    = LAYOUT_2
        _entities.removeAll { it.ring == 2 }
        if (ringIndex == 2) ringIndex = 1   // напрям виправить tap(): на краю він розвертається
    }

    /** DEBUG: бустер попереду гравця. false — вільного місця у вікні не лишилось. */
    fun debugSpawnBoost(bt: Boost): Boolean = debugPlace(Kind.BOOST, bt)

    /** DEBUG: шип попереду гравця. false — вільного місця у вікні не лишилось. */
    fun debugSpawnSpike(): Boolean = debugPlace(Kind.SPIKE, null)

    /**
     * Спершу кільце гравця — щоб підкинуте гарантовано долетіло до м'яча, —
     * потім решта по колу. Жодного rng: debug-кнопка не зсуває послідовність
     * рану, яку далі генерує seed.
     */
    private fun debugPlace(kind: Kind, boost: Boost?): Boolean {
        for (k in 0 until ringCount) {
            val ring = (ringIndex + k) % ringCount
            val a    = debugFreeAngle(ring) ?: continue
            _entities.add(Entity(
                id    = nextId++,
                kind  = kind,
                boost = boost,
                ring  = ring,
                a     = a,
                rr    = ringR[ring],
            ))
            return true
        }
        return false
    }

    /**
     * Перший вільний кут на кільці віялом від +90°: 90, 90+s, 90−s, 90+2s …
     * Крок s — DEBUG_GAP у градусах саме цього кільця. Зайнято, якщо будь-яка
     * сутність кільця (своя чи з натурального спавну) ближча за 0.9·s:
     * запас від float-похибки norm(), щоб сусідня клітинка сітки не «злипалась».
     */
    private fun debugFreeAngle(ring: Int): Float? {
        val step = DEBUG_GAP / ringR[ring] * RAD_TO_DEG
        val maxK = ((DEBUG_ARC_MAX - DEBUG_ARC_START) / step).toInt() + 1

        for (i in 0..maxK * 2) {
            val k   = (i + 1) / 2
            val off = DEBUG_ARC_START + if (i % 2 == 1) k * step else -k * step
            if (off < DEBUG_ARC_MIN || off > DEBUG_ARC_MAX) continue

            val a = norm(angle + off)
            if (_entities.none { it.ring == ring && abs(angDiff(it.a, a)) < step * 0.9f }) return a
        }
        return null
    }

    private fun updateFrozen(dt: Float) {
        radius += (ringR[ringIndex] - radius) * min(1f, 14f * dt)
        stepRings(dt, dt)

        for (e in _entities) {
            e.s = min(1f, e.s + 4f * dt)
            if (!e.pulled) e.rr += (ringR[e.ring] - e.rr) * min(1f, 6f * dt)
        }
    }

    private fun orbit3Online() {
        ringCount = 3
        target    = LAYOUT_3
        announceT = 1.8f
        listener?.onOrbit3Online()
    }

    /**
     * Роз'їзд кілець (час світу) і проявлення третього (реальний час). Третє
     * гасне назад лише після debug-вимкнення — у грі орбіта не зникає.
     */
    private fun stepRings(dt: Float, wdt: Float) {
        for (i in 0 until 3) ringR[i] += (target[i] - ringR[i]) * min(1f, 2.5f * wdt)
        ring3Alpha = if (ringCount == 3) min(1f, ring3Alpha + 1.5f * dt)
        else max(0f, ring3Alpha - 1.5f * dt)
    }


    private fun applyBoost(bt: Boost, e: Entity?) {
        boostUsed++
        listener?.onBoostApplied(bt, e)

        when (bt) {
            Boost.SHIELD -> {
                shield = min(3, shield + 1)
                shieldMax = max(shieldMax, shield)
                return
            }

            Boost.PULSE -> {
                // Хвиля вбиває спайки в секторі перед гравцем; кожен = +15 бонусу
                waveT = 0.5f
                shake = max(shake, 0.5f)
                val it = _entities.iterator()
                while (it.hasNext()) {
                    val s = it.next()
                    if (s.kind != Kind.SPIKE) continue
                    val rel = angDiff(s.a, angle)
                    if (rel > -25f && rel < 140f) {
                        listener?.onPulseKill(s)
                        bonus += 15
                        it.remove()
                    }
                }
                return
            }

            else -> {
                // Таймерні бусти взаємовиключні: новий скидає всі
                val dur = bt.dur * durMul

                magnetT = 0f; frenzyT = 0f; slowT = 0f
                when (bt) {
                    Boost.MAGNET -> magnetT = dur
                    Boost.FRENZY -> frenzyT = dur
                    Boost.SLOW   -> slowT   = dur
                    else -> Unit
                }
                boostTot = dur
            }
        }
    }

    private fun comboMult(): Float = min(5f, 1f + combo)

    /**
     * Множник для ВИДУ (HUD, попап над іскрою). Стеля 5 — правило гри, тож
     * живе тут, а не дублюється константою в кожному, хто його показує.
     */
    val multiplier: Int get() = comboMult().toInt()

    /**
     * Частка вікна комбо, що лишилась, 0..1 — виду для кільця над м'ячем.
     * 0, коли комбо немає. Іскра й гем ставлять вікно заново на 100 %,
     * далі воно лише тане; вийшло — комбо гасне в нуль, без сходинок.
     */
    val comboFrac: Float get() = if (combo > 0f) (comboT / comboWin).coerceIn(0f, 1f) else 0f

    /**
     * Кут іскри шипа [e] (одиниці рушія) або null — не шип чи іскру вже спіймано.
     * Лінійний зсув ORB_OFF переведений у градуси через радіус кільця: на
     * меншому колі той самий зсув — більший кут, дуга по кільцях однакова.
     * Виду — щоб поставити актора іскри; окремої сутності для неї немає.
     */
    fun sparkAngle(e: Entity): Float? =
        if (e.kind == Kind.SPIKE && e.orb) norm(e.a - ORB_OFF / e.rr * RAD_TO_DEG) else null

    /** Іскру спіймано: комбо +1, таймер заново, бонус 5·множник одразу в рахунок. */
    private fun nearMiss(e: Entity, sparkA: Float) {
        combo  = min(MAX_COMBO.toFloat(), combo + 1f)
        comboT = comboWin
        nearCount++
        val b = (5f * comboMult()).toInt()
        bonus += b
        listener?.onNearMiss(e, sparkA, b)
    }

    private fun updateEntities(dt: Float, wdt: Float) {
        val arrived = abs(radius - ringR[ringIndex]) < 45f

        val it = _entities.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.s = min(1f, e.s + 4f * wdt)

            // МАГНІТ: тягне геми з УСІХ кілець у сектор ±50° перед гравцем
            if (magnetT > 0f && e.kind == Kind.GEM) {
                val rel0 = angDiff(angle, e.a)
                if (abs(rel0) < 50f) {
                    e.a = norm(e.a + sign(rel0) * min(abs(rel0), 260f * dt))
                    e.rr += (radius - e.rr) * min(1f, 8f * dt)
                    e.pulled = true
                }
            } else if (!e.pulled) {
                // Не захоплений — плавно тримається свого кільця (роз'їзд ORBIT III)
                e.rr += (ringR[e.ring] - e.rr) * min(1f, 6f * wdt)
            }

            val rel = angDiff(e.a, angle)

            // DEBUG · EZ COMBO: шип щойно ПРОСКОЧИВ повз (rel змінив знак + → −)
            // і гравець у радіусі 145 — зараховуємо без іскри
            val pr = e.prevRel
            if (debugEzCombo && e.orb && pr != null && pr > 0f && rel <= 0f && abs(radius - e.rr) < 145f) {
                e.orb = false
                nearMiss(e, e.a)
            }
            e.prevRel = rel

            // Пішов далеко за спину — прибираємо (магнітні геми не чіпаємо)
            if (rel < -60f && !e.pulled) { it.remove(); continue }

            // ІСКРА КОМБО: гравець у вікні ±ORB_PICK по дузі навколо іскри і
            // близько по радіусу. Радіусне вікно ±45 тримається ще ~30 мс після
            // тапу (лерп радіуса), тож ловиться і «проїхав крізь іскру на кільці
            // шипа», і «тапнув рівно на іскрі й полетів геть». Правило для
            // гравця одне: тапни, коли торкнувся іскри.
            val sparkA = sparkAngle(e)
            if (sparkA != null && e.s > 0.5f) {
                val orel = angDiff(sparkA, angle)
                if (abs(orel) < ORB_PICK / e.rr * RAD_TO_DEG && abs(radius - e.rr) < 45f) {
                    e.orb = false
                    nearMiss(e, sparkA)
                }
            }

            when (e.kind) {
                Kind.GEM -> if (e.s > 0.5f) {
                    val er = 15f * (1f + 0.4f * config.upMagnet)
                    // Кутове вікно попадання: лінійний розмір (20+er) переведений
                    // у градуси через радіус кільця — на меншому колі те саме
                    // «тіло» гема займає більший кут
                    val angularHit = e.ring == ringIndex && arrived &&
                            abs(rel) < (20f + er) / ringR[e.ring] * RAD_TO_DEG &&
                            abs(radius - e.rr) < 45f
                    val pulledHit = e.pulled && dist(e) < 30f

                    if (angularHit || pulledHit) {
                        val v = 1f *
                                (if (e.ring == 2) 2f else 1f) *          // ORBIT III: геми x2
                                (if (frenzyT > 0f) 2f else 1f) *
                                comboMult() *
                                (1f + 0.05f * config.upValue)
                        gemsRun += v
                        gemCount++
                        if (combo > 0f) comboT = comboWin            // гем підтримує комбо
                        listener?.onGemPicked(e, v)
                        it.remove()
                        continue
                    }
                }

                Kind.BOOST -> if (e.s > 0.5f && e.ring == ringIndex && arrived) {
                    if (abs(rel) < 38f / ringR[e.ring] * RAD_TO_DEG) {
                        // Тільки запам'ятовуємо — застосування нижче, поза циклом
                        pendingBoost  = e.boost
                        pendingBoostE = e
                        it.remove()
                        continue
                    }
                }

                Kind.SPIKE -> if (e.s > 0.5f && e.ring == ringIndex && arrived && invuln <= 0f) {
                    if (abs(rel) < (20f + 17f) / ringR[e.ring] * RAD_TO_DEG) {
                        if (shield > 0) {
                            shield--
                            invuln = 1.2f
                            shake  = max(shake, 0.5f)
                            listener?.onShieldSaved(e)
                            it.remove()
                            continue
                        } else {
                            die()
                            return
                        }
                    }
                }
            }
        }

        // Ітерація завершена — тепер список можна безпечно мутувати.
        // Занулюємо ПЕРЕД викликом: якщо applyBoost колись сам щось підбере,
        // не отримаємо рекурсивного сліду.
        pendingBoost?.let { bt ->
            val e = pendingBoostE
            pendingBoost  = null
            pendingBoostE = null
            applyBoost(bt, e)
        }
    }

    // ------------------------------------------------------------------------
    // Death
    // ------------------------------------------------------------------------
    private fun die() {
        phase = Phase.DEAD
        shake = 1f
        listener?.onDied(buildResult())
    }

    /** Підсумок поточного стану — для GameOver і run_end. */
    fun buildResult() = RunResult(
        score       = score,
        gems        = gemsCollected,
        durationSec = time.toInt(),
        deathRing   = ringIndex + 1,
        nearMisses  = nearCount,
        boostsUsed  = boostUsed,
        revived     = revived,
    )

    // ------------------------------------------------------------------------
    // Utils
    // ------------------------------------------------------------------------
    private fun dist(e: Entity): Float {
        val d = Math.toRadians(angDiff(e.a, angle).toDouble())
        // відстань між точками на (майже) колі: хорда за кутом + різниця радіусів
        val chord = 2.0 * radius * abs(sin(d / 2.0))
        return hypot(chord.toFloat(), radius - e.rr)
    }

}