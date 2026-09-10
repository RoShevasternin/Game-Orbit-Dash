package com.lewydo.orbitdash.game.actors.layout.constraintLayout

import com.badlogic.gdx.scenes.scene2d.Actor
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

// ═════════════════════════════════════════════════════════════════════════════
//  AConstraintLayout  —  оптимізована версія (push-модель + кешовані anchors)
//
//  ОПТИМІЗАЦІЇ:
//    1. anyDirty прапорець     — act() виходить за 1 перевірку якщо нічого не змінилось
//    2. Node.anchors кеш       — allAnchors() не створює новий List кожен виклик
//    3. anchorSnapshots        — відстежуємо тільки зовнішні anchor-и, не всіх акторів
//    4. hasExternalAnchors     — checkAnchors() пропускається якщо нема зовнішніх anchor-ів
//    5. markDependentsDirty    — dirty тільки залежні від зміненого anchor
//    6. isLayouting guard      — захист від рекурсії layout() → setSize → sizeChanged → layout()
//    7. node.dirty = false ДО resolveNode — якщо resolve знову помітить dirty, він піде в чергу
//    8. Нуль алокацій hot path — ArrayList reuse, без filter/map
//    9. childSnapshots         — відстежуємо розміри дітей (для HUG дітей типу AAutoLayout)
//   10. childrenChanged()      — ЄДИНИЙ шов синхронізації складу дітей (див. нижче)
//   11. isAdding guard         — add() не тягне повну перебудову списків спостереження
//   12. HashMap-lookup         — watchChild/watchAnchor за O(1), а не лінійним пошуком
//   13. Збереження snapshot-ів — rebuildWatchLists() не губить необроблені зміни розміру
//
//  ЧОМУ childrenChanged(), А НЕ removeActor():
//  libGDX видаляє акторів кількома шляхами — removeActor(actor),
//  removeActor(actor, unfocus) (саме він працює при перенесенні в іншу групу)
//  і clearChildren(). Перекриття однієї перевантаженої версії ловить не всі.
//  childrenChanged() кличеться з УСІХ, тому вся синхронізація живе там.
//
//  ЗНИКНЕННЯ ANCHOR-А НЕ ВБИВАЄ ЗАЛЕЖНОГО: посилання обнуляється, вузол
//  лишається живим. Актор застигає по тій осі, що втратила anchor, але
//  друга вісь, resize і update() працюють далі.
//
//  ПОЗИЦІЯ ДІТЕЙ НЕ ВІДСТЕЖУЄТЬСЯ — свідомо. Layout реагує на зміну РОЗМІРУ
//  дитини, але не на зміну її x/y. Тому актор можна анімувати через
//  Actions.moveBy / scaleTo, і констрейнт не буде з цим боротися.
//  Увага: layout() перераховує ВСІ вузли безумовно, тож абсолютний moveTo
//  краще не використовувати — після випадкового invalidateHierarchy() буде ривок.
//
//  ПОРЯДОК LAYOUT ДЛЯ КОЖНОГО АКТОРА:
//    1. Виставити розмір (dimension resolution)
//    2. Виставити позицію (constraint resolution)
//
//  ВАЖЛИВО: якщо B залежить від A → add(A) перед add(B).
//  PUBLIC API — ідентичний попередній версії, клієнтський код не треба міняти.
// ═════════════════════════════════════════════════════════════════════════════

open class AConstraintLayout(override val screen: AdvancedScreen) : AdvancedGroup() {

    // ── Внутрішній вузол ──────────────────────────────────────────────────────
    private class Node(val actor: Actor, val params: CLParams) {
        var dirty: Boolean = true

        // Кешований список anchor-ів — оновлюється тільки в add() і update()
        // В hot path (markDependentsDirty) — нуль алокацій
        var anchors: List<Actor> = params.allAnchors()
            private set

        fun refreshAnchors() {
            anchors = params.allAnchors()
        }
    }

    // LinkedHashMap — зберігає порядок додавання (важливо: B залежить від A)
    private val nodes = LinkedHashMap<Actor, Node>()

    // Черга вузлів що потребують перерахунку
    private val dirtyQueue = ArrayDeque<Node>()

