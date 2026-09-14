# 23 — Блюр: одна пара проходів, піраміда до σ ≈ 12, кубічний апскейл

Поглинає нічого — доповнює патч 18 (`BlurEffect(blur)` у юнітах, авто-density, піраміда)
і патч «density у VfxGroup» того ж дня (§11 картки сесії 13.09).

## Що і навіщо

Стрес на пристрої: 8 × `ABlur(140×140, blur 30)` з рухомою дитиною — **20 FPS, ~178 draw**,
і рівно стільки ж на буфері в 16 разів меншому. Ціна живого блюру на тайловому GPU —
**кількість проходів**, не пікселі: кожен бліт = перемикання render target + ~8 GL-викликів
через JNI ≈ 0.19 мс. А проходів було 18–26 на групу, бо σ = 12 текселів набиралась
повторами 9-tap ядра з кроком ≤ 2 (`passes = ceil((σ/3.378)²)`).

Що зроблено — три речі, усі всередині `BlurEffect`, API не змінився:

1. **Повне ядро за один прохід.** Ваги й зсуви під точну σ рахує CPU (`buildKernel`),
   шейдер отримує масиви. Пари сусідніх текселів — одним білінійним семплом на дробовому
   зсуві (linear sampling): та сама дискретна згортка, вдвічі менше fetch-ів. 37 семплів =
   ±36 текселів = 3σ при σ = 12. **Одна пара H+V замість 9–13.**
2. **Піраміда до σ ≤ 12 текселів** (`SIGMA_WORK_MAX`): буфер ділиться ½ (box 2×2 лінійним
   фільтром), блюр на дні однією парою, назад — **кубічним B-сплайном** (новий
   `upsampleCubicFS.glsl`, 4 білінійні семпли). Білінійний апскейл був причиною «зламів
   нахилу» при σ < 12 на дні; B-сплайн C¹-гладкий.
3. **`highp` для координат** у шейдері блюру: на буфері 480 px `mediump` (fp16) давав
   похибку ~0.5 px.

Ціна на групу: 1 (діти) + 0–3 (вниз) + 2 (H+V) + 0–1 (угору) + 1 (результат) = 4–7 draw
замість ~22. `VfxTexture` з авто-density: 2 бліти на запікання замість 26.

## Файли

| файл | що |
|---|---|
| `assets/shader/base/blur/gaussianBlurFS.glsl` | ЗАМІНЕНО — ядро з масивів `u_offset[18]` / `u_weight[18]`, `u_center`, `u_pairs`, `u_texelStep` |
| `assets/shader/base/copy/upsampleCubicFS.glsl` | НОВИЙ — кубічний B-сплайн через 4 білінійні семпли (Sigg & Hadwiger, GPU Gems 2 гл. 20) |
| `utils/vfx/effects/base/BlurEffect.kt` | ЗАМІНЕНО — `buildKernel()`, одна пара `blurHV()`, піраміда + апскейл; константи `SIGMA_WORK_MAX = 12`, `KERNEL_SIGMAS = 3`, `MAX_PAIRS = 18` |
| `screens/TestScreen.kt` | стенд: `LAB_MODE = 1` — три плями 236 (коло 100, blur 68) проти Figma; `2` — стрес `LAB_N` груп |

Код — у проєкті (файли вставлені напряму, diff у git). Нічого ззовні не міняється:
`BlurEffect(blur)`, `ABlur.blur`, `ABlurBack.blur`, `VfxTexture(... post = [BlurEffect(b)])`.

## Перевірка

Еталон Figma (`TEST_CIRCLE.png`) у проєкті вже немає, але з патча 18 відомо, що Layer Blur
B = чистий гаус із σ = 0.426·B (RMS 0.006), тож еталон — **аналітичний**: диск R = 50 ⊛
гаус σ = 28.97, `f(r) = ∫₀ᴿ ρ/σ² · e^{−(r−ρ)²/2σ²} · e^{−x}I₀(x) dρ`, x = rρ/σ²
(`profile.py`, центр 0.7745 = 1 − e^{−R²/2σ²}).

**Симуляція конвеєра на CPU** (`sim.py`: растр 4×4 → піраміда → ядро парами → B-сплайн →
білінійний семплінг екраном 3 px/юніт, 8 напрямків, r = 0..118):

