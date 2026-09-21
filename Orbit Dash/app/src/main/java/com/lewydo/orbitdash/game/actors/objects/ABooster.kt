package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.ui.ABoostHex
import com.lewydo.orbitdash.game.content.info
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import kotlin.math.PI
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  ABooster — підбираний буст. На відміну від гема й спайка, колір НЕ з теми:
//  кожен тип має власний, і це навмисно — гравець мусить упізнавати буст за
//  кольором ще до того, як прочитає назву, у будь-якій палітрі.
//
//  Сама фігура — ABoostHex, та сама цеглинка, що й у HUD (APanelBooster):
//  предмет на полі й індикатор нагорі мусять бути впізнавано одним і тим же.
//  Тут до неї додаються два ореоли з пульсом — це деталь ПОЛЯ, у HUD вона
//  була б шумом.
// ─────────────────────────────────────────────────────────────────────────────
class ABooster(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        // Figma boost_hex → glow: два плоскі кола, без блюру
        private const val GLOW_1_SIZE = 50f      // v1
        private const val GLOW_1_ALPHA = 0.20f
        private const val GLOW_2_SIZE = 70f      // v2
        private const val GLOW_2_ALPHA = 0.10f

        private const val PULSE_SPEED = 6f
        private const val PULSE_AMP = 0.12f

        /** Зовнішнє кільце відстає на чверть періоду — хвиля йде зсередини назовні. 0 = у фазі. */
        private const val PULSE_LAG = (PI / 2).toFloat()

        /** Наскільки кільце гасне на піку розширення. 0 = лише масштаб, без згасання. */
        private const val PULSE_FADE = 0.4f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 35f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow1    = Image(gdxGame.assetsMsdf.circle).apply { color.a = GLOW_1_ALPHA }
    private val aGlow2    = Image(gdxGame.assetsMsdf.circle).apply { color.a = GLOW_2_ALPHA }
    private val aBoostHex = ABoostHex(screen)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private var pulseT = 0f

    var boost: RunEngine.Boost = RunEngine.Boost.MAGNET
        set(value) {
            field = value

            applyInfo()
        }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addBoostHex()

        applyInfo()
    }

    override fun act(delta: Float) {
        super.act(delta)

        // Хвиля зсередини назовні: внутрішнє кільце веде, зовнішнє наздоганяє
        // на чверть періоду — буст «розходиться», а не просто дихає
        pulseT += delta * PULSE_SPEED
        pulse(aGlow2, GLOW_2_ALPHA, sin(pulseT))
        pulse(aGlow1, GLOW_1_ALPHA, sin(pulseT - PULSE_LAG))
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow2) { size(GLOW_2_SIZE); center() }
        add(aGlow1) { size(GLOW_1_SIZE); center() }
        aGlow1.setOrigin(Align.center)
        aGlow2.setOrigin(Align.center)
    }

    private fun addBoostHex() {
        add(aBoostHex) { fillParent() }
    }

    // ------------------------------------------------------------------------
    // Apply
    // ------------------------------------------------------------------------

    /** Ореоли — тут, фігура з іконкою — у цеглинці: один запит до каталогу на двох. */
    private fun applyInfo() {
        val color = boost.info.color

        aGlow1.setColorRGB(color)
        aGlow2.setColorRGB(color)

        aBoostHex.boost = boost
    }

    /**
     * Одна фаза кільця. p ∈ [-1, 1]: на +1 кільце найширше і найтьмяніше,
     * на -1 — стиснуте і найяскравіше. Спокій (p = 0) — точні числа з макета.
     */
    private fun pulse(ring: Image, baseAlpha: Float, p: Float) {
        ring.setScale(1f + PULSE_AMP * p)
        ring.color.a = baseAlpha * (1f - PULSE_FADE * p)
    }

}

