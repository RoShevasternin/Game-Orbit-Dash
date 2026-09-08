# Ефекти назовні: `bleed` — модель Figma для `VfxTexture` і `VfxGroup`

## Модель

У Figma шар має межі фігури, а ефект (блюр, тінь, світіння) вільно виходить за них.
Треба вирівнятись з урахуванням ефекту — обгортаєш у frame, і він обіймає ефект.

У нас тепер так само, одним поняттям — **`bleed`**: поле під ефект **назовні** від
фігури, на бік, у world-юнітах.

| у Figma | у нас |
|---|---|
| шар із фігурою | `AMsdfImage` / `Image` — межі = фігура |
| шар + Layer Blur | `VfxTexture(w, h, …, post = [Blur], bleed = 24f).image()` або `VfxGroup` з `bleed = 24f` — межі = фігура, ефект назовні |
| frame з «hug contents», що обіймає ефект | `Image(tex.region)` розміром `tex.outerWidth × tex.outerHeight` |

Одна деталь одна на всіх: **`OverflowImage`** — `Image`, чий регіон ширший за актора
на задані частки. Ним малюється msdf-поле в `AMsdfImage` і bleed у `VfxTexture`;
`VfxGroup` робить те саме сам у `drawResult`.

Порядок: 1 → 2 → 3 → 4 → 5 → 6. Компілюється після 2, після 3, після 4; кроки 5 і 6 —
разом (5 прибирає `aaaTexture`, 6 прибирає його вживання у стенді). Крок 4 (`VfxGroup`)
окремий: із `bleed = 0` поведінка байт-у-байт та сама, `ABlurBack` / `AMask` / `ABlur`
не помічають.

Перевірено: три незалежні рецензії по коду + `assembleDebug` у лабораторній копії з
усіма кроками — зібралось.

---

## 1. НОВИЙ ФАЙЛ `utils/vfx/OverflowImage.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

