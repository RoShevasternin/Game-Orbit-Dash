# 22г — Шрифти на вимогу: заміри на пристрої, баг у `dispose()` і як форсити

## Заміряно на пристрої (Redmi 24117RN76E, debug, лабораторна копія)

Таймери навколо `create()`, `LoaderScreen.show()` і кожного `lazy` в `MsdfManager`:

```
create:start             @   0.0 ms
loader:show start        @ 323.6 ms     ← менеджери + saveManager.load() + navigate
loader:splash готовий    @ 392.5 ms     ← MSDF: атлас + Msdf() + shape-шейдер = 68.8 ms
    msdf:shadowShader      5.2 ms
    msdf:Inter-ExtraBold 299.8 ms   ← !
    msdf:Inter-Medium    126.0 ms   ← !
loader:актори створені   @ 856.1 ms     ← super.show() = 463.6 ms, з них 426 — шрифти
render:перший кадр       @ 859.5 ms
    msdf:fillShader        2.1 ms   ← уже після першого кадру
```

Три висновки, які міняють план:

1. **`by lazy` вже дав реальний виграш:** `Inter-Bold` (450 КБ / 2.95 МБ GPU) у старті не
   створюється взагалі — його бере лише `APanelGameHud`, тобто гра. Так само
   `strokeShader` і `innerShader`. Мінус ~200–300 мс зі старту.
2. **Але чорний екран не зник, а переїхав:** `AMainLoader` створює `MsdfStyle` у **полях**,
   тому `Inter-ExtraBold` + `Inter-Medium` = **426 мс** вантажаться в `super.show()` —
   до першого намальованого кадру. Тобто `lazy` сам по собі паузу під прогрес-бар не кладе.
3. **MSDF-атлас коштує 68.8 мс** — і це не 3 КБ картинки, це компіляція
   `MsdfShapeEffect`-шейдера всередині `SpriteUtil.Msdf()`. Перенесення в `LoaderScreen`
   (патч 22в) правильне, але саме число варто знати.

---

## 1. БЛОКЕР: `MsdfManager.dispose()` форсить усе, що ніколи не створювалось

Зараз:

```kotlin
    override fun dispose() {
        disposeAll(
            fillShader, strokeShader, shadowShader, innerShader,
            fontInter_Medium, fontInter_Bold, fontInter_ExtraBold,
        )
    }
```

Кожне звертання до `by lazy` **створює** об'єкт, якщо його ще не було. За заміром вище в
сесії створюються лише `fillShader`, `shadowShader`, `Inter-Medium`, `Inter-ExtraBold` —
отже `dispose()` на виході прочитає з диска `Inter-Bold` (450 КБ), скомпілює два шейдери й
одразу їх знищить. Гірше: `dispose()` приходить, коли GL-контекст уже може бути мертвий
(Android знищує Activity) — компіляція шейдера й `Texture()` там дадуть або краш, або
GL-помилки. Це той самий клас багів, що патч 20.

**ЗАМІНИТИ** увесь блок полів і `dispose()`:

```kotlin
class MsdfManager : Disposable {

    val fillShader   by lazy { MsdfEffectShader("shader/base/msdf/font/msdf_fill.glsl") }
    val strokeShader by lazy { MsdfEffectShader("shader/base/msdf/font/msdf_stroke.glsl") }
    val shadowShader by lazy { MsdfEffectShader("shader/base/msdf/font/msdf_shadow.glsl") }
    val innerShader  by lazy { MsdfEffectShader("shader/base/msdf/font/msdf_inner_shadow.glsl") }

    val fontInter_Medium by lazy { MsdfFont(
        "font/msdf/Inter-Medium.json",
        "font/msdf/Inter-Medium.png",
    ) }
    val fontInter_Bold by lazy { MsdfFont(
        "font/msdf/Inter-Bold.json",
        "font/msdf/Inter-Bold.png",
    ) }
    val fontInter_ExtraBold by lazy { MsdfFont(
        "font/msdf/Inter-ExtraBold.json",
        "font/msdf/Inter-ExtraBold.png",
    ) }
```
**на**
```kotlin
class MsdfManager : Disposable {

    // Створене — і ТІЛЬКИ воно — лягає сюди. Інакше dispose() звертається до
    // lazy-полів і створює те, чим жодного разу не користувались: читання шрифта
    // з диска й компіляція шейдера на виході, коли GL-контексту вже може не бути.
    private val created = mutableListOf<Disposable>()

    private fun <T : Disposable> onDemand(block: () -> T) = lazy { block().also { created.add(it) } }

    val fillShader   by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_fill.glsl") }
    val strokeShader by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_stroke.glsl") }
    val shadowShader by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_shadow.glsl") }
    val innerShader  by onDemand { MsdfEffectShader("shader/base/msdf/font/msdf_inner_shadow.glsl") }

    val fontInter_Medium by onDemand { MsdfFont(
        "font/msdf/Inter-Medium.json",
        "font/msdf/Inter-Medium.png",
    ) }
    val fontInter_Bold by onDemand { MsdfFont(
        "font/msdf/Inter-Bold.json",
        "font/msdf/Inter-Bold.png",
    ) }
    val fontInter_ExtraBold by onDemand { MsdfFont(
        "font/msdf/Inter-ExtraBold.json",
        "font/msdf/Inter-ExtraBold.png",
    ) }
```

