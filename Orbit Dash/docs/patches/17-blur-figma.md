> **НЕ ВСТАВЛЯТИ — поглинуто патчем 18** (`18-blur-figma-units.md`). Лишено як діагноз і заміри етапу 1.

# 17 — Світіння «як у Figma»: `BlurEffect` H+V×passes, `glow` по еталону, клемп FBO

## Що і навіщо

Світіння (`SpriteUtil.Msdf.glow` — м'яч, гем, шип, бустер, комета) виглядало гірше за
Layer Blur у Figma. Причина — один рядок:

```kotlin
val glow = VfxTexture(236f, 236f, circle_msdf, effect, listOf(BlurEffect(radius = 68f))).region
```

`68` — це Layer Blur із Figma, а `BlurEffect.radius` — **крок між семплами в текселях**.
При `density 3` крок = 22.7 юніта: замість гауса — сума 9 зсунутих копій кола. Побічно:
`bleed` 210, FBO 1968² (15.5 МБ) + два ping-pong у пулі ≈ 46 МБ VRAM на одну пляму;
на 1440p буфер 2624 px — понад гарантований ліміт GLES2 (2048), клемпа не було.

Ще два дефекти самого `BlurEffect`, які видно й при правильному `radius`:

- паси 3–4 — напрямки `(0.383, 0.924)` і `(0.924, 0.383)`, обидва в одному квадранті.
  Їхні коваріації **додаються**, і пляма на 45 % ширша по діагоналі 45°, ніж упоперек.
  На знімку «до» це видно оком — еліпс. H+V уже дають точний 2D-гаус;
- 9-tap ядро обривається на `4·radius` = 2.37σ, де гаус ще має 6 % — на темному тлі
  кільце.

Виміряно по експорту з Figma (`textures/loader/TEST_CIRCLE.png`, коло 100, blur 68):
це **чистий гаус із σ = 29 юнітів = 0.426·blur**, RMS 0.006. Формула §6а
(`σ = 0.48·blur`, «чистий гаус — 5 %») на цій пропорції не підтвердилась.

Результат на пристрої (1080×2400): профіль збігається з еталоном із **RMS 0.008**,
0° і 45° різняться на 0.004 (було 0.054), центр 0.770 проти 0.784, GL-пам'ять
119 → 70 МБ, буфер 134² px ≈ 72 КБ замість 15.5 МБ. Перша спроба з `density 0.2`
(буфер 68 px) давала ту саму σ, але апскейл 15× показував злами нахилу між
текселями — тому `density 0.4` і σ добирається пасами.

---

## 1. `BlurEffect.kt` — ЗАМІНИТИ файл цілком

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/BlurEffect.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// BlurEffect — Gaussian blur (H + V, повторювані passes разів)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gaussian blur.
 *
 * Пара проходів через ping-pong — горизонтальний і вертикальний. Це
 * математично точний 2D-гаус: Gaussian2D(x,y) = Gaussian1D(x) × Gaussian1D(y).
 * Діагональних проходів НЕ додавати: два напрямки в одному квадранті
 * складають коваріації, і пляма стає еліпсом, витягнутим по 45°.
 *
 * ─── Що таке radius і що таке passes ────────────────────────────────────────
 * radius — КРОК між семплами в текселях буфера. Не σ і не Layer Blur із Figma.
 *   Ядро в шейдері — 9 семплів з вагами, дисперсія яких 2.854 кроку², тож
 *   σ одного проходу = 1.689 · radius текселів. Крок понад 2 текселі
 *   розсуває семпли далі, ніж деталі в джерелі, — виходять смуги, а не блюр.
 *   Тому radius лишається ≤ 2, а ширину дає density буфера (VfxTexture).
 * passes — скільки разів повторити пару H+V. Згортка N гаусів дає σ·√N,
 *   і, важливіше, хвіст: одне 9-tap ядро обривається на 4·radius = 2.37σ,
 *   де гаус ще має 6 % — на темному тлі це кільце. Після трьох згорток обрив
 *   іде на ~4σ, де лишається 0.02 %.
 *
 *   σ разом = 1.689 · radius · √passes текселів = sigmaTexels().
 *   У юнітах — поділити на density. Рецепт під Figma: Layer Blur B →
 *   σ ≈ 0.43·B юніта (виміряно по експорту, docs/decisions.md) →
 *   density = sigmaTexels() / σ.
 *
 * radius = 0 → pass-through (жодного Blit, жодного swap).
 */
class BlurEffect(var radius: Float = 2f, var passes: Int = 1) : VfxEffect() {

    override val fragmentShader = "shader/base/blur/gaussianBlurFS.glsl"

    /** σ підсумкового розмиття в текселях буфера. */
    fun sigmaTexels(): Float = SIGMA_PER_STEP * radius * sqrt(passes.toFloat())

    // Multi-pass: override render() а не setUniforms()
    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (radius <= 0f || passes <= 0) return  // pass-through — src не змінюється

        repeat(passes) {
            // Горизонтальний
            Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
                s.setUniformf("u_direction",  1f, 0f)
                s.setUniformf("u_groupSize",  ctx.bufferW.toFloat(), ctx.bufferH.toFloat())
                s.setUniformf("u_blurAmount", radius)
            }
            pingPong.swap()  // результат H-pass тепер в src

            // Вертикальний
            Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
                s.setUniformf("u_direction",  0f, 1f)
                s.setUniformf("u_groupSize",  ctx.bufferW.toFloat(), ctx.bufferH.toFloat())
                s.setUniformf("u_blurAmount", radius)
            }
            pingPong.swap()  // результат V-pass тепер в src
        }
    }

    /**
     * Докуди ефект виносить альфу. Ядро гарантовано обривається на
     * 4·radius·passes текселів, але σ росте лише як √passes, і за 4σ гаус уже
     * 0.03 % — там нічого зберігати. Менше з двох: інакше bleed для 12 пасів
     * роздувся б удвічі проти реального хвоста. Це й є bleed, який VfxTexture
     * бере собі: reachTexels() / density юнітів на бік.
     */
    override fun reachTexels(): Float = minOf(4f * radius * passes, 4f * sigmaTexels())

    override fun stateKey(): Long = radius.toRawBits().toLong() * 31 + passes

    companion object {
        /** σ одного 9-tap проходу в кроках: √2.854 — дисперсія ваг ядра в шейдері. */
        const val SIGMA_PER_STEP = 1.689f
    }

}
```

Дефолт `radius` став `2f` (був `8f` — суперечив правилу «≤ 2»). Дефолтом ніхто не
користувався: `ABlur` / `ABlurBack` створюють `BlurEffect(radius = 0f)`, `SpriteUtil` —
явно. `ABlur` / `ABlurBack` не анімують `radiusBlur` ніде (grep порожній), тож зміна їх
не зачіпає; якщо колись увімкнуться — σ буде в 1.41 раза вужча за стару.

---

## 2. `SpriteUtil.kt` — `glow` по еталону

`app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`, `class Msdf`,
одразу після `val circle = VfxTexture(22f, 22f, circle_msdf, effect).region`.

**ЗАМІНИТИ**

```kotlin
        val glow        = VfxTexture(236f, 236f, circle_msdf, effect, listOf(BlurEffect(radius = 68f))).region
