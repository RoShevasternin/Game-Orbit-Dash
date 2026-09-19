package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

// ─────────────────────────────────────────────────────────────────────────────
//  ASpark — іскра комбо перед шипом. Біла крапка з білим ореолом: та сама
//  графіка, що стане супутником навколо м'яча при комбо, — гравець читає
//  «спіймані іскри стали моїми». Кольори теми не потрібні: іскра біла в
//  будь-якій темі (Figma), тому ThemeSync тут немає.
//
//  Межі актора = КРАПКА (база скейлера 6 — вона ж у Figma), ореол утричі більший
//  і живе назовні, як у ABall / AGem / ASpike. Розмір крапки задає GameScreen
//  (SPARK_SIZE), тут лише пропорції. Ті самі msdf.circle / msdf.glow — нової
//  фігури в атласі не треба.
//
//  Пульс: розмір актора — це СТЕЛЯ, а не середина. Іскра трохи зменшується й
//  повертається до нього, більшою за макет не стає ніколи — як зірка в ASpike,
//  де зона зіткнення не пульсує, тож і фігура не має здаватись ширшою.
//  Фаза — кут шипа: іскри на полі не дихають в один такт. Скейлимо ДІТЕЙ, а не
//  групу: transform-група — два флаші батча на кадр, а іскор до чотирнадцяти.
// ─────────────────────────────────────────────────────────────────────────────
class ASpark(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 6f)

    companion object {
        private const val GLOW_SIZE  = 18f
        private const val GLOW_ALPHA = 0.90f

        /**
         * Нижня точка пульсу. 0.74 — те саме «дихання», що було (0.85..1.15 дає
         * відношення 1.35), але прив'язане стелею до макета, а не серединою.
         */
        private const val PULSE_MIN    = 0.74f
        private const val PULSE_SPEED  = 6f                                   // рад/с
        private const val PULSE_PERIOD = (2.0 * Math.PI).toFloat() / PULSE_SPEED
    }

    /** Фаза пульсу, радіани. Ставить GameScreen із кута шипа при взятті з пулу. */
    var phase = 0f

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow = Image(gdxGame.assetsMsdf.glow).apply { color.a = GLOW_ALPHA }
    private val aDot  = Image(gdxGame.assetsMsdf.circle)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    /** Свій час по колу періоду: актор із пулу живе всю сесію, а float за години попливе. */
    private var time = 0f

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addDot()
    }

    override fun act(delta: Float) {
        super.act(delta)

        // Origin — після super.act(): розміри дітей лейаут вирішує саме там
        aGlow.setOrigin(Align.center)
        aDot.setOrigin(Align.center)

        time = (time + delta) % PULSE_PERIOD
        // sin → 0..1, далі PULSE_MIN..1: у піку рівно макет, ніколи більше
        val wave = 0.5f + 0.5f * MathUtils.sin(time * PULSE_SPEED + phase)
        val k = PULSE_MIN + (1f - PULSE_MIN) * wave
        aGlow.setScale(k)
        aDot.setScale(k)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addDot() {
        add(aDot) { fillParent() }
    }
}
