package com.lewydo.orbitdash.game.actors.button.base

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.manager.util.SoundUtil
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

// Базовий — тільки touch логіка, без анімацій
abstract class AButtonBase(
    override val screen: AdvancedScreen,
) : AdvancedGroup() {

    var onTouchDown    : AButtonBase.(x: Float, y: Float) -> Unit = { _, _ -> }
    var onTouchDragged : AButtonBase.(x: Float, y: Float) -> Unit = { _, _ -> }
    var onTouchUp      : AButtonBase.(x: Float, y: Float) -> Unit = { _, _ -> }

    private var onClickBlock : () -> Unit = {}
    private var clickSound   : SoundUtil.AdvancedSound? = null

    private val scrollThreshold = 10f
    private var startX    = 0f
    private var startY    = 0f
    private var isDragged = false

    override fun addActorsOnGroup() {
        setOrigin(Align.center)
        addListener(buildListener())
    }

    private fun buildListener() = object : InputListener() {
        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            startX = x; startY = y; isDragged = false
            press()
            onTouchDown(x, y)
            return true
        }
        override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
            onTouchDragged(x, y)
            val dx = x - startX; val dy = y - startY
            if (dx * dx + dy * dy > scrollThreshold * scrollThreshold) {
                if (!isDragged) { isDragged = true; unpress() }
            }
        }
        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            onTouchUp(x, y)
            unpress()
            if (!isDragged) {
                clickSound?.let { gdxGame.soundUtil.play(it) }
                onClickBlock()
            }
        }
    }

    abstract fun press()
    abstract fun unpress()
    abstract fun disable()
    abstract fun enable()

    fun setOnClickListener(
        sound: SoundUtil.AdvancedSound? = gdxGame.soundUtil.CLICK,
        block: () -> Unit,
    ) {
        clickSound   = sound
        onClickBlock = block
    }
}