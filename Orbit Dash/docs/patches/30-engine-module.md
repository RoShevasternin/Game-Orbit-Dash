# 30 — Рушій в окремому модулі `:engine`

## Що і навіщо

Після патча 29 `game/engine/` не імпортує з проєкту нічого. Але це тримається лише на
дисципліні й на `grep` із `CLAUDE.md`: пакет — просто префікс імені, і компілятор дозволяє
`RunEngine` імпортувати будь-що з `app`.

Модуль робить із правила **стіну**. Перевірено в лабораторній копії: додав у `RunEngine`
два «заборонені» імпорти — і збірка впала:

```
e: RunEngine.kt:4:12  Unresolved reference 'badlogic'.
e: RunEngine.kt:5:34  Unresolved reference 'content'.
```

Не «не варто так робити», а **не компілюється**: у модулі `:engine` немає ні libGDX, ні
класів гри, тож їх нема звідки взяти.

### Чому саме зараз і чому лише рушій

Минулого разу я радив чекати сигналу. Передумав, бо ціна впала майже до нуля:

- межа **вже чиста** (патч 29) — нічого розплутувати;
- **назва пакета не міняється** — `com.lewydo.orbitdash.game.engine` лишається, тож
  шість файлів, що імпортують рушій (`GameScreen`, `ABooster`, `APanelMenu`, `AShieldPip`,
  `APanelGameHud`, `BoostCatalog`), **не чіпаються взагалі**;
- `RunEngine` — один файл.

Друга причина — **тести без телефона** отримуєш одразу. У патчі два реальні тести:
детермінізм за seed і порядок `SPAWN_POOL` (те, що в патчі 27 я пропонував перевіряти
«очима»). Прогін — `sh gradlew :engine:test`, на ноутбуці, без пристрою й емулятора.

**`:game` / `:app` окремо — не роблю і не раджу.** Там межа брудна: актори звертаються до
`gdxGame.analytics`, `gdxGame.ads`, `modelPlayer` — щоб розрізати, треба інтерфейси на
кожен сервіс. Це тиждень переписування заради стіни, яку ти поки не порушував.

### Ціна, чесно

- Ще один `build.gradle.kts` на 20 рядків; версія Kotlin (2.4.20) тепер згадується двічі в
  кореневому файлі — у serialization-плагіні й у `kotlin.jvm`. Оновлюєш одну — онови й другу.
- `internal` більше не видно між `:engine` і `:app`. У `RunEngine` зараз жодного `internal`
  немає — перевірив.
- Android Studio попросить **Gradle Sync** після вставки.

### Що перевірено в лабораторії (копія твого поточного стану + цей патч)

| що | результат |
|---|---|
| `:engine:test` | 2 тести, 0 падінь |
| `assembleDebug` (уся гра з модулем) | APK зібрано |
| libGDX-імпорт у `RunEngine` | не компілюється — стіна працює |

Порядок вставки: 1 → 7, потім Sync. Компілюється лише після **5** (після 4 `app` ще не
бачить рушія).

---

## 1. `settings.gradle.kts` — підключити модуль

**Файл:** `settings.gradle.kts` (корінь `Orbit Dash/`)

**ЗАМІНИТИ** останній рядок

```kotlin
include(":app")
```

на

```kotlin
include(":app")
include(":engine")
```

---

## 2. `build.gradle.kts` (кореневий) — Kotlin для чистого модуля

**Файл:** `build.gradle.kts` (корінь `Orbit Dash/`)

**ЗАМІНИТИ** блок `plugins`

```kotlin
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
```

на

```kotlin
plugins {
    id("com.android.application") version "9.4.0" apply false
    // Та сама версія, що в serialization нижче: це одна версія Kotlin на весь проєкт
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
```

---

## 3. `engine/build.gradle.kts` — НОВИЙ файл

**Файл:** `engine/build.gradle.kts` (нова папка `engine/` поруч з `app/`)

```kotlin
// ═════════════════════════════════════════════════════════════════════════════
//  :engine — правила бігу. Чистий Kotlin/JVM: без Android, без libGDX.
//
//  Порожній dependencies — це і є суть модуля. Кожна залежність, додана сюди,
//  розширює те, що рушію ДОЗВОЛЕНО знати. Перш ніж додати — див. CLAUDE.md,
//  «Розміщення файлів».
// ═════════════════════════════════════════════════════════════════════════════
plugins {
    id("org.jetbrains.kotlin.jvm")
}

// JVM 11 — як в :app. Вище не можна: D8 у складі app мусить прожувати цей байткод
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
```

---

## 4. `engine/.gitignore` — НОВИЙ файл

Кореневий `.gitignore` ігнорує лише `/build` у корені, а `app/` має власний. Без цього
`engine/build/` полізе в git.

