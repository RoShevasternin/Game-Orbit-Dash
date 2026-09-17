# 37 — Ореол за м'ячем, ORBIT III на льоту, плавний пунктир без накопичення

## Що і навіщо

### Ореол займає область активної орбіти

Діаметр ореолу = діаметр орбіти м'яча − 2·10: `190 → 170`, `320 → 300`. У трьох
кільцях: `130 → 110`, `225 → 205`, `320 → 300`. Розмір береться з **`engine.radius`**,
а не з кільця. Тому ореол перелітає разом із м'ячем тим самим лерпом 14/с, сповільнюється
разом зі слоу-мо й `TIME x0.25` і стоїть на `PAUSE`. Під час роз'їзду ORBIT III він теж іде
за м'ячем. Без м'яча (автономний режим) ореол лерпає до активного кільця.
`engine.radius` — у тих самих одиницях, що `ringR`, тобто це вже діаметр поля.

Розмір тепер змінюється щокадру, тому ореол виставляється так само, як кільця:
`setSizeScaled` + `setPosition(center)`, без констрейнта.

### ORBIT III — одразу, без рестарту

Раніше кнопка ставила `Config.orbit3` і перезапускала ран, а орбіта з'являлась лише на 30-й
секунді. Тепер кнопка кличе новий debug-хук рушія `debugSetOrbit3(on)`:
- **увімкнення** — той самий роз'їзд і анонс, що й у грі;
- **вимкнення** — кільця сходяться назад, третє гасне (`ring3Alpha` → 0), сутності третього
  кільця зникають, м'яч із нього переходить на друге;
- перемикач діє й на паузі;
- після перемикача правило «з 30-ї секунди» до кінця рану мовчить;
- у наступних ранах перемикач тримається (`applyDebugFlags`).

На **мертвому** рані рушій не оновлюється: кнопка запам'ятає стан, а кільця поїдуть після
тапу-рестарту.

У грі нічого не змінилось. `ring3Alpha` гасне лише тоді, коли `ringCount` знову 2, а це
буває тільки після debug-вимкнення. Тест детермінізму проходить.

### Пунктир смикається — справжня причина: точність, а не швидкість

Телефон — **Mali-G57** (`dumpsys SurfaceFlinger`). На Mali `mediump` — справжні 16 біт.
`u_dashPhase` був `dashShift / period`, а `dashShift` накопичувався за весь ран без меж.
За хвилину гри це ~235 періодів. На такому числі 16-бітний float має крок 1/8 періоду,
тобто штрихи стрибають на ~9 px. Що довша гра, то сильніші ривки — рівно те, чого ти
боїшся.

Виправлення в двох місцях:
1. **Нічого не накопичується.** Поле віддає кільцю лише зсув **за цей кадр**
   (`dashStep`, після передачі — `0`). Кільце одразу згортає його у фазу
   `dashPhase ∈ [0, 1)` через `.mod(1f)`. Фаза рахується в частках періоду *поточного*
   кільця, тож під час роз'їзду ORBIT III пунктир теж не стрибає. Раніше він там
   прокручувався, бо велике `dashShift` ділилось на період, що змінювався.
2. **`highp` у шейдері** (з запасним `mediump`, як у `starFieldFS`). Навіть із фазою 0..1
   координата вздовж дуги доходить до ±23 періодів, а в `mediump` це крок 1/64 періоду:
   торці штрихів у русі тремтять на ~1 px.

### Інші кути, що росли без меж

- `AGem` / `ASpike`: `rotation += SPIN_SPEED·delta`. Актори з пулу живуть усю сесію,
  тож кут росте без кінця (шип — 648 000° за годину). Тепер `% 360f`.
- Також перевірено: `lastBallAngle` і кут рушія нормалізовані, `haloD` — лерп,
  `ring3Alpha` обмежений, `ShaderClock.time` уже згорнуто `% 100`.

