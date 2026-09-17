# 31 — Усі версії в одному файлі: `gradle/libs.versions.toml`

## Що і навіщо

Після модуля `:engine` одні й ті самі числа стоять у кількох місцях, і їх треба міняти
синхронно руками:

| що | де зараз |
|---|---|
| `junit 4.13.2` | `app/build.gradle.kts` і `engine/build.gradle.kts` |
| Kotlin `2.4.20` | двічі в кореневому `build.gradle.kts` (плагіни `kotlin.jvm` і `serialization`) |
| JVM `11` | **чотири** рази: `compileOptions` і `jvmTarget` в `app`, `java {}` і `jvmTarget` в `engine` |
| libGDX `1.14.2` | змінна `gdxVersion` усередині `app/build.gradle.kts` — інший модуль її не бачить |

Для цього в Gradle є стандартний механізм — **version catalog**. Один файл
`gradle/libs.versions.toml`, Gradle знаходить його **сам** (у `settings.gradle.kts` нічого
додавати не треба) і дає в кожному `build.gradle.kts` об'єкт `libs`:

```kotlin
testImplementation("junit:junit:4.13.2")   // було: версія в рядку, в кожному модулі своя
testImplementation(libs.junit)             // стане: версія лише в toml
```

Переношу **усі** залежності, а не лише спільні: каталог, у якому половина версій, а
половина досі в `build.gradle.kts`, — це два місця для пошуку замість одного.

Бонус: Android Studio підсвічує застарілі версії прямо в `libs.versions.toml`, одним
списком на весь проєкт.

### Що перевірено в лабораторії

Копія твого поточного стану + блок 0 + цей патч:

| перевірка | результат |
|---|---|
| `assembleDebug` | зібрано |
| `:engine:test` | пройшло |
| дерево залежностей `releaseRuntimeClasspath` до / після | **ідентичне**, 881 рядок |
| `debugCompileClasspath` | ідентичне, 626 рядків |
| `debugUnitTestRuntimeClasspath` | ідентичне, 884 рядки |
| `natives` (натівні .so libGDX) | ідентичне |

Тобто в APK потрапляють рівно ті самі бібліотеки тих самих версій — змінилось лише те, **де
записано число**.

Порядок вставки: **0** → 1 → 2 → 3 → 4 → Gradle Sync. Блоки 2–4 без блоку 1 не
скомпілюються (`Unresolved reference: libs`).

---

## 0. Спершу — повернути `RunEngine` у `game.engine`

Не частина каталогу, але без цього проєкт у дивному стані. Зараз:

```
engine/src/main/kotlin/com/lewydo/orbitdash/RunEngine.kt
package com.lewydo.orbitdash
```

і IDE переписала шість імпортів на `com.lewydo.orbitdash.RunEngine`. Збирається — але
рушій опинився в **корені пакета**, поруч із `MainActivity`, а не поруч із рештою гри
(`game.content`, `game.screens`, …). І таблиця в `CLAUDE.md` каже `game/engine/`.

Встав у Terminal цілим блоком:

```bash
cd "/Users/admin/Apps/Game Orbit Dash/Orbit Dash"

# файл — у правильну папку
mkdir -p engine/src/main/kotlin/com/lewydo/orbitdash/game/engine
mv engine/src/main/kotlin/com/lewydo/orbitdash/RunEngine.kt \
   engine/src/main/kotlin/com/lewydo/orbitdash/game/engine/RunEngine.kt

# перший рядок файлу: package com.lewydo.orbitdash → package com.lewydo.orbitdash.game.engine
sed -i '' '1s/^package com\.lewydo\.orbitdash$/package com.lewydo.orbitdash.game.engine/' \
   engine/src/main/kotlin/com/lewydo/orbitdash/game/engine/RunEngine.kt

# шість імпортів — назад
grep -rl "import com.lewydo.orbitdash.engine.RunEngine" app/src/main/java \
  | xargs sed -i '' 's/import com\.lewydo\.orbitdash\.RunEngine/import com.lewydo.orbitdash.game.engine.RunEngine/'

# перевірка: обидві команди мають нічого не вивести
grep -rn "orbitdash.RunEngine" app/src engine/src
head -1 engine/src/main/kotlin/com/lewydo/orbitdash/game/engine/RunEngine.kt | grep -v "game.engine"
```

---

## 1. `gradle/libs.versions.toml` — НОВИЙ файл

**Файл:** `gradle/libs.versions.toml` (у папці `gradle/`, поруч із `wrapper/`)

Три розділи: `[versions]` — числа, `[libraries]` — бібліотеки, `[plugins]` — плагіни.
Бібліотека посилається на число через `version.ref`, тому дві бібліотеки з однією версією
розійтись не можуть.

