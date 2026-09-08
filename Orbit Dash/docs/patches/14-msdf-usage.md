# Патч 14 — MSDF у грі: `resized()`, `AMsdfGroup`, `AMsdfImage`, `loadAtlasNow()`, `ninePatch()`

> **Застаріло частково (7 вересня 2026).** Блок `ninePatch(region, svgRadius)`
> і `rrect_patch` замінено — див. [`15-ninepatch-splits.md`](15-ninepatch-splits.md).
> Решта патча актуальна і вставлена.


Увесь код **зібраний і прогнаний на пристрої** в лабораторній копії проєкту.
Пояснення — `docs/msdf-usage.md`.

## Порядок вставки

1. `assets/shader/base/msdf/shape/msdf_shape.glsl` — край через `fwidth(d)` (для 9-patch і обертання)
2. `utils/vfx/VfxTexture.kt` — `resized()`
3. `manager/SpriteManager.kt` — `loadAtlasNow()`
4. `GDXGame.kt` — `create()`: синхронний MSDF замість крашу
5. `actors/vfx/AMsdfGroup.kt` — **новий**
6. `actors/vfx/AMsdfImage.kt` — **новий**
7. `manager/util/SpriteUtil.kt` — `Msdf`: `DIM`, `ninePatch()`, регіони, запечені
8. `screens/TestScreen.kt` — **замінити цілком** (стенд v3)

Компілюється після 7. Для 8 і для `circle`/`rrect` у пункті 7 потрібні
`svg/circle.svg` і `svg/rrect.svg` в атласі — вони в `assets/msdf/svg/` після
цього патчу (див. кінець файлу) і перегенеровуються кліком по `gen-msdf.command`.

---

## 1. `msdf_shape.glsl` — ЗАМІНИТИ ЦІЛКОМ

**Файл:** `app/src/main/assets/shader/base/msdf/shape/msdf_shape.glsl`

Що змінилось: антиаліас рахується з похідних самої відстані (`d / fwidth(d)`),
а не з похідних UV. Це робить край правильним при **нерівномірному** розтягу
(9-patch кнопки 300×56) і при обертанні. Без похідних — фолбек як був.

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

// Ширина антиаліасу — з похідних САМОЇ відстані, а не UV: тоді край правильний
// і при нерівномірному розтягу (9-patch, кнопка 300×56), і при обертанні.
// Без похідних (рідкість на GLES 2.0) — фолбек через u_unitRange/u_screenPxRange.
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

    float d = median(t.rgb) - 0.5;
#if defined(GL_OES_standard_derivatives) || !defined(GL_ES)
    // d/|∇d| = відстань до краю в екранних пікселях незалежно від масштабу по осях
    float alpha = clamp(d / max(fwidth(d), 1.0e-4) + 0.5, 0.0, 1.0);
#else
    float alpha = clamp(d * screenPxRange() + 0.5, 0.0, 1.0);
#endif
    if (alpha < 0.004) discard;

    gl_FragColor = vec4(v_color.rgb, v_color.a * alpha);
}```

---

## 2. `VfxTexture.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxTexture.kt`

### ДОДАТИ — одразу після `fun invalidate() { lastKey = Long.MIN_VALUE }`

```kotlin

    /**
     * Та сама фігура й ті самі ефекти — ІНША роздільність. Для випадку «в
     * SpriteUtil запечено 48, а тут треба 160»: копія під потрібний розмір,
     * оригінал не чіпається.
     *
     * Ефекти ДІЛЯТЬСЯ (ті самі інстанси): змінив blur в одному — оновились
     * обидва наступним кадром. Тому й resized, а не copy. Потрібна незалежна —
     * збери через конструктор з новими інстансами ефектів.
     *
     * Це нова текстура зі своїм FBO і своєю пам'яттю — не викликай у циклі
     * чи в draw(); тримай як поле, як і оригінал.
     */
    fun resized(width: Float = this.width, height: Float = this.height, density: Float = this.density) =
        VfxTexture(width, height, base, shape, post, density)
