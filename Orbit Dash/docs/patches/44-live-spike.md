# 44 — Живий шип у грі

## Що і навіщо

Шип більше не застигла MSDF-зірка, а шейдер, у якому кожен промінь живе окремо.
Стенд і рішення — у чернетці `43-live-spike-draft.md`, тут — ігрова версія.

- **М'яч далеко** — у шипа свій характер (від `id` сутності, тож той самий сід
  дає ті самі шипи):
  - WAVE — 6 променів, уколи біжать по колу за годинниковою;
  - JAB — 4 промені, б'ють вроздріб;
  - COUNT — кількість гойдається 3 → 4 → 6 → 4: нові промені виростають із
    ядра, зайві ховаються в нього.
- **М'яч близько** — за частку секунди 8 променів, шип роздувається, «бамс»
  раз на 0.8 с (усі втягуються й разом вистрілюють), ореол розгоряється й
  спалахує на удар, оберт пришвидшується. Насторожується швидко (5/с),
  заспокоюється повільно (0.7/с) — пролетів повз і не миготить.
- **Ореол зменшено** (ти казав «завеликий»). У спокої 0.70 від `GLOW_SIZE`,
  на загрозі 0.85, спалах +0.10 — максимум 0.95. Було: на стенді до 1.3,
  у старому шипі завжди 1.0. Числа — у `companion object` `ASpike`
  (`GLOW_SCALE_*`, `GLOW_ALPHA_*`).
- **Розмір — як у старого шипа.** Стара MSDF-зірка виходила за межі актора
  (вістря на 1.08 півсторони — крутився сам квад). Шейдер крутить зірку
  всередині квада, тому квад зірки ширший за актор на 15 % (`SHAPE_SIZE = 46`
  при фреймі 40). Заміряно на пристрої, найдальше вістря від центру:
  старий шип ≈ 41 px; новий у спокої ≈ 33 px (промені трохи коротші, і їх
  3–6), на уколі й «бамсі» — до ≈ 42 px. Перша версія без цього була вдвічі
  меншою (≈ 21 px) — «чому шипи стали такі малі».

### Хто що робить

- **`GameScreen`** передає шипу лише **позицію м'яча** (`watchBall`). Жодного
  «якщо близько» — застереження в шапці `GameScreen` про «if спайк близько»
  дотримано: рішення, як реагувати, приймає вигляд.
- **`ASpike`** рахує близькість у **своїх розмірах** (NEAR 1.2, FAR 5.0 — від
  центру до центру), згладжує, обирає кількість променів, крутить ореол.
- **`spikeFS.glsl`** малює форму: 24 можливі позиції променів, характер,
  «бамс» — усе від `u_time`, CPU не рахує жодного променя.
- **Рушій не змінено.** Зона зіткнення та сама; на повній загрозі вістря
  доходить до краю актора, тобто до розміру, яким шип малювався завжди.
  Спокійний шип візуально менший, але тоді м'яч далеко.

### Пакети нових файлів

- `assets/shader/objects/spikeFS.glsl` — поруч із `background/` (зоряне поле)
  і `orbit/` (кільця): шейдери за тим, що малюють; `objects` — як пакет
  `actors/objects`, де живе `ASpike`.
- `utils/vfx/effects/SpikeEffect.kt` — поруч з `OrbitRingEffect`: ефект
  ігрового об'єкта, імпортує лише базу `VfxEffect`, імпортує його `ASpike`.
  `Mood` (характер) — у ефекті, бо це параметр вигляду, а не правило гри.

### Перевірено на пристрої (Redmi, лабораторна копія)

- 12 шипів на полі (дебаг-кнопка), пауза м'яча: **61 FPS**, draw 69.
  Шип біля м'яча — 8 променів і яскравий ореол, решта — 3–6, спокійні.
- TIME ×0.25, м'яч їде до шипів: шипи на шляху розлючуються заздалегідь,
  після смерті м'яча поруч б'ють далі. 57–61 FPS під час запису екрана.
- Падінь немає. Відео — «Живі шипи в грі».

**Не зроблено свідомо: батчинг.** Кожен шип — свій `VfxImage` (свій шейдер
→ окремий draw call, ~3 на шип разом з ореолом і крапкою). За
`docs/many-actors.md` це дешево (103 draw = 61 FPS), і на 12 шипах так і є.
Якщо колись шипів стане десятки — схема патча 25: шейдер на групу, дані в
вершині (`r` level, `g` threat, `b` mood+phase).

### Що крутити

