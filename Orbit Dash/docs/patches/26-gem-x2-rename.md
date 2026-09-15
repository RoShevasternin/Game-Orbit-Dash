# 26 — `FRENZY` → `GEM_X2` і мапа «буст → вид» в одному місці

## Що і навіщо

Зараз одна річ має **три імені**:

| де | як називається |
|---|---|
| рушій | `RunEngine.Boost.FRENZY`, `frenzyT` |
| ассет | `boost_icon_gem_x2` |
| гравець бачить | `"GEM x2"` (`APanelMenu.StartBoost`) |

Це не косметика: словник має бути один від Figma через ассети до коду, інакше кожна
наступна людина (і кожна наступна сесія) витрачає час на звірку, що `FRENZY` — це той,
у якого іконка `gem_x2`.

**Чому `GEM_X2`, а не `X2`:** `X2` не каже, чого саме подвоєння. У грі з рекламою майже
напевно з'явиться «x2 монет за рекламу» або «x2 очок», і `X2` з ними зіткнеться.

**Чесне застереження проти перейменування** (єдине): `FRENZY` називає **весь** механізм —
спавн ×1.5, 85 % гемів замість шипів, цінність ×2. `GEM_X2` називає лише те, що гравець
читає на іконці. Якщо колись механіка виросте за межі гемів, ім'я стане напівправдою.
Я вважаю цей ризик меншим за три імені на одну річ — тим паче, що іконка й підпис
уже зафіксовані як «GEM x2».

Порядок вставки: 1 → 2 → 3 → 4. Компілюється після 4 (після 1 буде червоно в трьох файлах).

---

## 1. `RunEngine.kt` — перейменувати

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/engine/RunEngine.kt`

Заміни точково (рядки для орієнтиру):

| рядок | було | стане |
|---|---|---|
| 70 | `enum class Boost { SHIELD, MAGNET, FRENZY, SLOW, PULSE }` | `enum class Boost { SHIELD, MAGNET, GEM_X2, SLOW, PULSE }` |
| 192 | `var frenzyT = 0f;       private set` | `var gemX2T = 0f;        private set` |
| 296 | `frenzyT   = max(0f, frenzyT - dt)` | `gemX2T    = max(0f, gemX2T - dt)` |
| 319 | `// … (складність), frenzy пришвидшує` | `// … (складність), GEM x2 пришвидшує` |
| 320 | `spawnT -= wdt * (if (frenzyT > 0f) 1.5f else 1f)` | `spawnT -= wdt * (if (gemX2T > 0f) 1.5f else 1f)` |
| 343 | `… (if (frenzyT > 0f) rng.nextFloat() < 0.85f …` | `… (if (gemX2T > 0f) rng.nextFloat() < 0.85f …` |
| 356 | `// Зважений пул: магніт і frenzy частіші, щит рідший` | `// Зважений пул: магніт і GEM x2 частіші, щит рідший` |
| 360 | `Boost.FRENZY, Boost.FRENZY,` | `Boost.GEM_X2, Boost.GEM_X2,` |
| 428 | `Boost.FRENZY -> 10f` | `Boost.GEM_X2 -> 10f` |
| 433 | `magnetT = 0f; frenzyT = 0f; slowT = 0f` | `magnetT = 0f; gemX2T = 0f; slowT = 0f` |
| 436 | `Boost.FRENZY -> frenzyT = dur` | `Boost.GEM_X2 -> gemX2T = dur` |
| 506 | `(if (frenzyT > 0f) 2f else 1f) *` | `(if (gemX2T > 0f) 2f else 1f) *` |

Одним рядком у терміналі те саме:

```bash
cd "/Users/admin/Apps/Game Orbit Dash/Orbit Dash/app/src/main/java/com/lewydo/orbitdash"
sed -i '' -e 's/Boost\.FRENZY/Boost.GEM_X2/g' -e 's/\bFRENZY\b/GEM_X2/g' \
          -e 's/\bfrenzyT\b/gemX2T/g' -e 's/\bfrenzy\b/GEM x2/g' \
          game/engine/RunEngine.kt
```

---

## 2. `GameColor.kt` — перейменувати колір

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/GameColor.kt`

Було (рядки 30 і 37):

```kotlin
        val frenzy : Color = Color.valueOf("FFD54A")
...
            RunEngine.Boost.FRENZY -> frenzy
```

Стане:

```kotlin
        val gemX2  : Color = Color.valueOf("FFD54A")
...
            RunEngine.Boost.GEM_X2 -> gemX2
```

---

## 3. `SpriteUtil.kt` — іконка буста поруч із ассетами, а не в акторі

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`

ДОДАТИ до імпортів:

```kotlin
import com.lewydo.orbitdash.game.engine.RunEngine
```

ДОДАТИ у `class All`, одразу після п'яти `boost_icon_*`:

