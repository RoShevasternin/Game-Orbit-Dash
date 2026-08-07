package com.lewydo.orbitdash.game.utils.font.msdf.effects

import com.badlogic.gdx.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// InnerShadowEffect — тінь ВСЕРЕДИНІ літери (Figma Inner shadow).
//
//   • x, y — зсув у ДИЗАЙН-px. Конвенція Figma: y+ = ВНИЗ.
//   • blur — розмиття у дизайн-px.
//   • color — колір тіні (часто напівпрозорий чорний для «вдавленості»,
//     або світлий для «випуклості»).
//   • layer = +1 → ПОВЕРХ заливки (inner shadow видно на тексті).
//   • Можна кілька (стек).
//
//   ЛІМІТ як у drop shadow: зсув/blur ≤ padding квада (R/2 px гліфа).
//
//   Приклад (вдавлений текст):
//     label.addEffect(msdf.innerShadow(0f, 4f, 3f, Color(0f,0f,0f,0.5f)))
// ─────────────────────────────────────────────────────────────────────────────

class InnerShadowEffect(
    var x   : Float,
    var y   : Float,
    var blur: Float,
    color   : Color,
    private val shader: MsdfEffectShader,
) : MsdfEffect {

    companion object {
        fun maxOffset(font: com.lewydo.orbitdash.game.utils.font.msdf.MsdfFont, worldSize: Float): Float =
            (font.distanceRange * 0.5f - 0.5f) * worldSize / font.glyphSize
    }

    override val layer = 1           // поверх заливки
    override var enabled = true
    val color = Color(color)

    override fun render(ctx: MsdfEffectContext, drawGlyphs: () -> Unit) {
        if (color.a <= 0f) return

        val pad = ctx.font.distanceRange * 0.5f
        val maxOff = pad - 0.5f
        val ox = (x * ctx.pxToGlyph).coerceIn(-maxOff, maxOff)
        val oy = (y * ctx.pxToGlyph).coerceIn(-maxOff, maxOff)
        val b  = (blur * ctx.pxToGlyph).coerceIn(0f, (pad - 0.7f).coerceAtLeast(0f))

        val prog = shader.program
        if (ctx.batch.shader !== prog) ctx.batch.shader = prog
        prog.setUniformf("u_distanceRange", ctx.font.distanceRange)
        prog.setUniformf("u_fontScale", ctx.screenScale)
        prog.setUniformf("u_hasSdf", if (ctx.font.hasTrueSdf) 1f else 0f)
        prog.setUniformf("u_offsetUV", ox * ctx.glyphToUv, oy * ctx.glyphToUv)
        prog.setUniformf("u_blurPx", b)

        ctx.glyphColor.set(color.r, color.g, color.b, color.a * ctx.alphaMul)
        drawGlyphs()
    }

    override fun copy(): MsdfEffect = InnerShadowEffect(x, y, blur, color, shader).also { it.enabled = enabled }

}