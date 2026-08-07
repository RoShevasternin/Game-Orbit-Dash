package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.button.base.AButtonStyles
import com.lewydo.orbitdash.game.actors.button.base.AButtonTexture
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.animHide
import com.lewydo.orbitdash.game.utils.actor.animShow
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame

class LeaderboardScreen: AdvancedScreen() {

    private val textTitle = "LEADERBOARD"

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf by lazy { gdxGame.msdfManager }

    private val styleTitle = MsdfStyle(msdf, msdf.fontInter_ExtraBold, 160f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------

    private val aBackBtn      by lazy { AButtonTexture(this, AButtonStyles.Texture.NONE) }
    private val aTitleLbl     by lazy { AMsdfLabel(textTitle, styleTitle) }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        rootConstraintLayout.color.a = 0f
        //setBackground(gdxGame.assetsLoader.BACKGROUND)
        super.show()
        animShowScreen()

        // відправляємо актуальний XP і одразу відкриваємо стандартний UI Google
        gdxGame.activity.showLeaderboard()

        stageUI.root.animDelay(0.07f) { gdxGame.navigationManager.back() }
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        addBackBtn()
        addTitleLbl()
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------
    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.animHide(TIME_ANIM_SCREEN) { blockEnd() }
    }

    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.animShow(TIME_ANIM_SCREEN) { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------

    private fun AConstraintLayout.addBackBtn() {
        aBackBtn.setSize(236f, 236f)
        add(aBackBtn) { startToStart(margin = 128f); topToTop(margin = 78f) }
        aBackBtn.setOnClickListener { animHideScreen { gdxGame.navigationManager.back() } }
    }

    private fun AConstraintLayout.addTitleLbl() {
        aTitleLbl.setSize(1876f, 218f)
        add(aTitleLbl) { centerX(); topToBottom(aBackBtn) }
        aTitleLbl.setAlignment(Align.center)
    }

}