# 24 — `ABlurBack`: робоча густина окремо від вихідної

Закриває рядок із «Що далі» патча 23: *«у `ABlurBack` злити апскейл у прохід маски —
мінус один повноекранний прохід»*. Зливати не довелось — виявилось, що прохід не мусив
бути повноекранним.

## Що і навіщо

**Причина одна — рядок у `MaskEffect`:**

```kotlin
override fun preferredDensity(): Float = Float.POSITIVE_INFINITY
```

Через нього `VfxGroup.resolveDensity()` віддає екранну густину **всьому ланцюгу**, і
повнорозмірними стають **три** проходи, не один: діти (знімок екрана), апскейл блюру,
маска.

Але маска вимагає різкості не від **входу**, а від власного **виходу**. Їй байдуже, якої
роздільності прийшло розмите тло — важливо, щоб її край ліг у різкий буфер.
`preferredDensity()` змішував ці дві різні вимоги в одне число.

Патч їх розводить: `preferredDensity()` — про **робочий** буфер, новий `outputDensity()` —
про **власний вихід**. Ефект із `outputDensity()` стає **термінальним**: мусить бути
останнім у ланцюгу, отримує окремий буфер вихідної роздільності й сам піднімає джерело
кубічним B-сплайном.

Рахунок для типового попапа (група 360×800 юнітів, екран 1080×2400, `blur = 40`,
σ = 17.04 юніта):

| прохід | зараз (буфер 1080×2400) | після (робочий 270×600, вихід 1080×2400) |
|---|---|---|
| діти (знімок) | 1080×2400 — 2.59 Мпікс | 270×600 — 0.16 |
| піраміда вниз | 3 рівні: 540×1200, 270×600, 135×300 | 1 рівень: 135×300 |
| H + V | 135×300 | 135×300 |
| апскейл блюру | **1080×2400 — 2.59** | 270×600 — 0.16 |
| маска | **1080×2400 — 2.59** | 1080×2400 — 2.59 |
| **блітів / повнорозмірних** | **8 / 3** | **6 / 1** |
| **філ** | **8.7 Мпікс** | **3.03 Мпікс** |
| **FBO в пулі (назавжди)** | **≈ 24.3 МБ** | **≈ 12 МБ** |

Дорогим був не апскейл сам по собі, а те, що він і діти малювались у повний екран. Коли
робочий буфер меншає, апскейл блюру стає 0.16 Мпікс — **зливати його в маску вже нема
сенсу**: це коштувало б контракту «ефект лишив результат у чужому меншому FBO» між
`BlurEffect` і `MaskEffect` заради одного перемикання render target раз на попап.

**Що НЕ змінюється:** `AMask` (маска без блюру) — ніхто не голосує за робочу густину,
вона лишається екранною, вихідний розмір збігається з робочим, і термінальний прохід
бере вже орендований `pingPong.dst`. Байт у байт як було. `ABlur` (блюр без маски) —
термінального ефекту немає взагалі.

Порядок вставки: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8. Компілюється після 6; 1 без 3–5 теж
компілюється (це просто переїзд файлів).

## Файли

| файл | що |
|---|---|
| `assets/shader/base/blur/upsampleCubicFS.glsl` | переїзд із `base/copy/` (`copyFS` лишається там) |
| `utils/vfx/effects/base/BlurEffect.kt` | один рядок шляху |
| `assets/shader/base/mask/maskUpsampleFS.glsl` | **НОВИЙ** — маска + B-сплайн одним проходом |
| `utils/vfx/effects/base/VfxEffect.kt` | +`outputDensity()`, +`renderToOutput()`, KDoc `preferredDensity()` |
| `utils/vfx/effects/base/MaskEffect.kt` | ЗАМІНИТИ ФАЙЛ — термінальний ефект |
| `utils/vfx/VfxGroup.kt` | робоча/вихідна густина в `draw()`; дно авто-густини (зміна 10) |
| `utils/vfx/VfxTexture.kt` | один рядок — `outputDensity` теж у `max` |
| `actors/vfx/ABlurBack.kt` | KDoc + коментар про мінификацію знімка |
| `screens/TestScreen.kt` | стенд (тимчасове) |

---

## 1. `upsampleCubicFS.glsl` → `shader/base/blur/`

