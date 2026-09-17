# 42 — Шип: повільний оберт за годинниковою + удар

## Що і навіщо

Шип крутився на 180°/с проти годинникової: вісім зубців мерехтіли, і це
читалось як пилка, дрібно й не страшно. Тепер рух має два шари:

1. **Оберт за годинниковою, 40°/с** (повний оберт за 9 с). У libGDX додатний
   кут крутить **проти** годинникової, тому в коді мінус.
2. **Удар раз на 1.25 с.** Зірка **різко** стискається до 86 % (0.125 с),
   **повільно** відпускає до макета (до 0.69 с), далі пауза до кінця такту.
   У ту саму мить ореол розходиться на +35 % і гасне на 45 %: шип ніби
   «б'є» хвилею назовні.

**Чому не синус, як у `ABooster`.** Бустер рівно дихає, і це читається як
«візьми мене». Тут різкий фронт, довгий хвіст і пауза: саме пауза робить
удар помітним.

**Зірка ніколи не більша за макет.** Зона зіткнення в рушії не пульсує, тож
шип не має здаватись ширшим, ніж він б'є. Тому пульс лише зменшує.

**Усі шипи б'ються в один такт.** Такт рахується від спільного
`ShaderClock.time`, а не від лічильника в кожному акторі. Поле має один
ритм, і шип, щойно взятий з пулу, не починає такт з випадкової фази.
`ShaderClock` обнуляється кожні 100 с, тому `BEAT_PERIOD` мусить ділити
100 націло (1.25 → 80 тактів), інакше раз на 100 с удар смикнеться.
Це записано в коментарі біля константи.

Попутно оновлено шапку класу: там ще був «TEMP-АРТ… беремо гем», хоча
зірка шипа (`assetsMsdf.spike`) давно своя.

### Перевірено на пристрої (Redmi, лабораторна копія, тема TOXIC)

Дебаг-пауза, два шипи з дебаг-кнопки, серія з 14 знімків. Площа зірки в
кадрі (пікселі кольору шипа):

| | зірка | ореол |
|---|---|---|
| спокій | ≈ 3240 | ≈ 12 460 |
| пік | 2323 (**72 %**, розрахунок 0.86² = 74 %) | ≈ 14 670 (ширший) |

Зірка й ореол рухаються в протифазі, як задумано. FPS 60–61, падінь немає.
Напрям оберту зі статичних знімків не заміряти (зірка симетрична через кожні
45°), тож його я не міряв, а взяв із документації libGDX. Глянь очима.

### Що крутити, якщо не сподобається

Усе в `companion object`:

| хочеш | змінюй |
|---|---|
| важче / легше обертання | `SPIN_SPEED` (40 → 25 ще важче) |
| частіше / рідше удар | `BEAT_PERIOD` (лише дільники 100: 1.0, 1.25, 2.0, 2.5) |
| різкіший удар | `BEAT_SQUEEZE` менше (0.10 → 0.06) |
| глибший стиск | `SHRINK` (0.14 → 0.20) |
| сильніша хвиля | `GLOW_FLARE`, `GLOW_FADE` |

Якщо захочеш «серцебиття» (два удари підряд, тук-тук, потім пауза) —
це друга гілка в `beatAt()`, скажи, дороблю.

---

## `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ASpike.kt`

Змін багато, простіше **ЗАМІНИТИ ВЕСЬ ФАЙЛ**. Нижче спершу що саме змінилось,
потім файл цілком.

### Що змінилось

**Імпорти — ДОДАТИ:**
```kotlin
import com.badlogic.gdx.math.Interpolation
import com.lewydo.orbitdash.game.utils.ShaderClock
```
**ВИДАЛИТИ** (більше не використовується):
```kotlin
import com.lewydo.orbitdash.game.utils.GameColor
```

**`companion object` — було:**
```kotlin
    companion object {
        private const val GLOW_SIZE  = 100f
        private const val POINT_SIZE = 10f

        /** Швидше за гем — рух сам по собі сигналить «не чіпай». */
        private const val SPIN_SPEED = 180f
    }
```
стало — `GLOW_ALPHA`, `SPIN_SPEED = 40f`, константи такту (див. файл нижче).

**`aGlow` — було `color.a = 0.90f`, стало `color.a = GLOW_ALPHA`** (той самий
0.90; тепер ним же користується пульс).

**`act()` — було:**
```kotlin
    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        aShape.setOrigin(Align.center)
        aShape.rotation = (aShape.rotation + SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж
    }
```
стало:
```kotlin
    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        // Origin — після super.act(): розміри дітей лейаут вирішує саме там
        aShape.setOrigin(Align.center)
        aGlow.setOrigin(Align.center)

        // Мінус — за годинниковою: у libGDX додатний кут крутить проти
        aShape.rotation = (aShape.rotation - SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж

        applyBeat(beatAt(ShaderClock.time))
    }
```

**ДОДАТИ** секцію `// Beat` між `// Add Actors` і `// Theme`: функції
`beatAt()` і `applyBeat()`.

### Файл цілком