    // Швидкий прапорець — чи є взагалі що обробляти цього кадру
    private var anyDirty = false

    // Захист від рекурсії: layout() → resolveNode → setSize → sizeChanged → layout()
    private var isLayouting = false

    // Захист від зайвої роботи: addActor() всередині add() стріляє childrenChanged(),
    // але add() і так реєструє актора вручну — повна перебудова там непотрібна.
    private var isAdding = false

    // Snapshot-и тільки для ЗОВНІШНІХ anchor-ів (не this)
    // layout (this) відстежується через sizeChanged() — без snapshot
    private val anchorSnapshots  = HashMap<Actor, FloatArray>()
    private val anchorActorsList = ArrayList<Actor>()
    private val anchorArraysList = ArrayList<FloatArray>()

    // Оптимізація: якщо нема зовнішніх anchor-ів — checkAnchors() не викликається взагалі
    private var hasExternalAnchors = false

    // Snapshot-и для дітей layout — відстежуємо зміни їх розміру.
    // Потрібно для HUG дітей (наприклад AAutoLayout) — коли дитина змінює розмір,
    // її позиція (center, topToBottom тощо) має автоматично перерахуватись.
    private val childSnapshots  = HashMap<Actor, FloatArray>()
    private val childSnapActors = ArrayList<Actor>()
    private val childSnapArrays = ArrayList<FloatArray>()

    // ── Public API ────────────────────────────────────────────────────────────

    fun add(actor: Actor, block: CLParams.() -> Unit): Actor {
        val params = CLParams(this).apply(block)

        if (params.widthMode == Dimension.FIXED && params.heightMode == Dimension.FIXED) {
            require(actor.width > 0f && actor.height > 0f) {
                "AConstraintLayout.add(): встанови setSize() або використай fillParent()/fillWidth()/fillHeight().\n" +
                        "Actor: ${actor::class.simpleName}"
            }
        }

        val node = Node(actor, params)
        nodes[actor] = node

        // Реєструємо тільки зовнішні anchor-и (this відстежується через sizeChanged)
        node.anchors.forEach { anchor ->
            if (anchor !== this) watchAnchor(anchor)
        }

        // isAdding — щоб childrenChanged() не тягнув повну перебудову списків:
        // реєстрація актора йде вручну наступним рядком
        isAdding = true
        addActor(actor)
        isAdding = false

        watchChild(actor) // відстежуємо розмір дитини

        // Виставляємо розмір і позицію одразу — без кадру затримки
        resolveNode(node)
        node.dirty = false

        return actor
    }

    fun update(actor: Actor, block: CLParams.() -> Unit) {
        val node = nodes[actor] ?: return
        node.params.apply(block)
        node.refreshAnchors()
        node.anchors.forEach { anchor ->
            if (anchor !== this) watchAnchor(anchor)
        }
        resolveNode(node)
        node.dirty = false
    }

    /**
     * Зняти актора з керування лейаутом, лишивши його дитиною групи.
     * Залежні від нього вузли лишаються живими — їхні посилання на цей
     * актор обнуляються в syncWithChildren().
     */
    fun detach(actor: Actor) {
        nodes.remove(actor)
        syncWithChildren()
    }

    fun clearConstrained() {
        nodes.clear()
        dirtyQueue.clear()
        anyDirty = false

        anchorSnapshots.clear()
        anchorActorsList.clear()
        anchorArraysList.clear()
        hasExternalAnchors = false

        childSnapshots.clear()
        childSnapActors.clear()
        childSnapArrays.clear()

        clearChildren()
    }

    override fun addActorsOnGroup() {}

    // ── childrenChanged: склад дітей змінився ────────────────────────────────
    // Єдина точка синхронізації. Ловить обидві версії removeActor(), addActor()
    // і clearChildren() — тому перекривати removeActor() більше не потрібно.

    override fun childrenChanged() {
        super.childrenChanged()
        if (isLayouting || isAdding) return
        syncWithChildren()
    }

