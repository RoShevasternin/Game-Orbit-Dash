package com.lewydo.orbitdash.engine

import com.lewydo.orbitdash.engine.RunEngine.Boost
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
//  Тести рушія — ганяються на ноутбуці, без пристрою:
//      sh gradlew :engine:test
//
//  Саме заради цього рушій і живе в окремому модулі: без libGDX у залежностях
//  ран — це просто цикл update(dt), і хвилина гри рахується за мілісекунди.
// ─────────────────────────────────────────────────────────────────────────────
class RunEngineTest {

    /**
     * Порядок пулу — частина детермінізму: rng.nextInt(8) індексує саме цей
     * список. Переставиш константи в enum Boost — зміниться кожен ран на тому
     * самому seed, і цей тест скаже про це першим.
     */
    @Test
    fun spawnPoolKeepsHistoricalOrder() {
        assertEquals(
            listOf(
                Boost.SHIELD,
                Boost.MAGNET, Boost.MAGNET,
                Boost.FRENZY, Boost.FRENZY,
                Boost.SLOW,
                Boost.PULSE,  Boost.PULSE,
            ),
            Boost.SPAWN_POOL,
        )
    }

    /** Той самий seed + ті самі тапи = той самий ран до останньої сутності. */
    @Test
    fun sameSeedGivesSameRun() {
        assertEquals(simulate(seed = 42L), simulate(seed = 42L))
    }

    /** Хвилина гри при 60 FPS, тап кожні 45 кадрів. Відбиток — усе, що видно ззовні. */
    private fun simulate(seed: Long): String {
        val e = RunEngine(RunEngine.Config(), seed)

        repeat(60 * 60) { frame ->
            if (frame % 45 == 0) e.tap()
            e.update(1f / 60f)
        }

        return "${e.phase} t=${e.time} score=${e.score} gems=${e.gemCount} " +
            e.entities.joinToString { "${it.kind}/${it.boost}/${it.ring}/${it.a}" }
    }

    /**
     * Пауза тримає м'яч: шип попереду на своєму кільці без паузи вбиває за
     * секунду, з паузою — ні за десять. Світ не спавнить, але поява дограє.
     */
    @Test
    fun frozenBallNeitherMovesNorDies() {
        val live = RunEngine(RunEngine.Config(), seed = 1L)
        live.debugSpawnSpike()
        repeat(60 * 3) { live.update(1f / 60f) }
        assertEquals("контроль: без паузи шип мав убити", RunEngine.Phase.DEAD, live.phase)

        val frozen = RunEngine(RunEngine.Config(), seed = 1L).apply { debugFrozen = true }
        val angle0 = frozen.angle
        frozen.debugSpawnSpike()
        repeat(60 * 10) { frozen.update(1f / 60f) }

        assertEquals(RunEngine.Phase.RUN, frozen.phase)
        assertEquals(angle0, frozen.angle, 0f)
        assertEquals(1, frozen.entities.size)
        assertEquals(1f, frozen.entities[0].s, 0f)
    }

    /** Debug-розстановка: усе попереду м'яча і жодна пара на кільці не ближча за DEBUG_GAP. */
    @Test
    fun debugSpawnsNeverOverlap() {
        val e = RunEngine(RunEngine.Config(), seed = 1L).apply { debugFrozen = true }

        var placed = 0
        while (placed < 100) {
            val ok = if (placed % 2 == 0) e.debugSpawnSpike() else e.debugSpawnBoost(Boost.MAGNET)
            if (!ok) break
            placed++
        }

        assertTrue("місце мало скінчитись, поставлено $placed", placed in 2 until 100)
        assertEquals(placed, e.entities.size)

        for (x in e.entities) {
            val rel = angDiff(x.a, e.angle)
            assertTrue("rel=$rel поза вікном", rel in 29.9f..170.1f)

            for (y in e.entities) if (x !== y && x.ring == y.ring) {
                val arc = abs(angDiff(x.a, y.a)) * (PI / 180.0).toFloat() * e.ringR[x.ring]
                assertTrue("дуга $arc між сусідами", arc >= RunEngine.DEBUG_GAP * 0.9f)
            }
        }
    }