| шлях | буфер → дно | RMS | центр | ізотропія | злам (Δ² відхилення) |
|---|---|---|---|---|---|
| `VfxTexture`, авто-density 0.414 | 98² | **0.0027** | 0.779 / 0.775 | 0.0003 | 0.00038 |
| `ABlur`, квантована 0.75 | 177² → 88² | **0.0029** | 0.776 | 0.0073 | 0.00018 |
| `ABlur`, density 3 | 708² → 88² (3 рівні) | **0.0029** | 0.776 | 0.0074 | 0.00009 |
| те саме, білінійний апскейл замість B-сплайна | 177² → 88² | 0.0030 | 0.777 | 0.0074 | 0.00056 |

Центр на +0.005 вище — обріз ядра на 3σ звужує гаус на 1.3 %; компенсація σ' = 1.0136·σ
перекомпенсовує піраміду (−0.007), тому не застосована. Дно з σ ≈ 5.4 (`SIGMA_WORK_MAX = 6`)
дає RMS 0.0026 і злам 0.00005 — можливо, але для типового `ABlur` це +1 прохід, а виграш у
fetch-ах відчутний лише на повноекранних буферах.

**На пристрої** (Redmi 24117RN76E, 1080×2400, 60 Hz), той самий `profile.py` по знімку:

| стенд | шлях | RMS проти аналітики | центр (еталон 0.775) | ізотропія | draw |
|---|---|---|---|---|---|
| A | `VfxTexture`, авто-density 0.41, без піраміди | 0.0029 | 0.772 | 0.015 (HUD і край екрана в полі заміру) | — |
| B | `ABlur`, density авто (квантована) → 1 рівень | **0.0017** | 0.772 | 0.002 | — |
| C | `ABlur`, density 3 → 3 рівні | **0.0017** | 0.772 | 0.002 | — |
| стрес | 8 × `ABlur(140, blur 30)` з рухомою дитиною, було 20 FPS / 178 draw | — | — | — | **56–57 FPS, 17.8 мс, 50 draw** |

Патч 18 давав RMS 0.0065 (авто) і 0.0091 (піраміда); тепер 0.0017 на живих групах.
Стрес: 6 draw на групу (діти, ↓, H, V, ↑, результат) проти ~22 — у 3.5 раза швидше;
8 одночасних живих блюрів — це стеля, 3–4 йдуть без просадки взагалі.

## Що далі

- `ABlurBack`: упаковати апскейл у прохід маски (маска семплить розмите дно B-сплайном) —
  мінус один повноекранний прохід. Не робилось: `ABlurBack` статичний і кешований.
- Якщо світіння має виходити за межі `ABlur` (модель Figma) — став `bleed = blur` під
  максимальне значення один раз; не міняй `bleed` на льоту (розмір FBO → новий bucket).

---

# Код — вставити в проєкт

Deny-правило на `app/**` не дало мені записати файли самому (див. картку, §16). Усі чотири файли — нижче цілком, у порядку вставки; компілюється після третього. Або одним рядком із лабораторії, поки scratchpad живий:

```bash
L="/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f460b756-5234-4d58-af6d-afa35c54593e/scratchpad/lab/app/src/main"
P="/Users/admin/Apps/Game Orbit Dash/Orbit Dash/app/src/main"
for f in assets/shader/base/blur/gaussianBlurFS.glsl assets/shader/base/copy/upsampleCubicFS.glsl \
         java/com/lewydo/orbitdash/game/utils/vfx/effects/base/BlurEffect.kt \
         java/com/lewydo/orbitdash/game/screens/TestScreen.kt; do cp "$L/$f" "$P/$f"; done
```

## `app/src/main/assets/shader/base/blur/gaussianBlurFS.glsl` — ЗАМІНИТИ ФАЙЛ ЦІЛКОМ

