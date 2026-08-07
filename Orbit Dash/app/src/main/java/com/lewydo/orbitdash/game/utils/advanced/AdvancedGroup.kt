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

    open val sizeScaler: SizeScaler = SizeScaler(SizeScaler.Axis.X, 1f)

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
        tryInitGroup()
        for (i in fillActors.indices) fillActors[i].setSize(width, height)
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

    protected fun Actor.setBoundsScaled(x: Float, y: Float, width: Float, height: Float) {
        setBounds(x.toActual, y.toActual, width.toActual, height.toActual)
    }

    protected fun Actor.setBoundsScaled(position: Vector2, size: Vector2) {
        setBoundsScaled(position.x, position.y, size.x, size.y)
    }

    protected fun Actor.setSizeScaled(width: Float, height: Float) {
        setSize(width.toActual, height.toActual)
    }

    fun interface Drawer {
        fun draw(alpha: Float)
    }

}