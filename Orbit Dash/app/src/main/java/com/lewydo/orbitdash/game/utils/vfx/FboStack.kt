package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.FrameBuffer

/**
 * Nesting-safe стек прив'язки FBO.
 *
 * ─── НАВІЩО ────────────────────────────────────────────────────────────────
 *
 * Проблема LibGDX: FrameBuffer.end() ЗАВЖДИ повертає на ЕКРАН (default
 * framebuffer 0), а не на батьківський FBO. Тому при вкладеності:
 *
 *   A.begin()              ← малюємо в A
 *     B.begin()            ← малюємо в B
 *     B.end()              ← повертає на ЕКРАН, не на A!  ✗
 *   ...решта A малюється на екран замість A — зламано
 *
 * FboStack виправляє: pop() повертає на ПОПЕРЕДНІЙ FBO у стеку
 * (або на екран якщо стек порожній):
 *
 *   push(A)                ← стек: [A], bind A
 *     push(B)              ← стек: [A,B], bind B
 *     pop()                ← стек: [A], bind A знову  ✓
 *   pop()                  ← стек: [], bind екран  ✓
 *
 * ─── ВИКОРИСТАННЯ ──────────────────────────────────────────────────────────
 *
 * Перед draw() сцени:
 *   FboStack.setScreen(viewport.screenX, screenY, screenWidth, screenHeight)
 *
 * Замість fbo.begin()/end():
 *   batch.flush()          ← злити пендінг спрайти на ПОТОЧНИЙ FBO
 *   FboStack.push(fbo)
 *   ...рендер...
 *   batch.flush()          ← злити в fbo
 *   FboStack.pop()
 *
 * ВАЖЛИВО: caller робить batch.flush() ПЕРЕД push і ПЕРЕД pop —
 * щоб спрайти потрапили в правильний FBO до зміни прив'язки.
 */
object FboStack {

    private val stack = ArrayDeque<FrameBuffer>()

    // Куди повертатись коли стек порожній (екранний viewport).
    private var screenX = 0
    private var screenY = 0
    private var screenW = 0
    private var screenH = 0

    /** Викликати один раз перед draw() сцени — задає екранний viewport */
    fun setScreen(x: Int, y: Int, w: Int, h: Int) {
        screenX = x; screenY = y; screenW = w; screenH = h
    }

    /** Прив'язати FBO і покласти в стек */
    fun push(fbo: FrameBuffer) {
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
        }
    }

    private fun bindScreen() {
        // Default framebuffer на Android = 0 (екран)
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0)
        Gdx.gl.glViewport(screenX, screenY, screenW, screenH)
    }

    /** Аварійне очищення — на випадок виключення в середині рендеру */
    fun reset() {
        stack.clear()
        bindScreen()
    }

    val depth: Int get() = stack.size
}