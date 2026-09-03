package com.lewydo.orbitdash.game.actors.label

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfFont
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfManager
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.font.msdf.effects.MsdfEffect
import com.lewydo.orbitdash.game.utils.font.msdf.effects.MsdfEffectContext

// ─────────────────────────────────────────────────────────────────────────────
// MsdfLabel — справжній scene2d Label з MSDF-рендером, ШАРАМИ ефектів і
// PER-LABEL letter-spacing.
//
//   Рідне від Label: wrap, ellipsis, \n, вирівнювання, відступи, autoSize.
//
//   LETTER-SPACING (per-label, НЕ впливає на інші лейбли):
//     setLetterSpacing(4f)  — у % розміру гліфа. Реалізовано так: перед
//     компонуванням/малюванням цього лейбла spacing додається до advance
//     гліфів, одразу після — прибирається. Оскільки scene2d малює послідовно
//     в одному потоці, інші лейбли не зачеплені.
//
//   ШАРИ ЕФЕКТІВ: layer<0 під текстом (stroke), база, layer>0 над.
//
//   Використання:
//     val lbl = MsdfLabel(msdf, msdf.nunitoBlack, "LEVEL 5", 80f)
//         .setLetterSpacing(3f)
//         .addEffect(msdf.stroke(4f, Color.BLACK))
// ─────────────────────────────────────────────────────────────────────────────

