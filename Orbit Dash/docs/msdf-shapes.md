# Задача 13 — MSDF-фігури: від пари SVG до екрана в п'яти розмірах

## 1. Що і навіщо

Той самий конвеєр, що в тебе для шрифтів, лише замість гліфів — твої фігури:

```
Figma (SVG, один білий шлях)  ──msdfgen──▶  PNG-поле 64×64  ──TexturePacker──▶  atlas/msdf.atlas
                                                                                     │
                                            VfxImage(region, MsdfShapeEffect)  ◀─────┘
                                            = різкий край на БУДЬ-ЯКОМУ розмірі, тінт через color
```

Регіон — звичайний, з атласу, через `SpriteManager` як усі. «Розумний» лише
шейдер: із поля відстаней він відновлює край з точністю до пікселя екрана,
скільки б не розтягнули квад. Ефектів у цій задачі **немає навмисно** — мета
побачити, чи справді MSDF тримає розмір. Ефекти далі — як для будь-якої
текстури (`VfxTexture`, шейдери), або власними SDF-шарами, як у шрифті.

### Що варто знати перед стартом

- **`-pxrange 8` = від −4 до +4 текселів, край на 0.5.** Досяжність ефектів
  усередині шейдера — половина діапазону. Для фігур цього вистачає, бо світіння
  ми по полю **не малюємо**: воно йде поверх, `VfxTexture` + `BlurEffect` або PNG
  (`decisions.md` → «Світіння по полю відстаней — ВІДКИНУТО»). Один `pxrange` на
  весь атлас.
- **`brew install msdfgen` — збірка без Skia**: бере лише **останній** `<path>`,
  ігнорує `transform`, не конвертує `<rect>/<circle>`, не читає `fill-rule`.
  Тому SVG має бути одним сплющеним шляхом, а `evenodd` кажемо явно.
- **Альфа PNG — не прозорість, а справжній SDF** (`mtsdf`). TexturePacker не
  повинен її чіпати: Keep transparent pixels, без premultiply, без trim.
- **`fwidth()` на GLES 2.0 — розширення** (`GL_OES_standard_derivatives`, ~99 %
  пристроїв). Шейдер має фолбек на юніформ.
- **Фільтр — тільки Linear.** Білінійна інтерполяція поля і є «вектор».

---

## 2. Крок 0 — інструмент

```bash
brew install msdfgen
msdfgen -version        # "MSDFgen v1.13" — без слів "with Skia" = правило одного <path>
```

---

## 3. Крок 1 — SVG у Figma

Для кожної фігури:

1. Обвідки → **Outline stroke**. Кілька шарів → виділити все → **Flatten** (⌘E).
   Має лишитись **один** векторний шар.
2. Fill = `#FFFFFF`. Жодних ефектів (блюр, тінь — msdfgen їх не бачить, а
   світіння ти отримаєш шейдером). Градієнтів немає — поле одноколірне.
3. На фреймі вимкнути **Clip content** (інакше додасться `clipPath`, який
   msdfgen проігнорує разом із фігурою всередині).
4. Export → SVG, 1x. У файлі має бути `viewBox` (Figma пише завжди) і один
   `<path … fill="white" fill-rule="evenodd" …/>`.

Назва файлу = назва регіону в коді (`star.svg` → регіон `star`).

---

## 4. Крок 2 — папка і скрипт

```
assets/msdf/
  svg/        ← сюди SVG з Figma
  png/        ← сюди msdfgen кладе поля (генерується)
  gen-msdf.sh
  msdf.tps    ← проєкт TexturePacker (крок 3)
```

**Файл:** `assets/msdf/gen-msdf.sh` — НОВИЙ

```bash
#!/bin/sh
# SVG з Figma → MTSDF-поля. Запускати з папки assets/msdf:  sh gen-msdf.sh
#
# PXRANGE — ширина діапазону поля в текселях (від -PXRANGE/2 до +PXRANGE/2).
#   ОДИН на весь атлас: те саме число має стояти в SpriteUtil.Msdf.PX_RANGE.
# DIM     — ДОВША сторона клітинки в px; коротша рахується з viewBox (20×64 → 26×64).
#   -autoframe вписує фігуру з полем PXRANGE/2 з кожного боку, тобто видима
#   фігура займає (DIM - PXRANGE)/DIM клітинки по довшій осі (56/64 = 87.5%).
#
# Без Skia (brew) msdfgen бере лише ОСТАННІЙ <path> і не читає fill-rule —
# тому SVG має бути одним сплющеним шляхом, а evenodd (як пише Figma) кажемо явно.
set -e
PXRANGE=8
DIM=64

mkdir -p png
for f in svg/*.svg; do
    n=$(basename "$f" .svg)
    echo "── $n"
    # клітинка з viewBox — повний цикл у самому gen-msdf.sh (патч 15)
    msdfgen mtsdf -svg "$f" -dimensions "$cw" "$ch" -pxrange $PXRANGE -autoframe \
            -fillrule evenodd -o "png/$n.png" -printmetrics
done
echo "готово: png/  (PXRANGE=$PXRANGE, DIM=$DIM)"
```

