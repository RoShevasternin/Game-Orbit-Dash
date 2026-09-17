# 46 — Туман замість ореолу під шипом

## Що і навіщо

Ореол шипа читався як яскрава пляма. Це той самий `glow`, що в м'яча й
кристала, лише тьмяніший. Причина в профілі: `glow` — це коло 40 з блюром 22.
Блюр менший за фігуру, тож посередині лишається **плато** на ~0.9, і око бачить
диск з м'яким краєм, хоч яку альфу постав.

Туман — інша текстура, а не менша альфа: **блюр більший за фігуру** (коло 40,
блюр 40). Профіль стає дзвоном без плато, пік ≈ 0.5, спад довгий, диска немає.

- `haze` — **нова** текстура в `SpriteUtil.Msdf`. Спільний `glow` не чіпаємо:
  його беруть м'яч, кристал, комета й декор.
- Розмір 156 при фреймі шипа 40 (регіон туману 120 = коло 40 + 40 з боків):
  видимий спад сягає ~55 від центру. Альфа 0.60, тож пік ≈ 0.30 (у старого
  ореолу при твоїй альфі 0.50 — ≈ 0.45 і плато).
- Удар (`FLARE 0.35`, `FADE 0.25` — твої числа) лишився, тепер розходиться туман.
- Ціна: ще один запечений `VfxTexture` (маленький буфер, один раз при старті).
  За `docs/many-actors.md` кілька таких — байдуже.

### Перевірено на пристрої

12 шипів, дебаг-пауза: навколо кожного — м'яка червона імла, диска немає.
Там, де шипи стоять щільно, тумани зливаються в смугу — це видно на знімку
«після». Якщо смуга заважає, альфу 0.60 → 0.45. Компіляція й запуск — без
помилок.

---

## 1. `app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`

`class Msdf`, одразу після `glow`:

```kotlin
        val glow_tex = VfxTexture(40f, 40f, circle_msdf, effect, listOf(BlurEffect(blur = 22f)))
        val glow     = glow_tex.region
```

**ДОДАТИ** під ним:

```kotlin

        // Туман: блюр БІЛЬШИЙ за фігуру → профіль-дзвін без плато, диска не видно,
        // пік ≈ 0.5 (у glow ≈ 0.9). Регіон = коло + 40 з боків, 120×120.
        val haze_tex = VfxTexture(40f, 40f, circle_msdf, effect, listOf(BlurEffect(blur = 40f)))
        val haze     = haze_tex.region
```

## 2. `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ASpike.kt`

### Мінімально — три рядки

`companion object`, **ЗАМІНИТИ**:
```kotlin
        private const val GLOW_SIZE  = 100f
        private const val GLOW_ALPHA = 0.50f
```
на
```kotlin
        private const val GLOW_SIZE  = 156f   // туман: регіон 120 = коло 40 + блюр 40 з боків
        private const val GLOW_ALPHA = 0.60f  // пік туману ≈ 0.5 × альфа
```

`// Actors`, **ЗАМІНИТИ**:
```kotlin
    private val aGlow  = Image(gdxGame.assetsMsdf.glow).apply { color.a = GLOW_ALPHA }
```
на
```kotlin
    private val aGlow  = Image(gdxGame.assetsMsdf.haze).apply { color.a = GLOW_ALPHA }
```

Компілюється після пунктів 1 і 2.

### Або весь файл — з перейменуванням `glow` → `haze`

Те саме, плюс назви й коментарі кажуть «туман», щоб наступного разу ніхто не
шукав тут ореол. Решта файлу — твоя (удар, оберт, `FLARE 0.35`, `FADE 0.25`).

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
//  ASpike — перешкода. Туман під фігурою + зірка + крапка в центрі, кольори з
//  теми (theme.spike). Туман, а не ореол, як у AGem: шип не світиться як
//  нагорода — навколо нього лише ледь помітна імла.
//
//  РУХ МАЄ ВАЖИТИ. Швидкий оберт читався як пилка — дрібно й не страшно.
//  Тепер два шари:
//    • повільний оберт за годинниковою — важка деталь, а не лезо;
//    • УДАР раз на такт: зірка різко стискається й повільно відпускає, а туман
//      у цей момент розходиться хвилею й рідшає — шип «б'є» назовні.
//  Не синус, як у ABooster: той рівно дихає і читається як «візьми мене».
//  Тут різкий фронт, довгий хвіст і пауза — пауза й робить удар помітним.
//
//  Зірка ніколи не стає БІЛЬШОЮ за макет: зона зіткнення в рушії не пульсує,
//  і візуально шип не має здаватись ширшим, ніж він б'є.
// ─────────────────────────────────────────────────────────────────────────────
class ASpike(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    companion object {
        /**
         * Туман під шипом (assetsMsdf.haze). Регіон 120 = коло 40 + блюр 40 з
         * боків; на 156 видимий спад сягає ~55 від центру, а пік — HAZE_ALPHA × 0.5.
         */
        private const val HAZE_SIZE  = 156f
        private const val HAZE_ALPHA = 0.60f
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
        private const val HAZE_FLARE = 0.35f   // туман на піку — +35 %
        private const val HAZE_FADE  = 0.25f   // і рідшає: хвиля пішла назовні
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aHaze  = Image(gdxGame.assetsMsdf.haze).apply { color.a = HAZE_ALPHA }
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
        addHaze()
        addShape()
        addPoint()

        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()

        // Origin — після super.act(): розміри дітей лейаут вирішує саме там
        aShape.setOrigin(Align.center)
        aHaze.setOrigin(Align.center)

        // Мінус — за годинниковою: у libGDX додатний кут крутить проти
        aShape.rotation = (aShape.rotation - SPIN_SPEED * delta) % 360f   // актор із пулу живе всю сесію — кут не росте без меж

        applyBeat(beatAt(ShaderClock.time))
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addHaze() {
        add(aHaze) { size(HAZE_SIZE); center() }
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

    /** Зірка стискається, туман розходиться й рідшає — в один і той самий момент. */
    private fun applyBeat(k: Float) {
        aShape.setScale(1f - SHRINK * k)
        aHaze.setScale(1f + HAZE_FLARE * k)
        aHaze.color.a = HAZE_ALPHA * (1f - HAZE_FADE * k)
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aHaze.setColorRGB(ThemeManager.current.spike)
        aShape.setColorRGB(ThemeManager.current.spike)
    }
}
```

## Або скопіювати з лабораторії

Поки жива сесія. Лабораторія — твій проєкт станом на 13:06 17.09 плюс ці зміни.
Якщо ти відтоді міняв ці файли — вставляй руками.

```bash
LAB=/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f03d26ed-12c3-428e-bcbc-fd80ce4b4d8f/scratchpad/lab
G=app/src/main/java/com/lewydo/orbitdash/game
cp "$LAB/$G/manager/util/SpriteUtil.kt"   "$G/manager/util/SpriteUtil.kt"
cp "$LAB/$G/actors/objects/ASpike.kt"     "$G/actors/objects/ASpike.kt"
```

(запускати з `Orbit Dash/`)

---

## Статус — НЕ застосовано (17 вересня 2026)

Вирішено лишити спільний `glow`, лише з меншою прозорістю:
`ASpike.GLOW_ALPHA = 0.60f` (було 0.90). Текстуру `haze` у `SpriteUtil` не додано.
Патч лишається як варіант, якщо пляма все-таки заважатиме.
