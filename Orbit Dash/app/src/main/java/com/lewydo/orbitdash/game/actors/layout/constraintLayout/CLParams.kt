package com.lewydo.orbitdash.game.actors.layout.constraintLayout

import com.badlogic.gdx.scenes.scene2d.Actor

// ═════════════════════════════════════════════════════════════════════════════
//  CLParams — правила позиціонування + розміру для AConstraintLayout
//
//  РОЗМІР (Dimension):
//    FIXED         — розмір береться з actor.width/height (default)
//    MATCH_PARENT  — розмір = розміру layout (з урахуванням margin)
//    PERCENT       — розмір = відсоток від розміру layout
//    SCALED        — розмір = дизайн-число × sizeScaler.factor лейауту
//
//  ДИЗАЙН-ОДИНИЦІ (scaled):
//    scaled()               — усі числа в цьому блоці — з макета (Figma), як у
//                             setSizeScaled / toActual; лейаут множить їх на
//                             sizeScaler.factor при КОЖНОМУ resolve. Тому після
//                             зміни розміру групи нічого не треба повторювати
//                             в sizeChanged() — ні розмір, ні margin.
//    size(w, h)             — розмір у дизайн-одиницях (режим SCALED)
//
//    add(aPoint) {
//        scaled()
//        size(10f, 10f)
//        startToStart(margin = 10f); topToTop(margin = 10f)
//    }
//
//  SHORTCUTS РОЗМІРУ:
//    fillParent()           — ширина і висота = layout
//    fillWidth()            — тільки ширина = layout
//    fillHeight()           — тільки висота = layout
//    fillWidth(0.8f)        — ширина = 80% layout
//    fillHeight(0.5f)       — висота = 50% layout
//
//  SHORTCUTS ПОЗИЦІЇ:
//    center()               — по центру layout (обидві осі)
//    centerX(anchor?)       — по центру горизонтально
//    centerY(anchor?)       — по центру вертикально
//    topToTop(margin)       — верх до верху layout
//    topToBottom(anchor)    — верх до низу anchor → актор НИЖЧЕ
//    bottomToBottom(margin) — низ до низу layout
//    bottomToTop(anchor)    — низ до верху anchor → актор ВИЩЕ
//    startToStart(margin)   — ліво до лівого layout
//    startToEnd(anchor)     — ліво до правого anchor → актор ПРАВІШЕ
//    endToEnd(margin)       — право до правого layout
//    endToStart(anchor)     — право до лівого anchor → актор ЛІВІШЕ
//
//  ВИКОРИСТАННЯ:
//    // Фон що займає весь layout:
//    add(aBgImg) { fillParent() }
//
//    // Фон 90% ширини, фіксована висота:
//    add(aBgImg.apply { setSize(0f, 300f) }) { fillWidth(0.9f) }
//
//    // Іконка зліва зверху:
//    add(aIcon.apply { setSize(130f, 130f) }) {
//        startToStart(margin = 80f)
//        topToTop(margin = 73f)
//    }
//
//    // Кнопка по центру знизу:
//    add(aBtn.apply { setSize(400f, 100f) }) {
//        centerX()
//        bottomToBottom(margin = 40f)
//    }
// ═════════════════════════════════════════════════════════════════════════════

enum class Dimension {
    FIXED,            // розмір не змінюється (default)
    MATCH_PARENT,     // розмір = layout size
    PERCENT,          // розмір = percent * layout size
    SCALED,           // розмір = designWidth/Height × sizeScaler.factor лейауту
    MATCH_CONSTRAINT  // розмір = відстань між двома anchor-ами (як 0dp в Android)
    // висота: потрібні topTo* + bottomTo* одночасно
    // ширина: потрібні startTo* + endTo* одночасно
}

class CLParams(internal val layout: AConstraintLayout) {

    // ── Dimension ─────────────────────────────────────────────────────────────

    var widthMode    = Dimension.FIXED
    var heightMode   = Dimension.FIXED
    var widthPercent  = 1f   // використовується тільки якщо widthMode == PERCENT
    var heightPercent = 1f   // використовується тільки якщо heightMode == PERCENT
    var designWidth   = 0f   // використовується тільки якщо widthMode == SCALED
    var designHeight  = 0f   // використовується тільки якщо heightMode == SCALED

    // ── Дизайн-одиниці ────────────────────────────────────────────────────────

    /**
     * true → усі числа вузла (size, margin) — у дизайн-одиницях макета; лейаут
     * множить їх на sizeScaler.factor при кожному resolve. Те саме, що
     * setSizeScaled / toActual, тільки без повтору в sizeChanged().
     */
    var scaled = false

    /** Дизайн → актуальне через sizeScaler лейауту, якщо вузол у дизайн-одиницях; інакше як є. */
    internal fun toActual(value: Float): Float = if (scaled) layout.sizeScaler.toActual(value) else value

    // ── Horizontal anchors ────────────────────────────────────────────────────

    internal var startToStartActor : Actor? = null
    internal var startToEndActor   : Actor? = null
    internal var endToEndActor     : Actor? = null
    internal var endToStartActor   : Actor? = null