Кубічний апскейл — частина блюру й нікому більше не потрібен, тож живе поруч із
`gaussianBlurFS.glsl`. `copyFS.glsl` **лишається в `base/copy/`**: він справді загальний
(уся робота — в лінійному фільтрі), знадобиться ще комусь.

```bash
cd "/Users/admin/Apps/Game Orbit Dash/Orbit Dash/app/src/main/assets/shader/base"
mv copy/upsampleCubicFS.glsl blur/upsampleCubicFS.glsl
```

## 2. `BlurEffect.kt` — ЗАМІНИТИ один рядок

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/BlurEffect.kt`

Було:

```kotlin
    private val upsampleShader get() = VfxShaderCache.get("shader/base/copy/upsampleCubicFS.glsl", Blit.VERT)
```

Стане:

```kotlin
    private val upsampleShader get() = VfxShaderCache.get("shader/base/blur/upsampleCubicFS.glsl", Blit.VERT)
```

`copyShader` не чіпати — `copyFS.glsl` лишається в `base/copy/`.

---

## 3. `app/src/main/assets/shader/base/mask/maskUpsampleFS.glsl` — НОВИЙ ФАЙЛ

```glsl
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// MASK + UPSAMPLE — маска й підняття джерела ОДНИМ проходом.
//
//   Маска — термінальний ефект: робочий буфер групи може бути в кілька разів
//   менший за вихідний (блюру достатньо σ ≈ 12 текселів, масці потрібен
//   різкий край). Тому основна текстура семплиться кубічним B-сплайном, а
//   маска — звичайно, у вихідній роздільності.
//
//   Блок sampleCubic — копія з shader/base/blur/upsampleCubicFS.glsl (GLSL не
//   має #include). Міняєш там — міняй і тут.
// ─────────────────────────────────────────────────────────────────────────────

varying vec2 v_texCoords;
varying vec4 v_color;

uniform sampler2D u_texture;    // робочий буфер (розмите тло)
uniform sampler2D u_mask;       // текстура маски (може бути атласна сторінка)
uniform vec4      u_maskUv;     // UV-межі маски: xy = (u, v), zw = (u2, v2)
uniform vec2      u_srcSize;    // розмір u_texture у текселях

// Кубічний B-сплайн через 4 білінійні семпли (Sigg & Hadwiger, GPU Gems 2, гл. 20)
vec4 sampleCubic(vec2 uv) {
    vec2 coord = uv * u_srcSize - 0.5;
    vec2 f     = fract(coord);
    coord     -= f;

    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
    vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;
    vec2 w3 = f3 / 6.0;

    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 h0 = coord - 1.0 + w1 / g0;
    vec2 h1 = coord + 1.0 + w3 / g1;

    vec2 inv = 1.0 / u_srcSize;
    return g0.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h0.y) + 0.5) * inv)
                 + g1.x * texture2D(u_texture, (vec2(h1.x, h0.y) + 0.5) * inv))
         + g1.y * (g0.x * texture2D(u_texture, (vec2(h0.x, h1.y) + 0.5) * inv)
                 + g1.x * texture2D(u_texture, (vec2(h1.x, h1.y) + 0.5) * inv));
}

void main() {
    // v_texCoords (0..1 по буферу) → ремап у UV-простір регіону маски.
    // 1.0 - y зберігає стару Y-інверсію (FBO перевернутий відносно текстур).
    vec2 maskUV = mix(u_maskUv.xy, u_maskUv.zw, vec2(v_texCoords.x, 1.0 - v_texCoords.y));

    vec4 maskColor = texture2D(u_mask, maskUV);
    vec4 texColor  = sampleCubic(v_texCoords);

    // Приглушуємо і колір, і альфу на прозорих ділянках маски (premultiplied-friendly)
    texColor.rgb *= maskColor.a;
    texColor.a   *= maskColor.a;

    gl_FragColor = texColor * v_color;
}
```

---

## 4. `VfxEffect.kt` — ДОДАТИ два члени, ЗАМІНИТИ KDoc `preferredDensity()`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/VfxEffect.kt`

ДОДАТИ до імпортів:

```kotlin
import com.badlogic.gdx.graphics.glutils.FrameBuffer
```

