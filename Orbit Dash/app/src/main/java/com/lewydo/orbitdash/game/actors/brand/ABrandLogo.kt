package com.lewydo.orbitdash.game.actors.brand

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

class ABrandLogo(override val screen: AdvancedScreen): AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 208f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aBrandBackImg  = Image(gdxGame.assetsBrand.brand_back)
    private val aBrandFrontImg = Image(gdxGame.assetsBrand.brand_front)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addBrandBackImg()
        addBrandFrontImg()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------

    private fun addBrandBackImg() {
        add(aBrandBackImg) { fillParent() }
    }

    private fun addBrandFrontImg() {
        aBrandFrontImg.setSizeScaled(140f, 140f)
        add(aBrandFrontImg) { center() }
    }

}