# 33 — Дебаг: ромби бустів і шипа, пауза м'яча, розстановка без накладань

## Що і навіщо

Тестувати бусти через одну кнопку `+SHIELD` незручно: не видно кольорів і анімацій
решти, підкинуте стає в одну точку, а м'яч за секунду в нього врізається.

| було | стане |
|---|---|
| `RESTART` | прибрано — після смерті ран і так рестартить тап по полю |
| `+SHIELD` | прибрано — замість нього колонка ромбів |
| `WIDE 22..145` | `COMBO 100%` |
| — | `PAUSE` — стоїть лише м'яч |
| — | **колонка ромбів ліворуч від панелі**: 5 бустів у своєму кольорі й зі своєю іконкою + шип |

### Перевірено на телефоні (лабораторна збірка, 720×1650)

- ромби читаються, у тому числі білий SLOW;
- `PAUSE: ON` — м'яч стоїть, рахунок стоїть, ромби підкидають;
- 12 натискань поспіль: 10 об'єктів лягли на кільце м'яча віялом від +90°, коли там
  скінчилось місце — ще 2 на сусіднє; **жодного накладання**;
- перетяг колонки **за ромб** — колонка поїхала, нового буста не з'явилось;
- `:engine:test` — 4 тести, `assembleDebug` — зібрано.

### Три рішення, які варто знати

**Пауза живе в рушії, а не в екрані.** Якщо просто не кликати `engine.update()`, підкинуте
ніколи не проявиться: актор малюється з альфою `e.s`, а її росте саме `update()`. Тому
`debugFrozen`: кут, час, спавн, таймери й колізії стоять, але поява й посадка на кільце
дограють, а тап переводить м'яч на інше кільце — можна відійти від шипа.

**Розстановка — теж у рушії й без `rng`.** Сітка з кроком `DEBUG_GAP = 80` engine-юнітів
**по дузі** (≈ діаметр світіння бустера), тож на внутрішньому кільці кут більший, на
зовнішньому — менший, а відстань на екрані однакова. Вікно 30°..170° попереду: ближче не
встигнеш побачити, далі сутність стає «позаду» й її одразу прибирає правило `rel < −60`.
Без `rng` — щоб debug-кнопка не зсувала послідовність рану.

**Перемикачі панелі тепер переживають рестарт.** Це заодно лагодить старий баг: `WIDE`
жив у рушії, а новий ран = новий рушій — кнопка лишалась «ON», а рушій повертався в
дефолт. Тепер прапорці в екрані, `applyDebugFlags()` переприкладає їх на кожен ран.

**Одна пастка на пристрої:** якщо м'яч уже врізався (ран мертвий), ромби додадуть об'єкти,
але вони не з'являться — мертвий ран не оновлюється. Тапни по полю для рестарту: пауза
підхопиться одразу, якщо була ввімкнена.

Порядок вставки: 1 → 2 → 3. Компілюється після 3 (`GameScreen` кличе нові функції рушія й
новий актор).

---

## 1. `RunEngine.kt` — пауза і розстановка

**Файл:** `engine/src/main/kotlin/com/lewydo/orbitdash/engine/RunEngine.kt`

### 1.1 `companion object` — після `const val MAX_COMBO = 8`

**ДОДАТИ** (між `MAX_COMBO` і `// ── кути ──`):

```kotlin

        // ── debug-розстановка ──
        /**
         * Мінімальна дуга між debug-сутностями, engine units. 80 = 40 design поля
         * ≈ діаметр glow бустера: сусіди не накладаються навіть світінням.
         * Дуга, а не кут: на внутрішньому кільці той самий кут — коротший відрізок.
         */
        internal const val DEBUG_GAP = 80f

        /** Вікно розстановки попереду м'яча, градуси. Ближче за 30° — не встигнеш
         *  побачити; далі за 170° сутність стає «позаду» й її прибирає rel < −60. */
        private const val DEBUG_ARC_START = 90f
        private const val DEBUG_ARC_MIN   = 30f
        private const val DEBUG_ARC_MAX   = 170f
```

`internal`, а не `private`, у `DEBUG_GAP` — щоб тест рушія міряв ту саму константу.

### 1.2 `update()` — перші рядки

**ЗАМІНИТИ**

```kotlin
    fun update(dt: Float) {
        if (phase != Phase.RUN) return
```

на

```kotlin
    fun update(dt: Float) {
        if (phase != Phase.RUN) return
        if (debugFrozen) { updateFrozen(dt); return }
```

### 1.3 Секція `// Debug` — замінити `debugSpawnBoost` цілком

