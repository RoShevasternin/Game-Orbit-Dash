# 18 — Блюр як у Figma: `BlurEffect(blur)` у юнітах, авто-density, піраміда

Поглинає патч 17 (його не вставляти). База — файли проєкту станом на 10 вересня, без 17.

## Що і навіщо

Мета: писати в коді рівно те, що бачиш у Figma, і отримувати той самий шар:

```kotlin
// Figma: коло 100×100, Layer Blur 68
val glowTex = VfxTexture(100f, 100f, circle_msdf, effect, listOf(BlurEffect(blur = 68f)))
val glow    = glowTex.region            // tex.outerWidth == 236 — як фрейм «hug contents»
```

Що для цього зроблено:

- **`BlurEffect(blur)`** — один параметр, Layer Blur із Figma у юнітах. По експорту
  (`textures/loader/TEST_CIRCLE.png`) виміряно: Layer Blur B = гаус із σ = 0.426·B,
  RMS 0.006. `radius` / `passes` з API прибрані: крок семплів ≤ 2 (більше — смуги),
  пари H+V повторюються, поки σ не набереться. Діагональних пасів немає — вони робили
  еліпс (обидва напрямки в одному квадранті, коваріації додаються).
- **`bleed = blur`** (`VfxEffect.reachUnits()`, у юнітах замість текселів) — як bounds
  шару у Figma; гаус на цій межі ≈ 0.01. Тому `outer` = фрейм Figma 1:1.
- **`density` рахується сама** (`VfxEffect.preferredDensity()`): розмитій текстурі
  роздільність фігури не потрібна — блюру досить σ ≈ 12 текселів (при 6 після
  апскейлу видно злами нахилу між текселями, при 12 — ні). Стеля — `DENSITY` екрана.
  Для `glow` це 0.41 → буфер 98×98 px замість 1968×1968 (46 МБ → ~40 КБ).
- **Піраміда** для буферів повної роздільності (`VfxGroup`, `ABlurBack`, або явна
  `density`): буфер ділиться на 2, поки σ не впаде до ~12 текселів, блюр там, один
  білінійний апскейл назад. Так робить Skia під Figma. Для цього `VfxContext` отримав
  `pool` і `density`, `PingPong.of()` — ping-pong над чужими буферами, `copyFS.glsl` —
  copy-шейдер (уся робота в лінійному фільтрі).
- `VfxTexture` клемпить буфер під `GL_MAX_TEXTURE_SIZE` пропорційно (на 1440p старий
  glow просив 2624 px — понад гарантію GLES2).
- `ABlur` / `ABlurBack`: `radiusBlur` → `blur`, ті самі юніти.

Перевірено на пристрої проти еталона Figma по 8 напрямках:

| шлях | RMS проти еталона | розкид між напрямками |
|---|---|---|
| було (`radius = 68`) | 0.42 | 0.054 — еліпс |
| авто-density (піде в гру) | **0.0065** | 0.005 |
| `density = 3` → піраміда 3 рівні (708 → 88 px) | **0.0091** | 0.006 |

---

## 1. `BlurEffect.kt` — ЗАМІНИТИ файл цілком

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/BlurEffect.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache
import kotlin.math.ceil
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
 * Малюємо парою проходів H+V (це точний 2D-гаус: G(x,y) = G(x)·G(y)),
 * повтореною passes разів. Діагональних проходів НЕ додавати: два напрямки в
 * одному квадранті складають коваріації, і пляма стає еліпсом.
 *
 * Шейдер — 9 семплів з кроком step текселів, σ одного проходу = 1.689·step.
 * step понад 2 текселі розсуває семпли далі за деталі джерела — смуги.
 * Тому step ≤ 2, а велика σ добирається двома способами:
 *   • passes — згортка N гаусів дає σ·√N; заразом хвіст перестає обриватись
 *     (одне 9-tap ядро закінчується на 2.37σ — це кільце на темному тлі);
 *   • піраміда — якщо σ у текселях цього буфера все одно завелика (буфер
 *     повної роздільності: VfxGroup, ABlurBack), буфер ділиться на 2, поки σ
 *     не впаде до ~SIGMA_TEXELS, блюриться там і повертається одним upsample.
 *     Так робить Skia під Figma.
 *
 * ─── Роздільність ──────────────────────────────────────────────────────────
 * Розмитій текстурі не потрібна щільність фігури. preferredDensity() каже
 * VfxTexture (без явної density), скільки текселів на юніт досить:
 * σ = SIGMA_TEXELS текселів. Нижче — після апскейлу видно злами нахилу між
 * текселями (перевірено: σ ≈ 6 текселів — видно, ≈ 12 — ні).
 *
 * blur = 0 → pass-through (жодного Blit, жодного swap).
 */