    var horizontalBias: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f) }

    // Пишеш сире число (дизайн- або актуальне — за scaled), лейаут читає вже
    // помножене: так усі resolve* лишаються без змін.
    var marginStart : Float = 0f
        get() = toActual(field)
    var marginEnd   : Float = 0f
        get() = toActual(field)

    // ── Vertical anchors ──────────────────────────────────────────────────────

    internal var topToTopActor      : Actor? = null
    internal var topToBottomActor   : Actor? = null
    internal var bottomToBottomActor: Actor? = null
    internal var bottomToTopActor   : Actor? = null

    var verticalBias: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f) }

    var marginTop    : Float = 0f
        get() = toActual(field)
    var marginBottom : Float = 0f
        get() = toActual(field)

    // ── Dimension shortcuts ───────────────────────────────────────────────────

    /** Усі числа цього вузла — в дизайн-одиницях макета (див. scaled). Викликати ПЕРШИМ у блоці. */
    fun scaled() { scaled = true }

    /** Розмір у дизайн-одиницях: актуальний = design × sizeScaler.factor, перераховується при кожному resolve. */
    fun size(width: Float, height: Float) {
        designWidth = width;  widthMode  = Dimension.SCALED
        designHeight = height; heightMode = Dimension.SCALED
        scaled = true
    }

    /** Ширина і висота = розмір layout */
    fun fillParent() {
        widthMode  = Dimension.MATCH_PARENT
        heightMode = Dimension.MATCH_PARENT
    }

    /** Ширина = [percent] * ширина layout (1f = 100%) */
    fun fillWidth(percent: Float = 1f) {
        widthMode    = if (percent >= 1f) Dimension.MATCH_PARENT else Dimension.PERCENT
        widthPercent = percent
    }

    /** Висота = [percent] * висота layout (1f = 100%) */
    fun fillHeight(percent: Float = 1f) {
        heightMode    = if (percent >= 1f) Dimension.MATCH_PARENT else Dimension.PERCENT
        heightPercent = percent
    }

    /**
     * Висота = відстань між topTo* і bottomTo* anchor-ами (як 0dp в Android).
     * Потрібно також вказати topTo* і bottomTo* — вони визначають межі.
     *
     * Приклад:
     *   add(aBgImg) {
     *       matchHeight()
     *       topToBottom(aPanelTopMenu)
     *       bottomToBottom()
     *       startToStart(); endToEnd()
     *   }
     *   // висота aBgImg = від низу aPanelTopMenu до низу layout — динамічно ✓
     */
    fun matchHeight() {
        heightMode = Dimension.MATCH_CONSTRAINT
    }

    /**
     * Ширина = відстань між startTo* і endTo* anchor-ами.
     * Потрібно також вказати startTo* і endTo*.
     */
    fun matchWidth() {
        widthMode = Dimension.MATCH_CONSTRAINT
    }

    /** Ширина і висота визначаються anchor-ами */
    fun matchConstraint() {
        widthMode  = Dimension.MATCH_CONSTRAINT
        heightMode = Dimension.MATCH_CONSTRAINT
    }

    // ── Center shortcuts ──────────────────────────────────────────────────────

    fun centerX(anchor: Actor? = null) {
        val a = anchor ?: layout
        startToStartActor = a;  endToEndActor = a
        marginStart = 0f;       marginEnd = 0f
        horizontalBias = 0.5f
    }

    fun centerY(anchor: Actor? = null) {
        val a = anchor ?: layout
        topToTopActor = a;      bottomToBottomActor = a
        marginTop = 0f;         marginBottom = 0f
        verticalBias = 0.5f
    }

    fun center(anchor: Actor? = null) { centerX(anchor); centerY(anchor) }

    // ── Vertical shortcuts ────────────────────────────────────────────────────

    fun topToTop(anchor: Actor? = null, margin: Float = 0f) {
        topToTopActor = anchor ?: layout; marginTop = margin
    }
    fun topToBottom(anchor: Actor? = null, margin: Float = 0f) {
        topToBottomActor = anchor ?: layout; marginTop = margin
    }
    fun bottomToBottom(anchor: Actor? = null, margin: Float = 0f) {
        bottomToBottomActor = anchor ?: layout; marginBottom = margin
    }
    fun bottomToTop(anchor: Actor? = null, margin: Float = 0f) {
        bottomToTopActor = anchor ?: layout; marginBottom = margin
    }

    // ── Horizontal shortcuts ──────────────────────────────────────────────────

    fun startToStart(anchor: Actor? = null, margin: Float = 0f) {
        startToStartActor = anchor ?: layout; marginStart = margin
    }
    fun startToEnd(anchor: Actor? = null, margin: Float = 0f) {
        startToEndActor = anchor ?: layout; marginStart = margin
    }
    fun endToEnd(anchor: Actor? = null, margin: Float = 0f) {
        endToEndActor = anchor ?: layout; marginEnd = margin
    }
    fun endToStart(anchor: Actor? = null, margin: Float = 0f) {
        endToStartActor = anchor ?: layout; marginEnd = margin
    }

    // ── Internal: всі anchor-и (для snapshot реєстрації) ─────────────────────

    internal fun allAnchors(): List<Actor> = listOfNotNull(
        startToStartActor, startToEndActor, endToEndActor,    endToStartActor,
        topToTopActor,     topToBottomActor, bottomToBottomActor, bottomToTopActor
    )
}