**ЗАМІНИТИ**

```kotlin
    /**
     * DEBUG: підкинути бустер попереду гравця, на ЙОГО кільці — щоб він
     * гарантовано долетів до нього, а не проїхав повз по сусідньому.
     * Кут 90° уперед: досить часу побачити, замало щоб забути про нього.
     */
    fun debugSpawnBoost(bt: Boost) {
        _entities.add(Entity(
            id    = nextId++,
            kind  = Kind.BOOST,
            boost = bt,
            ring  = ringIndex,
            a     = norm(angle + 90f),
            rr    = ringR[ringIndex],
        ))
    }
```

на

```kotlin
    /**
     * DEBUG-пауза м'яча. Кут не росте, час, спавн, таймери й колізії стоять —
     * можна підкидати шипи просто перед м'ячем. Але те, що вже є на полі,
     * дограє появу (e.s) і посадку на кільце, а тап переводить м'яч на інше
     * кільце: так видно щойно підкинуте й можна відійти від шипа.
     */
    var debugFrozen = false

    /** DEBUG: бустер попереду гравця. false — вільного місця у вікні не лишилось. */
    fun debugSpawnBoost(bt: Boost): Boolean = debugPlace(Kind.BOOST, bt)

    /** DEBUG: шип попереду гравця. false — вільного місця у вікні не лишилось. */
    fun debugSpawnSpike(): Boolean = debugPlace(Kind.SPIKE, null)

    /**
     * Спершу кільце гравця — щоб підкинуте гарантовано долетіло до м'яча, —
     * потім решта по колу. Жодного rng: debug-кнопка не зсуває послідовність
     * рану, яку далі генерує seed.
     */
    private fun debugPlace(kind: Kind, boost: Boost?): Boolean {
        for (k in 0 until ringCount) {
            val ring = (ringIndex + k) % ringCount
            val a    = debugFreeAngle(ring) ?: continue
            _entities.add(Entity(
                id    = nextId++,
                kind  = kind,
                boost = boost,
                ring  = ring,
                a     = a,
                rr    = ringR[ring],
            ))
            return true
        }
        return false
    }

    /**
     * Перший вільний кут на кільці віялом від +90°: 90, 90+s, 90−s, 90+2s …
     * Крок s — DEBUG_GAP у градусах саме цього кільця. Зайнято, якщо будь-яка
     * сутність кільця (своя чи з натурального спавну) ближча за 0.9·s:
     * запас від float-похибки norm(), щоб сусідня клітинка сітки не «злипалась».
     */
    private fun debugFreeAngle(ring: Int): Float? {
        val step = DEBUG_GAP / ringR[ring] * RAD_TO_DEG
        val maxK = ((DEBUG_ARC_MAX - DEBUG_ARC_START) / step).toInt() + 1

        for (i in 0..maxK * 2) {
            val k   = (i + 1) / 2
            val off = DEBUG_ARC_START + if (i % 2 == 1) k * step else -k * step
            if (off < DEBUG_ARC_MIN || off > DEBUG_ARC_MAX) continue

            val a = norm(angle + off)
            if (_entities.none { it.ring == ring && abs(angDiff(it.a, a)) < step * 0.9f }) return a
        }
        return null
    }

    private fun updateFrozen(dt: Float) {
        radius += (ringR[ringIndex] - radius) * min(1f, 14f * dt)
        for (i in 0 until 3) ringR[i] += (target[i] - ringR[i]) * min(1f, 2.5f * dt)

        for (e in _entities) {
            e.s = min(1f, e.s + 4f * dt)
            if (!e.pulled) e.rr += (ringR[e.ring] - e.rr) * min(1f, 6f * dt)
        }
    }
```

---

## 2. `ADebugIconBar.kt` — НОВИЙ файл

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/debug/ADebugIconBar.kt`

**Пакет** `game.actors.debug` — поруч з `ADebugPanel` і `ADebugHud`: той самий рід
службових оверлеїв, так само нічого не знає про гру (колір, іконку й дію дає екран).

```kotlin
package com.lewydo.orbitdash.game.actors.debug

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.actor.setOnClickListener
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.debug.dragToMove
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG

// ═════════════════════════════════════════════════════════════════════════════
//  ADebugIconBar — стовпчик ромбів «іконка → дія» поруч із ADebugPanel.
//
//  Як і ADebugPanel, НІЧОГО не знає про гру: колір, іконку й дію дає екран.
//  На GameScreen це «підкинути буст / шип» — у кольорі й з іконкою самого
//  об'єкта, щоб перевіряти вигляд, не читаючи підписів.
//
//    private val aDebugIconBar by lazy {
//        ADebugIconBar(this, listOf(
//            ADebugIconBar.Item(icon, color, iconW = 28f, iconH = 32f) { engine.debugSpawnSpike() },
//        ))
//    }
//
//    addDebugPanel(aDebugPanel)
//    addDebugIconBar(aDebugIconBar, aDebugPanel)   // ПІСЛЯ панелі: вона — якір
//
//  Ромб — gdxGame.assetsMsdf.gem: той самий запечений регіон, що й в AGem,
//  нуль нових текстур. Іконка темна поверх кольору — читається й на білому SLOW.
//
//  Клік — setOnClickListener(stopEvent = false): подія мусить спливти до
//  стовпчика, інакше перетяг не почнеться з ромба. Клік після перетягу не
//  прилітає — DragToMove скасовує тач-фокус, і ClickListener отримує touchUp
//  «за межами» (Stage ставить координати в Integer.MIN_VALUE).
// ═════════════════════════════════════════════════════════════════════════════
class ADebugIconBar(
    override val screen: AdvancedScreen,
    private val items: List<Item>,
) : AAutoLayout(
    screen     = screen,
    direction  = Direction.VERTICAL,
    gapMain    = 6f,
    alignCross = AlignCross.CENTER,
    sizingW    = Sizing.HUG,
    sizingH    = Sizing.HUG,
) {

    /**
     * Один ромб. Розмір іконки — від викликача: арт різний (PNG бустів має
     * поля навколо гліфа, запечений шип — ні), одне число на всіх не підходить.
     */
    class Item(
        val icon   : TextureRegion,
        val color  : Color,
        val iconW  : Float,
        val iconH  : Float,
        val onClick: () -> Unit,
    )

    companion object {
        /** Сторона ромба. 34 юніти ≈ 100 px на 1080 — палець влучає без прицілювання. */
        const val CELL = 34f
    }

    override fun addActorsOnGroup() {
        if (!IS_DEBUG) { isVisible = false; return }

        items.forEach { item ->
            val cell = ACell(screen, item)
            cell.setSize(CELL, CELL)
            add(cell)
            cell.setOnClickListener(stopEvent = false) { item.onClick() }
        }
    }

    /** Ромб у кольорі + темна іконка по центру. */
    private class ACell(
        override val screen: AdvancedScreen,
        private val item: Item,
    ) : AConstraintLayout(screen) {

        private val aDiamond = Image(gdxGame.assetsMsdf.gem)
        private val aIcon    = Image(item.icon)

        override fun addActorsOnGroup() {
            aDiamond.setColorRGB(item.color)
            add(aDiamond) { fillParent() }

            aIcon.setColorRGB(GameColor.background)
            add(aIcon) { size(item.iconW, item.iconH); center() }
        }
    }
}

// ----------------------------------------------------------------------------
// Helper
// ----------------------------------------------------------------------------
/**
 * Ставить стовпчик ЛІВОРУЧ від панелі, низом до її низу. Поки стовпчик не
 * чіпали, він їде за панеллю (якір); потягнув сам стовпчик — живе окремо,
 * позиція лягає в Preferences під своїм ключем.
 */
fun AConstraintLayout.addDebugIconBar(bar: ADebugIconBar, panel: ADebugPanel) {
    if (!IS_DEBUG) return
    bar.setSize(1f, 1f)   // HUG по обох осях; нульовий розмір add() не пропустить
    add(bar) { endToStart(panel, margin = 8f); bottomToBottom(panel) }

    bar.dragToMove("${bar.screen::class.simpleName}.iconBar")
}
```

---

## 3. `GameScreen.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt`

### 3.1 Імпорти

**ЗАМІНИТИ**

```kotlin
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.ADebugPanel
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugPanel
```

на

```kotlin
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.ADebugIconBar
import com.lewydo.orbitdash.game.actors.debug.ADebugPanel
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugIconBar
import com.lewydo.orbitdash.game.actors.debug.addDebugPanel
```

І **ДОДАТИ** до решти імпортів:

```kotlin
import com.lewydo.orbitdash.game.content.info
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
```

### 3.2 Поля — після `debugTimeScale`

**ЗАМІНИТИ**

```kotlin
    private var debugOrbit3    = false
    /** DEBUG: множник часу для рушія. x0.25 — розглядати near-miss «під лупою». */
    private var debugTimeScale = 1f