```bash
cd assets/msdf && sh gen-msdf.sh
```

`-printmetrics` друкує `scale` і `bounds` — з них видно, як фігура лягла в
клітинку. Попередження «SVG file contains multiple paths…» означає, що
Flatten не зроблено — фігура вийде неповною.

Відкрий будь-який `png/*.png` у переглядачі: має бути кольорова (RGB-канали
різні) розмита пляма у формі фігури. Це нормально — так виглядає поле.

---

## 5. Крок 3 — зібрати атлас: ОДИН КЛІК

TexturePacker для цього атласу **не потрібен**. Його CLI — платна фіча
Essential-режиму: за її використання він фарбує спрайти червоним (перевірено
побайтово — 4087/4096 текселів зіпсовано). GUI-Publish безкоштовний, але це
зайвий крок, а пакування тут тривіальне.

Замість нього — `assets/msdf/pack-msdf.py`: складає `png/*.png` полицями з
відступами 2 px, копіює текселі **байт-у-байт** (жодного premultiply, trim чи
квантування — поле це дані) і пише `msdf.png` + `msdf.atlas` у форматі libGDX
прямо в `app/src/main/assets/atlas/`.

Увесь конвеєр — подвійний клік по **`assets/msdf/gen-msdf.command`**
(як `msdf-gen.command` у шрифтів):

```
свіжий SVG у svg/  →  клік по gen-msdf.command  →  msdf.atlas + msdf.png уже в ассетах
```

Перевірка: у `msdf.atlas` мають бути рядки `format: RGBA8888` і
`filter: Linear, Linear`, а регіони — по одному на кожен SVG.

`msdf.tps` можна лишити як запасний варіант (GUI-Publish дає той самий
результат), але в основному потоці він не бере участі.

---

## 6. Крок 4 — код (порядок вставки)

1. `assets/shader/base/msdf/msdf_shape.glsl` — новий
2. `utils/vfx/effects/MsdfShapeEffect.kt` — новий
3. `manager/SpriteManager.kt` — один рядок
4. `manager/util/SpriteUtil.kt` — новий клас `Msdf`
5. `GDXGame.kt` — один рядок
6. `screens/MenuScreen.kt` — стенд

### 6.1 `msdf_shape.glsl` — НОВИЙ ФАЙЛ

**Файл:** `app/src/main/assets/shader/base/msdf/msdf_shape.glsl`

```glsl
#ifdef GL_ES
#extension GL_OES_standard_derivatives : enable
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

// ─────────────────────────────────────────────────────────────────────────────
// MSDF-ФІГУРА — різка заливка з поля відстаней на будь-якому розмірі квада.
//
//   RGB = median → гострі кути (MSDF). A = справжній SDF (mtsdf) — тут лише як
//   глушник median-шуму в глибокій порожнечі; для ефектів — пізніше.
//
//   Юніформи ЛИШЕ на атлас (u_unitRange) — тому будь-яка кількість квадів
//   різного розміру з одного атласу може йти одним батчем. Масштаб береться
//   з fwidth(uv) щофрагмента (msdfgen README). Без похідних (рідкість на
//   GLES 2.0) — фолбек u_screenPxRange, як у msdf_fill.
//
//   Правило msdfgen: screenPxRange ніколи < 1; якщо < 2 — антиаліас ламається,
//   треба більший -pxrange. На 64px клітинці з pxrange 8 це стається при
//   зменшенні фігури до ~16 px на екрані.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_texCoords;

uniform sampler2D u_texture;
uniform vec2  u_unitRange;      // pxRange / vec2(atlasW, atlasH)
uniform float u_screenPxRange;  // фолбек: pxRange * (px екрана на тексель)

float median(vec3 c) { return max(min(c.r, c.g), min(max(c.r, c.g), c.b)); }

float screenPxRange() {
#if defined(GL_OES_standard_derivatives) || !defined(GL_ES)
    vec2 screenTexSize = vec2(1.0) / fwidth(v_texCoords);
    return max(0.5 * dot(u_unitRange, screenTexSize), 1.0);
#else
    return max(u_screenPxRange, 1.0);
#endif
}

void main() {
    vec4 t = texture2D(u_texture, v_texCoords);

    // Далеко за контуром окремі RGB-канали «перемикаються», і median може
    // стрибнути >0.5 — біла точка в порожньому полі. Справжній SDF монотонний:
    // глушимо ним. 0.2 ≈ далі 2.4 текселя від контуру при pxrange 8.
    if (t.a < 0.2) discard;

    float d     = median(t.rgb) - 0.5;
    float alpha = clamp(d * screenPxRange() + 0.5, 0.0, 1.0);
    if (alpha < 0.004) discard;

    gl_FragColor = vec4(v_color.rgb, v_color.a * alpha);
}
```

