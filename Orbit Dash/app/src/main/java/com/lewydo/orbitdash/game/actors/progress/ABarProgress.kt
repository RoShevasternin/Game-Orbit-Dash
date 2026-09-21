package com.lewydo.orbitdash.game.actors.progress

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.progress.BarProgressEffect

// ─────────────────────────────────────────────────────────────────────────────
// ABarProgress — смуга прогресу як готовий актор.
//
//   val bar = ABarProgress(screen).apply {
//       radius     = 1.5f                 // половина висоти = капсула
//       trackAlpha = 0.2f
//       fillColor.set(boost.info.color)
//   }
//   bar.setSize(64f, 3f)
//   ...
//   bar.frac = engine.boostFrac           // єдине, що рухається
//
// Форму рахує шейдер, тому текстура не потрібна: 0 ассетів, 1 draw, ідеальний
// край на будь-якому розмірі. Картинка замість кольору — один рядок:
//
//   bar.picture = atlas.progress_fill     // заповнення стає картинкою
//
// Пара з AMaskProgress: тут форма МАТЕМАТИЧНА (капсула), там — КАРТИНКОЮ
// (будь-яка, хоч із дірками). Вибір і решта випадків — docs/progress.md.
//
// Загортає «VfxImage + ефект» так само, як ARoundRect загортає RoundRectEffect.
// Живе поруч із AMaskProgress: одна тека actors/progress на всі прогреси.
// Рідкісні поля (textureSlides, texturedTrackAlpha, aaWidth) — на [fx].
// ─────────────────────────────────────────────────────────────────────────────
class ABarProgress(override val screen: AdvancedScreen) : VfxImage(screen) {

    /** Налаштування ефекту: м'якість краю, режими картинки. */
    val fx = BarProgressEffect()

    init {
        drawable = TextureRegionDrawable(screen.drawerUtil.getRegion())
        effect   = fx
    }

    /** Заповнення 0..1. */
    var frac: Float
        get()      = fx.frac
        set(value) { fx.frac = value }

    /** Радіус кінців у world-юнітах. Половина висоти = капсула. */
    var radius: Float
        get()      = fx.radius
        set(value) { fx.radius = value }

    /** Альфа заповнення. 1 — суцільне; менше — смуга-привид. */
    var fillAlpha: Float
        get()      = fx.fillAlpha
        set(value) { fx.fillAlpha = value }

    /** Альфа доріжки. 0 — доріжки немає. */
    var trackAlpha: Float
        get()      = fx.trackAlpha
        set(value) { fx.trackAlpha = value }

    val fillColor  get() = fx.fillColor
    val trackColor get() = fx.trackColor

    /**
     * Картинка заповнення замість кольору. null — назад до [fillColor].
     *
     * Вмикає текстурний режим сама: картинка натягується на всю смугу, і край
     * заповнення її ПРОЯВЛЯЄ. Хочеш, щоб вона їхала за краєм, —
     * `fx.textureSlides = true`.
     */
    var picture: TextureRegion?
        get() = if (fx.textured) (drawable as? TextureRegionDrawable)?.region else null
        set(value) {
            drawable    = TextureRegionDrawable(value ?: screen.drawerUtil.getRegion())
            fx.textured = value != null
        }
}
