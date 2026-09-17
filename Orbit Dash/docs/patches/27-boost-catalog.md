# 27 — Каталог бустів: один ключ, один запис

## Що і навіщо

Про кожен буст зараз знають **шість місць**, і жодне не знає про решту:

| де | що знає |
|---|---|
| `RunEngine.Boost` (70) | сам перелік |
| `RunEngine.applyBoost` (426–430) | тривалість: MAGNET 8, FRENZY 10, SLOW 4 |
| `RunEngine.spawnBoost` (356–363) | вагу в пулі — масив із 8 елементів руками |
| `GameColor.Boost.of()` | колір |
| `ABooster.getIcon()` | іконку з атласу |
| `APanelMenu.StartBoost` | **паралельний enum** із лейблами, 4 з 5 бустів |

Шостий буст = знайти всі шість і не забути жодного. `FRENZY` уже має три імені
(`FRENZY` у коді, `boost_icon_frenzy` в атласі, `GEM x2` на екрані) — тому й потрібне одне
місце, яке каже, що це одна річ.

Після патча: `boost.info` віддає повний запис, отримувач бере що треба.

**Поділ такий самий, як у проєкті скрізь:** enum у рушії тримає **правила** (чисті числа —
libGDX у `RunEngine` не заходить), каталог у `game/utils` — **вигляд і назву для гравця**.
Один ключ, дві таблиці: баланс і Figma міняються в різні моменти й різними руками.

**Чому не `Flow`:** Flow описує значення, що **змінюються в часі**, і тягне корутинний
скоуп. Таблиця бустів статична, а актори читають її в `act()`. Реактивність мала б сенс для
*активного* буста в HUD (`magnetT` спливає) — це інша задача, і вона теж вирішується
опитуванням щокадру.

Порядок вставки: 1 → 2 → 3 → 4 → 5 → 6. Компілюється **після 6** (після 1 буде червоно
в `GameColor`, `ABooster`, `APanelMenu`).

---

## 1. `RunEngine.kt` — правила в конструктор enum

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/engine/RunEngine.kt`

### 1.1 Сам enum (рядок 70)

**ЗАМІНИТИ** рядок

```kotlin
    enum class Boost { SHIELD, MAGNET, FRENZY, SLOW, PULSE }
```

на

```kotlin
    /**
     * Буст і його правила. Тут лише чисті числа: рушій не знає ні кольорів, ні
     * іконок — вигляд і назва для гравця живуть у BoostCatalog.
     *
     *   dur        — скільки триває, секунди; 0 = миттєвий (SHIELD, PULSE)
     *   weight     — скільки разів буст стоїть у пулі спавну
     *   startOffer — чи може випасти в рекламному офері меню
     */
    enum class Boost(
        val dur       : Float,
        val weight    : Int,
        val startOffer: Boolean,
    ) {
        SHIELD( 0f, 1, true),
        MAGNET( 8f, 2, true),
        FRENZY(10f, 2, true),
        SLOW  ( 4f, 1, true),
        PULSE ( 0f, 2, false);

        companion object {
            /**
             * Пул спавну: кожен буст повторено weight разів, у порядку enum.
             * Виходить рівно той масив, що стояв руками в spawnBoost(), — тож
             * той самий seed дає ту саму послідовність. Рахується один раз,
             * а не на кожен спавн.
             */
            val SPAWN_POOL: List<Boost> = entries.flatMap { b -> List(b.weight) { b } }

            /** Що може запропонувати рекламна кнопка меню. */
            val START_OFFERS: List<Boost> = entries.filter { it.startOffer }
        }
    }
