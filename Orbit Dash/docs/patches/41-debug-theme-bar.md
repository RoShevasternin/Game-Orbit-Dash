# 41 — Дебаг: кружечки тем на меню

## Що і навіщо

На `MenuScreen` у правому верхньому куті з'являється стовпчик із 8 кружечків,
по одному на палітру з `ThemeManager.palettes`. Тап кличе
`ThemeManager.switchTo(id)`, тобто той самий плавний перехід за `TRANSITION_TIME`,
що й при виборі скіна. Вибрана тема — у білому кільці.

- **Кружечок = заливка `player` + крапка `gem` у центрі.** За одним кольором
  схожі теми не розрізнити, а дві ролі палітри — одразу.
- **Вибір НЕ зберігається.** Кличемо лише `ThemeManager`, без `modelPlayer`, тож
  платні скіни не відкриваються, а після перезапуску тема знову та, що в
  збереженні. Побічний ефект, лише в дебазі: якщо після такого перемикання
  вибрати в шопі **той самий** скін, що збережений, тема не повернеться, бо
  `skinIdFlow` не видасть однакове значення вдруге.
- **Тягається пальцем**, як решта дебаг-оверлеїв (`dragToMove`, ключ
  `MenuScreen.swatchBar`).
- **У релізі — нічого.** `IS_DEBUG` перевіряється в хелпері й в `addActorsOnGroup()`.

### Пакет нового файлу

`game/actors/debug/`, поруч з `ADebugIconBar` / `ADebugPanel`. Це та сама
родина: стовпчик «вигляд → дія», який **нічого не знає про гру**. Кольори,
ознаку «вибрано» й дію дає екран, тому клас не імпортує `ThemeManager`.
Імпортує лише layout, `dragToMove` і `IS_DEBUG`, а його імпортує `MenuScreen`.

### Перевірено на пристрої (Redmi, лабораторна копія)

- Стовпчик стоїть праворуч угорі й не налазить на HUD, емблему чи титули.
  Активна NEON — у кільці.
- Тап SUNSET: через 0.4 с проміжні кольори (кнопка PLAY зеленувато-сіра між
  бірюзовим і помаранчевим), через ~3 с — чиста SUNSET. Кільце переходить одразу.
  61 FPS під час переходу.
- VOID, а через 0.5 с GOLD (перемикання посеред переходу): доходить до GOLD
  плавно, без стрибка.
- Падінь немає.

---

## 1. НОВИЙ файл `app/src/main/java/com/lewydo/orbitdash/game/actors/debug/ADebugSwatchBar.kt`

```kotlin
package com.lewydo.orbitdash.game.actors.debug

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.actor.setOnClickListener
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.debug.dragToMove
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG

// ═════════════════════════════════════════════════════════════════════════════
//  ADebugSwatchBar — стовпчик кольорових кружечків «колір → дія».
//
//  Як ADebugPanel / ADebugIconBar, НІЧОГО не знає про гру: кольори, ознаку
//  «вибрано» і дію дає екран. На MenuScreen це теми — тап перемикає палітру
//  через ThemeManager.switchTo(), тож видно той самий плавний перехід, що й
//  при виборі скіна.
//
//    private val aDebugThemeBar by lazy {
//        ADebugSwatchBar(this, ThemeManager.palettes.mapIndexed { id, p ->
//            ADebugSwatchBar.Item(p.player, p.gem, isActive = { ThemeManager.currentId == id }) {
//                ThemeManager.switchTo(id)
//            }
//        })
//    }
//
//    addDebugSwatchBar(aDebugThemeBar)
//
//  Кружечок — заливка main + крапка accent у центрі: дві ролі палітри, бо за
//  одним кольором схожі теми (ICE / GOLD) не розрізнити. Вибраний — у білому
//  кільці: під заливкою лежить більший білий круг, видно лише його край.
//
//  Клік — setOnClickListener(stopEvent = false), як в ADebugIconBar: подія
//  мусить спливти до стовпчика, інакше перетяг не почнеться з кружечка.
// ═════════════════════════════════════════════════════════════════════════════
class ADebugSwatchBar(
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

    /** Один кружечок. [isActive] питається щокадру — екран сам знає, що вибрано. */
    class Item(
        val main    : Color,
        val accent  : Color,
        val isActive: () -> Boolean,
        val onClick : () -> Unit,
    )

    companion object {
        /** Діаметр. 28 юнітів ≈ 84 px на 1080 — вісім штук не з'їдають екран. */
        const val CELL = 28f

        private const val RING = 2.5f   // товщина білого кільця вибраного
        private const val DOT  = 9f     // крапка accent
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        if (!IS_DEBUG) { isVisible = false; return }

        items.forEach { item ->
            val cell = ACell(screen, item)
            cell.setSize(CELL, CELL)
            add(cell)
            cell.setOnClickListener(stopEvent = false) { item.onClick() }
        }
    }

    // ------------------------------------------------------------------------
    // Cell
    // ------------------------------------------------------------------------
    /** Біле кільце (лише вибраний) → заливка main → крапка accent. */
    private class ACell(
        override val screen: AdvancedScreen,
        private val item: Item,
    ) : AConstraintLayout(screen) {

        private val aRing = Image(gdxGame.assetsMsdf.circle)
        private val aFill = Image(gdxGame.assetsMsdf.circle)
        private val aDot  = Image(gdxGame.assetsMsdf.circle)

        override fun addActorsOnGroup() {
            add(aRing) { fillParent() }

            aFill.setColorRGB(item.main)
            add(aFill) { size(CELL - 2f * RING); center() }

            aDot.setColorRGB(item.accent)
            add(aDot) { size(DOT); center() }
        }

        override fun act(delta: Float) {
            super.act(delta)
            aRing.isVisible = item.isActive()
        }
    }
}

// ----------------------------------------------------------------------------
// Helper
// ----------------------------------------------------------------------------
/**
 * Ставить стовпчик у правий верхній кут — там на меню вільно (HUD зліва,
 * емблема й титули по центру). Тягається пальцем, позиція — у Preferences.
 */
fun AConstraintLayout.addDebugSwatchBar(bar: ADebugSwatchBar) {
    if (!IS_DEBUG) return
    bar.setSize(1f, 1f)   // HUG по обох осях; нульовий розмір add() не пропустить
    add(bar) { endToEnd(margin = 10f); topToTop(margin = 10f) }

    bar.dragToMove("${bar.screen::class.simpleName}.swatchBar")
}
```

