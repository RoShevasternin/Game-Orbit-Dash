package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.graphics.glutils.FrameBuffer

/**
 * Ping-pong буфер — два FBO що міняються місцями між ефектами.
 *
 * Уявляй це як дві тарілки на кухні. Кухар (ефект) бере їжу з першої тарілки,
 * обробляє її і кладе результат на другу. Потім тарілки міняються місцями —
 * тепер оброблена їжа на "першій" тарілці, і наступний кухар робить теж саме.
 *
 * Скільки б кухарів (ефектів) не було — завжди рівно дві тарілки (FBO).
 *
 *   [src=A] → Effect1 → [dst=B]   swap → [src=B, dst=A]
 *   [src=B] → Effect2 → [dst=A]   swap → [src=A, dst=B]
 *   [src=A] → Effect3 → [dst=B]   swap → [src=B, dst=A]
 *   Результат завжди в src після pipeline.
 *
 * Обидва FBO беруться з VfxPool при створенні і повертаються туди через free().
 */
class PingPong private constructor(
    val width  : Int,
    val height : Int,
    src        : FrameBuffer,
    dst        : FrameBuffer,
    private val pool: VfxPool?,
) {
    /** Обидва буфери з пулу; free() поверне їх туди. */
    constructor(pool: VfxPool, width: Int, height: Int) :
            this(width, height, pool.obtain(width, height), pool.obtain(width, height), pool)

    var src: FrameBuffer = src ; private set
    var dst: FrameBuffer = dst ; private set

    /**
     * Міняє src і dst місцями.
     * Після кожного Blit.blit(src, dst, ...) обов'язково викликати swap() —
     * тоді результат стає src для наступного ефекту.
     */
    fun swap() { val t = src; src = dst; dst = t }

    /** Повертає обидва FBO в пул. Викликати після draw(). Не для of(): там буфери чужі. */
    fun free() {
        pool?.free(src)
        pool?.free(dst)
    }

    companion object {
        /**
         * Ping-pong над ЧУЖИМИ буферами однакового розміру — дно піраміди блюру,
         * де буфери вже орендовані й повертаються в пул тим, хто їх брав.
         */
        fun of(src: FrameBuffer, dst: FrameBuffer) = PingPong(src.width, src.height, src, dst, null)
    }
}