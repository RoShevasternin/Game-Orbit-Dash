package com.lewydo.orbitdash.game.utils.font.msdf

import com.badlogic.gdx.graphics.Color
import com.lewydo.orbitdash.game.utils.font.msdf.effects.MsdfEffect

// ─────────────────────────────────────────────────────────────────────────────
// MsdfStyle — стиль лейбла в дусі scene2d LabelStyle: описуєш РАЗ,
// використовуєш у скількох завгодно лейблах одним рядком.
//
//   Створення (звичайно у MsdfManager, як каталог стилів гри):
//
//     val TITLE = msdf.style(110f, Color.valueOf("FFD34D")) {
//         letterSpacing = 6f
//         stroke(4f, Color.valueOf("5A2D00"))
//         dropShadow(0f, 5f, 3f, Color(0f, 0f, 0f, 0.5f))
//     }
//
//   Використання:
//
//     val lbl  = MsdfLabel(TITLE, "LEVEL 12")
//     val lbl2 = MsdfLabel(TITLE, "+1500", 90f)   // той самий стиль, інший розмір
//
//   ЕФЕКТИ СПІЛЬНІ (як усе в LabelStyle): усі лейбли стилю ділять ті самі
//   інстанси ефектів — зміниш style-ефекту weight → зміняться всі. Це фіча
//   (глобальне редагування стилю). Потрібен індивідуальний ефект на одному
//   лейблі — додай його окремо через label.addEffect(msdf.stroke(...)).
// ─────────────────────────────────────────────────────────────────────────────

class MsdfStyle internal constructor(
    val manager: MsdfManager,
    var font   : MsdfFont,
    var size   : Float,
    color      : Color = Color.WHITE,
) {

    /**
     * ВЛАСНА копія: стиль не має ділити Color-інстанс із викликачем.
     * Інакше styleA.color.set(...) непомітно фарбує styleB, створений
     * з тієї ж константи. Виклики .cpy() на місцях більше не потрібні.
     */
    var color = Color(color)

    /** Letter-spacing у % (Figma). */
    var letterSpacing = 0f
    /** Line-height у % (Figma; 100 = auto). */
    var lineHeight = 100f
    /** Рамка = текст-фрейм Figma. */
    var figmaBox = true
    /** Вирівнювання (Align.*), null = дефолт Label. */
    var align: Int? = null

    val effects = ArrayList<MsdfEffect>(4)

    // ── copy: новий стиль на базі цього ───────────────────────────────────────
    /** Копія стилю з перекриттям потрібних полів. Ефекти КЛОНУЮТЬСЯ — копія
     *  повністю незалежна (зміна в копії не зачепить оригінал).
     *
     *      val REWARD = BADGE.copy(size = 56f)
     *      val TITLE  = BADGE.copy(size = 72f, color = GOLD) { stroke(4f, DARK) }
     *
     *  block додає ЩЕ ефекти/зміни поверх скопійованих. */
    fun copy(
        size         : Float    = this.size,
        color        : Color    = this.color,
        font         : MsdfFont = this.font,
        letterSpacing: Float    = this.letterSpacing,
        lineHeight   : Float    = this.lineHeight,
        figmaBox     : Boolean  = this.figmaBox,
        align        : Int?     = this.align,
        keepEffects  : Boolean  = true,
        block        : MsdfStyle.() -> Unit = {},
    ): MsdfStyle {
        val s = MsdfStyle(manager, font, size, color)
        s.letterSpacing = letterSpacing
        s.lineHeight    = lineHeight
        s.figmaBox      = figmaBox
        s.align         = align
        if (keepEffects) for (e in effects) s.effects.add(e.copy())
        return s.apply(block)
    }

    // ── DSL: ефекти прямо в блоці стилю ─────────────────────────────────────
    fun stroke(weight: Float, color: Color): MsdfStyle {
        effects.add(manager.stroke(weight, color)); return this
    }
    fun dropShadow(x: Float, y: Float, blur: Float, color: Color): MsdfStyle {
        effects.add(manager.dropShadow(x, y, blur, color)); return this
    }
    fun innerShadow(x: Float, y: Float, blur: Float, color: Color): MsdfStyle {
        effects.add(manager.innerShadow(x, y, blur, color)); return this
    }
}