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
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addGem()
        addPoint()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        // Крутиться САМ ромб, а не група: біла крапка стоїть у центрі й має
        // лишатись нерухомою. Origin — тут, а не в sizeChanged(): aGem має
        // fillParent(), його розмір відомий лише ПІСЛЯ layout у super.act().
        aGem.setOrigin(Align.center)
        aGem.rotation = (aGem.rotation + SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addGem() {
        add(aGem) { fillParent() }
        aGem.setOrigin(Align.center)
    }

    private fun addPoint() {
        add(aPoint) { size(POINT_SIZE); center() }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.gem)
        aGem.setColorRGB(ThemeManager.current.gem)
    }

}