    /**
     * Привести внутрішній стан до фактичного складу дітей:
     *   1. викинути вузли акторів, яких уже нема серед дітей
     *   2. обнулити посилання на зниклі anchor-и (вузол ЛИШАЄТЬСЯ живим)
     *   3. перебудувати списки спостереження — без посилань на мертвих
     */
    private fun syncWithChildren() {
        if (nodes.isNotEmpty()) {
            val it = nodes.keys.iterator()
            while (it.hasNext()) if (it.next().parent !== this) it.remove()
        }

        nodes.values.forEach { node ->
            val p = node.params
            var touched = false
            if (isDead(p.startToStartActor))   { p.startToStartActor   = null; touched = true }
            if (isDead(p.startToEndActor))     { p.startToEndActor     = null; touched = true }
            if (isDead(p.endToEndActor))       { p.endToEndActor       = null; touched = true }
            if (isDead(p.endToStartActor))     { p.endToStartActor     = null; touched = true }
            if (isDead(p.topToTopActor))       { p.topToTopActor       = null; touched = true }
            if (isDead(p.topToBottomActor))    { p.topToBottomActor    = null; touched = true }
            if (isDead(p.bottomToBottomActor)) { p.bottomToBottomActor = null; touched = true }
            if (isDead(p.bottomToTopActor))    { p.bottomToTopActor    = null; touched = true }
            if (touched) { node.refreshAnchors(); scheduleDirty(node) }
        }

        rebuildWatchLists()
    }

    /** Anchor мертвий, якщо це не сам layout і вже не його дитина. */
    private fun isDead(a: Actor?) = a != null && a !== this && a.parent !== this

    /**
     * Перебудувати обидва списки спостереження з нуля, викинувши мертвих.
     *
     * Snapshot-масиви для акторів, що лишилися, ПЕРЕВИКОРИСТОВУЮТЬСЯ, а не
     * створюються заново. Інакше зміна розміру, яку checkChildren()/checkAnchors()
     * ще не встиг обробити, була б перезаписана поточним значенням і загубилась.
     */
    private fun rebuildWatchLists() {
        // ── anchor-и ──
        val oldAnchors = HashMap(anchorSnapshots)
        anchorSnapshots.clear(); anchorActorsList.clear(); anchorArraysList.clear()
        hasExternalAnchors = false

        nodes.values.forEach { node ->
            node.anchors.forEach { anchor ->
                if (anchor !== this && anchor !in anchorSnapshots) {
                    val snap = oldAnchors[anchor]
                        ?: floatArrayOf(anchor.x, anchor.y, anchor.width, anchor.height)
                    anchorSnapshots[anchor] = snap
                    anchorActorsList.add(anchor)
                    anchorArraysList.add(snap)
                    hasExternalAnchors = true
                }
            }
        }

        // ── діти ──
        val oldChildren = HashMap(childSnapshots)
        childSnapshots.clear(); childSnapActors.clear(); childSnapArrays.clear()

        children.forEach { actor ->
            if (actor !in childSnapshots) {
                val snap = oldChildren[actor] ?: floatArrayOf(actor.width, actor.height)
                childSnapshots[actor] = snap
                childSnapActors.add(actor)
                childSnapArrays.add(snap)
            }
        }
    }

    // ── sizeChanged: розмір layout змінився ──────────────────────────────────

    override fun sizeChanged() {
        super.sizeChanged()
        if (isLayouting) return
        if (nodes.isNotEmpty()) {
            nodes.values.forEach { scheduleDirty(it) }
        }
    }

    // ── act(): тільки dirty вузли ─────────────────────────────────────────────

    override fun act(delta: Float) {
        super.act(delta)

        // Перевіряємо чи змінився розмір якоїсь дитини (наприклад AAutoLayout з HUG)
        checkChildren()

        if (hasExternalAnchors) checkAnchors()

        if (!anyDirty) return

        isLayouting = true
        while (dirtyQueue.isNotEmpty()) {
            val node = dirtyQueue.removeFirst()
            if (!node.dirty) continue
            node.dirty = false
            resolveNode(node)
        }
        isLayouting = false

        anyDirty = dirtyQueue.isNotEmpty()
    }

    // layout() — LibGDX може викликати після invalidate() або зміни розміру.
    override fun layout() {
        if (isLayouting) return
        isLayouting = true
        nodes.values.forEach { node ->
            node.dirty = false
            resolveNode(node)
        }
        dirtyQueue.clear()
        anyDirty = false
        isLayouting = false
    }

