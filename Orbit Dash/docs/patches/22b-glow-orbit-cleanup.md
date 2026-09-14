# 22б — `glowOrbit`: дочистити після заміни `orbit_glow.png` на `VfxTexture`

## Що і навіщо

Заміна сама по собі правильна, і числа збігаються один в один із Figma:
`blur = 120` → `bleed = 120` → `outer = 420 + 240 = 660` — рівно той розмір, який
`AOrbitEmblem.addOrbitGlow()` і ставить (`setSizeScaled(660f, 660f)`), тобто фрейм
«hug contents» 1:1. σ = 51.1 юніта, авто-`density` = 0.235, буфер **155×155 ≈ 94 КБ**.
Старий `orbit_glow.png` — **1320×1320 = 6.8 МБ у GPU** і 479 КБ в APK.

Але **зараз проєкт не збереться і не запуститься**, і місце для текстури обране не те:

1. **`LoaderScreen:105` ще читає `SpriteManager.EnumTexture.ORBIT_GLOW`**, який ти вже
   закоментував → не компілюється. А якби компілювалось — `finishLoading()` кинув би
   виняток, бо PNG уже видалений із `assets/`.
2. **`class Loader` більше не має жодного ассета зі свого атласу.** `region()` у ньому не
   викликається, а з `loader.atlas` (`circle`, `gem`, `item_glow`) нічого не читає жоден
   клас — усе це замінили MSDF і `glowTex`. Атлас (24 КБ) вантажиться дарма.
3. **Крос-залежність холдерів.** `private val assetsMsdf = gdxGame.assetsMsdf` у `Loader`
   працює лише тому, що `GDXGame.create()` форсить `assetsMsdf` після
   `loadAtlasNow(MSDF)`. Незаписана вимога до порядку ініціалізації на рівному місці:
   текстура зроблена з MSDF-регіона й блюру — тобто рідня `glowTex`, а не атласу лоадера.

Отже: перенести `glowOrbitTex` у `Msdf` поруч із `glowTex`, а `Loader`, `assetsLoader`,
`EnumAtlas.LOADER` і `EnumTexture.ORBIT_GLOW` — прибрати. Два світіння лежать в одному
місці й читаються як одна родина: 40/22 для об'єктів, 420/120 для орбіт.

Перевірено збіркою в лабораторній копії (`assembleDebug`).

---

## 1. `SpriteUtil.kt`

`app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`

**1а. Імпорти — ВИДАЛИТИ** три мертві рядки (`gdxGame` був лише для `Loader`,
`VfxTextures` і `roundToInt` не використовуються давно):

```kotlin
import com.lewydo.orbitdash.game.utils.gdxGame
```
```kotlin
import com.lewydo.orbitdash.game.utils.vfx.VfxTextures
```
```kotlin
import kotlin.math.roundToInt
```

**1б. `class Msdf`, після `val glow = glowTex.region` — ДОДАТИ:**

```kotlin

        // Світіння під орбіти емблеми: коло 420, Layer Blur 120 → outer = 660,
        // рівно той розмір, який ставить AOrbitEmblem. Замінило orbit_glow.png
        // (1320² = 6.8 МБ у GPU, 479 КБ в APK) на буфер 155² ≈ 94 КБ.
        val glowOrbitTex = VfxTexture(420f, 420f, circle_msdf, effect, listOf(BlurEffect(blur = 120f)))
        val glowOrbit    = glowOrbitTex.region
```

**1в. `class Loader` — ВИДАЛИТИ цілком:**

```kotlin
    class Loader {
        private fun region(name: String): TextureRegion = SpriteManager.EnumAtlas.LOADER.region(name)
        private val assetsMsdf = gdxGame.assetsMsdf

        val glowOrbitTex = VfxTexture(420f, 420f, assetsMsdf.circle_msdf, assetsMsdf.effect, listOf(BlurEffect(blur = 120f)))
        val glowOrbit    = glowOrbitTex.region
    }

```

---

## 2. `GDXGame.kt`