class BlurEffect(var blur: Float = 0f) : VfxEffect() {

    override val fragmentShader = "shader/base/blur/gaussianBlurFS.glsl"

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

    private val copyShader get() = VfxShaderCache.get("shader/base/copy/copyFS.glsl", Blit.VERT)

    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (blur <= 0f) return

        // σ у текселях ЦЬОГО буфера. Для VfxTexture з авто-density це ≈ SIGMA_TEXELS;
        // для буфера повної роздільності (VfxGroup) — у рази більше → піраміда.
        var s      = sigma * ctx.density
        var levels = 0
        var w      = ctx.bufferW
        var h      = ctx.bufferH
        if (ctx.pool != null) {
            while (s > SIGMA_TEXELS * 1.5f && w >= 16 && h >= 16) {
                s /= 2f; w /= 2; h /= 2; levels++
            }
        }

        // Скільки пар H+V і який крок, щоб σ вийшла рівно s при step ≤ STEP_MAX.
        val passes = ceil((s / (SIGMA_PER_STEP * STEP_MAX)).let { it * it }).toInt().coerceAtLeast(1)
        val step   = s / (SIGMA_PER_STEP * sqrt(passes.toFloat()))

        if (levels == 0) {
            blurHV(pingPong.src, pingPong.dst, ctx.bufferW, ctx.bufferH, step, passes, pingPong)
            return
        }

        // ── Піраміда ────────────────────────────────────────────────────────
        // Униз: кожен крок ×½ — fullscreen-квад із лінійним фільтром на буфер
        // удвічі менший семплить рівно між чотирма текселями = box 2×2.
        val pool  = ctx.pool!!
        val tmp   = ArrayList<FrameBuffer>(levels + 1)
        var cur   = pingPong.src
        var cw    = ctx.bufferW
        var ch    = ctx.bufferH
        repeat(levels) {
            cw /= 2; ch /= 2
            val next = pool.obtain(cw, ch)
            Blit.blit(cur, next, copyShader)
            tmp += next; cur = next
        }
        // На дні — свій ping-pong тієї самої роздільності.
        val other = pool.obtain(cw, ch)
        tmp += other
        val bottom = PingPong.of(cur, other)
        blurHV(bottom.src, bottom.dst, cw, ch, step, passes, bottom)
        // Угору одним стрибком: після σ ≈ 12 текселів деталей, які втратив би
        // білінійний апскейл, у картинці немає.
        Blit.blit(bottom.src, pingPong.dst, copyShader)
        pingPong.swap()
        for (fb in tmp) pool.free(fb)
    }

    /** passes пар H+V на ping-pong; результат лишається в pp.src. */
    private fun blurHV(src: FrameBuffer, dst: FrameBuffer, w: Int, h: Int, step: Float, passes: Int, pp: PingPong) {
        val fw = w.toFloat(); val fh = h.toFloat()
        repeat(passes) {
            Blit.blit(pp.src, pp.dst, shader) { sp ->
                sp.setUniformf("u_direction",  1f, 0f)
                sp.setUniformf("u_groupSize",  fw, fh)
                sp.setUniformf("u_blurAmount", step)
            }
            pp.swap()
            Blit.blit(pp.src, pp.dst, shader) { sp ->
                sp.setUniformf("u_direction",  0f, 1f)
                sp.setUniformf("u_groupSize",  fw, fh)
                sp.setUniformf("u_blurAmount", step)
            }
            pp.swap()
        }
    }

    companion object {
        /** σ гауса на одиницю Layer Blur, юнітів. Фіт по експорту з Figma (коло 100, blur 68). */
        const val SIGMA_PER_BLUR = 0.426f
        /** σ одного 9-tap проходу в кроках: √2.854 — дисперсія ваг ядра в шейдері. */
        const val SIGMA_PER_STEP = 1.689f
        /** Крок семплів, вище якого між ними дірки → смуги. */
        const val STEP_MAX = 2f
        /** Робоча σ в текселях: менше — злами при апскейлі, більше — зайві паси. */
        const val SIGMA_TEXELS = 12f
    }

}
```

---

## 2. `copyFS.glsl` — НОВИЙ файл

`app/src/main/assets/shader/base/copy/copyFS.glsl`

```glsl
#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// COPY — переписати текстуру в буфер іншого розміру.
//
// Уся робота — в лінійному фільтрі текстури: на буфер удвічі менший це box 2×2
// (downsample піраміди блюру), на більший — білінійний апскейл. Сам шейдер
// нічого не рахує.
// ─────────────────────────────────────────────────────────────────────────────

