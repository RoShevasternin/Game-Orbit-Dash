package com.lewydo.orbitdash.game.manager.util

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.manager.SpriteManager
import com.lewydo.orbitdash.game.utils.TextureEmpty
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import com.lewydo.orbitdash.game.utils.vfx.VfxTextures
import com.lewydo.orbitdash.game.utils.vfx.effects.base.BlurEffect
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect
import kotlin.math.roundToInt

class SpriteUtil {

    class Msdf {
        private fun getRegion(name: String): TextureRegion = SpriteManager.EnumAtlas.MSDF.region(name)

        /** Те саме число, що PXRANGE у assets/msdf/gen-msdf.command. Один на атлас. */
        val PX_RANGE = 8f

        /** Сторінка атласу. Одна: кілька іконок у 1024² вміщаються з запасом. */
        val texture: Texture = SpriteManager.EnumAtlas.MSDF.data.atlas.textures.first().apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }

        /** Спільний ефект на всі фігури цього атласу — юніформи в них однакові. */
        val effect = MsdfShapeEffect(texture, PX_RANGE)

        // ── ВЕКТОР: регіони, назва = ім'я SVG без розширення ────────────────
        //    Малювати AMsdfImage(screen, msdf.star).
        //    Звичайний Image(msdf.star) дасть кашу: msdf-шейдера в нього немає.
        val circle_msdf = getRegion("circle")
        val gem_msdf    = getRegion("gem")


        val circleTex = VfxTexture(40f, 40f, circle_msdf, effect)
        val circle    = circleTex.region

        val gemTex = VfxTexture(40f, 40f, gem_msdf, effect)
        val gem    = gemTex.region

        // Світіння під об'єкти — один в один шар із Figma: коло 100×100, Layer Blur 68
        // (еталон textures/loader/TEST_CIRCLE.png). density і bleed рахуються самі:
        // outer = 236 = фрейм «hug contents» у Figma. Два способи малювати:
        //   Image(glow)         — уся пляма 236 в актора; коло — 100/236 = 42 % по центру
        //   glowTex.image()     — межі актора = коло, світіння виходить назовні (модель Figma)
        //   img.setSize(
        //          gdxGame.assetsMsdf.glowTex.outerWidth,
        //          gdxGame.assetsMsdf.glowTex.outerHeight
        //   ) — уся пляма 236 в актора;

        val glowTex = VfxTexture(40f, 40f, circle_msdf, effect, listOf(BlurEffect(blur = 22f)))
        val glow    = glowTex.region
    }

    class Brand {
        private fun region(name: String): TextureRegion = SpriteManager.EnumAtlas.BRAND.region(name)

        val brand_back  = region("brand_back")
        val brand_front = region("brand_front")
        val brand_line  = region("brand_line")
        val lewydo      = region("lewydo")
        val slogan      = region("slogan")
    }

    class Loader {
        private fun region(name: String): TextureRegion = SpriteManager.EnumAtlas.LOADER.region(name)

        //val gem = region("gem")

        val ORBIT_GLOW = SpriteManager.EnumTexture.ORBIT_GLOW.data.texture
    }

    class All {
        private fun region(name: String): TextureRegion = SpriteManager.EnumAtlas.ALL.region(name)
        private fun patch(name: String): NinePatch = SpriteManager.EnumAtlas._9_PATCH.ninePatch(name)

        // ------------------------------------------------------------------------------
        // ATLAS ALL
        // ------------------------------------------------------------------------------

        val badge_glow = region("badge_glow")
        val shield_pip = region("shield_pip")
        val boost_hex  = region("boost_hex")
        val spike      = region("spike")

        val icon_gem_x2   = region("icon_gem_x2")
        val icon_magnet   = region("icon_magnet")
        val icon_pulse    = region("icon_pulse")
        val icon_shield   = region("icon_shield")
        val icon_slow_mo  = region("icon_slow_mo")

        //val listGlarePanelGame = List(4) { getAllRegion("glare_panel_game_${it.inc()}") }

        // ------------------------------------------------------------------------------
        // ATLAS 9_PATCH
        // ------------------------------------------------------------------------------

        val panel_coin = patch("panel_coin")

        // ------------------------------------------------------------------------------
        // TEXTURES
        // ------------------------------------------------------------------------------

        // TEST
        //val bg_test    = SpriteManager.EnumTexture.bg_test.data.texture

        // ALL
        val LIGHT    = TextureEmpty //SpriteManager.EnumTexture.LIGHT.data.texture

        // All | panel
        //val STAR = SpriteManager.EnumTexture.star.data.texture

        // All | dialog
        val DIALOG_CLEAR_GRID = LIGHT
    }

}

/** Спільний геттер регіона: сам знає шлях атласу, тож помилка каже, ЩО перепакувати. */
private fun SpriteManager.EnumAtlas.region(name: String): TextureRegion = data.atlas.findRegion(name) ?: error("Регіон '$name' відсутній в ${data.path} — перепакуй атлас")
private fun SpriteManager.EnumAtlas.ninePatch(name: String): NinePatch = data.atlas.createPatch(name) ?: error("NinePatch '$name' відсутній в ${data.path} — перепакуй атлас")