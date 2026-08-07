package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext

// ─────────────────────────────────────────────────────────────────────────────
// RoundRectEffect — заокруглений прямокутник (single-pass).
//
// Вихід білий, колір задає тінт актора. Радіус і товщина обводки — у
// world-юнітах, тому не залежать від розміру актора.
// ─────────────────────────────────────────────────────────────────────────────
class RoundRectEffect : VfxEffect() {

    override val fragmentShader = "shader/ui/roundRectFS.glsl"

    var radius      = 16f
    var fillAlpha   = 1f
    var strokeWidth = 0f
    var strokeAlpha = 1f
    var aaWidth     = 1.2f

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_size", ctx.width, ctx.height)
        shader.setUniformf("u_radius", radius)
        shader.setUniformf("u_aa", aaWidth)
        shader.setUniformf("u_fillAlpha", fillAlpha)
        shader.setUniformf("u_strokeWidth", strokeWidth)
        shader.setUniformf("u_strokeAlpha", strokeAlpha)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + radius.toRawBits()
        k = k * 31 + fillAlpha.toRawBits()
        k = k * 31 + strokeWidth.toRawBits()
        k = k * 31 + strokeAlpha.toRawBits()
        return k
    }
}