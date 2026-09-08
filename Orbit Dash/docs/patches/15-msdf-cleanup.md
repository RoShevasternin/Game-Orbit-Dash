# Патч 15 — MSDF: прибрати зайве, несиметричні SVG, розмір фігури як у Figma

Рішення 7 вересня 2026:

- **MSDF 9-patch не робимо взагалі.** Розтяжні панелі — растрові `.9.png` в атласі
  `_9_PATCH`, як `panel_coin`. Хелпер `ninePatch`, регіон `rrect`, `DIM` — видалити.
- **`ARoundRect` лишається** — кнопки. Радіус і розмір — у world-юнітах, один в один
  із Figma (шейдер отримує `u_size` = розмір актора, `u_radius` = число, яке ти ввів).
- **`ACircle` / `CircleEffect` / `PolygonEffect` і їхні шейдери — видалити.** Єдиний
  користувач був `ABallDecor`; він переходить на `msdf.circle`. Калібрування блюру
  збережено в `docs/decisions.md` — на випадок, якщо колись знадобиться знову.
- **Несиметричні SVG — працюють.** `gen-msdf.sh` рахує клітинку з `viewBox`;
  `pack-msdf.py` довільні розміри вже пакує. Перевірено: 20×64 → 26×64, 200×100 → 64×36,
  поле 4 текселі з усіх боків, пропорції збережено.
- **`setSize` = розмір фігури, як у Figma.** Поле autoframe малюється НАЗОВНІ від меж
  актора (`MsdfImage`), а не всередині. `fillParent()` дає фігуру в розмір батька, а не 87.5 %.

Порядок: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8. Компілюється після 6 (до того `ABallDecor`
ще посилається на `ACircle`); видаляти файли (7) — після 6.

---

## 1. `assets/msdf/gen-msdf.sh` — клітинка з viewBox

**ЗАМІНИТИ** весь цикл `for f in svg/*.svg; do … done`:

```sh
mkdir -p png
for f in svg/*.svg; do
    n=$(basename "$f" .svg)
    # Клітинка — з viewBox: довша сторона = DIM, коротша пропорційно, плюс
    # PXRANGE поля. 20×64 → 26×64, 200×100 → 64×36. Без цього autoframe
    # вписав би фігуру в квадрат DIM×DIM і половина клітинки пішла б у порожнє.
    dims=$(python3 -c '
import re, sys
svg = open(sys.argv[1]).read(); dim, px = int(sys.argv[2]), int(sys.argv[3])
m = re.search(r"viewBox=\"\s*[-\d.]+\s+[-\d.]+\s+([\d.]+)\s+([\d.]+)", svg)
w, h = (float(m.group(1)), float(m.group(2))) if m else (1.0, 1.0)
k = (dim - px) / max(w, h)
print(round(w * k) + px, round(h * k) + px)' "$f" "$DIM" "$PXRANGE")
    cw=${dims% *}; ch=${dims#* }
    echo "── $n  ${cw}×${ch}"
    msdfgen mtsdf -svg "$f" -dimensions "$cw" "$ch" -pxrange $PXRANGE -autoframe \
            -fillrule evenodd -o "png/$n.png" -printmetrics
done
echo "готово: png/  (PXRANGE=$PXRANGE, DIM=$DIM)"
```

Коментар угорі файлу про `DIM` доповнити: «`DIM` — **довша** сторона клітинки; коротша
рахується з viewBox».

## 2. `assets/msdf/` — прибрати rrect і перегенерувати

**ВИДАЛИТИ** `svg/rrect.svg` і `png/rrect.png` (пакувальник бере все з `png/`, тому
PNG теж). Потім двоклік `gen-msdf.command` — атлас без `rrect`.

## 3. `utils/vfx/effects/base/MsdfShapeEffect.kt`

### 3.1 ДОДАТИ імпорт

```kotlin
import com.lewydo.orbitdash.game.utils.vfx.VfxTextures
```

### 3.2 ЗАМІНИТИ конструктор — `pxRange` стає публічним

**Було:**

```kotlin
class MsdfShapeEffect(
    private val atlas  : Texture,
    private val pxRange: Float,
) : VfxEffect() {
```

**Стало:**

```kotlin
class MsdfShapeEffect(
    private val atlas: Texture,
    /** Ширина діапазону поля в текселях. Потрібен і MsdfImage: поле autoframe = pxRange/2 з кожного боку. */
    val pxRange: Float,
) : VfxEffect() {
```

### 3.3 ЗАМІНИТИ фолбек

**Було:**

