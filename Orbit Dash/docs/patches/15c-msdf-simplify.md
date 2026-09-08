# Спростити: прибрати `AMsdfGroup`, злити `MsdfBatchImage` в `AMsdfImage`

`bake()` / `bakedImage()` з попередньої картки — **скасовано**, не вставляй.

## Що прибираємо і чому

**`AMsdfGroup` — видалити.** Вона вимагає, щоб **усі** діти були msdf-регіонами
одного атласу. У грі кожна сутність (`ASpike`, `AGem`, `ABooster`, `ABall`) — це своя
група, де msdf-фігура сусідить із PNG-світінням. Плоского списку msdf-фігур не
виникає ніде. Реальних користувачів: нуль (у `TestScreen` лише невикористаний імпорт).

**`MsdfBatchImage` — злити в `AMsdfImage` як приватний клас.** Без групи він
використовується рівно в одному місці — як нутрощі `AMsdfImage`. Публічним він лише
створює ризик, якого ти й побоювався: узяти його замість `AMsdfImage` і отримати
кольорову кашу (він не ставить msdf-шейдер). Приватний — узяти неможливо.

**`val test = VfxTexture(...)` — видалити.** Різку фігуру запікати нема сенсу.

---

## 1. ВИДАЛИТИ файли

```
app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/msdf/AMsdfGroup.kt
app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/msdf/MsdfBatchImage.kt
```

## 2. `actors/vfx/msdf/AMsdfImage.kt` — ЗАМІНИТИ весь файл

```kotlin
package com.lewydo.orbitdash.game.actors.vfx.msdf

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
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
//   setSize / fillParent / setScale / rotation / hit-box — усе про САМУ ФІГУРУ,
//   як у Figma. Поле msdf (pxRange/2 текселів, без нього нічим згладити край)
//   малюється НАЗОВНІ від меж актора — див. MsdfInner нижче.
//
//   Ціна — окремий draw call на актор: VfxImage ставить шейдер у draw().
// ─────────────────────────────────────────────────────────────────────────────
open class AMsdfImage(
    screen: AdvancedScreen,
    region: TextureRegion,
    effect: MsdfShapeEffect = gdxGame.assetsMsdf.effect,
) : VfxImage(screen, TextureRegionDrawable(region), effect, MsdfInner(effect.pxRange))

/**
 * Внутрішній Image для AMsdfImage: малює регіон разом із полем, але поле —
 * ЗА межами прямокутника актора. Тому розмір актора = розмір фігури.
 *
 * Приватний навмисно: msdf-шейдер ставить AMsdfImage, а сам по собі цей Image
 * дасть кольорову кашу з поля відстаней.
 */
private class MsdfInner(private val pxRange: Float) : Image() {

    override fun draw(batch: Batch, parentAlpha: Float) {
        val d = drawable as? TextureRegionDrawable ?: run { super.draw(batch, parentAlpha); return }
        val r = d.region
        validate()

        // Поле з кожного боку в юнітах: pxRange/2 текселів × (юнітів на тексель).
        // Множник по осях різний — на несиметричній клітинці (64×39) саме так і треба.
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

## 3. `manager/util/SpriteUtil.kt`

**ВИДАЛИТИ** рядок 35:

```kotlin
        val test = VfxTexture(186f, 101f, circle, effect).region
```

**ЗАМІНИТИ** коментар над регіонами (рядки ~30–31):

```kotlin
        // ── ВЕКТОР: регіони, назва = ім'я SVG без розширення ────────────────
        //    Малювати через effect: VfxImage(screen, msdf.star, msdf.effect)
        //    або багато одразу — AMsdfGroup(screen, msdf.effect) + Image(msdf.star)
```
→
```kotlin
        // ── ВЕКТОР: регіони, назва = ім'я SVG без розширення ────────────────
        //    Малювати AMsdfImage(screen, msdf.star) — розмір = розмір фігури.
        //    Звичайний Image(msdf.star) дасть кашу: msdf-шейдера в нього немає.
```

**ПОВЕРНУТИ** тестову фігуру на справжню:

```kotlin
        val circle = getRegion("aaa")
```
→
```kotlin
        val circle = getRegion("circle")
```

Імпорт `VfxTexture` лишається — ним користуються закоментовані приклади нижче;
якщо прибереш і їх, прибери й імпорт.

## 4. `assets/msdf/` — прибрати тестову фігуру

Видалити `svg/aaa.svg` і `png/aaa.png`, двоклік `gen-msdf.command`.

## 5. `screens/TestScreen.kt`

Прибрати імпорт `AMsdfGroup` (він і так невикористаний) і, якщо стенд уже не
потрібен, `addMsdfSandbox()` з викликом.

---

## Перевірка

```bash
./gradlew assembleDebug
```

На пристрої — м'яч: коло того ж розміру, різке, колір теми як був.

## Що лишається і чому

- **`VfxTexture`** — користувачів нуль, але це єдиний спосіб дати **одну живу
  текстуру багатьом акторам**, незалежно від сцени й екрана. `VfxGroup` цього не
  вміє (див. нижче). Клас нічого не коштує, поки немає інстансів: `VfxTextures.update()`
  крутить порожній список. Знадобиться — вже є; ні — видалиш одним рухом.
- **`VfxTextures`** — прибрати не можна в будь-якому разі: у ньому `emptyTexture`
  (`TextureEmpty`), яким користуються стилі кнопок, `ALevelPopup`, `ADescription`,
  `SpriteUtil.All.LIGHT`.