class AMsdfLabel(
    private val manager: MsdfManager,
    val font           : MsdfFont,
    text               : CharSequence,
    worldSize          : Float,
    color              : Color = Color.WHITE,
) : Label(text, LabelStyle(font.bitmapFont, Color(color))) {

    /** Створення зі СТИЛЮ (як LabelStyle): усі параметри й ефекти зі стилю,
     *  розмір можна перекрити третім аргументом.
     *      val lbl = MsdfLabel("LEVEL 12  , TITLE")
     *      val big = MsdfLabel("BIG", 140f, TITLE)   */
    constructor(text: CharSequence, style: MsdfStyle, worldSize: Float = style.size, color: Color = style.color) : this(
        style.manager, style.font, text, worldSize, color,
    ) {
        letterSpacingPercent = style.letterSpacing
        lineHeightPercent    = style.lineHeight
        useFigmaBox          = style.figmaBox
        style.align?.let { setAlignment(it) }
        for (e in style.effects) addEffect(e)
    }

    // true після завершення init{} — захист від викликів getPrefWidth/pushSpacing
    // із super-конструктора Label (Kotlin ще не ініціалізував наші поля).
    private var ready = false

    private val effects = ArrayList<MsdfEffect>(4)
    var autoSize = false
    private var wrapOn = false

    /** Letter-spacing у % розміру гліфа (per-label). */
    var letterSpacingPercent = 0f
        set(value) { field = value; invalidateHierarchy(); autoPack() }

    /** Line-height у % (Figma-стиль, як letterSpacing але по вертикалі).
     *  100 = auto (природний lineHeight шрифта, поведінка звичайного Label).
     *  120 = рядки далі, 80 = щільніше. Діє на багаторядковий текст. */
    var lineHeightPercent = 100f
        set(value) { field = value; invalidateHierarchy(); autoPack() }

    /** Рамка як текст-фрейм Figma: висота = lineHeight × рядки (з резервами
     *  зверху під діакритики і знизу під descender — як у макеті). false =
     *  щільна рамка по тексту (поведінка звичайного Label). Wrap → завжди
     *  рідна поведінка. Позиція літер усередині може відрізнятись від Figma
     *  на ~2-3px (Label центрує капітали, Figma ставить по baseline).
     *  ДЕФОЛТ true — дизайни беруться з Figma, рамки мають збігатися. */
    var useFigmaBox = true
        set(value) { field = value; invalidateHierarchy(); autoPack() }

    /**
     * Кольорова розмітка в тексті: `"Question 1[#FFFFFFCC] of 12"`.
     *
     * Вимкнено за замовчуванням, бо markupEnabled — властивість ШРИФТА, спільна
     * для всіх лейблів. Вмикаємо точково, навколо операцій цього лейбла.
     *
     * ⚠️ Базовий колір стилю МНОЖИТЬСЯ на колір розмітки (Label.cache.tint).
     * Тому для стилю з markup базовий колір має бути білим, інакше «білий»
     * фрагмент вийде тонованим.
     *
     * ⚠️ Літерал `[` у звичайному тексті екранується подвоєнням: `[[`.
     */
    var markup = false
        set(value) { field = value; invalidateHierarchy(); autoPack() }

    var worldSize: Float = worldSize
        set(value) {
            field = value
            setFontScale(value / font.glyphSize)
            invalidateHierarchy(); autoPack()
        }

    init {
        ready = true
        setFontScale(worldSize / font.glyphSize)
        autoPack()
    }

    // ─── Публічне API ────────────────────────────────────────────────────────

    fun setLetterSpacing(percent: Float): AMsdfLabel { letterSpacingPercent = percent; return this }
    fun setLineHeight(percent: Float): AMsdfLabel { lineHeightPercent = percent; return this }
    fun figmaBox(enabled: Boolean = true): AMsdfLabel { useFigmaBox = enabled; return this }
    fun markup(enabled: Boolean = true): AMsdfLabel { markup = enabled; return this }

    fun addEffect(effect: MsdfEffect): AMsdfLabel {
        effects.add(effect); effects.sortBy { it.layer }; return this
    }
    fun clearEffects(): AMsdfLabel { effects.clear(); return this }

    /** Колір заливки. Мутуй напряму: lbl.textColor.set(c) / .lerp(...) / .a = … */
    val textColor: Color get() = style.fontColor

    fun setTextColor(c: Color): AMsdfLabel {
        if (style.fontColor == null) style.fontColor = Color(c) else style.fontColor.set(c)
        return this
    }

    // ─── Auto-size ───────────────────────────────────────────────────────────

    private fun autoPack() { if (autoSize && !wrapOn) pack() }
    override fun setText(newText: CharSequence?) { super.setText(newText); autoPack() }
    override fun setWrap(wrap: Boolean) { wrapOn = wrap; super.setWrap(wrap) }

    // ─── Per-label spacing: тимчасово міняємо advance навколо super-виклику ──

    // Збереження матриці batch навколо обертання/масштабу (Label ігнорує
    // rotation/scale, бо BitmapFontCache малює в абсолютних координатах —
    // тож застосовуємо трансформацію актора до batch вручну).
    private val oldTransform = Matrix4()
    private var transformApplied = false

    private var spacingApplied   = 0
    private var markupApplied    = false
    private var lineDeltaApplied = 0f     // скільки додано зараз
    private var spacingDepth     = 0      // глибина вкладених push (проти подвійного)

    private fun pushSpacing() {
        if (!ready) return                    // виклик із super-конструктора
        spacingDepth++
        if (spacingDepth > 1) return          // вже застосовано зовнішнім push
        // markup читається під час layout (парсинг GlyphLayout), тож вмикати
        // треба до super.layout / prefWidth / draw і вимикати одразу після.
        if (markup && !font.bitmapFont.data.markupEnabled) {
            font.bitmapFont.data.markupEnabled = true
            markupApplied = true
        }
        // letter-spacing → advance гліфів
        if (letterSpacingPercent != 0f) {
            val add = (font.glyphSize * letterSpacingPercent / 100f).toInt()
            if (add != 0) {
                val data = font.bitmapFont.data
                for (page in data.glyphs) { page ?: continue
                    for (g in page) { g ?: continue; g.xadvance += add } }
                spacingApplied = add
            }
        }
        // line-height % → відстань між рядками (Figma-стиль; 100 = auto)
        if (lineHeightPercent != 100f) {
            val data = font.bitmapFont.data
            lineDeltaApplied = data.lineHeight * (lineHeightPercent - 100f) / 100f
            data.lineHeight += lineDeltaApplied
            data.down = -data.lineHeight
        }
    }
    private fun popSpacing() {
        if (!ready) return
        spacingDepth--
        if (spacingDepth > 0) return          // ще всередині зовнішнього push
        if (spacingApplied != 0) {
            val data = font.bitmapFont.data
            for (page in data.glyphs) { page ?: continue
                for (g in page) { g ?: continue; g.xadvance -= spacingApplied } }
            spacingApplied = 0
        }
        if (lineDeltaApplied != 0f) {
            val data = font.bitmapFont.data
            data.lineHeight -= lineDeltaApplied
            data.down = -data.lineHeight
            lineDeltaApplied = 0f
        }
        if (markupApplied) {
            font.bitmapFont.data.markupEnabled = false
            markupApplied = false
        }
    }

    // layout зі spacing (інакше рядок компонується без інтервалів)
    override fun layout() {
        pushSpacing(); super.layout(); popSpacing()
    }

    // ── prefWidth: spacing, МІНУС останній інтервал (Figma: spacing МІЖ
    //    літерами, не після). Ефекти (stroke/тіні) НЕ враховуються — вони
    //    вилазять за рамку, як у Figma; рамка обгортає лише СИМВОЛИ. ──
    // prefWidth зі spacing. НІЧОГО не віднімаємо: GlyphLayout сам не рахує
    // advance останнього гліфа (бере його візуальну ширину) — тобто інтервалів
    // рівно (n−1), «між літерами», як у Figma. Старе віднімання одного
    // інтервалу компенсувало правий SDF-padding квада; після padLeft/padRight
    // воно стало подвійним і остання літера вилазила за рамку.
    override fun getPrefWidth(): Float {
        if (!ready) return super.getPrefWidth()
        pushSpacing()
        val w = super.getPrefWidth()
        popSpacing()
        return w
    }

    // ── prefHeight: РІДНА формула Label (capHeight − 2·descent) —
    //    стандартна типографіка: хвости до низу рамки, зверху відступ
    //    = |descent|, як у звичайних Label гри. Обгортка push/pop потрібна,
    //    щоб багаторядковий pref враховував lineHeightPercent. ──
    override fun getPrefHeight(): Float {
        if (!ready) return super.getPrefHeight()
        pushSpacing()
        val h = if (useFigmaBox && !wrapOn) {
            // Figma-фрейм: lineHeight × кількість рядків. data.lineHeight на
            // цей момент уже містить lineHeightPercent-дельту (pushSpacing).
            var lines = 1
            val txt = text
            for (i in 0 until txt.length) if (txt[i] == '\n') lines++
            font.bitmapFont.data.lineHeight * fontScaleY * lines
        } else {
            super.getPrefHeight()
        }
        popSpacing()
        return h
    }

    // ─── Рендер шарами ───────────────────────────────────────────────────────

    override fun draw(batch: Batch, parentAlpha: Float) {
        applyTransform(batch)      // rotation/scale актора → матриця batch
        pushSpacing()   // spacing діє на час малювання цього лейбла

        val vp = stage?.viewport
        val pxPerWorld =
            if (vp != null && vp.worldWidth > 0f) vp.screenWidth / vp.worldWidth else 1f
        val screenScale = fontScaleX * pxPerWorld
        val alphaMul = color.a * parentAlpha

        val ctx = MsdfEffectContext(
            batch = batch, font = font,
            screenScale = screenScale, worldSize = worldSize, alphaMul = alphaMul,
            pxToGlyph = font.glyphSize / worldSize,
            glyphToUv = 1f / font.texWidth,
        )

        val fillColor = style.fontColor

        // 1) Ефекти ПІД текстом (layer < 0)
        for (e in effects) {
            if (e.layer >= 0 || !e.enabled) continue
            drawEffectLayer(e, ctx, fillColor)
        }

        // 2) БАЗА — заливка
        if (batch.shader !== manager.fillShader.program)
            batch.shader = manager.fillShader.program
        manager.fillShader.program.setUniformf("u_distanceRange", font.distanceRange)
        manager.fillShader.program.setUniformf("u_fontScale", screenScale)
        manager.fillShader.program.setUniformf("u_hasSdf", if (font.hasTrueSdf) 1f else 0f)
        super.draw(batch, parentAlpha)
        batch.shader = null

        // 3) Ефекти НАД текстом (layer > 0)
        for (e in effects) {
            if (e.layer <= 0 || !e.enabled) continue
            drawEffectLayer(e, ctx, fillColor)
        }

        popSpacing()
        resetTransform(batch)      // відкотити матрицю
    }

    // Застосувати rotation/scale/origin актора до матриці batch (лише якщо
    // є що застосовувати — інакше нуль накладних). Обертання навколо origin.
    private fun applyTransform(batch: Batch) {
        if (rotation == 0f && scaleX == 1f && scaleY == 1f) {
            transformApplied = false
            return
        }
        transformApplied = true
        oldTransform.set(batch.transformMatrix)
        val m = Matrix4(oldTransform)
        m.translate(x + originX, y + originY, 0f)
        m.rotate(0f, 0f, 1f, rotation)
        m.scale(scaleX, scaleY, 1f)
        m.translate(-(x + originX), -(y + originY), 0f)
        batch.transformMatrix = m
    }

    private fun resetTransform(batch: Batch) {
        if (transformApplied) {
            batch.transformMatrix = oldTransform
            transformApplied = false
        }
    }

    private fun drawEffectLayer(e: MsdfEffect, ctx: MsdfEffectContext, fillColor: Color) {
        e.render(ctx) {
            style.fontColor = ctx.glyphColor
            super.draw(ctx.batch, 1f)
        }
        // shader МІЖ шарами не скидаємо: наступний шар/база ставлять свій.
        // Кожен зайвий reset = flush = +1 draw (×лейбл ×шар — у діалогах
        // зі стильованими лейблами це десятки зайвих draw).
        style.fontColor = fillColor
    }
}