    /**
     * Debug-перемикач третьої орбіти: вмикається одразу (без 30-ї секунди),
     * вимикається назад — кільця сходяться, третє гасне, його сутності
     * зникають, а м'яч із нього переходить на друге.
     */
    @Test
    fun debugOrbit3TogglesInPlace() {
        val e = RunEngine(RunEngine.Config(), seed = 1L).apply { debugFrozen = true }

        e.debugSetOrbit3(true)
        assertEquals(3, e.ringCount)
        repeat(60 * 5) { e.update(1f / 60f) }
        assertArrayEquals(RunEngine.LAYOUT_3, e.ringR, 0.01f)
        assertEquals(1f, e.ring3Alpha, 0f)

        e.tap(); e.tap()
        assertEquals(2, e.ringIndex)
        e.debugSpawnSpike()                          // лягає на кільце м'яча — третє
        assertEquals(2, e.entities.single().ring)

        e.debugSetOrbit3(false)
        assertEquals(2, e.ringCount)
        assertEquals(1, e.ringIndex)
        assertTrue(e.entities.isEmpty())

        repeat(60 * 5) { e.update(1f / 60f) }
        assertArrayEquals(RunEngine.LAYOUT_2, e.ringR, 0.01f)
        assertEquals(0f, e.ring3Alpha, 0f)

        e.tap()                                      // на краю — розворот, не вихід за кільця
        assertEquals(0, e.ringIndex)
    }

    /** Вимкнув debug — правило «з 30-ї секунди» не вмикає орбіту назад. */
    @Test
    fun debugOrbit3OffOutlivesThirtySecondRule() {
        val on  = survive(RunEngine(RunEngine.Config(orbit3 = true), seed = 7L), seconds = 31f)
        assertEquals("контроль: без debug на 31-й секунді орбіт три", 3, on.ringCount)

        val off = RunEngine(RunEngine.Config(orbit3 = true), seed = 7L).apply { debugSetOrbit3(false) }
        survive(off, seconds = 31f)
        assertEquals(2, off.ringCount)
    }

    /**
     * Геми рану: bank() віддає рівно те, що гравець бачив (RunResult, HUD), і
     * рівно один раз. upValue дає дробові геми — саме там round і floor
     * розходились би.
     */
    @Test
    fun bankPaysOnceExactlyWhatWasShown() {
        val e = survive(RunEngine(RunEngine.Config(orbit3 = true, upValue = 1), seed = 7L), seconds = 31f)
        assertTrue("контроль: за 31 с мали набратись геми, є ${e.gemsRun}", e.gemsRun >= 1f)
        assertTrue("контроль: геми мали бути дробові, є ${e.gemsRun}", e.gemsRun % 1f != 0f)

        killNow(e)
        val shown = e.gemsCollected
        assertEquals(e.buildResult().gems, shown)

        assertEquals(shown, e.bank())
        assertEquals(0, e.bank())
        assertEquals("сума рану лишається на екрані після банку", shown, e.gemsCollected)
    }

    /** Ревайв після банку: старі геми вже в балансі, лічильник рану починає з нуля. */
    @Test
    fun reviveAfterBankStartsFromZero() {
        val e = survive(RunEngine(RunEngine.Config(orbit3 = true), seed = 7L), seconds = 31f)
        killNow(e)
        assertTrue(e.bank() > 0)

        e.revive()
        assertEquals(RunEngine.Phase.RUN, e.phase)
        assertEquals(0, e.gemsCollected)
        assertEquals(0f, e.gemsRun, 0f)
    }

    // ------------------------------------------------------------------------
    // Іскра комбо
    // ------------------------------------------------------------------------

