package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.background.AComet
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.AHug
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AAnchorOf
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.loader.AMainLoader
import com.lewydo.orbitdash.game.actors.panel.APanelMenu
import com.lewydo.orbitdash.game.actors.panel.APanelMenuState
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.actor.enable
import com.lewydo.orbitdash.game.utils.actor.setSize
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.runGDX
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.services.analytics.AnalyticsManager
import com.lewydo.orbitdash.util.log
import com.selftest.mindora.game.actors.progress.AProgressItemPortrait

// ----------------------------------------------------------------------------
//  ЧОМУ AHug, А НЕ detach. Політ панелей — це CSS transform:translate у
//  прототипі: РАМКА стоїть у лейауті, КОНТЕНТ їздить усередині. AHug — саме
//  така рамка: дзеркалить розмір дитини, а content.x/y вільні для анімацій.
//  Констрейнти живуть безперервно (жодних detach/re-add вікон), тож resize,
//  банер чи зміна висоти HUG посеред польоту нічого не ламають.
// ----------------------------------------------------------------------------
class MenuScreen : AdvancedScreen() {

    companion object {
        // Тайминги = React-прототип меню:
        //   transform 0.7s cubic-bezier(0.22,1,0.36,1) → exp10Out
        //   opacity   0.5s ease                        → sine
        private const val ENTER_OFFSET    = 70f
        private const val ENTER_TIME      = 0.7f
        private const val ENTER_TIME_FADE = 0.5f
        private const val DELAY_MENU      = 0.07f   // каскад: стата → кнопки

        /** Вихід швидший за вхід — «пірнаємо в гру», а не прощаємось. */
        private const val EXIT_TIME_PANEL  = 0.3f
        private const val EXIT_TIME_MAIN   = 0.34f
        private const val DELAY_EXIT_STATE = 0.05f
        private const val DELAY_EXIT_MAIN  = 0.08f

        /** Сумарний час виходу до перемикання екрана. */
        private const val TIME_HIDE_TOTAL = 0.42f

        /** Поява брендблоку, коли він НЕ приїхав з лоадера. */
        private const val ENTER_TIME_MAIN = 0.45f
        private const val MAIN_ZOOM       = 1.14f   // = зум виходу: рух туди-назад однаковий
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aStarField by lazy { AStarField(this) }
    private val aComet     by lazy { AComet(this) }
    private val aMain      by lazy { AMainLoader(this, AMainLoader.State.MENU) }

    /** Ланка конвертації root-екрана: не покладаємось на те, що aMain 1:1 з root. */
    private val aTitlesAnchor by lazy { AAnchorOf(aMain.aTitlesAnchor) }

    private val aPanelMenuState by lazy { APanelMenuState(this) }
    private val aPanelMenu      by lazy { APanelMenu(this) }

    /** Рамки польоту: розмір беруть у дитини, content їздить вільно. */
    private val aHugState by lazy { AHug(this, aPanelMenuState) }
    private val aHugMenu  by lazy { AHug(this, aPanelMenu) }

    private val ads get() = gdxGame.activity.adManager.rewarded

    /**
     * Безшовний морф можливий ТІЛЬКИ з лоадера: там aMain стояв у кадрі
     * мілісекунду тому, на тих самих якорях. З решти екранів брендблоку не
     * існувало — він має з'явитись сам (animEnterZoom).
     */
    private val isFromLoader
        get() = gdxGame.navigationManager.fromScreenName == LoaderScreen::class.java.name

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        wirePanelMenu()

        // Реклама живе на UI-потоці, сцена — на GL. Кожен сигнал доступності
        // перестрибує через runGDX; початковий стан знімаємо одразу, бо
        // rewarded міг завантажитись ще на лоадері.
        ads.onAvailabilityChanged = { ready -> runGDX { aPanelMenu.refresh(ready) } }
        aPanelMenu.refresh(ads.isReady)

        animShowScreen()
    }