**Не чіпав, але знайшов (скажи, чи братись):**
- `StarFieldClock.phase` (`AStarField.kt:35`) росте без меж і йде в `u_drift`. Шейдер там
  `highp`, тож ривків найближчі години не буде. Але хеш зір рахується з координати
  клітинки, і після дуже довгої сесії числа стануть великими. Просто взяти `%` не можна:
  перенесення на іншу клітинку змінить зорі, потрібен період, кратний сітці.
- `ShaderClock` згортається кожні 100 с. Мерехтіння `sin(u_time·f)` і цикл життя зір
  (`period` 25..70, не дільник 100) на стику стрибають. Це не лаг, а раз на 100 с зорі
  разом «перемикаються».

### Перевірено

- `sh gradlew :engine:test` — 6/6 зелених, серед них два нові: `debugOrbit3TogglesInPlace`
  і `debugOrbit3OffOutlivesThirtySecondRule`. Тест уже лежить у
  `engine/src/test/.../RunEngineTest.kt` і компілюється після кроку **4**.
- `assembleDebug` лабораторної копії з усіма змінами нижче — зелений.
- Телефон (1080×2400, 3 px/юніт), профіль від центру поля:
  - 2 кільця, м'яч на внутрішньому (95): край ореолу на 255 px = 85 → **170** ✓;
  - після ORBIT III: кільця на 193 / 335 / 478 px = 130 / 225 / 320 ✓, м'яч на
    зовнішньому, ореол 450 px = 150 → **300** ✓.
- Вимкнення ORBIT III на пристрої не клацав (телефон узяв ти) — його перевіряє тест.
- Плавність пунктиру по знімках не виміряти, це треба оцінити очима.

Порядок вставки: 1 → 7. Компілюється після **6**: `syncFrom` отримує `ballD`, `GameScreen`
кличе `debugSetOrbit3`.

---

## 1. `orbitRingFS.glsl`

**Файл:** `app/src/main/assets/shader/orbit/orbitRingFS.glsl`

### 1.1 Шапка — ЗАМІНИТИ

```glsl
#ifdef GL_ES
precision mediump float;
#endif
```

на

```glsl
// highp: координата вздовж дуги доходить до ±u_dashCount/2 (~23 періоди). У
// mediump (Mali — справжні 16 біт) це крок 1/64 періоду, і торці штрихів у русі
// тремтять на піксель.
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif
```

### 1.2 Юніформ — ЗАМІНИТИ коментар

```glsl
uniform float u_dashPhase; // зсув візерунка, у періодах
```

на

```glsl
uniform float u_dashPhase; // зсув візерунка, у періодах, 0..1
```

---

