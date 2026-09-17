# 28 — `ADebugHud` і `ADebugPanel` тягаються пальцем

## Що і навіщо

Обидва оверлеї прибиті до кутів константами в хелперах (`endToEnd(10) / bottomToBottom(10)`,
`startToStart(14) / topToTop(14)`) і затуляють те, що саме зараз треба бачити. Хочеться
взяти пальцем і відсунути — на будь-якому екрані, без перезбірки.

Три речі, які треба розв'язати, інакше «просто `setPosition` у драгу» не працює:

**1. Лейаут поверне актора назад.** Позицію тримає `AConstraintLayout`: він переприкладає
констрейнти на `sizeChanged()`, на зміну розміру дитини (`checkChildren()`) і в `layout()`.
Для HUD це не теорія: `PerfMonitor` раз на секунду міняє текст, `autoSize` пакує напис,
розмір дитини змінюється — і напис стрибає назад у куток просто посеред перетягу.
Лікується наявним API: **`AConstraintLayout.detach(actor)`** — знімає з керування, лишаючи
дитиною. Викликаємо на початку першого драгу (і при відновленні збереженої позиції).

**2. Кнопка під пальцем відпрацює клік.** `AButtonBase` скасовує клік, якщо палець проїхав
понад `scrollThreshold = 10`. Але палець стоїть **нерухомо відносно кнопки** — їде вся
панель разом із ним, локальні координати не міняються, `isDragged` лишається `false`, і на
відпусканні прилітає `RESTART`. Правильний механізм у scene2d для цього вже є —
`stage.cancelTouchFocusExcept(...)`: він шле слухачам `touchUp` із прапорцем
`isTouchFocusCancel`. `AButtonBase` цей прапорець зараз ігнорує — одна перевірка в `touchUp`
(блок 2) лагодить і цей випадок, і будь-який майбутній скрол/драг над кнопками.

