package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.engine.RunEngine
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

// ─────────────────────────────────────────────────────────────────────────────
//  ABooster — підбираний буст. На відміну від гема й спайка, колір НЕ з теми:
//  кожен тип має власний, і це навмисно — гравець мусить упізнавати буст за
//  кольором ще до того, як прочитає назву, у будь-якій палітрі.
//
//  TEMP-АРТ: спрайта бустера немає, беремо кульку (ball) як нейтральну
//  «капсулу» + пульсацію. Коли з'явиться арт із символами всередині —
//  міняється aShape і, за потреби, додається іконка типу.
// ─────────────────────────────────────────────────────────────────────────────
class ABooster(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    companion object {
        /** Кольори з прототипу — та сама мова, що в демці. */
        private val COLOR_SHIELD = Color.valueOf("4dd9ff")
        private val COLOR_MAGNET = Color.valueOf("c07bff")
        private val COLOR_FRENZY = Color.valueOf("ffd54a")
        private val COLOR_SLOW   = Color.valueOf("ffffff")
        private val COLOR_PULSE  = Color.valueOf("ff9f2e")

        private const val PULSE_SPEED = 6f
        private const val PULSE_AMP   = 0.12f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsLoader.item_glow)
    private val aShape = Image(gdxGame.assetsLoader.ball)   // TEMP-арт

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private var pulseT = 0f

    /** Тип буста — задає колір. Ставиться при видачі з пулу. */
    var boost: RunEngine.Boost = RunEngine.Boost.MAGNET
        set(value) {
            field = value
            applyColor()
        }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addShape()

        applyColor()
    }

    override fun act(delta: Float) {
        super.act(delta)

        // Пульсація glow — буст «дихає», щоб виділятись серед статичних гемів
        pulseT += delta * PULSE_SPEED
        val k = 1f + PULSE_AMP * kotlin.math.sin(pulseT)
        aGlow.setScale(k)
    }

    private fun applyColor() {
        val c = when (boost) {
            RunEngine.Boost.SHIELD -> COLOR_SHIELD
            RunEngine.Boost.MAGNET -> COLOR_MAGNET
            RunEngine.Boost.FRENZY -> COLOR_FRENZY
            RunEngine.Boost.SLOW   -> COLOR_SLOW
            RunEngine.Boost.PULSE  -> COLOR_PULSE
        }
        aGlow.color.set(c).apply { a = aGlow.color.a }
        aShape.color.set(c).apply { a = aShape.color.a }
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent != null) aGlow.setSizeScaled(110f, 110f)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        aGlow.setSizeScaled(110f, 110f)
        aGlow.setOrigin(Align.center)
        add(aGlow) { center() }
    }

    private fun addShape() {
        add(aShape) { fillParent() }
        aShape.setOrigin(Align.center)
    }

}
