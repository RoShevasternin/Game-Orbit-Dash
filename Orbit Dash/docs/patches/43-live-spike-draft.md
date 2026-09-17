# 43 — Живий шип (ЧЕРНЕТКА, стенд)

> **Ігрова версія — `44-live-spike.md`.** Тут лишається стенд (`TestScreen`) і
> перша версія ідеї; шейдер і ефект у 44 — ті самі.

> Статус на 17.09.2026: стенд зібрано в лабораторії й показано на пристрої
> (відео «Живі шипи»). Користувач ще обирає. Це **не патч до вставки**, а
> збережений робочий код, щоб не загубився разом зі scratchpad.

## Ідея (від користувача)

Шип — шейдер, а не MSDF: кожен промінь живе окремо. Далеко від м'яча в кожного
шипа свій характер; що ближче м'яч — то більше променів і тим агресивніший
«бамс»: «я небезпечний, краще оминай».

## Що зроблено на стенді

- **24 позиції променів** (крок 15°). Видно 3 / 4 / 6 / 8, тобто кожна 8/6/4/3-тя.
  `level` 0..3 обирає набір, дробова частина — перехід: нові виростають із
  ядра, зайві втягуються. Позиція 0 є в усіх наборах.
- **Характер у спокої** (`Mood`):
  - WAVE — 6 променів, уколи біжать по колу за годинниковою;
  - JAB — 4 промені, б'ють вроздріб;
  - COUNT — кількість гойдається 3 → 4 → 6 → 4.
- **Загроза** 0..1 від відстані м'яч ↔ шип (NEAR 34, FAR 110). Росте швидко
  (3/с), спадає повільно (0.7/с). Коли загроза > 0.25, стає 8 променів (ростуть 6
  рівнів/с, ховаються 1.2/с). Шип роздувається (довжина в спокої 0.62 → 0.74),
  характер тоне в BURST (такт 0.8 с: набирає → бамс → осідає), а ореол
  розгоряється й спалахує на кожен бамс.
- **Геометрія як у `spike.svg`**: основа променя на колі 0.452 від довжини,
  півширина 22.5°; на 8 променях це рівно та сама зірка.
- **Оберт** за годинниковою 18°/с, під загрозою до 58°/с.
- **Пристрій**: 61 FPS, 7 шипів, кожен — окремий `VfxImage` (свій шейдер і
  юніформи → окремий draw call).

## Що лишилось до гри

- **Батчинг.** У грі до 14 шипів. Схема патча 25: шейдер один на групу, а те,
  що в кожного своє, — у вершині: `r` = level, `g` = threat, `b` = mood + phase,
  `a` = spawn-fade. Колір теми — юніформ на всіх.
- **Хто рахує загрозу.** Відстань м'яч ↔ шип — у `GameScreen.syncEntities()`
  (місток), з позицій акторів, а не в рушії: це суто вигляд, колізій не
  змінює. Актор отримує лише `threat`.
- **Хітбокс.** Зона зіткнення в рушії не змінюється. На повній загрозі вістря
  доходить до 0.98 півсторони — це й має відповідати хітбоксу. Спокійний шип
  візуально менший, але тоді м'яч далеко.
- Лоадер у лабораторії веде на `TestScreen` (`LoaderScreen.NEXT_SCREEN_NAME`) —
  у проєкт це не переносити.

---

## `app/src/main/assets/shader/objects/spikeFS.glsl`

