package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext

// ─────────────────────────────────────────────────────────────────────────────
// BlurEffect — Gaussian blur (multi-pass: H + V)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gaussian blur.
 *
 * Два проходи через ping-pong:
 *   Pass 1: горизонтальне розмиття (u_direction = 1,0)
 *   Pass 2: вертикальне розмиття  (u_direction = 0,1)
 *
 * Математично точний 2D Gaussian: Gaussian2D(x,y) = Gaussian1D(x) × Gaussian1D(y).
 * Більше пасів по діагоналях — артефакт "сітки", не потрібно.
 *
 * radius = 0 → pass-through (жодного Blit, жодного swap).
 */
class BlurEffect(var radius: Float = 8f) : VfxEffect() {

    override val fragmentShader = "shader/base/blur/gaussianBlurFS.glsl"

    // Multi-pass: override render() а не setUniforms()
    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (radius <= 0f) return  // pass-through — src не змінюється

        // Pass 1: горизонтальний
        Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
            s.setUniformf("u_direction",  1f, 0f)
            s.setUniformf("u_groupSize",  ctx.bufferW.toFloat(), ctx.bufferH.toFloat())
            s.setUniformf("u_blurAmount", radius)
        }
        pingPong.swap()  // результат H-pass тепер в src

        // Pass 2: вертикальний
        Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
            s.setUniformf("u_direction",  0f, 1f)
            s.setUniformf("u_groupSize",  ctx.bufferW.toFloat(), ctx.bufferH.toFloat())
            s.setUniformf("u_blurAmount", radius)
        }
        pingPong.swap()  // результат V-pass тепер в src

        // Pass 3: діагональний 1
        Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
            s.setUniformf("u_direction",  0.383f,  0.924f)
            s.setUniformf("u_groupSize",  ctx.bufferW.toFloat(), ctx.bufferH.toFloat())
            s.setUniformf("u_blurAmount", radius)
        }
        pingPong.swap()  // результат D1-pass тепер в src

        // Pass 4: діагональний 2
        Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
            s.setUniformf("u_direction",  0.924f,  0.383f)
            s.setUniformf("u_groupSize",  ctx.bufferW.toFloat(), ctx.bufferH.toFloat())
            s.setUniformf("u_blurAmount", radius)
        }
        pingPong.swap()  // результат D2-pass тепер в src = фінальний blur
    }

    override fun stateKey(): Long = radius.toRawBits().toLong()

}