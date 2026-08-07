package com.lewydo.orbitdash.game.actors.orbit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.OrbitRingEffect

// ─────────────────────────────────────────────────────────────────────────────
// AOrbitRing — порожнє коло, вписане в межі групи.
//
//   val aRing = AOrbitRing(screen).apply {
//       ringColor.set(GameColor.blue_00E5FF)
//       thickness = 4f
//   }
//   add(aRing) { size(150f, 150f); center() }
//
// Радіус не задається — береться найбільший, що влазить у setSize().
// ─────────────────────────────────────────────────────────────────────────────
class AOrbitRing(override val screen: AdvancedScreen) : VfxImage(screen) {

    private val fx = OrbitRingEffect()

    init {
        // Біла заглушка — колір і форму дає шейдер
        drawable = TextureRegionDrawable(screen.drawerUtil.getRegion())
        effect   = fx
    }

    /** Колір кільця. Присвоєння копіює значення, зовнішній об'єкт не захоплюється. */
    var ringColor: Color
        get() = fx.color
        set(value) { fx.color.set(value) }

    /** Товщина обводки у world-юнітах. */
    var thickness: Float
        get() = fx.thickness
        set(value) { fx.thickness = value }

    /** Радіус, який вийшов при поточному розмірі — якщо треба щось на нього посадити. */
    val currentRadius: Float
        get() = (minOf(width, height) - thickness) * 0.5f
}