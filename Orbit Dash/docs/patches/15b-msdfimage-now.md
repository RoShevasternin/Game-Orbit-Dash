# Зараз: `setShapeSize` → `MsdfImage` (поле назовні)

Стан на момент написання (перевірено по коду): кроки 1, 2, 3, 7, 8 патча 15 вставлені.
Лишилось п'ять правок і збірка. Порядок — як тут, компілюється після кроку 4.

---

## 1. НОВИЙ ФАЙЛ `app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/msdf/MsdfImage.kt`

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

## 2. `utils/vfx/VfxImage.kt` — параметр `inner`

**ЗАМІНИТИ** первинний конструктор (рядки 36–40):

```kotlin
open class VfxImage(
    override val screen: AdvancedScreen,
    drawable           : Drawable?  = null,
    var effect         : VfxEffect? = null,
) : AdvancedGroup() {
```
→
```kotlin
open class VfxImage(
    override val screen: AdvancedScreen,
    drawable           : Drawable?  = null,
    var effect         : VfxEffect? = null,
    /** Внутрішній Image. Підміняється, коли малювати треба інакше — MsdfImage. */
    inner              : Image      = Image(),
) : AdvancedGroup() {
```

**ЗАМІНИТИ** поле (рядок ~51):

```kotlin
    private val image = Image()
```
→
```kotlin
    private val image = inner
```

Три вторинні конструктори не чіпати.

## 3. `actors/vfx/msdf/AMsdfImage.kt` — ЗАМІНИТИ весь файл

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

## 4. `actors/objects/decor/ABallDecor.kt` — назад на `fillParent`

**ВИДАЛИТИ** рядок 46:

```kotlin
        aBall.setShapeSize(width, height)   // коло рівно в розмір декору, поле — зверху
```

**ЗАМІНИТИ** `addBall()` (рядки 57–60):

```kotlin
    private fun addBall() {
        aBall.setShapeSize(width, height)
        add(aBall) { center() }
    }
```
→
```kotlin
    private fun addBall() {
        add(aBall) { fillParent() }
    }
```

## 5. `manager/util/SpriteUtil.kt` — ВИДАЛИТИ невикористаний імпорт (рядок 11)

```kotlin
import kotlin.math.roundToInt
```

Також у коментарі над регіонами (`або багато одразу — AMsdfGroup(...) + Image(msdf.star)`)
→ `+ MsdfImage(msdf.star)`. Необов'язково для збірки.

## 6. `screens/TestScreen.kt` — стенд

`aaa1.setShapeSize(186f, 101f)` → `aaa1.setSize(186f, 101f)`. Тепер `aaa` і `aaa1`
однакові — можеш лишити одного.

Очікування на пристрої: `debug()`-рамка `aaa` **збігається** з `deb` (186×101), а фігура
заповнює рамку до краю. Якщо фігура менша за рамку на ~12 % — `inner` у `VfxImage`
не підставився (крок 2).

Пізніше в `AMsdfGroup`: діти — `MsdfImage(msdf.x)`, не `Image(msdf.x)`.

---

## Збірка

```bash
./gradlew assembleDebug
```
