package com.lewydo.orbitdash.game.actors.ui

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

// ═════════════════════════════════════════════════════════════════════════════
//  ABadgeDot — точка «є нове»: halo + ядро.
//
//  Окремий клас, бо це КОМПОЗИЦІЯ (два актори), а не параметр кнопки. Далі
//  піде на плитку нового скіна, шестерню налаштувань, стрік.
//
//  Image, а не ARoundRect: той є VfxImage і на кожен draw перемикає шейдер
//  батча (два флаші). Тут — звичайні регіони, батчаться з рештою UI.
//
//  Halo ВИХОДИТЬ за межі групи — так і задумано. Розмір групи = розмір ядра,
//  тому позиціонувати треба ядро, а halo сам розтечеться назовні.
// ═════════════════════════════════════════════════════════════════════════════
class ABadgeDot(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val GLOW_SCALE = 1.4f

        // Пінг: щільна точка розходиться назовні й гасне
        private const val PULSE_FROM  = 0.55f
        private const val PULSE_TO    = 1.45f
        private const val PULSE_ALPHA = 0.75f
        private const val PULSE_TIME  = 1.1f
        private const val PULSE_PAUSE = 0.45f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow = Image(gdxGame.assetsAll.badge_glow)
    private val aDot  = Image(gdxGame.assetsMsdf.circle)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        aGlow.setSize(width * GLOW_SCALE, height * GLOW_SCALE)
        aGlow.setOrigin(Align.center)   // без цього скейл поїде в лівий нижній кут
        add(aGlow) { center() }

        add(aDot) { fillParent() }

        syncTheme()
        startPulse()
    }

    /** Колір не зберігається — щокадру з теми, тому лерп скіна працює сам. */
    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()
    }

    // ------------------------------------------------------------------------
    // Animations
    // ------------------------------------------------------------------------
    /**
     * Пінг: щільна точка розходиться, гасне, пауза, знову.
     *
     * Асиметричний пінг, а не симетричне «дихання» 0.5↔1: рух НАЗОВНІ читається
     * як сигнал «сюди», рівномірне пульсування — як декор, який око швидко
     * перестає помічати. Пауза між циклами дає той самий ефект, що й у радара.
     *
     * Миттєві scaleTo/alpha на початку обов'язкові: forever продовжує з
     * поточних значень, тож без скидання друга ітерація стартувала б з 1.45/0.
     */
    private fun startPulse() {
        aGlow.clearActions()
        aGlow.addAction(
            Actions.forever(
                Actions.sequence(
                    Actions.scaleTo(PULSE_FROM, PULSE_FROM),
                    Actions.alpha(PULSE_ALPHA),
                    Actions.parallel(
                        Actions.scaleTo(PULSE_TO, PULSE_TO, PULSE_TIME, Interpolation.circleOut),
                        Actions.alpha(0f, PULSE_TIME, Interpolation.fade),
                    ),
                    Actions.delay(PULSE_PAUSE),
                )
            )
        )
    }

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        val gemColor = ThemeManager.current.gem
        aGlow.setColorRGB(gemColor)
        aDot.setColorRGB(gemColor)
    }
}