```glsl
#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// SPIKE — живий шип: ядро + промені, кожен зі своєю довжиною.
//
//   КІЛЬКІСТЬ. 24 можливі позиції (крок 15°). Видно 3, 4, 6 або 8 — це
//   кожна 8-ма, 6-та, 4-та чи 3-тя позиція. u_level 0..3 обирає набір,
//   дробова частина — перехід: нові промені виростають із ядра, зайві
//   втягуються в нього. Позиція 0 є в усіх наборах — вона ніколи не зникає.
//
//   ХАРАКТЕР у спокої (u_mode): WAVE — уколи біжать по колу; JAB — б'ють
//   вроздріб; COUNT — лише ледь дихає, а кількість гойдає CPU.
//
//   ЗАГРОЗА (u_threat 0..1): шип роздувається, характер тоне в BURST —
//   усі промені втягуються й разом вистрілюють, «бамс» раз на 0.8 с.
//
//   Геометрія — як у spike.svg: основа променя на колі 0.452 від довжини,
//   півширина 22.5°. На 8 променях сусідні основи сходяться — рівно та зірка.
//   Промінь = клин між двома прямими від основи до вістря (складено по осі);
//   ядро — коло. Разом — min(), без швів.
//
//   Перевіряємо лише 5 найближчих позицій: ширший за ±2 кроки промінь
//   до пікселя не дістане.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_localUV;

uniform vec2  u_size;     // квад у world-юнітах
uniform vec3  u_color;
uniform float u_time;     // с, ShaderClock
uniform float u_phase;    // 0..1, щоб сусіди не йшли в такт
uniform float u_rot;      // кут, рад
uniform float u_mode;     // 0 WAVE · 1 JAB · 2 COUNT
uniform float u_level;    // 0..3 → 3 · 4 · 6 · 8 променів
uniform float u_threat;   // 0..1
uniform float u_aa;       // згладжування, world-юніти

const float SLOTS  = 24.0;
const float STEP   = 0.26179939;   // 2π / 24
const float HALF_W = 0.39269908;   // 22.5°
const float INNER  = 0.452;

float hash(float n) { return fract(sin(n) * 43758.5453); }

float smoothUnit(float x) { x = clamp(x, 0.0, 1.0); return x * x * (3.0 - 2.0 * x); }

// Укол: різкий виліт до a, повільне втягування до 1. Поза [0, 1] — спокій.
float jab(float u, float a) {
    if (u < 0.0 || u > 1.0) return 0.0;
    if (u < a) { float x = u / a; return 1.0 - (1.0 - x) * (1.0 - x); }
    float x = (u - a) / (1.0 - a);
    return 1.0 - x * x * (3.0 - 2.0 * x);
}

float slotOn(float s, float idx) {
    float every = idx < 0.5 ? 8.0 : (idx < 1.5 ? 6.0 : (idx < 2.5 ? 4.0 : 3.0));
    return mod(s, every) < 0.5 ? 1.0 : 0.0;
}

float activation(float s) {
    float lo = floor(u_level);
    float hi = min(lo + 1.0, 3.0);
    return mix(slotOn(s, lo), slotOn(s, hi), smoothUnit(u_level - lo));
}

// Спокій: +1 вистрілив, −1 втягнувся
float calmShift(float s) {
    float t = u_time + u_phase * 20.0;
    if (u_mode < 0.5) return jab(fract(t / 2.0 + s / SLOTS) * 8.0 / 3.0, 0.18);   // по колу за годинниковою
    if (u_mode < 1.5) return jab(fract(t / 2.5 - hash(s + 1.7)) * 5.7, 0.14);     // вроздріб
    return 0.15 * sin(t * 2.4 + s * 0.9);                                        // дихає
}

// Загроза: набирає → бамс → осідає. Такт 0.8 с ділить 100 націло (ShaderClock).
float burstShift() {
    float c = fract(u_time / 0.8 + u_phase);
    if (c < 0.45) return -smoothUnit(c / 0.45);
    if (c < 0.52) { float x = (c - 0.45) / 0.07; return -1.0 + 2.0 * (1.0 - (1.0 - x) * (1.0 - x)); }
    return 1.0 - smoothUnit((c - 0.52) / 0.48);
}

void main() {
    float halfSide = 0.5 * min(u_size.x, u_size.y);
    vec2  p = (v_localUV - 0.5) * u_size / halfSide;   // −1..1
    float r = length(p);

    // Під загрозою шип роздувається: 0.98 — вістря на піку, край квада не чіпає
    float rest = mix(0.62, 0.74, u_threat);
    float amp  = 0.24;
    float pull = 0.16;
    float rin  = INNER * rest;
    float xb   = rin * cos(HALF_W);
    vec2  B    = vec2(xb, rin * sin(HALF_W));

    float anger = smoothUnit(u_threat * 1.6 - 0.3);
    float burst = burstShift();

    float d = r - rin;                                  // ядро
    if (r > 0.0001) {
        float ang = atan(p.y, p.x) - u_rot;
        float k   = floor(ang / STEP + 0.5);

        for (int j = -2; j <= 2; j++) {
            float sk  = k + float(j);
            float s   = mod(sk, SLOTS);
            float act = activation(s);
            if (act < 0.002) continue;

            float shift = mix(calmShift(s), burst, anger);
            float full  = rest + (shift > 0.0 ? shift * amp : shift * pull);
            float L     = mix(xb * 1.02, full, act);     // схований — вістря всередині ядра

            float a = ang - sk * STEP;
            vec2  q = vec2(cos(a), abs(sin(a))) * r;    // складено по осі променя
            vec2  T = vec2(L, 0.0);
            vec2  e = B - T;
            vec2  n = normalize(vec2(e.y, -e.x));       // назовні

            d = min(d, max(dot(q - T, n), xb - q.x));
        }
    }

    d *= halfSide;                                      // world-юніти, <0 усередині
    float alpha = 1.0 - smoothstep(-u_aa * 0.5, u_aa * 0.5, d);
    gl_FragColor = vec4(u_color, alpha) * v_color;
}
```

