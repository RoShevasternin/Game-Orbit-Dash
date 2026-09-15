package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache
import kotlin.math.ceil
import kotlin.math.exp
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
 * Малюємо ОДНОЮ парою проходів H+V (це точний 2D-гаус: G(x,y) = G(x)·G(y)).
 * Ядро під точну σ рахується тут, на CPU, і йде в шейдер масивами: пари
 * сусідніх текселів беруться одним білінійним семплом (linear sampling), тому
 * 37 семплів покривають 3σ при σ = 12 текселів. Діагональних проходів НЕ
 * додавати: два напрямки в одному квадранті складають коваріації — еліпс.
 *
 * Чому одна пара, а не повтори 9-tap ядра, як було: заміряно 13.09.2026 —
 * ціна живого блюру на тайловому GPU це кількість ПРОХОДІВ (перемикань render
 * target), а не пікселів: 8 груп по 18 блітів = 20 FPS на будь-якому розмірі
 * буфера. Тепер на групу 2 бліти блюру + до 2 на піраміду.
 *
 * ─── Піраміда ──────────────────────────────────────────────────────────────
 * Якщо σ у текселях буфера більша за SIGMA_WORK_MAX (буфер повної роздільності:
 * ABlurBack, явна density, або VfxGroup із квантованою density), буфер ділиться
 * на 2 (box 2×2 через лінійний фільтр), поки σ не впаде в (6, 12], блюр іде там —
 * у 4–16 разів менше пікселів на семпл — і повертається одним кубічним B-сплайном
 * (upsampleCubicFS): C¹-гладко, без зламів нахилу, які давав білінійний апскейл
 * при σ < 12. Так робить Skia під Figma.
 *
 * ─── Роздільність ──────────────────────────────────────────────────────────
 * Розмитій текстурі не потрібна щільність фігури. preferredDensity() каже
 * VfxTexture / VfxGroup, скільки текселів на юніт досить для буфера, де стоїть
 * ПІДСУМОК: σ = SIGMA_TEXELS текселів (менше — екран білінійно семплить злами).
 *
 * blur = 0 → pass-through (жодного Blit, жодного swap).
 */
class BlurEffect(var blur: Float = 0f) : VfxEffect() {

    companion object {
        /** σ гауса на одиницю Layer Blur, юнітів. Фіт по експорту з Figma (коло 100, blur 68). */
        const val SIGMA_PER_BLUR = 0.426f
        /** σ у текселях буфера з ПІДСУМКОМ: менше — злами при білінійному семплінгу екраном. */
        const val SIGMA_TEXELS = 12f
        /** Стеля σ на рівні, де рахується ядро: вище — піраміда ділить буфер ½. = MAX_PAIRS·2/3. */
        const val SIGMA_WORK_MAX = 12f
        /** Радіус ядра в σ: за 3σ лишається 0.27 % маси — обрізу не видно. */
        const val KERNEL_SIGMAS = 3f
        /** Пар семплів на бік у шейдері (#define MAX_PAIRS) = ceil(3·12 / 2). */
        const val MAX_PAIRS = 18
    }

    override val fragmentShader = "shader/base/blur/gaussianBlurFS.glsl"

    private val copyShader     get() = VfxShaderCache.get("shader/base/copy/copyFS.glsl",          Blit.VERT)
    private val upsampleShader get() = VfxShaderCache.get("shader/base/blur/upsampleCubicFS.glsl", Blit.VERT)

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

    // ─── Ядро ────────────────────────────────────────────────────────────────
    // Рахується лише коли σ змінилась; у кадрі — нуль алокацій.

    private val kOffset = FloatArray(MAX_PAIRS)
    private val kWeight = FloatArray(MAX_PAIRS)
    private val kRaw    = FloatArray(2 * MAX_PAIRS + 1)
    private var kCenter = 1f
    private var kPairs  = 0
    private var kSigma  = -1f

