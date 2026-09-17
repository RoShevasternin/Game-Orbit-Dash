# 38 — ID AdMob в одному місці: `gradle.properties`

## Що і навіщо

Шість ID AdMob були розкидані по `app/build.gradle.kts`: по три в `debug { … }` і в
`release { … }`, кожен у своєму багаторядковому `buildConfigField`. Тепер усі шість —
один блок у `gradle.properties`, а збірка бере їх звідти функцією `admob("debug")` /
`admob("release")`.

- **Чому `gradle.properties`, а не `local.properties`:** файл у git, тож ID лежать на
  GitHub і не загубляться разом із ноутбуком. `local.properties` в `.gitignore`.
- **Це безпечно, хоч репозиторій публічний:** ID AdMob не секрет, вони однаково
  лежать в APK відкритим текстом.
- **Забудеш ключ — збірка впаде з назвою ключа**
  (`Missing 'admob.release.bannerId' in gradle.properties`), а не збере застосунок
  без реклами.

Перевірено в лабораторній копії: `generate{Debug,Release}BuildConfig` і
`process{Debug,Release}MainManifest` проходять, у `BuildConfig` і маніфесті ті самі ID,
що й до зміни.

---

## 1. `gradle.properties` (корінь проєкту, поруч із `settings.gradle.kts`)

**ДОДАТИ** в кінець файлу:

```properties

# AdMob ----------------------------------------------------------------------------------
# Не секрети: ці ID однаково лежать в APK відкритим текстом.
# debug — тестові ID від Google, release — справжні.
admob.debug.appId=ca-app-pub-3940256099942544~3347511713
admob.debug.bannerId=ca-app-pub-3940256099942544/9214589741
admob.debug.rewardedId=ca-app-pub-3940256099942544/5224354917

admob.release.appId=ca-app-pub-4052300465234748~9784404522
admob.release.bannerId=ca-app-pub-4052300465234748/6327275168
admob.release.rewardedId=ca-app-pub-4052300465234748/2627703303
```

---

## 2. `app/build.gradle.kts`

### 2.1 Самий верх файлу, над `plugins {`

**ДОДАТИ:**

```kotlin
import com.android.build.api.dsl.ApplicationBuildType

```

### 2.2 `buildTypes { debug { … } }`

**ЗАМІНИТИ** — усе від `// Field ---` до закривної дужки `debug`:

```kotlin
            // Field ------------------------------------------------------------------------
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField(
                "String",
                "ADMOB_BANNER_ID",
                "\"ca-app-pub-3940256099942544/9214589741\""
            )
            buildConfigField(
                "String",
                "ADMOB_REWARDED_ID",
                "\"ca-app-pub-3940256099942544/5224354917\""
            )
        }
        release {
```

на:

```kotlin
            // Field ------------------------------------------------------------------------
            admob("debug")
        }
        release {
```

### 2.3 `buildTypes { release { … } }`

**ЗАМІНИТИ** — усе від `// Field ---` до закривних дужок `release` і `buildTypes`:

```kotlin
            // Field ------------------------------------------------------------------------
            manifestPlaceholders["admobAppId"] = "ca-app-pub-4052300465234748~9784404522"
            buildConfigField(
                "String",
                "ADMOB_BANNER_ID",
                "\"ca-app-pub-4052300465234748/6327275168\""
            )
            buildConfigField(
                "String",
                "ADMOB_REWARDED_ID",
                "\"ca-app-pub-4052300465234748/2627703303\""
            )
        }
    }
    compileOptions {
```

на:

```kotlin
            // Field ------------------------------------------------------------------------
            admob("release")
        }
    }
    compileOptions {
```

### 2.4 Між блоком `kotlin { … }` і `val natives: Configuration = …`

**ДОДАТИ:**

```kotlin
// ID AdMob для типу збірки — з gradle.properties (admob.<type>.*)
fun ApplicationBuildType.admob(type: String) {
    fun id(key: String) = providers.gradleProperty("admob.$type.$key").orNull
        ?: error("Missing 'admob.$type.$key' in gradle.properties")

    manifestPlaceholders["admobAppId"] = id("appId")
    buildConfigField("String", "ADMOB_BANNER_ID",   "\"${id("bannerId")}\"")
    buildConfigField("String", "ADMOB_REWARDED_ID", "\"${id("rewardedId")}\"")
}
```

Функція стоїть нижче за `android { }`, а викликається всередині нього. У `.kts` це
нормально: функції скрипта видно по всьому файлу.

Компілюється лише після всіх чотирьох пунктів разом із п. 1.

---

## Або однією командою

Файли з перевіреної лабораторної копії (поки жива сесія):

```bash
LAB=/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f03d26ed-12c3-428e-bcbc-fd80ce4b4d8f/scratchpad/lab
cp "$LAB/gradle.properties"    ./gradle.properties
cp "$LAB/app/build.gradle.kts" ./app/build.gradle.kts
```

(запускати з `Orbit Dash/`)

## Далі

Щоб змінити ID: відкрий `gradle.properties`, заміни значення, збери. Новий ID для
нового типу реклами: рядок `admob.<type>.xxxId` у `gradle.properties` і рядок
`buildConfigField` у `admob()`.
