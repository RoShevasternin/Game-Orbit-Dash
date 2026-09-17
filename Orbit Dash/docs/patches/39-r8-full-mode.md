# 39 — R8 full mode (рекомендація Play Console)

## Що і навіщо

Play Console пише «Improve your app's memory and performance with R8 optimization».
**R8 у нас уже увімкнений**: `isMinifyEnabled = true` у `release`, і в `market/OD v6.aab`
лежить `BUNDLE-METADATA/com.android.tools/r8.json` зі `isShrinkingEnabled: true`.
Google незадоволений іншим. У тому ж `r8.json`:

- `isProGuardCompatibilityModeEnabled: true` — R8 працює в режимі сумісності
  з ProGuard. Його вмикає рядок `android.enableR8.fullMode=false` у `gradle.properties`.
  Починаючи з AGP 8 за замовчуванням діє повний режим, а цей рядок його вимикає.
- `noOptimizationPercentage: 15.46` — 15 % коду виключено з оптимізації правилами `-keep`.

Рішення: прибрати рядок і тим самим увімкнути повний режим.

### Справжня поломка, яку це відкриває

Без додаткового правила release-збірка **падає одразу на старті**:

```
Unable to get provider androidx.startup.InitializationProvider:
  RuntimeException: Failed to create an instance of androidx.work.impl.WorkDatabase
```

Причина: `play-services-ads` тягне старі `androidx.work:work-runtime:2.7.0` →
`androidx.room:room-runtime:2.2.5`. Сам ти WorkManager і Room не підключав, це
залежності AdMob. Room створює `WorkDatabase_Impl` через рефлексію
конструктором без аргументів. Правило в старому Room зберігає лише клас, без
конструктора. Режим сумісності зберігає конструктор без аргументів автоматично,
а повний режим — ні: у мапінгу збірки без правила `WorkDatabase_Impl.<init>()`
просто немає. Одне правило `-keep` це виправляє.

Коли AdMob перейде на новий `work-runtime` (там Room із правильними правилами),
це правило стане зайвим, але не шкідливим.

### Що перевірено на пристрої (Redmi, release із debug-ключем, у лабораторії)

| | compat (як у маркеті) | full без правила | full + правило |
|---|---|---|---|
| старт | ок | **падіння** | ок |
| `classes.dex` | 8.47 МБ | — | **7.66 МБ (−9.6 %)** |
| APK | 10.66 МБ | — | 10.35 МБ |
| Remote Config → Gson → TikTok | ок | — | ок, модель розібрана повністю |
| AdMob банер | ок | — | ок, оновлюється |
| лоадер → меню → PLAY → забіг → смерть → тап → новий забіг | ок | — | ок, однаково |
| згорнути → повернутись (GL-контекст) | — | — | ок, рендер цілий |

Помилок від гри в logcat немає, лише системний шум MIUI і WebView.

**Не перевірено** (без справжніх дій це не перевірити, або ризиковано):
rewarded-реклама, покупка в `SHOP` (Billing), `RANKS` (Play Games), події TikTok.
Для Billing, Install Referrer і TikTok у `proguard-rules.pro` уже стоять широкі
`-keep` на весь пакет, тож повний режим їх не чіпає. Але rewarded і покупку
перед релізом **пройди руками** на внутрішньому тестуванні в Play.

Під час тесту release-збірка показувала **справжні** банери (бойові ID). Я на них
не тиснув. Кілька показів із твого пристрою — не проблема, а от кліки — так.

### Debug

У `debug` R8 не працює взагалі (`isMinifyEnabled = false`), тож повний режим
на нього не впливає. Вмикати R8 у debug не треба: збірка стане повільнішою,
а стек-трейси й налагодження — незручнішими. Тестувати R8 треба на release.

---

## 1. `gradle.properties` (корінь проєкту)

**ВИДАЛИТИ** рядок:

```properties
android.enableR8.fullMode=false
```

## 2. `app/proguard-rules.pro`

**ДОДАТИ** в кінець файлу, після блоку `# Android Lifecycle`:

```proguard

# Room (WorkManager з AdMob) -----------------------------------------------
# Старий room-runtime 2.2.5 тримає клас бази без конструктора; full mode R8 його викидає,
# і Room не може створити WorkDatabase_Impl через рефлексію — падіння на старті
-keep class * extends androidx.room.RoomDatabase { <init>(); }
```

Пункти 1 і 2 — **лише разом**. Пункт 1 без пункту 2 дає застосунок, що падає на старті.

---

## Як перевірити самому перед релізом

1. Збери `bundleRelease` як завжди і завантаж у **внутрішнє тестування** Play.
2. Постав звідти, пройди: старт → PLAY → смерть → SHOP (відкрити покупку,
   можна скасувати) → rewarded (`+GEMS · AD`) → RANKS → згорнути й повернутись.
3. Перевір, що в новому `.aab` є `BUNDLE-METADATA/com.android.tools/r8.json` з
   `"isProGuardCompatibilityModeEnabled":false`:
   ```bash
   unzip -p "OD v7.aab" BUNDLE-METADATA/com.android.tools/r8.json | tr ',' '\n' | grep Compat
   ```

Чи зникне рекомендація в Play Console — **не гарантую**. Вона може зважати ще
й на широкі `-keep` (15 % неоптимізованого коду). Якщо не зникне, наступний крок —
вужчі правила для `com.tiktok.**`, `androidx.lifecycle.**`,
`com.badlogic.gdx.scenes.scene2d.**`. Але це окрема робота з тим самим тестом на пристрої.

## Попутно помічено

`MainActivity.fetchRemoteConfig()` пише `log("MODEL = $model")`. У release це
виводить TikTok `secret` у logcat, і його прочитає будь-хто з `adb`. Варто
обгорнути в `if (IS_DEBUG)` або не логувати `secret`.