```kotlin
    /**
     * Фолбек, коли на пристрої немає GL_OES_standard_derivatives: скільки
     * екранних пікселів припадає на один тексель атласу. Приблизно — але
     * такі пристрої рідкість, і краще трохи м'якший край, ніж жодного.
     */
    var screenPxPerTexel = 3f
```

**Стало:**

```kotlin
    /**
     * Фолбек, коли на пристрої немає GL_OES_standard_derivatives: скільки
     * екранних пікселів припадає на один тексель атласу.
     *
     * Точно порахувати не можна: юніформ один на весь батч, а фігури в ньому
     * різного розміру. DENSITY (px на юніт) — це відповідь для випадку
     * «1 тексель = 1 юніт», тобто клітинка 64 намальована на 64 юніти; для
     * решти — наближення. Такі пристрої рідкість, краще м'якший край, ніж жодного.
     */
    var screenPxPerTexel = VfxTextures.DENSITY
```

## 4. НОВИЙ ФАЙЛ `actors/vfx/msdf/MsdfImage.kt` — поле назовні

```kotlin
package com.lewydo.orbitdash.game.actors.vfx.msdf

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.gdxGame

// ─────────────────────────────────────────────────────────────────────────────
// MsdfImage — Image з msdf-регіоном, у якого setSize() = розмір САМОЇ ФІГУРИ.
//
//   msdfgen лишає навколо фігури поле pxRange/2 текселів — без нього не було б
//   чим згладити край. Звичайний Image малює регіон разом із полем, тож
//   setSize(64, 64) дає фігуру 56. Тут поле малюється НАЗОВНІ від меж актора —
//   як у Figma, де ефект розширює шар, а розмір шару лишається твоїм.
//
//   Тому setSize / fillParent / setScale / rotation / hit-box — усе про фігуру.
//   Група дітей не кліпає, вихід за межі безпечний.
//
//   Куди: діти AMsdfGroup, нутрощі AMsdfImage. Поза msdf-шейдером сенсу не має.
// ─────────────────────────────────────────────────────────────────────────────
open class MsdfImage(
    region: TextureRegion? = null,
    private val pxRange: Float = gdxGame.assetsMsdf.PX_RANGE,
) : Image(region?.let { TextureRegionDrawable(it) }) {

    override fun draw(batch: Batch, parentAlpha: Float) {
        val d = drawable as? TextureRegionDrawable ?: run { super.draw(batch, parentAlpha); return }
        val r = d.region
        validate()

        // Поле з кожного боку в юнітах: pxRange/2 текселів × (юнітів на тексель).
        // Множник по осях різний — на клітинці 26×64 це саме те, що треба.
        val mx = width  * 0.5f * pxRange / (r.regionWidth  - pxRange)
        val my = height * 0.5f * pxRange / (r.regionHeight - pxRange)

        val c = color
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha)
        d.draw(batch,
            x - mx, y - my,                       // квад більший за актора на поле
            originX + mx, originY + my,           // origin — той самий, у новій системі
            width + mx * 2f, height + my * 2f,
            scaleX, scaleY, rotation)
    }
}
```

## 5. `utils/vfx/VfxImage.kt` — внутрішній Image підмінний

**ЗАМІНИТИ** первинний конструктор і поле `image`:

**Було:**

```kotlin
open class VfxImage(
    override val screen: AdvancedScreen,
    drawable           : Drawable?  = null,
    var effect         : VfxEffect? = null,
) : AdvancedGroup() {
```
```kotlin
    private val image = Image()
```

**Стало:**

```kotlin
open class VfxImage(
    override val screen: AdvancedScreen,
    drawable           : Drawable?  = null,
    var effect         : VfxEffect? = null,
    /** Внутрішній Image. Підміняється, коли малювати треба інакше — MsdfImage. */
    inner              : Image      = Image(),
) : AdvancedGroup() {
```
```kotlin
    private val image = inner
```

Вторинні конструктори не чіпати — дефолт підхоплюється.

## 6. `actors/vfx/msdf/AMsdfImage.kt`

**ЗАМІНИТИ** весь файл:

```kotlin
package com.lewydo.orbitdash.game.actors.vfx.msdf

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect

// ─────────────────────────────────────────────────────────────────────────────
// AMsdfImage — «просто SVG-картинка»: MSDF-регіон, різкий на будь-якому розмірі.
//
//   AMsdfImage(screen, msdf.star).apply { setSize(96f, 96f); color = GOLD }
//
//   setSize — розмір самої фігури, як у Figma: поле msdf малюється назовні
//   (MsdfImage). fillParent() дає фігуру в розмір батька.
//
//   Це VfxImage з ефектом атласу, підставленим автоматично. Ціна та сама —
//   окремий draw call на актор. Багато однакових → AMsdfGroup + MsdfImage.
// ─────────────────────────────────────────────────────────────────────────────
open class AMsdfImage(
    screen: AdvancedScreen,
    region: TextureRegion,
    effect: MsdfShapeEffect = gdxGame.assetsMsdf.effect,
) : VfxImage(screen, TextureRegionDrawable(region), effect, MsdfImage(pxRange = effect.pxRange))
```

