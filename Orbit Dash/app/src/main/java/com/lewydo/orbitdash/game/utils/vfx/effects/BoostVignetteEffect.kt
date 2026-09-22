package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// BoostVignetteEffect — кольоровий край буста і до трьох кілець ОДНИМ проходом.
//
// Живий ефект (VfxImage), а не запечений: кільця їдуть щокадру. Край при цьому
// не стає дорожчим — квад той самий, повноекранний, який і так малювався; кільця
// додають лише кілька операцій на піксель. Тому три кільця тут коштують нуль
// draw call і нуль зайвої заливки — на відміну від трьох окремих квадів чи
// ShapeDrawer, який платить CPU за сотні вершин.
//
// Усі довжини — у частках зовнішнього радіуса (вписане коло квада = 1), як
// у vignetteFS. Центр кілець — зсув від центру квада в тих самих частках.
// ─────────────────────────────────────────────────────────────────────────────
class BoostVignetteEffect(
    private val inner: Float,
    private val midA : Float,
) : VfxEffect() {

    override val fragmentShader = "shader/vignette/boostVignetteFS.glsl"

    /** Множник краю: сила саме краю, поверх спільної огинаючої в тінті актора. */
    var edgeA = 1f

    /** Центр кілець відносно центру квада, у частках радіуса; y — як у UV (вниз). */
    val ringCenter = Vector2()

    /** Кільця: x — радіус, y — товщина, z — альфа. z = 0 — кільця немає. */
    val ring0 = Vector3()
    val ring1 = Vector3()
    val ring2 = Vector3()

    /** Колір кілець. Міняти через .set(). */
    val ringTint = Color(1f, 1f, 1f, 1f)

    /** Згладжування краю кільця, у частках радіуса. */
    var aa = 0.003f

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_inner",    inner)
        shader.setUniformf("u_midA",     midA)
        shader.setUniformf("u_edgeA",    edgeA)
        shader.setUniformf("u_ringC",    ringCenter.x, ringCenter.y)
        shader.setUniformf("u_ring0",    ring0.x, ring0.y, ring0.z)
        shader.setUniformf("u_ring1",    ring1.x, ring1.y, ring1.z)
        shader.setUniformf("u_ring2",    ring2.x, ring2.y, ring2.z)
        shader.setUniformf("u_ringTint", ringTint.r, ringTint.g, ringTint.b)
        shader.setUniformf("u_aa",       aa)
    }

    override fun stateKey(): Long {
        var k = 67L
        k = k * 31 + inner.toRawBits()
        k = k * 31 + midA.toRawBits()
        k = k * 31 + edgeA.toRawBits()
        k = k * 31 + ringCenter.x.toRawBits(); k = k * 31 + ringCenter.y.toRawBits()
        for (r in arrayOf(ring0, ring1, ring2)) {
            k = k * 31 + r.x.toRawBits(); k = k * 31 + r.y.toRawBits(); k = k * 31 + r.z.toRawBits()
        }
        k = k * 31 + ringTint.toIntBits().toLong()
        k = k * 31 + aa.toRawBits()
        return k
    }
}
