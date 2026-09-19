package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// ProgressRingEffect — кільце із заповненням (single-pass).
//
// Доріжка по всьому колу з альфою trackAlpha і заповнена дуга від 12-ї години
// за годинниковою на frac оберту; frac = 1 — звичайне суцільне кільце. Так
// малюються обидва кільця м'яча: таймер комбо (frac тане) і щит (frac = 1).
//
// Розміри — у world-юнітах групи, у частки квада переводяться тут через
// ctx.width, як в OrbitRingEffect. Радіус задається явно (макет), а без нього —
// найбільший, що влазить у квад разом із товщиною й AA.
// ─────────────────────────────────────────────────────────────────────────────
class ProgressRingEffect : VfxEffect() {

    override val fragmentShader = "shader/orbit/progressRingFS.glsl"

    /** Радіус осі кільця у world-юнітах; null — найбільший, що влазить. */
    var radius: Float? = null

    /** Товщина кільця у world-юнітах. */
    var thickness = 4f

    /** Колір кільця. */
    val color: Color = Color(1f, 1f, 1f, 1f)

    /** Заповнення 0..1 від 12-ї години за годинниковою. */
    var frac = 1f

    /** Альфа незаповненої частини кільця. */
    var trackAlpha = 0.25f

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        val invW = 1f / ctx.width
        val aa   = 1.5f                                           // згладжування, world

        // Без явного радіуса зовнішня грань — на aa всередину від межі квада,
        // інакше фейд зрізається
        val r = radius ?: ((minOf(ctx.width, ctx.height) - thickness) * 0.5f - aa)

        shader.setUniformf("u_hOverW", ctx.height / ctx.width)
        shader.setUniformf("u_radius", r * invW)
        shader.setUniformf("u_width",  thickness * invW)
        shader.setUniformf("u_aa",     aa * invW)
        shader.setUniformf("u_color",  color.r, color.g, color.b)
        shader.setUniformf("u_frac",   frac.coerceIn(0f, 1f))
        shader.setUniformf("u_trackA", trackAlpha)
    }

    override fun stateKey(): Long {
        var k = 23L
        k = k * 31 + (radius ?: -1f).toRawBits()
        k = k * 31 + thickness.toRawBits()
        k = k * 31 + color.toIntBits().toLong()
        k = k * 31 + frac.toRawBits()
        k = k * 31 + trackAlpha.toRawBits()
        return k
    }
}