| хочеш | де |
|---|---|
| помічає раніше / пізніше | `ASpike.NEAR` / `FAR` (у розмірах шипа) |
| швидше злиться / заспокоюється | `THREAT_UP` / `THREAT_DOWN` |
| з якої загрози 8 променів | `ANGRY_AT` |
| ореол | `GLOW_SCALE_*`, `GLOW_ALPHA_*` |
| скільки променів у характеру | `ASpike.calmLevel()` (0 → 3, 1 → 4, 2 → 6, 3 → 8) |
| темп «бамсу» | `spikeFS.glsl` `burstShift()` **і** `ASpike.BURST_PERIOD` — разом, ділить 100 націло |
| розмір у спокої / на загрозі | `spikeFS.glsl`: `rest = mix(0.72, 0.76, u_threat)`; вістря = rest + `amp` (0.22) ≤ 0.98. Загалом більший — `ASpike.SHAPE_SIZE` |

---

## Порядок вставки

1 → 2 → 3 → 4. Компілюється після 4.

## 1. НОВИЙ файл `app/src/main/assets/shader/objects/spikeFS.glsl`

Каталогу `shader/objects/` ще немає — створити.

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

    // Частки півсторони КВАДА. Квад більший за актор (ASpike.SHAPE_SIZE), тож
    // 0.98 — вістря на піку — це ~1.13 півсторони актора, як стара MSDF-зірка
    // (у spike.svg вістря на 1.08). Під загрозою шип трохи роздувається.
    float rest = mix(0.72, 0.76, u_threat);
    float amp  = 0.22;
    float pull = 0.14;
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

## 2. НОВИЙ файл `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/SpikeEffect.kt`

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

## 3. `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ASpike.kt` — ЗАМІНИТИ ВЕСЬ ФАЙЛ

Від патча 42 (удар і повільний оберт MSDF-зірки) не лишається нічого: зірка
тепер шейдер, і «удар» замінено на «бамс» під загрозою.

