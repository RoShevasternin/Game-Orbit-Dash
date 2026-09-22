package com.lewydo.orbitdash.game.manager

import com.lewydo.orbitdash.game.content.Beep
import com.lewydo.orbitdash.game.content.Sfx
import com.lewydo.orbitdash.game.content.SfxCatalog
import com.lewydo.orbitdash.game.content.Wave
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Чиста частина синтезатора — без Gdx, на JVM. Стережемо, що рендер повторює
 * WebAudio-осцилятор прототипу: довжина, огинальна, слайд частоти, драбина
 * півтонів, формат wav, детермінізм.
 */
class SoundSynthTest {

    private fun zeroCrossings(pcm: FloatArray, from: Int, to: Int): Int {
        var n = 0
        for (i in (from + 1) until to) if ((pcm[i - 1] < 0f) != (pcm[i] < 0f)) n++
        return n
    }

    @Test
    fun `довжина = dur × SR`() {
        val pcm = SoundSynth.render(SfxCatalog.TAP_OUT)          // 0.06 с
        assertEquals(2646, pcm.size)
    }

    @Test
    fun `гучність спадає до −60 дБ і не клацає в кінці`() {
        val b   = SfxCatalog.DEATH.layers[0]                     // saw 0.5 с, vol 0.15
        val pcm = SoundSynth.render(SfxCatalog.DEATH)
        val n   = pcm.size
        // перші 5 мс — майже повна гучність
        assertTrue(SoundSynth.peak(pcm.copyOfRange(0, 220)) > b.vol * 0.8f)
        // за 5 мс до кінця — нижче 2 % від vol (WebAudio: 0.001 · vol ≈ −60 дБ)
        assertTrue(abs(pcm[n - 220]) < b.vol * 0.02f)
        // останній семпл — рівно нуль (хвіст 1 мс)
        assertEquals(0f, pcm[n - 1], 1e-6f)
    }

    @Test
    fun `частота без slide тримається`() {
        // UI_TICK: square 500 Гц, 50 мс, slide 0 → 2·500·0.05 = 50 переходів через нуль
        val pcm = SoundSynth.render(SfxCatalog.UI_TICK)
        val zc  = zeroCrossings(pcm, 0, pcm.size)
        assertTrue("zc=$zc", zc in 49..51)
    }

    @Test
    fun `slide піднімає частоту до кінця`() {
        // GEM(0): sine 700 → 950 Гц за 80 мс: перші 10 мс ≈ 14 переходів, останні ≈ 19
        val pcm  = SoundSynth.render(SfxCatalog.gem(0))
        val n    = pcm.size
        val head = zeroCrossings(pcm, 0, 441)
        val tail = zeroCrossings(pcm, n - 441, n)
        assertTrue("head=$head", head in 13..15)
        assertTrue("tail=$tail", tail in 18..20)
    }

    @Test
    fun `slide униз не нижче 40 Гц`() {
        val b   = Beep(60f, 0.1f, Wave.SINE, 0.1f, -100f)        // 60 − 100 < 0 → max(40, …) = 40
        val pcm = FloatArray(SoundSynth.samples(b)).also { SoundSynth.renderBeep(b, it) }
        val tail = zeroCrossings(pcm, pcm.size - 2205, pcm.size) // останні 50 мс на ~40 Гц → 4 переходи
        assertTrue("tail=$tail", tail in 3..5)
    }

    @Test
    fun `драбина — півтон за крок`() {
        assertEquals(700f,  SfxCatalog.gem(0).layers[0].f,  0.01f)
        assertEquals(1400f, SfxCatalog.gem(12).layers[0].f, 0.01f)                  // октава
        assertEquals(950f * 2f.pow(8f / 12f), SfxCatalog.combo(8).layers[0].f, 0.01f)
        assertTrue(SfxCatalog.gem(99) === SfxCatalog.gem(SfxCatalog.GEM_CHAIN_MAX))  // стеля, не виліт
    }

    @Test
    fun `два шари міксуються`() {
        val pcm = SoundSynth.render(SfxCatalog.ORBIT3)             // 0.4 + 0.5 с → довжина 0.5 с
        assertEquals(22050, pcm.size)
        val p = SoundSynth.peak(pcm)
        assertTrue("peak=$p", p > 0.12f && p <= 0.32f)             // більше за один шар, не більше за суму
    }

    @Test
    fun `wav — 16-bit mono 44100, нормалізований`() {
        val pcm = SoundSynth.render(SfxCatalog.TAP_IN)
        val wav = SoundSynth.toWav(pcm, 1f / SoundSynth.peak(pcm))
        assertEquals(44 + pcm.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(44100, le32(wav, 24))
        assertEquals(1,  le16(wav, 22))                             // mono
        assertEquals(16, le16(wav, 34))                             // біт на семпл
        var maxAbs = 0
        for (i in 44 until wav.size step 2) maxAbs = maxOf(maxAbs, abs(le16(wav, i).toShort().toInt()))
        assertTrue("max=$maxAbs", maxAbs in 32700..32767)
    }

    @Test
    fun `рендер детермінований, ключ кешу — від чисел`() {
        assertArrayEquals(SoundSynth.render(SfxCatalog.PULSE), SoundSynth.render(SfxCatalog.PULSE), 0f)
        val key = SoundSynth.cacheKey(SfxCatalog.all)
        val changed = SfxCatalog.all.map {
            if (it === SfxCatalog.TAP_OUT) Sfx("tap_out", Beep(301f, 0.06f, Wave.SQUARE, 0.07f, 200f)) else it
        }
        assertTrue(key != SoundSynth.cacheKey(changed))
        assertEquals(key, SoundSynth.cacheKey(SfxCatalog.all))
    }

    private fun le16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, at: Int) = le16(b, at) or (le16(b, at + 2) shl 16)
}
