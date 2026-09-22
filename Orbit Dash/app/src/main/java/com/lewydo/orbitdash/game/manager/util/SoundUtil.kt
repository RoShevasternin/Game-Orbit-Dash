package com.lewydo.orbitdash.game.manager.util

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.content.Sfx
import com.lewydo.orbitdash.game.content.SfxCatalog
import com.lewydo.orbitdash.game.manager.AudioManager
import com.lewydo.orbitdash.game.utils.gdxGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// SoundUtil + саунд-директор (voice limiting, як у FMOD/Wwise).
//
//   1. THROTTLE per-sound: той самий звук не частіше ніж раз на throttleMs
//      (70мс дефолт). Вухо однакові звуки в цьому вікні зливає в один.
//   2. КАНАЛ + споживач на IO: GL-потік лише кладе запит (мікросекунди),
//      блокуючий sound.play() (SoundPool) виконується поза кадром.
//   3. DROP_LATEST при переповненні (8): при спамі зайві скидаються, а не
//      «доганяються» — інакше звук відстає від картинки.
//
//   Власний scope (як у MusicUtil) + Disposable: самодостатньо, не залежить
//   від порядку скасування gdxGame.coroutine.
// ─────────────────────────────────────────────────────────────────────────────

class SoundUtil : Disposable {

    private companion object {
        const val QUEUE_CAPACITY = 8
        /**
         * Множник синтезованих звуків: vol прототипу → динамік телефона.
         *
         * 3.2 = 1 / 0.31, де 0.31 — пік найгучнішого рецепта (ORBIT3, два шари).
         * Потрібен, бо гучність приїжджає ДВІЧІ: wav уже нормалізовано до повної
         * шкали, а coff = vol прототипу (0.06..0.31) тоді вдруге його притискає —
         * тап виходив на 4 % шкали проти 60 % у старого click.mp3, тобто нечутно.
         * Множник повертає абсолютний рівень, лишаючи баланс прототипу між звуками.
         */
        const val SYNTH_GAIN = 3.2f
    }

    // ── Синтезовані звуки (SoundSynth) ──────────────────────────────────────
    //  Один AdvancedSound на Sfx: тротлінг живе в ньому, тому кешуємо.
    private val synth = gdxGame.soundSynth
    private val bySfx = HashMap<Sfx, AdvancedSound>()

    /** Обгортка над запеченим семплом; coff = vol прототипу (wav нормалізовано). */
    fun sound(sfx: Sfx): AdvancedSound =
        bySfx.getOrPut(sfx) { AdvancedSound(synth.sound(sfx), synth.peak(sfx) * SYNTH_GAIN) }

    /** Відтворити подію з каталогу. */
    fun play(sfx: Sfx, playCoff: Float = 1f) = play(sound(sfx), playCoff)

    /** Кнопки й тумблери — теж синтез, як у прототипі; click.mp3 більше не грає. */
    val CLICK     = sound(SfxCatalog.UI_TICK)
    val CHECK_BOX = sound(SfxCatalog.CHECK_BOX)

    // 0..100
    var volumeLevel = AudioManager.volumeLevelPercent

    var isPause = (volumeLevel <= 0f)

    // ── Директор ─────────────────────────────────────────────────────────────

    private val coroutine = CoroutineScope(Dispatchers.Default)

    private val channel = Channel<PlayRequest>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_LATEST,   // спам → зайві геть
    )

    init {
        // Один споживач (послідовний). IO — бо sound.play() блокуючий.
        coroutine.launch(Dispatchers.IO) {
            for (req in channel) {
                runCatching { req.sound.play(req.volume) }   // не валимо цикл
            }
        }
    }

    /** Відтворити з тротлінгом. GL-потік НЕ блокується. */
    fun play(advancedSound: AdvancedSound, playCoff: Float = 1f) {
        if (isPause) return

        val now = System.currentTimeMillis()
        if (now - advancedSound.lastPlayMs < advancedSound.throttleMs) return
        advancedSound.lastPlayMs = now

        val volume = ((volumeLevel / 100f) * advancedSound.coff) * playCoff
        channel.trySend(PlayRequest(advancedSound.sound, volume))
    }

    override fun dispose() {
        channel.close()
        coroutine.cancel()
    }

    private class PlayRequest(val sound: Sound, val volume: Float)

    /** coff — гучність; throttleMs — мін. інтервал між повторами ЦЬОГО звуку. */
    class AdvancedSound(
        val sound: Sound,
        val coff : Float,
        val throttleMs: Long = 70L,
    ) {
        internal var lastPlayMs = 0L
    }

}