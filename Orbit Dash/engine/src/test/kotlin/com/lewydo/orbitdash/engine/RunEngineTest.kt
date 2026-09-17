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

    private fun angDiff(a: Float, b: Float): Float = ((a - b) % 360f + 540f) % 360f - 180f
}
