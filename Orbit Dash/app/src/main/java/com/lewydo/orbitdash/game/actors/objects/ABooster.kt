package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.engine.RunEngine
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  ABooster — підбираний буст. На відміну від гема й спайка, колір НЕ з теми:
//  кожен тип має власний, і це навмисно — гравець мусить упізнавати буст за
//  кольором ще до того, як прочитає назву, у будь-якій палітрі.
// ─────────────────────────────────────────────────────────────────────────────
class ABooster(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val GLOW_SIZE = 100f

        private const val PULSE_SPEED = 6f
        private const val PULSE_AMP   = 0.12f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsMsdf.glow).apply { color.a = 0.90f }
    private val aHex   = Image(gdxGame.assetsMsdf.boost_hex)
    private val aIcon  = Image(gdxGame.assetsAll.boost_icon_magnet)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private var pulseT = 0f

    /** Тип буста — задає колір. Ставиться при видачі з пулу. */
    var boost: RunEngine.Boost = RunEngine.Boost.MAGNET
        set(value) {
            field = value

            applyColor()
            applyIcon()
        }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addHex()
        addIcon()

        applyColor()
        applyIcon()
    }

    override fun act(delta: Float) {
        super.act(delta)

        // Пульсація glow — буст «дихає», щоб виділятись серед статичних гемів
        pulseT += delta * PULSE_SPEED
        val k = 1f + PULSE_AMP * sin(pulseT)
        aGlow.setScale(k)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE, GLOW_SIZE); center() }
        aGlow.setOrigin(Align.center)
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

    private fun applyColor() {
        val c = GameColor.Boost.of(boost)
        aGlow.setColorRGB(c)
        aHex.setColorRGB(c)
        aIcon.setColorRGB(c)
    }

    private fun applyIcon() {
        aIcon.drawable = TextureRegionDrawable(getIcon())
    }

    // ------------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------------
    private fun getIcon() = when (boost) {
        RunEngine.Boost.SHIELD -> gdxGame.assetsAll.boost_icon_shield
        RunEngine.Boost.MAGNET -> gdxGame.assetsAll.boost_icon_magnet
        RunEngine.Boost.FRENZY -> gdxGame.assetsAll.boost_icon_gem_x2
        RunEngine.Boost.SLOW   -> gdxGame.assetsAll.boost_icon_slow_mo
        RunEngine.Boost.PULSE  -> gdxGame.assetsAll.boost_icon_pulse
    }

}
