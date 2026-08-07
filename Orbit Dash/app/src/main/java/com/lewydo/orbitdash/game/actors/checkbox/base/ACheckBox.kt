package com.lewydo.orbitdash.game.actors.checkbox.base

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.lewydo.orbitdash.game.utils.actor.addAndFillActors
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

class ACheckBox(
    override val screen: AdvancedScreen,
    style: Style,
) : ACheckBoxBase(screen) {

    data class Style(
        val default: Drawable,
        val checked: Drawable,
    )

    private val defaultImage = Image(style.default)
    private val checkImage   = Image(style.checked).apply { isVisible = false }

    override fun addActorsOnGroup() {
        addAndFillActors(listOf(defaultImage, checkImage))
        super.addActorsOnGroup()
    }

    override fun onChecked() {
        defaultImage.isVisible = false
        checkImage.isVisible   = true
    }

    override fun onUnchecked() {
        defaultImage.isVisible = true
        checkImage.isVisible   = false
    }

    fun setStyle(style: Style) {
        defaultImage.drawable = style.default
        checkImage.drawable   = style.checked
    }
}