```kotlin
package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.ShaderClock
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.SpikeEffect

// ─────────────────────────────────────────────────────────────────────────────
//  ASpike — ЖИВИЙ шип. Ореол + зірка-шейдер (SpikeEffect) + темна крапка.
//
//  Зірка — не MSDF: там форма застигла, а тут кожен промінь живе окремо —
//  вистрілює, втягується, виростає з ядра чи ховається в нього.
//
//  ДВА СТАНИ, між ними — плавно:
//    • м'яч далеко — у шипа свій характер (id сутності → WAVE / JAB / COUNT),
//      3–6 променів, повільний оберт за годинниковою;
//    • м'яч близько — 8 променів, шип роздувається і б'є «бамс» раз на 0.8 с,
//      ореол розгоряється й спалахує на кожен удар. «Краще оминай».
//  Насторожується швидко, заспокоюється повільно — м'яч, що пролетів повз,
//  не викликає миготіння.
//
//  Відстань до м'яча дає GameScreen (watchBall) — просто позицію, без рішень.
//  Як на неї реагувати — справа вигляду, тому пороги тут, у шипі, і в його
//  розмірах: на будь-якому екрані «помічає» з однієї відстані.
//
//  Зона зіткнення в рушії від цього НЕ змінюється. Розмір — як у старої
//  MSDF-зірки: у спокої вістря трохи коротші, на уколі й «бамсі» — такі самі.
// ─────────────────────────────────────────────────────────────────────────────
class ASpike(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    companion object {
        private const val GLOW_SIZE  = 100f
        private const val POINT_SIZE = 10f

        /**
         * Квад зірки більший за актор. Шейдер крутить зірку ВСЕРЕДИНІ квада, тож
         * вістря мусить лишатись у ньому за будь-якого кута. Стара MSDF-зірка
         * виходила за межі на 8 % (крутився сам квад) — щоб шип не зменшився,
         * квад ширший на 15 %.
         */
        private const val SHAPE_SIZE = 46f

        // Ореол, частки GLOW_SIZE: спокій → загроза, + спалах на «бамс»
        private const val GLOW_SCALE_CALM   = 0.70f
        private const val GLOW_SCALE_THREAT = 0.85f
        private const val GLOW_SCALE_FLARE  = 0.10f
        private const val GLOW_ALPHA_CALM   = 0.50f
        private const val GLOW_ALPHA_THREAT = 0.80f
        private const val GLOW_ALPHA_FLARE  = 0.15f

        /** Оберт за годинниковою, °/с. Під загрозою додається SPIN_THREAT. */
        private const val SPIN_CALM   = 18f
        private const val SPIN_THREAT = 40f

        /** Від центру до центру, у РОЗМІРАХ шипа: ближче NEAR — загроза повна, далі FAR — спокій. */
        private const val NEAR = 1.2f
        private const val FAR  = 5.0f

        private const val THREAT_UP   = 5.0f   // 1/с — насторожується швидко
        private const val THREAT_DOWN = 0.7f   // заспокоюється повільно

        /** З цієї загрози — усі вісім променів. */
        private const val ANGRY_AT     = 0.25f
        private const val LEVEL_EIGHT  = 3f
        private const val GROW_SPEED   = 6.0f  // рівнів/с — промені вистрілюють
        private const val SHRINK_SPEED = 1.2f  // і неспішно ховаються

        /** COUNT: скільки променів по черзі (0 → 3, 1 → 4, 2 → 6) і скільки секунд на крок. */
        private val COUNT_CYCLE = floatArrayOf(0f, 1f, 2f, 1f)
        private const val COUNT_STEP = 1.4f

        /** Такт «бамсу» — той самий, що в spikeFS.glsl (burstShift). */
        private const val BURST_PERIOD = 0.8f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val fx     = SpikeEffect()
    private val aGlow  = Image(gdxGame.assetsMsdf.glow)
    private val aShape = VfxImage(screen, gdxGame.assetsMsdf.circle, fx)   // регіон — лише квад, форму малює шейдер
    private val aPoint = Image(gdxGame.assetsMsdf.circle).apply { color = Color.BLACK.cpy().apply { a = 0.40f } }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    private val ball = Vector2()

    private var threat   = 0f
    private var level    = 0f
    private var angleDeg = 0f

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addShape()
        addPoint()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        val time = ShaderClock.time

        val target = proximity()
        threat = approach(threat, target, (if (target > threat) THREAT_UP else THREAT_DOWN) * delta)

        val wanted = if (threat > ANGRY_AT) LEVEL_EIGHT else calmLevel(time)
        level = approach(level, wanted, (if (wanted > level) GROW_SPEED else SHRINK_SPEED) * delta)

        // Мінус — за годинниковою: у libGDX додатний кут крутить проти
        angleDeg = (angleDeg - (SPIN_CALM + SPIN_THREAT * threat) * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж

        fx.time     = time
        fx.rotation = angleDeg * MathUtils.degreesToRadians
        fx.level    = level
        fx.threat   = threat

        applyGlow(burstFlare(time) * threat)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addShape() {
        add(aShape) { size(SHAPE_SIZE); center() }
    }

    private fun addPoint() {
        add(aPoint) { size(POINT_SIZE); center() }
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------

    /**
     * Шип щойно взяли з пулу: характер і фаза від id сутності (той самий сід —
     * ті самі шипи), старт спокійний — інакше з пулу виходив би ще розлючений.
     */
    fun spawn(id: Int) {
        fx.mood  = SpikeEffect.Mood.entries[Math.floorMod(id, SpikeEffect.Mood.entries.size)]
        fx.phase = (id * 0.618034f) % 1f

        threat   = 0f
        level    = calmLevel(ShaderClock.time)
        angleDeg = fx.phase * 360f
        ball.set(Float.MAX_VALUE, Float.MAX_VALUE)
    }

    /** Центр м'яча в координатах батька (поля). Кличе GameScreen щокадру. */
    fun watchBall(x: Float, y: Float) { ball.set(x, y) }

    // ------------------------------------------------------------------------
    // Mood
    // ------------------------------------------------------------------------

    /** 1 — м'яч впритул, 0 — далі FAR. Пороги в розмірах шипа. */
    private fun proximity(): Float {
        if (width <= 0f || ball.x == Float.MAX_VALUE) return 0f
        val dist = Vector2.dst(ball.x, ball.y, x + width / 2f, y + height / 2f) / width
        return 1f - Interpolation.smooth.apply(((dist - NEAR) / (FAR - NEAR)).coerceIn(0f, 1f))
    }

    /** Скільки променів у спокої: WAVE — 6, JAB — 4, COUNT — гойдається 3 → 4 → 6 → 4. */
    private fun calmLevel(time: Float): Float = when (fx.mood) {
        SpikeEffect.Mood.WAVE  -> 2f
        SpikeEffect.Mood.JAB   -> 1f
        SpikeEffect.Mood.COUNT -> COUNT_CYCLE[Math.floorMod(((time + fx.phase * 20f) / COUNT_STEP).toInt(), COUNT_CYCLE.size)]
    }

    /** Та сама крива «бамсу», що в шейдері, — лише виліт: 0 поки набирає, 1 у мить удару. */
    private fun burstFlare(time: Float): Float {
        val c = ((time / BURST_PERIOD + fx.phase) % 1f + 1f) % 1f
        return when {
            c < 0.45f -> 0f
            c < 0.52f -> { val x = (c - 0.45f) / 0.07f; (-1f + 2f * (1f - (1f - x) * (1f - x))).coerceAtLeast(0f) }
            else      -> 1f - Interpolation.smooth.apply((c - 0.52f) / 0.48f)
        }
    }

    /** Ореол розгоряється із загрозою і спалахує на кожен «бамс». */
    private fun applyGlow(flare: Float) {
        aGlow.setOrigin(Align.center)   // після super.act(): розмір дітей лейаут вирішує там
        aGlow.setScale(MathUtils.lerp(GLOW_SCALE_CALM, GLOW_SCALE_THREAT, threat) + GLOW_SCALE_FLARE * flare)
        aGlow.color.a = MathUtils.lerp(GLOW_ALPHA_CALM, GLOW_ALPHA_THREAT, threat) + GLOW_ALPHA_FLARE * flare
    }

    private fun approach(value: Float, target: Float, step: Float) =
        if (value < target) minOf(value + step, target) else maxOf(value - step, target)

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        fx.color.set(ThemeManager.current.spike)
        aGlow.setColorRGB(ThemeManager.current.spike)
    }
}
```

