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
// Радіус не задається — береться найбільший, що влазить у setSize(). Якщо
// кільцю треба місце назовні (glow, пунктир активної орбіти) — outerPad.
//
// АКТИВНА ОРБІТА — числа з Figma (ring-outer-active), у design-юнітах поля.
// setActive(player, k) переводить їх у world через k = design→world групи.
// ─────────────────────────────────────────────────────────────────────────────
class AOrbitRing(override val screen: AdvancedScreen) : VfxImage(screen) {

    companion object {
        // Figma ring-outer-active, design-юніти поля (320 = зовнішня орбіта)
        private const val GLOW1_W = 10f;  private const val GLOW1_A = 0.14f
        private const val GLOW2_W = 14f;  private const val GLOW2_A = 0.08f
        private const val DASH_R_OFFSET = 9f      // пунктир на 9 назовні від осі орбіти
        private const val DASH_W   = 2f;  private const val DASH_A = 0.5f
        private const val DASH_LEN = 8f;  private const val DASH_GAP = 15f

        /** Скільки місця назовні від осі орбіти займає активний стиль: пунктир + його товщина + AA. */
        const val ACTIVE_REACH = DASH_R_OFFSET + DASH_W + 2f
    }

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

    /** Відступ осі кільця від краю квада (world). null — найбільше коло, що влазить. */
    var outerPad: Float?
        get() = fx.outerPad
        set(value) { fx.outerPad = value }

    /** Зсунути пунктир на [shift] world-юнітів дуги. Росте — проти годинникової. Фаза сама згортається в 0..1. */
    fun advanceDash(shift: Float) = fx.advanceDash(shift, width, height)

    /** Радіус, який вийшов при поточному розмірі — якщо треба щось на нього посадити. */
    val currentRadius: Float
        get() = fx.radiusFor(width, height)

    /** Увімкнути стиль активної орбіти. [player] — колір м'яча, [k] — design→world групи. */
    fun setActive(player: Color, k: Float) {
        fx.active = true
        fx.playerColor.set(player)
        fx.glow1Width = GLOW1_W * k;  fx.glow1Alpha = GLOW1_A
        fx.glow2Width = GLOW2_W * k;  fx.glow2Alpha = GLOW2_A
        fx.dashOffsetR = DASH_R_OFFSET * k
        fx.dashWidth = DASH_W * k;    fx.dashAlpha = DASH_A
        fx.dashLen = DASH_LEN * k;    fx.dashGap = DASH_GAP * k
    }

    fun setInactive() { fx.active = false }
}