```toml
# ═════════════════════════════════════════════════════════════════════════════
#  ВЕРСІЇ ВСЬОГО ПРОЄКТУ — ТУТ І НІДЕ БІЛЬШЕ.
#
#  Gradle сам знаходить цей файл (gradle/libs.versions.toml) і дає в кожному
#  build.gradle.kts об'єкт `libs`. Імена перетворюються на доступ через крапку:
#      junit                 →  libs.junit
#      gdx-backend-android   →  libs.gdx.backend.android
#      kotlin-jvm (плагін)   →  libs.plugins.kotlin.jvm
#      jvm (версія)          →  libs.versions.jvm
#
#  Оновлюєш бібліотеку — міняєш ОДИН рядок у [versions]. Якщо версію ділять
#  кілька записів (kotlin → два плагіни, gdx → чотири бібліотеки), вони всі
#  посилаються на неї через version.ref і розійтись не можуть.
# ═════════════════════════════════════════════════════════════════════════════

[versions]
# ── Мова і збірка ──
# jvm — байткод УСІХ модулів. :engine не може бути вищим за :app: D8 у складі
# app мусить його прожувати. Тому одне число на обидва.
jvm               = "11"
agp               = "9.4.0"
kotlin            = "2.4.20"   # плагіни kotlin-jvm і serialization — завжди одна версія

# ── Тести ──
junit             = "4.13.2"
androidxJunit     = "1.3.0"
espresso          = "3.7.0"

# ── AndroidX ──
coreKtx           = "1.19.0"
appcompat         = "1.8.0"
activityKtx       = "1.13.0"
constraintlayout  = "2.2.2"
navigation        = "2.10.1"
datastore         = "1.2.1"

# ── Гра ──
gdx               = "1.14.2"
shapedrawer       = "2.6.0"
serializationJson = "1.11.0"
gson              = "2.14.0"

# ── Сервіси Google і монетизація ──
googleServices    = "4.5.0"
crashlytics       = "3.0.8"
firebaseBom       = "34.19.0"
playServicesAds   = "25.4.0"
playGames         = "22.0.0"
billing           = "9.1.0"
installReferrer   = "2.2"
tiktok            = "1.6.1"


[libraries]
# ── Тести ──
junit                    = { module = "junit:junit",                            version.ref = "junit" }
androidx-test-ext-junit  = { module = "androidx.test.ext:junit",                version.ref = "androidxJunit" }
androidx-espresso-core   = { module = "androidx.test.espresso:espresso-core",   version.ref = "espresso" }

# ── AndroidX ──
androidx-core-ktx         = { module = "androidx.core:core-ktx",                         version.ref = "coreKtx" }
androidx-appcompat        = { module = "androidx.appcompat:appcompat",                   version.ref = "appcompat" }
androidx-activity-ktx     = { module = "androidx.activity:activity-ktx",                 version.ref = "activityKtx" }
androidx-constraintlayout = { module = "androidx.constraintlayout:constraintlayout",     version.ref = "constraintlayout" }
androidx-navigation-ktx   = { module = "androidx.navigation:navigation-fragment-ktx",    version.ref = "navigation" }
androidx-datastore        = { module = "androidx.datastore:datastore-preferences",       version.ref = "datastore" }

# ── libGDX: чотири бібліотеки, одна версія ──
# *-platform — натівні .so; ABI додається класифікатором у app/build.gradle.kts
gdx-backend-android   = { module = "com.badlogicgames.gdx:gdx-backend-android",   version.ref = "gdx" }
gdx-platform          = { module = "com.badlogicgames.gdx:gdx-platform",          version.ref = "gdx" }
gdx-freetype          = { module = "com.badlogicgames.gdx:gdx-freetype",          version.ref = "gdx" }
gdx-freetype-platform = { module = "com.badlogicgames.gdx:gdx-freetype-platform", version.ref = "gdx" }

# ── Гра, інше ──
shapedrawer               = { module = "space.earlygrey:shapedrawer",                   version.ref = "shapedrawer" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serializationJson" }
gson                      = { module = "com.google.code.gson:gson",                     version.ref = "gson" }

# ── Firebase: версію задає BOM, окремі бібліотеки — без версії ──
firebase-bom          = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-analytics    = { module = "com.google.firebase:firebase-analytics" }
firebase-crashlytics  = { module = "com.google.firebase:firebase-crashlytics" }
firebase-config       = { module = "com.google.firebase:firebase-config" }
firebase-messaging    = { module = "com.google.firebase:firebase-messaging" }

# ── Монетизація і Play ──
play-services-ads      = { module = "com.google.android.gms:play-services-ads",      version.ref = "playServicesAds" }
play-services-games-v2 = { module = "com.google.android.gms:play-services-games-v2", version.ref = "playGames" }
billing-ktx            = { module = "com.android.billingclient:billing-ktx",         version.ref = "billing" }
installreferrer        = { module = "com.android.installreferrer:installreferrer",   version.ref = "installReferrer" }
tiktok-business-sdk    = { module = "com.github.tiktok:tiktok-business-android-sdk", version.ref = "tiktok" }


[plugins]
android-application  = { id = "com.android.application",                  version.ref = "agp" }
kotlin-jvm           = { id = "org.jetbrains.kotlin.jvm",                  version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
google-services      = { id = "com.google.gms.google-services",            version.ref = "googleServices" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics",           version.ref = "crashlytics" }
```

---

## 2. `build.gradle.kts` (кореневий) — плагіни з каталогу