## 4. `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt`

### 4а. `syncEntities()` — ЗАМІНИТИ функцію від початку до `releaseMissing()`

Було:
```kotlin
    private fun syncEntities() {
        seenIds.clear()

        for (e in engine.entities) {
            seenIds.add(e.id)
            val actor = activeActors.getOrPut(e.id) { acquire(e) }

            aOrbitField.positionAt(actor, e.rr * RunEngine.TO_FIELD, -e.a)
            actor.color.a = e.s   // spawn-fade 0→1
        }

        releaseMissing()
    }
```
Стало:
```kotlin
    private fun syncEntities() {
        seenIds.clear()

        // Шипи стежать за м'ячем — їм іде лише позиція, реагують вони самі (ASpike.watchBall)
        val ballX = aBall.x + aBall.width  / 2f
        val ballY = aBall.y + aBall.height / 2f

        for (e in engine.entities) {
            seenIds.add(e.id)
            val actor = activeActors.getOrPut(e.id) { acquire(e) }

            aOrbitField.positionAt(actor, e.rr * RunEngine.TO_FIELD, -e.a)
            actor.color.a = e.s   // spawn-fade 0→1

            if (actor is ASpike) actor.watchBall(ballX, ballY)
        }

        releaseMissing()
    }
```

### 4б. `acquire()` — ЗАМІНИТИ рядок шипа

Було:
```kotlin
        RunEngine.Kind.SPIKE -> freeSpikes.removeLastOrNull()  ?: ASpike(this).also { prepare(it, SPIKE_SIZE) }
```
Стало (два рядки, як у бустера нижче):
```kotlin
        RunEngine.Kind.SPIKE -> (freeSpikes.removeLastOrNull() ?: ASpike(this).also { prepare(it, SPIKE_SIZE) })
            .also { it.spawn(e.id) }
```

`spawn()` обов'язковий: без нього шип із пулу виходив би ще розлюченим після
попереднього рану.

---

## Або скопіювати з лабораторії

Поки жива сесія. Лабораторія — твій проєкт станом на 12:30 17.09 (з патчами
41 і 42) плюс ці зміни. Якщо ти відтоді міняв `ASpike` чи `GameScreen` —
вставляй руками.

```bash
LAB=/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f03d26ed-12c3-428e-bcbc-fd80ce4b4d8f/scratchpad/lab
M=app/src/main
G=$M/java/com/lewydo/orbitdash/game
mkdir -p "$M/assets/shader/objects"
cp "$LAB/$M/assets/shader/objects/spikeFS.glsl"          "$M/assets/shader/objects/spikeFS.glsl"
cp "$LAB/$G/utils/vfx/effects/SpikeEffect.kt"            "$G/utils/vfx/effects/SpikeEffect.kt"
cp "$LAB/$G/actors/objects/ASpike.kt"                    "$G/actors/objects/ASpike.kt"
cp "$LAB/$G/screens/GameScreen.kt"                       "$G/screens/GameScreen.kt"
```

(запускати з `Orbit Dash/`)
