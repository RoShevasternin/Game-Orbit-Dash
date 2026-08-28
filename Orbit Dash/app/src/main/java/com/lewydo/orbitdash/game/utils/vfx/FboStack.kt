package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.FrameBuffer

/**
 * Nesting-safe стек прив'язки FBO + scissor-safe.
 *
 * ─── Навіщо стек ────────────────────────────────────────────────────────────
 * LibGDX FrameBuffer.end() ЗАВЖДИ повертає на екран, а не на попередній FBO —
 * вкладені VfxGroup ламались. pop() тут повертає на попередній FBO у стеку.
 *
 * ─── Навіщо scissor-handling (НОВЕ) ────────────────────────────────────────
 * ScrollPane (та будь-який clipBegin) вмикає glScissor у WINDOW-координатах.
 * Якщо VfxGroup малює у FBO поки scissor активний — clear і draw обрізаються
 * прямокутником з ЕКРАННОГО простору, який для FBO безглуздий →
 * FBO лишається порожнім → "діти маски не рисуються у ScrollPane".
 *
 * Фікс: при вході у FBO-режим (стек 0→1) вимикаємо GL_SCISSOR_TEST,
 * при виході на екран (стек →0) відновлюємо якщо був увімкнений.
 * Scissor-ПРЯМОКУТНИК GL зберігає сам — ми лише перемикаємо enable.
 * Результат маски на екрані далі коректно кліпиться скролом (scissor
 * відновлюється ДО малювання результату... див. примітку в VfxGroup).
 */
object FboStack {

    private val stack = ArrayDeque<FrameBuffer>()

    private var screenX = 0
    private var screenY = 0
    private var screenW = 0
    private var screenH = 0

    // Чи був scissor увімкнений на момент входу в FBO-режим
    private var scissorWasEnabled = false

    /** Викликати перед draw() сцени — задає екранний viewport */
    fun setScreen(x: Int, y: Int, w: Int, h: Int) {
        screenX = x; screenY = y; screenW = w; screenH = h
    }

    /** Прив'язати FBO і покласти в стек */
    fun push(fbo: FrameBuffer) {
        if (stack.isEmpty()) {
            // Вхід у FBO-режим: scissor (window coords) не має сенсу всередині FBO
            scissorWasEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)
            if (scissorWasEnabled) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        }

        stack.addLast(fbo)
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, fbo.framebufferHandle)
        Gdx.gl.glViewport(0, 0, fbo.width, fbo.height)
    }

    /** Зняти верхній FBO зі стеку, повернутись на попередній (або екран) */
    fun pop() {
        if (stack.isEmpty()) { bindScreen(); return }
        stack.removeLast()

        val top = stack.lastOrNull()
        if (top != null) {
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, top.framebufferHandle)
            Gdx.gl.glViewport(0, 0, top.width, top.height)
        } else {
            bindScreen()
            // Вихід на екран: повертаємо scissor якщо він був (ScrollPane кліпінг
            // продовжує діяти на все що малюється далі, включно з результатом маски)
            if (scissorWasEnabled) {
                Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST)
                scissorWasEnabled = false
            }
        }
    }

    private fun bindScreen() {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
        Gdx.gl.glViewport(screenX, screenY, screenW, screenH)
    }

    /** Аварійне очищення (зміна екрану тощо) */
    fun reset() {
        stack.clear()
        scissorWasEnabled = false
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
    }

    val depth: Int get() = stack.size
}