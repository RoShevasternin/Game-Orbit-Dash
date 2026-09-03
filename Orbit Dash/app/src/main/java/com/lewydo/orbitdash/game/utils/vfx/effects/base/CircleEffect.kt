package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext

// ─────────────────────────────────────────────────────────────────────────────
// CircleEffect — коло (single-pass).
//
// Вихід білий, колір задає тінт актора. Радіус, товщина обводки й розмиття —
// у world-юнітах, тому не залежать від розміру актора: 2 лишається 2 і на
// 10×10, і на 300×300.
// ─────────────────────────────────────────────────────────────────────────────
class CircleEffect : VfxEffect() {

    override val fragmentShader = "shader/base/circle/circleFS.glsl"

    /** Радіус кола. 0 — вписати в актора (звичайне коло без розмиття). */
    var radius      = 0f

    /** Половина ширини розмиття, у кожен бік від межі. 0 — різкий край. */
    var blur        = 0f

    var fillAlpha   = 1f
    var strokeWidth = 0f
    var strokeAlpha = 1f
    var aaWidth     = 1.2f

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_size", ctx.width, ctx.height)
        shader.setUniformf("u_radius", radius)
        shader.setUniformf("u_aa", aaWidth)
        shader.setUniformf("u_blur", blur)
        shader.setUniformf("u_fillAlpha", fillAlpha)
        shader.setUniformf("u_strokeWidth", strokeWidth)
        shader.setUniformf("u_strokeAlpha", strokeAlpha)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + radius.toRawBits()
        k = k * 31 + blur.toRawBits()
        k = k * 31 + fillAlpha.toRawBits()
        k = k * 31 + strokeWidth.toRawBits()
        k = k * 31 + strokeAlpha.toRawBits()
        return k
    }
}