## 2. `OrbitRingEffect.kt` — ЗАМІНИТИ файл цілком

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/OrbitRingEffect.kt`

Що змінилось: `var dashShift` → `dashPhase` (0..1, private set) + `advanceDash()`;
`dashCount()` / `dashPeriod()` винесено, щоб `setUniforms` і `advanceDash` рахували період
однаково.

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect
import kotlin.math.PI
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// OrbitRingEffect — кільце орбіти (single-pass).
//
// Усі розміри — у world-юнітах групи, у частки квада переводяться тут через
// ctx.width. Радіус: за замовчуванням найбільший, що влазить у групу; з
// outerPad — рівно outerPad від краю квада (щоб glow і пунктир, які ширші за
// саму орбіту, не обрізались межею).
//
// active = true вмикає шари з Figma ring-outer-active: два glow і пунктир
// кольору м'яча (playerColor). Числа Figma живуть у AOrbitRing, тут — юніформи.
// ─────────────────────────────────────────────────────────────────────────────
class OrbitRingEffect : VfxEffect() {

    override val fragmentShader = "shader/orbit/orbitRingFS.glsl"

    /** Товщина орбіти у world-юнітах. */
    var thickness = 4f

    /** Колір орбіти. */
    val color: Color = Color(1f, 1f, 1f, 1f)

    /** null — радіус як завжди: найбільше коло, що влазить. Число — відступ осі орбіти від краю квада. */
    var outerPad: Float? = null

    // ── активна орбіта ──
    var active = false
    val playerColor: Color = Color(1f, 1f, 1f, 1f)
    var glow1Width = 0f;  var glow1Alpha = 0f
    var glow2Width = 0f;  var glow2Alpha = 0f
    /** Радіус пунктиру = радіус орбіти + це. */
    var dashOffsetR = 0f
    var dashWidth = 0f;   var dashAlpha = 0f
    var dashLen = 1f;     var dashGap = 1f
    /**
     * Фаза пунктиру в періодах штриха, ЗАВЖДИ 0..1. Сума зсувів за ран росла б
     * без меж, а в шейдері велике число — ступінчасте: пунктир смикався б тим
     * сильніше, чим довше гра. Тому зсув одразу згортається в частку періоду.
     */
    var dashPhase = 0f
        private set

    /**
     * Зсунути пунктир на [shift] world-юнітів дуги. Росте — проти годинникової.
     * Період — поточного кільця: під час роз'їзду він змінюється, а фаза в
     * частках періоду лишається на місці, без ривка.
     */
    fun advanceDash(shift: Float, width: Float, height: Float) {
        val dashR = radiusFor(width, height) + dashOffsetR
        dashPhase = (dashPhase + shift / dashPeriod(dashR)).mod(1f)
    }

    /** Штрихів по колу — ЦІЛЕ число, інакше на 180° був би шов із половинкою штриха. */
    private fun dashCount(dashR: Float): Int =
        ((2.0 * PI * dashR).toFloat() / (dashLen + dashGap)).roundToInt().coerceAtLeast(1)

    private fun dashPeriod(dashR: Float): Float = (2.0 * PI * dashR).toFloat() / dashCount(dashR)

    /** Радіус при поточному розмірі (world), без запасу на AA — для посадки об'єктів. */
    fun radiusFor(width: Float, height: Float): Float {
        val pad = outerPad
        return if (pad != null) minOf(width, height) * 0.5f - pad
        else (minOf(width, height) - thickness) * 0.5f
    }

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        val invW = 1f / ctx.width

        // Ширина згладжування у world-юнітах
        val aa = 1.5f

        // Без outerPad зовнішня грань лягала б рівно на межу квада, фейд
        // обрізався б, і коло зверху/знизу/зліва/справа виглядало б зрізаним.
        val radius = if (outerPad != null) radiusFor(ctx.width, ctx.height)
        else radiusFor(ctx.width, ctx.height) - aa

        shader.setUniformf("u_hOverW", ctx.height / ctx.width)
        shader.setUniformf("u_radius", radius * invW)
        shader.setUniformf("u_width",  thickness * invW)
        shader.setUniformf("u_aa",     aa * invW)
        shader.setUniformf("u_color",  color.r, color.g, color.b)

        shader.setUniformf("u_active", if (active) 1f else 0f)
        if (!active) return

        val dashR = radius + dashOffsetR

        shader.setUniformf("u_player",    playerColor.r, playerColor.g, playerColor.b)
        shader.setUniformf("u_glowW",     glow1Width * invW, glow2Width * invW)
        shader.setUniformf("u_glowA",     glow1Alpha, glow2Alpha)
        shader.setUniformf("u_dashR",     dashR * invW)
        shader.setUniformf("u_dashW",     dashWidth * invW)
        shader.setUniformf("u_dashA",     dashAlpha)
        shader.setUniformf("u_dashCount", dashCount(dashR).toFloat())
        shader.setUniformf("u_dashFill",  dashLen / (dashLen + dashGap))
        shader.setUniformf("u_dashPhase", dashPhase)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + thickness.toRawBits()
        k = k * 31 + color.toIntBits().toLong()
        k = k * 31 + (if (active) 1 else 0)
        if (active) {
            k = k * 31 + playerColor.toIntBits().toLong()
            k = k * 31 + dashPhase.toRawBits()
        }
        return k
    }
}
```

