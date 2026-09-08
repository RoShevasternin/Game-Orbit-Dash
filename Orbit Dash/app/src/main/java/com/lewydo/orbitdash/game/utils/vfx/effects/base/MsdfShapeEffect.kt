package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxTextures
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// MsdfShapeEffect — малює MSDF-фігуру з атласу різко на будь-якому розмірі.
//
//   Один інстанс на атлас: юніформи залежать лише від розміру сторінки та
//   pxRange, з яким згенеровано поля. Працює і як ефект VfxImage (живий актор,
//   будь-який розмір), і як shape у VfxTexture (запекти в звичайну текстуру —
//   для батчингу зі змішаними стилями або для post-ефектів).
//
//   Досяжність майбутніх ефектів (обводка, світіння) — pxRange / 2 текселі.
// ─────────────────────────────────────────────────────────────────────────────
class MsdfShapeEffect(
    private val atlas: Texture,
    /** Ширина діапазону поля в текселях. Потрібен і setShapeSize(): поле autoframe = pxRange/2 з кожного боку. */
    val pxRange: Float,
) : VfxEffect() {

    override val fragmentShader = "shader/base/msdf/shape/msdf_shape.glsl"

    /**
     * Фолбек, коли на пристрої немає GL_OES_standard_derivatives: скільки
     * екранних пікселів припадає на один тексель атласу.
     *
     * Точно порахувати не можна: юніформ один на весь батч, а фігури в ньому
     * різного розміру. DENSITY (px на юніт) — це відповідь для випадку
     * «1 тексель = 1 юніт», тобто клітинка 64 намальована на 64 юніти; для
     * решти — наближення. Такі пристрої рідкість, краще м'якший край, ніж жодного.
     */
    var screenPxPerTexel = VfxTextures.DENSITY

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_unitRange", pxRange / atlas.width, pxRange / atlas.height)
        shader.setUniformf("u_screenPxRange", pxRange * screenPxPerTexel)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + pxRange.toRawBits()
        k = k * 31 + screenPxPerTexel.toRawBits()
        return k
    }
}