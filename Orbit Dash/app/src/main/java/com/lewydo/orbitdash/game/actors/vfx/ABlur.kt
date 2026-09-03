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
 *   radiusBlur       — радіус розмиття (0 = вимкнено)
 *   isBlurEnabled    — чи активний blur
 *   isStaticEffect   — заморозити результат (від VfxGroup)
 *   rerenderOnce()   — примусово перерендерити (від VfxGroup)
 *   textureRegionBlur — зовнішня текстура замість дітей (наприклад скріншот)
 */
class ABlur(
    override val screen: AdvancedScreen,
    var textureRegionBlur: TextureRegion? = null,
) : VfxGroup(screen) {

    private val blurEffect = BlurEffect(radius = 0f)

    var radiusBlur: Float = 0f
        set(value) {
            blurEffect.radius = value
            field = value
        }


    val isBlurEnabled: Boolean get() = blurEffect.radius > 0f

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