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
import com.lewydo.orbitdash.game.utils.theme.ThemeSync

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
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addBall()
        addPoint()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        setOrigin(Align.center)
        // Розміри дітей тримає реєстр групи, margin'и лейаут множить сам —
        // тут повторювати нічого.
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addBall() {
        add(aBall) { fillParent() }

        // keepScaled {
        //     aBg.radius      = RADIUS.toActual
        //     aBg.strokeWidth = STROKE.toActual
        // }
    }

    private fun addPoint() {
        add(aPoint) {
            size(POINT_SIZE)
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