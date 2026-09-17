# 34 — Активна орбіта як у Figma: яскравіше кільце теми, два glow і пунктир, що їде за м'ячем

## Що і навіщо

Зараз активна орбіта — це просто товстіша лінія кольору м'яча. У Figma (`ring-outer-active`,
фрейм `OrbitField`) вона складена з чотирьох шарів, знизу вгору:

| шар | що | Figma |
|---|---|---|
| `glow2` | обводка кольору м'яча | товщина 14, alpha 0.08 |
| `glow1` | обводка кольору м'яча | товщина 10, alpha 0.14 |
| `orbit` | сама орбіта | `#525AB6`, товщина 4.5 |
| `current` | пунктир кольору м'яча на **9 назовні** від орбіти | товщина 2, alpha 0.5, штрих 8 / пропуск 15 |

Неактивне кільце (`ring-inner`): `#2B3060`, товщина 3.

**`#525AB6` — не новий колір, а кільце теми ×1.9.** `2B3060 → 525AB6` покомпонентно:
82/43 = 1.91, 90/48 = 1.88, 182/96 = 1.90. Тому в палітру нічого не додаємо: активна орбіта
кожної теми — її ж `ring`, помножений на `ACTIVE_BRIGHTEN = 1.9`. Прототип робив те саме з
2.2 (`brighten(p.ring, 2.2)`); беру 1.9, бо саме це число стоїть у Figma.

**Пунктир їде за м'ячем, як у прототипі:** `st.dashOff -= v·wdt·0.9` — 0.9 px на кожен
градус кута м'яча, у той самий бік. Піксель прототипу = 2 design поля, тому в нас
`DASH_FOLLOW = 0.45` design-юнітів дуги на градус. Стоїть м'яч (`PAUSE`) — стоїть і пунктир.
У меню й прев'ю (автономний режим) пунктир крутиться повільно сам.

**Один прохід, нуль FBO.** Усі чотири шари концентричні, тож малюються тим самим шейдером,
що й досі, — просто з більшою кількістю юніформів. Шари компонуються як «over» зі
straight-альфою, тобто рівно так, як Figma накладає обводки: у перетині двох glow виходить
`0.14 + 0.08·(1 − 0.14) = 0.209`. Неактивне кільце — та сама гілка, що й була, зайвого не
рахує.

### Перевірено на телефоні (лабораторна збірка, 720×1650, 2 px на design-юніт)

Радіальний профіль через активне кільце, піксель у піксель:

| що | заміряно | Figma |
|---|---|---|
| колір орбіти | `(82, 91, 182)` = `#525AB6` | `#525AB6` ✓ |
| товщина орбіти | 9 px | 4.5 ✓ |
| glow2 над фоном | `(13, 33, 53)` | `0E1024` + cyan 0.08 → `(13, 33, 53)` ✓ |
| glow1 + glow2 | `(11, 60, 82)` | alpha 0.209 → `(11, 60, 82)` ✓ |
| ширина glow2 / glow1 | 28 / 20 px | 14 / 10 ✓ |
| неактивне кільце | `(43, 48, 96)` = `#2B3060`, 6 px | `#2B3060`, 3 ✓ |

Два кадри через 0.25 с — штрихи на правому боці кільця пішли вниз, тобто **за годинниковою,
у бік м'яча** ✓.

> **Хибний замір.** На пристрої пунктир ішов проти м'яча: `v_localUV` має Y униз, і
> `atan(p.y, p.x)` крутився за годинниковою. Виправлено в патчі 35. Меню (емблема) не змінилось: `AOrbitEmblem` користується кільцями як раніше.

### Чого свідомо не робив

- **Пульсацію товщини з прототипу** (`1 + 0.06·sin`) і його тонку лінію на `r − 8` — у Figma
  їх немає, а ти сказав «як у Figma».
- **Емблему в меню не чіпав.** Якщо захочеш там той самий стиль на внутрішньому кільці —
  один рядок: `aRingInner.setActive(theme.player, sizeScaler.factor)`.
- **Поле лишається 320.** Пунктир виходить на 9 назовні, тобто кільце малює до 338 — рівно
  як фрейм `OrbitField` у Figma (338). Нічого не обрізається: групи не кліпають дітей.

