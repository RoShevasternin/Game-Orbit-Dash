package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext

// ─────────────────────────────────────────────────────────────────────────────
// RadialGradientEffect — радіальний градієнт зі стопами, як у Figma.
//
//   val fx = RadialGradientEffect()
//       .stop(0.745f, magnet, 0.25f)     // до 74.5 % радіуса — α 0.25
//       .stop(1f,     magnet, 0.70f)     // на краю — α 0.7
//   val tex = VfxTexture(115f, 115f, shape = fx, density = 0.5f)
//
// Це «paint radial» із SVG-експорту один в один: offset → позиція, stop-color +
// stop-opacity → колір. Центр і радіус — як у Figma без gradientTransform
// (центр квада, радіус — вписане коло); за потреби переставляються.
//
// Базовий ефект: про гру не знає, живе в base/ і поїде в наступну гру.
// ─────────────────────────────────────────────────────────────────────────────
class RadialGradientEffect : VfxEffect() {

    companion object {
        /** Стеля стопів — стільки ж у шейдері. Figma-градієнти гри в неї влазять. */
        const val MAX_STOPS = 4
    }

    override val fragmentShader = "shader/base/radialGradient/radialGradientFS.glsl"

    /** Центр у частках квада. */
    var centerX = 0.5f
    var centerY = 0.5f

    /** Радіус у частках квада: 0.5 — вписане коло, як Figma без трансформації. */
    var radius = 0.5f

    /**
     * Обрізати по радіусу. true — форма коло, як <circle> із радіальною заливкою
     * в SVG; false — як Figma на прямокутнику: останній стоп тягнеться до країв.
     */
    var clip = true

    /** Згладжування краю кола, у частках радіуса; ~1–2 текселі запеченої текстури. */
    var aa = 0.03f

    private val stopT = FloatArray(MAX_STOPS)
    private val stopC = Array(MAX_STOPS) { Color(0f, 0f, 0f, 0f) }
    private var count = 0

    /** Додати стоп: позиція 0..1 (зростають), колір і його альфа. */
    fun stop(t: Float, color: Color, alpha: Float): RadialGradientEffect {
        require(count < MAX_STOPS) { "RadialGradientEffect: більше за $MAX_STOPS стопів" }
        stopT[count] = t
        stopC[count].set(color.r, color.g, color.b, alpha)
        count++
        return this
    }

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_center", centerX, centerY)
        shader.setUniformf("u_radius", radius)
        shader.setUniformi("u_count", count.coerceAtLeast(1))
        shader.setUniformf("u_clip", if (clip) 1f else 0f)
        shader.setUniformf("u_aa", aa)
        for (i in 0 until MAX_STOPS) {
            shader.setUniformf("u_stopT[$i]", stopT[i])
            shader.setUniformf("u_stopC[$i]", stopC[i].r, stopC[i].g, stopC[i].b, stopC[i].a)
        }
    }

    override fun stateKey(): Long {
        var k = 71L
        k = k * 31 + centerX.toRawBits(); k = k * 31 + centerY.toRawBits()
        k = k * 31 + radius.toRawBits()
        k = k * 31 + count
        k = k * 31 + (if (clip) 1 else 0)
        k = k * 31 + aa.toRawBits()
        for (i in 0 until count) {
            k = k * 31 + stopT[i].toRawBits()
            k = k * 31 + stopC[i].toIntBits().toLong()
        }
        return k
    }
}