### 6.1 `actors/objects/decor/ABallDecor.kt` — `ACircle` → `msdf.circle`

Два рядки. `fillParent()` лишається — тепер він дає коло рівно в розмір декору.

```kotlin
import com.lewydo.orbitdash.game.actors.ui.base.ACircle
```
→
```kotlin
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
```

```kotlin
    private val aBall = ACircle(screen)//Image(gdxGame.assetsLoader.circle)
```
→
```kotlin
    private val aBall = AMsdfImage(screen, gdxGame.assetsMsdf.circle)
```

### 6.2 `actors/vfx/AMsdfGroup.kt` — лише коментар і `TestScreen`

Діти групи — `MsdfImage(msdf.star)` замість `Image(msdf.star)`, тоді `setSize(24, 24)` — це
зірка 24. Звичайний `Image` і далі працює, але дає 87.5 %. У коментарі класу приклад
`icons.add(Image(msdf.star)…)` → `MsdfImage(msdf.star)`; те саме в `TestScreen`
(розміри там візуально зростуть на 14 % — це і є правильні).

## 7. ВИДАЛИТИ файли

```
app/src/main/java/com/lewydo/orbitdash/game/actors/ui/base/ACircle.kt
app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/CircleEffect.kt
app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/base/PolygonEffect.kt
app/src/main/assets/shader/base/circle/          (circleFS.glsl)
app/src/main/assets/shader/base/polygon/         (polygonFS.glsl)
```

Інших користувачів немає — перевірено grep'ом по `app/`. У `VfxTexture.kt` лишаються
згадки `CircleEffect` у **коментарях** (рядки ~22, 24, 36) — компіляції не заважають,
але приклад там варто переписати на `base = msdf.star, shape = msdf.effect`.

## 8. `manager/util/SpriteUtil.kt` — прибрати 9-patch і rrect

### 8.1 ВИДАЛИТИ імпорт (рядок 11)

```kotlin
import kotlin.math.roundToInt
```

### 8.2 ЗАМІНИТИ константи

**Було:**

```kotlin
        /** Ті самі числа, що в assets/msdf/gen-msdf.command. Потрібні для ninePatch(). */
        val PX_RANGE = 8f
        val DIM      = 64f
```

**Стало:**

```kotlin
        /** Те саме число, що PXRANGE у assets/msdf/gen-msdf.command. Один на атлас. */
        val PX_RANGE = 8f
```

### 8.3 ВИДАЛИТИ регіон

```kotlin
        val rrect  = getRegion("rrect")     // 64×64, радіус 16
```

### 8.4 ВИДАЛИТИ весь блок 9-patch

Від коментаря `/** 9-patch із MSDF-фігури, що ТЯГНЕТЬСЯ …` до
`val rrect_patch = ninePatch(rrect, svgRadius = 16f)` включно.

### 8.5 Коментар над регіонами

`//    або багато одразу — AMsdfGroup(screen, msdf.effect) + Image(msdf.star)`
→ `+ MsdfImage(msdf.star)`.

---

## Перевірка

```bash
./gradlew assembleDebug
```

На пристрої: м'яч у меню/грі — коло того ж діаметра, що й раніше, край різкий, колір
теми як був. Якщо коло раптом на 12 % менше — `MsdfImage` не підхопився (крок 5).

**Де поле лишається видимим:** `VfxTexture(base = msdf.star)` — це растр, FBO = квад,
і зірка в `star_48` займає 42 з 48. Це єдине місце; там розмір і так «роздільність».
Знадобиться точний розмір запеченої фігури — скажи, зроблю окремо.

## Що лишилось на потім (не в цьому патчі)

- `RoundRectEffect.aaWidth = 1.2f` — це 1.2 **юніта** = 3.6 px на 1080p. Figma
  згладжує в 1 px. Якщо край кнопки видасться м'яким поруч із MSDF-фігурами —
  `aaWidth = 1f / VfxTextures.DENSITY`. Перевіряти на пристрої; `GHOST_STROKE = 2f`
  умову «≥ aaWidth» і далі задовольняє.
- `msdf_shape.glsl`: гілка `screenPxRange()` з похідними тепер мертва (main рахує
  `fwidth(d)` напряму), `u_unitRange` у цій гілці не використовується. Не шкодить.
