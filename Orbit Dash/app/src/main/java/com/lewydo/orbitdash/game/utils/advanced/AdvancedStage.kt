package com.lewydo.orbitdash.game.utils.advanced

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.Viewport
import com.lewydo.orbitdash.game.utils.vfx.FboStack

/**
 * Stage без pre-pass.
 *
 * ─── ЩО ЗМІНИЛОСЬ ──────────────────────────────────────────────────────────
 *
 * Видалено реєстр PreRenderable і весь pre-pass блок. Раніше:
 *
 *   render() {
 *     act()
 *     // PRE-PASS: batch.begin() → preRender ВСІХ VfxGroup → batch.end()
 *     //          купа begin/end циклів + FBO bind/unbind на початку кадру
 *     draw()  // обхід дерева ВДРУГЕ
 *   }
 *
 * Тепер VfxGroup робить FBO-роботу inline у власному draw() в природному
 * z-порядку. Один обхід дерева. Статичні ефекти кешуються.
 *
 * setScreen() задає FboStack екранний viewport — щоб FboStack.pop() при
 * порожньому стеку коректно повертався на екран (а не на весь backbuffer).
 */
open class AdvancedStage(viewport: Viewport) : Stage(viewport) {

    fun update(screenWidth: Int, screenHeight: Int, centerCamera: Boolean) {
        viewport.update(screenWidth, screenHeight, centerCamera)
        root.setSize(viewport.worldWidth, viewport.worldHeight)
    }

    fun render() {
        viewport.apply()
        act()

        // Задаємо екранний viewport для FboStack (куди повертатись після FBO)
        FboStack.setScreen(
            viewport.screenX,
            viewport.screenY,
            viewport.screenWidth,
            viewport.screenHeight
        )

        // Один обхід дерева. VfxGroup роблять FBO-роботу inline у своєму draw().
        draw()
    }

    override fun dispose() {
        actors.onEach { actor -> if (actor is Disposable) actor.dispose() }
        super.dispose()
    }
}