## `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/SpikeEffect.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// SpikeEffect — живий шип: змінна кількість променів, характер, загроза.
// Single-pass, без FBO. Анімація променів — у шейдері від time; CPU дає лише
// рівень (скільки променів), загрозу, фазу й кут.
// ─────────────────────────────────────────────────────────────────────────────
class SpikeEffect : VfxEffect() {

    /** Як шип поводиться, поки м'яч далеко. */
    enum class Mood { WAVE, JAB, COUNT }

    override val fragmentShader = "shader/objects/spikeFS.glsl"

    var mood     = Mood.WAVE
    var time     = 0f
    var phase    = 0f
    /** Кут зірки, радіани. */
    var rotation = 0f
    /** 0..3 → 3 · 4 · 6 · 8 променів; дробова частина — перехід. */
    var level    = 3f
    /** 0..1 — наскільки м'яч близько. */
    var threat   = 0f
    var aaWidth  = 0.7f

    val color = Color(Color.WHITE)

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_size",   ctx.width, ctx.height)
        shader.setUniformf("u_color",  color.r, color.g, color.b)
        shader.setUniformf("u_time",   time)
        shader.setUniformf("u_phase",  phase)
        shader.setUniformf("u_rot",    rotation)
        shader.setUniformf("u_mode",   mood.ordinal.toFloat())
        shader.setUniformf("u_level",  level)
        shader.setUniformf("u_threat", threat)
        shader.setUniformf("u_aa",     aaWidth)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + time.toRawBits()
        k = k * 31 + rotation.toRawBits()
        k = k * 31 + level.toRawBits()
        k = k * 31 + threat.toRawBits()
        k = k * 31 + mood.ordinal
        k = k * 31 + color.toIntBits()
        return k
    }
}
```

## `app/src/main/java/com/lewydo/orbitdash/game/screens/TestScreen.kt` (стенд, тимчасовий)

```kotlin
package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.objects.ABall
import com.lewydo.orbitdash.game.actors.orbit.AOrbitRing
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.ShaderClock
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.SpikeEffect
import com.lewydo.orbitdash.game.utils.vfx.effects.SpikeEffect.Mood

/**
 * СТЕНД: живі шипи. ТИМЧАСОВЕ.
 * М'яч їде по зовнішній орбіті; тягнеш пальцем — іде за пальцем.
 * Далеко — кожен шип у своєму характері, близько — 8 променів і «бамс».
 */
class TestScreen : AdvancedScreen() {

    private val aStarField by lazy { AStarField(this) }
    private val aField     by lazy { AStandField(this) }

    private val msdf by lazy { gdxGame.msdfManager }
    private val styleInfo by lazy { MsdfStyle(msdf, msdf.fontInter_Medium, 11f, Color.WHITE.cpy().apply { a = 0.75f }) }

    private val tmp = Vector2()

