package com.lewydo.orbitdash.game.actors.checkbox.base

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.lewydo.orbitdash.game.manager.util.SoundUtil
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.actor.enable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.runGDX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

abstract class ACheckBoxBase(
    override val screen: AdvancedScreen,
) : AdvancedGroup() {

    // ------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------
    val checkFlow  = MutableStateFlow(false)
    val isChecked  get() = checkFlow.value

    var checkBoxGroup: ACheckBoxGroup? = null

    // ------------------------------------------------------------------------
    // Callbacks
    // ------------------------------------------------------------------------
    private var onCheckBlock       : (Boolean) -> Unit = {}
    private var checkSound         : SoundUtil.AdvancedSound? = null
    private var isInvokeCheckBlock : Boolean = true

    // ------------------------------------------------------------------------
    // Touch блоки
    // ------------------------------------------------------------------------
    var onTouchDown    : ACheckBoxBase.(x: Float, y: Float) -> Unit = { _, _ -> }
    var onTouchDragged : ACheckBoxBase.(x: Float, y: Float) -> Unit = { _, _ -> }
    var onTouchUp      : ACheckBoxBase.(x: Float, y: Float) -> Unit = { _, _ -> }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addListener(buildListener())
        collectCheckFlow()
    }

    private fun collectCheckFlow() {
        coroutine?.launch {
            checkFlow.collect { isCheck ->
                runGDX { if (isInvokeCheckBlock) onCheckBlock(isCheck) }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Listener — ScrollPane сумісний
    // ------------------------------------------------------------------------
    private fun buildListener() = object : InputListener() {
        private val scrollThreshold = 10f
        private var startX    = 0f
        private var startY    = 0f
        private var isDragged = false
        private var isWithin  = false

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            startX    = x
            startY    = y
            isDragged = false
            isWithin  = true
            onTouchDown(x, y)
            // без event?.stop() — ScrollPane отримує події
            return true
        }

        override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            isWithin = x in 0f..width && y in 0f..height
            onTouchDragged(x, y)

            val dx = x - startX
            val dy = y - startY
            if (dx * dx + dy * dy > scrollThreshold * scrollThreshold) {
                isDragged = true
            }
        }

        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            onTouchUp(x, y)

            if (isDragged || !isWithin) return

            // Звук тільки при реальному кліку
            checkSound?.let { gdxGame.soundUtil.play(it) }

            if (checkBoxGroup != null) {
                if (!isChecked) check()
            } else {
                toggle()
            }
        }
    }

    // ------------------------------------------------------------------------
    // Check / Uncheck / Toggle
    // ------------------------------------------------------------------------
    fun check(invokeBlock: Boolean = true) {
        isInvokeCheckBlock = invokeBlock
        checkBoxGroup?.onChecked(this)  // ← тепер група сама керує станом
        checkFlow.value = true
        onChecked()
    }

    fun uncheck(invokeBlock: Boolean = true) {
        isInvokeCheckBlock = invokeBlock
        checkFlow.value    = false
        onUnchecked()
    }

    fun toggle() {
        if (isChecked) uncheck() else check()
    }

    fun checkAndDisable()  { check();   disable() }
    fun uncheckAndEnable() { uncheck(); enable()  }

    fun setOnCheckListener(
        sound: SoundUtil.AdvancedSound? = gdxGame.soundUtil.CHECK_BOX,
        block: (Boolean) -> Unit,
    ) {
        checkSound   = sound
        onCheckBlock = block
    }

    // ------------------------------------------------------------------------
    // Abstract
    // ------------------------------------------------------------------------
    abstract fun onChecked()
    abstract fun onUnchecked()
}