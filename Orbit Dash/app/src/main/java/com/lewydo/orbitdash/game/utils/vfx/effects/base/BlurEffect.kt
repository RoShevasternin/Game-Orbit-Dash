package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache
import kotlin.math.ceil
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// BlurEffect — Layer Blur як у Figma
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gaussian blur з одним параметром — `blur`, **те саме число, що Layer Blur у
 * Figma**, у world-юнітах. Усе інше рахується тут.
 *
 *   VfxTexture(100f, 100f, msdf.circle, msdf.effect, listOf(BlurEffect(blur = 68f)))
 *   — коло 100×100 з Layer Blur 68; outerWidth = 236, як фрейм «hug contents».
 *
 * ─── Що всередині ───────────────────────────────────────────────────────────
 * Layer Blur B у Figma — звичайний гаус із σ = 0.426·B юніта. Виміряно по
 * експорту (коло 100 / blur 68): RMS 0.006 проти чистого гауса.
 *
 * Малюємо парою проходів H+V (це точний 2D-гаус: G(x,y) = G(x)·G(y)),
 * повтореною passes разів. Діагональних проходів НЕ додавати: два напрямки в
 * одному квадранті складають коваріації, і пляма стає еліпсом.
 *
 * Шейдер — 9 семплів з кроком step текселів, σ одного проходу = 1.689·step.
 * step понад 2 текселі розсуває семпли далі за деталі джерела — смуги.
 * Тому step ≤ 2, а велика σ добирається двома способами:
 *   • passes — згортка N гаусів дає σ·√N; заразом хвіст перестає обриватись
 *     (одне 9-tap ядро закінчується на 2.37σ — це кільце на темному тлі);
 *   • піраміда — якщо σ у текселях цього буфера все одно завелика (буфер
 *     повної роздільності: VfxGroup, ABlurBack), буфер ділиться на 2, поки σ
 *     не впаде до ~SIGMA_TEXELS, блюриться там і повертається одним upsample.
 *     Так робить Skia під Figma.
 *
 * ─── Роздільність ──────────────────────────────────────────────────────────
 * Розмитій текстурі не потрібна щільність фігури. preferredDensity() каже
 * VfxTexture (без явної density), скільки текселів на юніт досить:
 * σ = SIGMA_TEXELS текселів. Нижче — після апскейлу видно злами нахилу між
 * текселями (перевірено: σ ≈ 6 текселів — видно, ≈ 12 — ні).
 *
 * blur = 0 → pass-through (жодного Blit, жодного swap).
 */
class BlurEffect(var blur: Float = 0f) : VfxEffect() {

    companion object {
        /** σ гауса на одиницю Layer Blur, юнітів. Фіт по експорту з Figma (коло 100, blur 68). */
        const val SIGMA_PER_BLUR = 0.426f
        /** σ одного 9-tap проходу в кроках: √2.854 — дисперсія ваг ядра в шейдері. */
        const val SIGMA_PER_STEP = 1.689f
        /** Крок семплів, вище якого між ними дірки → смуги. */
        const val STEP_MAX = 2f
        /** Робоча σ в текселях: менше — злами при апскейлі, більше — зайві паси. */
        const val SIGMA_TEXELS = 12f
    }

    override val fragmentShader = "shader/base/blur/gaussianBlurFS.glsl"

    /** σ гауса в юнітах — Figma Layer Blur × 0.426. */
    val sigma: Float get() = SIGMA_PER_BLUR * blur

    override val isEnabled: Boolean get() = blur > 0f

    override fun stateKey(): Long = blur.toRawBits().toLong()

    /**
     * Скільки ефект виносить назовні, у юнітах. Правило Figma: bounds шару =
     * фігура + blur з кожного боку; гаус на цій межі (2.35σ від краю фігури)
     * уже ≈ 0.01 — той самий обріз, що й у експорті.
     */
    override fun reachUnits(): Float = blur

    /** Текселів на юніт, яких блюру досить: σ = SIGMA_TEXELS текселів. */
    override fun preferredDensity(): Float? = if (blur > 0f) SIGMA_TEXELS / sigma else null

    private val copyShader get() = VfxShaderCache.get("shader/base/copy/copyFS.glsl", Blit.VERT)

    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (blur <= 0f) return

        // σ у текселях ЦЬОГО буфера. Для VfxTexture з авто-density це ≈ SIGMA_TEXELS;
        // для буфера повної роздільності (VfxGroup) — у рази більше → піраміда.
        var s      = sigma * ctx.density
        var levels = 0
        var w      = ctx.bufferW
        var h      = ctx.bufferH
        if (ctx.pool != null) {
            while (s > SIGMA_TEXELS * 1.5f && w >= 16 && h >= 16) {
                s /= 2f; w /= 2; h /= 2; levels++
            }
        }

        // Скільки пар H+V і який крок, щоб σ вийшла рівно s при step ≤ STEP_MAX.
        val passes = ceil((s / (SIGMA_PER_STEP * STEP_MAX)).let { it * it }).toInt().coerceAtLeast(1)
        val step   = s / (SIGMA_PER_STEP * sqrt(passes.toFloat()))

        if (levels == 0) {
            blurHV(pingPong.src, pingPong.dst, ctx.bufferW, ctx.bufferH, step, passes, pingPong)
            return
        }

        // ── Піраміда ────────────────────────────────────────────────────────
        // Униз: кожен крок ×½ — fullscreen-квад із лінійним фільтром на буфер
        // удвічі менший семплить рівно між чотирма текселями = box 2×2.
        val pool  = ctx.pool!!
        val tmp   = ArrayList<FrameBuffer>(levels + 1)
        var cur   = pingPong.src
        var cw    = ctx.bufferW
        var ch    = ctx.bufferH
        repeat(levels) {
            cw /= 2; ch /= 2
            val next = pool.obtain(cw, ch)
            Blit.blit(cur, next, copyShader)
            tmp += next; cur = next
        }
        // На дні — свій ping-pong тієї самої роздільності.
        val other = pool.obtain(cw, ch)
        tmp += other
        val bottom = PingPong.of(cur, other)
        blurHV(bottom.src, bottom.dst, cw, ch, step, passes, bottom)
        // Угору одним стрибком: після σ ≈ 12 текселів деталей, які втратив би
        // білінійний апскейл, у картинці немає.
        Blit.blit(bottom.src, pingPong.dst, copyShader)
        pingPong.swap()
        for (fb in tmp) pool.free(fb)
    }

    /** passes пар H+V на ping-pong; результат лишається в pp.src. */
    private fun blurHV(src: FrameBuffer, dst: FrameBuffer, w: Int, h: Int, step: Float, passes: Int, pp: PingPong) {
        val fw = w.toFloat(); val fh = h.toFloat()
        repeat(passes) {
            Blit.blit(pp.src, pp.dst, shader) { sp ->
                sp.setUniformf("u_direction",  1f, 0f)
                sp.setUniformf("u_groupSize",  fw, fh)
                sp.setUniformf("u_blurAmount", step)
            }
            pp.swap()
            Blit.blit(pp.src, pp.dst, shader) { sp ->
                sp.setUniformf("u_direction",  0f, 1f)
                sp.setUniformf("u_groupSize",  fw, fh)
                sp.setUniformf("u_blurAmount", step)
            }
            pp.swap()
        }
    }

}