```

---

## 3. `SpriteManager.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/manager/SpriteManager.kt`

### ДОДАТИ — після `fun initAll() { … }`

```kotlin

    /**
     * Синхронно завантажити й ініціалізувати атласи — для того, що потрібно
     * ДО лоадера: BRAND на бренд-екрані, MSDF з першого кадру будь-якого екрана.
     * Блокує потік на час завантаження, тож лише для дрібних атласів.
     */
    fun loadAtlasNow(vararg atlases: EnumAtlas) {
        loadableAtlasList = atlases.map { it.data }.toMutableList()
        loadAtlas()
        assetManager.finishLoading()
        initAtlas()
    }
```

(`BrandScreen.loadBandAssets()` і `LoaderScreen.loadSplashAssets()` роблять те
саме руками — можеш перевести на цей метод, коли буде зручно.)

---

## 4. `GDXGame.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/GDXGame.kt`

### ДОДАТИ — до імпортів (якщо ще немає)

```kotlin
import com.lewydo.orbitdash.game.manager.SpriteManager
```

### ЗАМІНИТИ — у `create()` між `collectModelPlayer()` і `val firstScreenName`

Було (крашить: атлас ще не завантажений):

```kotlin
        // Запечені фігури беруть регіони з msdf-атласу, який щойно ініціалізовано.
        // Розсмоктуємо lazy тут: конструктор GL не чіпає, а перший рендер піде у
        // VfxTextures.update() на наступному кадрі — до того, як їх хтось намалює.
        gdxGame.assetsMsdf
```

Стане:

```kotlin
        // MSDF-атлас потрібен усім екранам, включно з Brand, важить кілобайти й ні
        // від чого не залежить — вантажимо синхронно тут. Далі assetsMsdf доступний
        // із першого кадру. (loadAssets() лоадера підхопить його ще раз — дедуплікується.)
        spriteManager.loadAtlasNow(SpriteManager.EnumAtlas.MSDF)
        assetsMsdf
```

Дотику в `LoaderScreen.initAssets()` **не треба**.

---

## 5. `AMsdfGroup.kt` — НОВИЙ ФАЙЛ

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/AMsdfGroup.kt`

```kotlin
package com.lewydo.orbitdash.game.actors.vfx

import com.badlogic.gdx.graphics.g2d.Batch
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect

// ─────────────────────────────────────────────────────────────────────────────
// AMsdfGroup — «багато MSDF-фігур одним draw call».
//
//   Проблема: VfxImage ставить шейдер і юніформи НА КОЖЕН актор → flush на
//   кожного. Сто іконок = сто викликів.
//
//   Рішення: шейдер ставиться РАЗ на групу, а діти — звичайні Image з
//   msdf-регіонами. Вони батчаться між собою, як будь-які Image з одного
//   атласу: різні розміри, різні тінти (Image.color → v_color) — один виклик.
//   Різкість на будь-якому розмірі дає fwidth() у шейдері, юніформи на актор
//   не потрібні.
//
//   Правила:
//   • діти — ТІЛЬКИ Image з регіонами msdf-атласу цього ефекту. Звичайний PNG
//     усередині буде прогнаний через msdf-шейдер і перетвориться на кашу;
//   • це AConstraintLayout — розкладай дітей через add(actor) { … } як звичайно;
//   • один стиль на групу. Інший стиль (інший ефект/атлас) — інша група.
//
//       val icons = AMsdfGroup(screen, gdxGame.assetsMsdf.effect)
//       icons.add(Image(msdf.star).apply { setSize(24f, 24f) }) { … }
//       icons.add(Image(msdf.heart).apply { setSize(40f, 40f); color = GOLD }) { … }
// ─────────────────────────────────────────────────────────────────────────────
open class AMsdfGroup(
    screen: AdvancedScreen,
    private val effect: MsdfShapeEffect,
) : AConstraintLayout(screen) {

    /** MsdfShapeEffect читає лише атлас і pxRange — розмір контексту йому байдужий. */
    private val ctx = VfxContext(1f, 1f, 1, 1)

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) return

        val sp = effect.batchShader
        batch.shader = sp                 // flush попереднього батча, далі — наш шейдер
        effect.setUniforms(sp, ctx)       // юніформи на групу, не на актор

        super.draw(batch, parentAlpha)    // діти батчаться: одна текстура, один шейдер

        batch.shader = null               // flush наших, повернення дефолтного шейдера
    }
}
```

---

## 6. `AMsdfImage.kt` — НОВИЙ ФАЙЛ

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/vfx/AMsdfImage.kt`

```kotlin
package com.lewydo.orbitdash.game.actors.vfx

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect

// ─────────────────────────────────────────────────────────────────────────────
// AMsdfImage — «просто SVG-картинка»: MSDF-регіон, різкий на будь-якому розмірі.
//
//   AMsdfImage(screen, gdxGame.assetsMsdf.star).apply {
//       setSize(96f, 96f)
//       color = GameColor.gold
//   }
//
//   Це VfxImage з ефектом атласу, підставленим автоматично. Ціна та сама —
//   окремий draw call на актор. Багато однакових → AMsdfGroup + Image.
// ─────────────────────────────────────────────────────────────────────────────
open class AMsdfImage(
    screen: AdvancedScreen,
    region: TextureRegion,
    effect: MsdfShapeEffect = gdxGame.assetsMsdf.effect,
) : VfxImage(screen, region, effect)
```

---

## 7. `SpriteUtil.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`

### ЗАМІНИТИ — імпорт

```kotlin
import com.lewydo.orbitdash.game.utils.vfx.effects.base.CircleEffect
```

→

```kotlin
import com.lewydo.orbitdash.game.utils.vfx.effects.base.BlurEffect
```

### ЗАМІНИТИ — клас `Msdf` цілком (від `class Msdf {` до його `}`)

Потрібен імпорт `kotlin.math.roundToInt`.

```kotlin
    class Msdf {
        private fun getRegion(name: String): TextureRegion = SpriteManager.EnumAtlas.MSDF.region(name)

        /** Ті самі числа, що в assets/msdf/gen-msdf.command. Потрібні для ninePatch(). */
        val PX_RANGE = 8f
        val DIM      = 64f

        /** Сторінка атласу. Одна: кілька іконок у 1024² вміщаються з запасом. */
        val texture: Texture = SpriteManager.EnumAtlas.MSDF.data.atlas.textures.first().apply {
            setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }

        /** Спільний ефект на всі фігури цього атласу — юніформи в них однакові. */
        val effect = MsdfShapeEffect(texture, PX_RANGE)

        // ── ВЕКТОР: регіони, назва = ім'я SVG без розширення ────────────────
        //    Малювати через effect: VfxImage(screen, msdf.star, msdf.effect)
        //    або багато одразу — AMsdfGroup(screen, msdf.effect) + Image(msdf.star)
        val star   = getRegion("star")
        val circle = getRegion("circle")
        val rrect  = getRegion("rrect")     // 64×64, радіус 16

        /**
         * 9-patch із MSDF-фігури, що ТЯГНЕТЬСЯ (кнопка, панель): кути беруться
         * з поля у своєму розмірі, тягнеться лише середина.
         *
         * svgRadius — радіус, який ти намалював у Figma, у координатах viewBox.
         * Решту рахує сама: autoframe ужимає фігуру до (DIM − PX_RANGE)/DIM і
         * лишає PX_RANGE/2 текселів поля з кожного боку. Для rrect 64/r16 → 18.
         *
         *     val patch = msdf.ninePatch(msdf.rrect, svgRadius = 16f)
         *     Image(patch).apply { setSize(200f, 56f) }      // кути не пливуть
         *
         * Краще взяти split БІЛЬШИЙ за потрібний, ніж менший: зайве — шматок
         * прямого ребра, який просто не тягнеться; замалий — потягне саму дугу.
         */
        fun ninePatch(region: TextureRegion, svgRadius: Float): NinePatch {
            val split = (PX_RANGE * 0.5f + svgRadius * (DIM - PX_RANGE) / DIM).roundToInt()
            return NinePatch(region, split, split, split, split)
        }

        /** Готовий 9-patch кнопки — split 18, порахований із радіуса 16. */
        val rrect_patch = ninePatch(rrect, svgRadius = 16f)

        // ── РАСТР: запечені VfxTexture — ТІЛЬКИ заради ефектів ──────────────
        //    Це вже картинка фіксованої роздільності: малювати РІВНО в розмірі
        //    запікання, інший розмір → .resized(w, h). Деталі — docs/msdf-usage.md.
        //    Регіон стабільний: змінив параметр ефекту — усі споживачі оновились.

        /** Різка зірка під 48 юнітів (144 px). Малювати рівно 48. Тут лише як приклад. */
        val star_48 = VfxTexture(48f, 48f, base = star, shape = effect)

        /**
         * Світіння: та сама зірка, розмита. Малювати ПІД різкою копією того ж
         * розміру. Радіус можна крутити на льоту — усі споживачі оновляться.
         *
         * density = 1: світінню роздільність не потрібна, а блюр стає гладким.
         * BlurEffect.radius — це КРОК між 9 семплами в текселях буфера, не σ.
         * Крок понад ~2 текселі дає смуги (перевірено: крок 6 при density 3 —
         * шорсткість 0.50, крок 2 при density 1 — 0.00). Ширше світіння —
         * нижча density, не більший radius.
         */
        val star_glow = VfxTexture(128f, 128f, base = star, shape = effect,
                                   post = listOf(BlurEffect(radius = 2f)), density = 1f)
    }
