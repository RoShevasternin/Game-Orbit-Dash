package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class ABall(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsLoader.item_glow)
    private val aBall  = Image(gdxGame.assetsLoader.circle)
    private val aPoint = Image(gdxGame.assetsLoader.circle).apply { color = GameColor.white_90 }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addBall()
        addPoint()

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
        setOrigin(Align.center)
        if (aGlow.parent == null) return
        aGlow.setSizeScaled(100f, 100f)
        aPoint.setSizeScaled(10f, 10f)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        aGlow.setSizeScaled(100f, 100f)
        add(aGlow) { center() }
    }

    private fun addBall() {
        add(aBall) { fillParent() }
    }

    private fun addPoint() {
        val padding = 10f.toActual
        aPoint.setSizeScaled(10f, 10f)
        add(aPoint) { startToStart(margin = padding); topToTop(margin = padding) }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.player)
        aBall.setColorRGB(ThemeManager.current.player)
    }

}