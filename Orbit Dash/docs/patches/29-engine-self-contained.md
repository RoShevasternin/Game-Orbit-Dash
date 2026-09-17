# 29 — `game/engine/` не імпортує з проєкту нічого

## Що і навіщо

`RunEngine` — чистий Kotlin, але **два рядки** тягнуть його в загальний пакет проєкту:

```kotlin
import com.lewydo.orbitdash.game.utils.RAD_TO_DEG
import com.lewydo.orbitdash.game.utils.TWO_PI
```

Обидві константи лежать у `game/utils/Constants.kt` поруч із `WIDTH_UI`, `REMOVE_ADS_PRICE`
і `TITLE_1` — тобто в одному файлі з розміром екрана, ціною покупки й назвою гри. І
**жоден інший файл проєкту їх не використовує**: перевірив увесь `app/src` (main, test,
androidTest) — тільки `RunEngine`.

Після патча `game/engine/` не має жодного `import com.lewydo…`. Це дає дві речі:

1. **Межу видно одним рядком** (записано в `CLAUDE.md`, розділ «Розміщення файлів»):
   ```bash
   grep -h "^import com.lewydo" app/src/main/java/com/lewydo/orbitdash/game/engine/*.kt
   ```
   Порожньо — межа ціла. Щось вивело — хтось затягнув проєкт у рушій.
2. **Заготовка під модуль `:engine`.** Коли знадобиться (юніт-тести балансу без пристрою),
   винести рушій — це рух папки й кілька рядків Gradle, без розплутування залежностей.

### Куди саме — і чому не окремий файл

Минулого разу я сказав «наприклад, `engine/MathConst.kt`». Подивившись уважніше —
**ні, краще в `companion object` самого `RunEngine`**, у наявну секцію `── кути ──`:

- там уже живуть `norm()` і `angDiff()` — та сама тема, кутова арифметика рушія;
- у констант **один користувач**; окремий файл на дві `const val` — це папка під
  майбутнє, якого ще немає;
- `private` — ніхто зовні на них не сперся, і наступного разу вони не розповзуться.

Якщо колись у `game/engine/` з'явиться другий файл, якому теж треба кути, — тоді й
винесемо `norm`, `angDiff` і константи разом у `engine/Angles.kt`. Зараз це було б
передчасно.

`Math.PI` → `kotlin.math.PI`: значення бітово те саме (`3.141592653589793`), детермінізм не
зачіпається, а рушій імпортує тоді лише `kotlin.*` — без Java-класів, що теж на руку
майбутньому модулю.

Порядок вставки: 1 → 2. Компілюється після 2 (після 1 — червоні `RAD_TO_DEG` / `TWO_PI` у
`RunEngine`).

---

## 1. `Constants.kt` — прибрати секцію MATH

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/utils/Constants.kt`

**ВИДАЛИТИ** (рядки 8–12):

```kotlin
// ------------------------------------------------------------------------
// MATH
// ------------------------------------------------------------------------
const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()
const val TWO_PI     = (Math.PI * 2).toFloat()

```

Файл після цього:

```kotlin
package com.lewydo.orbitdash.game.utils

const val WIDTH_UI  = 360f
const val HEIGHT_UI = 800f

const val TIME_ANIM_SCREEN = 0.4f

// ------------------------------------------------------------------------
// REMOVE_ADS_PRICE
// ------------------------------------------------------------------------
const val REMOVE_ADS_PRICE = 1.99f

// ------------------------------------------------------------------------
// TITLE
// ------------------------------------------------------------------------
const val TITLE_1 = "ORBIT"
const val TITLE_2 = "DASH"
```

---

## 2. `RunEngine.kt` — константи в companion

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/engine/RunEngine.kt`

### 2.1 Імпорти (рядки 3–4)

**ЗАМІНИТИ**

```kotlin
import com.lewydo.orbitdash.game.utils.RAD_TO_DEG
import com.lewydo.orbitdash.game.utils.TWO_PI
import kotlin.math.abs
```

на

```kotlin
import kotlin.math.PI
import kotlin.math.abs
```

### 2.2 `companion object`, секція `── кути ──` (рядки 155–158)

**ЗАМІНИТИ**

```kotlin
        // ── кути ──
        private fun norm(a: Float): Float { var x = a % 360f; if (x < 0f) x += 360f; return x }
```

на

```kotlin
        // ── кути ──
        // Тут, а не в game/utils: рушій не імпортує з проєкту нічого (CLAUDE.md,
        // «Розміщення файлів»). Користувач у констант один — цей клас.
        private const val RAD_TO_DEG = (180.0 / PI).toFloat()
        private const val TWO_PI     = (PI * 2).toFloat()

        private fun norm(a: Float): Float { var x = a % 360f; if (x < 0f) x += 360f; return x }
```

Місця використання (`sin(TWO_PI * time / 20f)` у рядку 317, три `* RAD_TO_DEG` у 520 / 540 /
550) **не міняються**: `private` члени companion видно всередині класу за тим самим іменем.

---

## Перевірка

```bash
cd "/Users/admin/Apps/Game Orbit Dash/Orbit Dash"

# межа: вивід має бути порожнім
grep -h "^import com.lewydo" app/src/main/java/com/lewydo/orbitdash/game/engine/*.kt

# ніхто більше не посилається на старі імена з utils
grep -rn "utils.RAD_TO_DEG\|utils.TWO_PI" app/src

sh gradlew assembleDebug --console=plain -q
```

Поведінка гри не змінюється ні на біт: ті самі значення, ті самі формули. На пристрої
перевіряти нема чого — досить компілятора.
