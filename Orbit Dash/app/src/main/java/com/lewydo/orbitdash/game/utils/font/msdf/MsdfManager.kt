package com.lewydo.orbitdash.game.utils.font.msdf

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.utils.disposeAll
import com.lewydo.orbitdash.game.utils.font.msdf.effects.MsdfEffectShader
import com.lewydo.orbitdash.game.utils.font.msdf.effects.StrokeEffect
import com.lewydo.orbitdash.game.utils.font.msdf.effects.DropShadowEffect
import com.lewydo.orbitdash.game.utils.font.msdf.effects.InnerShadowEffect

// ─────────────────────────────────────────────────────────────────────────────
// MsdfManager — єдина точка: шрифти + шейдери шарів.
// Кожен ефект має свій шейдер тут. Додати ефект = shader + factory-метод.
// ─────────────────────────────────────────────────────────────────────────────

class MsdfManager : Disposable {

    // Створене — і ТІЛЬКИ воно — лягає сюди. Інакше dispose() звертається до
    // lazy-полів і створює те, чим жодного разу не користувались: читання шрифта
    // з диска й компіляція шейдера на виході, коли GL-контексту вже може не бути.
    private val created = mutableListOf<Disposable>()

    private fun <T : Disposable> onDemand(block: () -> T) = lazy { block().also { created.add(it) } }

    val fillShader   by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_fill.glsl") }
    val strokeShader by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_stroke.glsl") }
    val shadowShader by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_shadow.glsl") }
    val innerShader  by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_inner_shadow.glsl") }

    val fontInter_Medium by onDemand { MsdfFont(
        "font/msdf/Inter-Medium.json",
        "font/msdf/Inter-Medium.png",
    ) }
    val fontInter_Bold by onDemand { MsdfFont(
        "font/msdf/Inter-Bold.json",
        "font/msdf/Inter-Bold.png",
    ) }
    val fontInter_ExtraBold by onDemand { MsdfFont(
        "font/msdf/Inter-ExtraBold.json",
        "font/msdf/Inter-ExtraBold.png",
    ) }

    /** Обведення OUTSIDE. weight у дизайн-px. */
    fun stroke(weight: Float, color: Color) = StrokeEffect(weight, color, strokeShader)

    /** Тінь як у Figma: x,y (y+ = вниз), blur — усе в дизайн-px. Можна кілька. */
    fun dropShadow(x: Float, y: Float, blur: Float, color: Color) = DropShadowEffect(x, y, blur, color, shadowShader)

    /** Внутрішня тінь (Figma Inner shadow): x,y (y+ = вниз), blur у дизайн-px. */
    fun innerShadow(x: Float, y: Float, blur: Float, color: Color) = InnerShadowEffect(x, y, blur, color, innerShader)

    override fun dispose() {
        created.disposeAll()   // Iterable<Disposable>.disposeAll() з utils/Util.kt
        created.clear()
    }

    // ------------------------------------------------------------------------
    // Type
    // ------------------------------------------------------------------------
//    val FLYING_COIN by lazy { MsdfStyle(this, fontNunito_Black, 90f)
//        .stroke(5f, GameColor.purple_350080)
//        .dropShadow(6f, 6f, 4f, GameColor.purple_350080)
//    }

}