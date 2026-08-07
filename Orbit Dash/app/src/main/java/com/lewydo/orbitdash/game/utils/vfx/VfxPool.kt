package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.Disposable

/**
 * Shared пул FrameBuffer об'єктів.
 *
 * Проблема без пулу: кожен актор з ефектом виділяє власні FBO при ініціалізації
 * і тримає їх у GPU пам'яті до кінця життя. 16 акторів × 4 FBO = 64 текстури
 * в GPU пам'яті одночасно, навіть якщо більшість не рендерується в цьому кадрі.
 *
 * Рішення: актори беруть FBO "в оренду" тільки під час preRender() і одразу
 * повертають після. Максимальна кількість одночасно активних FBO = глибина
 * дерева ефектів (зазвичай 2-4), а не кількість акторів.
 *
 * Пул групує буфери за розміром. FBO 200×300 і FBO 200×300 — один bucket.
 * FBO 200×300 і 400×600 — різні buckets. При поверненні FBO кладеться
 * назад у свій bucket і готовий до reuse в тому ж кадрі.
 */
class VfxPool(
    private val format: Pixmap.Format = Pixmap.Format.RGBA8888
) : Disposable {

    // Ключ = (width << 32) | height — унікальний для кожного розміру
    private val available  = HashMap<Long, ArrayDeque<FrameBuffer>>()
    private val allCreated = mutableListOf<FrameBuffer>()

    private fun key(w: Int, h: Int): Long = w.toLong().shl(32).or(h.toLong())

    /** -------------------------------------------------------------------------
    // Отримати FBO з пулу або створити новий якщо немає вільного.
    //
    // Повернений FBO має Linear texture filter — щоб blur і scaling
    // виглядали плавно без пікселізації.
    //
    // КОНТРАКТ: після використання ОБОВ'ЯЗКОВО викликати free().
    // ------------------------------------------------------------------------- */
    fun obtain(width: Int, height: Int): FrameBuffer {
        val queue = available[key(width, height)]
        if (!queue.isNullOrEmpty()) return queue.removeFirst()

        return FrameBuffer(format, width, height, false).also { fbo ->
            fbo.colorBufferTexture.setFilter(
                Texture.TextureFilter.Linear,
                Texture.TextureFilter.Linear
            )
            allCreated.add(fbo)
        }
    }

    /** Повернути FBO в пул для повторного використання */
    fun free(fbo: FrameBuffer) {
        available.getOrPut(key(fbo.width, fbo.height)) { ArrayDeque() }.addLast(fbo)
    }

    /** Dispose всіх FBO — викликати тільки при виході з гри */
    override fun dispose() {
        allCreated.forEach { runCatching { it.dispose() } }
        allCreated.clear()
        available.clear()
    }
}