package com.lewydo.orbitdash.game.actors.vfx.msdf

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.OverflowImage
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect

// ─────────────────────────────────────────────────────────────────────────────
// AMsdfImage — «просто SVG-картинка»: MSDF-регіон, різкий на будь-якому розмірі.
//
//   AMsdfImage(screen, msdf.star).apply { setSize(96f, 96f); color = GOLD }
//
//   setSize / fillParent / setScale / rotation / hit-box — усе про САМУ ФІГУРУ,
//   як у Figma. Поле msdf (pxRange/2 текселів, без нього нічим згладити край)
//   малюється НАЗОВНІ від меж актора — OverflowImage із частками з клітинки.
//
//   Ціна — окремий draw call на актор: VfxImage ставить шейдер у draw().
//   Потрібен ефект (блюр, світіння) — це VfxTexture або VfxGroup із bleed.
// ─────────────────────────────────────────────────────────────────────────────
open class AMsdfImage(
    screen: AdvancedScreen,
    region: TextureRegion,
    effect: MsdfShapeEffect = gdxGame.assetsMsdf.effect,
) : VfxImage(screen, TextureRegionDrawable(region), effect, MsdfOverflow(effect.pxRange))

/**
 * Частки поля — з ЖИВОГО регіона, щокадру: підмінив drawable на іншу клітинку
 * (64×64 → 64×39) — поле перерахувалось. Різні по осях, бо клітинка несиметрична.
 */
private class MsdfOverflow(private val pxRange: Float) : OverflowImage() {
    private val region get() = (drawable as? TextureRegionDrawable)?.region
    override val padX get() = region?.let { 0.5f * pxRange / (it.regionWidth  - pxRange) } ?: 0f
    override val padY get() = region?.let { 0.5f * pxRange / (it.regionHeight - pxRange) } ?: 0f
}