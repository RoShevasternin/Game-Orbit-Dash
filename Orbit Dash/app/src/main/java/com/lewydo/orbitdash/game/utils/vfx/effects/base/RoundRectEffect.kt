package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext

// ─────────────────────────────────────────────────────────────────────────────
// RoundRectEffect — заокруглений прямокутник (single-pass).
//
// Заливка й обводка мають власні кольори. Обидва БІЛІ (за замовчуванням) —
// вихід білий, колір задає тінт актора, як було завжди. Задав хоч один —
// фігура стає двоколірною, а тінт актора множиться зверху (тримай його білим).
//
// Радіус і товщина обводки — у world-юнітах, тому не залежать від розміру актора.
// ─────────────────────────────────────────────────────────────────────────────
class RoundRectEffect : VfxEffect() {

    override val fragmentShader = "shader/base/roundRect/roundRectFS.glsl"

    var radius      = 16f
    var fillAlpha   = 1f
    var strokeWidth = 0f
    var strokeAlpha = 1f
    var aaWidth     = 1.2f

    /** Колір заливки. Білий = «беру з тінту актора». Міняти через .set(), об'єкт живий. */
    val fillColor   = Color(Color.WHITE)

    /** Колір обводки. Білий = «беру з тінту актора». */
    val strokeColor = Color(Color.WHITE)

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_size", ctx.width, ctx.height)
        shader.setUniformf("u_radius", radius)
        shader.setUniformf("u_aa", aaWidth)
        shader.setUniformf("u_fillAlpha", fillAlpha)
        shader.setUniformf("u_fillColor", fillColor.r, fillColor.g, fillColor.b)
        shader.setUniformf("u_strokeWidth", strokeWidth)
        shader.setUniformf("u_strokeAlpha", strokeAlpha)
        shader.setUniformf("u_strokeColor", strokeColor.r, strokeColor.g, strokeColor.b)
    }

    /**
     * Ключ стану для кешу VfxTexture: усе, що змінює картинку. Кольори — через
     * toIntBits(), бо це рівно ті 32 біти, які пішли б у шейдер.
     */
    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + radius.toRawBits()
        k = k * 31 + fillAlpha.toRawBits()
        k = k * 31 + strokeWidth.toRawBits()
        k = k * 31 + strokeAlpha.toRawBits()
        k = k * 31 + aaWidth.toRawBits()
        k = k * 31 + fillColor.toIntBits()
        k = k * 31 + strokeColor.toIntBits()
        return k
    }
}