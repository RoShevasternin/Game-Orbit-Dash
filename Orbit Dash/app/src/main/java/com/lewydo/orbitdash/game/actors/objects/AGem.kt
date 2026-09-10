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

class AGem(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val GLOW_SIZE  = 100f
        private const val POINT_SIZE = 10f

        /** Оберт ромба. Повільніше за шип (180): гем має читатись, а не миготіти. */
        private const val SPIN_SPEED = 120f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow    = Image(gdxGame.assetsMsdf.glow).apply { color.a = 0.90f }
    private val aGem     = Image(gdxGame.assetsMsdf.gem)
    private val aPoint   = Image(gdxGame.assetsMsdf.circle).apply { color = GameColor.white_90 }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addGem()
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

        // Крутиться САМ ромб, а не група: біла крапка стоїть у центрі й має
        // лишатись нерухомою. Origin — тут, а не в sizeChanged(): aGem має
        // fillParent(), його розмір відомий лише ПІСЛЯ layout у super.act().
        aGem.setOrigin(Align.center)
        aGem.rotation += SPIN_SPEED * delta
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent == null) return
        aGlow.setSizeScaled(GLOW_SIZE, GLOW_SIZE)
        aPoint.setSizeScaled(POINT_SIZE, POINT_SIZE)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        aGlow.setSizeScaled(GLOW_SIZE, GLOW_SIZE)
        add(aGlow) { center() }
    }

    private fun addGem() {
        add(aGem) { fillParent() }
        aGem.setOrigin(Align.center)
    }

    private fun addPoint() {
        aPoint.setSizeScaled(POINT_SIZE, POINT_SIZE)
        add(aPoint) { center() }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.gem)
        aGem.setColorRGB(ThemeManager.current.gem)
    }

}