// ─────────────────────────────────────────────────────────────────────────────
// OverflowImage — Image, чий регіон ШИРШИЙ за актора: зайве малюється назовні.
//
//   Модель Figma: межі шару = фігура, а ефект (блюр, тінь, світіння) виходить
//   за них. Тут те саме: setSize / fillParent / hit-box — про фігуру, а поле
//   регіона (msdf-відступ або bleed під ефект) лягає ЗА межі прямокутника.
//
//   padX / padY — поле з КОЖНОГО боку як частка ширини / висоти актора.
//   open: AMsdfImage перевизначає їх геттерами, щоб рахувати з живого регіона.
//
//   Звідки частки:
//     AMsdfImage          — з клітинки msdf: 0.5·pxRange / (cell − pxRange)
//     VfxTexture.image()  — bleed / width, bleed / height
//
//   Межі: звичайна Group не кліпає, вихід за межі безпечний. Усередині VfxGroup
//   поле обріже FBO — там потрібен bleed самої групи. Scaling / Align в Image
//   не підтримуються: малюється рівно width × height плюс поле.
// ─────────────────────────────────────────────────────────────────────────────
open class OverflowImage(
    region: TextureRegion? = null,
    padX: Float = 0f,
    padY: Float = 0f,
) : Image(region?.let { TextureRegionDrawable(it) }) {

    open val padX: Float = padX
    open val padY: Float = padY

    override fun draw(batch: Batch, parentAlpha: Float) {
        val d = drawable as? TextureRegionDrawable ?: run { super.draw(batch, parentAlpha); return }
        validate()

        val mx = width  * padX
        val my = height * padY

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

## 2. `actors/vfx/msdf/AMsdfImage.kt` — ЗАМІНИТИ весь файл

`MsdfInner` зникає — це той самий `OverflowImage` із частками з клітинки.

```kotlin
package com.lewydo.orbitdash.game.actors.vfx.msdf

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.OverflowImage
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect

// ─────────────────────────────────────────────────────────────────────────────
// AMsdfImage — «просто SVG-картинка»: MSDF-регіон, різкий на будь-якому розмірі.
//
//   AMsdfImage(screen, msdf.star).apply { setSize(96f, 96f); color = GOLD }
//
//   setSize / fillParent / setScale / rotation / hit-box — усе про САМУ ФІГУРУ,
//   як у Figma. Поле msdf (pxRange/2 текселів, без нього нічим згладити край)
//   малюється НАЗОВНІ від меж актора — OverflowImage із частками з клітинки.
//
//   Ціна — окремий draw call на актор: VfxImage ставить шейдер у draw().
//   Потрібен ефект (блюр, світіння) — це VfxTexture або VfxGroup із bleed.
// ─────────────────────────────────────────────────────────────────────────────
open class AMsdfImage(
    screen: AdvancedScreen,
    region: TextureRegion,
    effect: MsdfShapeEffect = gdxGame.assetsMsdf.effect,
) : VfxImage(screen, TextureRegionDrawable(region), effect, MsdfOverflow(effect.pxRange))

/**
 * Частки поля — з ЖИВОГО регіона, щокадру: підмінив drawable на іншу клітинку
 * (64×64 → 64×39) — поле перерахувалось. Різні по осях, бо клітинка несиметрична.
 */
private class MsdfOverflow(private val pxRange: Float) : OverflowImage() {
    private val region get() = (drawable as? TextureRegionDrawable)?.region
    override val padX get() = region?.let { 0.5f * pxRange / (it.regionWidth  - pxRange) } ?: 0f
    override val padY get() = region?.let { 0.5f * pxRange / (it.regionHeight - pxRange) } ?: 0f
}
```

## 3. `utils/vfx/VfxTexture.kt`

### 3.1 ДОДАТИ імпорт (якщо ще немає після 15d)

```kotlin
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect
```

### 3.2 ЗАМІНИТИ блок «Розмір» у коментарі класу

**Було:**

```kotlin
// ─── Розмір ─────────────────────────────────────────────────────────────────
//   width/height — у world-юнітах, як у актора. Це РОЗДІЛЬНІСТЬ текстури, а не
//   розмір на екрані: регіон розтягнеться під Image. Параметри ефектів (blur,
//   radius, strokeWidth) — теж у world-юнітах, рівно як у ACircle: те, що ти
//   налаштував живцем, тут виглядатиме так само.
```

**Стало:**

```kotlin
// ─── Розмір і bleed — модель Figma ──────────────────────────────────────────
//   width/height — розмір ФІГУРИ у world-юнітах, як у Figma: квад бази
//   розширюється на msdf-поле, і фігура лягає рівно в (0,0)-(width,height).
//   bleed — поле під post-ефекти НАЗОВНІ від фігури, на бік. FBO = фігура +
//   bleed з обох боків. Без bleed блюру нема куди розпливтись — його зріже.
//   Msdf-поле бази при bleed = 0 вилітає за FBO; при bleed > 0 лягає в поле
//   і лишається прозорим завдяки discard у msdf-шейдері.
//   Скільки треба: ≈ 9 × radius / density юнітів (чотири проходи по 4 кроки
//   по radius текселів); краще з запасом.
//
//   Малювати:
//     image()                       — шар: межі = фігура, ефект назовні
//     Image(region) outerW × outerH — frame, що обіймає ефект (для вирівнювання)
//
//   Це РОЗДІЛЬНІСТЬ текстури: density пікселів на юніт, регіон розтягнеться
//   під актора.
```

### 3.3 ЗАМІНИТИ конструктор і поля розміру

**Було:**

```kotlin
class VfxTexture(
    val width  : Float,
    val height : Float,
    base       : TextureRegion?   = null,        // null → білий квад
    val shape  : VfxEffect?       = null,        // малює базу
    val post   : List<VfxEffect>  = emptyList(), // обробляє результат
    val density: Float            = VfxTextures.DENSITY,
) : Disposable {

    /** Стабільний регіон: той самий об'єкт назавжди, вміст під ним оновлюється. */
    val region = TextureRegion(VfxTextures.emptyTexture)

    /** База. Зміна → перемалювання наступним кадром. */
    var base: TextureRegion? = base
        set(value) { field = value; invalidate() }

    private val bufW = ceil(width  * density).toInt().coerceAtLeast(1)
    private val bufH = ceil(height * density).toInt().coerceAtLeast(1)
    private val ctx  = VfxContext(width, height, bufW, bufH)
```

**Стало:**

```kotlin
class VfxTexture(
    val width  : Float,                          // розмір ФІГУРИ, як у Figma
    val height : Float,
    base       : TextureRegion?   = null,        // null → білий квад
    val shape  : VfxEffect?       = null,        // малює базу
    val post   : List<VfxEffect>  = emptyList(), // обробляє результат
    val density: Float            = VfxTextures.DENSITY,
    val bleed  : Float            = 0f,          // поле під post НАЗОВНІ від фігури, на бік
) : Disposable {

    /** Стабільний регіон: той самий об'єкт назавжди, вміст під ним оновлюється. */
    val region = TextureRegion(VfxTextures.emptyTexture)

    /** База. Зміна → перемалювання наступним кадром. */
    var base: TextureRegion? = base
        set(value) { field = value; invalidate() }

    /** Повний розмір текстури в юнітах: фігура + bleed з обох боків. Для frame-варіанта. */
    val outerWidth  = width  + bleed * 2f
    val outerHeight = height + bleed * 2f

    /** Поле як частка фігури — для OverflowImage. 0 при bleed = 0. */
    val padX get() = if (width  > 0f) bleed / width  else 0f
    val padY get() = if (height > 0f) bleed / height else 0f

    private val bufW = ceil(outerWidth  * density).toInt().coerceAtLeast(1)
    private val bufH = ceil(outerHeight * density).toInt().coerceAtLeast(1)

    /** Контекст post-ефектів — увесь буфер, разом із bleed. */
    private val ctx      = VfxContext(outerWidth, outerHeight, bufW, bufH)
    /** Контекст shape-ефекту — сама фігура: u_size у RoundRectEffect має бути її розміром. */
    private val shapeCtx = VfxContext(width, height, bufW, bufH)
```

### 3.4 ДОДАТИ після `invalidate()` (блок API)

```kotlin
    /**
     * Актор у моделі Figma: межі = фігура width×height, bleed з ефектом виходить
     * назовні. Потрібна рамка, що обіймає ефект, — звичайний Image(region)
     * розміром outerWidth × outerHeight.
     */
    fun image() = OverflowImage(region, padX, padY)
```

### 3.5 ЗАМІНИТИ `resized()`

```kotlin
    fun resized(width: Float = this.width, height: Float = this.height, density: Float = this.density) =
        VfxTexture(width, height, base, shape, post, density)
```
→
```kotlin
    fun resized(
        width  : Float = this.width,
        height : Float = this.height,
        density: Float = this.density,
        bleed  : Float = this.bleed,
    ) = VfxTexture(width, height, base, shape, post, density, bleed)
```

### 3.6 ЗАМІНИТИ у `drawBase()` — проєкцію і контекст shape

**Було:**

```kotlin
        // Проєкція у world-юнітах: квад (0,0)-(width,height) заповнює FBO цілком,
        // а шейдер отримує u_size у тих самих юнітах, що й у VfxImage.
        proj.setToOrtho2D(0f, 0f, width, height)
```
→
```kotlin
        // Проєкція у world-юнітах: фігура — квад (0,0)-(width,height), навколо
        // неї bleed. Разом вони заповнюють FBO цілком.
        proj.setToOrtho2D(-bleed, -bleed, outerWidth, outerHeight)
```

**Було:**

```kotlin
            fx.setUniforms(sp, ctx)
```
→
```kotlin
            fx.setUniforms(sp, shapeCtx)   // u_size = фігура, не весь буфер
```

Блок малювання квада (з msdf-виносом із 15d) лишається як є: квад `(0,0)-(width,height)`
розширений на msdf-поле тепер стоїть посеред bleed. Лише коментар у ньому:

```kotlin
        // а в текстуру лягає сама фігура, край у край. Те саме, що MsdfInner
        // робить на екрані; без цього у 186×101 запеклася б фігура 163×80.
```
→
```kotlin
        // а в текстуру лягає сама фігура, край у край. Те саме, що OverflowImage
        // робить у AMsdfImage; без цього у 186×101 запеклася б фігура 163×80.
```
(якщо текст коментаря в тебе трохи інший — просто заміни `MsdfInner` на `OverflowImage`).

## 4. `utils/vfx/VfxGroup.kt` — живий ефект назовні (окремо, за потреби)

### 4.1 ДОДАТИ поле після `autoCache`

```kotlin
    /**
     * Поле під ефекти НАЗОВНІ від меж групи, на бік, у world-юнітах — модель
     * Figma: межі шару = контент, блюр / тінь виходять за них. FBO більший на
     * bleed з кожного боку, результат малюється зі зсувом; hit-box, layout і
     * діти лишаються про контент. 0 — як було.
     *
     * Лишати 0 у ABlurBack і AMask: знімок екрана береться рівно з width×height,
     * а маска лягає на весь буфер разом із полем. bleed — для блюру й світіння
     * над власними дітьми.
     */
    var bleed = 0f
        set(value) {
            if (field == value) return
            field = value
            if (width > 0f && height > 0f) setupCamera()
            needsUpdate = true
        }
```

### 4.2 ЗАМІНИТИ розмір буфера у `draw()`

**Було:**

```kotlin
        val bufW   = (width  * scaleX).toInt().coerceAtLeast(1)
        val bufH   = (height * scaleY).toInt().coerceAtLeast(1)
        val ctx    = VfxContext(width, height, bufW, bufH)
```
→
```kotlin
        val outerW = width  + bleed * 2f
        val outerH = height + bleed * 2f
        val bufW   = (outerW * scaleX).toInt().coerceAtLeast(1)
        val bufH   = (outerH * scaleY).toInt().coerceAtLeast(1)
        val ctx    = VfxContext(outerW, outerH, bufW, bufH)
```

### 4.3 ЗАМІНИТИ `drawResult()` — рядок `batch.draw`

```kotlin
        batch.draw(region, x, y, originX, originY, width, height, scaleX, scaleY, rotation)
```
→
```kotlin
        // Результат ширший за групу на bleed з кожного боку — те саме, що OverflowImage
        val b = bleed
        batch.draw(region,
            x - b, y - b, originX + b, originY + b,
            width + b * 2f, height + b * 2f,
            scaleX, scaleY, rotation)
```

### 4.4 ЗАМІНИТИ `setupCamera()`

```kotlin
    private fun setupCamera() {
        camera.setToOrtho(false, width, height)
        camera.position.set(width / 2f, height / 2f, 0f)
        camera.update()
    }
```
→
```kotlin
    private fun setupCamera() {
        // Камера на (−bleed..width+bleed) × (−bleed..height+bleed): діти малюються
        // у своїх координатах, а навколо лишається поле під ефект.
        camera.setToOrtho(false, width + bleed * 2f, height + bleed * 2f)
        camera.position.set(width / 2f, height / 2f, 0f)
        camera.update()
    }
```

### 4.5 ДОДАТИ у `contentHash()` — щоб батьківська група бачила зміну bleed дитини

**Було:**

```kotlin
            if (c is VfxGroup) for (e in c.effects) h = h * 31 + e.stateKey()
```
→
```kotlin
            if (c is VfxGroup) {
                for (e in c.effects) h = h * 31 + e.stateKey()
                h = h * 31 + c.bleed.toRawBits().toLong()   // інакше кеш батька не оновиться
            }
```

## 5. `manager/util/SpriteUtil.kt` — приклад замість тестового рядка

**ЗАМІНИТИ:**

```kotlin
        val aaaTexture = VfxTexture(186f, 101f, aaa, effect, post = listOf(BlurEffect(radius = 20f)))
```
→
```kotlin
        /**
         * Світіння: та сама фігура, розмита. Фігура 186×101, блюр виходить на
         * bleed назовні. density 1 — світінню роздільність не потрібна, а крок
         * блюру понад 2 текселі дає смуги (docs/msdf-usage.md §4): ширше
         * світіння — нижча density, не більший radius. bleed ≈ 9·2/1 = 18 → 24.
         */
        val aaaGlow = VfxTexture(186f, 101f, base = aaa, shape = effect,
            post = listOf(BlurEffect(radius = 2f)), density = 1f, bleed = 24f)
```

`radius = 20f` при density 3 — це крок 20 текселів між семплами: смуги замість
розмиття. Це вже наступали, записано в §7 `msdf-usage.md`.

І закоментований приклад нижче (рядки ~41–44) — дописати `bleed`, інакше він
навчає обрізаному світінню:

```kotlin
        //    val star_glow = VfxTexture(128f, 128f, base = star, shape = effect, post = listOf(BlurEffect(radius = 2f)), density = 1f)
```
→
```kotlin
        //    val star_glow = VfxTexture(48f, 48f, base = star, shape = effect, post = listOf(BlurEffect(radius = 2f)), density = 1f, bleed = 24f)
```

## 6. `screens/TestScreen.kt` — ЗАМІНИТИ тіло `addMsdfSandbox()`

Старий блок посилається на `aaaTexture`, якого після кроку 5 немає.

**Було** (усе тіло функції):

```kotlin
        val deb = Image(drawerUtil.getRegion(Color.RED))
        deb.setSize(186f, 101f)
        add(deb) { center() }
        deb.debug()

        val aaa = AMsdfImage(this@TestScreen, gdxGame.assetsMsdf.aaa)
        aaa.setSize(186f, 101f)
        add(aaa) { centerX(); bottomToTop(deb, 20f) }
        aaa.debug()

        val aaa2 = Image(gdxGame.assetsMsdf.aaaTexture.region)
        aaa2.setSize(186f, 101f)
        add(aaa2) { centerX(); topToBottom(deb, 20f) }
        aaa2.debug()
```

**Стало:**

```kotlin
        val glow = gdxGame.assetsMsdf.aaaGlow.image()      // шар: межі = фігура
        glow.setSize(186f, 101f)
        add(glow) { center() }
        glow.debug()                                       // рамка 186×101, світіння виходить за неї

        val sharp = AMsdfImage(this@TestScreen, gdxGame.assetsMsdf.aaa)
        sharp.setSize(186f, 101f)
        add(sharp) { center() }                            // різка поверх — накладаються край у край

        // frame, що обіймає ефект — для вирівнювання відносно світіння
        val frame = Image(gdxGame.assetsMsdf.aaaGlow.region)
        frame.setSize(gdxGame.assetsMsdf.aaaGlow.outerWidth, gdxGame.assetsMsdf.aaaGlow.outerHeight)
        add(frame) { centerX(); bottomToTop(glow, 40f) }
        frame.debug()                                      // рамка 234×149, світіння всередині
```

---

## Перевірка

```bash
./gradlew assembleDebug
```

На пристрої:
- `glow.debug()` — рамка 186×101, м'яке світіння виходить за неї на ~24 з усіх боків,
  **не обрізане** прямою лінією;
- `sharp` лягає на `glow` край у край;
- `frame` — світіння повністю всередині рамки;
- меню з `ABlurBack` (розмитий фон) — як було: крок 4 із `bleed = 0` нічого не міняє.
