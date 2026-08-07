package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.utils.Disposable

/**
 * Центральний координатор render-системи.
 * Тримає VfxPool який shared між усіма VfxGroup на екрані.
 *
 * Живе в AdvancedScreen — один екземпляр на екран.
 * Доступний через screen.renderPipeline.vfxPool з будь-якого VfxGroup.
 *
 * Dispose викликається в AdvancedScreen.dispose() — звільняє всі FBO пулу.
 */
class RenderPipeline : Disposable {

    /** Shared пул FrameBuffer для всіх VfxGroup на цьому екрані */
    val vfxPool = VfxPool()

    override fun dispose() {
        vfxPool.dispose()
    }
}