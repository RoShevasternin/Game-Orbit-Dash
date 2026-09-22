package com.lewydo.orbitdash.game.manager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.content.Beep
import com.lewydo.orbitdash.game.content.Sfx
import com.lewydo.orbitdash.game.content.Wave
import com.lewydo.orbitdash.util.log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ----------------------------------------------------------------------------
//  СИНТЕЗАТОР — WebAudio-осцилятор прототипу, порахований наперед.
//
//  Той самий принцип, що VfxTexture: запекти раз, грати багато. Живий синтез
//  (AudioDevice) на Android — це AudioTrack із затримкою 50–150 мс і власним
//  мікшером, а SoundPool грає готовий семпл за кілька мс. Тому кожен Sfx
//  рендериться в PCM тією самою математикою, що beep() у JSX, пишеться як
//  wav у локальне сховище і далі живе як звичайний Sound.
//
//  Що збігається з WebAudio один в один:
//    частота  f(t) = f · (f₁/f)^(t/dur), f₁ = max(40, f + slide)  — exponentialRamp
//    гучність g(t) = vol · (0.001/vol)^(t/dur)                    — exponentialRamp
//    форми    sine / square / sawtooth; square і saw band-limited (polyBLEP),
//             як і в браузері — наївні сходинки дзвеніли б аліасингом
//  Що додано: 1 мс лінійного згасання в самому кінці. WebAudio просто
//  зупиняє осцилятор, і на −60 дБ це ледь чутний клац; нам він не потрібен.
//
//  КЕШ. Тека local/sfx/v<хеш>/ — хеш рахується з усіх рецептів + SR + VERSION.
//  Змінив число в каталозі — нова тека, стара стирається. Перший запуск
//  пише ~40 файлів (≈0.5 МБ, десятки мс), далі лише читає.
//
//  GL тут ні до чого: SoundPool переживає паузу. А от Activity Android може
//  знищити, лишивши процес, — тому це не object, а поле GDXGame: create()
//  зробить новий, dispose() відпустить семпли старого.
// ----------------------------------------------------------------------------
class SoundSynth : Disposable {