---

## 2. `app/src/main/java/com/lewydo/orbitdash/game/screens/MenuScreen.kt`

### 2.1 Імпорти — ДОДАТИ три рядки

Після `import …debug.ADebugHud`:
```kotlin
import com.lewydo.orbitdash.game.actors.debug.ADebugSwatchBar
```

Після `import …debug.addDebugHud`:
```kotlin
import com.lewydo.orbitdash.game.actors.debug.addDebugSwatchBar
```

Після `import com.lewydo.orbitdash.game.utils.runGDX`:
```kotlin
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
```

### 2.2 Секція `// Debug` — ДОДАТИ між `isFromLoader` і `// Lifecycle`

Так само, як у `GameScreen`: секція `Debug` стоїть одразу перед `Lifecycle`.

```kotlin
    private val isFromLoader
        get() = gdxGame.navigationManager.fromScreenName == LoaderScreen::class.java.name

    // ------------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------------
    /**
     * Кружечки тем: тап — той самий плавний перехід, що й при виборі скіна.
     * Лише ThemeManager, без modelPlayer: вибір НЕ зберігається і не відкриває
     * платних скінів — після перезапуску тема знову та, що в збереженні.
     */
    private val aDebugThemeBar by lazy {
        ADebugSwatchBar(this, ThemeManager.palettes.mapIndexed { id, palette ->
            ADebugSwatchBar.Item(palette.player, palette.gem, isActive = { ThemeManager.currentId == id }) {
                ThemeManager.switchTo(id)
            }
        })
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
```

(перші два й останні три рядки вже є у файлі — вони орієнтир, вставляється те, що між ними)

### 2.3 `addActorsOnRootConstraintLayout()` — ДОДАТИ рядок після HUD

```kotlin
        addDebugHud(ADebugHud(this@MenuScreen))
        addDebugSwatchBar(aDebugThemeBar)
    }
```

Компілюється після пунктів 1 і 2 разом.

---

## Або скопіювати з лабораторії

Поки жива сесія. `MenuScreen.kt` у лабораторії — це твій файл станом на 11:35
17.09 плюс ці зміни. Якщо ти відтоді його міняв, вставляй руками.

```bash
LAB=/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f03d26ed-12c3-428e-bcbc-fd80ce4b4d8f/scratchpad/lab
G=app/src/main/java/com/lewydo/orbitdash/game
cp "$LAB/$G/actors/debug/ADebugSwatchBar.kt" "$G/actors/debug/ADebugSwatchBar.kt"
cp "$LAB/$G/screens/MenuScreen.kt"           "$G/screens/MenuScreen.kt"
```

(запускати з `Orbit Dash/`)
