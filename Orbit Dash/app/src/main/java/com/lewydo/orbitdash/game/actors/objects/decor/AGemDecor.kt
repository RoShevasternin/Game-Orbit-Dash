package com.lewydo.orbitdash.game.actors.objects.decor

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync
import javax.microedition.khronos.opengles.GL

class AGemDecor(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val GLOW_SIZE = 100f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow    = Image(gdxGame.assetsMsdf.glow).apply { color.a = 0.90f }
    private val aDiamond = Image(gdxGame.assetsMsdf.gem)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    var spin: Float
        get() = aDiamond.rotation
        set(value) { aDiamond.rotation = value }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addDiamond()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        aDiamond.setOrigin(Align.center)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE, GLOW_SIZE); center() }
    }

    private fun addDiamond() {
        add(aDiamond) { fillParent() }
        aDiamond.setOrigin(Align.center)
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.gem)
        aDiamond.setColorRGB(ThemeManager.current.gem)
    }

}