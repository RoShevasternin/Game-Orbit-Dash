package com.lewydo.orbitdash.game.actors.panel

import com.badlogic.gdx.graphics.Color
import com.lewydo.orbitdash.game.actors.button.ADefButton
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class APanelGameHud(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val theme = ThemeManager.current

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf = gdxGame.msdfManager

    private val styleBold = MsdfStyle(msdf, msdf.fontInter_Bold, 1f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aScoreLbl = AMsdfLabel("", styleBold, 34f, Color.WHITE)
    private val aGemLbl   = AMsdfLabel("", styleBold, 14f, theme.gem)
    private val aComboLbl = AMsdfLabel("", styleBold, 14f, theme.player)


    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addScoreLbl()
        addGemLbl()
        addComboLbl()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addScoreLbl() {
        aScoreLbl.setSize(110f, 40f)
        add(aScoreLbl) { centerX(); topToTop() }
    }

    private fun addGemLbl() {
        aGemLbl.setSize(45f, 18f)
        add(aGemLbl) { startToStart(); topToTop(margin = 12f) }
    }

    private fun addComboLbl() {
        aComboLbl.setSize(110f, 18f)
        add(aComboLbl) { centerX(aScoreLbl); topToBottom(aScoreLbl, margin = 12f) }
    }

}
