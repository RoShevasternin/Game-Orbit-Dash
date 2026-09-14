# 22 — Реєстр дизайн-геометрії в `AdvancedGroup`: «поставити і тримати» замість `scaled()` і повторів у `sizeChanged()`

## Що і навіщо

Розмір дитини в дизайн-одиницях зараз ставлять **двічі**: `setSizeScaled(...)` в `addX()` і
те саме в `sizeChanged()` з оберегом `if (aGlow.parent == null) return` — 12 пар у 9 акторах.
`ABrandLogo` цей повтор забув і при ресайзі не оновлюється. Патч 19 (`scaled()` / `size()` у
блоці `add { }`) прибирав дубль, але прапорцем **на кожному вузлі** і **лише для дітей
`AConstraintLayout`**; застосований в одному акторі (`ABall`). Групи поза CL і діти, додані
через `addActor` (м'яч і гем в емблемі), лишались без механізму.

Справжня причина: число з макета треба **переприкладати** при зміні розміру батька, а
запам'ятовує його ніхто — тому автор мусить повторити його сам. Механізм для цього в
`AdvancedGroup` уже є — `fillActors`: список дітей, яким `sizeChanged()` переставляє розмір.
Він лише вміє «100 % батька».

Рішення — **розширити `fillActors` до реєстру дизайн-геометрії**. Одне правило:

> Група — це фрейм із Figma. Один раз кажеш їй розмір фрейму (`sizeScaler`). Далі всі числа,
> які даєш дітям, — з Figma. Записав один раз — група тримає сама. Хто батько — байдуже.

- `setSizeScaled(w, h)` / `setPositionScaled(x, y)` / `setBoundsScaled(x, y, w, h)` —
  «поставити **і тримати**»: пишуть у реєстр, група переприкладає в `sizeChanged()`.
  Розмір і позиція — **окремі слоти**: позицію реєструють лише дітям поза лейаутом; кому
  позицію веде код щокадру (`placeOnOrbit`, сутності на полі) — тримають тільки розмір.
- **Дизайн-одиниці — властивість групи, не вузла.** Хто оголосив `sizeScaler` — у того
  margin'и в констрейнтах множаться самі; хто не оголосив — `factor = 1`, числа як є.
  `scaled()` зникає. `size()` у блоці лишається як цукор над тим самим реєстром.
- Дефолт `sizeScaler` → `SizeScaler(Axis.X, 0f)`: `calculateScale` уже дає `factor = 1`
  при `designSize <= 0`. Перевірено: усі 13 груп із сирими margin'ами скейлера не
  оголошують, єдиний прямий `toActual` поза інфраструктурою — `AOrbitField`.

Компілюється після пунктів 1–3. Пункти 4–5 — міграція акторів, кожен незалежний.

---

## 1. `AdvancedGroup.kt`

`app/src/main/java/com/lewydo/orbitdash/game/utils/advanced/AdvancedGroup.kt`

**1а. Дефолтний скейлер — ЗАМІНИТИ**

```kotlin
    open val sizeScaler: SizeScaler = SizeScaler(SizeScaler.Axis.X, 1f)
```
**на**
```kotlin
    /** Дизайн-фрейм групи (Figma). 0 = фрейму нема: factor = 1, числа дітей — юніти сцени як є. */
    open val sizeScaler: SizeScaler = SizeScaler(SizeScaler.Axis.X, 0f)
```

**1б. Реєстр — ДОДАТИ** одразу після `private val fillActors = mutableListOf<Actor>()`:

```kotlin
    // ------------------------------------------------------------------------
    // Дизайн-геометрія дітей. Той самий механізм, що fillActors, тільки замість
    // «100 % батька» — число з макета. Записали один раз через setSizeScaled /
    // setPositionScaled — група сама переприкладає при кожній зміні свого розміру.
    // Розмір і позиція — окремі слоти: позицію реєструють лише дітям поза
    // лейаутом; кому позицію веде код щокадру (орбіта) — тримає тільки розмір.
    // ------------------------------------------------------------------------
    private class DesignSpec {
        var w = -1f;        var h = -1f          // < 0  — розмір не тримаємо
        var x = Float.NaN;  var y = Float.NaN    // NaN — позицію не тримаємо
    }

    private val designSpecs = LinkedHashMap<Actor, DesignSpec>()
```

**1в. `sizeChanged()` — ЗАМІНИТИ**

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        tryInitGroup()
        for (i in fillActors.indices) fillActors[i].setSize(width, height)
    }
```
**на**
```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        tryInitGroup()   // свіжий factor і (один раз) addActorsOnGroup() — реєстр наповнюється тут
        for (i in fillActors.indices) fillActors[i].setSize(width, height)
        applyDesignSpecs()
    }