---

## 3. `AOrbitRing.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/orbit/AOrbitRing.kt`, після `outerPad`.

**ЗАМІНИТИ**

```kotlin
    /** Зсув пунктиру вздовж дуги (world). Росте — візерунок їде проти годинникової. */
    var dashShift: Float
        get() = fx.dashShift
        set(value) { fx.dashShift = value }
```

на

```kotlin
    /** Зсунути пунктир на [shift] world-юнітів дуги. Росте — проти годинникової. Фаза сама згортається в 0..1. */
    fun advanceDash(shift: Float) = fx.advanceDash(shift, width, height)
```

---

## 4. `RunEngine.kt`

**Файл:** `engine/src/main/kotlin/com/lewydo/orbitdash/engine/RunEngine.kt`

### 4.1 `update()` — ЗАМІНИТИ

```kotlin
        // ORBIT III вмикається з 30-ї секунди — посеред рану, звідси й лерп
        if (config.orbit3 && ringCount == 2 && time >= 30f) {
            ringCount = 3
            target    = LAYOUT_3
            announceT = 1.8f
            listener?.onOrbit3Online()
        }
        for (i in 0 until 3) ringR[i] += (target[i] - ringR[i]) * min(1f, 2.5f * wdt)
        if (ringCount == 3) ring3Alpha = min(1f, ring3Alpha + 1.5f * dt)
```

на

```kotlin
        // ORBIT III вмикається з 30-ї секунди — посеред рану, звідси й лерп.
        // Debug-перемикач забирає це рішення собі до кінця рану
        if (!debugOrbit3Set && config.orbit3 && ringCount == 2 && time >= 30f) orbit3Online()
        stepRings(dt, wdt)
```

### 4.2 Debug-блок — ДОДАТИ після `var debugFrozen = false`

```kotlin

    /** true — третю орбіту вже перемкнув debug, правило «з 30-ї секунди» мовчить. */
    private var debugOrbit3Set = false

    /**
     * DEBUG: третя орбіта зараз, без 30-ї секунди. Увімкнення — той самий
     * роз'їзд і анонс, що й у грі. Вимкнення — кільця сходяться назад, третє
     * гасне, його сутності зникають, м'яч із нього переходить на друге.
     */
    fun debugSetOrbit3(on: Boolean) {
        debugOrbit3Set = true
        if (on == (ringCount == 3)) return

        if (on) { orbit3Online(); return }

        ringCount = 2
        target    = LAYOUT_2
        _entities.removeAll { it.ring == 2 }
        if (ringIndex == 2) ringIndex = 1   // напрям виправить tap(): на краю він розвертається
    }
```

### 4.3 `updateFrozen()` — ЗАМІНИТИ рядок

```kotlin
        for (i in 0 until 3) ringR[i] += (target[i] - ringR[i]) * min(1f, 2.5f * dt)
```

(той, що **в `updateFrozen`**, одразу під `radius += …`) на

```kotlin
        stepRings(dt, dt)
```

### 4.4 ДОДАТИ перед `private fun applyBoost(`

```kotlin
    private fun orbit3Online() {
        ringCount = 3
        target    = LAYOUT_3
        announceT = 1.8f
        listener?.onOrbit3Online()
    }

    /**
     * Роз'їзд кілець (час світу) і проявлення третього (реальний час). Третє
     * гасне назад лише після debug-вимкнення — у грі орбіта не зникає.
     */
    private fun stepRings(dt: Float, wdt: Float) {
        for (i in 0 until 3) ringR[i] += (target[i] - ringR[i]) * min(1f, 2.5f * wdt)
        ring3Alpha = if (ringCount == 3) min(1f, ring3Alpha + 1.5f * dt)
                     else max(0f, ring3Alpha - 1.5f * dt)
    }

```

