package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext

// ─────────────────────────────────────────────────────────────────────────────
// OrbitRingEffect — порожнє коло (single-pass).
//
// thickness задається у world-юнітах групи, у частки квада переводиться тут
// через ctx.width. Радіус рахується сам: найбільший, що влазить у групу.
// ─────────────────────────────────────────────────────────────────────────────
class OrbitRingEffect : VfxEffect() {

    override val fragmentShader = "shader/orbit/orbitRingFS.glsl"

    /** Товщина обводки у world-юнітах. */
    var thickness = 4f

    /** Колір кільця. */
    val color: Color = Color(1f, 1f, 1f, 1f)

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        val invW = 1f / ctx.width

        // Ширина згладжування у world-юнітах
        val aa = 1.5f

        // Найбільше коло, що влазить у групу: половина меншої сторони
        // мінус половина товщини — і мінус запас під згладжування.
        //
        // Без цього запасу зовнішня грань лягає рівно на межу квада, фейд
        // обрізається, і зверху/знизу/зліва/справа коло виглядає зрізаним.
        val radius = (minOf(ctx.width, ctx.height) - thickness) * 0.5f - aa

        shader.setUniformf("u_hOverW", ctx.height / ctx.width)
        shader.setUniformf("u_radius", radius * invW)
        shader.setUniformf("u_width",  thickness * invW)
        shader.setUniformf("u_aa",     aa * invW)
        shader.setUniformf("u_color",  color.r, color.g, color.b)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + thickness.toRawBits()
        k = k * 31 + color.toIntBits().toLong()
        return k
    }
}