```

### 1.2 `spawnBoost()` (рядок 355)

**ЗАМІНИТИ** початок функції — від `private fun spawnBoost() {` до рядка `val ring =`:

```kotlin
    private fun spawnBoost() {
        // Зважений пул: магніт і frenzy частіші, щит рідший
        val pool = arrayOf(
            Boost.SHIELD,
            Boost.MAGNET, Boost.MAGNET,
            Boost.FRENZY, Boost.FRENZY,
            Boost.SLOW,
            Boost.PULSE,  Boost.PULSE,
        )
        val ring = rng.nextInt(ringCount)
```

на

```kotlin
    private fun spawnBoost() {
        // Зважений пул — ваги стоять у самому enum (Boost.weight)
        val pool = Boost.SPAWN_POOL
        val ring = rng.nextInt(ringCount)
```

Решта тіла (`_entities.add(Entity(… boost = pool[rng.nextInt(pool.size)] …))`) — без змін.

### 1.3 `applyBoost()`, гілка `else` (рядки 424–432)

**ЗАМІНИТИ** — увесь вираз `val dur = … * durMul` разом із рядком скидання таймерів, щоб
було видно межу:

```kotlin
            else -> {
                // Таймерні бусти взаємовиключні: новий скидає всі
                val dur = when (bt) {
                    Boost.MAGNET -> 8f
                    Boost.FRENZY -> 10f
                    Boost.SLOW   -> 4f
                    else -> 0f
                } * durMul

                magnetT = 0f; frenzyT = 0f; slowT = 0f
```

на

```kotlin
            else -> {
                // Таймерні бусти взаємовиключні: новий скидає всі
                val dur = bt.dur * durMul

                magnetT = 0f; frenzyT = 0f; slowT = 0f
```

Нижчий `when (bt) { Boost.MAGNET -> magnetT = dur … }` лишається як є: поля різні, це не
таблиця, а розкладка по полях.

---

## 2. `BoostCatalog.kt` — НОВИЙ файл

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/BoostCatalog.kt` (поруч із
`GameColor.kt`)

```kotlin
package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.engine.RunEngine.Boost

// ----------------------------------------------------------------------------
//  КАТАЛОГ БУСТІВ — ОДИН КЛЮЧ, ОДИН ЗАПИС.
//
//  RunEngine.Boost каже, ЩО це і як воно грає (dur, weight, startOffer — чисті
//  числа, рушій лишається без libGDX). Каталог каже, ЯК воно виглядає і як
//  зветься для гравця. Отримувач бере з запису те, що йому треба:
//
//      val info = boost.info
//      aHex.setColorRGB(info.color)
//      aIcon.drawable = TextureRegionDrawable(info.icon)
//
//  БУСТЕРИ НЕ В ТЕМІ, І ЦЕ НАВМИСНО. Тема фарбує ролі світу (фон, гравець,
//  здобич), і вони змінюються зі скіном. Колір бустера — не оформлення, а мова
//  гри: фіолетовий означає «магніт» так само, як червоне світло означає «стій».
//  Межа проста: якщо об'єкт упізнається за ФОРМОЮ, колір може бути темним
//  (спайк — єдина восьмипроменева зірка на полі, тому він у ThemeManager).
//  Якщо колір — ЄДИНИЙ розрізнювач, він константа. П'ять бустерів мають
//  однаковий шестикутник і різняться лише кольором та іконкою.
//
//  ІКОНКА — GETTER, НІКОЛИ НЕ ПОЛЕ. object живе довше за GDXGame: Android може
//  знищити Activity, лишивши процес, і gdxGame.assetsAll (by lazy у GDXGame)
//  стане НОВИМ об'єктом. Закешований тут TextureRegion після такого повернення
//  вказував би на атлас мертвої гри. Резолвимо при зверненні — це читання поля,
//  не пошук. Той самий клас пасток, що whiteTex у VfxTextures (патч 20).
// ----------------------------------------------------------------------------

/** Усе, що гра знає про буст. Правила читаються наскрізь із enum. */
data class BoostInfo(
    val boost: Boost,
    /** Те, що читає гравець. Ім'я в коді може бути іншим: FRENZY → «GEM x2». */
    val label: String,
    val color: Color,
) {
    /** Іконка з атласу ALL. Резолвиться при зверненні — див. шапку файлу. */
    val icon: TextureRegion get() = BoostCatalog.iconOf(boost)

    // Правила — наскрізь із enum, щоб отримувач не тримав два джерела
    val dur       : Float   get() = boost.dur
    val weight    : Int     get() = boost.weight
    val startOffer: Boolean get() = boost.startOffer

    /** Ім'я для аналітики: рівно enum, у нижньому регістрі. */
    val analytics: String get() = boost.name.lowercase()
}

object BoostCatalog {

    // Color.valueOf, а не Color.WHITE навіть для SLOW: WHITE — спільний
    // статичний об'єкт libGDX, і випадкова мутація тінту зачепила б усіх.
    private fun row(boost: Boost, label: String, hex: String) =
        boost to BoostInfo(boost, label, Color.valueOf(hex))

    private val table: Map<Boost, BoostInfo> = mapOf(
        row(Boost.SHIELD, "SHIELD",  "4DD9FF"),
        row(Boost.MAGNET, "MAGNET",  "C07BFF"),
        row(Boost.FRENZY, "GEM x2",  "FFD54A"),
        row(Boost.SLOW,   "SLOW-MO", "FFFFFF"),
        row(Boost.PULSE,  "PULSE",   "FF9F2E"),
    )

    operator fun get(boost: Boost): BoostInfo = table.getValue(boost)

    /** Єдине місце, де ім'я буста зустрічається з іменем файлу в атласі. */
    internal fun iconOf(boost: Boost): TextureRegion = with(gdxGame.assetsAll) {
        when (boost) {
            Boost.SHIELD -> boost_icon_shield
            Boost.MAGNET -> boost_icon_magnet
            Boost.FRENZY -> boost_icon_frenzy
            Boost.SLOW   -> boost_icon_slow_mo
            Boost.PULSE  -> boost_icon_pulse
        }
    }
}

/** Точка входу: boost.info.color, boost.info.icon, boost.info.label. */
val Boost.info: BoostInfo get() = BoostCatalog[this]
```

---

## 3. `GameColor.kt` — прибрати `object Boost`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/GameColor.kt`

**ВИДАЛИТИ** увесь блок від коментаря `// БУСТЕРИ — НЕ В ТЕМІ…` до закривної дужки
`object Boost` включно (рядки 15–41) — і разом із ним **рядок імпорту**
`import com.lewydo.orbitdash.engine.RunEngine`, він стає зайвим.

Файл після цього:

```kotlin
package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.graphics.Color

object GameColor {

    val background : Color = Color.valueOf("0E1024")

    val white_25 : Color = Color.WHITE.cpy().apply { a = 0.25f }
    val white_45 : Color = Color.WHITE.cpy().apply { a = 0.45f }
    val white_65 : Color = Color.WHITE.cpy().apply { a = 0.65f }
    val white_90 : Color = Color.WHITE.cpy().apply { a = 0.90f }
}
```

---

## 4. `ABooster.kt` — один запит замість двох

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ABooster.kt`

### 4.1 Імпорти

**ЗАМІНИТИ** рядок

```kotlin
import com.lewydo.orbitdash.game.utils.GameColor
```

на

```kotlin
import com.lewydo.orbitdash.game.utils.info
```

### 4.2 Іконка за замовчуванням (рядок 36)

**ЗАМІНИТИ**

```kotlin
    private val aIcon  = Image(gdxGame.assetsAll.boost_icon_magnet)
```

на

```kotlin
    private val aIcon  = Image(RunEngine.Boost.MAGNET.info.icon)
```

### 4.3 Сеттер `boost` (рядки 44–51)

**ЗАМІНИТИ**

```kotlin
    var boost: RunEngine.Boost = RunEngine.Boost.MAGNET
        set(value) {
            field = value

            applyColor()
            applyIcon()
        }
```

на

```kotlin
    var boost: RunEngine.Boost = RunEngine.Boost.MAGNET
        set(value) {
            field = value

            applyInfo()
        }
```

### 4.4 `addActorsOnGroup()` (рядки 56–63)

**ЗАМІНИТИ**

```kotlin
        applyColor()
        applyIcon()
    }
```

на

```kotlin
        applyInfo()
    }
```

### 4.5 Блоки `Apply` і `Helper`

**ЗАМІНИТИ** усе від `// Apply` до кінця класу (рядки 88–116, тобто `applyColor()`,
`applyIcon()`, `getIcon()` разом із заголовком `// Helper`):

```kotlin
    // ------------------------------------------------------------------------
    // Apply
    // ------------------------------------------------------------------------

    private fun applyColor() {
        val c = GameColor.Boost.of(boost)
        aGlow.setColorRGB(c)
        aHex.setColorRGB(c)
        aIcon.setColorRGB(c)
    }

    private fun applyIcon() {
        aIcon.drawable = TextureRegionDrawable(getIcon())
    }

    // ------------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------------
    private fun getIcon() = when (boost) {
        RunEngine.Boost.SHIELD -> gdxGame.assetsAll.boost_icon_shield
        RunEngine.Boost.MAGNET -> gdxGame.assetsAll.boost_icon_magnet
        RunEngine.Boost.FRENZY -> gdxGame.assetsAll.boost_icon_frenzy
        RunEngine.Boost.SLOW   -> gdxGame.assetsAll.boost_icon_slow_mo
        RunEngine.Boost.PULSE  -> gdxGame.assetsAll.boost_icon_pulse
    }

}
```

на

```kotlin
    // ------------------------------------------------------------------------
    // Apply
    // ------------------------------------------------------------------------

    /** Один запит до каталогу: колір і іконка приходять разом, одним записом. */
    private fun applyInfo() {
        val info = boost.info

        aGlow.setColorRGB(info.color)
        aHex.setColorRGB(info.color)
        aIcon.setColorRGB(info.color)

        aIcon.drawable = TextureRegionDrawable(info.icon)
    }

}
```

> Імпорт `gdxGame` у файлі лишається — його тримають `aGlow` і `aHex` (`assetsMsdf`).

---

## 5. `AShieldPip.kt` — колір щита з каталогу

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/panel/boost/AShieldPip.kt`

### 5.1 Імпорти (рядок 7)

**ЗАМІНИТИ**

```kotlin
import com.lewydo.orbitdash.game.utils.GameColor
```

на

```kotlin
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.utils.info
```

### 5.2 `applyState()`, гілка `State.FULL` (рядки 106–112)

**ЗАМІНИТИ**

```kotlin
            State.FULL -> {
                aPipImg.setColorRGB(GameColor.Boost.shield)
                aPipImg.color.a = 1f
                aGlowImg.setColorRGB(GameColor.Boost.shield)
                aGlowImg.color.a = GLOW_ALPHA
            }
```

на

```kotlin
            State.FULL -> {
                val c = RunEngine.Boost.SHIELD.info.color

                aPipImg.setColorRGB(c)
                aPipImg.color.a = 1f
                aGlowImg.setColorRGB(c)
                aGlowImg.color.a = GLOW_ALPHA
            }
```

---

## 6. `APanelMenu.kt` — прибрати паралельний enum

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/panel/APanelMenu.kt`

Панель більше не має власного словника бустів: вона пропонує **той самий тип**, який чекає
`RunEngine.Config.startBoost`. Мапінг `StartBoost → Boost`, який довелося б написати при
підключенні стартового буста, зникає ще до того, як з'явився.

### 6.1 Імпорти

**ДОДАТИ** до наявних:

```kotlin
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.utils.info
```

### 6.2 `enum class StartBoost` (рядки 39–47)

**ВИДАЛИТИ** цілком:

```kotlin
    /** Стартові бусти, які може запропонувати рекламна кнопка. */
    enum class StartBoost(val label: String) {
        MAGNET("MAGNET"),
        SHIELD("SHIELD"),
        FRENZY("GEM x2"),
        SLOW  ("SLOW-MO");

        companion object { fun random() = entries.random() }
    }
```

Блок `companion object { GEMS_MIN_STEP … }` під ним лишається без змін.

### 6.3 Стан (рядок 87)

**ЗАМІНИТИ**

```kotlin
    var rolledBoost  = StartBoost.random(); private set
```

на

```kotlin
    var rolledBoost  = RunEngine.Boost.START_OFFERS.random(); private set
```

### 6.4 Колбек (рядок 94)

**ЗАМІНИТИ**

```kotlin
    var onPlayBoost : (StartBoost) -> Unit = {}
```

на

```kotlin
    var onPlayBoost : (RunEngine.Boost) -> Unit = {}
```

### 6.5 `rerollBoost()` (рядок 156)

**ЗАМІНИТИ**

```kotlin
        rolledBoost = StartBoost.random()
```

на

```kotlin
        rolledBoost = RunEngine.Boost.START_OFFERS.random()
```

### 6.6 `applyOfferTexts()` (рядок 214)

**ЗАМІНИТИ**

```kotlin
        aBoostBtn.label.setText("PLAY + ${rolledBoost.label} · AD")
```

на

```kotlin
        aBoostBtn.label.setText("PLAY + ${rolledBoost.info.label} · AD")
```

`MenuScreen.wirePanelMenu()` міняти **не треба**: тип параметра лямбди виводиться сам, а
`log("PLAY with start boost: $boost")` друкує те саме ім'я, що й раніше. Коли startBoost
поїде в ран, там буде `Config(startBoost = boost)` — без жодного мапінгу між двома enum.

---

## Чого я свідомо не зробив

- **Аналітику не чіпав.** `AnalyticsManager.Boost.NONE` і `runStart()` у `GameScreen.kt:243`
  лишаються як є: стартовий буст ще не доїжджає до рану. Коли доїде —
  `runStart(boost?.info?.analytics ?: NONE)`, поле в записі вже готове.
- **`GameColor` не розганяв далі.** `background` і `white_*` — це палітра UI, вона не про
  бусти й переїжджати їй нікуди.
- **Кеш `BoostInfo` не робив «розумним».** `table` створюється при першому зверненні до
  `BoostCatalog` і тримає лише `Color` — GL-ресурсів у ньому немає, тож `contextGeneration`
  йому не потрібен. Усе, що вміє протухнути (іконка), — getter.

## Перевірка

```bash
export PATH="$PATH:/Users/admin/Library/Android/sdk/platform-tools"
sh gradlew assembleDebug --console=plain -q
adb install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk   # чекати Success
adb shell am force-stop com.lewydo.orbitdash
adb shell monkey -p com.lewydo.orbitdash -c android.intent.category.LAUNCHER 1
adb shell sleep 9; adb shell input tap 540 1500    # ОДИН тап
adb shell cmd statusbar collapse
adb shell sleep 5; adb exec-out screencap -p > shot.png
```

1. бустери на полі — колір і іконка ті самі, що були;
2. кнопка меню крутить чотири офери, `FRENZY` показує **`GEM x2`**, `PULSE` не випадає;
3. дебаг `+SHIELD` дає щит, капсули блакитні;
4. тривалості: магніт ~8 с, frenzy ~10 с, slow ~4 с (по `log("BOOST $boost")`).

Детермінізм спавну перевіряти не треба **логікою**, але варто **очима**: `SPAWN_POOL` у
порядку enum дає `[SHIELD, MAGNET, MAGNET, FRENZY, FRENZY, SLOW, PULSE, PULSE]` — рівно той
масив, що був у `spawnBoost()`. Якщо колись переставиш константи в enum місцями, послідовність
на тому самому seed зміниться.
