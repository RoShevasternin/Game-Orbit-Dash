package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.ProgressRingEffect

// ─────────────────────────────────────────────────────────────────────────────
//  ABall — м'яч гравця з усіма його станами: один актор, як один компонент у
//  Figma (BALL + COMBO / ball + shield + combo). Шари знизу вгору, як у макеті:
//  ореол → кільце щита → тіло → цятка напряму → супутники комбо → кільце комбо.
//
//  База скейлера 40 = діаметр м'яча; усі числа дітей — з Figma в цій базі.
//  Кільця й ореол більші за фрейм і живуть назовні — модель «межі актора =
//  фігура».
//
//  НАПРЯМ КРУТИТЬ ЛИШЕ ЦЯТКУ, не групу. Група з rotation — transform-матриця
//  і два флаші батча на кадр, а обертати в м'ячі є що одне — білу цятку. Тому
//  heading ставить її по колу (cos/sin): та сама картинка, що давав rotation
//  групи (135° − angle, один оберт цятки на оберт по орбіті — м'яч
//  «повертається» разом з орбітою, як ctx.rotate у прототипі), але кільце
//  комбо завжди починається з 12-ї, а супутники крутяться своїм темпом.
//
//  Кільця — один шейдер ProgressRingEffect: комбо тане від 12-ї за
//  годинниковою, щит — те саме кільце з frac = 1. Супутники — та сама графіка,
//  що іскра на полі: гравець читає «спіймані іскри стали моїми».
// ─────────────────────────────────────────────────────────────────────────────
class ABall(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        // ── м'яч ──
        /** Диск r 23.8 + blur σ 11 у Figma → квад 47.6 × 84/40 (outer/диск текстури glow). */
        private const val GLOW_SIZE  = 100f
        private const val GLOW_ALPHA = 0.90f
        private const val POINT_SIZE = 10f
        /** Цятка в макеті на (−5, −5) від центру: 7.07 під 135° при heading = 0. */
        private const val POINT_R     = 7.071f
        private const val POINT_ANGLE = 135f
        /**
         * Обертів цятки на один оберт по орбіті. 1 — як у прототипі (ctx.rotate
         * на кут орбіти): цятка тримає одне місце відносно напряму руху, м'яч
         * «повертається разом з орбітою». ~9.5 (r кільця / r м'яча) — котився б
         * колесом; на 100–300°/с це вже мерехтіння, тому не воно.
         */
        private const val POINT_SPIN  = 1f

        // ── кільце щита ──
        private const val SHIELD_R = 28.75f
        private const val SHIELD_W = 2.5f

        // ── супутники комбо ──
        /** Найбільше супутників: x5 дає чотири (множник − 1). */
        const val MAX_SATS = 4
        private const val SAT_R      = 29f
        private const val SAT_DOT    = 6f
        /** Ореол супутника: диск r 4.8 + σ 2.2 у Figma → 9.6 × 84/40, та сама формула, що GLOW_SIZE. */
        private const val SAT_GLOW   = 20f
        private const val SAT_GLOW_A = 0.90f
        /** Оберт супутників, °/с, як у прототипі; мінус — за годинниковою, куди летить м'яч. */
        private const val SAT_SPEED  = 260f

        // ── кільце комбо ──
        private const val COMBO_R       = 40f
        private const val COMBO_W       = 4f
        private const val COMBO_TRACK_A = 0.25f

        /** Квад під кільця — фрейм компонента у Figma (100): обводка до 42 + запас під AA. */
        private const val RING_QUAD = 100f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    /** Напрям руху, екранні градуси. Крутить лише цятку. Ставить GameScreen. */
    var heading = 0f

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsMsdf.glow).apply { color.a = GLOW_ALPHA }
    private val aBall  = Image(gdxGame.assetsMsdf.circle)
    private val aPoint = Image(gdxGame.assetsMsdf.circle).apply { color = GameColor.white_90 }

    private val fxShield = ProgressRingEffect().apply { frac = 1f }
    private val aShield  = VfxImage(screen, screen.drawerUtil.getRegion(), fxShield)

    private val fxCombo = ProgressRingEffect().apply { trackAlpha = COMBO_TRACK_A }
    private val aCombo  = VfxImage(screen, screen.drawerUtil.getRegion(), fxCombo)

    private class Sat(val aGlow: Image, val aDot: Image)
    private val sats = List(MAX_SATS) {
        Sat(
            aGlow = Image(gdxGame.assetsMsdf.glow).apply { color.a = SAT_GLOW_A },
            aDot  = Image(gdxGame.assetsMsdf.circle),
        )
    }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    /** Скільки супутників показувати; 0 — комбо немає, кільця теж. */
    private var satCount = 0

    /** Кут першого супутника, градуси; решта — рівномірно по колу. */
    private var spin = 0f

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addShield()
        addBall()
        addPoint()
        addSats()
        addCombo()

        // Похідна геометрія кілець — через keepScaled, а не sizeChanged():
        // GameScreen ставить розмір м'яча ДО addActor, а фактор скейлера
        // рахується лише зі stage — sizeChanged() бачив factor = 1 і виносив
        // обидва кільця за квад. keepScaled виконується тут, уже з фактором,
        // і переприкладається на кожен resize.
        keepScaled {
            fxShield.radius    = SHIELD_R.toActual
            fxShield.thickness = SHIELD_W.toActual
            fxCombo.radius     = COMBO_R.toActual
            fxCombo.thickness  = COMBO_W.toActual
        }

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        placePoint()

        spin = (spin - SAT_SPEED * delta) % 360f
        placeSats()
    }

    // ------------------------------------------------------------------------
    // Sync · стан із рушія
    // ------------------------------------------------------------------------

    /** Кільце щита є, поки є заряд. */
    var shieldOn: Boolean
        get() = aShield.isVisible
        set(value) { aShield.isVisible = value }

    /**
     * Комбо: [frac] — частка вікна 0..1 (кільце), [count] — супутників
     * (множник − 1). count = 0 — комбо немає: ні кільця, ні супутників.
     */
    fun setCombo(frac: Float, count: Int) {
        fxCombo.frac = frac
        val n = count.coerceIn(0, MAX_SATS)
        if (n == satCount) return
        satCount = n
        aCombo.isVisible = n > 0
        sats.forEachIndexed { i, s -> val on = i < n; s.aGlow.isVisible = on; s.aDot.isVisible = on }
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addShield() {
        add(aShield) { size(RING_QUAD); center() }
        aShield.isVisible = false
    }

    private fun addBall() {
        add(aBall) { fillParent() }
    }

    /** Позицію цятки веде heading у act() — у реєстрі лише розмір. */
    private fun addPoint() {
        addActor(aPoint)
        aPoint.setSizeScaled(POINT_SIZE, POINT_SIZE)
    }

    private fun addSats() {
        for (s in sats) {
            addActor(s.aGlow); s.aGlow.setSizeScaled(SAT_GLOW, SAT_GLOW)
            addActor(s.aDot);  s.aDot.setSizeScaled(SAT_DOT, SAT_DOT)
            s.aGlow.isVisible = false
            s.aDot.isVisible  = false
        }
    }

    private fun addCombo() {
        add(aCombo) { size(RING_QUAD); center() }
        aCombo.isVisible = false
    }

    // ------------------------------------------------------------------------
    // Place
    // ------------------------------------------------------------------------
    private fun placePoint() {
        val a = (POINT_ANGLE + heading * POINT_SPIN) * MathUtils.degreesToRadians
        val r = POINT_R.toActual
        aPoint.setPosition(width / 2f + MathUtils.cos(a) * r, height / 2f + MathUtils.sin(a) * r, Align.center)
    }

    /** Супутники рівномірно по колу від spin; перший починає з 12-ї години. */
    private fun placeSats() {
        if (satCount == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val r  = SAT_R.toActual
        val step = 360f / satCount

        for (i in 0 until satCount) {
            val a = (90f + spin + i * step) * MathUtils.degreesToRadians
            val x = cx + MathUtils.cos(a) * r
            val y = cy + MathUtils.sin(a) * r
            sats[i].aGlow.setPosition(x, y, Align.center)
            sats[i].aDot.setPosition(x, y, Align.center)
        }
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.player)
        aBall.setColorRGB(ThemeManager.current.player)
    }
}
