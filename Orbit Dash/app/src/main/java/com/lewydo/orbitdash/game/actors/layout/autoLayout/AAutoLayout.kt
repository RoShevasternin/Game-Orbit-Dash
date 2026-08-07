package com.lewydo.orbitdash.game.actors.layout.autoLayout

import com.badlogic.gdx.scenes.scene2d.Actor
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

// ═════════════════════════════════════════════════════════════════════════════
//  AAutoLayout — Figma Auto Layout для LibGDX
//
//  COORDINATE SYSTEM:
//    LibGDX: Y=0 знизу, Y=height зверху
//    HORIZONTAL: головна вісь = X (зліва направо), поперечна = Y (знизу вгору)
//    VERTICAL:   головна вісь = Y (зверху вниз),   поперечна = X (зліва направо)
//
//  SIZING:
//    FIXED — розмір задається вручну setSize()
//    HUG   — група стискається під вміст
//    FILL  — дитина заповнює батька (через ALParams)
//
//  ALIGN CROSS (поперечна вісь):
//    START   — HORIZONTAL: притиснути до низу;  VERTICAL: притиснути до лівого краю
//    CENTER  — по центру поперечної осі
//    END     — HORIZONTAL: притиснути до верху; VERTICAL: притиснути до правого краю
//    STRETCH — розтягнути на всю поперечну вісь (або на висоту рядка при wrap)
// ═════════════════════════════════════════════════════════════════════════════