```

**на**

```kotlin
        // Світіння під об'єкти — еталон із Figma: коло 100×100, Layer Blur 68
        // (textures/loader/TEST_CIRCLE.png). По експорту виміряно: це гаус із
        // σ ≈ 29 юнітів (0.43·blur). radius — крок семплів, лишається 2 (більше —
        // смуги); ширину дають passes і density: σ_texels = 1.689·2·√12 = 11.7 →
        // density = 11.7/29 ≈ 0.4. Нижча density (0.2, 3 паси) давала ту саму σ
        // з буфера 68 px, але апскейл 15× показував злами нахилу між текселями.
        // bleed рахується сам: 4σ/0.4 = 117 юнітів; буфер 134×134 px ≈ 72 КБ.
        // Регіон = уся пляма 334×334, коло — 100 із них у центрі.
        val glow        = VfxTexture(100f, 100f, circle_msdf, effect, listOf(BlurEffect(radius = 2f, passes = 12)), density = 0.4f).region
```

Наслідок для акторів: вони малюють `Image(glow)` як **усю пляму** (`ABall.setSizeScaled(100)`).
Частка кола в ній була 236/656 = 0.36, стала 100/334 = 0.30 — ядро трохи менше відносно
плями, як у Figma. Якщо треба старе відношення — підняти розміри в акторах, `density` не
чіпати.

---

## 3. `VfxTextures.kt` — ліміт GL

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxTextures.kt`

**ДОДАТИ** в імпорти (поруч із `Pixmap`):

```kotlin
import com.badlogic.gdx.graphics.GL20
```