і **ЗАМІНИТИ** `dispose()`:

```kotlin
    override fun dispose() {
        disposeAll(
            fillShader,
            strokeShader,
            shadowShader,
            innerShader,

            fontInter_Medium,
            fontInter_Bold,
            fontInter_ExtraBold,
        )
    }
```
**на**
```kotlin
    override fun dispose() {
        created.disposeAll()   // Iterable<Disposable>.disposeAll() з utils/Util.kt
        created.clear()
    }
```

Імпорт `disposeAll` у файлі вже є. Перевірено збіркою й запуском на пристрої.

---

## 2. Як форсити — те, про що питав

### 2а. Явно, у `loadSplashAssets()` — два рядки, без ризику

`LoaderScreen.loadSplashAssets()`, після `gdxGame.assetsMsdf` — ДОДАТИ:

```kotlin
        // Шрифти, які бере сам лоадер (AMainLoader: титули + прогрес). Форсимо тут,
        // щоб пауза була в одному видимому місці, а не «де перший AMsdfLabel».
        // Заміряно: ExtraBold 300 мс, Medium 126 мс. Inter-Bold НЕ чіпаємо —
        // він потрібен лише APanelGameHud, тобто вже в грі.
        with(gdxGame.msdfManager) {
            fontInter_ExtraBold
            fontInter_Medium
        }
```

Це не прибирає 426 мс із чорного екрана — воно робить залежність **явною**: лоадер сам
каже, що йому треба, і це не залежить від того, які поля лежать в акторах. Якщо колись
титули поїдуть на інший шрифт — рядок правиться свідомо, а не «раптом стало довго».

### 2б. Щоб пауза справді легла під прогрес — треба намалювати кадр раніше

Порядок зараз: `loadSplashAssets()` → `super.show()` (створює `AMainLoader` → шрифти
426 мс) → перший кадр. Тобто все, що створюється в `show()`, за визначенням на чорному.

Щоб пауза стала видимою, потрібні два рівні:

| коли | що вантажити | чому |
|---|---|---|
| `loadSplashAssets()` (чорний екран) | MSDF-атлас + **Inter-Medium** (126 мс) | ним намальований `aProgressLbl` — без нього нема чим показувати прогрес |
| крок після першого кадру | **Inter-ExtraBold** (300 мс) | це титули «ORBIT / DASH» — вони можуть з'явитись через `animShow`, коли шрифт готовий |

Це вимагає, щоб `AMainLoader` створював титули **ліниво** (`by lazy` на `styleTitle1/2`,
`aTitle1Lbl/2Lbl` + додавання їх у `fillTitlesGroup()` уже після готовності шрифта), а
`LoaderScreen` мав крок у `loadingAssets()`, який їх дотягує. Виграш: чорний екран
860 → ~520 мс, решта 300 мс іде під зорями й відсотками.

Це окремий патч — він чіпає анімації й морф титулів у меню (`aTitlesAnchor`,
`animMorphTo`), і робити його наосліп я не буду. Скажи — напишу з розбором усіх
чотирьох станів `AMainLoader`.

---

## 3. Статус патчів 22 / 22б / 22в — що вставлено, що ні

Перевірив збіркою (`BUILD SUCCESSFUL`) і по коду:

| крок | стан |
|---|---|
| Патч 22 (реєстр дизайн-геометрії) | **вставлено повністю** — `AdvancedGroup`, `CLParams`, `AConstraintLayout`, усі 8 акторів, `ALevelPopup` |
| 22в §1 (MSDF у `LoaderScreen`) | **вставлено** — `GDXGame.create()` чистий, `loadSplashAssets()` вантажить атлас першим |
| 22в §2 (шрифти `by lazy`) | **вставлено**, але з багом у `dispose()` — див. §1 вище |
| 22б §1 (`glowOrbitTex` → `Msdf`) | **НЕ вставлено** — `class Loader` на місці, з полем `assetsMsdf` |
| 22б §1а (мертві імпорти) | **НЕ вставлено** — `gdxGame`, `VfxTextures`, `roundToInt` |
| 22б §2 (`assetsLoader` з `GDXGame`) | **НЕ вставлено** |
| 22б §4а (`EnumAtlas.LOADER`) | **НЕ вставлено** — атлас і далі вантажиться в `loadSplashAssets()` |
| 22б §6 (`loader.atlas` + `loader.png`) | **НЕ видалено** (24 КБ) |
| `EnumTexture.ORBIT_GLOW` | закоментовано ✓, `orbit_glow.png` видалено ✓ |

Окремо: ти видалив **сирці** `assets/loader/circle.png`, `gem.png`, `item_glow.png`
(TexturePacker), але сам `loader.atlas` ще містить ці три регіони й вантажиться на сплеші.
Тобто або перепакувати без них (і тоді атлас порожній), або зробити 22б §4а+§6 — прибрати
атлас цілком. Читає з нього зараз **ніхто**.
