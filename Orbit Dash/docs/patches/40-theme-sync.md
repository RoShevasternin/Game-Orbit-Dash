# 40 — `ThemeSync`: тема в акторі одним рядком

## Що і навіщо

Шість акторів тримали однакові шість рядків:

```kotlin
private var themeVersion = -1

override fun act(delta: Float) {
    super.act(delta)
    if (themeVersion != ThemeManager.version) {
        themeVersion = ThemeManager.version
        syncTheme()
    }
}
```

А `ASpike`, `ABadgeDot`, `ADefButton` фарбувались **щокадру** без перевірки.
Коментар у `ADefButton` пояснював це тим, що «разовий виклик зловив би проміжний
кадр». Але `ThemeManager.version` росте на **кожному** кадрі лерпу, тож перевірка
версії ловить увесь перехід, і в щокадровій синхронізації немає потреби.

Тепер перевірку робить маленький клас, а актор пише одне:

```kotlin
private val themeSync = ThemeSync(::syncTheme)
…
themeSync.sync()
```

Функції `syncTheme()` в акторах **не змінюються**: `ThemeSync` просто кличе їх,
коли версія розійшлась.

**Виграш — однаковість, а не FPS.** Щокадрова синхронізація коштувала кілька
`Color.set` на актора. Це копійки навіть на двох десятках шипів. Сенс у тому, що
тепер є один спосіб, і його не доведеться вигадувати в кожному новому акторі.

### Чому не інакше

- **Не `Action` на акторі** (`addAction(ThemeSyncAction { … })`), хоч це й
  прибрало б `act()` зовсім. `ADefButton.press()`, `fadeParts()`, `AOrbitEmblem`,
  `AShieldPip` і `actorUtil` кличуть `clearActions()`, і синхронізація мовчки
  зникла б на першому ж тапі.
- **Не хук в `AdvancedGroup`** (`open fun onThemeChanged()`). Базова група в
  `utils/advanced` почала б знати про тему гри, і хук не покрив би
  не-груп (`AStarField` — це `VfxImage`).
- **Не підписка в `ThemeManager`** (список слухачів). Актори з пулів живуть поза
  сценою, а відписку легко забути. У `ThemeSync` забувати нічого.

### Що свідомо НЕ переведено

| актор | чому лишається щокадру |
|---|---|
| `AOrbitField.syncRings()` | щокадру з інших причин: активне кільце, крок пунктира. Тема тут — два рядки з багатьох |
| `AStarField` (`if (syncWithTheme) fx.colorB.set(…)`) | один `set` на все поле. Через `ThemeSync` довелося б робити `invalidate()` при перемиканні `syncWithTheme` — складніше за виграш |
| `AComet.draw()` | колір ставиться в `tint` безпосередньо перед малюванням, залежить від `bright` конкретної комети |

### Пакет нового файлу

`game/utils/theme/`, поруч із `ThemeManager`. Файл залежить лише від
`ThemeManager.version`, а імпортують його актори. Це частина механізму теми,
тому новий пакет не потрібен.

### Перевірено

`:app:compileDebugKotlin` у лабораторній копії — **BUILD SUCCESSFUL**.
На пристрої перемикання скіна **не проганяв**: для покупки скіна бракує кристалів.
Логіка та сама, що вже працювала в `ABall`/`AGem`.

---

## 1. НОВИЙ файл `app/src/main/java/com/lewydo/orbitdash/game/utils/theme/ThemeSync.kt`