```

**1г. `dispose()` — ДОДАТИ** після `fillActors.clear()`:

```kotlin
            designSpecs.clear()
```

**1д. Хелпери в кінці розділу `// Transforms` — ЗАМІНИТИ**

```kotlin
    protected fun Actor.setBoundsScaled(x: Float, y: Float, width: Float, height: Float) {
        setBounds(x.toActual, y.toActual, width.toActual, height.toActual)
    }

    protected fun Actor.setBoundsScaled(position: Vector2, size: Vector2) {
        setBoundsScaled(position.x, position.y, size.x, size.y)
    }

    protected fun Actor.setSizeScaled(width: Float, height: Float) {
        setSize(width.toActual, height.toActual)
    }
```
**на**
```kotlin
    // ------------------------------------------------------------------------
    // Дизайн-одиниці: поставити І ТРИМАТИ. internal, а не protected — CLParams.size()
    // пише в цей самий реєстр.
    // ------------------------------------------------------------------------

    /** Розмір у дизайн-одиницях. Група переприкладе його при кожному своєму sizeChanged(). */
    internal fun Actor.setSizeScaled(width: Float, height: Float) {
        designSpecs.getOrPut(this) { DesignSpec() }.also { it.w = width; it.h = height }
        setSize(width.toActual, height.toActual)
    }

    /** Позиція у дизайн-одиницях. Лише дітям поза лейаутом — констрейнти ставлять позицію самі. */
    internal fun Actor.setPositionScaled(x: Float, y: Float) {
        designSpecs.getOrPut(this) { DesignSpec() }.also { it.x = x; it.y = y }
        setPosition(x.toActual, y.toActual)
    }

    internal fun Actor.setBoundsScaled(x: Float, y: Float, width: Float, height: Float) {
        setSizeScaled(width, height)
        setPositionScaled(x, y)
    }

    internal fun Actor.setBoundsScaled(position: Vector2, size: Vector2) {
        setBoundsScaled(position.x, position.y, size.x, size.y)
    }

    /** Зняти з реєстру: далі геометрією керує код (анімація розміру, ручний layout). */
    internal fun Actor.freeScaled() { designSpecs.remove(this) }

    private fun applyDesignSpecs() {
        if (designSpecs.isEmpty()) return
        for ((actor, s) in designSpecs) {
            // parent == null — актор у пулі (піпи щита): пам'ятаємо, застосуємо, як повернеться
            if (actor.parent !== this) continue
            if (s.w >= 0f)    actor.setSize(s.w.toActual, s.h.toActual)
            if (!s.x.isNaN()) actor.setPosition(s.x.toActual, s.y.toActual)
        }
    }
```

Порядок у `sizeChanged()` гарантує: реєстр переприклав розміри → `AConstraintLayout.sizeChanged()`
(після `super`) помітив вузли dirty → в `act()` позиції рахуються з уже правильного `actor.width`.

---

## 2. `CLParams.kt`

`app/src/main/java/com/lewydo/orbitdash/game/actors/layout/constraintLayout/CLParams.kt`

**2а. Шапка — ЗАМІНИТИ** фрагмент від `//    SCALED        — розмір = …` до `//    }` включно
(рядки 12–26):

```
//    SCALED        — розмір = дизайн-число × sizeScaler.factor лейауту
//
//  ДИЗАЙН-ОДИНИЦІ (scaled):
//    scaled()               — усі числа в цьому блоці — з макета (Figma), як у
//                             setSizeScaled / toActual; лейаут множить їх на
//                             sizeScaler.factor при КОЖНОМУ resolve. Тому після
//                             зміни розміру групи нічого не треба повторювати
//                             в sizeChanged() — ні розмір, ні margin.
//    size(w, h)             — розмір у дизайн-одиницях (режим SCALED)
//
//    add(aPoint) {
//        scaled()
//        size(10f, 10f)
//        startToStart(margin = 10f); topToTop(margin = 10f)
//    }
```
**на**
```
//
//  ДИЗАЙН-ОДИНИЦІ — властивість ГРУПИ, не вузла:
//    лейаут, що оголосив sizeScaler, читає всі margin як числа з макета і множить
//    їх на factor при кожному resolve; лейаут без скейлера має factor = 1 — числа
//    йдуть як є. Нічого не повторюється в sizeChanged().
//    size(w, h)             — розмір у дизайн-одиницях; пише в реєстр групи —
//                             той самий, що setSizeScaled (патч 22)
//
//    add(aPoint) {
//        size(10f, 10f)
//        startToStart(margin = 10f); topToTop(margin = 10f)
//    }
```

**2б. `enum class Dimension` — ВИДАЛИТИ** рядок

```kotlin
    SCALED,           // розмір = designWidth/Height × sizeScaler.factor лейауту
```

