package com.lewydo.orbitdash.game.actors.panel.boost

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.engine.RunEngine
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class AShieldPip(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlowImg  = Image(gdxGame.assetsAll.shield_pip).apply { color.a = 0.30f }
    private val aPipImg   = Image(gdxGame.assetsAll.shield_pip)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlowImg()
        addPipImg()

        syncTheme() // ← стартовий колір
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
        if (aGlowImg.parent == null) return
        aGlowImg.setSizeScaled(50f, 35f)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlowImg() {
        aGlowImg.setSizeScaled(50f, 35f)
        add(aGlowImg) { center() }
    }

    private fun addPipImg() {
        add(aPipImg) { fillParent() }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        val colorPlayer = ThemeManager.current.player
        aGlowImg.color.set(colorPlayer, aGlowImg.color.a)
        aPipImg.color.set(colorPlayer)
    }

}
