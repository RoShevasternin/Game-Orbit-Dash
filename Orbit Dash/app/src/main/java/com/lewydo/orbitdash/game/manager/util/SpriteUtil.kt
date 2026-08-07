package com.lewydo.orbitdash.game.manager.util

import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.manager.SpriteManager
import com.lewydo.orbitdash.game.utils.TextureEmpty

class SpriteUtil {

    class Brand {
        private fun getRegion(name: String): TextureRegion = SpriteManager.EnumAtlas.BRAND.data.atlas.findRegion(name)

        val brand_back  = getRegion("brand_back")
        val brand_front = getRegion("brand_front")
        val brand_line  = getRegion("brand_line")
        val lewydo      = getRegion("lewydo")
        val slogan      = getRegion("slogan")
    }

    class Loader {
        private fun getRegion(name: String): TextureRegion = SpriteManager.EnumAtlas.LOADER.data.atlas.findRegion(name)

        val item_glow = getRegion("item_glow")
        val ball      = getRegion("ball")
        val gem       = getRegion("gem")

        val ORBIT_GLOW = SpriteManager.EnumTexture.ORBIT_GLOW.data.texture
    }

    class All {
        private fun getAllRegion(name: String): TextureRegion = SpriteManager.EnumAtlas.ALL.data.atlas.findRegion(name) ?: error("Регіон '$name' відсутній в atlas/all.atlas — перепакуй атлас")

        private fun get9Patch(name: String): NinePatch = SpriteManager.EnumAtlas._9_PATCH.data.atlas.createPatch(name) ?: error("Регіон '$name' відсутній в atlas/_9_patch.atlas — перепакуй атлас")

        // ------------------------------------------------------------------------------
        // ATLAS ALL
        // ------------------------------------------------------------------------------

        val badge_dot  = getAllRegion("badge_dot")
        val badge_glow = getAllRegion("badge_glow")

        //val listGlarePanelGame = List(4) { getAllRegion("glare_panel_game_${it.inc()}") }

        // ------------------------------------------------------------------------------
        // ATLAS 9_PATCH
        // ------------------------------------------------------------------------------

        val panel_coin = get9Patch("panel_coin")

        // ------------------------------------------------------------------------------
        // TEXTURES
        // ------------------------------------------------------------------------------

        // TEST
        //val bg_test    = SpriteManager.EnumTexture.bg_test.data.texture

        // ALL
        val LIGHT    = TextureEmpty //SpriteManager.EnumTexture.LIGHT.data.texture

        // All | panel
        val PANEL_TOP = LIGHT

        // All | dialog
        val DIALOG_CLEAR_GRID = LIGHT
    }

}