open class AAutoLayout(
    override val screen : AdvancedScreen,

    direction    : Direction  = Direction.HORIZONTAL,
    wrap         : Boolean    = false,
    alignMain    : AlignMain  = AlignMain.START,
    alignCross   : AlignCross = AlignCross.START,
    gapMain      : Float      = 0f,
    gapCross     : Float      = 0f,
    paddingTop   : Float      = 0f,
    paddingBottom: Float      = 0f,
    paddingStart : Float      = 0f,
    paddingEnd   : Float      = 0f,
    sizingW      : Sizing     = Sizing.FIXED,
    sizingH      : Sizing     = Sizing.FIXED,
) : AdvancedGroup() {

    // ── Enums ─────────────────────────────────────────────────────────────────

    enum class Direction  { HORIZONTAL, VERTICAL }
    enum class AlignMain  { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND }
    enum class AlignCross { START, CENTER, END, STRETCH }
    enum class AlignSelf  { AUTO, START, CENTER, END, STRETCH }
    enum class Sizing     { FIXED, HUG, FILL }

    // ── Параметри групи ───────────────────────────────────────────────────────

    var direction    = direction;    set(v) { field = v; invalidate() }
    var wrap         = wrap;         set(v) { field = v; invalidate() }
    var alignMain    = alignMain;    set(v) { field = v; invalidate() }
    var alignCross   = alignCross;   set(v) { field = v; invalidate() }
    var gapMain      = gapMain;      set(v) { field = v; invalidate() }
    var gapCross     = gapCross;     set(v) { field = v; invalidate() }
    var paddingTop   = paddingTop;   set(v) { field = v; invalidate() }
    var paddingBottom= paddingBottom;set(v) { field = v; invalidate() }
    var paddingStart = paddingStart; set(v) { field = v; invalidate() }
    var paddingEnd   = paddingEnd;   set(v) { field = v; invalidate() }
    var sizingW      = sizingW;      set(v) { field = v; invalidate() }
    var sizingH      = sizingH;      set(v) { field = v; invalidate() }
    var reverse      = false;         set(v) { field = v; invalidate() }

    // ── Min/Max розміри ───────────────────────────────────────────────────────
    // Обмежують розмір групи при HUG або FIXED
    // Корисно для scroll контенту, адаптивних панелей тощо
    var minW : Float = 0f;               set(v) { field = v; invalidate() }
    var maxW : Float = Float.MAX_VALUE;  set(v) { field = v; invalidate() }
    var minH : Float = 0f;               set(v) { field = v; invalidate() }
    var maxH : Float = Float.MAX_VALUE;  set(v) { field = v; invalidate() }

    fun setMinSize(w: Float, h: Float) { minW = w; minH = h }
    fun setMaxSize(w: Float, h: Float) { maxW = w; maxH = h }

    fun setPadding(all: Float) {
        paddingTop = all; paddingBottom = all; paddingStart = all; paddingEnd = all
    }
    fun setPadding(vertical: Float, horizontal: Float) {
        paddingTop = vertical; paddingBottom = vertical
        paddingStart = horizontal; paddingEnd = horizontal
    }

    private val childParams = LinkedHashMap<Actor, ALParams>()

    // ── Захист від рекурсії ───────────────────────────────────────────────────
    // HUG змінює width/height → sizeChanged() → invalidate() → layout() знову
    // isLayouting блокує повторний виклик layout() під час поточного

    private var isLayouting = false

    // ── Snapshot: відстежуємо зміни розміру дітей ────────────────────────────

    private val snapActors      = ArrayList<Actor>()
    private val snapArrays      = ArrayList<FloatArray>()
    private var anyChildChanged = false

    // ── Public API ────────────────────────────────────────────────────────────

    override fun addActorsOnGroup() {}

    fun add(actor: Actor, block: ALParams.() -> Unit = {}): Actor {
        childParams[actor] = ALParams().apply(block)
        addActor(actor)
        watchChild(actor)
        invalidate()
        return actor
    }

    fun update(actor: Actor, block: ALParams.() -> Unit) {
        (childParams[actor] ?: ALParams().also { childParams[actor] = it }).apply(block)
        invalidate()
    }

    fun remove(actor: Actor) {
        childParams.remove(actor)
        unWatchChild(actor)
        removeActor(actor)
        invalidate()
    }

    fun clearItems() {
        childParams.clear()
        snapActors.clear()
        snapArrays.clear()
        clearChildren()
        invalidate()
    }

    // ── getPrefWidth/Height ───────────────────────────────────────────────────

    override fun getPrefWidth()  = width
    override fun getPrefHeight() = height

    // ── sizeChanged ───────────────────────────────────────────────────────────

    override fun sizeChanged() {
        super.sizeChanged()
        if (!isLayouting) invalidate()
    }

    // ── act(): snapshot ───────────────────────────────────────────────────────

    override fun act(delta: Float) {
        super.act(delta)
        if (snapActors.isEmpty()) return
        for (i in snapActors.indices) {
            val a = snapActors[i]; val s = snapArrays[i]
            if (s[0] != a.width || s[1] != a.height) {
                s[0] = a.width; s[1] = a.height
                anyChildChanged = true
            }
        }
        if (anyChildChanged) { anyChildChanged = false; invalidate() }
    }

    // ── layout() — точка входу ────────────────────────────────────────────────

    override fun layout() {
        if (children.isEmpty) return
        if (isLayouting) return
        isLayouting = true

        val items = children.toList().let { if (reverse) it.reversed() else it }
        val isH   = direction == Direction.HORIZONTAL

        if (wrap) layoutWrap(items, isH)
        else      layoutNoWrap(items, isH)

        isLayouting = false
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NO WRAP
    // ─────────────────────────────────────────────────────────────────────────

    private fun layoutNoWrap(items: List<Actor>, isH: Boolean) {
        // 1. STRETCH: розтягуємо дітей на всю поперечну вісь
        val crossTotal = if (isH) innerHeight else innerWidth
        items.forEach { actor ->
            if (effectiveCross(actor) == AlignCross.STRETCH) {
                if (isH) actor.height = crossTotal
                else     actor.width  = crossTotal
            }
        }

        // 2. GROW: розподіляємо вільний простір
        applyGrow(items, isH)

        // 3. HUG: підлаштовуємо розмір групи
        applyHugNoWrap(items, isH)

        // 4. Розставляємо
        val mainTotal = totalMain(items, isH)
        val free      = (mainSpace(isH) - mainTotal).coerceAtLeast(0f)
        val gap       = mainGap(free, items.size)

        if (isH) {
            // HORIZONTAL: зліва направо
            var x = paddingStart + mainAlignOffset(free, items.size)
            for (actor in items) {
                val cPos = crossPos(actor, actor.height, crossTotal, isH, crossBase = paddingBottom)
                actor.setPosition(x, cPos)
                x += actor.width + gap
            }
        } else {
            // VERTICAL: зверху вниз (як в Figma/CSS)
            // START = елементи зверху, END = елементи знизу (вільний простір зверху)
            var y = height - paddingTop - mainAlignOffset(free, items.size)
            for (actor in items) {
                y -= actor.height
                val cPos = crossPos(actor, actor.width, crossTotal, isH, crossBase = paddingStart)
                actor.setPosition(cPos, y)
                y -= gap
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  WRAP
    // ─────────────────────────────────────────────────────────────────────────

    private fun layoutWrap(items: List<Actor>, isH: Boolean) {
        val lines = buildLines(items, isH)

        // Загальний розмір по поперечній осі
        val totalCross = lines.sumOf { it.crossSize.toDouble() }.toFloat() +
                gapCross * (lines.size - 1).coerceAtLeast(0)

        // HUG по поперечній осі
        var hugChanged = false
        if (isH && sizingH == Sizing.HUG) {
            val newH = totalCross + paddingTop + paddingBottom
            if (height != newH) { height = newH; hugChanged = true }
        }
        if (!isH && sizingW == Sizing.HUG) {
            val newW = totalCross + paddingStart + paddingEnd
            if (width != newW) { width = newW; hugChanged = true }
        }

        // HUG по головній осі (якщо потрібно)
        if (isH && sizingW == Sizing.HUG) {
            val maxLineMain = lines.maxOf { it.mainSize }
            val newW = maxLineMain + paddingStart + paddingEnd
            if (width != newW) { width = newW; hugChanged = true }
        }
        if (!isH && sizingH == Sizing.HUG) {
            val maxLineMain = lines.maxOf { it.mainSize }
            val newH = maxLineMain + paddingTop + paddingBottom
            if (height != newH) { height = newH; hugChanged = true }
        }
        if (hugChanged) invalidateHierarchy()
        // Обмежуємо розмір після HUG
        applyMinMax()

        // Розставляємо рядки
        if (isH) {
            // HORIZONTAL wrap: рядки йдуть зверху вниз
            // LibGDX Y=0 знизу — перший рядок починається зверху
            // currentLineBottom = Y нижнього краю поточного рядка
            var currentLineBottom = height - paddingTop - lines[0].crossSize
            for ((i, line) in lines.withIndex()) {
                layoutLine(line, line.crossSize, currentLineBottom, isH)
                if (i + 1 < lines.size) {
                    currentLineBottom -= gapCross + lines[i + 1].crossSize
                }
            }
        } else {
            // VERTICAL wrap: колонки йдуть зліва направо
            // crossBase для VERTICAL = X лівого краю поточної колонки
            var currentColLeft = paddingStart
            for (line in lines) {
                layoutLine(line, line.crossSize, currentColLeft, isH)
                currentColLeft += line.crossSize + gapCross
            }
        }
    }

    // Розставляємо один рядок/колонку
    // crossBase: для HORIZONTAL = Y нижнього краю рядка
    //            для VERTICAL   = X лівого краю колонки
    private fun layoutLine(line: Line, crossSize: Float, crossBase: Float, isH: Boolean) {
        // STRETCH в межах рядка
        line.actors.forEach { actor ->
            if (effectiveCross(actor) == AlignCross.STRETCH) {
                if (isH) actor.height = crossSize
                else     actor.width  = crossSize
            }
        }

        val mainTotal = totalMain(line.actors, isH)
        val free      = (mainSpace(isH) - mainTotal).coerceAtLeast(0f)
        val gap       = mainGap(free, line.actors.size)

        if (isH) {
            var x = paddingStart + mainAlignOffset(free, line.actors.size)
            for (actor in line.actors) {
                val cPos = crossPos(actor, actor.height, crossSize, isH, crossBase = crossBase)
                actor.setPosition(x, cPos)
                x += actor.width + gap
            }
        } else {
            // VERTICAL: зверху вниз
            // crossBase = X лівого краю поточної колонки
            // crossSize = ширина колонки (максимальна ширина елемента в колонці)
            var y = height - paddingTop - mainAlignOffset(free, line.actors.size)
            for (actor in line.actors) {
                y -= actor.height
                val cPos = crossPos(actor, actor.width, crossSize, isH, crossBase = crossBase)
                actor.setPosition(cPos, y)
                y -= gap
            }
        }
    }

    // ── Line building ─────────────────────────────────────────────────────────

    private data class Line(
        val actors   : List<Actor>,
        val mainSize : Float,
        var crossSize: Float,
    )

    private fun buildLines(items: List<Actor>, isH: Boolean): List<Line> {
        val lines      = ArrayList<Line>()
        var rowActors  = ArrayList<Actor>()
        var rowMain    = 0f
        var rowCross   = 0f
        val available  = mainSpace(isH)

        for (actor in items) {
            val aMain  = if (isH) actor.width  else actor.height
            val aCross = if (isH) actor.height else actor.width
            val needed = if (rowActors.isEmpty()) aMain else rowMain + gapMain + aMain

            if (needed > available && rowActors.isNotEmpty()) {
                lines.add(Line(rowActors, rowMain, rowCross))
                rowActors = ArrayList(); rowMain = 0f; rowCross = 0f
            }

            rowActors.add(actor)
            rowMain  = if (rowActors.size == 1) aMain else rowMain + gapMain + aMain
            rowCross = maxOf(rowCross, aCross)
        }
        if (rowActors.isNotEmpty()) lines.add(Line(rowActors, rowMain, rowCross))
        return lines
    }

    // ── HUG для NoWrap ────────────────────────────────────────────────────────

    private fun applyHugNoWrap(items: List<Actor>, isH: Boolean) {
        var changed = false
        if (sizingW == Sizing.HUG) {
            val newW = if (isH) totalMain(items, true) + paddingStart + paddingEnd
            else     (items.maxOfOrNull { it.width } ?: 0f) + paddingStart + paddingEnd
            if (width != newW) { width = newW; changed = true }
        }
        if (sizingH == Sizing.HUG) {
            val newH = if (!isH) totalMain(items, false) + paddingTop + paddingBottom
            else      (items.maxOfOrNull { it.height } ?: 0f) + paddingTop + paddingBottom
            if (height != newH) { height = newH; changed = true }
        }
        // Сповіщаємо батька (наприклад AConstraintLayout) що наш розмір змінився
        // Це дозволяє батьку перерахувати нашу позицію (center, topToBottom тощо)
        if (changed) invalidateHierarchy()
        // Обмежуємо розмір після HUG
        applyMinMax()
    }

    // ── Min/Max clamp ─────────────────────────────────────────────────────────
    // Викликається після HUG щоб обмежити розмір групи
    private fun applyMinMax() {
        val newW = width.coerceIn(minW, maxW)
        val newH = height.coerceIn(minH, maxH)
        if (newW != width || newH != height) {
            if (newW != width)  width  = newW
            if (newH != height) height = newH
            invalidateHierarchy()
        }
    }

    // ── GROW ──────────────────────────────────────────────────────────────────

    private fun applyGrow(items: List<Actor>, isH: Boolean) {
        val totalGrow = items.sumOf { (childParams[it]?.grow ?: 0f).toDouble() }.toFloat()
        if (totalGrow <= 0f) return
        val free = (mainSpace(isH) - totalMain(items, isH)).coerceAtLeast(0f)
        if (free <= 0f) return
        items.forEach { actor ->
            val grow = childParams[actor]?.grow ?: 0f
            if (grow > 0f) {
                val extra = free * (grow / totalGrow)
                if (isH) actor.width  += extra else actor.height += extra
            }
        }
    }

    // ── Position helpers ──────────────────────────────────────────────────────

    // Доступний простір по головній осі (innerWidth або innerHeight)
    private fun mainSpace(isH: Boolean) = if (isH) innerWidth else innerHeight

    // Стартова позиція на головній осі
    private fun mainStartPos(isH: Boolean) = if (isH) paddingStart else paddingBottom

    // Зміщення для CENTER/END
    private fun mainAlignOffset(free: Float, count: Int) = when (alignMain) {
        AlignMain.CENTER       -> free / 2f
        AlignMain.END          -> free
        AlignMain.SPACE_AROUND -> free / count / 2f  // ← половина gap як відступ від краю
        else                   -> 0f
    }

    // Gap між елементами
    private fun mainGap(free: Float, count: Int) = when (alignMain) {
        AlignMain.SPACE_BETWEEN -> if (count > 1) gapMain + free / (count - 1) else gapMain
        AlignMain.SPACE_AROUND  -> gapMain + free / count  // повний gap між елементами
        else                    -> gapMain
    }

    // Позиція по поперечній осі
    // crossBase: для HORIZONTAL = Y нижнього краю рядка/групи
    //            для VERTICAL   = X лівого краю колонки/групи
    private fun crossPos(
        actor    : Actor,
        crossLen : Float,
        crossSize: Float,
        isH      : Boolean,
        crossBase: Float,
    ): Float {
        return when (effectiveCross(actor)) {
            AlignCross.START   -> crossBase
            AlignCross.CENTER  -> crossBase + (crossSize - crossLen) / 2f
            AlignCross.END     -> crossBase + crossSize - crossLen
            AlignCross.STRETCH -> crossBase
        }
    }

    private fun effectiveCross(actor: Actor): AlignCross {
        return when (childParams[actor]?.alignSelf ?: AlignSelf.AUTO) {
            AlignSelf.AUTO    -> alignCross
            AlignSelf.START   -> AlignCross.START
            AlignSelf.CENTER  -> AlignCross.CENTER
            AlignSelf.END     -> AlignCross.END
            AlignSelf.STRETCH -> AlignCross.STRETCH
        }
    }

    // ── Size helpers ──────────────────────────────────────────────────────────

    private val innerWidth  get() = (width  - paddingStart - paddingEnd).coerceAtLeast(0f)
    private val innerHeight get() = (height - paddingTop   - paddingBottom).coerceAtLeast(0f)

    private fun totalMain(items: List<Actor>, isH: Boolean): Float {
        if (items.isEmpty()) return 0f
        val sum  = items.sumOf { (if (isH) it.width else it.height).toDouble() }.toFloat()
        val gaps = gapMain * (items.size - 1).coerceAtLeast(0)
        return sum + gaps
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    private fun watchChild(actor: Actor) {
        if (actor in snapActors) return
        snapActors.add(actor)
        snapArrays.add(floatArrayOf(actor.width, actor.height))
    }

    private fun unWatchChild(actor: Actor) {
        val i = snapActors.indexOf(actor)
        if (i >= 0) { snapActors.removeAt(i); snapArrays.removeAt(i) }
    }

    override fun childrenChanged() {
        super.childrenChanged()
        val current = children.toList()
        snapActors.filter { it !in current }.forEach { unWatchChild(it) }
        current.forEach { if (it !in snapActors) watchChild(it) }
        childParams.keys.retainAll(current.toSet())   // ← прибрати параметри зниклих
        invalidate()
    }

    // ── Dispose ───────────────────────────────────────────────────────────────

    override fun dispose() {
        childParams.clear()
        snapActors.clear()
        snapArrays.clear()
        super.dispose()
    }
}