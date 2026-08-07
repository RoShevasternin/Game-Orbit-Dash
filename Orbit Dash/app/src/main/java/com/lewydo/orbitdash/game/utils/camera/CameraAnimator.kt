package com.lewydo.orbitdash.game.utils.camera

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.lewydo.orbitdash.game.utils.camera.animation.*

class CameraAnimator(private val camera: OrthographicCamera, private val controller: CameraController) {

    private val animations = mutableListOf<CameraAnimation>()

    fun moveBy(x: Float, y: Float, time: Float, interpolation: Interpolation = Interpolation.smooth) {
        animations.add(MoveAnimation(Vector2(x, y), time, interpolation))
    }

    fun zoomTo(zoom: Float, time: Float, interpolation: Interpolation = Interpolation.smooth) {
        animations.add(ZoomAnimation(zoom, time, interpolation))
    }

    fun sequence(vararg animations: CameraAnimation) {
        this.animations.add(SequenceAnimation(*animations))
    }

    fun parallel(vararg animations: CameraAnimation) {
        this.animations.add(ParallelAnimation(*animations))
    }

    fun build(): CameraAnimation {
        return if (animations.size == 1) animations.first() else SequenceAnimation(*animations.toTypedArray())
    }

    fun start() {
        controller.animate(camera, build())
    }
}