**2в. Оголошення класу — ЗАМІНИТИ**

```kotlin
class CLParams(internal val layout: AConstraintLayout) {
```
**на**
```kotlin
class CLParams(internal val layout: AConstraintLayout, private val actor: Actor) {
```

**2г. Поля — ВИДАЛИТИ** два рядки після `heightPercent`:

```kotlin
    var designWidth   = 0f   // використовується тільки якщо widthMode == SCALED
    var designHeight  = 0f   // використовується тільки якщо heightMode == SCALED
```

**2д. Розділ `// ── Дизайн-одиниці ──` — ЗАМІНИТИ** цілком

```kotlin
    /**
     * true → усі числа вузла (size, margin) — у дизайн-одиницях макета; лейаут
     * множить їх на sizeScaler.factor при кожному resolve. Те саме, що
     * setSizeScaled / toActual, тільки без повтору в sizeChanged().
     */
    var scaled = false

    /** Дизайн → актуальне через sizeScaler лейауту, якщо вузол у дизайн-одиницях; інакше як є. */
    internal fun toActual(value: Float): Float = if (scaled) layout.sizeScaler.toActual(value) else value
```
**на**
```kotlin
    /** Дизайн → актуальне через sizeScaler ЛЕЙАУТУ. Без скейлера factor = 1 — число як є. */
    internal fun toActual(value: Float): Float = layout.sizeScaler.toActual(value)
```

**2е. Коментар над margin'ами — ЗАМІНИТИ**

```kotlin
    // Пишеш сире число (дизайн- або актуальне — за scaled), лейаут читає вже
    // помножене: так усі resolve* лишаються без змін.
```
**на**
```kotlin
    // Пишеш число з макета, лейаут читає вже помножене на factor групи:
    // так усі resolve* лишаються без змін.
```

**2ж. `// ── Dimension shortcuts ──` — ЗАМІНИТИ**

```kotlin
    /** Усі числа цього вузла — в дизайн-одиницях макета (див. scaled). Викликати ПЕРШИМ у блоці. */
    fun scaled() { scaled = true }

    /** Розмір у дизайн-одиницях: актуальний = design × sizeScaler.factor, перераховується при кожному resolve. */
    fun size(width: Float, height: Float) {
        designWidth = width;  widthMode  = Dimension.SCALED
        designHeight = height; heightMode = Dimension.SCALED
        scaled = true
    }
```
**на**
```kotlin
    /**
     * Розмір у дизайн-одиницях. Цукор над реєстром групи: те саме, що
     * actor.setSizeScaled(w, h) перед add(). Актор отримує розмір ще в блоці,
     * тому require(width > 0) в add() проходить.
     */
    fun size(width: Float, height: Float) {
        with(layout) { actor.setSizeScaled(width, height) }
        widthMode = Dimension.FIXED; heightMode = Dimension.FIXED
    }
```

---

## 3. `AConstraintLayout.kt`

`app/src/main/java/com/lewydo/orbitdash/game/actors/layout/constraintLayout/AConstraintLayout.kt`

**3а. `add()` — ЗАМІНИТИ**

```kotlin
        val params = CLParams(this).apply(block)
```
**на**
```kotlin
        val params = CLParams(this, actor).apply(block)
```

**3б. `applyDimension()` — ВИДАЛИТИ** два рядки (по одному в кожному `when`):

```kotlin
            Dimension.SCALED -> p.toActual(p.designWidth)
```
```kotlin
            Dimension.SCALED -> p.toActual(p.designHeight)
```

Тут уже компілюється.

---

## 4. Актори: `sizeChanged()` з повтором — ВИДАЛИТИ

Число лишається там, де воно вже є — в `addX()`. Оберег `parent == null` більше не потрібен:
реєстр порожній, поки дітей нема.

**4а. `ABall.kt`** (`actors/objects/`) — ЗАМІНИТИ три місця:

```kotlin
        add(aGlow) { scaled(); size(GLOW_SIZE, GLOW_SIZE); center() }
```
**на**
```kotlin
        add(aGlow) { size(GLOW_SIZE, GLOW_SIZE); center() }
```

```kotlin
        add(aPoint) {
            scaled()
            size(POINT_SIZE, POINT_SIZE)
```
**на**
```kotlin
        add(aPoint) {
            size(POINT_SIZE, POINT_SIZE)
```

```kotlin
        setOrigin(Align.center)
        // Розміри й відступи дітей — у дизайн-одиницях (scaled()), лейаут
        // перераховує їх сам при кожному resolve. Тут повторювати нічого.
```
**на**
```kotlin
        setOrigin(Align.center)
        // Розміри дітей тримає реєстр групи, margin'и лейаут множить сам —
        // тут повторювати нічого.
```

