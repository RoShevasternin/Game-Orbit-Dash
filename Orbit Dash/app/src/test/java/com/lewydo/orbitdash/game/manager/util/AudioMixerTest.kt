package com.lewydo.orbitdash.game.manager.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/**
 * Чиста математика пульта — без Gdx і без сейву. Стережемо рівно те, через що
 * звук уже одного разу зник: щоб системна гучність не прикладалась двічі,
 * щоб повзунок ішов рівно на слух і щоб нічого не вилітало за 0..1.
 */
class AudioMixerTest {

    private fun db(gain: Float) = 20.0 * log10(gain.toDouble())

    @Test
    fun `усе на максимумі — це запас, а не одиниця`() {
        // Найгучніший звук каталогу (coff 1) при всіх повзунках на максимум
        assertEquals(AudioMixer.SFX_HEADROOM, AudioMixer.sfxAt(1f, 1f, 1f), 1e-6f)
        // а музика запасу не потребує — доріжка одна
        assertEquals(1f, AudioMixer.gain(1f, 1f, 1f), 1e-6f)
    }

    @Test
    fun `повзунок на середині — мінус 12 дБ`() {
        val g = AudioMixer.gain(1f, 0.5f, 1f)
        assertEquals(0.25f, g, 1e-6f)
        assertTrue("дБ=${db(g)}", abs(db(g) + 12.0) < 0.1)
    }

    @Test
    fun `два повзунки складаються в дБ, як у пульті`() {
        // −12 дБ (шина) + −12 дБ (майстер) = −24 дБ
        val g = AudioMixer.gain(1f, 0.5f, 0.5f)
        assertEquals(0.0625f, g, 1e-6f)
        assertTrue("дБ=${db(g)}", abs(db(g) + 24.0) < 0.1)
    }

    @Test
    fun `нуль на будь-якому повзунку — тиша`() {
        assertEquals(0f, AudioMixer.gain(1f, 0f, 1f), 0f)
        assertEquals(0f, AudioMixer.gain(1f, 1f, 0f), 0f)
        assertEquals(0f, AudioMixer.gain(0f, 1f, 1f), 0f)
    }

    @Test
    fun `вихід завжди в межах 0 до 1`() {
        // Сміття на вході не має вилетіти за шкалу Sound_play()
        assertEquals(1f, AudioMixer.gain(5f, 9f, 9f), 1e-6f)
        assertEquals(0f, AudioMixer.gain(-2f, 1f, 1f), 0f)
        assertEquals(0f, AudioMixer.gain(1f, -1f, 1f), 0f)
        assertEquals(1f, AudioMixer.curve(2f), 0f)
        assertEquals(0f, AudioMixer.curve(-0.5f), 0f)
    }

    @Test
    fun `крива монотонна і не має плато`() {
        var prev = -1f
        var v = 0f
        while (v <= 1.0001f) {
            val g = AudioMixer.curve(v)
            assertTrue("не росте на $v", g > prev)
            prev = g
            v += 0.05f
        }
    }

    @Test
    fun `баланс ассетів зберігається на будь-якому положенні повзунка`() {
        // Тап (coff 0.23) має лишатись рівно втричі тихішим за орбіту (0.69)
        // і на максимумі, і на чверті ходу — повзунок не міняє мікс.
        for (slider in listOf(1f, 0.5f, 0.25f)) {
            val tap    = AudioMixer.gain(0.23f, slider, 1f)
            val orbit3 = AudioMixer.gain(0.69f, slider, 1f)
            assertEquals("slider=$slider", 3f, orbit3 / tap, 1e-4f)
        }
    }

    @Test
    fun `системна гучність у формулу не входить`() {
        // Єдиний тест, який справді стереже баг патча 71: раніше play()
        // множив ще й на системну гучність, і та прикладалась двічі.
        // Якщо хтось поверне множник — тут з'явиться третій аргумент.
        val g = AudioMixer.gain(0.5f, 1f, 1f)
        assertEquals(0.5f, g, 1e-6f)
    }
}