ЗАМІНИТИ блок `preferredDensity()` цілком (KDoc брехав про маску) на три члени:

```kotlin
    /**
     * МІНІМАЛЬНО потрібна ефекту роздільність РОБОЧОГО буфера, текселів на юніт.
     * null = байдуже (тінт, HSL, маска) — ефект не голосує.
     *
     * Ланцюг бере МАКСИМУМ із вимог (найвибагливіший вирішує), стеля — екранна
     * густина. Блюр просить мало (σ ≈ 12 текселів — розмитій картинці більше не
     * треба). Маска не голосує тут узагалі: різкість потрібна її ВИХОДУ, не
     * входу, — див. outputDensity().
     * Так VfxTexture рахує density у конструкторі, а VfxGroup — щокадру.
     */
    open fun preferredDensity(): Float? = null

    /**
     * Густина ВЛАСНОГО ВИХОДУ, текселів на юніт. null (типово) — ефект пише в
     * робочий буфер разом з усіма.
     *
     * Не-null робить ефект ТЕРМІНАЛЬНИМ: VfxGroup дає йому окремий буфер
     * вихідної роздільності (стеля — екранна) і кличе renderToOutput() замість
     * render(). Такий ефект МУСИТЬ БУТИ ОСТАННІМ у ланцюгу — інакше VfxGroup
     * кине виняток.
     *
     * Сенс: решта ланцюга працює в дешевому робочому буфері, а різкий буфер
     * платиться рівно один раз, на тому проході, якому він справді потрібен.
     */
    open fun outputDensity(): Float? = null

    /**
     * Малює робочий буфер src у вихідний dst. Кличеться замість render() і лише
     * для термінального ефекту. Перевизначити ОБОВ'ЯЗКОВО, якщо outputDensity()
     * не null: розміри можуть не збігатись (читай їх із src/dst), і підняти
     * джерело — обов'язок ефекту.
     */
    open fun renderToOutput(src: FrameBuffer, dst: FrameBuffer, ctx: VfxContext) {}
```

---

## 5. `MaskEffect.kt` — ЗАМІНИТИ ФАЙЛ ЦІЛКОМ

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/MaskEffect.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache

/**
 * Маскування alpha-текстурою. Приймає Texture АБО TextureRegion (з атласу).
 *
 * Внутрішньо все зберігається як TextureRegion — для standalone Texture
 * створюється full-регіон (0,0,1,1), тому шейдер працює однаково.
 * UV регіону передаються в u_maskUv → семплиться тільки ділянка атласу.
 *
 * ─── Термінальний ефект (патч 24) ───────────────────────────────────────────
 * Маска вимагає різкості не від ВХОДУ, а від власного ВИХОДУ: байдуже, якої
 * роздільності прийшло розмите тло — важливо, щоб її край ліг у різкий буфер.
 * Тому вона не голосує в preferredDensity() (це про робочий буфер), а каже
 * своє через outputDensity() і сама малює у вихідний, піднімаючи джерело
 * кубічним B-сплайном, якщо воно менше.
 *
 * До патча 24 тут стояло preferredDensity() = +∞, і екранну густину отримував
 * УВЕСЬ ланцюг: у ABlurBack повнорозмірними ставали три проходи замість одного.
 *
 * Маски нема (maskRegion == null) — нема й ефекту: outputDensity() = null,
 * група поводиться як звичайний ABlur.
 */
class MaskEffect() : VfxEffect() {

    constructor(texture: Texture?) : this() { maskTexture = texture }
    constructor(region: TextureRegion?) : this() { maskRegion = region }

    override val fragmentShader = "shader/base/mask/maskFS.glsl"

    /** Та сама маска + кубічний B-сплайн: джерело менше за приймач. */
    private val upsampleShader get() = VfxShaderCache.get("shader/base/mask/maskUpsampleFS.glsl", Blit.VERT)

    /** Маска як регіон (атлас або full-текстура). Головне сховище. */
    var maskRegion: TextureRegion? = null

    /** Назад-сумісний доступ як Texture. set загортає у full-регіон. */
    var maskTexture: Texture?
        get()      = maskRegion?.texture
        set(value) { maskRegion = value?.let { TextureRegion(it) } }