varying vec2 v_texCoords;
uniform sampler2D u_texture;

void main() {
    gl_FragColor = texture2D(u_texture, v_texCoords);
}
```

---

## 3. `PingPong.kt` — ЗАМІНИТИ файл цілком

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/PingPong.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.graphics.glutils.FrameBuffer

/**
 * Ping-pong буфер — два FBO що міняються місцями між ефектами.
 *
 * Уявляй це як дві тарілки на кухні. Кухар (ефект) бере їжу з першої тарілки,
 * обробляє її і кладе результат на другу. Потім тарілки міняються місцями —
 * тепер оброблена їжа на "першій" тарілці, і наступний кухар робить теж саме.
 *
 * Скільки б кухарів (ефектів) не було — завжди рівно дві тарілки (FBO).
 *
 *   [src=A] → Effect1 → [dst=B]   swap → [src=B, dst=A]
 *   [src=B] → Effect2 → [dst=A]   swap → [src=A, dst=B]
 *   [src=A] → Effect3 → [dst=B]   swap → [src=B, dst=A]
 *   Результат завжди в src після pipeline.
 *
 * Обидва FBO беруться з VfxPool при створенні і повертаються туди через free().
 */
class PingPong private constructor(
    val width  : Int,
    val height : Int,
    src        : FrameBuffer,
    dst        : FrameBuffer,
    private val pool: VfxPool?,
) {
    /** Обидва буфери з пулу; free() поверне їх туди. */
    constructor(pool: VfxPool, width: Int, height: Int) :
        this(width, height, pool.obtain(width, height), pool.obtain(width, height), pool)

    var src: FrameBuffer = src ; private set
    var dst: FrameBuffer = dst ; private set

    /**
     * Міняє src і dst місцями.
     * Після кожного Blit.blit(src, dst, ...) обов'язково викликати swap() —
     * тоді результат стає src для наступного ефекту.
     */
    fun swap() { val t = src; src = dst; dst = t }

    /** Повертає обидва FBO в пул. Викликати після draw(). Не для of(): там буфери чужі. */
    fun free() {
        pool?.free(src)
        pool?.free(dst)
    }

    companion object {
        /**
         * Ping-pong над ЧУЖИМИ буферами однакового розміру — дно піраміди блюру,
         * де буфери вже орендовані й повертаються в пул тим, хто їх брав.
         */
        fun of(src: FrameBuffer, dst: FrameBuffer) = PingPong(src.width, src.height, src, dst, null)
    }
}
```

---

## 4. `VfxContext.kt` — ЗАМІНИТИ `data class` (кінець файла)

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxContext.kt`

**ЗАМІНИТИ**
```kotlin
data class VfxContext(
    val width  : Float,
    val height : Float,
    val bufferW: Int,
    val bufferH: Int,
)
```
**на**
```kotlin
data class VfxContext(
    val width  : Float,
    val height : Float,
    val bufferW: Int,
    val bufferH: Int,
    /** Пул для ефектів, яким потрібні тимчасові буфери ІНШОГО розміру (піраміда блюру). null → без піраміди. */
    val pool   : VfxPool? = null,
) {
    /** Текселів буфера на один world-юніт. */
    val density: Float get() = if (width > 0f) bufferW / width else 1f
}
```

---

## 5. `VfxEffect.kt` — `reachTexels` → `reachUnits`, `preferredDensity`

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/VfxEffect.kt`

**ЗАМІНИТИ**
```kotlin
    /**
     * Наскільки ефект розповзається ЗА межі свого джерела, у текселях буфера.
     * Потрібно, щоб VfxTexture сам порахував bleed — поле під ефект назовні.
     * 0 = ефект нічого не виносить (маска, тінт).
     */
    open fun reachTexels(): Float = 0f
```
**на**
```kotlin
    /**
     * Наскільки ефект розповзається ЗА межі свого джерела, у world-юнітах.
     * Потрібно, щоб VfxTexture сам порахував bleed — поле під ефект назовні.
     * 0 = ефект нічого не виносить (маска, тінт).
     */
    open fun reachUnits(): Float = 0f

    /**
     * Якої роздільності (текселів на юніт) ефекту ДОСИТЬ. VfxTexture без явної
     * density бере мінімум по ланцюгу post — розмитій текстурі щільність
     * фігури не потрібна. null = байдуже (маска, тінт).
     */
    open fun preferredDensity(): Float? = null
```