---

## 5. `AOrbitField.kt` — ЗАМІНИТИ файл цілком

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/orbit/AOrbitField.kt`

Що змінилось: `HALO_SIZE` → `HALO_GAP` + `HALO_LERP`; `dashShift` → `dashStep` (обнуляється
в `syncRings`); `haloD` + `applyHaloSize()`; `syncFrom(…, ballD)`; невикористаний
`import …ui.Image` прибрано.

```kotlin
package com.lewydo.orbitdash.game.actors.orbit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

// ═════════════════════════════════════════════════════════════════════════════
//  AOrbitField — ігрове поле: кільця + посадка об'єктів на них.
//
//  ОДНА СИСТЕМА ЧИСЕЛ. sizeScaler з DESIGN_W = 320, тобто дизайн-розмір поля
//  дорівнює ДІАМЕТРУ ЗОВНІШНЬОЇ ОРБІТИ — поле обрізане рівно по ній. Тому всі
//  числа в цьому файлі ті самі, що у Figma-мокапі (360×800).
//
//  РОЗКЛАДКИ ЗАДАНІ В ДІАМЕТРАХ, бо саме так вони підписані в дизайні.
//  На радіус ділимо в одному місці — там, де цього вимагає тригонометрія.
//
//  ДВА РЕЖИМИ:
//   • автономний — поле саме лерпає кільця (меню, превʼю, дебаг);
//   • ВЕДЕНИЙ (driven) — радіуси приходять з RunEngine через syncFrom().
//     Під час рану джерело правди ОДНЕ: колізії рахуються по тих самих
//     радіусах, що малюються. Два незалежні лерпи розійшлись би фазами —
//     гравець бачив би кільце в одному місці, а вмирав у іншому.
//
//  ЩО ПОЛЕ ЗНАЄ: геометрію. ЩО НЕ ЗНАЄ: правил гри.
// ═════════════════════════════════════════════════════════════════════════════
class AOrbitField(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, DESIGN_W)

    companion object {
        /** Дизайн-розмір поля = діаметр зовнішньої орбіти. */
        const val DESIGN_W = 320f

        /**
         * Ореол під кільцями: диск кольору м'яча на HALO_GAP всередині орбіти
         * м'яча (по радіусу): 190 → 170, 320 → 300, у трьох кільцях 130 → 110.
         */
        private const val HALO_GAP   = 10f
        private const val HALO_ALPHA = 0.05f
        /** Без м'яча (автономний режим) ореол лерпає до активного кільця — як радіус м'яча в рушії. */
        private const val HALO_LERP  = 14f

        const val MAX_RINGS = 3

        /** Діаметр зовнішньої орбіти. Він же — парковка для неактивного кільця. */
        private const val D_OUTER = 320f

        /**
         * ДІАМЕТРИ кілець (design), зсередини назовні.
         *
         * У LAYOUT_2 третє паркується на D_OUTER — рівно під зовнішнім. Тоді
         * активація ORBIT III читається як «зовнішня орбіта розділилась»:
         * третє проявляється на місці, а перші два стискаються всередину.
         */
        private val LAYOUT_2 = floatArrayOf(190f, D_OUTER, D_OUTER)
        private val LAYOUT_3 = floatArrayOf(130f, 225f,    D_OUTER)

        // Figma: ring-inner 3, ring-outer-active 4.5
        private const val RING_THICKNESS     = 3f
        private const val RING_THICKNESS_ACT = 4.5f

        /**
         * Активна орбіта = кільце теми, яскравіше. ×1.9 — саме так у Figma NEON:
         * 2B3060 → 525AB6 покомпонентно. Для решти тем виводиться тим самим
         * множником, окремого кольору в палітрі не треба.
         */
        private const val ACTIVE_BRIGHTEN = 1.9f

        /**
         * Запас квада кільця назовні від осі орбіти — під glow і пунктир
         * активної (≥ AOrbitRing.ACTIVE_REACH). Тому поле 320 малює до
         * 320 + 2·9 = 338 — рівно як фрейм OrbitField у Figma.
         */
        private const val RING_PAD = 14f

        /**
         * Пунктир активної орбіти їде за м'ячем: 0.9 px прототипу на градус
         * (st.dashOff -= v·wdt·0.9), px прототипу = 2 design поля → 0.45.
         */
        private const val DASH_FOLLOW = 0.45f
        /** Без м'яча (меню, превʼю) — повільно сам, design-юнітів дуги за секунду. */
        private const val DASH_IDLE_SPEED = -25f

        /** Лерп автономного режиму. У driven швидкість задає рушій. */
        private const val LAYOUT_LERP = 2.5f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aHaloImg = AMsdfImage(screen, gdxGame.assetsMsdf.circle_msdf).apply { color.a = HALO_ALPHA }
    private val rings    = Array(MAX_RINGS) { AOrbitRing(screen) }

    // ------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------
    private val diameters = LAYOUT_2.copyOf()
    private var target    = LAYOUT_2

    /** true — радіуси приходять ззовні (RunEngine), власний лерп мовчить. */
    private var driven = false

    var ringCount = 2
        private set

    var activeRing = 0
        set(value) { field = value.coerceIn(0, MAX_RINGS - 1) }

    /** Проявлення третього кільця 0→1. У driven приходить з рушія. */
    var ring3Alpha = 0f

    /**
     * Зсув пунктиру за ЦЕЙ кадр, design. syncRings() віддає його кільцю й
     * обнуляє — сума за ран ніде не накопичується, фазу тримає кільце в 0..1.
     */
    private var dashStep = 0f
    /** Кут м'яча з минулого syncFrom — щоб пунктир їхав за ним, а не за часом. */
    private var lastBallAngle: Float? = null

    /** Діаметр ореолу, design. У driven — від орбіти м'яча, інакше лерп до активного кільця. */
    private var haloD = LAYOUT_2[0] - 2f * HALO_GAP

    /** Колір активної орбіти: кільце теми × ACTIVE_BRIGHTEN. Один об'єкт, без алокацій у кадрі. */
    private val activeRingColor = Color()

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addHaloImg()

        for (i in 0 until MAX_RINGS) {
            addActor(rings[i])
            applyRingSize(i)
        }
        syncRings()
    }

    override fun act(delta: Float) {
        super.act(delta)

        if (!driven) {
            for (i in 0 until MAX_RINGS) {
                val diff = target[i] - diameters[i]
                if (MathUtils.isZero(diff, 0.1f)) {
                    if (diameters[i] != target[i]) { diameters[i] = target[i]; applyRingSize(i) }
                } else {
                    diameters[i] += diff * MathUtils.clamp(LAYOUT_LERP * delta, 0f, 1f)
                    applyRingSize(i)
                }
            }
            ring3Alpha = if (ringCount == 3) MathUtils.clamp(ring3Alpha + 1.5f * delta, 0f, 1f) else 0f
            dashStep += DASH_IDLE_SPEED * delta

            val haloTarget = diameters[activeRing] - 2f * HALO_GAP
            haloD += (haloTarget - haloD) * MathUtils.clamp(HALO_LERP * delta, 0f, 1f)
            applyHaloSize()
        }

        syncRings()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (rings[0].parent != null) {
            applyHaloSize()
            for (i in 0 until MAX_RINGS) applyRingSize(i)
        }
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addHaloImg() {
        addActor(aHaloImg)
        applyHaloSize()
    }

    // ------------------------------------------------------------------------
    // Public API · керування ззовні
    // ------------------------------------------------------------------------

    /**
     * Прийняти геометрію від рушія. [ringR] — діаметри в одиницях, які
     * збігаються з design поля (див. RunEngine: одиниці прототипу = діаметри).
     * [ballD] — діаметр орбіти м'яча в тих самих одиницях (engine.radius):
     * ореол іде за м'ячем, а не за кільцем — разом зі слоу-мо й паузою.
     */
    fun syncFrom(ringR: FloatArray, count: Int, active: Int, r3Alpha: Float, ballAngle: Float, ballD: Float) {
        driven = true

        for (i in 0 until MAX_RINGS) diameters[i] = ringR[i]
        ringCount  = count.coerceIn(2, MAX_RINGS)
        activeRing = active
        ring3Alpha = r3Alpha

        // Пунктир їде за м'ячем: приріст кута → зсув дуги. Через різницю, бо
        // кут нормалізований 0..360 і на стику стрибає
        lastBallAngle?.let { prev ->
            val delta = ((ballAngle - prev) % 360f + 540f) % 360f - 180f
            dashStep += DASH_FOLLOW * delta
        }
        lastBallAngle = ballAngle

        haloD = ballD - 2f * HALO_GAP

        if (rings[0].parent != null) {
            applyHaloSize()
            for (i in 0 until MAX_RINGS) applyRingSize(i)
        }
    }

    /** Повернути полю самостійність (превʼю, меню). */
    fun releaseDriven() { driven = false; lastBallAngle = null }

    // ------------------------------------------------------------------------
    // Public API · геометрія
    // ------------------------------------------------------------------------

    fun diameterOf(index: Int): Float = diameters[index.coerceIn(0, MAX_RINGS - 1)]

    fun radiusOf(index: Int): Float = diameterOf(index) * 0.5f

    /**
     * Посадити актора на кільце під кутом.
     * angleDeg: 0 = праворуч, 90 = вгору, проти годинникової (математична).
     */
    fun positionOn(actor: Actor, ringIndex: Int, angleDeg: Float) =
        positionAt(actor, radiusOf(ringIndex), angleDeg)

    /**
     * Довільний радіус — для переходів між кільцями й сутностей рушія.
     * Позиціонуємо за ЦЕНТРОМ актора: у об'єктів різні розміри.
     */
    fun positionAt(actor: Actor, radiusDesign: Float, angleDeg: Float) {
        val rad = angleDeg * MathUtils.degreesToRadians
        val r   = radiusDesign.toActual
        actor.setPosition(
            width  / 2f + MathUtils.cos(rad) * r,
            height / 2f + MathUtils.sin(rad) * r,
            Align.center,
        )
    }

    fun centerX() = width / 2f
    fun centerY() = height / 2f

    // ------------------------------------------------------------------------
    // Public API · розкладка (автономний режим)
    // ------------------------------------------------------------------------
    fun setRingCount(count: Int) {
        val c = count.coerceIn(2, MAX_RINGS)
        if (c == ringCount) return
        ringCount = c
        target    = if (c >= 3) LAYOUT_3 else LAYOUT_2
    }

    fun setRingCountInstant(count: Int) {
        setRingCount(count)
        target.copyInto(diameters)
        if (rings[0].parent != null) for (i in 0 until MAX_RINGS) applyRingSize(i)
    }

    // ------------------------------------------------------------------------
    // Rings
    // ------------------------------------------------------------------------

    /**
     * Радіус AOrbitRing виводиться з розміру: «поставити діаметр» = «задати
     * сторону квадрата» = діаметр + 2·RING_PAD, а вісь орбіти — рівно на
     * RING_PAD від краю (outerPad). Так glow і пунктир мають куди лягти.
     */
    private fun applyRingSize(index: Int) {
        val ring = rings[index]
        val side = diameters[index] + 2f * RING_PAD
        ring.setSizeScaled(side, side)
        ring.outerPad = RING_PAD.toActual
        ring.setPosition(width / 2f, height / 2f, Align.center)
    }

    /** Ореол — той самий підхід, що й кільця: розмір живий, тому не через констрейнт. */
    private fun applyHaloSize() {
        aHaloImg.setSizeScaled(haloD, haloD)
        aHaloImg.setPosition(width / 2f, height / 2f, Align.center)
    }

    private fun syncRings() {
        val theme = ThemeManager.current
        aHaloImg.setColorRGB(theme.player)
        activeRingColor.set(theme.ring).mul(ACTIVE_BRIGHTEN).clamp()

        for (i in 0 until MAX_RINGS) {
            val ring   = rings[i]
            val active = i == activeRing

            ring.ringColor = if (active) activeRingColor else theme.ring
            ring.thickness = (if (active) RING_THICKNESS_ACT else RING_THICKNESS).toActual

            if (active) {
                ring.setActive(theme.player, sizeScaler.factor)
                ring.advanceDash(dashStep.toActual)
            } else ring.setInactive()

            // Третє кільце проявляється, решта завжди видимі
            ring.color.a = if (i < 2) 1f else ring3Alpha
        }
        dashStep = 0f
    }

}
```

---

## 6. `GameScreen.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt`

### 6.1 `aDebugPanel` — ЗАМІНИТИ пункт ORBIT III

```kotlin
            ADebugPanel.Item("ORBIT III") { btn ->
                debugOrbit3 = !debugOrbit3
                btn.label.setText(if (debugOrbit3) "O3: ON" else "ORBIT III")
                startRun()
            },