```glsl
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// GAUSSIAN BLUR — один прохід (H або V) із ПОВНИМ ядром під поточну σ.
//
//   Ваги й зсуви рахує BlurEffect на CPU під точну σ у текселях цього буфера
//   і віддає масивами. Кожна пара сусідніх текселів береться ОДНИМ білінійним
//   семплом на дробовому зсуві (linear sampling): вага пари = w1 + w2, зсув =
//   зважене середнє — це точно та сама дискретна згортка, лише вдвічі менше
//   fetch-ів. Тому 37 семплів покривають ±36 текселів = 3σ при σ = 12.
//
//   Один прохід H + один V = точний 2D-гаус будь-якої σ ≤ MAX_PAIRS·2/3
//   текселів. Більшу σ BlurEffect не просить: він спершу ділить буфер пірамідою.
//   Раніше σ набиралась повторами 9-tap ядра — до 13 пар проходів на кадр;
//   тепер завжди одна пара. Ціна проходу на тайловому GPU (перемикання render
//   target) набагато вища за ціну зайвих семплів на малому буфері.
//
//   highp для координат: на буфері 480 px mediump (fp16) дає похибку ~0.5 px.
// ─────────────────────────────────────────────────────────────────────────────

#define MAX_PAIRS 18

varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2  u_texelStep;              // (1/w, 0) для H або (0, 1/h) для V
uniform int   u_pairs;                  // скільки пар справді задано (≤ MAX_PAIRS)
uniform float u_center;                 // вага центрального текселя
uniform float u_offset[MAX_PAIRS];      // зсув пари в текселях (дробовий)
uniform float u_weight[MAX_PAIRS];      // вага пари (на обидва боки однакова)

void main() {
    vec4 sum = texture2D(u_texture, v_texCoords) * u_center;
    for (int i = 0; i < MAX_PAIRS; i++) {
        if (i >= u_pairs) break;
        vec2 d = u_texelStep * u_offset[i];
        sum += (texture2D(u_texture, v_texCoords + d) + texture2D(u_texture, v_texCoords - d)) * u_weight[i];
    }
    gl_FragColor = sum;
}
```

## `app/src/main/assets/shader/base/copy/upsampleCubicFS.glsl` — НОВИЙ ФАЙЛ

```glsl
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// UPSAMPLE CUBIC — підняти буфер піраміди назад кубічним B-сплайном.
//
//   Білінійний апскейл — кусково-лінійний: на гладкому гаусі видно злами
//   нахилу між текселями джерела (Mach-смуги), якщо σ там менша за ~12
//   текселів. B-сплайн — C¹-гладкий, зламів немає, тому дно піраміди може
//   бути з σ ≈ 6 — у 4 рази менше пікселів на прохід блюру.
//
//   Чотири білінійні семпли замість шістнадцяти (Sigg & Hadwiger, GPU Gems 2,
//   гл. 20): пари сусідніх текселів беруться одним семплом на зваженому зсуві.
//   Ціна — сплайн трохи домішує розмиття (σ² += 1/3 текселя джерела): при
//   σ = 6 це +0.5 % ширини, для Figma-еталона невидимо.
// ─────────────────────────────────────────────────────────────────────────────

varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2 u_srcSize;     // розмір джерела в текселях

void main() {
    vec2 coord = v_texCoords * u_srcSize - 0.5;     // центри текселів джерела — на цілих
    vec2 f     = fract(coord);
    coord     -= f;                                 // індекс базового текселя i

    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;      // тексель i-1
    vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;          // тексель i
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;// тексель i+1
    vec2 w3 = f3 / 6.0;                                   // тексель i+2

    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 h0 = coord - 1.0 + w1 / g0;    // зсув між i-1 та i, де білінійний семпл дає w0:w1
    vec2 h1 = coord + 1.0 + w3 / g1;    // між i+1 та i+2

    vec2 inv = 1.0 / u_srcSize;
    vec4 c = g0.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h0.y) + 0.5) * inv)
                   + g1.x * texture2D(u_texture, (vec2(h1.x, h0.y) + 0.5) * inv))
           + g1.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h1.y) + 0.5) * inv)
                   + g1.x * texture2D(u_texture, (vec2(h1.x, h1.y) + 0.5) * inv));
    gl_FragColor = c;
}
```

