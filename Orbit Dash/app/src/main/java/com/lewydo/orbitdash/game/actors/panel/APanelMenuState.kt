package com.lewydo.orbitdash.game.actors.panel

import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

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
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addLbls()
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
    fun setStats(best: Long, gems: Long, day: Int) {
        aBestLbl.setText("BEST $best")
        aGemsLbl.setText("◆ $gems")
        aDayLbl.setText("DAY $day")
    }

}