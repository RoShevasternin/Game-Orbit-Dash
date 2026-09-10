package com.lewydo.orbitdash.game.actors.vfx

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxGroup
import com.lewydo.orbitdash.game.utils.vfx.effects.base.BlurEffect

/**
 * Gaussian blur на VfxGroup + BlurEffect.
 *
 * Публічне API збережено:
 *   blur             — Layer Blur як у Figma, юніти (0 = вимкнено)
 *   isBlurEnabled    — чи активний blur
 *   isStaticEffect   — заморозити результат (від VfxGroup)
 *   rerenderOnce()   — примусово перерендерити (від VfxGroup)
 *   textureRegionBlur — зовнішня текстура замість дітей (наприклад скріншот)
 */
class ABlur(
    override val screen: AdvancedScreen,
    var textureRegionBlur: TextureRegion? = null,
) : VfxGroup(screen) {

    private val blurEffect = BlurEffect(blur = 0f)

    /** Layer Blur як у Figma, юніти. 0 = вимкнено. */
    var blur: Float
        get()      = blurEffect.blur
        set(value) { blurEffect.blur = value }

    val isBlurEnabled: Boolean get() = blurEffect.isEnabled

    /** Зворотна сумісність з ABlurBack.captureOnce() */
    fun rerenderStaticOnce() { rerenderOnce() }

    override fun addActorsOnGroup() {
        super.addActorsOnGroup()  // ← налаштовує FBO камеру — обов'язково!
        addEffect(blurEffect)

        // Якщо передана зовнішня текстура — додаємо її як дитину.
        // VfxGroup.preRender() відрендерить її в FBO, потім BlurEffect розмиє.
        textureRegionBlur?.let { addAndFillActor(Image(TextureRegionDrawable(it))) }
    }
}