Порядок вставки: 1 → 2 → 3 → 4 → 5. Компілюється після **5**: `syncFrom` отримує новий
параметр, і `GameScreen` мусить його передати.

---

## 1. `orbitRingFS.glsl` — ЗАМІНИТИ файл цілком

**Файл:** `app/src/main/assets/shader/orbit/orbitRingFS.glsl`

```glsl
#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// ORBIT RING — кільце орбіти, один прохід.
//
// Неактивне: одна обводка u_color. Активне (u_active = 1) — чотири концентричні
// шари, як у Figma ring-outer-active, знизу вгору:
//   glow2  кольору м'яча, ширша,  прозоріша
//   glow1  кольору м'яча, вужча,  щільніша
//   orbit  u_color (яскравіше кільце теми)
//   dash   пунктир кольору м'яча на u_dashR, обертається (u_dashPhase)
//
// Шари компонуються як «over» зі straight-альфою — те саме, що робить Figma з
// накладеними обводками. Усі довжини — у частках ширини квада, тому картинка
// не залежить від розміру групи.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;      // колір/альфа актора (фейди груп працюють самі)
varying vec2 v_localUV;    // 0..1 у межах регіону (дає BATCH_VERT)

uniform float u_hOverW;    // height/width квада — аспект-корекція
uniform float u_radius;    // радіус орбіти
uniform float u_width;     // товщина орбіти
uniform float u_aa;        // згладжування країв
uniform vec3  u_color;     // колір орбіти

uniform float u_active;    // 0 — лише орбіта; 1 — glow і пунктир
uniform vec3  u_player;    // колір м'яча: glow і пунктир
uniform vec2  u_glowW;     // товщини glow1, glow2
uniform vec2  u_glowA;     // їхні альфи
uniform float u_dashR;     // радіус пунктиру
uniform float u_dashW;     // товщина пунктиру
uniform float u_dashA;     // альфа пунктиру
uniform float u_dashCount; // штрихів по колу — ЦІЛЕ, інакше шов на 180°
uniform float u_dashFill;  // частка періоду під штрихом
uniform float u_dashPhase; // зсув візерунка, у періодах

const float TWO_PI = 6.2831853;

/** Покриття обводки товщиною w на відстані d від її осі: фейд симетричний,
 *  половина всередину, половина назовні — товщина лишається рівно w. */
float stroke(float d, float w) {
    float halfW = w * 0.5;
    return 1.0 - smoothstep(halfW - u_aa * 0.5, halfW + u_aa * 0.5, d);
}

void main() {
    vec2 p = v_localUV - vec2(0.5);
    p.y *= u_hOverW;                       // відстань стає пропорційною світу

    float r = length(p);
    float d = abs(r - u_radius);           // відстань до осі орбіти

    float aOrbit = stroke(d, u_width);
    vec3  col = u_color * aOrbit;          // premultiplied, поки компонуємо
    float a   = aOrbit;

    if (u_active > 0.5) {
        // glow ПІД орбітою: два шари одного кольору, «over» дає у перетині
        // a1 + a2·(1−a1) — рівно як накладені обводки у Figma
        float g2 = stroke(d, u_glowW.y) * u_glowA.y;
        float g1 = stroke(d, u_glowW.x) * u_glowA.x;
        float ga = g1 + g2 * (1.0 - g1);
        col = u_color * aOrbit + u_player * ga * (1.0 - aOrbit);
        a   = aOrbit + ga * (1.0 - aOrbit);

        // пунктир ЗВЕРХУ. Координата вздовж дуги — у періодах штриха; AA по дузі
        // переводимо з u_aa через довжину періоду, щоб торці штрихів були
        // такі ж м'які, як краї кільця
        float ring   = stroke(abs(r - u_dashR), u_dashW);
        float turns  = atan(p.y, p.x) / TWO_PI;              // −0.5..0.5 оберту
        float s      = fract(turns * u_dashCount - u_dashPhase);
        float perLen = TWO_PI * u_dashR / u_dashCount;
        float e      = u_aa / perLen;
        float dash   = smoothstep(0.0, e, s) * (1.0 - smoothstep(u_dashFill - e, u_dashFill, s));
        float da     = ring * dash * u_dashA;
        col = u_player * da + col * (1.0 - da);
        a   = da + a * (1.0 - da);
    }

    // назад у straight-альфу — так чекає SpriteBatch
    gl_FragColor = vec4(col / max(a, 1e-4), a) * v_color;
}
```

