package com.lewydo.orbitdash.game.utils.camera

import com.badlogic.gdx.graphics.OrthographicCamera

class CameraController {

    private val slots = mutableMapOf<OrthographicCamera, CameraSlot>()

    /** Додає камеру під контроль */
    fun add(camera: OrthographicCamera) {
        slots.getOrPut(camera) { CameraSlot(camera) }
    }

    /** Прибирає камеру з контролю */
    fun remove(camera: OrthographicCamera) {
        slots.remove(camera)
    }

    /** Викликаємо в render(delta) */
    fun update(delta: Float) {
        slots.values.forEach { slot ->
            slot.animation?.let {
                if (it.update(delta, slot.camera)) {
                    slot.animation = null
                }
            }
            slot.camera.update()
        }
    }

    /** Внутрішній метод для старту анімації камери */
    internal fun animate(camera: OrthographicCamera, animation: CameraAnimation) {
        val slot = slots[camera] ?: error("Camera is not registered in CameraController")
        slot.animation = animation
    }
}