```kotlin
        /**
         * Іконка буста за типом. Дзеркалить GameColor.Boost.of — та сама мапа,
         * інший ресурс. Живе тут, а не в ABooster: іконка потрібна ще панелі
         * бустів у HUD, кнопці старт-буста в меню і шопу, а приватний when
         * в акторі другий користувач просто скопіює.
         *
         * Це функція, а не поле enum'а: регіони беруться з атласу, а він
         * завантажується не в момент ініціалізації класу.
         */
        fun boostIcon(boost: RunEngine.Boost): TextureRegion = when (boost) {
            RunEngine.Boost.SHIELD -> boost_icon_shield
            RunEngine.Boost.MAGNET -> boost_icon_magnet
            RunEngine.Boost.GEM_X2 -> boost_icon_gem_x2
            RunEngine.Boost.SLOW   -> boost_icon_slow_mo
            RunEngine.Boost.PULSE  -> boost_icon_pulse
        }
```

---

## 4. `ABooster.kt` — прибрати приватний `when`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ABooster.kt`

ЗАМІНИТИ `applyIcon()`:

```kotlin
    private fun applyIcon() {
        aIcon.drawable = TextureRegionDrawable(gdxGame.assetsAll.boostIcon(boost))
    }
```

ВИДАЛИТИ цілком секцію `Helper` разом із `getIcon()` — вона більше ні для чого:

```kotlin
    // ------------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------------
    private fun getIcon() = when (boost) {
        RunEngine.Boost.SHIELD -> gdxGame.assetsAll.boost_icon_shield
        ...
    }
```

---

## 5. Необов'язкове: `APanelMenu.StartBoost` — паралельний enum

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/panel/APanelMenu.kt`

Мінімум (щоб просто компілювалось після 1): у `StartBoost` перейменувати `FRENZY` на
`GEM_X2`, підпис `"GEM x2"` лишити.

Але варто подумати про більше. `StartBoost` — це другий enum того самого домену, і він
ще не зшитий із рушієм (`GameScreen.kt:232`, `TODO: апгрейди й startBoost`). Коли
зшиватимеш — доведеться писати **третій** `when`, уже для конверсії `StartBoost` →
`RunEngine.Boost`. Дешевше не заводити конверсію взагалі:

```kotlin
        /** Які бусти взагалі можна запропонувати рекламною кнопкою. Це ПРАВИЛО оферу,
         *  тому список тут, а не в рушії: рушій уміє будь-який Boost. */
        private val START_BOOSTS = listOf(
            RunEngine.Boost.MAGNET,
            RunEngine.Boost.SHIELD,
            RunEngine.Boost.GEM_X2,
            RunEngine.Boost.SLOW,
        )

        fun randomStartBoost() = START_BOOSTS.random()
```

а підпис — на боці виду, поруч із кольором та іконкою:

```kotlin
// GameColor.kt або окремий BoostText — там, де тобі логічніше
fun label(boost: RunEngine.Boost): String = when (boost) {
    RunEngine.Boost.SHIELD -> "SHIELD"
    RunEngine.Boost.MAGNET -> "MAGNET"
    RunEngine.Boost.GEM_X2 -> "GEM x2"
    RunEngine.Boost.SLOW   -> "SLOW-MO"
    RunEngine.Boost.PULSE  -> "PULSE"
}
```

Тоді `rolledBoost` одразу `RunEngine.Boost`, і в `Config(startBoost = rolledBoost)` він
іде без жодної конверсії.

---

## Де має жити мапа «буст → вид» — і чому не в рушії

`CLAUDE.md`: `RunEngine` — **чиста логіка бігу**, без libGDX. Колір, регіон і текст для
гравця — усе це вид. Тому в рушій вони не йдуть: рушій знає лише `enum Boost`.

Напрямок залежності правильний уже зараз: `GameColor` **імпортує** `RunEngine` і мапить
його enum на свій ресурс, а не навпаки. `GameColor.Boost.of(boost)` — еталонна форма,
її й тримаємось; `SpriteUtil.All.boostIcon(boost)` — та сама форма для іншого ресурсу.

Чому не один enum `BoostVisual(color, icon, label)` замість трьох мап: іконки — регіони
з атласу, а атлас завантажується **пізніше** за ініціалізацію класів. Константа enum'а,
обчислена при завантаженні класу, полізла б у ще неіснуючий атлас. Тому функції, а не поля.

Ціна поточного розкладу — три `when` на один enum. Але вони **вичерпні** (`when` по enum
без `else`), тож у момент, коли додаси шостий буст, компілятор сам покаже всі три місця.
Саме тому такий розклад безпечний, а копія `when` у приватному методі актора — ні:
її компілятор теж покаже, але вона розмножиться в кожному новому екрані.
