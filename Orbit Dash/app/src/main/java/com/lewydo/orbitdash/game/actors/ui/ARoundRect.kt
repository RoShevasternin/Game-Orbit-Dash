package com.lewydo.orbitdash.game.actors.ui

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.RoundRectEffect

// ─────────────────────────────────────────────────────────────────────────────
// ARoundRect — заокруглений прямокутник як актор.
//
//   val bg = ARoundRect(screen).apply { radius = 16f }
//   bg.color = GameColor.blue_00E5FF     // ← колір через тінт
//
// Заливка й обводка незалежні: можна зробити або plain, або тільки рамку.
// ─────────────────────────────────────────────────────────────────────────────
class ARoundRect(override val screen: AdvancedScreen) : VfxImage(screen) {

    private val fx = RoundRectEffect()

    init {
        drawable = TextureRegionDrawable(screen.drawerUtil.getRegion())
        effect   = fx
    }

    var radius: Float
        get() = fx.radius
        set(value) { fx.radius = value }

    /** 0 = прозорий центр (лишиться тільки обводка). */
    var fillAlpha: Float
        get() = fx.fillAlpha
        set(value) { fx.fillAlpha = value }

    /** 0 = без обводки. */
    var strokeWidth: Float
        get() = fx.strokeWidth
        set(value) { fx.strokeWidth = value }

    var strokeAlpha: Float
        get() = fx.strokeAlpha
        set(value) { fx.strokeAlpha = value }
}