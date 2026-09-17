package com.lewydo.orbitdash.game.actors.objects.decor

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync

class ABallDecor(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val GLOW_SIZE = 100f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow = Image(gdxGame.assetsMsdf.glow).apply { color.a = 0.90f }
    private val aBall = AMsdfImage(screen, gdxGame.assetsMsdf.circle)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addBall()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addBall() {
        add(aBall) { fillParent() }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.player)
        aBall.setColorRGB(ThemeManager.current.player)
    }

}