```

на

```kotlin
    private var debugOrbit3    = false
    /** DEBUG: множник часу для рушія. x0.25 — розглядати near-miss «під лупою». */
    private var debugTimeScale = 1f
    /** DEBUG: широке вікно near-miss (22..145) — комбо з сусіднього кільця. */
    private var debugComboWide = false
    /** DEBUG: м'яч стоїть, решта живе — див. RunEngine.debugFrozen. */
    private var debugPaused    = false
```

### 3.3 `aDebugPanel` — ЗАМІНИТИ цілком, і одразу під ним новий `aDebugIconBar`

Від `private val aDebugPanel by lazy {` до його закривної `}` (рядки 106–130) — **весь
блок** на:

```kotlin
    private val aDebugPanel by lazy {
        ADebugPanel(this, listOf(
            ADebugPanel.Item("ORBIT III") { btn ->
                debugOrbit3 = !debugOrbit3
                btn.label.setText(if (debugOrbit3) "O3: ON" else "ORBIT III")
                startRun()
            },
            ADebugPanel.Item("COMBO 100%") { btn ->
                // Пресет «зараховувати сусіднє кільце»: різниця кілець 130,
                // тож 145 накриває спайк на сусідній орбіті.
                debugComboWide = !debugComboWide
                engine.nearMin = if (debugComboWide) 22f else 26f
                engine.nearMax = if (debugComboWide) 145f else 90f
                btn.label.setText(if (debugComboWide) "COMBO: ON" else "COMBO 100%")
            },
            ADebugPanel.Item("TIME x0.25") { btn ->
                // Слоу-мо ВСЬОГО рушія (dt на вході). Кутова геометрія вікна
                // near-miss від цього не змінюється — лише час на реакцію ×4.
                debugTimeScale = if (debugTimeScale < 1f) 1f else 0.25f
                btn.label.setText(if (debugTimeScale < 1f) "TIME: ON" else "TIME x0.25")
            },
            ADebugPanel.Item("PAUSE") { btn ->
                // Стоїть лише м'яч: актори грають свої анімації, підкинуте дограє
                // появу, а колізій немає — шип перед м'ячем не вб'є.
                debugPaused = !debugPaused
                engine.debugFrozen = debugPaused
                btn.label.setText(if (debugPaused) "PAUSE: ON" else "PAUSE")
            },
        ))
    }

    /**
     * Ромби «підкинути»: кожен буст у своєму кольорі й зі своєю іконкою + шип.
     * Розстановку без накладань робить рушій (debugSpawnBoost / debugSpawnSpike).
     */
    private val aDebugIconBar by lazy {
        val boosts = RunEngine.Boost.entries.map { boost ->
            // PNG бустів 105×120 з полями: гліф — приблизно третина висоти
            ADebugIconBar.Item(boost.info.icon, boost.info.color, iconW = 28f, iconH = 32f) {
                engine.debugSpawnBoost(boost)
            }
        }
        val spike = ADebugIconBar.Item(gdxGame.assetsMsdf.spike, ThemeManager.current.spike, iconW = 20f, iconH = 20f) {
            engine.debugSpawnSpike()
        }
        ADebugIconBar(this, boosts + spike)
    }
```

Новий буст у `enum Boost` сам з'явиться ромбом — список будується з `entries`.

### 3.4 `addActorsOnRootConstraintLayout()`

**ЗАМІНИТИ**

```kotlin
        addDebugHud(ADebugHud(this@GameScreen))
        addDebugPanel(aDebugPanel)
```

на

```kotlin
        addDebugHud(ADebugHud(this@GameScreen))
        addDebugPanel(aDebugPanel)
        addDebugIconBar(aDebugIconBar, aDebugPanel)   // ПІСЛЯ панелі: вона — якір
```

### 3.5 `startRun()`

**ЗАМІНИТИ**

```kotlin
        engine.listener = runListener
```

на

```kotlin
        engine.listener = runListener
        applyDebugFlags()
```

### 3.6 Нова функція — перед `private val runListener`

**ДОДАТИ**:

```kotlin
    /**
     * Перемикачі дебаг-панелі живуть в екрані, а не в рушії: новий ран — новий
     * RunEngine, і без цього кнопка лишалась би «ON», а рушій — у дефолтах.
     */
    private fun applyDebugFlags() {
        engine.debugFrozen = debugPaused
        if (debugComboWide) { engine.nearMin = 22f; engine.nearMax = 145f }
    }

```

---

## Після вставки

Напиши «є» — я додам два тести рушія (пауза не дає загинути; розстановка ніколи не
накладається — вони вже пройшли в лабораторії), прожену `:engine:test` і збірку, і оновлю
в `CLAUDE.md` рядок «Перед релізом» зі старими назвами кнопок.