    /** Дискретний гаус на цілих зсувах 0..3σ, згорнутий у пари під linear sampling. */
    private fun buildKernel(s: Float) {
        if (s == kSigma) return
        kSigma = s
        val r = ceil(KERNEL_SIGMAS * s).toInt().coerceIn(1, 2 * MAX_PAIRS)
        var sum = 0f
        for (i in 0..r) { kRaw[i] = exp(-(i * i) / (2f * s * s)); sum += if (i == 0) kRaw[i] else 2f * kRaw[i] }
        kCenter = kRaw[0] / sum
        var n = 0
        var i = 1
        while (i <= r) {
            val w1 = kRaw[i] / sum
            val w2 = if (i + 1 <= r) kRaw[i + 1] / sum else 0f
            val ww = w1 + w2
            kWeight[n] = ww
            kOffset[n] = if (ww > 0f) (i * w1 + (i + 1) * w2) / ww else i.toFloat()
            n++; i += 2
        }
        kPairs = n
    }

    private fun setKernel(sp: ShaderProgram, stepX: Float, stepY: Float) {
        sp.setUniformf("u_texelStep", stepX, stepY)
        sp.setUniformi("u_pairs", kPairs)
        sp.setUniformf("u_center", kCenter)
        sp.setUniform1fv("u_offset[0]", kOffset, 0, MAX_PAIRS)   // «[0]» — так масив звітує glGetActiveUniform
        sp.setUniform1fv("u_weight[0]", kWeight, 0, MAX_PAIRS)
    }

    /** Одна пара H+V на ping-pong; результат лишається в pp.src. */
    private fun blurHV(w: Int, h: Int, pp: PingPong) {
        Blit.blit(pp.src, pp.dst, shader) { sp -> setKernel(sp, 1f / w, 0f) }
        pp.swap()
        Blit.blit(pp.src, pp.dst, shader) { sp -> setKernel(sp, 0f, 1f / h) }
        pp.swap()
    }

    // ─── Render ──────────────────────────────────────────────────────────────

    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (blur <= 0f) return

        // σ у текселях ЦЬОГО буфера. VfxTexture з авто-density → рівно SIGMA_TEXELS;
        // VfxGroup із квантованою density → [12, 24); повна роздільність → десятки.
        var s      = sigma * ctx.density
        var levels = 0
        var w      = ctx.bufferW
        var h      = ctx.bufferH
        if (ctx.pool != null) {
            while (s > SIGMA_WORK_MAX * 1.001f && w >= 16 && h >= 16) {
                s /= 2f; w /= 2; h /= 2; levels++
            }
        }

        if (levels == 0) {
            // Без піраміди (нема пулу або σ й так мала). Якщо σ усе ж більша за ядро —
            // добираємо повторами: згортка N гаусів дає σ·√N. Нормально сюди не заходить.
            val passes = ceil((s / SIGMA_WORK_MAX).let { it * it }).toInt().coerceAtLeast(1)
            buildKernel(s / sqrt(passes.toFloat()))
            repeat(passes) { blurHV(ctx.bufferW, ctx.bufferH, pingPong) }
            return
        }

        // ── Піраміда ────────────────────────────────────────────────────────
        // Униз: кожен крок ×½ — fullscreen-квад із лінійним фільтром на буфер
        // удвічі менший семплить рівно між чотирма текселями = box 2×2.
        val pool = ctx.pool!!
        val tmp  = ArrayList<FrameBuffer>(levels + 1)
        var cur  = pingPong.src
        var cw   = ctx.bufferW
        var ch   = ctx.bufferH
        repeat(levels) {
            cw /= 2; ch /= 2
            val next = pool.obtain(cw, ch)
            Blit.blit(cur, next, copyShader)
            tmp += next; cur = next
        }
        // На дні — свій ping-pong тієї самої роздільності, одна пара H+V.
        val other  = pool.obtain(cw, ch)
        tmp += other
        val bottom = PingPong.of(cur, other)
        buildKernel(s)
        blurHV(cw, ch, bottom)
        // Угору одним стрибком кубічним B-сплайном — гладко при будь-якому кратному.
        Blit.blit(bottom.src, pingPong.dst, upsampleShader) { sp ->
            sp.setUniformf("u_srcSize", cw.toFloat(), ch.toFloat())
        }
        pingPong.swap()
        for (fb in tmp) pool.free(fb)
    }

}