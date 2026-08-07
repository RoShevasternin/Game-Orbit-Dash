package com.lewydo.orbitdash.game.actors.button.base

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.actors.button.base.AButtonTexture.Style
import com.lewydo.orbitdash.game.utils.TextureEmpty
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.region

object AButtonStyles {

    // ------------------------------------------------------------------------
    // AButtonTexture.Style
    // ------------------------------------------------------------------------

    object Texture {
        val NONE get() = Style(default = TextureRegionDrawable(TextureEmpty.region))

//        val SETTINGS
//            get() = Style(
//                default = TextureRegionDrawable(gdxGame.assetsAll.settings_def),
//                pressed = TextureRegionDrawable(gdxGame.assetsAll.settings_press),
//                disabled = TextureRegionDrawable(gdxGame.assetsAll.settings_press),
//            )
    }

    // ------------------------------------------------------------------------
    // AButtonAnim.Style
    // ------------------------------------------------------------------------

    object Anim {
        val NONE get() = AButtonAnim.Style(TextureRegionDrawable(TextureEmpty.region))
    }

    // All ------------------------------------------------------------------------
    //val DAILY_CONVERTER_ITEM           get() = AButtonAnim.Style(TextureRegionDrawable(gdxGame.assetsAll.daily_converter_item))
    //val DAILY_FREE_RBX_CALCULATOR_ITEM get() = AButtonAnim.Style(TextureRegionDrawable(gdxGame.assetsAll.daily_free_rbx_calculator_item))

    // ------------------------------------------------------------------------
    // AButtonAnimTexture.Style
    // ------------------------------------------------------------------------
    object AnimTexture {
        val NONE get() = AButtonAnimTexture.Style(TextureRegionDrawable(TextureEmpty.region))

//        val GOLDEN get() = AButtonAnimTexture.Style(
//            default  = TextureRegionDrawable(gdxGame.assetsAll.golden_def),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.golden_dis),
//        )
    }

}