package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.graphics.Color
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
        private const val GLOW_SIZE  = 100f
        private const val POINT_SIZE = 10f

        /** Швидше за гем — рух сам по собі сигналить «не чіпай». */
        private const val SPIN_SPEED = 180f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsMsdf.glow).apply { color.a = 0.90f }
    private val aShape = Image(gdxGame.assetsMsdf.spike)
    private val aPoint = Image(gdxGame.assetsMsdf.circle).apply { color = Color.BLACK.cpy().apply { a = 0.40f } }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addShape()
        addPoint()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        aShape.setOrigin(Align.center)
        aShape.rotation = (aShape.rotation + SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addShape() {
        add(aShape) { fillParent() }
        aShape.setOrigin(Align.center)
    }

    private fun addPoint() {
        add(aPoint) { size(POINT_SIZE); center() }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.spike)
        aShape.setColorRGB(ThemeManager.current.spike)
    }
}