```

Шапку-коментар над класом можна лишити; рядки про «ТІЛЬКИ після initAssets()»
замінити на «Атлас вантажиться синхронно в GDXGame.create()».

---

## 8. `TestScreen.kt` — ЗАМІНИТИ ЦІЛКОМ (стенд v4)

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/screens/TestScreen.kt`

```kotlin
package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.lewydo.orbitdash.game.actors.vfx.AMsdfGroup
import com.lewydo.orbitdash.game.actors.vfx.AMsdfImage
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.base.BlurEffect

class TestScreen : AdvancedScreen() {

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aStarField by lazy { AStarField(this) }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        animShowScreen()
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        addMsdfSandbox()
        addDebugHud(ADebugHud(this@TestScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val v = stageUI.screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
        aStarField.animRippleAt(v.x, v.y)
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
    // MSDF sandbox v2 — усі способи використання на одному екрані (ТИМЧАСОВЕ)
    // ------------------------------------------------------------------------
    //
    //   y=470  A. ВЕКТОР БАТЧЕМ — AMsdfGroup: 5 зірок (16..96), 5 тінтів, ОДИН draw call
    //   y=300  B. ЗАПЕЧЕНЕ — star_48: [1:1 різко] [розтягнуто до 128 — мило] [copy(128) — різко]
    //   y=150  C. ЕФЕКТИ — star_glow (blur) під різким вектором; праворуч той самий
    //                     регіон синім. Блюр «дихає» 6↔20 — обидва оновлюються
    //   y=50   D. ДОВІДКОВО — сире поле 64 і звичайний PNG 96 (для контрасту)
    //
    private fun AConstraintLayout.addMsdfSandbox() {
        val msdf   = gdxGame.assetsMsdf

        // ── A. вектор батчем ─────────────────────────────────────────────────
        val group = AMsdfGroup(this@TestScreen, msdf.effect).apply { setSize(360f, 130f) }
        add(group) { startToStart(margin = 0f); bottomToBottom(margin = 470f) }
        group.add(Image(msdf.circle).apply { setSize(40f, 40f); color = Color.valueOf("00e5ff") }) { startToStart(margin = 300f); bottomToBottom(margin = 10f) }  // коло з SVG
        var x = 22f
        for ((size, hex) in listOf(16f to "ffffff", 24f to "4dd9ff", 40f to "ffd54a", 64f to "c07bff", 96f to "ff9f2e")) {
            val img = Image(msdf.star).apply { setSize(size, size); color = Color.valueOf(hex) }
            val xx = x
            group.add(img) { startToStart(margin = xx); bottomToBottom(margin = 10f) }
            x += size + 14f
        }

        // ── E. MSDF 9-patch: ОДНА текстура rrect на будь-яку ширину ─────────
        //    Три ширини з одного регіона + вертикальний розтяг. Кути скрізь
        //    однакові й круглі; тягнеться лише середина. Усе в одній групі.
        val ui = AMsdfGroup(this@TestScreen, msdf.effect).apply { setSize(360f, 190f) }
        add(ui) { startToStart(margin = 0f); bottomToBottom(margin = 600f) }
        val patch = msdf.rrect_patch
        ui.add(Image(patch).apply { setSize(90f,  44f); color = Color.valueOf("00e5ff") }) { startToStart(margin = 22f);  bottomToBottom(margin = 130f) }
        ui.add(Image(patch).apply { setSize(180f, 44f); color = Color.valueOf("00e5ff") }) { startToStart(margin = 22f);  bottomToBottom(margin = 76f) }
        ui.add(Image(patch).apply { setSize(316f, 44f); color = Color.valueOf("00e5ff") }) { startToStart(margin = 22f);  bottomToBottom(margin = 22f) }
        ui.add(Image(patch).apply { setSize(56f, 152f); color = Color.valueOf("ff9f2e") }) { startToStart(margin = 282f); bottomToBottom(margin = 22f) }

        // ── B. запечене: роздільність фіксована ─────────────────────────────
        at(22f,  330f, Image(msdf.star_48.region).apply { setSize(48f, 48f) })                   // 1:1 → різко
        at(80f,  300f, Image(msdf.star_48.region).apply { setSize(128f, 128f) })                 // ×2.7 → МИЛО (пастка)
        at(220f, 300f, Image(msdf.star_48.resized(128f, 128f).region).apply { setSize(128f, 128f) }) // copy під розмір → різко

        // ── C. ефекти: спільна розмита текстура під різким вектором ───────────
        val glow = msdf.star_glow
        at(22f,  150f, Image(glow.region).apply { setSize(128f, 128f); color = Color.valueOf("ff9f2e") })
        at(22f,  150f, AMsdfImage(this@TestScreen, msdf.star).apply { setSize(128f, 128f); color = Color.valueOf("ffd54a") })
        at(200f, 150f, Image(glow.region).apply { setSize(128f, 128f); color = Color.valueOf("4dd9ff") })   // той самий регіон

        // блюр «дихає» 1 ↔ 2 текселі (density 1) — обидва споживачі оновлюються
        addAction(Actions.forever(Actions.sequence(
            Actions.run { glow.effect<BlurEffect>()?.radius = 1f }, Actions.delay(1.2f),
            Actions.run { glow.effect<BlurEffect>()?.radius = 2f }, Actions.delay(1.2f),
        )))

        // ── D. довідково ────────────────────────────────────────────────────
        at(22f,  50f, Image(msdf.star).apply { setSize(64f, 64f) })
        at(110f, 50f, Image(gdxGame.assetsAll.STAR).apply { setSize(96f, 96f) })
    }

    /** Поставити актора в лівий-нижній кут root. Розмір актор задає сам. */
    private fun AConstraintLayout.at(x: Float, y: Float, actor: Actor) {
        actor.debug()
        add(actor) { startToStart(margin = x); bottomToBottom(margin = y) }
    }

}
```

