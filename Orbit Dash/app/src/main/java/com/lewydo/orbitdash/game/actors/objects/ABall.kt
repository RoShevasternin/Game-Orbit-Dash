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

    companion object {
        private const val GLOW_SIZE  = 100f
        private const val POINT_SIZE = 10f

        private const val POINT_PADDING = 10f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsMsdf.glow).apply { color.a = 0.90f }
    private val aBall  = Image(gdxGame.assetsMsdf.circle)
    private val aPoint = Image(gdxGame.assetsMsdf.circle).apply { color = GameColor.white_90 }

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
        // Розміри й відступи дітей — у дизайн-одиницях (scaled()), лейаут
        // перераховує їх сам при кожному resolve. Тут повторювати нічого.
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { scaled(); size(GLOW_SIZE, GLOW_SIZE); center() }
    }

    private fun addBall() {
        add(aBall) { fillParent() }
    }

    private fun addPoint() {
        add(aPoint) {
            scaled()
            size(POINT_SIZE, POINT_SIZE)
            startToStart(margin = POINT_PADDING); topToTop(margin = POINT_PADDING)
        }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.player)
        aBall.setColorRGB(ThemeManager.current.player)
    }

}