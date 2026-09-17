package com.lewydo.orbitdash.game.actors.panel

import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync

class APanelMenuState(override val screen: AdvancedScreen): AAutoLayout(
    screen    = screen,
    direction = Direction.HORIZONTAL,
    gapMain   = 18f,
    sizingW   = Sizing.HUG
) {

    private val themeColors = ThemeManager.current

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf by lazy { gdxGame.msdfManager }

    private val styleBest = MsdfStyle(msdf, msdf.fontInter_Medium, 13f, GameColor.white_65).apply { letterSpacing = 15f }
    private val styleGems = styleBest.copy(color = themeColors.gem)
    private val styleDay  = styleBest.copy(color = GameColor.white_45)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aBestLbl = AMsdfLabel("BEST 0", styleBest)
    private val aGemsLbl = AMsdfLabel("◆ 0", styleGems)
    private val aDayLbl  = AMsdfLabel("DAY 1", styleDay)

    private val listLbl = listOf(aBestLbl, aGemsLbl, aDayLbl)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    // Кеш: setText перебудовує розкладку MSDF — лише коли число змінилось
    private var lastBest = -1
    private var lastGems = -1

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addLbls()
        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addLbls() {
        listLbl.forEach { lbl ->
            lbl.autoSize = true
            add(lbl)
            lbl.pack()

            //lbl.debug()
        }
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------
    /** Рекорд і баланс гемів — з PlayerModel. */
    fun setStats(best: Int, gems: Int) {
        if (best != lastBest) { lastBest = best; aBestLbl.setText("BEST $best") }
        if (gems != lastGems) { lastGems = gems; aGemsLbl.setText("◆ $gems") }
    }

    /** День стріку. Поки стріку немає — лишається «DAY 1» з конструктора. */
    fun setDay(day: Int) {
        aDayLbl.setText("DAY $day")
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGemsLbl.setTextColor(ThemeManager.current.gem)
    }

}