    /** Різким має бути ВИХІД маски → стеля екрана. Без маски ефекту нема. */
    override fun outputDensity(): Float? = if (maskRegion != null) Float.POSITIVE_INFINITY else null

    /**
     * Робочий буфер → вихідний. Розміри різні — B-сплайн і маска одним проходом;
     * збіглися (AMask, блюру нема) — старий maskFS, байт у байт як до патча 24.
     */
    override fun renderToOutput(src: FrameBuffer, dst: FrameBuffer, ctx: VfxContext) {
        val region     = maskRegion ?: return
        val isUpsample = src.width != dst.width || src.height != dst.height

        Blit.blit(src, dst, if (isUpsample) upsampleShader else shader) { s ->
            s.setUniformi("u_texture", 0)          // unit 0 вже bind-нутий Blit (src)

            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1)
            region.texture.bind(1)
            s.setUniformi("u_mask", 1)             // unit 1 = сторінка маски

            // UV-межі регіону: для full-текстури це (0,0,1,1) — стара поведінка
            s.setUniformf("u_maskUv", region.u, region.v, region.u2, region.v2)

            if (isUpsample) s.setUniformf("u_srcSize", src.width.toFloat(), src.height.toFloat())

            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)  // повертаємо активний unit
        }
    }

    /**
     * Ланцюг без вихідного буфера (VfxTexture.post) — маска звичайним проходом
     * 1:1 на ping-pong. Рівно те, що робив render() до патча 24.
     */
    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (maskRegion == null) return             // pass-through
        renderToOutput(pingPong.src, pingPong.dst, ctx)
        pingPong.swap()
    }

    // autoCache: детектує зміну і текстури, і UV регіону
    override fun stateKey(): Long {
        val r = maskRegion ?: return 0L
        var h = r.texture.hashCode().toLong()
        h = h * 31 + r.u.toRawBits().toLong()
        h = h * 31 + r.v.toRawBits().toLong()
        h = h * 31 + r.u2.toRawBits().toLong()
        h = h * 31 + r.v2.toRawBits().toLong()
        return h
    }
}
```

---

## 6. `VfxGroup.kt` — дві заміни в `draw()`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxGroup.kt`

### 6.1. Розміри буферів

Було:

```kotlin
        // Одна густина на обидві осі: viewport ізотропний, а буфер із різним
        // кроком по X і Y зламав би блюр (u_groupSize рахує крок із bufferW).
        val vp     = stage!!.viewport
        val screenDensity = vp.screenWidth.toFloat() / vp.worldWidth.coerceAtLeast(1f)
        val d      = resolveDensity(screenDensity)
        val outerW = width  + bleed * 2f
        val outerH = height + bleed * 2f
        val bufW   = (outerW * d).toInt().coerceAtLeast(1)
        val bufH   = (outerH * d).toInt().coerceAtLeast(1)
        val ctx    = VfxContext(outerW, outerH, bufW, bufH, pool)
```

Стане:

```kotlin
        // Одна густина на обидві осі: viewport ізотропний, а буфер із різним
        // кроком по X і Y зламав би блюр (u_groupSize рахує крок із bufferW).
        val vp            = stage!!.viewport
        val screenDensity = vp.screenWidth.toFloat() / vp.worldWidth.coerceAtLeast(1f)

        // Термінальний ефект (маска) сам малює у буфер ВИХІДНОЇ роздільності:
        // різкість потрібна його виходу, а не входу. Решта ланцюга працює в
        // дешевому робочому буфері. Такий ефект мусить бути останнім — тиха
        // помилка тут дала б м'який край маски й жодного сліду.
        var terminal: VfxEffect? = null
        for (i in _effects.indices) {
            if (_effects[i].outputDensity() == null) continue
            if (i != _effects.lastIndex) throw IllegalStateException(
                "${this::class.simpleName}: ${_effects[i]::class.simpleName} — " +
                        "термінальний ефект, мусить бути останнім у ланцюгу"
            )
            terminal = _effects[i]
        }

        val workD  = resolveDensity(screenDensity)
        val outD   = terminal?.outputDensity()?.coerceAtMost(screenDensity) ?: workD
        val outerW = width  + bleed * 2f
        val outerH = height + bleed * 2f
        val bufW   = (outerW * workD).toInt().coerceAtLeast(1)
        val bufH   = (outerH * workD).toInt().coerceAtLeast(1)
        val outW   = if (terminal == null) bufW else (outerW * outD).toInt().coerceAtLeast(1)
        val outH   = if (terminal == null) bufH else (outerH * outD).toInt().coerceAtLeast(1)
        val ctx    = VfxContext(outerW, outerH, bufW, bufH, pool)
```

