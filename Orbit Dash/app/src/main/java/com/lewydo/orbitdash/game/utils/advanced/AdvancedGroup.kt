package com.lewydo.orbitdash.game.utils.advanced

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.disposeAll
import com.lewydo.orbitdash.util.cancelCoroutinesAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean

abstract class AdvancedGroup : WidgetGroup(), Disposable {
    abstract val screen: AdvancedScreen

    /** Дизайн-фрейм групи (Figma). 0 = фрейму нема: factor = 1, числа дітей — юніти сцени як є. */
    open val sizeScaler: SizeScaler = SizeScaler(SizeScaler.Axis.X, 0f)

    /** Лінивий скоуп: створюється лише коли група реально ним користується. */
    private var _coroutine: CoroutineScope? = null
    val coroutine: CoroutineScope?
        get() {
            if (_coroutine == null && !isDisposed) _coroutine = CoroutineScope(Dispatchers.Default)
            return _coroutine
        }

    var isDisposed = false
        private set

    var isDisposeOnRemove = true

    val preDrawArray  = Array<Drawer>()
    val postDrawArray = Array<Drawer>()
    val disposableSet = mutableSetOf<Disposable>()

    val Vector2.toActual get() = sizeScaler.toActual(this)
    val Vector2.toDesign get() = sizeScaler.toDesign(this)
    val Float.toActual   get() = sizeScaler.toActual(this)
    val Float.toDesign   get() = sizeScaler.toDesign(this)

    private val onceInit = AtomicBoolean(true)

    private val mapIsTransform = mutableMapOf<AdvancedGroup, Boolean>()

    // Список акторів які мають заповнювати всю групу
    private val fillActors = mutableListOf<Actor>()

    // ------------------------------------------------------------------------
    // Дизайн-геометрія дітей. Той самий механізм, що fillActors, тільки замість
    // «100 % батька» — число з макета. Записали один раз через setSizeScaled /
    // setPositionScaled — група сама переприкладає при кожній зміні свого розміру.
    // Розмір і позиція — окремі слоти: позицію реєструють лише дітям поза
    // лейаутом; кому позицію веде код щокадру (орбіта) — тримає тільки розмір.
    // ------------------------------------------------------------------------
    private class DesignSpec {
        var w = -1f;        var h = -1f          // < 0  — розмір не тримаємо
        var x = Float.NaN;  var y = Float.NaN    // NaN — позицію не тримаємо
    }

    private val designSpecs = LinkedHashMap<Actor, DesignSpec>()

    // Похідні дизайн-величини, що не є геометрією актора: радіус кутів, товщина
    // обводки, blur, bleed. Слот під кожну заводити нема сенсу — список відкритий.
    // Тому блок: ставить їх через toActual, група кличе його одразу і після
    // applyDesignSpecs() при кожному sizeChanged(). Не hot path — лише на resize.
    private val designBlocks = ArrayList<() -> Unit>()

    /** Створення й додавання дітей. Викликається РІВНО ОДИН РАЗ. */
    abstract fun addActorsOnGroup()

    // ------------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------------
    override fun draw(batch: Batch?, parentAlpha: Float) {
        for (i in 0 until preDrawArray.size) preDrawArray[i].draw(parentAlpha * color.a)
        super.draw(batch, parentAlpha)
        for (i in 0 until postDrawArray.size) postDrawArray[i].draw(parentAlpha * color.a)
    }

    // ------------------------------------------------------------------------
    // Stage / init
    // ------------------------------------------------------------------------
    override fun setStage(stage: Stage?) {
        super.setStage(stage)
        tryInitGroup()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        tryInitGroup()   // свіжий factor і (один раз) addActorsOnGroup() — реєстр наповнюється тут
        for (i in fillActors.indices) fillActors[i].setSize(width, height)
        applyDesignSpecs()
        // Похідні — ПІСЛЯ геометрії: радіус може залежати від уже виставленого розміру
        for (i in designBlocks.indices) designBlocks[i]()
    }

    private fun tryInitGroup() {
        if (width > 0 && height > 0 && stage != null) {
            sizeScaler.calculateScale(Vector2(width, height))
            if (onceInit.getAndSet(false)) addActorsOnGroup()
        }
    }

    // ------------------------------------------------------------------------
    // Dispose
    // ------------------------------------------------------------------------
    override fun dispose() {
        if (isDisposed.not()) {
            preDrawArray.clear()
            postDrawArray.clear()

            fillActors.clear()
            designSpecs.clear()
            designBlocks.clear()

            disposableSet.disposeAll()
            disposableSet.clear()

            disposeAndClearChildren()

            cancelCoroutinesAll(coroutine)
            _coroutine = null

            isDisposed = true
        }
    }

    override fun remove(): Boolean {
        if (isDisposeOnRemove) dispose()
        return super.remove()
    }