```kotlin
package com.lewydo.orbitdash.game.utils.theme

// ─────────────────────────────────────────────────────────────────────────────
// ThemeSync — перефарбувати актора, лише коли тема справді змінилась.
//
//     private val themeSync = ThemeSync(::syncTheme)
//
//     override fun addActorsOnGroup() { …; themeSync.sync() }       // перший кадр — уже в кольорі
//     override fun act(delta: Float)  { super.act(delta); themeSync.sync() }
//
// sync() порівнює збережену ThemeManager.version з поточною: у спокої — нічого,
// під час лерпу скіна — щокадру. Перший виклик фарбує завжди.
//
// Чому не Action на акторі: press()/fadeParts()/анімації кличуть clearActions()
// і мовчки зняли б синхронізацію. Чому не підписка в ThemeManager: актори з
// пулів живуть поза сценою, відписку легко забути — а тут забувати нічого.
// ─────────────────────────────────────────────────────────────────────────────
class ThemeSync(private val apply: () -> Unit) {

    private var seenVersion = -1

    fun sync() {
        if (seenVersion == ThemeManager.version) return
        seenVersion = ThemeManager.version
        apply()
    }

    /** Колір залежить не лише від теми (override, variant) — перефарбувати на наступному sync(). */
    fun invalidate() { seenVersion = -1 }
}
```

---

## 2. Шість акторів з ручною перевіркою — ОДНАКОВА заміна

Файли (усі в `app/src/main/java/com/lewydo/orbitdash/game/actors/`):

- `objects/ABall.kt`
- `objects/AGem.kt`
- `objects/decor/ABallDecor.kt`
- `objects/decor/AGemDecor.kt`
- `orbit/AOrbitEmblem.kt`
- `panel/APanelGameHud.kt`: тільки 2а, 2б, 2в (`syncTheme()` в `addActorsOnGroup()` там немає, а `themeVersion` стоїть серед полів угорі, рядок 36)

**2а. ДОДАТИ** імпорт під `import …theme.ThemeManager`:

```kotlin
import com.lewydo.orbitdash.game.utils.theme.ThemeSync
```

**2б. ЗАМІНИТИ** поле:

```kotlin
    private var themeVersion = -1
```
на
```kotlin
    private val themeSync = ThemeSync(::syncTheme)
```

(У HUD ім'я `theme` уже зайняте, `private val theme = ThemeManager.current`,
тому скрізь `themeSync`.)

**2в. ЗАМІНИТИ** в `act()` увесь блок:

```kotlin
        if (themeVersion != ThemeManager.version) {
            themeVersion = ThemeManager.version
            syncTheme()
        }
```
на
```kotlin
        themeSync.sync()
```

**2г. ЗАМІНИТИ** в кінці `addActorsOnGroup()` (крім HUD):

```kotlin
        syncTheme()
    }
```
на
```kotlin
        themeSync.sync()
    }
```

Імпорт `ThemeManager` **лишається**: його читає `syncTheme()`.

---

## 3. `actors/objects/ASpike.kt`

**ДОДАТИ** імпорт `ThemeSync` (як 2а).

**ЗАМІНИТИ** — кінець `addActorsOnGroup()` і початок `act()`:

```kotlin
        addPoint()

        syncTheme()
    }

    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()
```
на
```kotlin
        addPoint()

        themeSync.sync()
    }

    private val themeSync = ThemeSync(::syncTheme)

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
```

---

## 4. `actors/ui/ABadgeDot.kt`

**ДОДАТИ** імпорт `ThemeSync` (як 2а).

**ЗАМІНИТИ** в `addActorsOnGroup()`:

```kotlin
        syncTheme()
        startPulse()
```
на
```kotlin
        themeSync.sync()
        startPulse()
```

**ЗАМІНИТИ** `act()` разом із коментарем над ним:

```kotlin
    /** Колір не зберігається — щокадру з теми, тому лерп скіна працює сам. */
    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()
    }
```
на
```kotlin
    private val themeSync = ThemeSync(::syncTheme)

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
    }
```

---

## 5. `actors/button/ADefButton.kt`

Тут колір залежить **не лише від теми**: ще від `variant`, `bgOverride` і від
того, чи вже створений бейдж. Тому в трьох місцях `invalidate()`: без нього
зміна `bgOverride` у спокійній темі не перефарбувала б кнопку ніколи.

**ДОДАТИ** імпорт `ThemeSync` (як 2а).

