package com.lewydo.orbitdash.game.screens

import com.lewydo.orbitdash.game.actors.brand.ABrandGroup
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.manager.SpriteManager
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.animHide
import com.lewydo.orbitdash.game.utils.actor.animShow
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

class BrandScreen : AdvancedScreen() {

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aBrandGroup by lazy { ABrandGroup(this) }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        loadBandAssets()
        super.show()

        animShowScreen {
            aBrandGroup.playIntroAnimation {
                animHideScreen { gdxGame.navigationManager.navigate(LoaderScreen::class.java.name) }
            }
        }
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        aBrandGroup.setSize(208f, 322f)
        add(aBrandGroup) { center() }
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------
    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.animHide(TIME_ANIM_SCREEN) { blockEnd() }
    }

    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.animShow { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------
    private fun loadBandAssets() {
        with(gdxGame.spriteManager) {
            loadableAtlasList = mutableListOf(SpriteManager.EnumAtlas.BRAND.data)
            loadAtlas()
//            loadableTexturesList = mutableListOf(SpriteManager.EnumTexture.BRAND_BACKGROUND.data)
//            loadTexture()
        }
        gdxGame.assetManager.finishLoading()
        gdxGame.spriteManager.initAll()
    }


}