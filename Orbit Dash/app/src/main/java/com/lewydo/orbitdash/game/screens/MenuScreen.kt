package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.lewydo.orbitdash.game.actors.background.AComet
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AAnchorOf
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.loader.AMainLoader
import com.lewydo.orbitdash.game.actors.panel.APanelMenu
import com.lewydo.orbitdash.game.actors.panel.APanelMenuState
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.animHideAndDisable
import com.lewydo.orbitdash.game.utils.actor.enable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

class MenuScreen : AdvancedScreen() {

    companion object {
        /**
         * Пауза перед стартом появи. Не косметика: aPanelMenu має sizingH = HUG,
         * і його висота стає справжньою лише після першого act(). Стартуємо
         * раніше — запам'ятаємо «куди повертатись» за старим розміром.
         */
        private const val WAIT_LAYOUT = 0.1f

        /** Панель статистики трохи випереджає кнопки — рух читається як каскад. */
        private const val DELAY_STATE = 0f
        private const val DELAY_MENU  = 0.06f
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

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
        addAndFillActor(aComet)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        add(aMain) { fillParent() }
        addActor(aTitlesAnchor)

        addPanelMenuState()
        addPanelMenu()

        animEnterPanels()

        addDebugHud(ADebugHud(this@MenuScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val v = stageUI.screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
        aStarField.animRippleAt(v.x, v.y)
        return false
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------
    override fun animHideScreen(blockEnd: Block) {}
    override fun animShowScreen(blockEnd: Block) {}

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    //  ДОДАВАННЯ РОЗДІЛЕНЕ НАДВОЄ — і це не стиль, а вимога HUG.
    //
    //  addPanelX()    — сід розміру + констрейнти. РІВНО ОДИН РАЗ.
    //  attachPanelX() — самі констрейнти. Стільки разів, скільки треба.
    //
    //  Числа в setSize по HUG-осі (1f) — заглушка, яку AAutoLayout перезапише
    //  на першому act(). Викликати setSize вдруге означає СКИНУТИ вже
    //  порахований розмір назад у 1: кадр із висотою 1 (а позиція aPanelMenu
    //  йде через verticalBias, тож він ще й стрибне на середину діапазону),
    //  потім кадр із правильною. Це і був той ривок із блиманням.
    //
    //  Повторний add() безпечний сам по собі: Group.addActor бачить
    //  parent == this і виходить, тож склад дітей і z-order не міняються —
    //  переписується лише вузол лейаута. Тобто «просто перепривʼязати» = add()
    //  без setSize, переробляти AConstraintLayout не довелось.

    private fun AConstraintLayout.addPanelMenuState() {
        aPanelMenuState.setSize(1f, 16f)   // ширина — сід для HUG
        attachPanelMenuState()
    }

    private fun AConstraintLayout.attachPanelMenuState() {
        add(aPanelMenuState) { centerX(); topToBottom(aTitlesAnchor, 20f) }
    }

    private fun AConstraintLayout.addPanelMenu() {
        aPanelMenu.setSize(300f, 1f)       // висота — сід для HUG
        attachPanelMenu()
    }

    private fun AConstraintLayout.attachPanelMenu() {
        add(aPanelMenu) { centerX(); topToBottom(aPanelMenuState); bottomToBottom() }
    }

    // ------------------------------------------------------------------------
    // Animations
    // ------------------------------------------------------------------------
    /**
     * Зорі, комета й aMain приходять з лоадера без розриву — їх не чіпаємо.
     * Нове на цьому екрані лише меню, тому випливає лише воно.
     */
    private fun animEnterPanels() {
        aPanelMenuState.animHideAndDisable()
        aPanelMenu.animHideAndDisable()

        rootConstraintLayout.animDelay(WAIT_LAYOUT) {
            // Спершу ЗАЛЕЖНИЙ: поки aPanelMenu під лейаутом, він тягнеться за
            // своїм якорем aPanelMenuState і зіпсував би його політ.
            aPanelMenuState.animEnterUp(delay = DELAY_STATE)
            aPanelMenu.animEnterUp(delay = DELAY_MENU) {
                // Спершу ЯКІР, потім залежний: інакше attachPanelMenu()
                // порахує позицію від aPanelMenuState, який ще поза лейаутом.
                rootConstraintLayout.attachPanelMenuState()
                rootConstraintLayout.attachPanelMenu()
            }
        }
    }

    /**
     * Поява «випливанням знизу»: актор УЖЕ стоїть там, де має бути (лейаут його
     * розставив) — ми зсуваємо його вниз на [offsetY] і повертаємо назад з fade-in.
     *
     * Той самий контракт, що в animToTarget: на час польоту актор знімається з
     * констрейнтів, інакше будь-який dirty (зміна розміру дитини, рух якоря)
     * поверне його в ціль посеред руху. Повернення під лейаут — у [blockEnd]:
     * повторний add з тими самими констрейнтами.
     *
     * ПОРЯДОК ДЛЯ ЗАЛЕЖНИХ: якщо B заякорений на A — виклич спершу для B.
     * Поки B під лейаутом, він тягнеться за A і зіпсує його політ.
     *
     * Дефолти = таймінги React-прототипу меню:
     *   transform 0.7s cubic-bezier(0.22, 1, 0.36, 1)  → exp10Out
     *   opacity   0.5s ease                            → sine
     */
    fun Actor.animEnterUp(
        offsetY: Float = 70f,
        time: Float = 0.7f,
        timeFade: Float = 0.5f,
        delay: Float = 0f,
        interpolation: Interpolation = Interpolation.exp10Out,
        blockEnd: Block = {},
    ) {
        (parent as? AConstraintLayout)?.detach(this)

        clearActions()
        color.a = 0f
        isVisible = true
        moveBy(0f, -offsetY)

        addAction(Actions.sequence(
            Actions.delay(delay),
            // enable на СТАРТІ польоту, а не в кінці: елемент клікабельний, щойно
            // почав проявлятись — так само, як pointerEvents вмикається разом
            // з transition у прототипі.
            Actions.run { enable() },
            Actions.parallel(
                Actions.moveBy(0f, offsetY, time, interpolation),
                Actions.fadeIn(timeFade, Interpolation.sine),
            ),
            Actions.run(blockEnd),
        ))
    }

}