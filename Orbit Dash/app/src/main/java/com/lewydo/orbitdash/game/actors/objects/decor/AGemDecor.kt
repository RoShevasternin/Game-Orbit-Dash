package com.lewydo.orbitdash.game.actors.objects.decor

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class AGemDecor(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow    = Image(gdxGame.assetsLoader.item_glow)
    private val aDiamond = Image(gdxGame.assetsLoader.gem)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    var spin: Float
        get() = aDiamond.rotation
        set(value) { aDiamond.rotation = value }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addDiamond()

        syncTheme()
    }

    private var themeVersion = -1

    override fun act(delta: Float) {
        super.act(delta)
        if (themeVersion != ThemeManager.version) {
            themeVersion = ThemeManager.version
            syncTheme()
        }

        aDiamond.setOrigin(Align.center)
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