package com.lewydo.orbitdash.game.utils.camera

import com.badlogic.gdx.graphics.OrthographicCamera

val cameraController = CameraController()

fun OrthographicCamera.anim(block: CameraAnimator.() -> Unit) {
    val animator = CameraAnimator(this, cameraController)
    animator.block()
    animator.start()
}