### 6.2 `MsdfShapeEffect.kt` — НОВИЙ ФАЙЛ

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/MsdfShapeEffect.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// MsdfShapeEffect — малює MSDF-фігуру з атласу різко на будь-якому розмірі.
//
//   Один інстанс на атлас: юніформи залежать лише від розміру сторінки та
//   pxRange, з яким згенеровано поля. Працює і як ефект VfxImage (живий актор,
//   будь-який розмір), і як shape у VfxTexture (запекти в звичайну текстуру —
//   для батчингу зі змішаними стилями або для post-ефектів).
//
//   Досяжність майбутніх ефектів (обводка, світіння) — pxRange / 2 текселі.
// ─────────────────────────────────────────────────────────────────────────────
class MsdfShapeEffect(
    private val atlas  : Texture,
    private val pxRange: Float,
) : VfxEffect() {

    override val fragmentShader = "shader/base/msdf/msdf_shape.glsl"

    /**
     * Фолбек, коли на пристрої немає GL_OES_standard_derivatives: скільки
     * екранних пікселів припадає на один тексель атласу. Приблизно — але
     * такі пристрої рідкість, і краще трохи м'якший край, ніж жодного.
     */
    var screenPxPerTexel = 3f

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_unitRange", pxRange / atlas.width, pxRange / atlas.height)
        shader.setUniformf("u_screenPxRange", pxRange * screenPxPerTexel)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + pxRange.toRawBits()
        k = k * 31 + screenPxPerTexel.toRawBits()
        return k
    }
}
```

### 6.3 `SpriteManager.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/manager/SpriteManager.kt`

#### ЗАМІНИТИ — `EnumAtlas`, рядки 61–67

Було:

```kotlin
    enum class EnumAtlas(val data: AtlasData) {
        BRAND(AtlasData("atlas/brand.atlas")),

        LOADER(AtlasData("atlas/loader.atlas")),

        ALL     (AtlasData("atlas/all.atlas")),
        _9_PATCH(AtlasData("atlas/9_patch.atlas")),
    }
```

Стане:

```kotlin
    enum class EnumAtlas(val data: AtlasData) {
        BRAND(AtlasData("atlas/brand.atlas")),

        LOADER(AtlasData("atlas/loader.atlas")),

        ALL     (AtlasData("atlas/all.atlas")),
        _9_PATCH(AtlasData("atlas/9_patch.atlas")),
        MSDF    (AtlasData("atlas/msdf.atlas")),     // поля відстаней фігур (msdfgen), див. SpriteUtil.Msdf
    }
```

Вантажиться сам: `loadAssets()` бере `EnumAtlas.entries` цілком.

### 6.4 `SpriteUtil.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`

#### ДОДАТИ — до імпортів

```kotlin
import com.badlogic.gdx.graphics.Texture
import com.lewydo.orbitdash.game.utils.vfx.effects.MsdfShapeEffect
```

#### ДОДАТИ — новий клас між `class Shapes` і `class All`

```kotlin
    // ------------------------------------------------------------------------
    //  MSDF-фігури: SVG з Figma → msdfgen → atlas/msdf.atlas. Регіони звичайні,
    //  «розумний» лише шейдер при малюванні (MsdfShapeEffect): він відновлює
    //  різкий край на будь-якому розмірі з поля відстаней.
    //
    //  PX_RANGE — ширина діапазону поля в текселях, та сама, що в gen-msdf.sh
    //  (-pxrange). ОДИН на весь атлас: усі PNG в ньому згенеровані з ним.
    //  Досяжність майбутніх ефектів (обводка, світіння) = PX_RANGE / 2 текселі.
    //
    //  Альфа в цих PNG — не прозорість, а справжній SDF. Тому в TexturePacker:
    //  Alpha handling = Keep transparent pixels, Premultiply alpha = off,
    //  Trim = None. Фільтр — тільки Linear: білінійна інтерполяція поля і є
    //  «вектор»; Nearest дасть сходинки. Тут виставляємо примусово.
    //
    //  Видима фігура менша за квад: -autoframe лишає PX_RANGE/2 текселів поля
    //  з кожного боку (на 64px клітинці фігура займає 56/64). Як 2×blur у кола.
    //
    //  Використання (живий актор, будь-який розмір):
    //      VfxImage(screen, gdxGame.assetsMsdf.star, gdxGame.assetsMsdf.effect)
    // ------------------------------------------------------------------------
    class Msdf {
        val PX_RANGE = 8f

        /** Сторінка атласу. Одна: кілька іконок у 1024² вміщаються з запасом. */
        val texture: Texture = SpriteManager.EnumAtlas.MSDF.data.atlas.textures.first().apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }

        /** Спільний ефект на всі фігури цього атласу — юніформи в них однакові. */
        val effect = MsdfShapeEffect(texture, PX_RANGE)

        // ── регіони: назва = ім'я SVG без розширення ────────────────────────
        val star = SpriteManager.EnumAtlas.MSDF.region("star")
    }