    /** Іскра є лише в шипа, висить за ORB_OFF попереду нього; в гема й буста її немає. */
    @Test
    fun sparkHangsAheadOfSpikeOnly() {
        val e = RunEngine(RunEngine.Config(), seed = 1L).apply { debugFrozen = true }
        check(e.debugSpawnSpike()); check(e.debugSpawnBoost(Boost.MAGNET))
        val spike = e.entities.single { it.kind == RunEngine.Kind.SPIKE }
        val boost = e.entities.single { it.kind == RunEngine.Kind.BOOST }

        val expected = norm(spike.a - RunEngine.ORB_OFF / spike.rr * RAD_TO_DEG)
        assertEquals(expected, e.sparkAngle(spike)!!, 0.001f)
        assertEquals(null, e.sparkAngle(boost))

        // Гем — через mercy-доріжку: шість гемів на 1-й секунді
        val m = RunEngine(RunEngine.Config(mercy = true), seed = 1L)
        repeat(70) { m.update(1f / 60f) }
        val gem = m.entities.first { it.kind == RunEngine.Kind.GEM }
        assertEquals(null, m.sparkAngle(gem))
    }

    /**
     * Шип на своєму кільці: іскру спіймано по дорозі (комбо +1, слухач отримав
     * кут ІСКРИ, вона погасла), а далі шип убиває — ризик той самий, що був.
     */
    @Test
    fun sparkIsCaughtBeforeSpikeKills() {
        val e = RunEngine(RunEngine.Config(), seed = 1L)
        var caught = 0
        var caughtAt = Float.NaN
        e.listener = object : RunEngine.Listener {
            override fun onNearMiss(e: RunEngine.Entity, sparkAngle: Float, bonus: Int) { caught++; caughtAt = sparkAngle }
        }
        check(e.debugSpawnSpike())
        val spike  = e.entities.single()
        val sparkA = e.sparkAngle(spike)!!

        var comboSeen = 0f
        var t = 0f
        while (e.phase == RunEngine.Phase.RUN && t < 5f) {
            e.update(1f / 60f); t += 1f / 60f
            comboSeen = maxOf(comboSeen, e.combo)
        }

        assertEquals(RunEngine.Phase.DEAD, e.phase)
        assertEquals(1, caught)
        assertEquals(sparkA, caughtAt, 0.001f)
        assertEquals(1f, comboSeen, 0f)
        assertEquals(1, e.buildResult().nearMisses)
        assertEquals("іскра погасла", null, e.sparkAngle(spike))
    }

    /** Торкнувся іскри — тапнув: комбо є, шип не вбив, бо м'яч уже летить геть. */
    @Test
    fun tapRightAfterSparkEscapesSpike() {
        val e = RunEngine(RunEngine.Config(), seed = 1L)
        check(e.debugSpawnSpike())

        var tapped = false
        var t = 0f
        while (t < 2f) {
            e.update(1f / 60f); t += 1f / 60f
            if (!tapped && e.buildResult().nearMisses == 1) { e.tap(); tapped = true }
        }

        assertTrue("іскру мало бути спіймано", tapped)
        assertEquals(RunEngine.Phase.RUN, e.phase)
        assertEquals("комбо тримається comboWin = 4 с", 1f, e.combo, 0f)
    }

    /**
     * Стрибок НА кільце шипа рівно в іскру: радіус ще лерпиться (30 з 45 після
     * першого кадру), а іскра вже зарахована. Тап назад одразу — м'яч цілий.
     */
    @Test
    fun jumpingOntoSparkCatchesIt() {
        val e = RunEngine(RunEngine.Config(), seed = 1L)
        check(e.debugSpawnSpike())          // на кільці 0, де м'яч
        e.tap()                             // м'яч на кільце 1, шип лишився на 0
        val spike   = e.entities.single()
        val pickDeg = RunEngine.ORB_PICK / spike.rr * RAD_TO_DEG

        var jumped = false
        var t = 0f
        while (t < 2f) {
            val sparkA = e.sparkAngle(spike)
            if (!jumped && sparkA != null && angDiff(sparkA, e.angle) < pickDeg) { e.tap(); jumped = true }
            e.update(1f / 60f); t += 1f / 60f
            if (jumped && e.ringIndex == 0 && e.buildResult().nearMisses == 1) e.tap()   // спіймав — назад
        }

        assertTrue(jumped)
        assertEquals(RunEngine.Phase.RUN, e.phase)
        assertEquals(1, e.buildResult().nearMisses)
    }