**4б. `ASpike.kt`** (`actors/objects/`) — ВИДАЛИТИ

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent != null) aGlow.setSizeScaled(100f, 100f)
    }

```

**4в. `ABooster.kt`** (`actors/objects/`) — ВИДАЛИТИ

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent != null) aGlow.setSizeScaled(110f, 110f)
    }

```

**4г. `AGem.kt`** (`actors/objects/`) — ВИДАЛИТИ

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent == null) return
        aGlow.setSizeScaled(GLOW_SIZE, GLOW_SIZE)
        aPoint.setSizeScaled(POINT_SIZE, POINT_SIZE)
    }

```

**4д. `ABallDecor.kt`** і **`AGemDecor.kt`** (`actors/objects/decor/`) — ВИДАЛИТИ в кожному

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlow.parent == null) return
        aGlow.setSizeScaled(100f, 100f)
    }

```

**4е. `AShieldPip.kt`** (`actors/panel/boost/`) — ВИДАЛИТИ

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlowImg.parent == null) return
        aGlowImg.setSizeScaled(GLOW_W, GLOW_H)
    }

```

**4ж. `APanelShield.kt`** (`actors/panel/boost/`) — ВИДАЛИТИ

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        if (listShieldPip.first().parent == null) return
        listShieldPip.forEach { it.setSizeScaled(PIP_W, PIP_H) }
    }

```

`pip.setSizeScaled(PIP_W, PIP_H)` в `applySlots()` лишається — його досить. Піп, що вийшов у
пул (`parent == null`), реєстр пам'ятає і не чіпає.

**4з. `AOrbitEmblem.kt`** (`actors/orbit/`) — ВИДАЛИТИ

```kotlin
    /** При анімації розміру перерозкладаємо все, що задано у дизайн-одиницях. */
    override fun sizeChanged() {
        super.sizeChanged()
        if (aRingInner.parent == null) return
        aOrbitGlowImg.setSizeScaled(660f, 660f)
        aRingInner.setSizeScaled(140f, 140f)
        aBall.setSizeScaled(BALL_SIZE, BALL_SIZE)
        aGem.setSizeScaled(GEM_SIZE, GEM_SIZE)
    }

```

`aBall`/`aGem` додані через `addActor` + `setSizeScaled` — розмір тримає реєстр, позицію —
`placeOnOrbit()`; у реєстрі позиції в них нема, тож морф не воює з орбітою.

**`ABrandLogo.kt`** — не чіпати: `setSizeScaled(140f, 140f)` в `addBrandFrontImg()` тепер
тримається сам, `sizeChanged()` там і не було.

**`AOrbitField.kt`** — не чіпати: `applyRingSize()` — похідна геометрія, `sizeChanged()` там
законний.

---

## 5. `ALevelPopup.kt` — перший клієнт слоту позиції (опційно)

`app/src/main/java/com/lewydo/orbitdash/game/actors/popup/ALevelPopup.kt`, `addXPLbl()` — ЗАМІНИТИ

```kotlin
        aXPLbl.setBounds(82f, 127f, 533f, 98f)
```
**на**
```kotlin
        aXPLbl.setBoundsScaled(82f, 127f, 533f, 98f)   // група тримає і розмір, і позицію
```

Скейлера в попапа нема → `factor = 1`, поведінка та сама, але при ресайзі попапа лейбл
тепер піде за ним. Числа старої системи (1080-макет) — перевести на 360 окремо.

---

## Пастки

- `setSizeScaled` кличуть **до** `add()` — на момент реєстрації `parent == null`. Це нормально:
  `parent === this` перевіряється лише при переприкладанні.
- `Actions.sizeTo` на зареєстрованій дитині: при ресайзі батька розмір повернеться до
  дизайн-числа. Так само поводився старий `sizeChanged()` — регресії нема; треба анімувати —
  `freeScaled()`.
- `size()` працює і в `update(actor) { … }` — актор у `CLParams` є.
- `setOrigin(Align.center)` в `act()` (`AGem`, `ASpike`, `AGemDecor`) — як було, не чіпати.

## Перевірка на пристрої

1. Лоадер → меню: емблема морфить (`animMorphTo`) — світіння, внутрішнє кільце, куля й гем
   масштабуються разом із нею, куля лишається на орбіті.
2. PLAY: крапка `ABall` у лівому верхньому куті з відступом 10 — margin'и без `scaled()`
   множаться самі. Гем/шип/бустер різних розмірів (`prepare()` → 18/25/13) — світіння пропорційне.
3. Щит: 3 піпи з'являються/зникають — розмір піпа після повернення з пулу правильний.
4. `BrandScreen`: передній шар логотипа 140/208 від фрейму.