```kotlin
package com.lewydo.orbitdash.game.actors.objects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.ShaderClock
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync

// ─────────────────────────────────────────────────────────────────────────────
//  ASpike — перешкода. Той самий шаблон, що AGem: ореол під фігурою + зірка +
//  крапка в центрі, кольори з теми (theme.spike).
//
//  РУХ МАЄ ВАЖИТИ. Швидкий оберт читався як пилка — дрібно й не страшно.
//  Тепер два шари:
//    • повільний оберт за годинниковою — важка деталь, а не лезо;
//    • УДАР раз на такт: зірка різко стискається й повільно відпускає, а ореол
//      у цей момент розходиться хвилею й гасне — шип «б'є» назовні.
//  Не синус, як у ABooster: той рівно дихає і читається як «візьми мене».
//  Тут різкий фронт, довгий хвіст і пауза — пауза й робить удар помітним.
//
//  Зірка ніколи не стає БІЛЬШОЮ за макет: зона зіткнення в рушії не пульсує,
//  і візуально шип не має здаватись ширшим, ніж він б'є.
// ─────────────────────────────────────────────────────────────────────────────
class ASpike(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    companion object {
        private const val GLOW_SIZE  = 100f
        private const val GLOW_ALPHA = 0.90f
        private const val POINT_SIZE = 10f

        /** Оберт за годинниковою, °/с. Повний оберт за 9 с — важко, без мерехтіння зубців. */
        private const val SPIN_SPEED = 40f

        /**
         * Такт удару, с. Спільний на всі шипи (ShaderClock.time) — поле б'ється
         * в один ритм, а шип із пулу не починає такт із середини.
         * ShaderClock обнуляється кожні 100 с: такт мусить ділити 100 націло,
         * інакше раз на 100 с удар смикнеться.
         */
        private const val BEAT_PERIOD  = 1.25f
        /** Частки такту: стиск до SQUEEZE → відпуск до RELEASE → пауза до кінця. */
        private const val BEAT_SQUEEZE = 0.10f
        private const val BEAT_RELEASE = 0.55f

        private const val SHRINK     = 0.14f   // зірка на піку — 86 % макета
        private const val GLOW_FLARE = 0.35f   // ореол на піку — +35 %
        private const val GLOW_FADE  = 0.45f   // і гасне: хвиля пішла назовні
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlow  = Image(gdxGame.assetsMsdf.glow).apply { color.a = GLOW_ALPHA }
    private val aShape = Image(gdxGame.assetsMsdf.spike)
    private val aPoint = Image(gdxGame.assetsMsdf.circle).apply { color = Color.BLACK.cpy().apply { a = 0.40f } }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlow()
        addShape()
        addPoint()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        // Origin — після super.act(): розміри дітей лейаут вирішує саме там
        aShape.setOrigin(Align.center)
        aGlow.setOrigin(Align.center)

        // Мінус — за годинниковою: у libGDX додатний кут крутить проти
        aShape.rotation = (aShape.rotation - SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж

        applyBeat(beatAt(ShaderClock.time))
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlow() {
        add(aGlow) { size(GLOW_SIZE); center() }
    }

    private fun addShape() {
        add(aShape) { fillParent() }
        aShape.setOrigin(Align.center)
    }

    private fun addPoint() {
        add(aPoint) { size(POINT_SIZE); center() }
    }

    // ------------------------------------------------------------------------
    // Beat
    // ------------------------------------------------------------------------

    /** Сила удару в момент [time]: 0 — спокій (точно макет), 1 — пік. */
    private fun beatAt(time: Float): Float {
        val u = (time % BEAT_PERIOD) / BEAT_PERIOD
        return when {
            u < BEAT_SQUEEZE -> Interpolation.pow2Out.apply(u / BEAT_SQUEEZE)
            u < BEAT_RELEASE -> 1f - Interpolation.smooth.apply((u - BEAT_SQUEEZE) / (BEAT_RELEASE - BEAT_SQUEEZE))
            else             -> 0f
        }
    }

    /** Зірка стискається, ореол розходиться й гасне — в один і той самий момент. */
    private fun applyBeat(k: Float) {
        aShape.setScale(1f - SHRINK * k)
        aGlow.setScale(1f + GLOW_FLARE * k)
        aGlow.color.a = GLOW_ALPHA * (1f - GLOW_FADE * k)
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGlow.setColorRGB(ThemeManager.current.spike)
        aShape.setColorRGB(ThemeManager.current.spike)
    }
}
```

## Або скопіювати з лабораторії

Поки жива сесія. `ASpike.kt` у лабораторії — твій файл станом на 11:35 17.09
плюс ці зміни. Якщо ти відтоді його міняв, вставляй руками.

```bash
LAB=/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f03d26ed-12c3-428e-bcbc-fd80ce4b4d8f/scratchpad/lab
G=app/src/main/java/com/lewydo/orbitdash/game
cp "$LAB/$G/actors/objects/ASpike.kt" "$G/actors/objects/ASpike.kt"
```

(запускати з `Orbit Dash/`)
