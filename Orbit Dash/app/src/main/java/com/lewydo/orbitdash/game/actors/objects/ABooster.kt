package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
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
// ─────────────────────────────────────────────────────────────────────────────
class ABooster(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        // Figma boost_hex → glow: два плоскі кола, без блюру
        private const val GLOW_1_SIZE  = 50f      // v1
        private const val GLOW_1_ALPHA = 0.20f
        private const val GLOW_2_SIZE  = 70f      // v2
        private const val GLOW_2_ALPHA = 0.10f

        private const val PULSE_SPEED = 6f
        private const val PULSE_AMP   = 0.12f
        /** Зовнішнє кільце відстає на чверть періоду — хвиля йде зсередини назовні. 0 = у фазі. */
        private const val PULSE_LAG   = (PI / 2).toFloat()
        /** Наскільки кільце гасне на піку розширення. 0 = лише масштаб, без згасання. */
        private const val PULSE_FADE  = 0.4f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 35f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow1  = Image(gdxGame.assetsMsdf.circle).apply { color.a = GLOW_1_ALPHA }
    private val aGlow2  = Image(gdxGame.assetsMsdf.circle).apply { color.a = GLOW_2_ALPHA }
    private val aHex    = Image(gdxGame.assetsMsdf.boost_hex)
    private val aIcon   = Image(RunEngine.Boost.MAGNET.info.icon)

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
        addHex()
        addIcon()

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
        add(aGlow2) { size(GLOW_2_SIZE, GLOW_2_SIZE); center() }
        add(aGlow1) { size(GLOW_1_SIZE, GLOW_1_SIZE); center() }
        aGlow1.setOrigin(Align.center)
        aGlow2.setOrigin(Align.center)
    }

    private fun addHex() {
        add(aHex) { fillParent() }
        aHex.setOrigin(Align.center)
    }

    private fun addIcon() {
        add(aIcon) { fillParent() }
        aIcon.setOrigin(Align.center)
    }

    // ------------------------------------------------------------------------
    // Apply
    // ------------------------------------------------------------------------

    /** Один запит до каталогу: колір і іконка приходять разом, одним записом. */
    private fun applyInfo() {
        val info = boost.info

        aGlow1.setColorRGB(info.color)
        aGlow2.setColorRGB(info.color)
        aHex.setColorRGB(info.color)
        aIcon.setColorRGB(info.color)

        aIcon.drawable = TextureRegionDrawable(info.icon)
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
