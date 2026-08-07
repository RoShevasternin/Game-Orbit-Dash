package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class AGem(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

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

    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()

        aDiamond.setOrigin(Align.center)
    }

    private fun syncTheme() {
        aGlow.color.set(ThemeManager.current.gem).apply { a = aGlow.color.a }
        aDiamond.color.set(ThemeManager.current.gem).apply { a = aDiamond.color.a }
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent != null) aGlow.setSizeScaled(100f, 100f)
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

}