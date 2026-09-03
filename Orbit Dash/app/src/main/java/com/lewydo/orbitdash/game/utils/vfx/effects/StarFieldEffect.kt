package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.ShaderClock
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// StarFieldEffect — процедурні зорі на весь екран (single-pass).
//
// Замінює масив спрайтів: зорі не існують як обʼєкти, вони обчислюються
// з хешу координат клітинки. Тому їх «кількість» — це просто щільність,
// і 200 зір коштують рівно стільки ж, скільки 20.
// ─────────────────────────────────────────────────────────────────────────────
class StarFieldEffect : VfxEffect() {

    override val fragmentShader = "shader/background/starFieldFS.glsl"

    /** Щільність: скільки клітинок по висоті в найгустішому шарі. */
    var density = 16f

    /** Частка клітинок, у яких реально є зоря (0..1). */
    var fill = 0.55f

    /**
     * Накопичена фаза дрейфу. Веде AStarField через глобальний StarFieldClock —
     * саме тому небо не стрибає ні при зміні швидкості, ні при зміні екрана.
     */
    var driftPhase = 0f

    /** Сила мерехтіння 0..1. Понад 0.5 вже читається як блимання. */
    var twinkle = 0.35f

    /** 1 = крапки, >1 = зорі витягуються у смуги вздовж руху (warp). */
    var stretch = 1f

    // ── імпульс від тапу ─────────────────────────────────────────────────────
    var ripplePosX = 0.5f
    var ripplePosY = 0.5f
    var rippleRadius = 0f
    var rippleAmp = 0f

    /** Основний колір зір. */
    val colorA: Color = Color(1f, 1f, 1f, 1f)

    /** Колір рідкісних (≈15%) кольорових зір — зазвичай роль player теми. */
    val colorB: Color = Color(0f, 0.898f, 1f, 1f)

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_time",    ShaderClock.time)
        shader.setUniformf("u_aspect",  ctx.width / ctx.height)
        shader.setUniformf("u_density", density)
        shader.setUniformf("u_drift",   driftPhase)
        shader.setUniformf("u_twinkle", twinkle)
        shader.setUniformf("u_fill",    fill)
        shader.setUniformf("u_stretch", stretch)
        shader.setUniformf("u_ripplePos", ripplePosX, ripplePosY)
        shader.setUniformf("u_rippleRadius", rippleRadius)
        shader.setUniformf("u_rippleAmp", rippleAmp)
        shader.setUniformf("u_colorA",  colorA.r, colorA.g, colorA.b)
        shader.setUniformf("u_colorB",  colorB.r, colorB.g, colorB.b)
    }

    /** Анімовано щокадру — кешувати не можна, інакше небо «замерзне». */
    override fun stateKey(): Long = ShaderClock.time.toRawBits().toLong()
}