package com.lewydo.orbitdash.game.utils.font.msdf.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfFont

// ─────────────────────────────────────────────────────────────────────────────
// MsdfEffect — один ШАР ефекту (як шар у Figma). Незалежний модуль.
//
//   Кожен ефект = свій шейдер + параметри. MsdfLabel малює список ефектів
//   шарами (layer): <0 під текстом, >0 над. Заливка — посередині.
//
//   Контракт: ефект у render() ставить свій шейдер + uniforms, кладе колір
//   гліфів у ctx.glyphColor, і викликає drawGlyphs() (Label малює гліфи цим
//   шейдером і кольором). MsdfLabel відновлює стан після кожного шару.
// ─────────────────────────────────────────────────────────────────────────────

interface MsdfEffect {
    val layer: Int
    var enabled: Boolean
    fun render(ctx: MsdfEffectContext, drawGlyphs: () -> Unit)

    /** Незалежна копія (для MsdfStyle.copy(): щоб зміна ефекту в копії
     *  стилю не зачіпала оригінал). */
    fun copy(): MsdfEffect
}

class MsdfEffectContext(
    val batch      : Batch,
    val font       : MsdfFont,
    val screenScale: Float,
    val worldSize  : Float,
    val alphaMul   : Float,
    val pxToGlyph  : Float,
    val glyphToUv  : Float,
) {
    // Ефект кладе сюди колір, яким Label намалює гліфи цього шару.
    val glyphColor = Color(Color.WHITE)
}