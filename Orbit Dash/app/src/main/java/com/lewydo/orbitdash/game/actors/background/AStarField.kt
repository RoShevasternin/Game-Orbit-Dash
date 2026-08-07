package com.lewydo.orbitdash.game.actors.background

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.StarFieldEffect

// ═════════════════════════════════════════════════════════════════════════════
//  ГЛОБАЛЬНИЙ ГОДИННИК ДРЕЙФУ.
//
//  Живе поза екранами — саме це робить перехід LoaderScreen → MenuScreen
//  безшовним: нове поле продовжує рух старого, а не починає з нуля.
//  Разом із ShaderClock.time (теж глобальний) це означає, що новий актор
//  малює ПІКСЕЛЬ-У-ПІКСЕЛЬ те саме небо — свапу не видно взагалі.
//
//  Умови безшовності: однакові density/fill і однакові bounds актора
//  на обох екранах, і НЕ фейдити поле під час переходу.
// ═════════════════════════════════════════════════════════════════════════════
object StarFieldClock {

    var phase = 0f
        private set

    private var lastFrameId = -1L

    /** Крутиться один раз на кадр, скільки б полів не було на сцені. */
    fun update(delta: Float, speed: Float) {
        val frame = Gdx.graphics.frameId
        if (frame == lastFrameId) return
        lastFrameId = frame
        phase += delta * speed
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AStarField — зоряний фон.
//
//   val aStars = AStarField(screen)
//   add(aStars) { fillParent() }        // ПЕРШИМ, щоб було позаду всього
// ─────────────────────────────────────────────────────────────────────────────
class AStarField(override val screen: AdvancedScreen) : VfxImage(screen) {

    private val fx = StarFieldEffect()

    init {
        drawable = TextureRegionDrawable(screen.drawerUtil.getRegion())
        effect   = fx
        color.a  = BASE_ALPHA
    }

    companion object {
        const val BASE_ALPHA = 0.75f
    }

    // ── налаштування ─────────────────────────────────────────────────────────

    /** Скільки клітинок по висоті — масштаб «посіченості» неба. */
    var density: Float
        get() = fx.density
        set(value) { fx.density = value }

    /** Частка клітинок із зорею 0..1 — головний регулятор «скільки зір». */
    var fill: Float
        get() = fx.fill
        set(value) { fx.fill = value }

    /** Сила мерехтіння 0..1. */
    var twinkle: Float
        get() = fx.twinkle
        set(value) { fx.twinkle = value }

    /** Базова швидкість дрейфу. */
    var speed = 1f

    val colorA: Color get() = fx.colorA
    val colorB: Color get() = fx.colorB

    /** Кольорові зорі слідують за темою. */
    var syncWithTheme = true

    // ── імпульс від тапу ─────────────────────────────────────────────────────
    private val tmpVec = com.badlogic.gdx.math.Vector2()
    private var rippleT = 0f
    private var rippleActive = false

    /** Тривалість і сила імпульсу. */
    var rippleDuration = 0.9f
    var rippleStrength = 0.055f

    // ── warp-сплеск ──────────────────────────────────────────────────────────
    private var boostT    = 0f
    private var boostDur  = 0f
    private var boostPeak = 1f

    /**
     * Короткий «розгін» неба: зорі різко пришвидшуються і плавно гальмують,
     * заразом трохи яскравішають.
     *
     * Викликати в момент TAP TO START разом з морфом емблеми — тап перестає
     * бути просто зміною екрана і читається як РУХ: гравець ніби рушив уперед,
     * а не натиснув кнопку. Пік короткий (~15% тривалості), спад довгий —
     * так само поводиться будь-який реальний імпульс.
     */
    fun animWarp(peakSpeed: Float = 60f, duration: Float = 1.1f) {
        boostPeak = peakSpeed
        boostDur  = duration
        boostT    = 0f
    }

    /**
     * Імпульс від тапу: зорі розлітаються кільцевою хвилею і плавно
     * повертаються. На відміну від warp тут дрейф не чіпається — небо
     * лишається на місці, реагує лише локально.
     *
     * Координати — СТЕЙДЖНІ (з screenToStageCoordinates).
     */
    fun animRippleAt(stageX: Float, stageY: Float) {
        val p = tmpVec.set(stageX, stageY)
        stageToLocalCoordinates(p)
        if (width <= 0f || height <= 0f) return

        fx.ripplePosX = (p.x / width).coerceIn(0f, 1f)
        // localUV.y рахується згори вниз, а координати актора — знизу вгору
        fx.ripplePosY = 1f - (p.y / height).coerceIn(0f, 1f)

        rippleT = 0f
        rippleActive = true
    }

    /** Плавно згасити поле (напр. перед виходом у важкий ігровий екран). */
    fun animDim(target: Float = 0.35f, time: Float = 0.6f) {
        addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(target, time, Interpolation.sine))
    }

    override fun act(delta: Float) {
        super.act(delta)

        var currentSpeed = speed

        if (boostDur > 0f && boostT < boostDur) {
            boostT += delta
            val t = (boostT / boostDur).coerceIn(0f, 1f)
            // 0..0.15 — різкий підйом, далі довгий м'який спад
            val k = if (t < 0.15f) t / 0.15f
            else 1f - Interpolation.pow3Out.apply((t - 0.15f) / 0.85f)

            currentSpeed = speed + (boostPeak - speed) * k
            color.a = BASE_ALPHA * (1f + 0.4f * k)
            // Смуги замість крапок — головний носій відчуття швидкості
            fx.stretch = 1f + 5.5f * k

            if (t >= 1f) {
                boostDur = 0f
                color.a = BASE_ALPHA
                fx.stretch = 1f
            }
        }

        if (rippleActive) {
            rippleT += delta
            val t = (rippleT / rippleDuration).coerceIn(0f, 1f)
            // Фронт розходиться, сила згасає — повернення виходить саме собою
            fx.rippleRadius = t * 1.15f
            fx.rippleAmp = rippleStrength * (1f - t) * (1f - t)
            if (t >= 1f) {
                rippleActive = false
                fx.rippleAmp = 0f
            }
        }

        StarFieldClock.update(delta, currentSpeed)
        fx.driftPhase = StarFieldClock.phase

        if (syncWithTheme) fx.colorB.set(ThemeManager.current.player)
    }
}