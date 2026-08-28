package com.lewydo.orbitdash.game.actors.popup

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.utils.NumberFormatter
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.runGDX
import kotlinx.coroutines.launch

class ALevelPopup(override val screen: AdvancedScreen) : AdvancedGroup() {

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf by lazy { gdxGame.msdfManager }

    private val styleDef = MsdfStyle(msdf, msdf.fontInter_ExtraBold, 72f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------

    private val aPopupImg = Image(gdxGame.assetsAll.DIALOG_CLEAR_GRID)
    private val aXPLbl    = AMsdfLabel("XP: 0/0", styleDef)
    //private val aProgress = AProgressPopupXP(screen)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    override fun addActorsOnGroup() {
        addPopupImg()
        addXPLbl()
        addProgress()
        collectXp()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------

    private fun addPopupImg() {
        addAndFillActor(aPopupImg)
    }

    private fun addXPLbl() {
        addActor(aXPLbl)
        aXPLbl.setBounds(82f, 127f, 533f, 98f)
    }

    private fun addProgress() {
        //addActor(aProgress)
        //aProgress.setBounds(82f, 67f, 533f, 44f)
    }

    // ------------------------------------------------------------------------
    // Collect
    // ------------------------------------------------------------------------

    private fun collectXp() {
//        coroutine?.launch {
//            gdxGame.modelPlayer.xpFlow.collect {
//                runGDX { updateXpUI() }
//            }
//        }
    }

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------

    private fun updateXpUI() {
        val model      = gdxGame.modelPlayer



        adjustPopupWidth()
    }

    private fun adjustPopupWidth() {
        if (aXPLbl.width < 533f) return

        val newWidth = 82f + aXPLbl.width + 82f
        //aPopupImg.setSize(newWidth, aPopupImg.height)
    }
}