`app/src/main/java/com/lewydo/orbitdash/game/GDXGame.kt`, поле поруч із рештою `assets*` —
ВИДАЛИТИ:

```kotlin
    val assetsLoader by lazy { SpriteUtil.Loader() }
```

---

## 3. `AOrbitEmblem.kt`

`app/src/main/java/com/lewydo/orbitdash/game/actors/orbit/AOrbitEmblem.kt`, розділ `Actors`
— ЗАМІНИТИ

```kotlin
    private val aOrbitGlowImg = Image(gdxGame.assetsLoader.glowOrbit).apply { color.a = 0.07f }
```
**на**
```kotlin
    private val aOrbitGlowImg = Image(gdxGame.assetsMsdf.glowOrbit).apply { color.a = 0.07f }
```

---

## 4. `SpriteManager.kt`

`app/src/main/java/com/lewydo/orbitdash/game/manager/SpriteManager.kt`

**4а. `EnumAtlas` — ВИДАЛИТИ** (жодного регіона з нього ніхто не читає):

```kotlin
        LOADER(AtlasData("atlas/loader.atlas")),

```

**4б. `EnumTexture` — ЗАМІНИТИ** (енум лишається порожнім; `;` обов'язковий, бо всі
входження закомментовані, а `entries` далі використовується в `LoaderScreen.loadAssets()`):

```kotlin
        // Loader
        //ORBIT_GLOW(TextureData("textures/loader/orbit_glow.png")),
```
**на**
```kotlin
        // Порожньо: світіння орбіт тепер VfxTexture (патч 22б), не PNG.
        ;
```

---

## 5. `LoaderScreen.kt`

`app/src/main/java/com/lewydo/orbitdash/game/screens/LoaderScreen.kt`, `loadSplashAssets()`
— ЗАМІНИТИ

```kotlin
        with(gdxGame.spriteManager) {
            loadableAtlasList = mutableListOf(SpriteManager.EnumAtlas.LOADER.data)
            loadAtlas()
            loadableTexturesList = mutableListOf(
                SpriteManager.EnumTexture.ORBIT_GLOW.data,
            )
            loadTexture()
            //loadableGroupList = mutableListOf(SpriteManager.EnumTextureGroup.LIGHT_C.data)
            //loadGroups()
        }
```
**на**
```kotlin
        // Порожньо: сплешу більше нічого не треба — MSDF-атлас уже завантажений
        // синхронно в GDXGame.create(), а світіння орбіт — VfxTexture, не PNG.
```

`finishLoading()` + `initAll()` нижче лишаються: вони безпечні на порожніх списках і
потрібні, коли сюда щось повернеться.

---

## 6. Файли

ВИДАЛИТИ:

- `app/src/main/assets/atlas/loader.atlas`
- `app/src/main/assets/atlas/loader.png` (24 КБ)
- `app/src/main/assets/textures/loader/orbit_glow.png` — уже видалений

Разом із PNG: **−503 КБ APK, −6.8 МБ GPU-пам'яті**, на одне синхронне завантаження
атласу й текстури на сплеші менше.

---

## 7. Документація

`CLAUDE.md`, §«Процедурні фігури» / `docs/decisions.md` §7 — рядок про `item_glow`
(«PNG в атласі лоадера, ним користуються м'яч, гем, шип, бустер, комета») більше не
відповідає коду: атласу лоадера немає, усі користуються `assetsMsdf.glow`. Прибрати
пункт із відкритих питань.

---

## Що перевірити на пристрої

Апскейл цього світіння — **×12.8** (155 текселів → 1980 px на 1080p), найбільший у
проєкті. Це в межах, під які рахувалась `SIGMA_TEXELS = 12` (σ = 12 текселів саме щоб
білінійний апскейл не показував зламів нахилу), але на альфі 0.07 бендинг було б видно
першим. Якщо на знімку меню/лоадера з'являться кільця чи смуги — лікується явною
`density = 0.4f` (буфер 264² ≈ 279 КБ), не зміною `blur`.