**Файл:** `build.gradle.kts` (корінь `Orbit Dash/`)

**ЗАМІНИТИ** блок `plugins`

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

> Якщо коментаря «Та сама версія…» у тебе немає — не страшно, заміни блок цілком.

на

```kotlin
// Версії плагінів — у gradle/libs.versions.toml, розділ [plugins]
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.kotlin.jvm)           apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services)      apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
```

Блок `buildscript { repositories … }` над ним — без змін.

---

## 3. `engine/build.gradle.kts` — ЗАМІНИТИ файл цілком

**Файл:** `engine/build.gradle.kts`

```kotlin
// ═════════════════════════════════════════════════════════════════════════════
//  :engine — правила бігу. Чистий Kotlin/JVM: без Android, без libGDX.
//
//  Порожній dependencies — це і є суть модуля. Кожна залежність, додана сюди,
//  розширює те, що рушію ДОЗВОЛЕНО знати. Перш ніж додати — див. CLAUDE.md,
//  «Розміщення файлів».
//
//  Версії — у gradle/libs.versions.toml.
// ═════════════════════════════════════════════════════════════════════════════
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// JVM — спільний з :app (libs.versions.jvm): D8 у складі app мусить прожувати цей байткод
java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvm.get()))
    }
}

dependencies {
    testImplementation(libs.junit)
}
```

---

## 4. `app/build.gradle.kts`

**Файл:** `app/build.gradle.kts`

### 4.1 `plugins` (рядки 1–6)

**ЗАМІНИТИ**

```kotlin
plugins {
    id("com.android.application")
    id("kotlinx-serialization")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}
```

на

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}
```

`kotlinx-serialization` — це старий псевдонім того самого плагіна
`org.jetbrains.kotlin.plugin.serialization`. Тепер обидва модулі кличуть його одним ім'ям.

### 4.2 `compileOptions` усередині `android { }` (рядки 69–72)

**ЗАМІНИТИ**

```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
```

на

```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvm.get())
    }
```

### 4.3 `kotlin { }` (рядки 86–90)

**ЗАМІНИТИ**

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
```

на

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvm.get()))
    }
}
```

### 4.4 `dependencies { }` — ЗАМІНИТИ блок цілком

Від рядка `dependencies {` (97) до його закривної `}` (157) включно — **весь блок**. Нижче
`tasks.register("copyAndroidNatives")` і `tasks.configureEach` — без змін.

```kotlin
dependencies {
    // Усі версії — у gradle/libs.versions.toml. Тут лише ЩО підключаємо.

    // Modules --------------------------------------------------------------------------
    implementation(project(":engine"))

    // Test Core ------------------------------------------------------------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // AndroidX Core ------------------------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.ktx)
    implementation(libs.androidx.datastore)

    // LibGDX Core ------------------------------------------------------------------------
    implementation(libs.gdx.backend.android)
    implementation(libs.gdx.freetype)

    // Натівні .so на кожен ABI. Класифікатор не вміщається в каталог — додаємо тут
    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
        natives(variantOf(libs.gdx.platform)          { classifier("natives-$abi") })
        natives(variantOf(libs.gdx.freetype.platform) { classifier("natives-$abi") })
    }

    // Other Core ------------------------------------------------------------------------
    implementation(libs.shapedrawer)
    implementation(libs.kotlinx.serialization.json)

    // Other ------------------------------------------------------------------------

    // Firebase — версію кожної бібліотеки задає BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.messaging)

    // TikTok
    implementation(libs.tiktok.business.sdk)

    // Billing
    implementation(libs.billing.ktx)

    // Install Referrer
    implementation(libs.installreferrer)

    // AdMob
    implementation(libs.play.services.ads)

    // Gson (парсинг JSON з Gist)
    implementation(libs.gson)

    // Google Play Services v2
    implementation(libs.play.services.games.v2)
}
```

Що змінилось по суті, крім адрес:

- `val gdxVersion = "1.14.2"` зник — версія в `[versions] gdx`.
- Вісім рядків `natives(...)` стали циклом на чотири ABI: однакові рядки різнились лише
  назвою ABI. Набір бібліотек той самий — перевірено порівнянням дерева `natives`.

---

## Після вставки

1. **Gradle Sync.**
2. Перевірка:
   ```bash
   cd "/Users/admin/Apps/Game Orbit Dash/Orbit Dash"
   sh gradlew :engine:test assembleDebug --console=plain
   ```
   Очікуване — `BUILD SUCCESSFUL`.

## Як тепер оновлювати

- **Бібліотеку** — один рядок у `[versions]`.
- **Kotlin** — `kotlin = "…"`: оновляться обидва плагіни разом.
- **libGDX** — `gdx = "…"`: оновляться всі чотири бібліотеки й усі натіви.
- **JVM** — `jvm = "…"`: оновиться в обох модулях, у чотирьох місцях.
- **Нова бібліотека** — рядок у `[versions]` (якщо версія нова), рядок у `[libraries]`, і в
  `build.gradle.kts` лише `implementation(libs.xxx)`. Дефіси в назві стають крапками:
  `play-services-ads` → `libs.play.services.ads`.
