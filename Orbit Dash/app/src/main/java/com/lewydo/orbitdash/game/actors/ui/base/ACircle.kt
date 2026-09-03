package com.lewydo.orbitdash.game.actors.ui.base

import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.CircleEffect

// ─────────────────────────────────────────────────────────────────────────────
// ACircle — коло як актор. Заміна текстурі там, де потрібен просто круг:
// розмір довільний, край завжди чистий, мипмапи не потрібні.
//
//   val dot = ACircle(screen).apply { setSize(20f, 20f) }
//   dot.color = GameColor.blue_00E5FF          // ← колір через тінт, як в Image
//
//   // кільце (порожній центр)
//   val ring = ACircle(screen).apply { fillAlpha = 0f; strokeWidth = 3f }
//
//   // розмите коло як у Figma: діаметр 150, Layer Blur 40
//   val glow = ACircle(screen).apply { setCircle(diameter = 150f, blur = 40f) }
//
//   // або навпаки: розмір актора фіксований, коло стискається під розмиття
//   val soft = ACircle(screen).apply { setSize(150f, 150f); blur = 40f }
//
// Прозорість цілого актора — через color.a, як завжди.
// ─────────────────────────────────────────────────────────────────────────────
class ACircle(override val screen: AdvancedScreen) : VfxImage(screen) {

    private val fx = CircleEffect()

    init {
        drawable = TextureRegionDrawable(screen.drawerUtil.getRegion())
        effect   = fx
    }

    /**
     * Коло діаметром diameter із розмиттям blur — один в один як у Figma.
     *
     * Актор навмисно БІЛЬШИЙ за коло на 2×blur: намалювати за межі свого
     * прямокутника актор не може в принципі, а зовнішній половині розмиття
     * потрібне місце. Саме коло лишається того діаметра, який ти задав, і
     * стоїть у центрі — тож при center() позиція збігається з макетом.
     *
     * blur = 0 → звичайне різке коло рівно по розміру актора.
     */
    fun setCircle(diameter: Float, blur: Float = 0f) {
        setSize(diameter + 2f * blur, diameter + 2f * blur)
        fx.radius = if (blur > 0f) diameter * 0.5f else 0f
        fx.blur   = blur
    }

    /**
     * Половина ширини розмиття у кожен бік; дорівнює значенню Layer Blur із Figma.
     *
     * Якщо radius не заданий (0), коло само вписується в актора З УРАХУВАННЯМ
     * розмиття: розмір актора лишається твій, а видиме коло меншає на 2×blur.
     * Потрібно навпаки — щоб коло було заданого діаметра, а ріс актор — бери
     * setCircle().
     */
    var blur: Float
        get() = fx.blur
        set(value) { fx.blur = value }

    /** Радіус кола. 0 — вписати в актора. Зазвичай виставляється через setCircle(). */
    var radius: Float
        get() = fx.radius
        set(value) { fx.radius = value }

    /** 0 = прозорий центр (лишиться тільки обводка). */
    var fillAlpha: Float
        get() = fx.fillAlpha
        set(value) { fx.fillAlpha = value }

    /** 0 = без обводки. У world-юнітах, іде всередину від межі кола. */
    var strokeWidth: Float
        get() = fx.strokeWidth
        set(value) { fx.strokeWidth = value }

    var strokeAlpha: Float
        get() = fx.strokeAlpha
        set(value) { fx.strokeAlpha = value }
}