**5а. ДОДАТИ** після поля `aBadge`:

```kotlin
    /** Створюється ЛІНИВО — на кнопках без бейджа не існує як актор. */
    private var aBadge: ABadgeDot? = null

    private val themeSync = ThemeSync(::syncTheme)
```

**5б. ЗАМІНИТИ** сеттер `variant`:

```kotlin
    var variant = variant
        set(value) { field = value; applyVariant() }
```
на
```kotlin
    var variant = variant
        set(value) { field = value; applyVariant(); themeSync.invalidate() }   // variant міняє і кольори
```

**5в. ЗАМІНИТИ** `bgOverride`:

```kotlin
    var bgOverride: Color? = null
```
на
```kotlin
    var bgOverride: Color? = null
        set(value) { field = value; themeSync.invalidate() }
```

**5г. ЗАМІНИТИ** в `addActorsOnGroup()`:

```kotlin
        syncTheme()      // щоб перший кадр був уже правильного кольору
```
на
```kotlin
        themeSync.sync() // щоб перший кадр був уже правильного кольору
```

**5д. ЗАМІНИТИ** `act()` разом із коментарем (старий коментар уже неправдивий):

```kotlin
    /**
     * Кольори синхронізуються ЩОКАДРУ, бо ThemeManager не перемикає палітру,
     * а лерпає її (~0.35 с) — разовий виклик зловив би проміжний кадр.
     */
    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()
    }
```
на
```kotlin
    /** Тема лерпається ~2 с — sync() ловить кожен її кадр, а в спокої нічого не робить. */
    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
    }
```

**5е. ЗАМІНИТИ** кінець `createBadge()`:

```kotlin
        aBadge = badge
        addActor(badge)
        layoutBadge()
    }
```
на
```kotlin
        aBadge = badge
        addActor(badge)
        layoutBadge()
        themeSync.invalidate()   // тінт бейджа ставить syncTheme() — новий бейдж його ще не має
    }
```

---

Компілюється лише після всіх пунктів разом із п. 1.

## Або скопіювати з лабораторії

Поки жива сесія. Лабораторія зроблена з твого проєкту о 10:56 сьогодні. Якщо
ти після цього міняв ці файли, копіювати **не можна**, вставляй руками.

```bash
LAB=/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f03d26ed-12c3-428e-bcbc-fd80ce4b4d8f/scratchpad/lab
G=app/src/main/java/com/lewydo/orbitdash/game
for f in utils/theme/ThemeSync.kt \
         actors/objects/ABall.kt actors/objects/AGem.kt actors/objects/ASpike.kt \
         actors/objects/decor/ABallDecor.kt actors/objects/decor/AGemDecor.kt \
         actors/orbit/AOrbitEmblem.kt actors/panel/APanelGameHud.kt \
         actors/ui/ABadgeDot.kt actors/button/ADefButton.kt; do
  cp "$LAB/$G/$f" "$G/$f"
done
```

(запускати з `Orbit Dash/`)

---

## Застосовано — 17 вересня 2026

Частину вставив ти (`ThemeSync.kt`, `ABallDecor`, `ASpike`, `ABadgeDot`, `ADefButton`),
решту, з твого дозволу, — Claude прямо в проєкті, з секцією `// Field` над `themeSync`:
`ABall`, `AGem`, `AGemDecor`, `AOrbitEmblem`, `APanelGameHud`.

Поза початковим патчем переведено ще два місця, що фарбувались щокадру:

- `actors/loader/AMainLoader.kt`: титули й тіні `ORBIT` / `DASH`
- `GDXGame.render()`: колір очищення екрана

Виправлено в `ADefButton.act()` коментар, скопійований з `addActorsOnGroup()`, і
шапку `Theme.kt` (там було «~0.35 с», а `TRANSITION_TIME = 2f`).

`:app:compileDebugKotlin --rerun-tasks` у проєкті — BUILD SUCCESSFUL.
`themeVersion` у коді не лишилось.