---

## 2. `OrbitRingEffect.kt` — ЗАМІНИТИ файл цілком

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/OrbitRingEffect.kt`

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
    /** Зсув пунктиру вздовж дуги, world-юніти. Росте — візерунок їде проти годинникової. */
    var dashShift = 0f

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

        // Штрихів по колу — ЦІЛЕ число: період підлаштовується, інакше на 180°
        // був би шов із половинкою штриха
        val dashR  = radius + dashOffsetR
        val circ   = (2.0 * PI * dashR).toFloat()
        val count  = (circ / (dashLen + dashGap)).roundToInt().coerceAtLeast(1)
        val period = circ / count

        shader.setUniformf("u_player",    playerColor.r, playerColor.g, playerColor.b)
        shader.setUniformf("u_glowW",     glow1Width * invW, glow2Width * invW)
        shader.setUniformf("u_glowA",     glow1Alpha, glow2Alpha)
        shader.setUniformf("u_dashR",     dashR * invW)
        shader.setUniformf("u_dashW",     dashWidth * invW)
        shader.setUniformf("u_dashA",     dashAlpha)
        shader.setUniformf("u_dashCount", count.toFloat())
        shader.setUniformf("u_dashFill",  dashLen / (dashLen + dashGap))
        shader.setUniformf("u_dashPhase", dashShift / period)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + thickness.toRawBits()
        k = k * 31 + color.toIntBits().toLong()
        k = k * 31 + (if (active) 1 else 0)
        if (active) {
            k = k * 31 + playerColor.toIntBits().toLong()
            k = k * 31 + dashShift.toRawBits()
        }
        return k
    }
}
```

---

## 3. `AOrbitRing.kt` — ЗАМІНИТИ файл цілком

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/orbit/AOrbitRing.kt`

Числа з Figma живуть тут, у `companion` — одне місце. `setActive(player, k)` переводить їх
у world через `k` = `sizeScaler.factor` групи.

```kotlin
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

    /** Зсув пунктиру вздовж дуги (world). Росте — візерунок їде проти годинникової. */
    var dashShift: Float
        get() = fx.dashShift
        set(value) { fx.dashShift = value }

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
```

---

## 4. `AOrbitField.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/orbit/AOrbitField.kt`

### 4.1 Імпорт

**ЗАМІНИТИ**

```kotlin
import com.badlogic.gdx.math.MathUtils
```

на

```kotlin
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
```

### 4.2 `companion object` — товщини й нові константи

**ЗАМІНИТИ**

```kotlin
        private const val RING_THICKNESS     = 3f
        private const val RING_THICKNESS_ACT = 4f
```

на

```kotlin
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
```

### 4.3 Стан — після `ring3Alpha`

**ЗАМІНИТИ**

```kotlin
    /** Проявлення третього кільця 0→1. У driven приходить з рушія. */
    var ring3Alpha = 0f
```

на

```kotlin
    /** Проявлення третього кільця 0→1. У driven приходить з рушія. */
    var ring3Alpha = 0f

    /** Зсув пунктиру активної орбіти вздовж дуги, design. Накопичується — не нормалізується. */
    private var dashShift = 0f
    /** Кут м'яча з минулого syncFrom — щоб пунктир їхав за ним, а не за часом. */
    private var lastBallAngle: Float? = null

    /** Колір активної орбіти: кільце теми × ACTIVE_BRIGHTEN. Один об'єкт, без алокацій у кадрі. */
    private val activeRingColor = Color()
```

### 4.4 `act()` — автономний режим крутить пунктир сам

**ЗАМІНИТИ**

```kotlin
            ring3Alpha = if (ringCount == 3) MathUtils.clamp(ring3Alpha + 1.5f * delta, 0f, 1f) else 0f
        }

        syncRings()