### 6.2. Ланцюг і результат

Було:

```kotlin
        for (effect in _effects) effect.render(pingPong, ctx)

        pool.free(pingPong.dst)
        val resultFbo = pingPong.src
```

Стане:

```kotlin
        val t        = terminal
        val chainEnd = if (t == null) _effects.size else _effects.lastIndex
        for (i in 0 until chainEnd) _effects[i].render(pingPong, ctx)

        // Термінальний: робочий буфер → вихідний (сам піднімає B-сплайном, якщо
        // розміри різні). Розміри збіглися (AMask) — беремо вже орендований dst,
        // зайвого бакета в пулі не з'являється.
        val resultFbo: FrameBuffer
        if (t == null) {
            pool.free(pingPong.dst)
            resultFbo = pingPong.src
        } else {
            val out = if (outW == bufW && outH == bufH) pingPong.dst else pool.obtain(outW, outH)
            t.renderToOutput(pingPong.src, out, ctx)
            pool.free(pingPong.src)
            if (out !== pingPong.dst) pool.free(pingPong.dst)
            resultFbo = out
        }
```

---

## 7. `VfxTexture.kt` — ЗАМІНИТИ один рядок

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxTexture.kt`

Запечена текстура — це і є свій власний вихід, окремого вихідного проходу тут немає.
Тому в `max` мають входити обидві вимоги, інакше маска в `post` мовчки отримала б
густину блюру й м'який край. (Сьогодні маски в `post` ні в кого немає — це оберег наперед.)

Було:

```kotlin
        ?: post.mapNotNull { it.preferredDensity() }.maxOrNull()?.coerceAtMost(VfxTextures.DENSITY)
```

Стане:

```kotlin
        ?: post.flatMap { listOfNotNull(it.preferredDensity(), it.outputDensity()) }
            .maxOrNull()?.coerceAtMost(VfxTextures.DENSITY)
```

І в KDoc над полем: `MaskEffect: екранна` → `MaskEffect: екранна (через outputDensity)`.

---

## 8. `ABlurBack.kt` — два коментарі

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/ABlurBack.kt`

### 8.1. ЗАМІНИТИ KDoc властивості `blur`

Було:

```kotlin
    /** Layer Blur як у Figma, юніти. 0 = вимкнено. Буфер тут повної роздільності — BlurEffect сам іде пірамідою. */
```

Стане:

```kotlin
    /**
     * Layer Blur як у Figma, юніти. 0 = вимкнено.
     *
     * Робочий буфер — густини блюру (σ ≈ 12 текселів), повнорозмірний лише
     * вихідний прохід маски: маска тут термінальний ефект (патч 24).
     */
```

### 8.2. ДОДАТИ коментар над `addAndFillActor(Image(regionScreenShot))` у `addActorsOnGroup()`

```kotlin
        // Знімок береться в екранній роздільності, а лягає в РОБОЧИЙ буфер —
        // тобто зі зменшенням (при blur 40 це 4×). Білінійний фільтр на такій
        // мінификації бере 2×2 текселі з 4×4: аліасинг іде ДО блюру, і гаус
        // його вже не прибере. Знімок статичний, тож мерехтіння не буде — буде
        // трохи «не той» блюр на дрібному тлі (зорі — точки 1–2 px).
        // Мипи не рятують: 1080×2400 — NPOT, glGenerateMipmap на GLES2 для NPOT
        // не гарантований. Якщо на пристрої видно — density = 1.5f явно
        // (один крок ½: 2× білінійно — це чесний box).
        addAndFillActor(Image(regionScreenShot))
```

---