---

## 6. `VfxTextures.kt` — ліміт GL

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxTextures.kt`

**ДОДАТИ** в імпорти (поруч із `Pixmap`):
```kotlin
import com.badlogic.gdx.graphics.GL20
```

**ДОДАТИ** після `val DENSITY … }`, перед `// ─── Спільні ресурси рендеру ───`:
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

## 7. `VfxTexture.kt` — авто-density, bleed у юнітах, пул, клемп

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxTexture.kt`

**7а. Шапка — ЗАМІНИТИ**
```
//   Скільки треба: ≈ 9 × radius / density юнітів (чотири проходи по 4 кроки
//   по radius текселів); краще з запасом.
```
**на**
```
//   Скільки треба — каже сам ефект: VfxEffect.reachUnits(), юніти на бік
//   (BlurEffect: = blur, як bounds шару у Figma).
//   density теж рахується сама: мінімум із VfxEffect.preferredDensity() по
//   ланцюгу post (BlurEffect: σ ≈ 12 текселів), стеля — DENSITY екрана.
```

**7б. Конструктор — ЗАМІНИТИ**
```kotlin
    val density: Float            = VfxTextures.DENSITY,
    bleed      : Float?           = null,        // поле під post назовні; null → з ланцюга post
) : Disposable {

    /**
     * Поле під post-ефекти НАЗОВНІ від фігури, на бік, у юнітах.
```
**на**
```kotlin
    density    : Float?           = null,        // текселів на юніт; null → скільки просить ланцюг post, стеля DENSITY
    bleed      : Float?           = null,        // поле під post назовні; null → з ланцюга post
) : Disposable {

    /**
     * Текселів на юніт. Явне число — як задано. null — мінімум із того, що
     * просять post-ефекти (BlurEffect: σ ≈ 12 теселів), не вище екранної
     * DENSITY: розмитій текстурі роздільність фігури не потрібна, а буфер
     * від density квадратично.
     */
    val density: Float = density
        ?: post.mapNotNull { it.preferredDensity() }.minOrNull()?.coerceAtMost(VfxTextures.DENSITY)
        ?: VfxTextures.DENSITY

    /**
     * Поле під post-ефекти НАЗОВНІ від фігури, на бік, у юнітах.
```

**7в. `bleed` — ЗАМІНИТИ**
```kotlin
    val bleed: Float = bleed ?: ceil(post.fold(0f) { acc, e -> acc + e.reachTexels() } / density)
```
**на**
```kotlin
    val bleed: Float = bleed ?: ceil(post.fold(0f) { acc, e -> acc + e.reachUnits() })
```

**7г. Буфер — ЗАМІНИТИ** (після `padX` / `padY`)
```kotlin
    private val bufW = ceil(outerWidth  * density).toInt().coerceAtLeast(1)
    private val bufH = ceil(outerHeight * density).toInt().coerceAtLeast(1)

    /** Контекст post-ефектів — увесь буфер, разом із bleed. */
    private val ctx      = VfxContext(outerWidth, outerHeight, bufW, bufH)
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
        val w   = ceil(outerWidth  * this.density).toInt().coerceAtLeast(1)
        val h   = ceil(outerHeight * this.density).toInt().coerceAtLeast(1)
        val max = VfxTextures.maxTextureSize
        val k   = if (maxOf(w, h) > max) max.toFloat() / maxOf(w, h) else 1f
        if (k < 1f) Gdx.app.error("VfxTexture", "буфер ${w}×${h} > GL_MAX_TEXTURE_SIZE $max — зменшено в ${1f / k} раза")
        bufW = (w * k).toInt().coerceAtLeast(1)
        bufH = (h * k).toInt().coerceAtLeast(1)
    }

    /** Контекст post-ефектів — увесь буфер, разом із bleed. */
    private val ctx      = VfxContext(outerWidth, outerHeight, bufW, bufH, VfxTextures.pool)
```

`resized(…)` не чіпати: `density: Float = this.density` і далі передає число — сумісно з `Float?`.

---

## 8. `VfxGroup.kt` — пул у контекст

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxGroup.kt`, у `draw()` після `bufH`.

**ЗАМІНИТИ**
```kotlin
        val ctx    = VfxContext(outerW, outerH, bufW, bufH)
```
**на**
```kotlin
        val ctx    = VfxContext(outerW, outerH, bufW, bufH, pool)
```

---

## 9. `ABlur.kt`

`app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/ABlur.kt`