```

на

```kotlin
            ring3Alpha = if (ringCount == 3) MathUtils.clamp(ring3Alpha + 1.5f * delta, 0f, 1f) else 0f
            dashShift += DASH_IDLE_SPEED * delta
        }

        syncRings()
```

### 4.5 `syncFrom()` — новий параметр `ballAngle`

**ЗАМІНИТИ**

```kotlin
    fun syncFrom(ringR: FloatArray, count: Int, active: Int, r3Alpha: Float) {
        driven = true

        for (i in 0 until MAX_RINGS) diameters[i] = ringR[i]
        ringCount  = count.coerceIn(2, MAX_RINGS)
        activeRing = active
        ring3Alpha = r3Alpha
```

на

```kotlin
    fun syncFrom(ringR: FloatArray, count: Int, active: Int, r3Alpha: Float, ballAngle: Float) {
        driven = true

        for (i in 0 until MAX_RINGS) diameters[i] = ringR[i]
        ringCount  = count.coerceIn(2, MAX_RINGS)
        activeRing = active
        ring3Alpha = r3Alpha

        // Пунктир їде за м'ячем: приріст кута → зсув дуги. Через різницю, бо
        // кут нормалізований 0..360 і на стику стрибає
        lastBallAngle?.let { prev ->
            val delta = ((ballAngle - prev) % 360f + 540f) % 360f - 180f
            dashShift += DASH_FOLLOW * delta
        }
        lastBallAngle = ballAngle
```

### 4.6 `releaseDriven()`

**ЗАМІНИТИ**

```kotlin
    fun releaseDriven() { driven = false }
```

на

```kotlin
    fun releaseDriven() { driven = false; lastBallAngle = null }
```

### 4.7 `applyRingSize()` + `syncRings()` — обидві функції цілком

**ЗАМІНИТИ**

```kotlin
    /**
     * Радіус AOrbitRing виводиться з розміру, тому «поставити діаметр» =
     * «задати сторону квадрата»: діаметр + обводка (вона малюється всередину).
     */
    private fun applyRingSize(index: Int) {
        val ring = rings[index]
        val side = diameters[index] + RING_THICKNESS_ACT
        ring.setSizeScaled(side, side)
        ring.setPosition(width / 2f, height / 2f, Align.center)
    }

    private fun syncRings() {
        val theme = ThemeManager.current
        for (i in 0 until MAX_RINGS) {
            val ring   = rings[i]
            val active = i == activeRing

            ring.ringColor = if (active) theme.player else theme.ring
            ring.thickness = (if (active) RING_THICKNESS_ACT else RING_THICKNESS).toActual

            // Третє кільце проявляється, решта завжди видимі
            ring.color.a = if (i < 2) 1f else ring3Alpha
        }
    }
```

на

```kotlin
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

    private fun syncRings() {
        val theme = ThemeManager.current
        activeRingColor.set(theme.ring).mul(ACTIVE_BRIGHTEN).clamp()

        for (i in 0 until MAX_RINGS) {
            val ring   = rings[i]
            val active = i == activeRing

            ring.ringColor = if (active) activeRingColor else theme.ring
            ring.thickness = (if (active) RING_THICKNESS_ACT else RING_THICKNESS).toActual

            if (active) {
                ring.setActive(theme.player, sizeScaler.factor)
                ring.dashShift = dashShift.toActual
            } else ring.setInactive()

            // Третє кільце проявляється, решта завжди видимі
            ring.color.a = if (i < 2) 1f else ring3Alpha
        }
    }
```

---

## 5. `GameScreen.kt` — `syncField()`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt`

**ЗАМІНИТИ**

```kotlin
            active  = engine.ringIndex,
            r3Alpha = engine.ring3Alpha,
        )
```

на

```kotlin
            active  = engine.ringIndex,
            r3Alpha = engine.ring3Alpha,
            ballAngle = -engine.angle,   // той самий переклад Y-вниз → Y-вгору, що й у syncPlayer
        )
```

---

## Перевірка

Збірка + пристрій: активне кільце — індиго з двома шарами світіння кольору м'яча й пунктиром
назовні; пунктир повзе в бік м'яча, з `PAUSE` стоїть; неактивне кільце — темне, тонке.
Меню має виглядати як раніше.
