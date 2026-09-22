package com.lewydo.orbitdash.game.utils

class Settings {
    var IS_ALARM = true

    var IS_VIBRO: Boolean
        get() = gdxGame.vibroUtil.isVibro
        set(value) {
            gdxGame.vibroUtil.isVibro = value
        }

    // ------------------------------------------------------------------------
    // Звук — три повзунки, 0..1
    // ------------------------------------------------------------------------
    //
    //  Джерело правди — сейв (PlayerData), а не стан програвача. Раніше
    //  IS_MUSIC читався як currentMusic.isPlaying, тобто «налаштування»
    //  самовільно перемикалось щоразу, коли музику глушила реклама або пауза.
    //
    var MASTER_VOLUME: Float
        get() = gdxGame.modelPlayer.volMaster
        set(value) { gdxGame.modelPlayer.setMasterVolume(value) }

    var MUSIC_VOLUME: Float
        get() = gdxGame.modelPlayer.volMusic
        set(value) { gdxGame.modelPlayer.setMusicVolume(value) }

    var SFX_VOLUME: Float
        get() = gdxGame.modelPlayer.volSfx
        set(value) { gdxGame.modelPlayer.setSfxVolume(value) }

    /** Тумблер над повзунком: вимкнути — 0, увімкнути — на повну. */
    var IS_SOUND: Boolean
        get() = SFX_VOLUME > 0f
        set(value) { SFX_VOLUME = if (value) 1f else 0f }

    var IS_MUSIC: Boolean
        get() = MUSIC_VOLUME > 0f
        set(value) { MUSIC_VOLUME = if (value) 1f else 0f }
}