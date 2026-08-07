package com.lewydo.orbitdash.game.actors.particleEffect

import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.ParticleEffectPool
import com.badlogic.gdx.scenes.scene2d.Group
import com.lewydo.orbitdash.game.utils.currentTimeMinus

// ─────────────────────────────────────────────────────────────────────────────
// AParticleEffectPool + «губернатор» (як particle budget у Unity/Unreal):
//
//   ПРОБЛЕМА (заміряно бісекцією): спам кліків → купа одночасних ефектів →
//   overdraw (великі напівпрозорі квади) → draw 118→265, fps 61→34. GPU
//   зафарбовує ті самі пікселі десятки разів.
//
//   РІШЕННЯ (стандарт движків — max instances + spawn rate):
//   1. maxActive — стеля ОДНОЧАСНИХ ефектів цього пулу (дефолт 3).
//      Понад стелю спавн ІГНОРУЄТЬСЯ: при спамі гравець бачить безперервний
//      феєрверк у будь-якому разі — 3 чи 13 ефектів на око не відрізнити,
//      а GPU відрізняє дуже добре.
//   2. minSpawnIntervalMs — мін. пауза між спавнами (дефолт 60мс):
//      2 пальці × швидкі кліки не породжують ефект на КОЖЕН тап.
//
//   Підбирай per-пул: дрібний ефект (зірочки) → maxActive 4-5;
//   великий (wave на всю кнопку) → maxActive 1-2, інтервал 150мс.
// ─────────────────────────────────────────────────────────────────────────────

class AParticleEffectPool(
    sourceEffect   : ParticleEffect,
    initialCapacity: Int = 2,
    maxSize        : Int = 16,
    /** Стеля одночасно АКТИВНИХ ефектів (понад — спавн ігнорується). */
    var maxActive: Int = 3,
    /** Мінімальний інтервал між спавнами, мс. */
    var minSpawnIntervalMs: Long = 60L,
) {

    // ------------------------------------------------------------------------
    // Pool
    // ------------------------------------------------------------------------

    private val pool = ParticleEffectPool(sourceEffect, initialCapacity, maxSize)

    private var activeCount = 0
    private var lastSpawnMs = 0L

    // ------------------------------------------------------------------------
    // Spawn
    // ------------------------------------------------------------------------

    fun spawn(
        parent : Group,
        x      : Float,
        y      : Float,
        setup  : (AParticleEffectActor.() -> Unit)? = null,
    ) {
        // ── Губернатор: бюджет активних + частота спавну ──
        val now = System.currentTimeMillis()
        if (activeCount >= maxActive) return
        if (now - lastSpawnMs < minSpawnIntervalMs) return
        lastSpawnMs = now

        val pooled = pool.obtain()
        val actor  = PooledActor(pooled)
        activeCount++

        setup?.invoke(actor)
        actor.setPosition(x, y)
        parent.addActor(actor)
        actor.start(true)
    }

    // ------------------------------------------------------------------------
    // Pooled Actor
    // ------------------------------------------------------------------------

    private inner class PooledActor(
        private val pooled: ParticleEffectPool.PooledEffect,
    ) : AParticleEffectActor(pooled) {

        override fun act(delta: Float) {
            super.act(delta)
            if (pooled.isComplete) {
                pool.free(pooled)
                activeCount = (activeCount - 1).coerceAtLeast(0)
                remove()
            }
        }
    }

}