    companion object {
        const val SR      = 44100
        /** Підняти, якщо змінилась математика рендера при тих самих рецептах. */
        const val VERSION = 1

        private const val CACHE_ROOT = "sfx"
        /** Хвіст лінійного згасання, с — щоб не клацало на stop(). */
        private const val TAIL_FADE  = 0.001f
        /** WebAudio: gain падає до 0.001, нижче — уже тиша. */
        private const val GAIN_END   = 0.001f
        /** WebAudio: exponentialRamp частоти не нижче 40 Гц. */
        private const val F_MIN      = 40f

        // ── чиста частина: рецепт → PCM → wav. Без Gdx — тестується на JVM ──

        /** Сума шарів, без нормалізації: пік = «vol» у термінах WebAudio. */
        fun render(sfx: Sfx): FloatArray {
            val out = FloatArray(sfx.layers.maxOf { samples(it) })
            sfx.layers.forEach { renderBeep(it, out) }
            return out
        }

        fun samples(b: Beep): Int = (b.dur * SR).roundToInt()

        /** Один осцилятор, ДОДАЄТЬСЯ в out — так шари міксуються. */
        fun renderBeep(b: Beep, out: FloatArray) {
            val n     = samples(b)
            val f1    = max(F_MIN, b.f + b.slide)
            // Експоненційні рампи — множенням на крок, а не pow() на кожен семпл
            val fStep = (f1 / b.f).toDouble().pow(1.0 / n)
            val gStep = (GAIN_END / b.vol).toDouble().pow(1.0 / n)
            val fade  = (TAIL_FADE * SR).roundToInt()
            var f     = b.f.toDouble()
            var g     = b.vol.toDouble()
            var phase = 0.0                        // 0..1, один період
            for (i in 0 until n) {
                val dt = f / SR
                val y  = when (b.wave) {
                    Wave.SINE   -> sin(2.0 * PI * phase)
                    Wave.SQUARE -> (if (phase < 0.5) 1.0 else -1.0) + blep(phase, dt) - blep((phase + 0.5) % 1.0, dt)
                    Wave.SAW    -> 2.0 * phase - 1.0 - blep(phase, dt)
                }
                val tail = if (n - i <= fade) (n - i - 1).toDouble() / fade else 1.0
                out[i] += (g * y * tail).toFloat()
                phase += dt
                if (phase >= 1.0) phase -= 1.0
                f *= fStep
                g *= gStep
            }
        }

        /** polyBLEP: згладжує стрибок хвилі на ширину одного семпла. */
        private fun blep(t: Double, dt: Double): Double = when {
            t < dt       -> { val x = t / dt;         x + x - x * x - 1.0 }
            t > 1.0 - dt -> { val x = (t - 1.0) / dt; x * x + x + x + 1.0 }
            else         -> 0.0
        }

        fun peak(pcm: FloatArray): Float {
            var p = 0f
            for (v in pcm) p = max(p, abs(v))
            return p
        }

        /** 16-bit mono PCM wav; gain — множник перед квантуванням (1/peak → повна шкала). */
        fun toWav(pcm: FloatArray, gain: Float): ByteArray {
            val data = pcm.size * 2
            val b = ByteArray(44 + data)
            fun str(at: Int, s: String) { for (i in s.indices) b[at + i] = s[i].code.toByte() }
            fun i16(at: Int, v: Int) { b[at] = v.toByte(); b[at + 1] = (v shr 8).toByte() }
            fun i32(at: Int, v: Int) { i16(at, v); i16(at + 2, v shr 16) }
            str(0, "RIFF"); i32(4, 36 + data); str(8, "WAVE")
            str(12, "fmt "); i32(16, 16); i16(20, 1); i16(22, 1)
            i32(24, SR); i32(28, SR * 2); i16(32, 2); i16(34, 16)
            str(36, "data"); i32(40, data)
            var at = 44
            for (v in pcm) {
                i16(at, (v * gain * 32767f).roundToInt().coerceIn(-32768, 32767))
                at += 2
            }
            return b
        }

        /** Ключ кешу: усі числа всіх рецептів. */
        fun cacheKey(all: List<Sfx>): String {
            val s = all.joinToString("|") { sfx ->
                sfx.name + ":" + sfx.layers.joinToString(",") { "${it.f}/${it.dur}/${it.wave}/${it.vol}/${it.slide}" }
            } + "|$SR|$VERSION"
            return s.hashCode().toUInt().toString(16)
        }
    }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val sounds = HashMap<Sfx, Sound>()
    private val peaks  = HashMap<Sfx, Float>()

    /**
     * Пік найгучнішого рецепта каталогу — опора відносного міксу.
     *
     * Кожен wav нормалізовано до повної шкали (усі 16 біт у діло, без шуму
     * квантування на тихих звуках), тож гучність повертає coff = peak/loudest:
     * найгучніший звук іде на 1.0, решта — у пропорції прототипу.
     */
    var loudestPeak = 1f; private set

    /** Запекти все з каталогу. Кличеться з лоадера — SoundPool довантажує асинхронно. */
    fun bakeAll(all: List<Sfx>) {
        val root = Gdx.files.local(CACHE_ROOT)
        val dir  = root.child("v" + cacheKey(all))
        // Старі версії — геть: рецепти змінились, ті файли вже нікому не потрібні
        if (root.exists()) root.list().forEach { if (it.isDirectory && it.name() != dir.name()) it.deleteDirectory() }

        var written = 0
        for (sfx in all) {
            val pcm = render(sfx)
            val p   = peak(pcm)
            peaks[sfx] = p
            val file = dir.child(sfx.name + ".wav")
            if (!file.exists()) {
                file.writeBytes(toWav(pcm, if (p > 0f) 1f / p else 1f), false)
                written++
            }
            sounds[sfx] = Gdx.audio.newSound(file)
        }
        loudestPeak = peaks.values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        log("SoundSynth: ${all.size} sfx, written $written, loudest $loudestPeak → ${dir.path()}")
    }

    fun sound(sfx: Sfx): Sound = sounds[sfx] ?: error("Sfx '${sfx.name}' не запечено — bakeAll() до першого play")

    /** Пікова гучність рецепта = vol прототипу; сам wav нормалізовано до повної шкали. */
    fun peak(sfx: Sfx): Float = peaks[sfx] ?: 1f

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        sounds.clear()
        peaks.clear()
    }
}
