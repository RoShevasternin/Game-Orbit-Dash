package com.lewydo.orbitdash.game.actors.panel

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.panel.boost.APanelShield
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.actors.panel.boost.APanelBooster
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync

// ----------------------------------------------------------------------------
//  ВЕРХНЯ СМУГА ГРИ. Панель НІМА: щокадру читає стан рушія і показує його,
//  жодних ігрових рішень не приймає.
//
//  ЧОМУ ЩОКАДРУ, А НЕ НА ПОДІЯХ: рахунок, геми й таймер комбо змінюються
//  безперервно — «підписатись» на них означало б слати 60 подій на секунду.
//  Дискретне (смерть, підбір бусту) приходить колбеками рушія в GameScreen.
//  Захист від зайвої роботи — кеш попередніх значень: setText викликається
//  лише коли число справді змінилось.
// ----------------------------------------------------------------------------
class APanelGameHud(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val theme     = ThemeManager.current
    private val themeSync = ThemeSync(::syncTheme)

    // Кеш: HUD оновлює текст лише при зміні значення
    private var lastScore = -1
    private var lastGems  = -1
    private var lastMult  = -1

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf = gdxGame.msdfManager

    private val styleBold = MsdfStyle(msdf, msdf.fontInter_Bold, 1f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aScoreLbl = AMsdfLabel("0", styleBold, 34f, Color.WHITE)
    private val aGemLbl   = AMsdfLabel("◆ 0", styleBold, 14f, theme.gem)
    private val aComboLbl = AMsdfLabel("", styleBold, 14f, theme.player)

    private val aPanelShield  = APanelShield(screen)
    private val aPanelBooster = APanelBooster(screen)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addScoreLbl()
        addGemLbl()
        addComboLbl()

        addPanelShield()
        addPanelBooster()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------

    /**
     * Єдина точка оновлення. Кличеться з GameScreen.render() щокадру.
     *
     * Геми — ЗІБРАНІ ЗА РАН, не баланс: у рані важливо, скільки назбирав
     * цього разу; баланс видно в меню.
     */
    fun syncFrom(engine: RunEngine) {
        val gems = engine.gemsCollected

        if (engine.score != lastScore) {
            lastScore = engine.score
            aScoreLbl.setText(engine.score.toString())
        }

        if (gems != lastGems) {
            lastGems = gems
            aGemLbl.setText("◆ $gems")
        }

        // Множник цілий і рідко міняється, тому кешуємо саме його, а не combo.
        // Стеля — у рушії (RunEngine.multiplier): це правило гри, не вигляд.
        val mult = if (engine.combo > 0f) engine.multiplier else 0
        if (mult != lastMult) {
            lastMult = mult
            aComboLbl.isVisible = mult > 0
            if (mult > 0) aComboLbl.setText("COMBO x$mult")
        }

        aPanelShield.syncFrom(engine.shieldMax, engine.shield)
        aPanelBooster.syncFrom(engine)
    }

    /** Новий ран — скидаємо кеш, інакше перший кадр покаже старі числа. */
    fun reset() {
        lastScore = -1
        lastGems  = -1
        lastMult  = -1
        aComboLbl.isVisible = false

        aPanelBooster.reset()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addScoreLbl() {
        aScoreLbl.setSize(110f, 40f)
        add(aScoreLbl) { centerX(); topToTop() }
        aScoreLbl.setAlignment(Align.center)
    }

    private fun addGemLbl() {
        aGemLbl.setSize(45f, 18f)
        add(aGemLbl) { startToStart(); topToTop(margin = 12f) }
    }

    private fun addComboLbl() {
        aComboLbl.setSize(110f, 18f)
        add(aComboLbl) { centerX(aScoreLbl); topToBottom(aScoreLbl, margin = 12f) }
        aComboLbl.setAlignment(Align.center)
        aComboLbl.isVisible = false
    }

    /**
     * Ширину НЕ задаємо: панель HUG, вона рівно така, скільки зараз капсул.
     * Разом з endToEnd() ряд лишається притиснутим до правого краю і при
     * одному заряді, і при трьох.
     */
    private fun addPanelShield() {
        aPanelShield.setSize(64f, 10f)
        add(aPanelShield) { endToEnd(); topToTop(margin = 15f) }
    }

    /** Під капсулами щита, тим самим правим краєм: обидва — «що зараз на мені». */
    private fun addPanelBooster() {
        aPanelBooster.setSize(64f, 31f)
        add(aPanelBooster) { endToEnd(); topToBottom(aPanelShield, margin = 14f) }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGemLbl.setColorRGB(ThemeManager.current.gem)
        aComboLbl.setColorRGB(ThemeManager.current.player)
    }

}