## 9. `TestScreen.kt` — стенд (ТИМЧАСОВЕ)

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/screens/TestScreen.kt`

ДОДАТИ до імпортів:

```kotlin
import com.lewydo.orbitdash.game.actors.vfx.ABlurBack
```

ДОДАТИ поле поруч із `aStarField`:

```kotlin
    // Маска стенда: заокруглений прямокутник, запечений у текстуру. Її край
    // різкий, і саме по ньому видно, чи лишився вихідний прохід повнорозмірним.
    private val maskTex = VfxTexture(200f, 300f, shape = RoundRectEffect().apply { radius = 24f })
```

ДОДАТИ у `show()` після `super.show()`:

```kotlin
        disposableSet.add(maskTex)
```

ЗАМІНИТИ тіло `addMsdfSandbox()`:

```kotlin
    private fun AConstraintLayout.addMsdfSandbox() {
        addBlurBackStand()
    }
```

ДОДАТИ функцію:

```kotlin
    // ── ABlurBack: робоча густина окремо від вихідної. ТИМЧАСОВЕ ─────────────
    // Тло — AStarField (дрібні точки, найгірший випадок для мінификації знімка).
    // isStaticEffect = true: знімок і весь ланцюг раз, далі один квад — саме
    // так це працюватиме в попапі.
    private fun AConstraintLayout.addBlurBackStand() {
        val back = ABlurBack(this@TestScreen).apply {
            setSize(200f, 300f)
            blur           = 40f
            maskRegion     = maskTex.region
            isStaticEffect = true
        }
        add(back) { center() }
    }
```

---

## Перевірка

1. `sh gradlew assembleDebug` — компілятор єдина справжня перевірка.
2. Знімок **до** патча і **після**, за протоколом: `adb install -r --no-streaming`,
   перевірити `Success`, `force-stop`, `monkey`, **один** тап, `cmd statusbar collapse`,
   `screencap` — і знімок подивитись очима, перш ніж читати цифри.
3. Що порівнювати:

| дивитись | очікувано |
|---|---|
| край маски (заокруглення 24) | **попіксельно той самий** — це єдине, що ризикує |
| розмите тло всередині | RMS ≈ 0.003 проти знімка «до» (`compare.py` з `23-blur-tools`) |
| дрібні зорі під плямою | без «бруду» й плям; є — вмикай `density = 1.5f` (див. 8.2) |
| `ADebugHud`, кадр захоплення | 8 блітів → 6 |
| `ADebugHud`, сталий стан | як було: 1 квад, кеш |

4. Пам'ять: тимчасовий лог розмірів `VfxPool.allCreated` після першого показу —
   очікувано зникає бакет 1080×2400 ×2 і три бакети піраміди, лишається
   270×600 ×2 + 135×300 ×2 + один 1080×2400.

## Заміряно на пристрої 14.09.2026 (Redmi 24117RN76E, 1080×2400)

Стенд: три однакові `ABlurBack` 104×300, `blur = 40`, маска — заокруглений прямокутник
із `VfxTexture`; зліва направо робоча густина 0.75 (авто) / 1.5 / 3 (= до патча 24).
Вихідний буфер у всіх 312×900. Контент — смуги 2 юніти (дрібніше за тексель робочого
буфера) і блоки 26 юнітів.

**Край маски — 2 px у всіх трьох.** Те, що ризикувало, не постраждало.

**Нутро — розходиться, і сильно, якщо робоча густина ¼ екранної:**

| контент | 0.75 проти 3 | 1.5 проти 3 |
|---|---|---|
| смуги 2 юніти (6 px) | RMS **0.111**, макс 73/255 | RMS **0.0019**, макс 42 |
| блоки 26 юнітів | RMS 0.0147, макс 16 | RMS **0.0015**, макс 8 |

Причина не в блюрі — у тому, що діти **растеризуються** в робочий буфер: смуга 2 юніти
при 0.75 текселя на юніт це 1.5 пікселя, і покриття втрачається ще до першого проходу
блюру. Піраміда всередині `BlurEffect` такої проблеми не має (там кожен крок ½ — чесний
box 2×2), але вона починає працювати вже ПІСЛЯ того, як контент ліг у буфер.

Звідси зміна 10.

**Кадр:**

| стенд | FPS | мс | draw / bind |
|---|---|---|---|
| 3 × `ABlurBack`, `isStaticEffect = false` (знімок щокадру), усі density 3 | 53 | 19.2, max 32.8 | 30 / 36 |
| те саме, усі density 1.5 | 53 | 19.1, max **29.3** | **27 / 33** |
| 3 × `ABlurBack`, `isStaticEffect = true` + `captureOnce()` | **60** | **16.7** | **6 / 6** |

Тобто у живому режимі ціну диктує **`glCopyTexSubImage2D` знімка**, а не буфери блюру:
густина міняє кількість блітів (−3) і хвіст (`max` 32.8 → 29.3), але не середній кадр.
Виграш патча для `ABlurBack` — **пам'ять, не час**. У режимі, в якому він реально
житиме в попапі (static + `captureOnce`), три замасковані блюри коштують 6 draw і 60 FPS.

**Пастка, яка з'їла півгодини:** `isStaticEffect = true` знімає екран на **першому
кадрі**, коли ще нічого не намальовано, і кешує цю чорноту назавжди. Знімок треба просити
`captureOnce()` тоді, коли контент уже на екрані. Це не патч 24 — так було завжди, просто
`ABlurBack` ніде в грі не використовується і на це ніхто не наступав.

Друга: знімок бачить лише те, що вже лягло у фреймбуфер **цього** кадру. Тло під
`ABlurBack` має бути намальоване раніше — інша сцена (`stageUI`) або попередній за
порядком актор, а не сусід у тому ж `AConstraintLayout`.

---

## 10. `VfxGroup.resolveDensity()` — дно авто-густини ½ екранної

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxGroup.kt`