**Файл:** `engine/.gitignore`

```
/build
```

---

## 5. Перенести `RunEngine.kt`

Назва пакета **лишається** — `package com.lewydo.orbitdash.game.engine`, у самому файлі не
міняється жоден символ. Міняється лише папка.

`src/main/kotlin`, а не `src/main/java`: це конвенція для чистих Kotlin-модулів. `app`
лишається з `java/`, як є.

`git mv`, а не перетягування в IDE — так історія файлу не рветься:

```bash
cd "/Users/admin/Apps/Game Orbit Dash/Orbit Dash"
mkdir -p engine/src/main/kotlin/com/lewydo/orbitdash/game/engine
git mv app/src/main/java/com/lewydo/orbitdash/game/engine/RunEngine.kt \
       engine/src/main/kotlin/com/lewydo/orbitdash/game/engine/RunEngine.kt
rmdir app/src/main/java/com/lewydo/orbitdash/game/engine
```

> `RunEngine.kt` зараз не закомічений (у ньому зміни патчів 27 і 29) — `git mv` однаково
> перенесе робочу версію разом зі змінами.

---

## 6. `app/build.gradle.kts` — залежність на рушій

**Файл:** `app/build.gradle.kts`

**ЗАМІНИТИ** початок блоку `dependencies`

```kotlin
dependencies {
    // Test Core ------------------------------------------------------------------------
    testImplementation("junit:junit:4.13.2")
```

на

```kotlin
dependencies {
    // Modules --------------------------------------------------------------------------
    implementation(project(":engine"))

    // Test Core ------------------------------------------------------------------------
    testImplementation("junit:junit:4.13.2")
```

Тут компілюється — після 1–6 і Gradle Sync.

---

## 7. `RunEngineTest.kt` — НОВИЙ файл

**Файл:** `engine/src/test/kotlin/com/lewydo/orbitdash/game/engine/RunEngineTest.kt`

```kotlin
package com.lewydo.orbitdash.game.engine

import com.lewydo.orbitdash.engine.RunEngine.Boost
import org.junit.Assert.assertEquals
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
//  Тести рушія — ганяються на ноутбуці, без пристрою:
//      sh gradlew :engine:test
//
//  Саме заради цього рушій і живе в окремому модулі: без libGDX у залежностях
//  ран — це просто цикл update(dt), і хвилина гри рахується за мілісекунди.
// ─────────────────────────────────────────────────────────────────────────────
class RunEngineTest {

    /**
     * Порядок пулу — частина детермінізму: rng.nextInt(8) індексує саме цей
     * список. Переставиш константи в enum Boost — зміниться кожен ран на тому
     * самому seed, і цей тест скаже про це першим.
     */
    @Test
    fun spawnPoolKeepsHistoricalOrder() {
        assertEquals(
            listOf(
                Boost.SHIELD,
                Boost.MAGNET, Boost.MAGNET,
                Boost.FRENZY, Boost.FRENZY,
                Boost.SLOW,
                Boost.PULSE,  Boost.PULSE,
            ),
            Boost.SPAWN_POOL,
        )
    }

    /** Той самий seed + ті самі тапи = той самий ран до останньої сутності. */
    @Test
    fun sameSeedGivesSameRun() {
        assertEquals(simulate(seed = 42L), simulate(seed = 42L))
    }

    /** Хвилина гри при 60 FPS, тап кожні 45 кадрів. Відбиток — усе, що видно ззовні. */
    private fun simulate(seed: Long): String {
        val e = RunEngine(RunEngine.Config(), seed)

        repeat(60 * 60) { frame ->
            if (frame % 45 == 0) e.tap()
            e.update(1f / 60f)
        }

        return "${e.phase} t=${e.time} score=${e.score} gems=${e.gemCount} " +
            e.entities.joinToString { "${it.kind}/${it.boost}/${it.ring}/${it.a}" }
    }
}
```

---

## Перевірка

```bash
cd "/Users/admin/Apps/Game Orbit Dash/Orbit Dash"
sh gradlew :engine:test assembleDebug --console=plain
```

Очікуване: `BUILD SUCCESSFUL`; звіт тестів —
`engine/build/reports/tests/test/index.html` (2 тести, 0 падінь).

Гра на пристрої поводиться так само: ті самі класи, той самий пакет, лише зібрані з іншої
папки. Ставити й дивитись нема на що.

## Після вставки — `CLAUDE.md`

Розділ «Розміщення файлів» зараз каже «проєкт — один Gradle-модуль» і дає `grep`-перевірку.
Коли вставиш і збірка пройде — напиши, я оновлю: межу рушія тримає компілятор, `grep` не
потрібен, а тести — `:engine:test`.
