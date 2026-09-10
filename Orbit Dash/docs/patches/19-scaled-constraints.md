# 19 — Констрейнти в дизайн-одиницях: `scaled()` / `size()` замість повторів у `sizeChanged()`

## Що і навіщо

Дев'ять акторів повторюють у `sizeChanged()` те, що вже написано в `add*()`:
`AOrbitEmblem` (4 рядки), `ABall` (3), `AGem` (2), `ASpike`, `ABooster`, `ABallDecor`,
`AGemDecor`, `AShieldPip`, `APanelShield` (по 1). Причина одна: `CLParams` зберігає
**актуальні** числа, а масштабування (`setSizeScaled`, `toActual`) живе в акторі.
Тому після зміни розміру групи актор мусить перерахувати все сам — і margin, який
осів у `CLParams` готовим числом, при цьому забувають (`ABall`: крапка масштабувалась,
відступ — ні).

Рішення — перенести масштабування туди, де вже є перерахунок: у `resolve` лейауту.
`CLParams` отримує режим `scaled` — «усі числа вузла в дизайн-одиницях макета», і
множить їх на `layout.sizeScaler.factor` при **кожному** читанні. `AConstraintLayout`
і так перераховує всі вузли після `sizeChanged()` — отже, `sizeChanged()` в акторі
стає порожнім.

```kotlin
add(aPoint) {
    scaled()                                 // усі числа нижче — з макета (Figma)
    size(POINT_SIZE, POINT_SIZE)
    startToStart(margin = POINT_PADDING); topToTop(margin = POINT_PADDING)
}
```

Як влаштовано:

- `Dimension.SCALED` — розмір = `sizeScaler.toActual(designWidth)` лейауту;
- margin-поля лишаються `var`, але **геттер проганяє через `toActual`**: пишеш сире число,
  лейаут читає масштабоване. Тому 26 місць у `resolve*` не змінюються, а `update(actor) { … }`
  працює як і раніше;
- для звичайних вузлів `toActual` віддає число як є — поведінка всього, що є, не змінюється.

Перевірено компілятором (лабораторна збірка з твоєю поточною версією, включно з
`update()`). Уточнення: куля в емблемі — `ABallDecor`, не `ABall`; `ABall` живе лише в
`GameScreen`, де розмір ставиться один раз до layout. Тож для `ABall` патч прибирає
дублювання, а не живий баг з відступом; сам механізм `scaled()` стане важливим там, де
розмір групи справді анімується. Перша перевірка в грі — PLAY: крапка у верхньому лівому
куті кулі.

---

## 1. `CLParams.kt`

`app/src/main/java/com/lewydo/orbitdash/game/actors/layout/constraintLayout/CLParams.kt`

**1а. Шапка — ДОДАТИ** після рядка `//    PERCENT       — розмір = відсоток від розміру layout`:

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

**1б. `enum class Dimension` — ДОДАТИ** між `PERCENT` і `MATCH_CONSTRAINT`:

```kotlin
    SCALED,           // розмір = designWidth/Height × sizeScaler.factor лейауту
```

**1в. Після `widthPercent` / `heightPercent` — ДОДАТИ:**

```kotlin
    var designWidth   = 0f   // використовується тільки якщо widthMode == SCALED
    var designHeight  = 0f   // використовується тільки якщо heightMode == SCALED

    // ── Дизайн-одиниці ────────────────────────────────────────────────────────

    /**
     * true → усі числа вузла (size, margin) — у дизайн-одиницях макета; лейаут
     * множить їх на sizeScaler.factor при кожному resolve. Те саме, що
     * setSizeScaled / toActual, тільки без повтору в sizeChanged().
     */
    var scaled = false

    /** Дизайн → актуальне через sizeScaler лейауту, якщо вузол у дизайн-одиницях; інакше як є. */
    internal fun toActual(value: Float): Float = if (scaled) layout.sizeScaler.toActual(value) else value
```

**1г. Margin — ЗАМІНИТИ** чотири оголошення:

```kotlin
    var marginStart : Float = 0f
    var marginEnd   : Float = 0f
```
```kotlin
    var marginTop    : Float = 0f
    var marginBottom : Float = 0f
```
**на**
```kotlin
    // Пишеш сире число (дизайн- або актуальне — за scaled), лейаут читає вже
    // помножене: так усі resolve* лишаються без змін.
    var marginStart : Float = 0f
        get() = toActual(field)
    var marginEnd   : Float = 0f
        get() = toActual(field)
```
```kotlin
    var marginTop    : Float = 0f
        get() = toActual(field)
    var marginBottom : Float = 0f
        get() = toActual(field)
```

**1д. `// ── Dimension shortcuts ──` — ДОДАТИ** перед `fillParent()`:

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

---

## 2. `AConstraintLayout.kt` — `applyDimension()`

**ДОДАТИ** гілку в обидва `when` (після `Dimension.PERCENT -> …`):

```kotlin
            Dimension.SCALED -> p.toActual(p.designWidth)
```
```kotlin
            Dimension.SCALED -> p.toActual(p.designHeight)
```

Більше нічого: `require` в `add()` перевіряє лише `FIXED`, `resolveX/Y` читають margin
уже помноженими.

---

## 3. `ABall.kt`

**ЗАМІНИТИ** `sizeChanged()`:

```kotlin
    override fun sizeChanged() {
        super.sizeChanged()
        setOrigin(Align.center)
        // Розміри й відступи дітей — у дизайн-одиницях (scaled()), лейаут
        // перераховує їх сам при кожному resolve. Тут повторювати нічого.
    }
```

**ЗАМІНИТИ** `addGlow()` і `addPoint()`:

```kotlin
    private fun addGlow() {
        add(aGlow) { scaled(); size(GLOW_SIZE, GLOW_SIZE); center() }
    }

    private fun addPoint() {
        add(aPoint) {
            scaled()
            size(POINT_SIZE, POINT_SIZE)
            startToStart(margin = POINT_PADDING); topToTop(margin = POINT_PADDING)
        }
    }
```

`addBall()` без змін (`fillParent()` і так живе в лейауті).

---

## Далі — ті самі три рядки в решті восьми

Той самий рецепт: `setSizeScaled(w, h)` перед `add` → `scaled(); size(w, h)` у блоці,
рядок у `sizeChanged()` — видалити. Кандидати з найбільшим виграшем — `AOrbitEmblem`
(чотири повтори) і `AGem`. Але там, де актор доданий через `addActor` (як `aBall`/`aGem`
в емблемі — позицією керує `placeOnOrbit()`), лейаут його не веде, і `setSizeScaled` у
`sizeChanged()` лишається — це не повтор, а єдине місце.