**ДОДАТИ** перед рядком `// ─── Спільні ресурси рендеру ───…` (після `val DENSITY`):

```kotlin
    /**
     * GL_MAX_TEXTURE_SIZE цього GPU — стеля для буфера VfxTexture. Гарантований
     * мінімум GLES2 — 2048; більший буфер на слабкому пристрої — чорна текстура.
     * by lazy: читати можна лише з GL-потоку, а перший VfxTexture і так там.
     */
    val maxTextureSize: Int by lazy {
        val buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16)
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, buf)
        buf.get(0).takeIf { it > 0 } ?: 2048
    }

```

---

## 4. `VfxTexture.kt` — клемп буфера

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxTexture.kt`

**ЗАМІНИТИ** (після `padX` / `padY`):

```kotlin
    private val bufW = ceil(outerWidth  * density).toInt().coerceAtLeast(1)
    private val bufH = ceil(outerHeight * density).toInt().coerceAtLeast(1)
```

**на**

```kotlin
    /**
     * Розмір буфера. Запит понад GL_MAX_TEXTURE_SIZE зменшується ПРОПОРЦІЙНО по
     * обох сторонах (обрізати одну — регіон розтягнувся б криво): щільність
     * стане нижчою за density, зате не чорна текстура на 1440p-планшеті.
     */
    private val bufW: Int
    private val bufH: Int
    init {
        val w   = ceil(outerWidth  * density).toInt().coerceAtLeast(1)
        val h   = ceil(outerHeight * density).toInt().coerceAtLeast(1)
        val max = VfxTextures.maxTextureSize
        val k   = if (maxOf(w, h) > max) max.toFloat() / maxOf(w, h) else 1f
        if (k < 1f) Gdx.app.error("VfxTexture", "буфер ${w}×${h} > GL_MAX_TEXTURE_SIZE $max — зменшено в ${1f / k} раза")
        bufW = (w * k).toInt().coerceAtLeast(1)
        bufH = (h * k).toInt().coerceAtLeast(1)
    }
```

І в коментарі шапки файла **ЗАМІНИТИ**

```
//   Скільки треба: ≈ 9 × radius / density юнітів (чотири проходи по 4 кроки
//   по radius текселів); краще з запасом.
```

**на**

```
//   Скільки треба — каже сам ефект: BlurEffect.reachTexels() = min(4·radius·passes,
//   4·σ) текселів; поділене на density — юніти на бік.
```

---

## Перевірка

Все чотири блоки зібрані й перевірені на `pvtwpbtohex44xmb` (лабораторна збірка =
проєкт + ці блоки, вона зараз на пристрої). Стенд — `TestScreen.addMsdfSandbox()` з
`Image(glow)` 236×236 по центру.

```bash
sh gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Що очікувати на екрані: кругла пляма без діагонального перекосу, без кільця на краю,
без вертикальних смуг у ядрі; `61 FPS`. У logcat — жодного `VfxTexture: буфер … >
GL_MAX_TEXTURE_SIZE` (на 1080p він і не мав би з'явитись).

Профіль (скрипт у scratchpad, декодер `read_png` з `pack-msdf.py`):

| r, px еталона | Figma | до | після |
|---|---|---|---|
| 0 | 0.784 | 0.904 | 0.770 |
| 36 | 0.541 | 0.858 | 0.536 |
| 72 | 0.157 | 0.707 | 0.155 |
| 108 | 0.004 | 0.473 | 0.013 |
| RMS | — | 0.42 | **0.008** |
| 0° vs 45° | — | 0.054 (еліпс) | 0.004 |

Хвіст у нас трохи довший за Figma (0.013 проти 0.004 на r = 108): Figma обрізає
bounds на 2.3σ, гаус тягнеться далі. Різниця ≤ 0.01 — оком не видно.

---

## Хвости

- `TestScreen.addMsdfSandbox()` — стенд; прибрати, як надивишся.
- `textures/loader/TEST_CIRCLE.png` — еталон, тепер на нього посилаються коментар у
  `SpriteUtil` і `decisions.md`. Або лишити як є, або перенести в `assets/loader/` поруч
  з `item_glow.png` і поправити два посилання.
- Етап 2, коли дійдемо: пірамідальний dual-filter (для `ABlurBack` на повній
  роздільності, де 12 пасів щокадру — уже дорого) і `BlurEffect` з параметром у юнітах
  Figma замість `radius` / `passes` / `density` у голові.