    override fun show() {
        super.show()
        animShowScreen()
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        add(aField) { fillParent() }

        val info = AMsdfLabel(
            "W — хвиля, 6 променів    J — уколи, 4    C — кількість 3-4-6\n" +
            "м'яч ближче — 8 променів і «бамс»\nтягни пальцем — м'яч іде за тобою",
            styleInfo,
        )
        info.setAlignment(Align.center)
        add(info) { size(340f, 50f); centerX(); bottomToBottom(margin = 24f) }

        addDebugHud(ADebugHud(this@TestScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        aField.finger = toField(screenX, screenY)
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        aField.finger = toField(screenX, screenY)
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        aField.finger = null
        return false
    }

    private fun toField(sx: Int, sy: Int): Vector2 {
        stageUI.screenToStageCoordinates(tmp.set(sx.toFloat(), sy.toFloat()))
        return aField.stageToLocalCoordinates(tmp).cpy()
    }

    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.disable()
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // Поле стенда: дві орбіти, сім шипів, м'яч
    // ------------------------------------------------------------------------
    private class AStandField(override val screen: AdvancedScreen) : AdvancedGroup() {

        companion object {
            private const val R_OUT  = 132f
            private const val R_IN   = 80f
            private const val SPIKE  = 34f
            private const val BALL   = 22f
            private const val BALL_SPEED = 55f   // °/с, за годинниковою
            /** М'яч їде МІЖ кільцями: проходить повз шипи обох орбіт і не затуляє їх. */
            private const val R_BALL = (R_OUT + R_IN) / 2f
        }

        /** Позиція пальця в координатах поля; null — м'яч їде сам. */
        var finger: Vector2? = null

        private val msdf by lazy { gdxGame.msdfManager }
        private val styleTag by lazy { MsdfStyle(msdf, msdf.fontInter_Bold, 10f, Color.WHITE.cpy().apply { a = 0.6f }) }

        private val ringOut = AOrbitRing(screen)
        private val ringIn  = AOrbitRing(screen)
        private val ball    = ABall(screen)

        // кут на орбіті (°), орбіта, характер
        private val layout = listOf(
            Triple(90f,  R_OUT, Mood.WAVE),
            Triple(20f,  R_OUT, Mood.JAB),
            Triple(-50f, R_OUT, Mood.COUNT),
            Triple(-125f, R_OUT, Mood.WAVE),
            Triple(185f, R_OUT, Mood.JAB),
            Triple(-20f, R_IN,  Mood.COUNT),
            Triple(150f, R_IN,  Mood.WAVE),
        )
        private val spikes = layout.mapIndexed { i, (_, _, mood) -> ALiveSpike(screen, mood, phase = i * 0.137f) }
        private val tags   = layout.map { (_, _, mood) -> AMsdfLabel(mood.name.take(1), styleTag).apply { setAlignment(Align.center) } }

        private var ballDeg = 60f
        private val center = Vector2()

        override fun addActorsOnGroup() {
            touchable = Touchable.disabled
            addActor(ringOut); addActor(ringIn)
            ringOut.thickness = 2f; ringIn.thickness = 2f
            spikes.forEach { it.setSize(SPIKE, SPIKE); addActor(it) }
            tags.forEach { it.setSize(20f, 14f); addActor(it) }
            ball.setSize(BALL, BALL); addActor(ball)
            place()
        }

        override fun sizeChanged() {
            super.sizeChanged()
            if (ball.parent != null) place()
        }

        private fun place() {
            center.set(width / 2f, height * 0.55f)
            for ((ring, r) in listOf(ringOut to R_OUT, ringIn to R_IN)) {
                ring.outerPad = 4f
                ring.setSize(2f * r + 8f, 2f * r + 8f)
                ring.setPosition(center.x, center.y, Align.center)
            }
            layout.forEachIndexed { i, (deg, r, _) ->
                val rad = deg * MathUtils.degreesToRadians
                spikes[i].setPosition(center.x + MathUtils.cos(rad) * r, center.y + MathUtils.sin(rad) * r, Align.center)
                val tr = r + (if (r == R_OUT) 30f else -30f)
                tags[i].setPosition(center.x + MathUtils.cos(rad) * tr, center.y + MathUtils.sin(rad) * tr, Align.center)
            }
        }

        override fun act(delta: Float) {
            super.act(delta)
            ringOut.ringColor = ThemeManager.current.ring
            ringIn.ringColor  = ThemeManager.current.ring

            val f = finger
            if (f != null) {
                ball.setPosition(f.x, f.y, Align.center)
            } else {
                ballDeg = (ballDeg - BALL_SPEED * delta) % 360f
                val rad = ballDeg * MathUtils.degreesToRadians
                ball.setPosition(center.x + MathUtils.cos(rad) * R_BALL, center.y + MathUtils.sin(rad) * R_BALL, Align.center)
            }

            val bx = ball.x + ball.width / 2f
            val by = ball.y + ball.height / 2f
            spikes.forEach { it.lookAt(bx, by, delta) }
        }
    }

    // ------------------------------------------------------------------------
    // Живий шип стенда
    // ------------------------------------------------------------------------
    private class ALiveSpike(
        override val screen: AdvancedScreen,
        private val mood: Mood,
        private val phase: Float,
    ) : AConstraintLayout(screen) {

        companion object {
            private const val NEAR = 34f      // від центру до центру: тут загроза повна
            private const val FAR  = 110f     // далі — спокій
            private const val THREAT_UP   = 3.0f   // 1/с — насторожується швидко
            private const val THREAT_DOWN = 0.7f   // заспокоюється повільно
            private const val GROW_SPEED   = 6.0f  // рівнів/с — промені вистрілюють
            private const val SHRINK_SPEED = 1.2f  // і неспішно ховаються
        }

        private val fx     = SpikeEffect().apply { this.mood = this@ALiveSpike.mood; this.phase = this@ALiveSpike.phase }
        private val aGlow  = Image(gdxGame.assetsMsdf.glow)
        private val aShape = VfxImage(screen, gdxGame.assetsMsdf.circle, fx)
        private val aPoint = Image(gdxGame.assetsMsdf.circle).apply { color = Color.BLACK.cpy().apply { a = 0.40f } }

        private var threat   = 0f
        private var level    = calmLevel(0f)
        private var angleDeg = phase * 360f

        override fun addActorsOnGroup() {
            add(aGlow)  { size(width * 2.5f); center() }
            add(aShape) { fillParent() }
            add(aPoint) { size(width * 0.22f); center() }
        }

        fun lookAt(bx: Float, by: Float, delta: Float) {
            val dist   = Vector2.dst(bx, by, x + width / 2f, y + height / 2f)
            val target = 1f - Interpolation.smooth.apply(((dist - NEAR) / (FAR - NEAR)).coerceIn(0f, 1f))
            threat = approach(threat, target, (if (target > threat) THREAT_UP else THREAT_DOWN) * delta)

            val t = ShaderClock.time
            val wanted = if (threat > 0.25f) 3f else calmLevel(t)
            level = approach(level, wanted, (if (wanted > level) GROW_SPEED else SHRINK_SPEED) * delta)

            angleDeg = (angleDeg - (18f + 40f * threat) * delta) % 360f   // за годинниковою; зліший — швидший

            fx.time     = t
            fx.rotation = angleDeg * MathUtils.degreesToRadians
            fx.level    = level
            fx.threat   = threat
            fx.color.set(ThemeManager.current.spike)

            // Ореол розгоряється із загрозою і спалахує на кожен «бамс»
            val flare = burst(t) * threat
            aGlow.setColorRGB(ThemeManager.current.spike)
            aGlow.color.a = 0.45f + 0.35f * threat + 0.2f * flare
            aGlow.setOrigin(Align.center)
            aGlow.setScale(0.85f + 0.2f * threat + 0.25f * flare)
        }

        /** Скільки променів у спокої: WAVE — 6, JAB — 4, COUNT — гойдається 3 → 4 → 6 → 4. */
        private fun calmLevel(t: Float): Float = when (mood) {
            Mood.WAVE  -> 2f
            Mood.JAB   -> 1f
            Mood.COUNT -> floatArrayOf(0f, 1f, 2f, 1f)[(((t + phase * 20f) / 1.4f).toInt() % 4 + 4) % 4]
        }

        /** Та сама крива «бамсу», що в шейдері — лише позитивна частина. */
        private fun burst(t: Float): Float {
            val c = ((t / 0.8f + phase) % 1f + 1f) % 1f
            return when {
                c < 0.45f -> 0f
                c < 0.52f -> { val x = (c - 0.45f) / 0.07f; (-1f + 2f * (1f - (1f - x) * (1f - x))).coerceAtLeast(0f) }
                else      -> 1f - Interpolation.smooth.apply(((c - 0.52f) / 0.48f).coerceIn(0f, 1f))
            }
        }

        private fun approach(v: Float, target: Float, step: Float) =
            if (v < target) minOf(v + step, target) else maxOf(v - step, target)
    }
}
```
