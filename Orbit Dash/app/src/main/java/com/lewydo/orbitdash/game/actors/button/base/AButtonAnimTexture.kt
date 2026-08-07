package com.lewydo.orbitdash.game.actors.button.base

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.lewydo.orbitdash.game.utils.actor.animHide
import com.lewydo.orbitdash.game.utils.actor.animShow
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

// Кнопка з анімацією (scale + dim) при натисканні
// і окремою текстурою для стану disabled

open class AButtonAnimTexture(
    override val screen: AdvancedScreen,
    style: Style,
) : AButtonBase(screen) {

    data class Style(
        val default : Drawable,
        val disabled: Drawable = default,
    )

    // Налаштування анімації — можна перевизначити ззовні
    var pressScale  = 0.93f
    var pressDim    = 0.75f
    var pressTime   = 0.08f
    var unpressTime = 0.20f

    private val defaultImage  = Image(style.default)
    private val disabledImage = Image(style.disabled).apply { color.a = 0f }

    override fun addActorsOnGroup() {
        addAndFillActors(listOf(defaultImage, disabledImage))
        super.addActorsOnGroup()
    }

    // ── Анімація натискання (як AButtonAnim) ──────────────────────────────────

    override fun press() {
        clearActions()
        defaultImage.clearActions()
        addAction(Actions.scaleTo(pressScale, pressScale, pressTime, Interpolation.fastSlow))
        defaultImage.addAction(Actions.color(Color(pressDim, pressDim, pressDim, 1f), pressTime))
    }

    override fun unpress() {
        clearActions()
        defaultImage.clearActions()
        addAction(Actions.scaleTo(1f, 1f, unpressTime, Interpolation.fastSlow))
        defaultImage.addAction(Actions.color(Color.WHITE, unpressTime))
    }

    // ── Disabled — текстура (як AButtonTexture) ───────────────────────────────

    override fun disable() {
        touchable = Touchable.disabled
        defaultImage.clearActions()
        defaultImage.animHide()
        disabledImage.animShow()
    }

    override fun enable() {
        touchable = Touchable.enabled
        disabledImage.animHide()
        defaultImage.animShow()
        unpress()
    }

    fun setStyle(style: Style) {
        defaultImage.drawable  = style.default
        disabledImage.drawable = style.disabled
    }
}