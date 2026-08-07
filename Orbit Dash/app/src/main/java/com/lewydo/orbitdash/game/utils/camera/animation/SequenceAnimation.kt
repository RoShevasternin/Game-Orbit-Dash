package com.lewydo.orbitdash.game.utils.camera.animation

import com.badlogic.gdx.graphics.OrthographicCamera
import com.lewydo.orbitdash.game.utils.camera.CameraAnimation

class SequenceAnimation(vararg val animations: CameraAnimation) : CameraAnimation() {

    private var currentIndex = 0

    override fun update(delta: Float, camera: OrthographicCamera): Boolean {
        if (currentIndex >= animations.size) return true

        val done = animations[currentIndex].update(delta, camera)
        if (done) currentIndex++
        return currentIndex >= animations.size
    }
}