Було:

```kotlin
        // Ділимо навпіл, поки наступний крок усе ще покриває вимогу
        var d = screenDensity
        while (d * 0.5f >= need) d *= 0.5f
        return d
```

Стане:

```kotlin
        // Ділимо навпіл, поки наступний крок покриває вимогу ефектів І не падає
        // нижче половини екранної. Дно не про блюр, а про ДІТЕЙ: вони
        // растеризуються в цей буфер, і деталь, дрібніша за тексель, гине ще до
        // першого проходу. Заміряно 14.09.2026 на смугах 2 юніти: при ½ екранної
        // RMS проти повної 0.0019, при ¼ — 0.111 (видно оком).
        // Треба нижче — задай density явно: там ти знаєш свій контент.
        val floor = screenDensity * DENSITY_FLOOR
        var d = screenDensity
        while (d * 0.5f >= need && d * 0.5f >= floor) d *= 0.5f
        return d
```

І константа в `companion object` класу (його там ще немає — завести на початку класу):

```kotlin
    companion object {
        /** Дно авто-густини як частка екранної. Нижче гине деталь ДІТЕЙ, не блюру. */
        const val DENSITY_FLOOR = 0.5f
    }
```

Що це міняє в числах для попапа 360×800, `blur = 40`: робочий буфер 540×1200 замість
270×600 (2.6 МБ проти 0.65 на буфер), сумарно ≈ 16.9 МБ замість 24.3 — менш ефектно, ніж
12 МБ, зате картинка збігається з повною роздільністю. Кількість проходів та сама (6).

Це зачіпає й звичайний `ABlur`: авто-густина стане ½ замість ¼ екранної. Заміру 13.09
це не суперечить — там дитина була суцільним помаранчевим квадратом, тобто контентом без
дрібної деталі, і ¼ проходила «візуально ідентично» саме через це.

---

## Чого свідомо не роблю

- **Не зливаю дно піраміди блюру прямо в маску** (буквальне «злити апскейл»). Після
  патча апскейл блюру — 0.16 Мпікс; злиття вимагало б передачі власності чужого
  меншого FBO між двома ефектами й оберега на неспожитий апскейл — заради одного
  перемикання render target раз на попап.
- **Не переношу маску в `drawResult()` батч-шейдером.** Тоді вихідного FBO не треба
  взагалі: 6 блітів → 4, 12 МБ → 1.6 МБ. Ціна — маска й B-сплайн рахуються **щокадру**
  на повний екран (6 вибірок на піксель) замість одного кешованого квада, і `MaskEffect`
  стає третьою категорією ефектів поруч із `VfxGroup`-прохід і `VfxImage`-прохід.
  Варто повернутись, якщо колись 10 МБ у пулі болітимуть більше за філ.