    fun disposeAndClearChildren() {
        val snapshot = children.begin()
        for (i in 0 until children.size) {
            val actor = snapshot[i]
            if (actor is Disposable) actor.dispose()
        }
        children.end()
        clearChildren()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    fun addAndFillActor(actor: Actor) {
        addActor(actor)
        fillActors.add(actor)
        actor.setSize(width, height)
    }

    fun addAndFillActors(vararg actors: Actor) { actors.forEach { addAndFillActor(it) } }
    fun addAndFillActors(actors: List<Actor>)  { actors.forEach { addAndFillActor(it) } }

    // ------------------------------------------------------------------------
    // Transforms
    // ------------------------------------------------------------------------
    private fun setIsTransformAll(newIsTransform: Boolean, states: MutableMap<AdvancedGroup, Boolean>) {
        states[this] = isTransform
        isTransform  = newIsTransform

        children.begin()
        for (i in 0 until children.size) {
            val child = children[i]
            if(child is AdvancedGroup) child.setIsTransformAll(newIsTransform, states)
        }
        children.end()
    }

    private fun restoreTransforms(states: Map<AdvancedGroup, Boolean>) {
        for ((group, state) in states) {
            group.isTransform = state
        }
        mapIsTransform.clear()
    }

    /** -------------------------------------------------------------------------
    // Малює всіх дітей без застосування transform-матриці (position/scale/rotation
    // батька вже враховані через FBO camera).
    // Передаємо [parentAlpha] без множення на child.color.a — LibGDX Group.draw()
    // та всі Actor.draw() самі множать color.a * parentAlpha всередині.
    // Якщо помножити тут ще раз — alpha буде подвоєна.
    // ------------------------------------------------------------------------- */
    fun drawChildrenWithoutTransform(batch: Batch, parentAlpha: Float) {
        // Вимикаємо трансформації у всьому дереві
        setIsTransformAll(false, mapIsTransform)

        // Малюємо усіх дітей (усі групи без трансформацій)
        children.begin()
        for (i in 0 until children.size) {
            val child = children[i]
            if (child.isVisible) {
                child.draw(batch, parentAlpha)
            }
        }
        children.end()

        // Відновлюємо трансформації після малювання
        restoreTransforms(mapIsTransform)
    }

    // ------------------------------------------------------------------------
    // Дизайн-одиниці: поставити І ТРИМАТИ. internal, а не protected — CLParams.size()
    // пише в цей самий реєстр.
    // ------------------------------------------------------------------------

    /** Розмір у дизайн-одиницях. Група переприкладе його при кожному своєму sizeChanged(). */
    internal fun Actor.setSizeScaled(width: Float, height: Float) {
        designSpecs.getOrPut(this) { DesignSpec() }.also { it.w = width; it.h = height }
        setSize(width.toActual, height.toActual)
    }

    /** Позиція у дизайн-одиницях. Лише дітям поза лейаутом — констрейнти ставлять позицію самі. */
    internal fun Actor.setPositionScaled(x: Float, y: Float) {
        designSpecs.getOrPut(this) { DesignSpec() }.also { it.x = x; it.y = y }
        setPosition(x.toActual, y.toActual)
    }

    internal fun Actor.setBoundsScaled(x: Float, y: Float, width: Float, height: Float) {
        setSizeScaled(width, height)
        setPositionScaled(x, y)
    }

    internal fun Actor.setBoundsScaled(position: Vector2, size: Vector2) {
        setBoundsScaled(position.x, position.y, size.x, size.y)
    }

    /** Зняти з реєстру: далі геометрією керує код (анімація розміру, ручний layout). */
    internal fun Actor.freeScaled() { designSpecs.remove(this) }

    /**
     * Тримати похідні величини в дизайн-одиницях — те саме, що setSizeScaled, але
     * для того, чого в DesignSpec нема:
     *
     *     keepScaled { aBg.radius = 16f.toActual; aBg.strokeWidth = 2f.toActual }
     *
     * Блок виконується одразу і при кожному sizeChanged() після геометрії. Лише
     * присвоєння — без алокацій і важкої роботи. Повертає блок, щоб можна було
     * зняти через freeScaled(block), коли величину забирає анімація.
     */
    fun keepScaled(block: () -> Unit): () -> Unit {
        designBlocks.add(block)
        block()
        return block
    }

    /** Зняти блок з реєстру: далі величиною керує код. */
    fun freeScaled(block: () -> Unit) { designBlocks.remove(block) }

    private fun applyDesignSpecs() {
        if (designSpecs.isEmpty()) return
        for ((actor, s) in designSpecs) {
            // parent == null — актор у пулі (піпи щита): пам'ятаємо, застосуємо, як повернеться
            if (actor.parent !== this) continue
            if (s.w >= 0f)    actor.setSize(s.w.toActual, s.h.toActual)
            if (!s.x.isNaN()) actor.setPosition(s.x.toActual, s.y.toActual)
        }
    }

    fun interface Drawer {
        fun draw(alpha: Float)
    }

}