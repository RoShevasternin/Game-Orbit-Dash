package com.lewydo.orbitdash.game.manager.util

import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.manager.MusicManager
import com.lewydo.orbitdash.game.utils.runGDX
import com.lewydo.orbitdash.util.cancelCoroutinesAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ----------------------------------------------------------------------------
//  МУЗИКА — ОДНА ДОРІЖКА, ЯКА ЗАВЖДИ ЗНАЄ СВОЮ ГУЧНІСТЬ.
//
//  Грає не більше одного треку: другий play() глушить перший. Гучність не
//  рахується тут — її дає AudioMixer (master × повзунок MUSIC), а трек
//  домножує власний coff із MusicManager.EnumMusic.
//
//  Повзунок тягнуть пальцем — тому не «перечитати при старті треку», а
//  ПІДПИСКА: collect на шину мікшера ставить volume, поки палець ще на
//  повзунку. Це і є та причина, через яку гучність живе у Flow, а не в полі.
// ----------------------------------------------------------------------------
class MusicUtil : Disposable {

    private val coroutine = CoroutineScope(Dispatchers.Default)

    // ── Field ───────────────────────────────────────────────────────────────

    /** Що грає зараз. null — тиша. */
    var current: MusicManager.EnumMusic? = null
        private set

    private val coff get() = current?.data?.coff ?: 1f

    init {
        // Шина змінилась (повзунок, майстер) → миттєво на доріжку.
        coroutine.launch {
            AudioMixer.musicBusFlow.collect { bus ->
                runGDX { current?.data?.music?.volume = bus * coff }
            }
        }
    }

    // ── API ─────────────────────────────────────────────────────────────────

    /** Запустити трек. Той самий трек удруге — нічого не робить, не перезапускає. */
    fun play(music: MusicManager.EnumMusic, looping: Boolean = true) = runGDX {
        if (current == music) return@runGDX
        current?.data?.music?.stop()
        current = music
        music.data.music.apply {
            isLooping = looping
            volume    = AudioMixer.musicBus() * music.data.coff
            play()
        }
    }

    fun stop() = runGDX {
        current?.data?.music?.stop()
        current = null
    }

    /** Гучність доріжки просто зараз — для дебаг-панелі й тестів на пристрої. */
    val volume: Float get() = current?.data?.music?.volume ?: 0f

    override fun dispose() {
        cancelCoroutinesAll(coroutine)
        current = null
    }
}
