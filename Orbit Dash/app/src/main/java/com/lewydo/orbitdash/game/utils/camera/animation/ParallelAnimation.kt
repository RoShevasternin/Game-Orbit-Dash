package com.lewydo.orbitdash.game.utils.camera.animation

import com.badlogic.gdx.graphics.OrthographicCamera
import com.lewydo.orbitdash.game.utils.camera.CameraAnimation

class ParallelAnimation(vararg val animations: CameraAnimation) : CameraAnimation() {

    override fun update(delta: Float, camera: OrthographicCamera): Boolean {
        var allDone = true
        animations.forEach { anim ->
            if (!anim.update(delta, camera)) allDone = false
        }
        return allDone
    }
}
