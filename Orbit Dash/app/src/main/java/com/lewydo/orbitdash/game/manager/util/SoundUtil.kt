package com.lewydo.orbitdash.game.manager.util

import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.content.Sfx
import com.lewydo.orbitdash.game.content.SfxCatalog
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
//   4. ГУЧНІСТЬ — не тут. Фінальне число дає AudioMixer: coff ассета × шина
//      SFX × майстер × запас. Системної гучності телефона у формулі немає,
//      її накладає Android поверх.
//
//   Власний scope (як у MusicUtil) + Disposable: самодостатньо, не залежить
//   від порядку скасування gdxGame.coroutine.
// ─────────────────────────────────────────────────────────────────────────────

class SoundUtil : Disposable {

    private companion object { const val QUEUE_CAPACITY = 8 }

    // ── Синтезовані звуки (SoundSynth) ──────────────────────────────────────
    //  Один AdvancedSound на Sfx: тротлінг живе в ньому, тому кешуємо.
    private val synth = gdxGame.soundSynth
    private val bySfx = HashMap<Sfx, AdvancedSound>()

    /**
     * Обгортка над запеченим семплом.
     *
     * coff = пік рецепта / пік найгучнішого рецепта: найгучніший звук каталогу
     * іде на 1.0, решта — у пропорції прототипу. Не абсолютний vol із JSX:
     * wav уже нормалізовано, тож vol удруге притиснув би тап до 4 % шкали.
     */
    fun sound(sfx: Sfx): AdvancedSound =
        bySfx.getOrPut(sfx) { AdvancedSound(synth.sound(sfx), synth.peak(sfx) / synth.loudestPeak) }

    /** Відтворити подію з каталогу. */
    fun play(sfx: Sfx, playCoff: Float = 1f) = play(sound(sfx), playCoff)

    /** Кнопки й тумблери — теж синтез, як у прототипі; click.mp3 більше не грає. */
    val CLICK     = sound(SfxCatalog.UI_TICK)
    val CHECK_BOX = sound(SfxCatalog.CHECK_BOX)

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
        if (AudioMixer.isSfxSilent) return

        val now = System.currentTimeMillis()
        if (now - advancedSound.lastPlayMs < advancedSound.throttleMs) return
        advancedSound.lastPlayMs = now

        channel.trySend(PlayRequest(advancedSound.sound, AudioMixer.sfx(advancedSound.coff, playCoff)))
    }

    override fun dispose() {
        channel.close()
        coroutine.cancel()
    }

    private class PlayRequest(val sound: Sound, val volume: Float)

    /** coff — вага в міксі 0..1 (автор); throttleMs — мін. інтервал між повторами ЦЬОГО звуку. */
    class AdvancedSound(
        val sound: Sound,
        val coff : Float,
        val throttleMs: Long = 70L,
    ) {
        internal var lastPlayMs = 0L
    }

}