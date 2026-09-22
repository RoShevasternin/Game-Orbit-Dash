package com.lewydo.orbitdash.game.manager

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Music

class MusicManager(var assetManager: AssetManager) {

    var loadableMusicList = mutableListOf<MusicData>()

    fun load() {
        loadableMusicList.onEach { assetManager.load(it.path, Music::class.java) }
    }

    fun init() {
        loadableMusicList.onEach { it.music = assetManager[it.path, Music::class.java] }
        loadableMusicList.clear()
    }

    enum class EnumMusic(val data: MusicData) {
        /** coff 0.27 — трек голосний сам по собі; під ним ще мають читатись ефекти. */
        MAIN(MusicData("music/main.mp3", coff = 0.27f)),
    }

    /** coff — вага треку в міксі, 0..1. Авторська, не повзунок гравця (AudioMixer). */
    data class MusicData(
        val path: String,
        val coff: Float = 1f,
    ) {
        lateinit var music: Music
    }

}