package com.lewydo.orbitdash.game.utils

class Settings {
    var IS_ALARM = true

    var IS_VIBRO: Boolean
        get() = gdxGame.vibroUtil.isVibro
        set(value) {
            gdxGame.vibroUtil.isVibro = value
        }

    var IS_SOUND: Boolean
        get() = gdxGame.soundUtil.isPause.not()
        set(value) {
            gdxGame.soundUtil.isPause = !value
        }

    var IS_MUSIC: Boolean
        get() = gdxGame.musicUtil.currentMusic?.isPlaying ?: false
        set(value) {
            if (value) gdxGame.musicUtil.currentMusic?.play()
            else gdxGame.musicUtil.currentMusic?.pause()
        }
}