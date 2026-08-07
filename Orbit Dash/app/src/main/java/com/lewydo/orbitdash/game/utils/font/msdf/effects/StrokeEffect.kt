package com.lewydo.orbitdash.game.utils.font.msdf.effects

import com.badlogic.gdx.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// StrokeEffect — обведення OUTSIDE (єдина потрібна позиція для казуальних ігор).
//   Малюється ПІД заливкою (layer=-1). weight у дизайн-px.
//   Товщина обмежена range/2 px гліфа → для товстих обводок генеруй атлас з
//   великим -pxrange (24-32).
// ─────────────────────────────────────────────────────────────────────────────

class StrokeEffect(
    var weight: Float,
    color     : Color,
    private val shader: MsdfEffectShader,
) : MsdfEffect {

    override val layer = -1          // під заливкою
    override var enabled = true
    val color = Color(color)

    override fun render(ctx: MsdfEffectContext, drawGlyphs: () -> Unit) {
        if (weight <= 0f || color.a <= 0f) return

        val wg = weight * ctx.pxToGlyph
        val maxReach = ctx.font.distanceRange * 0.5f - 0.6f
        val outer = (-wg).coerceAtLeast(-maxReach)   // назовні, в межах поля

        val prog = shader.program
        if (ctx.batch.shader !== prog) ctx.batch.shader = prog
        prog.setUniformf("u_distanceRange", ctx.font.distanceRange)
        prog.setUniformf("u_fontScale", ctx.screenScale)
        prog.setUniformf("u_hasSdf", if (ctx.font.hasTrueSdf) 1f else 0f)
        prog.setUniformf("u_outerPx", outer)

        ctx.glyphColor.set(color.r, color.g, color.b, color.a * ctx.alphaMul)
        drawGlyphs()
    }

    override fun copy(): MsdfEffect = StrokeEffect(weight, color, shader).also { it.enabled = enabled }
}