## `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/BlurEffect.kt` — ЗАМІНИТИ ФАЙЛ ЦІЛКОМ

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// BlurEffect — Layer Blur як у Figma
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gaussian blur з одним параметром — `blur`, **те саме число, що Layer Blur у
 * Figma**, у world-юнітах. Усе інше рахується тут.
 *
 *   VfxTexture(100f, 100f, msdf.circle, msdf.effect, listOf(BlurEffect(blur = 68f)))
 *   — коло 100×100 з Layer Blur 68; outerWidth = 236, як фрейм «hug contents».
 *
 * ─── Що всередині ───────────────────────────────────────────────────────────
 * Layer Blur B у Figma — звичайний гаус із σ = 0.426·B юніта. Виміряно по
 * експорту (коло 100 / blur 68): RMS 0.006 проти чистого гауса.
 *
 * Малюємо ОДНОЮ парою проходів H+V (це точний 2D-гаус: G(x,y) = G(x)·G(y)).
 * Ядро під точну σ рахується тут, на CPU, і йде в шейдер масивами: пари
 * сусідніх текселів беруться одним білінійним семплом (linear sampling), тому
 * 37 семплів покривають 3σ при σ = 12 текселів. Діагональних проходів НЕ
 * додавати: два напрямки в одному квадранті складають коваріації — еліпс.
 *
 * Чому одна пара, а не повтори 9-tap ядра, як було: заміряно 13.09.2026 —
 * ціна живого блюру на тайловому GPU це кількість ПРОХОДІВ (перемикань render
 * target), а не пікселів: 8 груп по 18 блітів = 20 FPS на будь-якому розмірі
 * буфера. Тепер на групу 2 бліти блюру + до 2 на піраміду.
 *
 * ─── Піраміда ──────────────────────────────────────────────────────────────
 * Якщо σ у текселях буфера більша за SIGMA_WORK_MAX (буфер повної роздільності:
 * ABlurBack, явна density, або VfxGroup із квантованою density), буфер ділиться
 * на 2 (box 2×2 через лінійний фільтр), поки σ не впаде в (6, 12], блюр іде там —
 * у 4–16 разів менше пікселів на семпл — і повертається одним кубічним B-сплайном
 * (upsampleCubicFS): C¹-гладко, без зламів нахилу, які давав білінійний апскейл
 * при σ < 12. Так робить Skia під Figma.
 *
 * ─── Роздільність ──────────────────────────────────────────────────────────
 * Розмитій текстурі не потрібна щільність фігури. preferredDensity() каже
 * VfxTexture / VfxGroup, скільки текселів на юніт досить для буфера, де стоїть
 * ПІДСУМОК: σ = SIGMA_TEXELS текселів (менше — екран білінійно семплить злами).
 *
 * blur = 0 → pass-through (жодного Blit, жодного swap).
 */
class BlurEffect(var blur: Float = 0f) : VfxEffect() {

    companion object {
        /** σ гауса на одиницю Layer Blur, юнітів. Фіт по експорту з Figma (коло 100, blur 68). */
        const val SIGMA_PER_BLUR = 0.426f
        /** σ у текселях буфера з ПІДСУМКОМ: менше — злами при білінійному семплінгу екраном. */
        const val SIGMA_TEXELS = 12f
        /** Стеля σ на рівні, де рахується ядро: вище — піраміда ділить буфер ½. = MAX_PAIRS·2/3. */
        const val SIGMA_WORK_MAX = 12f
        /** Радіус ядра в σ: за 3σ лишається 0.27 % маси — обрізу не видно. */
        const val KERNEL_SIGMAS = 3f
        /** Пар семплів на бік у шейдері (#define MAX_PAIRS) = ceil(3·12 / 2). */
        const val MAX_PAIRS = 18
    }

    override val fragmentShader = "shader/base/blur/gaussianBlurFS.glsl"

    private val copyShader     get() = VfxShaderCache.get("shader/base/copy/copyFS.glsl",          Blit.VERT)
    private val upsampleShader get() = VfxShaderCache.get("shader/base/copy/upsampleCubicFS.glsl", Blit.VERT)

    /** σ гауса в юнітах — Figma Layer Blur × 0.426. */
    val sigma: Float get() = SIGMA_PER_BLUR * blur

    override val isEnabled: Boolean get() = blur > 0f

    override fun stateKey(): Long = blur.toRawBits().toLong()

    /**
     * Скільки ефект виносить назовні, у юнітах. Правило Figma: bounds шару =
     * фігура + blur з кожного боку; гаус на цій межі (2.35σ від краю фігури)
     * уже ≈ 0.01 — той самий обріз, що й у експорті.
     */
    override fun reachUnits(): Float = blur