```

на

```kotlin
            ADebugPanel.Item("ORBIT III") { btn ->
                // Третя орбіта в ЦЬОМУ рані, одразу — з тим самим роз'їздом, що й у грі.
                // Тримається й у наступних ранах, поки не вимкнеш
                debugOrbit3 = !debugOrbit3
                engine.debugSetOrbit3(debugOrbit3)
                btn.label.setText(if (debugOrbit3) "O3: ON" else "ORBIT III")
            },
```

### 6.2 `applyDebugFlags()` — ЗАМІНИТИ

```kotlin
    private fun applyDebugFlags() {
        engine.debugFrozen = debugPaused
```

на

```kotlin
    private fun applyDebugFlags() {
        engine.debugFrozen = debugPaused
        if (debugOrbit3) engine.debugSetOrbit3(true)
```

### 6.3 `syncField()` — ЗАМІНИТИ

```kotlin
            ballAngle = -engine.angle,   // той самий переклад Y-вниз → Y-вгору, що й у syncPlayer
        )
```

на

```kotlin
            ballAngle = -engine.angle,   // той самий переклад Y-вниз → Y-вгору, що й у syncPlayer
            ballD     = engine.radius,   // радіус рушія = діаметр поля (TO_FIELD = 0.5)
        )
```

`orbit3 = debugOrbit3` у `startRun()` лишається: з увімкненим перемикачем правило 30-ї
секунди однаково мовчить.

---

## 7. Кути гема й шипа — `% 360`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/AGem.kt`, `act()`.

**ЗАМІНИТИ**

```kotlin
        aGem.rotation += SPIN_SPEED * delta
```

на

```kotlin
        aGem.rotation = (aGem.rotation + SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж
```

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ASpike.kt`, `act()`.

**ЗАМІНИТИ**

```kotlin
        aShape.rotation += SPIN_SPEED * delta
```

на

```kotlin
        aShape.rotation = (aShape.rotation + SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж
```

---

## Перевірка

`./gradlew assembleDebug`, потім на пристрої:
- ореол на внутрішній орбіті менший за неї на 10 з боку, на зовнішній — так само, і
  перелітає разом із м'ячем;
- ORBIT III: кільця одразу роз'їжджаються, третє проявляється; повторний тап — сходяться,
  третє гасне;
- пунктир їде рівно і через хвилину-дві гри (раніше саме тоді ривки ставали помітними).
