package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class ABall(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    private val aGlow = Image(gdxGame.assetsLoader.item_glow)
    private val aBall = Image(gdxGame.assetsLoader.ball)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addBall()

        syncTheme()
    }

    /**
     * Колір не зберігається в акторі — щокадру читається з ThemeManager.current.
     * Тому зміна скіна перефарбовує кульку сама, ще й з плавним лерпом.
     * Color.set() — нуль алокацій.
     */
    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()
    }

    private fun syncTheme() {
        aGlow.color.set(ThemeManager.current.player).apply { a = aGlow.color.a }
        aBall.color.set(ThemeManager.current.player).apply { a = aBall.color.a }
    }

    /** При анімації розміру групи glow має масштабуватись разом. */
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

    private fun addBall() {
        add(aBall) { fillParent() }
    }

}