    /** Текселів на юніт, яких блюру досить: σ = SIGMA_TEXELS текселів. */
    override fun preferredDensity(): Float? = if (blur > 0f) SIGMA_TEXELS / sigma else null

    // ─── Ядро ────────────────────────────────────────────────────────────────
    // Рахується лише коли σ змінилась; у кадрі — нуль алокацій.

    private val kOffset = FloatArray(MAX_PAIRS)
    private val kWeight = FloatArray(MAX_PAIRS)
    private val kRaw    = FloatArray(2 * MAX_PAIRS + 1)
    private var kCenter = 1f
    private var kPairs  = 0
    private var kSigma  = -1f

    /** Дискретний гаус на цілих зсувах 0..3σ, згорнутий у пари під linear sampling. */
    private fun buildKernel(s: Float) {
        if (s == kSigma) return
        kSigma = s
        val r = ceil(KERNEL_SIGMAS * s).toInt().coerceIn(1, 2 * MAX_PAIRS)
        var sum = 0f
        for (i in 0..r) { kRaw[i] = exp(-(i * i) / (2f * s * s)); sum += if (i == 0) kRaw[i] else 2f * kRaw[i] }
        kCenter = kRaw[0] / sum
        var n = 0
        var i = 1
        while (i <= r) {
            val w1 = kRaw[i] / sum
            val w2 = if (i + 1 <= r) kRaw[i + 1] / sum else 0f
            val ww = w1 + w2
            kWeight[n] = ww
            kOffset[n] = if (ww > 0f) (i * w1 + (i + 1) * w2) / ww else i.toFloat()
            n++; i += 2
        }
        kPairs = n
    }

    private fun setKernel(sp: ShaderProgram, stepX: Float, stepY: Float) {
        sp.setUniformf("u_texelStep", stepX, stepY)
        sp.setUniformi("u_pairs", kPairs)
        sp.setUniformf("u_center", kCenter)
        sp.setUniform1fv("u_offset[0]", kOffset, 0, MAX_PAIRS)   // «[0]» — так масив звітує glGetActiveUniform
        sp.setUniform1fv("u_weight[0]", kWeight, 0, MAX_PAIRS)
    }

    /** Одна пара H+V на ping-pong; результат лишається в pp.src. */
    private fun blurHV(w: Int, h: Int, pp: PingPong) {
        Blit.blit(pp.src, pp.dst, shader) { sp -> setKernel(sp, 1f / w, 0f) }
        pp.swap()
        Blit.blit(pp.src, pp.dst, shader) { sp -> setKernel(sp, 0f, 1f / h) }
        pp.swap()
    }

    // ─── Render ──────────────────────────────────────────────────────────────

    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (blur <= 0f) return

        // σ у текселях ЦЬОГО буфера. VfxTexture з авто-density → рівно SIGMA_TEXELS;
        // VfxGroup із квантованою density → [12, 24); повна роздільність → десятки.
        var s      = sigma * ctx.density
        var levels = 0
        var w      = ctx.bufferW
        var h      = ctx.bufferH
        if (ctx.pool != null) {
            while (s > SIGMA_WORK_MAX * 1.001f && w >= 16 && h >= 16) {
                s /= 2f; w /= 2; h /= 2; levels++
            }
        }

        if (levels == 0) {
            // Без піраміди (нема пулу або σ й так мала). Якщо σ усе ж більша за ядро —
            // добираємо повторами: згортка N гаусів дає σ·√N. Нормально сюди не заходить.
            val passes = ceil((s / SIGMA_WORK_MAX).let { it * it }).toInt().coerceAtLeast(1)
            buildKernel(s / sqrt(passes.toFloat()))
            repeat(passes) { blurHV(ctx.bufferW, ctx.bufferH, pingPong) }
            return
        }

