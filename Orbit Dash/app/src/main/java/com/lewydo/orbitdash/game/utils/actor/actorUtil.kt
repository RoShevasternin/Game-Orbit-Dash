package com.lewydo.orbitdash.game.utils.actor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.button.base.AButtonBase
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.manager.util.SoundUtil
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.gdxGame

fun Actor.setOnClickListener(
    sound: SoundUtil.AdvancedSound? = gdxGame.soundUtil.CLICK,
    radius: Float = 10f,
    block: (Actor) -> Unit
) {
    addListener(object : ClickListener() {
        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (!super.touchDown(event, x, y, pointer, button)) return false
            sound?.let { gdxGame.soundUtil.play(it) }
            event.stop()
            return true
        }

        override fun clicked(event: InputEvent, x: Float, y: Float) = block(event.listenerActor)
    }.apply { tapSquareSize = radius })
}

// ------------------------------------------------------------------------
// disable | enable
// ------------------------------------------------------------------------

fun Actor.disable() = when(this) {
    is AButtonBase -> disable()
    else       -> touchable = Touchable.disabled
}

fun Actor.enable() = when(this) {
    is AButtonBase -> enable()
    else       -> touchable = Touchable.enabled
}

// ------------------------------------------------------------------------
// Default Params
// ------------------------------------------------------------------------

fun Actor.setBounds(bounds: Rectangle) {
    with(bounds) { setBounds(x, y, width, height) }
}

fun Actor.setBounds(position: Vector2, size: Vector2) {
    setBounds(position.x, position.y, size.x, size.y)
}

fun Actor.setPosition(position: Vector2) {
    setPosition(position.x, position.y)
}

fun Actor.setOrigin(vector: Vector2) {
    setOrigin(vector.x, vector.y)
}

fun Actor.setSize(vector: Vector2) {
    setSize(vector.x, vector.y)
}

/** Міняє RGB, лишаючи альфу актора недоторканою.
 *  Потрібно скрізь, де колір приходить із теми, а прозорість
 *  належить самому актору (glow-шари, приглушені стани, анімації). */
fun Actor.setColorRGB(color: Color) {
    this.color.set(color.r, color.g, color.b, this.color.a)
}

// ------------------------------------------------------------------------
// Animations
// ------------------------------------------------------------------------
fun Actor.animShow(time: Float = 0f, clearPrevious: Boolean = true, block: () -> Unit = {}) {
    if (clearPrevious) clearActions()
    isVisible = true
    addAction(Actions.sequence(Actions.fadeIn(time), Actions.run(block)))
}

fun Actor.animHide(time: Float = 0f, clearPrevious: Boolean = true, block: () -> Unit = {}) {
    if (clearPrevious) clearActions()
    addAction(Actions.sequence(Actions.fadeOut(time), Actions.run { isVisible = false; block() }))
}

fun Actor.animShowAndEnable(time: Float = 0f, block: () -> Unit = {}) {
    enable()
    animShow(time) { block() }
}

fun Actor.animHideAndDisable(time: Float = 0f, block: () -> Unit = {}) {
    disable()
    animHide(time) { block() }
}
fun Actor.animMoveTo(
    x: Float, y: Float,
    time: Float = 0f,
    interpolation: Interpolation = Interpolation.linear,
    block: () -> Unit = {}
) {
    addAction(
        Actions.sequence(
            Actions.moveTo(x, y, time, interpolation),
            Actions.run { block() }
        ))
}
fun Actor.animDelay(time: Float, block: () -> Unit = {}) {
    addAction(
        Actions.sequence(
        Actions.delay(time),
        Actions.run { block.invoke() }
    ))
}
fun Actor.animRotateTo(
    rotation: Float,
    time: Float = 0f,
    interpolation: Interpolation = Interpolation.linear,
    block: () -> Unit = {}) {
    addAction(Actions.sequence(
        Actions.rotateTo(rotation, time, interpolation),
        Actions.run(block)
    ))
}

/**
 * Переїзд актора в позицію актора-якоря з опційним масштабуванням.
 * Сам відвʼязується від констрейнтів — інакше лейаут повертав би його назад.
 */
fun Actor.animToTarget(
    target: Actor,
    scale: Float = 1f,
    time: Float = 0.85f,
    interpolation: Interpolation = Interpolation.exp10Out,
    blockEnd: Block = {},
) {
    val p = parent ?: return
    (p as? AConstraintLayout)?.detach(this)

    val pos = target.localToStageCoordinates(Vector2())
    p.stageToLocalCoordinates(pos)

    setOrigin(Align.center)   // масштабуємо навколо центру, а не кута
    clearActions()
    addAction(Actions.sequence(
        Actions.parallel(
            Actions.moveTo(pos.x, pos.y, time, interpolation),
            Actions.scaleTo(scale, scale, time, interpolation),
        ),
        Actions.run(blockEnd)
    ))
}
