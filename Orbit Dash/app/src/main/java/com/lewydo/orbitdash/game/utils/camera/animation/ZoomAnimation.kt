package com.lewydo.orbitdash.game.utils.camera.animation

import com.lewydo.orbitdash.game.utils.camera.CameraAnimation
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils

class ZoomAnimation(
    private val targetZoom: Float,
    duration: Float,
    interpolation: Interpolation = Interpolation.smooth
) : CameraAnimation(interpolation) {

    private var startZoom = 1f
    private var initialized = false
    private val durationFinal = duration

    override fun update(delta: Float, camera: OrthographicCamera): Boolean {
        if (!initialized) {
            startZoom = camera.zoom
            this.duration = durationFinal
            initialized = true
        }

        elapsed += delta
        val alpha = interpolation.apply((elapsed / duration).coerceIn(0f, 1f))
        camera.zoom = MathUtils.lerp(startZoom, targetZoom, alpha)
        return elapsed >= duration
    }
}
