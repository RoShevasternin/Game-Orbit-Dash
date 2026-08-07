package com.lewydo.orbitdash.game.utils.font.msdf.effects

import com.badlogic.gdx.graphics.Color
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfFont

// ─────────────────────────────────────────────────────────────────────────────
// DropShadowEffect — тінь як у Figma Drop shadow: x, y, blur, color.
//
//   • x, y — зсув у ДИЗАЙН-px. Конвенція ЯК У FIGMA: y+ = ВНИЗ.
//   • blur — радіус розмиття у дизайн-px (безкоштовний у SDF).
//   • Можна КІЛЬКА тіней на лейбл (стек): просто addEffect ще раз.
//   • layer = -2 → під stroke (-1) і текстом.
//
//   ЛІМІТ ПОЛЯ: зсув і blur обмежені padding-ом квада (R/2 px гліфа):
//     max ≈ (R/2) × worldSize / glyphSize   (range=8 → ~worldSize/14)
//   Більший зсув семплив би сусідні гліфи атласу. Кламп м'який — тінь
//   просто впирається в максимум. Потрібні великі тіні → атлас з більшим R.
//
//   Приклад (рожева вниз + жовта вгору — стек):
//     label.addEffect(msdf.dropShadow( 0f,  6f, 4f, Color.PINK))
//     label.addEffect(msdf.dropShadow( 0f, -6f, 4f, Color.YELLOW))
// ─────────────────────────────────────────────────────────────────────────────

class DropShadowEffect(
    var x   : Float,
    var y   : Float,
    var blur: Float,
    color   : Color,
    private val shader: MsdfEffectShader,
) : MsdfEffect {

    companion object {
        /** Максимальний зсув/blur тіні (дизайн-px) для розміру тексту —
         *  фізичний ліміт padding квада (R/2). Для якісних великих тіней. */
        fun maxOffset(font: MsdfFont, worldSize: Float): Float = (font.distanceRange * 0.5f - 0.5f) * worldSize / font.glyphSize
    }

    override val layer = -2          // під stroke і текстом
    override var enabled = true
    val color = Color(color)

    override fun render(ctx: MsdfEffectContext, drawGlyphs: () -> Unit) {
        if (color.a <= 0f) return

        val pad = ctx.font.distanceRange * 0.5f
        val maxOff = pad - 0.5f                       // не вилазити за квад
        val ox = (x * ctx.pxToGlyph).coerceIn(-maxOff, maxOff)
        val oy = (y * ctx.pxToGlyph).coerceIn(-maxOff, maxOff)
        val b  = (blur * ctx.pxToGlyph).coerceIn(0f, (pad - 0.7f).coerceAtLeast(0f))

        val prog = shader.program
        if (ctx.batch.shader !== prog) ctx.batch.shader = prog
        prog.setUniformf("u_distanceRange", ctx.font.distanceRange)
        prog.setUniformf("u_fontScale", ctx.screenScale)
        prog.setUniformf("u_hasSdf", if (ctx.font.hasTrueSdf) 1f else 0f)
        // UV: v росте вниз, Figma y теж вниз → знаки збігаються без інверсії
        prog.setUniformf("u_offsetUV", ox * ctx.glyphToUv, oy * ctx.glyphToUv)
        prog.setUniformf("u_blurPx", b)

        ctx.glyphColor.set(color.r, color.g, color.b, color.a * ctx.alphaMul)
        drawGlyphs()
    }

    override fun copy(): MsdfEffect = DropShadowEffect(x, y, blur, color, shader).also { it.enabled = enabled }

}