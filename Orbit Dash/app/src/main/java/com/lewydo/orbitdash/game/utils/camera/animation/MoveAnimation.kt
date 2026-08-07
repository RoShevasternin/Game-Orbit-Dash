package com.lewydo.orbitdash.game.utils.camera.animation

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.lewydo.orbitdash.game.utils.camera.CameraAnimation

class MoveAnimation(
    private val offset: Vector2,
    duration: Float,
    interpolation: Interpolation = Interpolation.smooth
) : CameraAnimation(interpolation) {

    private lateinit var start: Vector3
    private var initialized = false

    private val durationFinal = duration

    override fun update(delta: Float, camera: OrthographicCamera): Boolean {
        if (!initialized) {
            start = camera.position.cpy()
            this.duration = durationFinal
            initialized = true
        }

        elapsed += delta
        val alpha = interpolation.apply((elapsed / duration).coerceIn(0f, 1f))
        camera.position.set(
            MathUtils.lerp(start.x, start.x + offset.x, alpha),
            MathUtils.lerp(start.y, start.y + offset.y, alpha),
            camera.position.z
        )
        return elapsed >= duration
    }
}
