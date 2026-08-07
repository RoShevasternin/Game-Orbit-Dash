package com.lewydo.orbitdash.game.utils.camera

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Interpolation

abstract class CameraAnimation(
    var interpolation: Interpolation = Interpolation.linear
) {
    protected var elapsed = 0f
    protected var duration = 1f

    /** Повертає true, якщо анімація завершена */
    abstract fun update(delta: Float, camera: OrthographicCamera): Boolean
}
