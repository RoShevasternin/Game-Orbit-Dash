package com.lewydo.orbitdash.game.manager

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas

class SpriteManager(var assetManager: AssetManager) {

    var loadableAtlasList    = mutableListOf<AtlasData>()
    var loadableTexturesList = mutableListOf<TextureData>()
    var loadableGroupList    = mutableListOf<TextureGroupData>()

    // ------------------------------------------------------------------------
    // Atlas
    // ------------------------------------------------------------------------
    fun loadAtlas() {
        loadableAtlasList.onEach { assetManager.load(it.path, TextureAtlas::class.java) }
    }

    fun initAtlas() {
        loadableAtlasList.onEach { it.atlas = assetManager[it.path, TextureAtlas::class.java] }
        loadableAtlasList.clear()
    }

    // ------------------------------------------------------------------------
    // Texture
    // ------------------------------------------------------------------------
    fun loadTexture() {
        loadableTexturesList.onEach { assetManager.load(it.path, Texture::class.java) }
    }

    fun initTexture() {
        loadableTexturesList.onEach { it.texture = assetManager[it.path, Texture::class.java] }
        loadableTexturesList.clear()
    }

    // ------------------------------------------------------------------------
    // TextureGroup
    // ------------------------------------------------------------------------
    fun loadGroups() {
        loadableGroupList.onEach { group -> group.paths.forEach { assetManager.load(it, Texture::class.java) } }
    }

    fun initGroups() {
        loadableGroupList.onEach { group -> group.textures = group.paths.map { assetManager[it, Texture::class.java] } }
        loadableGroupList.clear()
    }

    // ------------------------------------------------------------------------
    // Util
    // ------------------------------------------------------------------------
    fun initAll() {
        initAtlas()
        initTexture()
        initGroups()
    }


    /**
     * Синхронно завантажити й ініціалізувати атласи — для того, що потрібно
     * ДО лоадера: BRAND на бренд-екрані, MSDF з першого кадру будь-якого екрана.
     * Блокує потік на час завантаження, тож лише для дрібних атласів.
     */
    fun loadAtlasNow(vararg atlases: EnumAtlas) {
        loadableAtlasList = atlases.map { it.data }.toMutableList()
        loadAtlas()
        assetManager.finishLoading()
        initAtlas()
    }

    // ------------------------------------------------------------------------
    // EnumAtlas
    // ------------------------------------------------------------------------
    enum class EnumAtlas(val data: AtlasData) {
        BRAND(AtlasData("atlas/brand.atlas")),

        LOADER(AtlasData("atlas/loader.atlas")),

        ALL     (AtlasData("atlas/all.atlas")),
        _9_PATCH(AtlasData("atlas/9_patch.atlas")),
        MSDF    (AtlasData("atlas/msdf.atlas")),
    }

    // ------------------------------------------------------------------------
    // EnumTexture
    // ------------------------------------------------------------------------
    enum class EnumTexture(val data: TextureData) {
        //bg_test(TextureData("textures/bg_test.png")),

        // Band
        //BRAND_BACKGROUND(TextureData("textures/brand/background.png")),

        // Loader
        ORBIT_GLOW(TextureData("textures/loader/orbit_glow.png")),

//        // All
        star    (TextureData("textures/star.png")),
//        LIGHT    (TextureData("textures/all/LIGHT.png")),
//
//        // All | panel
//        PANEL_TOP           (TextureData("textures/all/panel/panel_top.png")),
//
//        // All | dialog
//        DIALOG_CLEAR_GRID   (TextureData("textures/all/dialog/dialog_clear_grid.png")),
    }

    // ------------------------------------------------------------------------
    // EnumTextureGroup
    // ------------------------------------------------------------------------
    enum class EnumTextureGroup(
        private val folder: String,
        private val prefix: String,
        private val count : Int,
        private val separator: String = "_",
    ) {
        //LIGHT_C("textures/loader/light/", "c", 6, ""),

        ;
        val data: TextureGroupData by lazy {
            TextureGroupData((1..count).map { "$folder/$prefix$separator$it.png" })
        }
    }

    data class AtlasData(val path: String) {
        lateinit var atlas: TextureAtlas
    }

    data class TextureData(val path: String) {
        lateinit var texture: Texture
    }

    data class TextureGroupData(val paths: List<String>) {
        lateinit var textures: List<Texture>
    }

}