    override fun dispose() {
        // Колбек тримає лямбду з посиланням на екран — обірвати, інакше
        // мертвий MenuScreen отримуватиме сигнали після навігації.
        ads.onAvailabilityChanged = null
        super.dispose()
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
        addAndFillActor(aComet)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        add(aMain) { fillParent() }
        addActor(aTitlesAnchor)

        addPanels()

        addDebugHud(ADebugHud(this@MenuScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val v = stageUI.screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
        aStarField.animRippleAt(v.x, v.y)
        return false
    }

    // ------------------------------------------------------------------------
    // Panel wiring
    // ------------------------------------------------------------------------
    //
    //  Панель — німа: вона знає ЩО пропонує (буст, суму), але не знає, як
    //  показується реклама і куди вести навігацію. Уся оркестрація тут.
    //  Колбеки AdMob приходять на UI — кожен дотик до гри через runGDX.
    //
    private fun wirePanelMenu() = with(aPanelMenu) {
        onPlay = {
            animHideScreen {
                gdxGame.navigationManager.navigate(
                    toScreenName   = GameScreen::class.java.name,
                    fromScreenName = MenuScreen::class.java.name,
                )
            }
        }

        onPlayBoost = { boost ->
            ads.show(
                onEarned = {
                    runGDX {
                        rerollBoost()   // буст спожитий, наступний — свіжий
                        // TODO: navigate GameScreen зі стартовим бустом,
                        //  коли RunEngine.Config поїде через navigate(key)
                        log("PLAY with start boost: $boost")
                    }
                },
                onFailed = {
                    // Не показалась — офер більше не валідний. Панель і сама
                    // погасить кнопки через onAvailabilityChanged; це страховка.
                    runGDX { refresh(ads.isReady) }
                },
            )
        }

        onGems = { amount ->
            ads.show(
                onEarned = {
                    runGDX {
                        gdxGame.modelPlayer.addGems(amount, AnalyticsManager.GemSource.AD)
                        AnalyticsManager.adReward(AnalyticsManager.Placement.GEMS_MENU)
                        rerollGems()
                    }
                },
                onFailed = {
                    runGDX { refresh(ads.isReady) }
                },
            )
        }

        onShop  = { log("SHOP") }   // TODO: navigate ShopScreen
        onDaily = { log("DAILY") }  // TODO: navigate MissionsScreen
        onRanks = {
            gdxGame.navigationManager.navigate(
                toScreenName   = LeaderboardScreen::class.java.name,
                fromScreenName = MenuScreen::class.java.name,
            )
        }
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------

    override fun animShowScreen(blockEnd: Block) {
        if (isFromLoader) {
            // Морф: aMain уже «стоїть» з минулого кадру лоадера — не чіпаємо.
        } else {
            aMain.animEnterZoom()
        }

        animEnterPanelState()
        animEnterPanelMenu()

        rootConstraintLayout.animDelay(ENTER_TIME) { blockEnd() }
    }

    /**
     * Вихід у гру: панелі пірнають униз, брендблок наближається й тане.
     * root вимикається одразу — подвійний клік по PLAY не запустить два рани.
     */
    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.disable()

        animExitPanelMenu()
        animExitPanelState(delay = DELAY_EXIT_STATE)
        aMain.animExitZoom(delay = DELAY_EXIT_MAIN)

        rootConstraintLayout.animDelay(TIME_HIDE_TOTAL) { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------

    private fun AConstraintLayout.addPanels() {
        // Сіди для HUG: ширина стати = 1 (роздасться), меню = 300 з макета
        aPanelMenuState.setSize(1f, 16f)
        aPanelMenu.setSize(300f, 1f)

        add(aHugState) { centerX(); topToBottom(aTitlesAnchor, 20f) }
        add(aHugMenu)  { centerX(); topToBottom(aHugState); bottomToBottom() }
    }

    // ------------------------------------------------------------------------
    // Animations · панелі (двигуни працюють з CONTENT усередині AHug)
    // ------------------------------------------------------------------------

    private fun animEnterPanelState() = animEnterContent(aPanelMenuState, delay = 0f)
    private fun animEnterPanelMenu()  = animEnterContent(aPanelMenu, delay = DELAY_MENU)

    private fun animExitPanelState(delay: Float = 0f) = animExitContent(aPanelMenuState, delay)
    private fun animExitPanelMenu (delay: Float = 0f) = animExitContent(aPanelMenu, delay)

    /** Виплив знизу: content зсунутий на -OFFSET і повертається з fade-in. */
    private fun animEnterContent(content: Actor, delay: Float) {
        content.clearActions()
        content.color.a = 0f
        content.y = -ENTER_OFFSET
        content.disable()

        content.addAction(Actions.sequence(
            Actions.delay(delay),
            // enable на СТАРТІ польоту: елемент клікабельний, щойно почав
            // проявлятись — як pointerEvents разом із transition у прототипі.
            Actions.run { content.enable() },
            Actions.parallel(
                Actions.moveTo(content.x, 0f, ENTER_TIME, Interpolation.exp10Out),
                Actions.fadeIn(ENTER_TIME_FADE, Interpolation.sine),
            ),
        ))
    }

    /** Пірнання вниз із розчиненням. */
    private fun animExitContent(content: Actor, delay: Float) {
        content.clearActions()
        content.addAction(Actions.sequence(
            Actions.delay(delay),
            Actions.parallel(
                Actions.moveTo(content.x, -ENTER_OFFSET, EXIT_TIME_PANEL, Interpolation.exp5In),
                Actions.fadeOut(EXIT_TIME_PANEL, Interpolation.sine),
            ),
        ))
    }

    // ------------------------------------------------------------------------
    // Animations · брендблок (scale+alpha не конфліктують з констрейнтами —
    // resolveNode пише лише x/y/width/height, тож detach не потрібен)
    // ------------------------------------------------------------------------

    /** Дзеркало виходу: 1.14 → 1.0 з проявленням — блок «сідає» на місце. */
    private fun Actor.animEnterZoom(delay: Float = 0f) {
        setOrigin(Align.center)
        clearActions()
        color.a = 0f
        setScale(MAIN_ZOOM)

        addAction(Actions.sequence(
            Actions.delay(delay),
            Actions.parallel(
                Actions.scaleTo(1f, 1f, ENTER_TIME_MAIN, Interpolation.exp10Out),
                // Альфа доганяє швидше за масштаб: інакше блок довго висить
                // напівпрозорим, і поява читається як «підвисло», а не як рух.
                Actions.fadeIn(ENTER_TIME_MAIN * 0.7f, Interpolation.sine),
            ),
        ))
    }

    /** Наближення з розчиненням — «пірнаємо в гру». */
    private fun Actor.animExitZoom(delay: Float = 0f) {
        setOrigin(Align.center)
        clearActions()

        addAction(Actions.sequence(
            Actions.delay(delay),
            Actions.parallel(
                Actions.scaleTo(MAIN_ZOOM, MAIN_ZOOM, EXIT_TIME_MAIN, Interpolation.exp5In),
                Actions.fadeOut(EXIT_TIME_MAIN, Interpolation.sine),
            ),
        ))
    }

}