---

## 9. Два нові SVG для атласу

Поклади в `assets/msdf/svg/` і клікни `gen-msdf.command`.

**`circle.svg`**

```xml
<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M32 0C49.6731 0 64 14.3269 64 32C64 49.6731 49.6731 64 32 64C14.3269 64 0 49.6731 0 32C0 14.3269 14.3269 0 32 0Z" fill="white"/>
</svg>
```

**`rrect.svg`** (64×64, радіус 16 → у 9-patch кути по 20 px: 16 + 4 поля)

```xml
<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="M16 0H48C56.8366 0 64 7.16344 64 16V48C64 56.8366 56.8366 64 48 64H16C7.16344 64 0 56.8366 0 48V16C0 7.16344 7.16344 0 16 0Z" fill="white"/>
</svg>
```

## 10. Що побачиш

```
y=600  E. 9-patch: 90 / 180 / 316 завширшки + вертикальна 56×152 з ОДНОГО
          регіона rrect — кути попіксельно однакові, тягнеться лише середина
y=470  A. п'ять зірок 16..96 + коло з SVG — одна група, один виклик
y=300  B. star_48: [1:1] [на 128 — мило] [resized(128) — різко]
y=150  C. світіння під AMsdfImage; праворуч той самий регіон; блюр 1↔2
y=50   D. сире поле і PNG 96
HUD:   draw 13, 61 FPS
```

## 11. Про 9-patch — коротко

`split = PX_RANGE/2 + svgRadius × (DIM − PX_RANGE) / DIM`

Для `rrect.svg` (viewBox 64, радіус 16) при PX_RANGE 8 і DIM 64 → **18**.
Рахує `msdf.ninePatch(region, svgRadius)`; готовий `msdf.rrect_patch`.
Інструмент, як Nine-patch editor для PNG, тут не потрібен: радіус ти
намалював у Figma, тож число виводиться, а не вгадується. Детально —
`docs/msdf-usage.md` §2а.