```

#### ДОДАТИ — приватний хелпер у кінець файлу `SpriteUtil.kt` (поза класом)

Один на всі холдери — щоб не повторювати `?: error(...)` у кожному класі.
Старі `getRegion` у `Brand` / `Loader` / `All` можна поступово перевести на нього.

```kotlin
/** Спільний геттер регіона: сам знає шлях атласу, тож помилка каже, ЩО перепакувати. */
private fun SpriteManager.EnumAtlas.region(name: String): TextureRegion =
    data.atlas.findRegion(name)
        ?: error("Регіон '$name' відсутній в ${data.path} — перепакуй атлас")
```

### 6.5 `GDXGame.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/GDXGame.kt`

#### ЗАМІНИТИ — блок `Assets`

Було:

```kotlin
    val assetsBrand  by lazy { SpriteUtil.Brand() }
    val assetsLoader by lazy { SpriteUtil.Loader() }
    val assetsAll    by lazy { SpriteUtil.All() }
    val assetsShapes by lazy { SpriteUtil.Shapes() }   // спільні VfxTexture; торкаємось у create()
```

Стане:

```kotlin
    val assetsBrand  by lazy { SpriteUtil.Brand() }
    val assetsLoader by lazy { SpriteUtil.Loader() }
    val assetsAll    by lazy { SpriteUtil.All() }
    val assetsShapes by lazy { SpriteUtil.Shapes() }   // спільні VfxTexture; торкаємось у create()
    val assetsMsdf   by lazy { SpriteUtil.Msdf() }     // MSDF-фігури; ТІЛЬКИ після initAssets()
```

### 6.6 Стенд — `TestScreen.kt` (вже створений)

Стенд живе в окремому `TestScreen` (створений 6 вересня, зареєстрований у
`NavigationManager`). Відкрити його: тимчасово в `LoaderScreen.isFinish()`
замінити ціль навігації з `MenuScreen` на `TestScreen`:

```kotlin
gdxGame.navigationManager.navigate(
    toScreenName = TestScreen::class.java.name,   // ТИМЧАСОВО замість MenuScreen
)
```

Важливо: `assetsMsdf` можна чіпати лише ПІСЛЯ `initAssets()` — атлас `MSDF`
вантажиться загальною хвилею `loadAssets()`. `TestScreen` відкривається після
лоадера, тож конструктори його акторів уже в безпеці.

Оригінальний блок стенда (якщо треба відтворити) — нижче.

### 6.6-довідково: код стенда

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/screens/MenuScreen.kt`

#### ДОДАТИ — до імпортів

```kotlin
import com.badlogic.gdx.graphics.Color
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
```

#### ЗАМІНИТИ — `addActorsOnRootConstraintLayout()`

Було:

```kotlin
    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        add(aMain) { fillParent() }
        addActor(aTitlesAnchor)

        //addPanels()

        addDebugHud(ADebugHud(this@MenuScreen))
    }
```

Стане:

```kotlin
    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        add(aMain) { fillParent() }
        addActor(aTitlesAnchor)

        //addPanels()

        addMsdfSandbox()

        addDebugHud(ADebugHud(this@MenuScreen))
    }

    // ------------------------------------------------------------------------
    // MSDF sandbox — ТИМЧАСОВЕ: та сама фігура в п'яти розмірах з ОДНОГО регіона
    // ------------------------------------------------------------------------
    //
    //    y=560   [5] сире поле без шейдера (кольорова пляма — так і має бути)
    //    y=220   [4] 320, з тінтом
    //    y=72    [0] 24   [1] 48   [2] 96   [3] 128
    //
    //  Що дивитись: край однаково різкий на 24 і на 320; кути гострі; дірки
    //  (evenodd) на місці; фігура не догори ногами; тінт працює.
    //
    private fun AConstraintLayout.addMsdfSandbox() {
        val msdf = gdxGame.assetsMsdf

        fun icon(size: Float) = VfxImage(this@MenuScreen, msdf.star, msdf.effect).apply {
            setSize(size, size)
        }

        at(22f,  72f,  icon(24f))
        at(54f,  72f,  icon(48f))
        at(110f, 72f,  icon(96f))
        at(214f, 72f,  icon(128f))
        at(20f,  220f, icon(320f).apply { color = Color.valueOf("4dd9ff") })

        // Сире поле звичайним Image, без шейдера: має бути кольорова розмита
        // пляма. Якщо тут порожньо — атлас не завантажився або регіон не знайдено.
        at(22f, 560f, Image(msdf.star).apply { setSize(96f, 96f) })
    }

    /** Поставити актора в лівий-нижній кут root. Розмір актор задає сам. */
    private fun AConstraintLayout.at(x: Float, y: Float, actor: Actor) {
        actor.debug()
        add(actor) { startToStart(margin = x); bottomToBottom(margin = y) }
    }
```

---

## 7. Крок 5 — запуск і що дивитись

```bash
sh ./gradlew assembleDebug installDebug
```

| бачиш | значить | що робити |
|---|---|---|
| край різкий на 24 і на 320, кути гострі | **працює** | далі — ефекти |
| фігура догори ногами | орієнтація Y у цій збірці msdfgen | додай `-yflip` у скрипт, перегенеруй |
| фігура неповна / без дірок | Flatten не зроблено, або `evenodd` не той | один `<path>` у SVG; спробуй без `-fillrule evenodd` |
| білі цятки в порожньому полі | median-шум далеко від контуру | у шейдері поріг `t.a < 0.2` → `0.3`; або більший `DIM` |
| край м'який / «мильний» на 24 | `screenPxRange < 2` | більший `PXRANGE` (12–16) при більшому `DIM` (96–128) |
| край «хвилястий» на 320 | замало текселів на кривих | `DIM` 128, `PXRANGE` пропорційно 16 |
| пляма внизу є, іконки немає | шейдер не скомпілювався | логкат: `VfxShaderCache: не вдалось скомпілювати` — покажи мені лог |
| внизу порожньо | атлас не завантажився / регіон не той | ім'я в `getRegion("…")` = ім'я SVG без розширення; `msdf.atlas` у `assets/atlas/` |

Ще два спостереження, які варто зробити свідомо:

- **Візуальний розмір ≠ розмір актора.** Фігура займає 56/64 квада (поле
  `PXRANGE/2` з кожного боку). Це та сама історія, що з `2×blur` у кола.
- **Draw calls.** Кожен `VfxImage` тут — окремий виклик (юніформи на актор).
  Для «сто однакових одним викликом» наступний крок — група, що ставить шейдер
  раз і малює звичайні `Image`. Для тесту розміру це неважливо.

---

## 8. Що далі

**Як цим користуватись у грі — `docs/msdf-usage.md`.** Нижче — історичний список.

### Було в плані

- **Ефекти «як для текстур»**: `VfxTexture(w, h, base = msdf.star, shape = msdf.effect, post = listOf(BlurEffect(…)))` — запікає різку фігуру потрібної роздільності й дає справжнє гаусове світіння. Ефекти всередині шейдера (обводка/тінь по полю, як у шрифті) теж можливі — досяжність `PX_RANGE/2`.
- **Батчинг**: `AMsdfGroup`, що виставляє шейдер раз на групу. — **ВИДАЛЕНО**:
  вимагала, щоб усі діти були msdf-регіонами, користувачів у грі не знайшлось
  (`decisions.md` → «`AMsdfGroup` — ВИДАЛЕНО»).
- **Фігури під світіння по полю**: окремий атлас із `-apxrange -24 4`, `DIM 128`. —
  **ВІДКИНУТО**: другий атлас зі своїм `pxrange` заради світіння, яке все одно
  спадає за відстанню, а не гаусом. Світіння робимо `VfxTexture` + `BlurEffect` або
  PNG `item_glow` (`decisions.md` → «Світіння по полю відстаней — ВІДКИНУТО»).
- **`CLAUDE.md`**: розділ «Процедурні фігури (`utils/shapes`)» описує видалений каталог — переписати під `VfxTexture` + MSDF.
