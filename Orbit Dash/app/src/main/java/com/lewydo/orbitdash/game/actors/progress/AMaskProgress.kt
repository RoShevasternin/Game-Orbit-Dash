package com.lewydo.orbitdash.game.actors.progress

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.progress.MaskProgressEffect

// ─────────────────────────────────────────────────────────────────────────────
// AMaskProgress — прогрес будь-якої форми як готовий актор.
//
//   val bar = AMaskProgress(screen, mask = maskTex.region)
//   bar.fill = atlas.progress_fill        // або лишити колір
//   bar.setSize(200f, 12f)
//   ...
//   bar.frac = 0.4f                       // єдине, що рухається
//
// Маска — це drawable актора, тому вона ж задає форму: прямокутник (білий
// регіон за замовчуванням), капсула з VfxTexture(RoundRectEffect), фігура з
// msdf-атласу або PNG з дірками від дизайнера.
//
// Загортає пару «VfxImage + ефект» так само, як ARoundRect загортає
// RoundRectEffect: щоб на місці використання було видно смугу, а не механіку.
// Потрібен доступ до рідкісних полів (axis, reversed, slides) — вони на [fx].
// ─────────────────────────────────────────────────────────────────────────────
class AMaskProgress(
    override val screen: AdvancedScreen,
    mask: TextureRegion? = null,
) : VfxImage(screen) {

    /** Налаштування ефекту: напрямок, м'якість краю, кольори. */
    val fx = MaskProgressEffect()

    init {
        drawable = TextureRegionDrawable(mask ?: screen.drawerUtil.getRegion())
        effect   = fx
    }

    /** Форма. Її альфа керує всім: дірка лишається діркою. */
    var mask: TextureRegion
        get()      = (drawable as TextureRegionDrawable).region
        set(value) { drawable = TextureRegionDrawable(value) }

    /** Заповнення 0..1. */
    var frac: Float
        get()      = fx.frac
        set(value) { fx.frac = value }

    /** Картинка заповнення; null — колір [fillColor]. */
    var fill: TextureRegion?
        get()      = fx.fill
        set(value) { fx.fill = value }

    /** Картинка фону; null — колір [trackColor] з [trackAlpha]. */
    var track: TextureRegion?
        get()      = fx.track
        set(value) { fx.track = value }

    val fillColor  get() = fx.fillColor
    val trackColor get() = fx.trackColor

    /** Альфа заповнення. 1 — суцільне; менше — привид поверх фону. */
    var fillAlpha: Float
        get()      = fx.fillAlpha
        set(value) { fx.fillAlpha = value }

    var trackAlpha: Float
        get()      = fx.trackAlpha
        set(value) { fx.trackAlpha = value }
}
