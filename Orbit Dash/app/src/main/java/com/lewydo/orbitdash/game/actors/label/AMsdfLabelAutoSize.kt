package com.lewydo.orbitdash.game.actors.label

import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle

// ─────────────────────────────────────────────────────────────────────────────
// AMsdfLabelAutoSize — MSDF-версія ALabelAutoSize: текст автоматично
// підганяє свій розмір під задану рамку (WIDTH / HEIGHT / MIN).
//
//   Відмінності від старої версії (на краще):
//   • БЕЗ бінарного пошуку: масштаб тексту лінійний, тому потрібний розмір
//     рахується ОДНІЄЮ формулою з одного заміру (fitSize / виміряне).
//   • БЕЗ мутацій спільного шрифта: замір через label.prefWidth, який у
//     MsdfLabel вже ізольований (push/pop) — нема що зберігати/відновлювати.
//   • Через MsdfStyle текст отримує ефекти (stroke/тінь) — цифри на кубах
//     тепер можуть мати обведення, як у Figma-макеті.
//
//   API сумісний зі старим: fitMode, isWrapWidth/Height, публічний label.
//   Замість min/maxFontScale — min/maxWorldSize (дизайн-px, інтуїтивніше).
//
//     val lbl = AMsdfLabelAutoSize(screen, "128", msdf.CUBE,
//                                  fitMode = FitMode.MIN)
//     lbl.setSize(120f, 120f)      // текст сам стане потрібного розміру
// ─────────────────────────────────────────────────────────────────────────────

class AMsdfLabelAutoSize(
    override val screen: AdvancedScreen,
    text : String = "",
    style: MsdfStyle,
    private val fitMode: FitMode = FitMode.WIDTH,
    val isWrapWidth : Boolean = false,
    val isWrapHeight: Boolean = false,
    private val minWorldSize: Float = 1f,
    private val maxWorldSize: Float = 100_000f,
) : AdvancedGroup() {

    val label = AMsdfLabel(text, style).apply {
        autoSize    = false          // розміром керує fit, не pack
        useFigmaBox = false          // рамку задає група; резерви Figma тут зайві
        setAlignment(Align.center)
    }

    private var computedTextWidth  = 0f
    private var computedTextHeight = 0f
    private var isAdjusting        = false

    // ------------------------------------------------------------------------
    // Prefs (для isWrapWidth/isWrapHeight — як у старого)
    // ------------------------------------------------------------------------

    override fun getPrefWidth()  = if (isWrapWidth)  computedTextWidth  else super.getPrefWidth()
    override fun getPrefHeight() = if (isWrapHeight) computedTextHeight else super.getPrefHeight()

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    override fun addActorsOnGroup() {
        addAndFillActor(label)
    }

    override fun sizeChanged() {
        super.sizeChanged()
        label.setSize(width, height)
        recalculate()
    }

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------

    fun setText(newText: CharSequence?) {
        label.setText(newText)
        recalculate()
    }

    private fun recalculate() {
        if (isAdjusting) return
        isAdjusting = true

        fit()

        if (isWrapWidth  && computedTextWidth  > 0f) width  = computedTextWidth
        if (isWrapHeight && computedTextHeight > 0f) height = computedTextHeight
        if (isWrapWidth || isWrapHeight) invalidateHierarchy()

        isAdjusting = false
    }

    // ── FIT: пряма формула замість пошуку ────────────────────────────────────
    //   Замір на еталонному розмірі (glyphSize шрифта, scale=1) → коефіцієнт
    //   k = fitSize / виміряне → новий worldSize = еталон × k. Лінійність
    //   масштабування гарантує точність без ітерацій.
    private fun fit() {
        val fitSize = when (fitMode) {
            FitMode.WIDTH  -> width
            FitMode.HEIGHT -> height
            FitMode.MIN    -> minOf(width, height)
        }
        if (fitSize <= 0f) return

        val txt = label.text
        if (txt.isEmpty()) {
            computedTextWidth = 0f; computedTextHeight = 0f
            return
        }

        // Замір на еталоні (worldSize = glyphSize → fontScale = 1)
        val ref = label.font.glyphSize
        label.worldSize = ref
        val wRef = label.prefWidth               // точна ширина: spacing, bearing

        // Висота — ВІЗУАЛЬНА (capHeight [+ рядки]), як міряв старий GlyphLayout:
        // цифри без хвостів мають заповнювати рамку по капіталях.
        val d = label.font.bitmapFont.data
        var lines = 1
        for (i in txt.indices) if (txt[i] == '\n') lines++
        val hRef = d.capHeight + (lines - 1) * d.lineHeight

        val k = when (fitMode) {
            FitMode.WIDTH  -> fitSize / wRef
            FitMode.HEIGHT -> fitSize / hRef
            FitMode.MIN    -> fitSize / maxOf(wRef, hRef)
        }

        val newSize = (ref * k).coerceIn(minWorldSize, maxWorldSize)
        label.worldSize = newSize
        label.setSize(width, height)             // рамка = група, текст по центру

        val s = newSize / ref
        computedTextWidth  = wRef * s
        computedTextHeight = hRef * s
    }

    // ------------------------------------------------------------------------
    // Enum
    // ------------------------------------------------------------------------

    enum class FitMode { WIDTH, HEIGHT, MIN }
}