package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// VignetteEffect — радіальний профіль альфи: центр порожній, край повний.
//
// Використовується ЛИШЕ для запікання у VfxTexture, тому щокадрової ціни
// не має взагалі: на екрані стоїть готова текстура, а колір і силу дає тінт.
//
// Живе в effects/, а не в effects/base/: base — цеглинки, які переїдуть у
// наступну гру (блюр, маска, roundRect, прогреси), а віньєтка цієї гри —
// такий самий її власний ефект, як OrbitRingEffect.
//
// inner — частка радіуса, де ще нічого не видно; midA — альфа у вузлі 0.55,
// тобто «наскільки пізно» вмикається градієнт (0.55 = пряма лінія).
// ─────────────────────────────────────────────────────────────────────────────
class VignetteEffect(
    private val inner: Float,
    private val midA : Float,
) : VfxEffect() {

    override val fragmentShader = "shader/vignette/vignetteFS.glsl"

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_inner", inner)
        shader.setUniformf("u_midA", midA)
    }

    override fun stateKey(): Long {
        var k = 59L
        k = k * 31 + inner.toRawBits()
        k = k * 31 + midA.toRawBits()
        return k
    }
}