    // ── Resolve: розмір → позиція ─────────────────────────────────────────────

    private fun resolveNode(node: Node) {
        applyDimension(node.actor, node.params)
        applyPosition(node.actor, node.params)
    }

    // ── Dimension resolution ──────────────────────────────────────────────────

    private fun applyDimension(actor: Actor, p: CLParams) {
        val newW = when (p.widthMode) {
            Dimension.FIXED -> actor.width
            Dimension.MATCH_PARENT -> width
            Dimension.PERCENT -> width * p.widthPercent
            Dimension.SCALED -> p.toActual(p.designWidth)
            Dimension.MATCH_CONSTRAINT -> resolveMatchWidth(p)
        }
        val newH = when (p.heightMode) {
            Dimension.FIXED -> actor.height
            Dimension.MATCH_PARENT -> height
            Dimension.PERCENT -> height * p.heightPercent
            Dimension.SCALED -> p.toActual(p.designHeight)
            Dimension.MATCH_CONSTRAINT -> resolveMatchHeight(p)
        }
        if (newW != actor.width || newH != actor.height) {
            actor.setSize(newW, newH)
        }
    }

    private fun resolveMatchWidth(p: CLParams): Float {
        val left = when {
            p.startToStartActor != null -> edgeLeft(p.startToStartActor!!) + p.marginStart
            p.startToEndActor != null -> edgeRight(p.startToEndActor!!) + p.marginStart
            else -> p.marginStart
        }
        val right = when {
            p.endToEndActor != null -> edgeRight(p.endToEndActor!!) - p.marginEnd
            p.endToStartActor != null -> edgeLeft(p.endToStartActor!!) - p.marginEnd
            else -> width - p.marginEnd
        }
        return (right - left).coerceAtLeast(0f)
    }

    private fun resolveMatchHeight(p: CLParams): Float {
        val top = when {
            p.topToTopActor != null -> edgeTop(p.topToTopActor!!) - p.marginTop
            p.topToBottomActor != null -> edgeBottom(p.topToBottomActor!!) - p.marginTop
            else -> height - p.marginTop
        }
        val bottom = when {
            p.bottomToBottomActor != null -> edgeBottom(p.bottomToBottomActor!!) + p.marginBottom
            p.bottomToTopActor != null -> edgeTop(p.bottomToTopActor!!) + p.marginBottom
            else -> p.marginBottom
        }
        return (top - bottom).coerceAtLeast(0f)
    }

    // ── Position resolution ───────────────────────────────────────────────────

    private fun applyPosition(actor: Actor, p: CLParams) {
        actor.setPosition(resolveX(actor, p), resolveY(actor, p))
    }

    private fun resolveX(actor: Actor, p: CLParams): Float {
        val w = actor.width

        if (p.widthMode == Dimension.MATCH_CONSTRAINT) {
            return when {
                p.startToStartActor != null -> edgeLeft(p.startToStartActor!!) + p.marginStart
                p.startToEndActor != null -> edgeRight(p.startToEndActor!!) + p.marginStart
                else -> p.marginStart
            }
        }

        val startX: Float? = when {
            p.startToStartActor != null -> edgeLeft(p.startToStartActor!!) + p.marginStart
            p.startToEndActor != null -> edgeRight(p.startToEndActor!!) + p.marginStart
            else -> null
        }
        val endX: Float? = when {
            p.endToEndActor != null -> edgeRight(p.endToEndActor!!) - w - p.marginEnd
            p.endToStartActor != null -> edgeLeft(p.endToStartActor!!) - w - p.marginEnd
            else -> null
        }
        return when {
            startX != null && endX != null -> startX + (endX - startX) * p.horizontalBias
            startX != null -> startX
            endX != null -> endX
            else -> actor.x
        }
    }

