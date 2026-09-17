package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect
import kotlin.math.PI
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// OrbitRingEffect — кільце орбіти (single-pass).
//
// Усі розміри — у world-юнітах групи, у частки квада переводяться тут через
// ctx.width. Радіус: за замовчуванням найбільший, що влазить у групу; з
// outerPad — рівно outerPad від краю квада (щоб glow і пунктир, які ширші за
// саму орбіту, не обрізались межею).
//
// active = true вмикає шари з Figma ring-outer-active: два glow і пунктир
// кольору м'яча (playerColor). Числа Figma живуть у AOrbitRing, тут — юніформи.
// ─────────────────────────────────────────────────────────────────────────────
class OrbitRingEffect : VfxEffect() {

    override val fragmentShader = "shader/orbit/orbitRingFS.glsl"

    /** Товщина орбіти у world-юнітах. */
    var thickness = 4f

    /** Колір орбіти. */
    val color: Color = Color(1f, 1f, 1f, 1f)

    /** null — радіус як завжди: найбільше коло, що влазить. Число — відступ осі орбіти від краю квада. */
    var outerPad: Float? = null

    // ── активна орбіта ──
    var active = false
    val playerColor: Color = Color(1f, 1f, 1f, 1f)
    var glow1Width = 0f;  var glow1Alpha = 0f
    var glow2Width = 0f;  var glow2Alpha = 0f
    /** Радіус пунктиру = радіус орбіти + це. */
    var dashOffsetR = 0f
    var dashWidth = 0f;   var dashAlpha = 0f
    var dashLen = 1f;     var dashGap = 1f
    /**
     * Фаза пунктиру в періодах штриха, ЗАВЖДИ 0..1. Сума зсувів за ран росла б
     * без меж, а в шейдері велике число — ступінчасте: пунктир смикався б тим
     * сильніше, чим довше гра. Тому зсув одразу згортається в частку періоду.
     */
    var dashPhase = 0f
        private set

    /**
     * Зсунути пунктир на [shift] world-юнітів дуги. Росте — проти годинникової.
     * Період — поточного кільця: під час роз'їзду він змінюється, а фаза в
     * частках періоду лишається на місці, без ривка.
     */
    fun advanceDash(shift: Float, width: Float, height: Float) {
        val dashR = radiusFor(width, height) + dashOffsetR
        dashPhase = (dashPhase + shift / dashPeriod(dashR)).mod(1f)
    }

    /** Штрихів по колу — ЦІЛЕ число, інакше на 180° був би шов із половинкою штриха. */
    private fun dashCount(dashR: Float): Int =
        ((2.0 * PI * dashR).toFloat() / (dashLen + dashGap)).roundToInt().coerceAtLeast(1)

    private fun dashPeriod(dashR: Float): Float = (2.0 * PI * dashR).toFloat() / dashCount(dashR)

    /** Радіус при поточному розмірі (world), без запасу на AA — для посадки об'єктів. */
    fun radiusFor(width: Float, height: Float): Float {
        val pad = outerPad
        return if (pad != null) minOf(width, height) * 0.5f - pad
        else (minOf(width, height) - thickness) * 0.5f
    }

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        val invW = 1f / ctx.width

        // Ширина згладжування у world-юнітах
        val aa = 1.5f

        // Без outerPad зовнішня грань лягала б рівно на межу квада, фейд
        // обрізався б, і коло зверху/знизу/зліва/справа виглядало б зрізаним.
        val radius = if (outerPad != null) radiusFor(ctx.width, ctx.height)
        else radiusFor(ctx.width, ctx.height) - aa

        shader.setUniformf("u_hOverW", ctx.height / ctx.width)
        shader.setUniformf("u_radius", radius * invW)
        shader.setUniformf("u_width",  thickness * invW)
        shader.setUniformf("u_aa",     aa * invW)
        shader.setUniformf("u_color",  color.r, color.g, color.b)

        shader.setUniformf("u_active", if (active) 1f else 0f)
        if (!active) return

        val dashR = radius + dashOffsetR

        shader.setUniformf("u_player",    playerColor.r, playerColor.g, playerColor.b)
        shader.setUniformf("u_glowW",     glow1Width * invW, glow2Width * invW)
        shader.setUniformf("u_glowA",     glow1Alpha, glow2Alpha)
        shader.setUniformf("u_dashR",     dashR * invW)
        shader.setUniformf("u_dashW",     dashWidth * invW)
        shader.setUniformf("u_dashA",     dashAlpha)
        shader.setUniformf("u_dashCount", dashCount(dashR).toFloat())
        shader.setUniformf("u_dashFill",  dashLen / (dashLen + dashGap))
        shader.setUniformf("u_dashPhase", dashPhase)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + thickness.toRawBits()
        k = k * 31 + color.toIntBits().toLong()
        k = k * 31 + (if (active) 1 else 0)
        if (active) {
            k = k * 31 + playerColor.toIntBits().toLong()
            k = k * 31 + dashPhase.toRawBits()
        }
        return k
    }
}