**ЗАМІНИТИ** у KDoc
```
 *   radiusBlur       — радіус розмиття (0 = вимкнено)
```
**на**
```
 *   blur             — Layer Blur як у Figma, юніти (0 = вимкнено)
```

**ЗАМІНИТИ**
```kotlin
    private val blurEffect = BlurEffect(radius = 0f)

    var radiusBlur: Float = 0f
        set(value) {
            blurEffect.radius = value
            field = value
        }


    val isBlurEnabled: Boolean get() = blurEffect.radius > 0f
```
**на**
```kotlin
    private val blurEffect = BlurEffect(blur = 0f)

    /** Layer Blur як у Figma, юніти. 0 = вимкнено. */
    var blur: Float
        get()      = blurEffect.blur
        set(value) { blurEffect.blur = value }

    val isBlurEnabled: Boolean get() = blurEffect.isEnabled
```

---

## 10. `ABlurBack.kt`

`app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/ABlurBack.kt`

**ЗАМІНИТИ**
```kotlin
    private val blurEffect = BlurEffect(radius = 0f)
    private val maskEffect = MaskEffect(maskTexture)

    var radiusBlur: Float = 0f
        set(value) { blurEffect.radius = value; field = value }

    val isBlurEnabled: Boolean get() = blurEffect.radius > 0f
```
**на**
```kotlin
    private val blurEffect = BlurEffect(blur = 0f)
    private val maskEffect = MaskEffect(maskTexture)

    /** Layer Blur як у Figma, юніти. 0 = вимкнено. Буфер тут повної роздільності — BlurEffect сам іде пірамідою. */
    var blur: Float
        get()      = blurEffect.blur
        set(value) { blurEffect.blur = value }

    val isBlurEnabled: Boolean get() = blurEffect.isEnabled
```

`radiusBlur` ніде ззовні не присвоювався (grep), тому інших правок немає.

---

## 11. `SpriteUtil.kt` — `glow`

`app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`, `class Msdf`.

**ЗАМІНИТИ**
```kotlin
        val glow        = VfxTexture(236f, 236f, circle_msdf, effect, listOf(BlurEffect(radius = 68f))).region
```
**на**
```kotlin
        // Світіння під об'єкти — один в один шар із Figma: коло 100×100, Layer Blur 68
        // (еталон textures/loader/TEST_CIRCLE.png). density і bleed рахуються самі:
        // outer = 236 = фрейм «hug contents» у Figma. Два способи малювати:
        //   Image(glow)         — уся пляма 236 в актора; коло — 100/236 = 42 % по центру
        //   glowTex.image()     — межі актора = коло, світіння виходить назовні (модель Figma)
        val glowTex     = VfxTexture(100f, 100f, circle_msdf, effect, listOf(BlurEffect(blur = 68f)))
        val glow        = glowTex.region
```

Наслідок для акторів: `Image(glow)` малює всю пляму 236; частка кола в ній була 236/656 = 36 %,
стала 42 %. Це рівно те, що в Figma. Якщо в грі треба інакше — крутити розміри в акторах
(`ABall.setSizeScaled(100)` тощо) або перейти на `glowTex.image()` з розміром об'єкта.

---

## 12. `TestScreen.kt` — стенд (за бажанням)

Один `Image(glow)` 236×236 по центру, як зараз, — цього досить: він 1:1 з `TEST_CIRCLE.png`.
Другий стенд (примусова `density = 3f` → піраміда) на одному екрані з першим перекривається
на 47 px — якщо треба, роби по одному або зменш обидва до 160.

---

## Перевірка

```bash
sh gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Кругла пляма без діагонального перекосу, без кільця, без смуг; 61 FPS. Лабораторна збірка
з цими самими байтами зараз на пристрої. Заміри — таблиця вгорі; скрипт профілю читає
знімок і еталон декодером `read_png` з `assets/msdf/pack-msdf.py`.

Хвіст у нас на ~0.01 довший за Figma на межі bounds — вона обрізає, гаус тягнеться. Оком
не видно.

## Що далі

- `AComet` / `ASpike` / `ABooster` і решта користувачів `glow` — подивитись, як їм із новою
  пропорцією 42 %; або перейти на `glowTex.image()`.
- Світіння по формі шипа/гексагона — тепер це просто `VfxTexture(w, h, msdf.spike, effect,
  listOf(BlurEffect(blur = B)))` із числом із Figma.
- `TestScreen.addMsdfSandbox()` і `TEST_CIRCLE.png` — де жити, вирішуєш ти; на PNG
  посилаються коментар у `SpriteUtil` і `decisions.md`.