    /** DEBUG · EZ COMBO: прохід повз шип на сусідньому кільці зараховує без іскри; без прапорця — ні. */
    @Test
    fun ezComboCountsPassOnNeighbourRing() {
        fun pass(ez: Boolean): RunEngine {
            val e = RunEngine(RunEngine.Config(), seed = 1L).apply { debugEzCombo = ez }
            check(e.debugSpawnSpike())      // на кільці м'яча
            e.tap()                         // м'яч на сусіднє: шип пройде повз на 130
            repeat(90) { e.update(1f / 60f) }
            check(e.phase == RunEngine.Phase.RUN) { "контроль: із сусіднього кільця шип не б'є" }
            return e
        }

        assertEquals(0, pass(ez = false).buildResult().nearMisses)
        assertEquals(1, pass(ez = true).buildResult().nearMisses)
    }

    // ------------------------------------------------------------------------
    // Вікно комбо
    // ------------------------------------------------------------------------

    /**
     * Вікно вийшло — комбо гасне в нуль ОДРАЗУ, не x3 → x2. Дві іскри → combo 2,
     * далі бот тікає від усього (шипи, геми — гем поновив би вікно), і від
     * останнього поновлення до нуля минає рівно comboWin без проміжної 1.
     */
    @Test
    fun comboExpiresToZeroNotStepDown() {
        val e = withCombo(seed = 1L)
        catchOneMore(e)
        assertTrue("контроль: combo ≥ 2, є ${e.combo}", e.combo >= 2f)

        // Скільки вікна лишилось на старті — стільки й має минути до нуля;
        // поновлення (іскра, гем) переставляє відлік
        var t = 0f
        var sinceT    = 0f
        var remaining = e.comboFrac * e.comboWin
        var prevFrac  = e.comboFrac
        var sawOne    = false
        while (e.combo > 0f && t < e.comboWin + 3f) {
            dodgeWide(e)
            e.update(1f / 60f); t += 1f / 60f
            if (e.comboFrac > prevFrac) { sinceT = t; remaining = e.comboFrac * e.comboWin }
            if (e.combo == 1f) sawOne = true
            prevFrac = e.comboFrac
        }

        assertEquals(RunEngine.Phase.RUN, e.phase)
        assertEquals(0f, e.combo, 0f)
        assertEquals(0f, e.comboFrac, 0f)
        assertTrue("сходинки x2 → x1 бути не мало", !sawOne)
        assertEquals("до нуля минає рівно залишок вікна", remaining, t - sinceT, 1.5f / 60f)
    }

    /** Гем при живому комбо повертає вікно на 100 %, не додає частку. */
    @Test
    fun gemRefillsComboWindowFully() {
        val e = withCombo(seed = 1L, mercy = true)     // mercy: доріжка гемів на кільці 0 з 1-ї секунди
        repeat(60) { e.update(1f / 60f) }              // вікно частково стануло
        val before = e.comboFrac
        assertTrue("контроль: вікно мало стати меншим за 1, є $before", before < 0.9f)

        val gems0 = e.gemCount
        var t = 0f
        while (e.gemCount == gems0 && t < 4f) { e.update(1f / 60f); t += 1f / 60f }
        check(e.gemCount > gems0) { "гем не підібрано за 4 с" }

        assertEquals("одразу після гема — повне вікно", 1f, e.comboFrac, 1f / 60f / e.comboWin + 0.001f)
        assertTrue("комбо живе", e.combo > 0f)
    }

    /**
     * Щит з'їв удар шипа — комбо і його вікно не чіпає. Порівнюємо з кадром
     * ПЕРЕД ударом: іскру цього ж шипа м'яч ловить на три кадри раніше, і вона
     * законно ставить вікно на 100 %.
     */
    @Test
    fun shieldHitKeepsCombo() {
        val e = withCombo(seed = 1L, upShield = 1)
        assertEquals(1, e.shield)

        // Шип на кільці м'яча — і летимо в нього, не тапаючи
        check(e.debugSpawnSpike())
        var comboPrev = e.combo
        var fracPrev  = e.comboFrac
        var t = 0f
        while (e.shield == 1 && t < 3f) {
            comboPrev = e.combo; fracPrev = e.comboFrac
            e.update(1f / 60f); t += 1f / 60f
        }

        assertEquals("щит мав спрацювати", 0, e.shield)
        assertEquals(RunEngine.Phase.RUN, e.phase)
        assertTrue("комбо було", comboPrev > 0f)
        assertEquals(comboPrev, e.combo, 0f)
        val dtFrac = 1f / 60f / e.comboWin
        assertEquals("вікно за кадр удару лише стануло на один крок", fracPrev - dtFrac, e.comboFrac, 0.001f)
    }

