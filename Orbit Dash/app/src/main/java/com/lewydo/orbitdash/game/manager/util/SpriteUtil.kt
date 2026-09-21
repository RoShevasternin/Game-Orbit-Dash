package com.lewydo.orbitdash.game.manager.util

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.manager.SpriteManager
import com.lewydo.orbitdash.game.utils.TextureEmpty
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import com.lewydo.orbitdash.game.utils.vfx.effects.base.RoundRectEffect
import com.lewydo.orbitdash.game.utils.vfx.effects.base.BlurEffect
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect

class SpriteUtil {

    class Msdf {
        private fun region(name: String): TextureRegion = SpriteManager.EnumAtlas.MSDF.region(name)

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
        val circle_msdf        = region("circle")
        val gem_msdf           = region("gem")
        val spike_msdf         = region("spike")
        val boost_hex_msdf     = region("boost_hex")

        val circle_tex = VfxTexture(40f, 40f, circle_msdf, effect)
        val circle     = circle_tex.region

        val gem_tex = VfxTexture(40f, 40f, gem_msdf, effect)
        val gem     = gem_tex.region

        val glow_tex = VfxTexture(40f, 40f, circle_msdf, effect, listOf(BlurEffect(blur = 22f)))
        val glow     = glow_tex.region

        val glow_orbit_tex = VfxTexture(420f, 420f, circle_msdf, effect, listOf(BlurEffect(blur = 120f)))
        val glow_orbit     = glow_orbit_tex.region

        val spike_tex = VfxTexture(40f, 40f, spike_msdf, effect)
        val spike     = spike_tex.region

        val boost_hex_tex = VfxTexture(35f, 40f, boost_hex_msdf, effect)
        val boost_hex     = boost_hex_tex.region
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
        //private fun region(name: String): TextureRegion = SpriteManager.EnumAtlas.LOADER.region(name)
        //private val assetsMsdf = gdxGame.assetsMsdf
    }

    class All {
        private fun region(name: String): TextureRegion = SpriteManager.EnumAtlas.ALL.region(name)
        private fun patch(name: String): NinePatch = SpriteManager.EnumAtlas._9_PATCH.ninePatch(name)

        // ------------------------------------------------------------------------------
        // ATLAS ALL
        // ------------------------------------------------------------------------------

        val badge_glow_tex = VfxTexture(14f, 14f, shape = RoundRectEffect().apply {
            radius      = 14f / 2f
            fillAlpha   = 0.50f
            strokeAlpha = 0.85f
            strokeWidth = 1f
        })
        val badge_glow = badge_glow_tex.region

        val shield_pip_tex = VfxTexture(40f, 25f, shape = RoundRectEffect().apply { radius = 8f })
        val shield_pip     = shield_pip_tex.region

        val boost_icon_frenzy   = region("boost_icon_frenzy")
        val boost_icon_magnet   = region("boost_icon_magnet")
        val boost_icon_pulse    = region("boost_icon_pulse")
        val boost_icon_shield   = region("boost_icon_shield")
        val boost_icon_slow_mo  = region("boost_icon_slow_mo")

        //val listGlarePanelGame = List(4) { getAllRegion("glare_panel_game_${it.inc()}") }

        // ------------------------------------------------------------------------------
        // ATLAS 9_PATCH
        // ------------------------------------------------------------------------------

        //val panel_coin = patch("panel_coin")

        // ------------------------------------------------------------------------------
        // TEXTURES
        // ------------------------------------------------------------------------------

        // TEST
        //val bg_test    = SpriteManager.EnumTexture.bg_test.data.texture

        // ALL
        val test_progress = SpriteManager.EnumTexture.test_progress.data.texture

        // All | panel
        //val STAR = SpriteManager.EnumTexture.star.data.texture

        // All | dialog
        val DIALOG_CLEAR_GRID = test_progress
    }

}

/** Спільний геттер регіона: сам знає шлях атласу, тож помилка каже, ЩО перепакувати. */
private fun SpriteManager.EnumAtlas.region(name: String): TextureRegion = data.atlas.findRegion(name) ?: error("Регіон '$name' відсутній в ${data.path} — перепакуй атлас")
private fun SpriteManager.EnumAtlas.ninePatch(name: String): NinePatch = data.atlas.createPatch(name) ?: error("NinePatch '$name' відсутній в ${data.path} — перепакуй атлас")