**3. `ADebugHud` не приймає дотиків узагалі** — `disable()` у `addActorsOnGroup()`, а сам
він `fillParent()`. Міняємо на `Touchable.childrenOnly`: група лишається наскрізною (вона
на весь екран, інакше з'їдала б увесь ввід), а дотики ловить **лише прямокутник напису**.

Плюс дрібниця, яка робить це справді зручним: позиція **зберігається у `Preferences`** під
ключем «екран + оверлей». Один раз розсунув — і після `install -r` воно там само, і на
кожному екрані своє місце.

Порядок вставки: 1 → 2 → 3 → 4. Компілюється після 1 (решта — незалежні правки).

---

## 1. `DragToMove.kt` — НОВИЙ файл

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/debug/DragToMove.kt`
(поруч із `PerfMonitor.kt` — це службовий інструмент, не елемент гри)

```kotlin
package com.lewydo.orbitdash.game.utils.debug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import kotlin.math.max

// ═════════════════════════════════════════════════════════════════════════════
//  ПЕРЕТЯГ СЛУЖБОВИХ ОВЕРЛЕЇВ — щоб дебаг не затуляв те, що дивишся.
//
//    aStatsLbl.dragToMove("${screen::class.simpleName}.hud")
//
//  ТРИ РЕЧІ, ЯКІ ТУТ НЕОЧЕВИДНІ:
//
//  1. ПЕРЕД ПЕРШИМ ЗСУВОМ АКТОРА ЗНІМАЄМО З ЛЕЙАУТУ (detach). Інакше найближчий
//     resolve поверне його на місце: констрейнти переприкладаються на зміну
//     розміру групи І на зміну розміру дитини. HUD міняє текст раз на секунду —
//     напис стрибав би назад у куток прямо під пальцем.
//
//  2. НА ПОЧАТКУ ДРАГУ ЗАБИРАЄМО ТАЧ У РЕШТИ СЛУХАЧІВ (cancelTouchFocusExcept).
//     Палець стоїть нерухомо ВІДНОСНО кнопки — панель їде разом із ним, і
//     власний поріг кнопки (scrollThreshold) не спрацьовує. Без цього кожен
//     перетяг закінчувався б натисканням кнопки, за яку тягнеш.
//
//  3. ЗСУВ РАХУЄМО ВІД ТОЧКИ ЗАХОПЛЕННЯ (x - dragStartX), а не від попереднього
//     кадру. Актор їде за пальцем, тож його локальні координати після кожного
//     moveBy повертаються до точки захоплення — дельта виходить чиста, без
//     накопичення похибки.
//
//  Позиція лежить у Preferences під ключем «екран + оверлей»: пережила
//  install -r — і оверлей там, де ти його лишив.
// ═════════════════════════════════════════════════════════════════════════════

/** Поріг початку драгу, дизайн-одиниці: менше — тягнеться від будь-якого дотику. */
private const val DRAG_START = 6f

/** Прозорість під час перетягу — видно, що актор «у руці». */
private const val DRAG_ALPHA = 0.55f

/**
 * Дозволяє тягати актора пальцем у межах його батька.
 * [key] — під ним позиція лягає в Preferences; має бути унікальним на екран.
 */
fun Actor.dragToMove(key: String) {
    addListener(DragMoveListener(this, key))

    val saved = DebugLayout.load(key) ?: return

    // Відновлюємо на першому act(): на момент add() панель ще HUG-нульова,
    // а батько може не мати розміру — clamp зіпсував би позицію.
    addAction(Actions.run {
        detachFromLayout()
        setPosition(saved.x, saved.y)
        clampInsideParent()
    })
}

private class DragMoveListener(
    private val target: Actor,
    private val key   : String,
) : DragListener() {

    private var savedAlpha = 1f

    init { tapSquareSize = DRAG_START }

    override fun dragStart(event: InputEvent, x: Float, y: Float, pointer: Int) {
        target.detachFromLayout()
        event.stage?.cancelTouchFocusExcept(this, event.listenerActor)

        savedAlpha = target.color.a
        target.color.a = DRAG_ALPHA
    }

    override fun drag(event: InputEvent, x: Float, y: Float, pointer: Int) {
        target.moveBy(x - dragStartX, y - dragStartY)
        target.clampInsideParent()
    }

    override fun dragStop(event: InputEvent, x: Float, y: Float, pointer: Int) {
        target.color.a = savedAlpha
        DebugLayout.save(key, target.x, target.y)
    }
}

/** Зняти з керування констрейнтами, лишивши дитиною групи. */
private fun Actor.detachFromLayout() {
    (parent as? AConstraintLayout)?.detach(this)
}

/** Не даємо заїхати за край: оверлей завжди лишається повністю на екрані. */
private fun Actor.clampInsideParent() {
    val p = parent ?: return
    if (p.width <= 0f || p.height <= 0f) return

    x = x.coerceIn(0f, max(0f, p.width  - width))
    y = y.coerceIn(0f, max(0f, p.height - height))
}

/**
 * Де лежать оверлеї. Окремий файл Preferences — щоб службові позиції ніколи
 * не змішувались із даними гравця.
 *
 * Preferences НЕ кешуємо в полі: об'єкт прив'язаний до Gdx.app, а Android може
 * перестворити Activity, лишивши процес живим. Gdx.app.getPreferences() і так
 * віддає той самий інстанс — кеш тут не економить, лише протухає.
 */
object DebugLayout {

    private const val FILE = "orbit_dash_debug_layout"

    private val prefs get() = Gdx.app.getPreferences(FILE)

    fun save(key: String, x: Float, y: Float) = prefs.run {
        putFloat("$key.x", x)
        putFloat("$key.y", y)
        flush()
    }

    fun load(key: String): Vector2? = prefs.run {
        if (!contains("$key.x")) null
        else Vector2(getFloat("$key.x"), getFloat("$key.y"))
    }

    /** Скинути все — оверлеї повернуться на місця з хелперів. */
    fun reset() = prefs.run { clear(); flush() }
}
```

---

## 2. `AButtonBase.kt` — шанувати скасування тач-фокуса

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/button/base/AButtonBase.kt`

**ЗАМІНИТИ** `touchUp` у `buildListener()` — увесь метод цілком:

```kotlin
        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            onTouchUp(x, y)
            unpress()
            if (!isDragged) {
                clickSound?.let { gdxGame.soundUtil.play(it) }
                onClickBlock()
            }
        }
```

на

```kotlin
        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            onTouchUp(x, y)
            unpress()

            // Тач у нас забрали (хтось почав перетяг і викликав cancelTouchFocus) —
            // це не клік. Власного порогу тут замало: коли їде вся панель, палець
            // стоїть НЕРУХОМО відносно кнопки й isDragged лишається false.
            if (isDragged || event?.isTouchFocusCancel == true) return

            clickSound?.let { gdxGame.soundUtil.play(it) }
            onClickBlock()
        }
```

Це правка загального призначення, не дебажна: слухач зобов'язаний шанувати скасування
тач-фокуса — так само поводитиметься будь-який майбутній скрол чи драг над кнопками.

---

## 3. `ADebugPanel.kt` — увімкнути перетяг у хелпері

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/debug/ADebugPanel.kt`

### 3.1 Імпорт

**ДОДАТИ**:

```kotlin
import com.lewydo.orbitdash.game.utils.debug.dragToMove
```

### 3.2 Хелпер у кінці файлу

**ЗАМІНИТИ**

```kotlin
/** Додає панель у правий низ екрана. У релізі — нуль вартості. */
fun AConstraintLayout.addDebugPanel(panel: ADebugPanel) {
    if (!IS_DEBUG) return
    panel.setSize(ADebugPanel.DEFAULT_WIDTH, 1f)   // ширина фіксована, висота HUG
    add(panel) { endToEnd(margin = 10f); bottomToBottom(margin = 10f) }
}
```

на

```kotlin
/**
 * Додає панель у правий низ екрана. У релізі — нуль вартості.
 * Панель тягається пальцем: позиція лежить у Preferences, окремо на кожен екран.
 */
fun AConstraintLayout.addDebugPanel(panel: ADebugPanel) {
    if (!IS_DEBUG) return
    panel.setSize(ADebugPanel.DEFAULT_WIDTH, 1f)   // ширина фіксована, висота HUG
    add(panel) { endToEnd(margin = 10f); bottomToBottom(margin = 10f) }

    panel.dragToMove("${panel.screen::class.simpleName}.panel")
}
```

Тягнути можна за будь-яке місце панелі, кнопки включно: подія спливає від кнопки до
групи, а клік під час перетягу гасить блок 2.

---

## 4. `ADebugHud.kt` — тягнемо сам напис

Група `fillParent()` на весь екран, тож тягти її не можна — тягнемо єдине, що видно.

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/debug/ADebugHud.kt`

### 4.1 Імпорти

**ЗАМІНИТИ**

```kotlin
import com.lewydo.orbitdash.game.utils.actor.disable
```

на

```kotlin
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.lewydo.orbitdash.game.utils.debug.dragToMove
```

### 4.2 `addActorsOnGroup()`

**ЗАМІНИТИ** — увесь метод:

```kotlin
    override fun addActorsOnGroup() {
        disable()

        if (!IS_DEBUG) { isVisible = false; return }

        PerfMonitor.enable(hudInterval = 1f, log = false)

        //aStatsLbl.debug()
        //aStatsLbl.wrap = true
        add(aStatsLbl) { startToStart(margin = 14f); topToTop(margin = 14f) }
    }
```

на

```kotlin
    override fun addActorsOnGroup() {
        // childrenOnly, а не disable(): група на весь екран і мусить лишатись
        // наскрізною, але сам напис має ловити дотик — за нього ми й тягнемо.
        touchable = Touchable.childrenOnly

        if (!IS_DEBUG) { isVisible = false; return }

        PerfMonitor.enable(hudInterval = 1f, log = false)

        //aStatsLbl.debug()
        //aStatsLbl.wrap = true
        add(aStatsLbl) { startToStart(margin = 14f); topToTop(margin = 14f) }

        aStatsLbl.touchable = Touchable.enabled
        aStatsLbl.dragToMove("${screen::class.simpleName}.hud")
    }
```

`aStatsLbl.touchable` ставимо явно: `autoSize` пакує напис під текст, тож область дотику —
рівно прямокутник цифр, а не куток екрана.

---

## Чого я свідомо не робив

- **Не додавав окрему кнопку-ручку.** Ти просив тягти «прямо на них», і після блоку 2 це
  безпечно: перетяг за кнопку кліку не дає.
- **Не робив перетяг загальною здатністю акторів** (`utils/actor/`): `DragToMove`
  зберігає позиції в дебажні Preferences і живе поруч із `PerfMonitor`. Знадобиться в
  ігровому UI — тоді й переїде, розділивши перетяг і сховище.
- **Не чіпав `ADebugPanel.DEFAULT_WIDTH` і початкові кутки** — вони лишаються стартовою
  позицією, доки нічого не збережено.
- **Не додавав скидання позицій у панель.** Є `DebugLayout.reset()` — якщо колись
  знадобиться, це один `ADebugPanel.Item("RESET HUD") { DebugLayout.reset() }`.

## Перевірка

Збірка + пристрій (клац по кнопці дебаг-панелі має працювати як раніше, перетяг —
не викликати дії):

```bash
export PATH="$PATH:/Users/admin/Library/Android/sdk/platform-tools"
sh gradlew assembleDebug --console=plain -q
adb install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk   # чекати Success
adb shell am force-stop com.lewydo.orbitdash
adb shell monkey -p com.lewydo.orbitdash -c android.intent.category.LAUNCHER 1
```

1. коротко тапнути `RESTART` — ран перезапускається (клік живий);
2. потягнути за `RESTART` — їде вся панель, ран **не** перезапускається;
3. відпустити, вбити застосунок, запустити знову — панель там, де лишив;
4. потягнути напис FPS у центр — цифри оновлюються раз на секунду й **не** стрибають
   назад у куток (це перевірка `detach`);
5. поза панеллю й написом гра приймає тапи як завжди.