        // ── Піраміда ────────────────────────────────────────────────────────
        // Униз: кожен крок ×½ — fullscreen-квад із лінійним фільтром на буфер
        // удвічі менший семплить рівно між чотирма текселями = box 2×2.
        val pool = ctx.pool!!
        val tmp  = ArrayList<FrameBuffer>(levels + 1)
        var cur  = pingPong.src
        var cw   = ctx.bufferW
        var ch   = ctx.bufferH
        repeat(levels) {
            cw /= 2; ch /= 2
            val next = pool.obtain(cw, ch)
            Blit.blit(cur, next, copyShader)
            tmp += next; cur = next
        }
        // На дні — свій ping-pong тієї самої роздільності, одна пара H+V.
        val other  = pool.obtain(cw, ch)
        tmp += other
        val bottom = PingPong.of(cur, other)
        buildKernel(s)
        blurHV(cw, ch, bottom)
        // Угору одним стрибком кубічним B-сплайном — гладко при будь-якому кратному.
        Blit.blit(bottom.src, pingPong.dst, upsampleShader) { sp ->
            sp.setUniformf("u_srcSize", cw.toFloat(), ch.toFloat())
        }
        pingPong.swap()
        for (fb in tmp) pool.free(fb)
    }

}
```

## `app/src/main/java/com/lewydo/orbitdash/game/screens/TestScreen.kt` — ЗАМІНИТИ ФАЙЛ ЦІЛКОМ (стенд, тимчасовий екран)

```kotlin
package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.ABlur
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import com.lewydo.orbitdash.game.utils.vfx.effects.base.BlurEffect

class TestScreen : AdvancedScreen() {

    companion object {
        /** LAB: 1 — профіль проти Figma (три плями, без зірок); 2 — стрес LAB_N груп */
        private const val LAB_MODE = 1
        private const val LAB_N    = 8
        private val LAB_DENSITY: Float? = null
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aStarField by lazy { AStarField(this) }

    // Еталон Figma: коло 100×100, Layer Blur 68 → outer 236 (TEST_CIRCLE)
    private val glow68 = VfxTexture(100f, 100f, gdxGame.assetsMsdf.circle_msdf, gdxGame.assetsMsdf.effect,
        listOf(BlurEffect(blur = 68f)))

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        disposableSet.add(glow68)
        animShowScreen()
    }

    override fun Group.addActorsOnStageUI() {
        if (LAB_MODE == 2) addAndFillActor(aStarField)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        if (LAB_MODE == 1) addProfileStand() else addBlurStress()
        addDebugHud(ADebugHud(this@TestScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val v = stageUI.screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
        if (LAB_MODE == 2) aStarField.animRippleAt(v.x, v.y)
        return false
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------
    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.disable()
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // LAB 1: профіль проти Figma — три однакові плями 236 (коло 100, blur 68)
    //   y=545  A. VfxTexture, авто-density (буфер 97², без піраміди)
    //   y=300  B. ABlur, density авто (квантована: 177² → піраміда 1 рівень → 88²)
    //   y=55   C. ABlur, density = 3 (708² → піраміда 3 рівні → 88²)
    // ------------------------------------------------------------------------
    private fun AConstraintLayout.addProfileStand() {
        val texImg = Image(glow68.region).apply { setSize(236f, 236f) }
        add(texImg) { startToStart(margin = 62f); bottomToBottom(margin = 545f) }

        fun live(explicit: Float?) = ABlur(this@TestScreen).apply {
            setSize(236f, 236f)
            blur    = 68f
            density = explicit
            addActor(AMsdfImage(this@TestScreen, gdxGame.assetsMsdf.circle_msdf).apply {
                setBounds(68f, 68f, 100f, 100f)
            })
        }
        add(live(null)) { startToStart(margin = 62f); bottomToBottom(margin = 300f) }
        add(live(3f))   { startToStart(margin = 62f); bottomToBottom(margin = 55f) }
    }

    // ------------------------------------------------------------------------
    // LAB 2: стрес — LAB_N однакових ABlur із рухомою дитиною
    // ------------------------------------------------------------------------
    private fun AConstraintLayout.addBlurStress() {
        for (i in 0 until LAB_N) {
            val g = ABlur(this@TestScreen).apply {
                setSize(140f, 140f)
                blur    = 30f
                density = LAB_DENSITY
                addActor(Image(drawerUtil.getTexture(Color.ORANGE)).apply {
                    setBounds(30f, 30f, 80f, 80f)
                    addAction(Actions.forever(Actions.sequence(
                        Actions.moveBy( 20f, 0f, 1f),
                        Actions.moveBy(-20f, 0f, 1f),
                    )))
                })
            }
            add(g) { startToStart(margin = 20f + i * 22f); bottomToBottom(margin = 120f + i * 40f) }
        }
    }
}
```