    private fun resolveY(actor: Actor, p: CLParams): Float {
        val h = actor.height

        if (p.heightMode == Dimension.MATCH_CONSTRAINT) {
            return when {
                p.bottomToBottomActor != null -> edgeBottom(p.bottomToBottomActor!!) + p.marginBottom
                p.bottomToTopActor != null -> edgeTop(p.bottomToTopActor!!) + p.marginBottom
                else -> p.marginBottom
            }
        }

        val bottomY: Float? = when {
            p.bottomToBottomActor != null -> edgeBottom(p.bottomToBottomActor!!) + p.marginBottom
            p.bottomToTopActor != null -> edgeTop(p.bottomToTopActor!!) + p.marginBottom
            else -> null
        }
        val topY: Float? = when {
            p.topToTopActor != null -> edgeTop(p.topToTopActor!!) - h - p.marginTop
            p.topToBottomActor != null -> edgeBottom(p.topToBottomActor!!) - h - p.marginTop
            else -> null
        }
        return when {
            bottomY != null && topY != null -> bottomY + (topY - bottomY) * p.verticalBias
            bottomY != null -> bottomY
            topY != null -> topY
            else -> actor.y
        }
    }

    // ── Edge helpers ──────────────────────────────────────────────────────────

    private fun edgeLeft(a: Actor) = if (a === this) 0f else a.x
    private fun edgeRight(a: Actor) = if (a === this) width else a.x + a.width
    private fun edgeBottom(a: Actor) = if (a === this) 0f else a.y
    private fun edgeTop(a: Actor) = if (a === this) height else a.y + a.height

    // ── Anchor watching ───────────────────────────────────────────────────────

    private fun watchAnchor(anchor: Actor) {
        if (anchor in anchorSnapshots) return
        val snap = floatArrayOf(anchor.x, anchor.y, anchor.width, anchor.height)
        anchorSnapshots[anchor] = snap
        anchorActorsList.add(anchor)
        anchorArraysList.add(snap)
        hasExternalAnchors = true
    }

    private fun checkAnchors() {
        for (i in anchorActorsList.indices) {
            val anchor = anchorActorsList[i]
            val snap = anchorArraysList[i]
            if (snap[0] != anchor.x || snap[1] != anchor.y ||
                snap[2] != anchor.width || snap[3] != anchor.height
            ) {
                snap[0] = anchor.x; snap[1] = anchor.y
                snap[2] = anchor.width; snap[3] = anchor.height
                markDependentsDirty(anchor)
            }
        }
    }

    // ── Child watching ────────────────────────────────────────────────────────
    // Відстежуємо зміни РОЗМІРУ дітей — необхідно коли дитина сама змінює свій
    // розмір (наприклад AAutoLayout з sizingW/H = HUG). В такому разі позиція
    // дитини (center, topToBottom тощо) має автоматично перерахуватись.
    //
    // Позиція (x/y) і scale дітей НЕ відстежуються — це навмисно, щоб анімації
    // через Actions.moveBy / scaleTo не конфліктували з констрейнтами.

    private fun watchChild(actor: Actor) {
        if (actor in childSnapshots) return
        val snap = floatArrayOf(actor.width, actor.height)
        childSnapshots[actor] = snap
        childSnapActors.add(actor)
        childSnapArrays.add(snap)
    }

    private fun checkChildren() {
        for (i in childSnapActors.indices) {
            val actor = childSnapActors[i]
            val snap = childSnapArrays[i]
            if (snap[0] != actor.width || snap[1] != actor.height) {
                snap[0] = actor.width
                snap[1] = actor.height
                // Помічаємо цей actor dirty — його позиція залежить від його розміру
                nodes[actor]?.let { scheduleDirty(it) }
                // Помічаємо всіх хто використовує цей actor як anchor
                markDependentsDirty(actor)
            }
        }
    }

    // ── Dirty propagation ─────────────────────────────────────────────────────

    private fun markDependentsDirty(changedAnchor: Actor) {
        nodes.values.forEach { node ->
            if (changedAnchor in node.anchors) scheduleDirty(node)
        }
    }

    private fun scheduleDirty(node: Node) {
        if (!node.dirty) {
            node.dirty = true
            dirtyQueue.addLast(node)
            anyDirty = true
        }
    }

    // ── Dispose ───────────────────────────────────────────────────────────────

    override fun dispose() {
        nodes.clear()
        dirtyQueue.clear()
        anyDirty = false

        anchorSnapshots.clear()
        anchorActorsList.clear()
        anchorArraysList.clear()
        hasExternalAnchors = false

        childSnapshots.clear()
        childSnapActors.clear()
        childSnapArrays.clear()

        super.dispose()
    }
}