package com.lewydo.orbitdash.game

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.ScreenUtils
import com.lewydo.orbitdash.BuildConfig
import com.lewydo.orbitdash.MainActivity
import com.lewydo.orbitdash.game.manager.MusicManager
import com.lewydo.orbitdash.game.manager.NavigationManager
import com.lewydo.orbitdash.game.manager.ParticleEffectManager
import com.lewydo.orbitdash.game.manager.SoundManager
import com.lewydo.orbitdash.game.manager.SpriteManager
import com.lewydo.orbitdash.game.manager.util.MusicUtil
import com.lewydo.orbitdash.game.manager.util.ParticleEffectUtil
import com.lewydo.orbitdash.game.manager.util.SoundUtil
import com.lewydo.orbitdash.game.manager.util.SpriteUtil
import com.lewydo.orbitdash.game.manager.util.VibroUtil
import com.lewydo.orbitdash.game.model.PlayerModel
import com.lewydo.orbitdash.game.screens.BrandScreen
import com.lewydo.orbitdash.game.screens.LoaderScreen
import com.lewydo.orbitdash.game.state.GameState
import com.lewydo.orbitdash.game.state.SaveGameStateManager
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.Settings
import com.lewydo.orbitdash.game.utils.ShaderClock
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGame
import com.lewydo.orbitdash.game.utils.disposeAll
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfManager
import com.lewydo.orbitdash.game.utils.runGDX
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache
import com.lewydo.orbitdash.services.analytics.AnalyticsManager
import com.lewydo.orbitdash.util.currentClassName
import com.lewydo.orbitdash.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GDXGame(val activity: MainActivity) : AdvancedGame() {

    // ------------------------------------------------------------------------
    // Assets
    // ------------------------------------------------------------------------

    val assetsBrand  by lazy { SpriteUtil.Brand() }
    val assetsLoader by lazy { SpriteUtil.Loader() }
    val assetsAll    by lazy { SpriteUtil.All() }

    //val particleEffectLoader by lazy { ParticleEffectUtil.Loader() }
    val particleEffectAll by lazy { ParticleEffectUtil.All() }

    // ------------------------------------------------------------------------
    // Audio
    // ------------------------------------------------------------------------

    val musicUtil by lazy { MusicUtil() }
    val soundUtil by lazy { SoundUtil() }
    val vibroUtil by lazy { VibroUtil() }

    // ------------------------------------------------------------------------
    // Managers
    // ------------------------------------------------------------------------

    lateinit var assetManager         : AssetManager          private set
    lateinit var navigationManager    : NavigationManager     private set
    lateinit var spriteManager        : SpriteManager         private set
    lateinit var musicManager         : MusicManager          private set
    lateinit var soundManager         : SoundManager          private set
    lateinit var particleEffectManager: ParticleEffectManager private set
    lateinit var msdfManager          : MsdfManager           private set

    // ------------------------------------------------------------------------
    // Coroutine
    // ------------------------------------------------------------------------

    val coroutine = CoroutineScope(Dispatchers.Default)

    // ------------------------------------------------------------------------
    // GameState
    // ------------------------------------------------------------------------

    private val gameState   = GameState()
    private val saveManager = SaveGameStateManager(gameState, coroutine)

    // ------------------------------------------------------------------------
    // Models
    // ------------------------------------------------------------------------

    val modelPlayer = PlayerModel(gameState, coroutine)

    // ------------------------------------------------------------------------
    // Services
    // ------------------------------------------------------------------------

    val settings  by lazy { Settings() }
    val analytics by lazy { AnalyticsManager() }

    // ------------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------------

    var backgroundColor = GameColor.background
    val disposableSet   = mutableSetOf<Disposable>()

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    override fun create() {
        assetManager          = AssetManager()
        spriteManager         = SpriteManager(assetManager)
        musicManager          = MusicManager(assetManager)
        soundManager          = SoundManager(assetManager)
        particleEffectManager = ParticleEffectManager(assetManager)
        msdfManager           = MsdfManager()
        navigationManager     = NavigationManager(this)

        saveManager.load()
        saveManager.startAutoSave(intervalSec = 30)

        collectModelPlayer()

        val firstScreenName = if (BuildConfig.DEBUG) LoaderScreen::class.java.name else BrandScreen::class.java.name
        navigationManager.navigate(firstScreenName)

        ShaderProgram.pedantic = false
    }

    override fun render() {
        ShaderClock.update()
        ThemeManager.update()
        ScreenUtils.clear(backgroundColor)
        super.render()

        syncTheme()
    }

    override fun pause() {
        super.pause()
        log("pause")
        saveManager.save() // потім зберігаємо вже свіжий стан
    }

    override fun resume() {
        super.resume()
        log("resume")
        Blit.dispose()
    }

    override fun dispose() {
        saveManager.stopAutoSave()
        saveManager.save()

        try {
            coroutine.cancel()
            disposableSet.disposeAll()
            disposeAll(assetManager, musicUtil, soundUtil, VfxShaderCache, Blit, msdfManager)
            super.dispose()
            log("dispose $currentClassName")
        } catch (e: Exception) {
            log("exception: ${e.message}")
        }
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------
    private fun collectModelPlayer() {
        coroutine.launch {
            modelPlayer.isLoadedFlow.first { it }

            // Перше застосування — миттєве: гравець не має бачити,
            // як його золотий скін «переїжджає» з неонового
            runGDX { ThemeManager.initWith(modelPlayer.currentSkinId) }

            // Далі кожна зміна — з плавним лерпом
            modelPlayer.skinIdFlow.collect { runGDX { ThemeManager.switchTo(it) } }
        }
    }

    private fun syncTheme() {
        val t = ThemeManager.current
        backgroundColor.set(t.bg)
    }

}