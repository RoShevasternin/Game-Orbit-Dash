package com.lewydo.orbitdash.game.actors.objects.decor

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class ABallDecor(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow = Image(gdxGame.assetsMsdf.glow).apply { color.a = 0.90f }
    private val aBall = AMsdfImage(screen, gdxGame.assetsMsdf.circle)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addBall()

        syncTheme()
    }

    private var themeVersion = -1

    override fun act(delta: Float) {
        super.act(delta)
        if (themeVersion != ThemeManager.version) {
            themeVersion = ThemeManager.version
            syncTheme()
        }
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent == null) return
        aGlow.setSizeScaled(100f, 100f)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        aGlow.setSizeScaled(100f, 100f)
        add(aGlow) { center() }
        aGlow.debug()
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