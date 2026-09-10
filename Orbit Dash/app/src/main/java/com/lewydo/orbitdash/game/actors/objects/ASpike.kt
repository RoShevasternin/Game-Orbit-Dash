package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

// ─────────────────────────────────────────────────────────────────────────────
//  ASpike — перешкода. Побудований за тим самим шаблоном, що AGem:
//  glow під фігурою + сама фігура, кольори щокадру з теми.
//
//  TEMP-АРТ: власного спрайта спайка в атласі ще немає, тому беремо гем,
//  фарбуємо в theme.spike і крутимо вдвічі швидше — силует читається як
//  «небезпека» за кольором і рухом. Коли з'явиться спрайт, міняється ОДИН
//  рядок (aShape) — код екрана й рушія не чіпається.
// ─────────────────────────────────────────────────────────────────────────────
class ASpike(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    companion object {
        /** Швидше за гем — рух сам по собі сигналить «не чіпай». */
        private const val SPIN_SPEED = 180f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsMsdf.glow)
    private val aShape = Image(gdxGame.assetsMsdf.gem)   // TEMP-арт

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addShape()

        syncTheme()
    }

    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()

        aShape.setOrigin(Align.center)
        aShape.rotation += SPIN_SPEED * delta
    }

    private fun syncTheme() {
        aGlow.color.set(ThemeManager.current.spike).apply { a = aGlow.color.a }
        aShape.color.set(ThemeManager.current.spike).apply { a = aShape.color.a }
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

    private fun addShape() {
        add(aShape) { fillParent() }
        aShape.setOrigin(Align.center)
    }

}
