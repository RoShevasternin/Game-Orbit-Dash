package com.lewydo.orbitdash.game.actors.progress

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.progress.RingProgressEffect

// ─────────────────────────────────────────────────────────────────────────────
// ARingProgress — кільце прогресу як готовий актор.
//
//   val ring = ARingProgress(screen).apply {
//       trackAlpha = 0.25f
//       thickness  = 4f
//   }
//   ring.setSize(RING_QUAD, RING_QUAD)    // КВАД, не діаметр кільця
//   ...
//   ring.frac = engine.comboFrac          // єдине, що рухається
//
// Заповнення йде від 12-ї години за годинниковою; frac = 1 — суцільне кільце
// (так зроблено кільце щита й хвиля спалаху).
//
// МЕЖІ АКТОРА — КВАД, А НЕ КІЛЬЦЕ. Шейдер малює всередині квада, тому той
// мусить умістити радіус + півтовщини + AA: RING_QUAD у ABall, QUAD у AWave.
// Це та сама модель «межі = фігура», лише фігура тут — саме квад із кільцем
// усередині, а радіус задається окремо в world-юнітах.
//
// Третій у родині: ABarProgress — форма МАТЕМАТИЧНА по прямій, AMaskProgress —
// КАРТИНКОЮ, тут — МАТЕМАТИЧНА по колу. Вибір — docs/progress.md.
//
// open, бо AWave успадковує актора, а не тримає ефект збоку: хвиля — те саме
// кільце з frac = 1, у якого радіус і товщина їдуть у часі.
// ─────────────────────────────────────────────────────────────────────────────
open class ARingProgress(override val screen: AdvancedScreen) : VfxImage(screen) {

    /** Налаштування ефекту. Усе часте прокинуте полями нижче. */
    val fx = RingProgressEffect()

    init {
        drawable = TextureRegionDrawable(screen.drawerUtil.getRegion())
        effect   = fx
    }

    /** Заповнення 0..1 від 12-ї години за годинниковою. */
    var frac: Float
        get()      = fx.frac
        set(value) { fx.frac = value }

    /** Радіус осі кільця у world-юнітах; null — найбільший, що влазить у квад. */
    var radius: Float?
        get()      = fx.radius
        set(value) { fx.radius = value }

    /** Товщина кільця у world-юнітах. */
    var thickness: Float
        get()      = fx.thickness
        set(value) { fx.thickness = value }

    /** Альфа незаповненої частини. 0 — доріжки немає. */
    var trackAlpha: Float
        get()      = fx.trackAlpha
        set(value) { fx.trackAlpha = value }

    /**
     * Колір кільця в шейдері. Зазвичай не потрібен: тінт самого актора
     * (setColorRGB) фарбує і кільце, і доріжку разом — саме так робить ABall.
     */
    val ringColor get() = fx.color
}