    /**
     * Ухиляння з ЗАПАСОМ: тап, коли шип АБО гем на своєму кільці ближче за 45°
     * попереду (гем поновлює вікно комбо — теж «подія»).
     * 45° на внутрішньому кільці — 150 юнітів, тобто тап за 98 до іскри, поза
     * її вікном ±32: бот не ловить іскор, лише тікає (бот survive() із 25°
     * тапнув би за 31 до іскри — і спіймав би її на першому кадрі лерпу).
     */
    private fun dodgeWide(e: RunEngine) {
        val danger = e.entities.any {
            it.kind != RunEngine.Kind.BOOST && it.ring == e.ringIndex && angDiff(it.a, e.angle) in 0f..45f
        }
        if (danger) e.tap()
    }

    /** Ще один debug-шип на кільці м'яча: спіймати його іскру і тапнути геть. */
    private fun catchOneMore(e: RunEngine) {
        val before = e.buildResult().nearMisses
        check(e.debugSpawnSpike())
        var t = 0f
        while (e.buildResult().nearMisses == before && t < 3f) { e.update(1f / 60f); t += 1f / 60f }
        check(e.buildResult().nearMisses > before) { "іскру не спіймано" }
        e.tap()
        repeat(6) { e.update(1f / 60f) }
        check(e.phase == RunEngine.Phase.RUN)
    }

    /** Ран, у якому щойно спіймано одну іскру: м'яч тапнув геть і живий. */
    private fun withCombo(seed: Long, mercy: Boolean = false, upShield: Int = 0): RunEngine {
        val e = RunEngine(RunEngine.Config(mercy = mercy, upShield = upShield), seed)
        check(e.debugSpawnSpike())
        var t = 0f
        while (e.buildResult().nearMisses == 0 && t < 3f) { e.update(1f / 60f); t += 1f / 60f }
        check(e.buildResult().nearMisses == 1) { "іскру не спіймано" }
        e.tap()
        repeat(6) { e.update(1f / 60f) }               // відлетів від шипа
        check(e.phase == RunEngine.Phase.RUN && e.combo == 1f)
        return e
    }

    /** Шип просто перед м'ячем — і чекати, поки вб'є. */
    private fun killNow(e: RunEngine) {
        check(e.debugSpawnSpike()) { "немає місця під шип" }
        var t = 0f
        while (e.phase == RunEngine.Phase.RUN && t < 5f) { e.update(1f / 60f); t += 1f / 60f }
        check(e.phase == RunEngine.Phase.DEAD) { "шип не вбив за 5 с" }
    }

    /**
     * Ран, що доживає до [seconds]: бот тапає, коли шип на його кільці ближче
     * за 25° попереду. Не вижив — тест про інше, тож падаємо одразу.
     */
    private fun survive(e: RunEngine, seconds: Float): RunEngine {
        while (e.time < seconds) {
            val danger = e.entities.any {
                it.kind == RunEngine.Kind.SPIKE && it.ring == e.ringIndex && angDiff(it.a, e.angle) in 0f..25f
            }
            if (danger) e.tap()
            e.update(1f / 60f)
            check(e.phase == RunEngine.Phase.RUN) { "бот загинув на t=${e.time}" }
        }
        return e
    }

    private val RAD_TO_DEG = (180.0 / PI).toFloat()
    private fun norm(a: Float): Float { var x = a % 360f; if (x < 0f) x += 360f; return x }
    private fun angDiff(a: Float, b: Float): Float = ((a - b) % 360f + 540f) % 360f - 180f
}
