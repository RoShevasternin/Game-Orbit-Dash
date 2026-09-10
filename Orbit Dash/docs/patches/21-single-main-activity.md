# 21 — Одна `MainActivity`: чому вона відкривалась двічі і чи потрібна `StartActivity`

## Що відбувалось

`MainActivity` «відкривалась по кілька разів» не через Xiaomi. Це поведінка задач Android:

1. Гру запускають **не з іконки** — з Android Studio (▶︎), `adb`, кнопки «Відкрити» в
   інсталяторі чи в Play Store. Кореневий intent задачі тоді **без**
   `ACTION_MAIN + CATEGORY_LAUNCHER`.
2. Користувач натискає Home, потім іконку. Лончер шле `ACTION_MAIN + LAUNCHER`.
   Система знаходить чинну задачу (та сама affinity), але intent не збігається з
   кореневим — і при `launchMode="standard"` **кладе нову `MainActivity` поверх старої**.

Обидві живі: два `GDXFragment` → два `GDXGame`, `initialize()` двічі (реклама,
лідерборд, remote config). Xiaomi це лише підсвічує: там розробляєш (запуск з IDE), а
MIUI довше тримає процес у кеші, тож стан «задача з чужим коренем» живе годинами. У
користувачів це теж буває — Play Store «Відкрити» → Home → іконка — класичний шлях.

## Заміряно на пристрої

Сценарій усюди один: `am start -n …/.MainActivity` (як IDE/інсталятор) → Home → тап по
іконці (`monkey … LAUNCHER`). Рахуємо унікальні `ActivityRecord` `MainActivity` і
лічильник `onCreate` (той, що в `MainActivity.kt:67`).

| варіант | екземплярів після тапу | `onCreate` |
|---|---|---|
| A — як зараз: `StartActivity`(launcher, singleTask) → `MainActivity`(singleTask) | 1 | 1 (StartActivity — 2) |
| B — `MainActivity` = launcher, `standard`, без `StartActivity` | **2** | **2** ← твій баг |
| C — `MainActivity` = launcher, `singleTask`, без `StartActivity` | 1 | 1 |
| D — як B + `isTaskRoot`-захист у `onCreate` | 0 — впала | 2, `finish()`, потім `UninitializedPropertyAccessException: adManager` в `onDestroy` |

Висновок: дублювання зупиняє **`launchMode="singleTask"` на `MainActivity`** — другий
запуск іде в чинний екземпляр через `onNewIntent()`, новий не створюється.
`StartActivity` цього не робить: вона лише пересилає в singleTask-`MainActivity` і
фінішить. Без неї (варіант C) результат той самий. Захист `isTaskRoot` (D) теж
працює по суті, але в цьому коді вимагає, щоб `onResume/onPause/onDestroy` терпіли
неініціалізований `adManager` — зайва крихкість заради того, що `singleTask` дає
безплатно.

Що втрачається без `StartActivity`: нічого. `activity_start.xml` — порожній
`ConstraintLayout`, splash вона не малює; перший кадр холодного старту — чорний
`windowBackground` із теми, а на Android 12+ система показує свій splash з іконкою в
будь-якому разі. Виграш: на одну Activity й один інфлейт менше на старті.

---

## 1. `AndroidManifest.xml` — ЗАМІНИТИ обидва `<activity>` на один

```xml
        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|fontScale|density|smallestScreenSize|locale|layoutDirection"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize"
            tools:ignore="DiscouragedApi,LockedOrientationActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```

Два моменти в цьому блоці:

- `exported="true"` — обов'язково для launcher-activity (зараз у `MainActivity` стоїть `false`).
- `configChanges` розширено. Це **друге джерело «логіка запускається двічі»** —
  перестворення: перемикання темної теми (часто автоматичне ввечері — `uiMode`),
  розмір шрифту (`fontScale`), «розмір екрана» в налаштуваннях (`density`),
  розкладання складаного (`smallestScreenSize`), мова (`locale|layoutDirection`).
  Кожне без цього — `onCreate` заново → новий `GDXGame` (той самий шлях, що дав
  `No buffer allocated!`). Грі на libGDX тут перестворюватись нема чого — розмір
  поверхні `GLSurfaceView` обробляє сам.

## 2. ВИДАЛИТИ

- `app/src/main/java/com/lewydo/orbitdash/StartActivity.kt`
- `app/src/main/res/layout/activity_start.xml`

## 3. `MainActivity.kt:67` — лічильник

`log("StartActivity: $onCreateCounter")` у `MainActivity` — скопійований дебаг-рядок,
через нього в логах два різні класи звуться однаково. **ВИДАЛИТИ** разом із
`onCreateCounter`, або перейменувати на `"MainActivity.onCreate #$onCreateCounter"`,
якщо хочеш лишити як індикатор перестворень.

---

## Перевірка

Той самий сценарій руками або через adb:

```bash
adb shell am start -n com.lewydo.orbitdash/.MainActivity     # «запуск не з іконки»
# Home, потім тап по іконці
adb shell dumpsys activity activities | grep -oE "ActivityRecord\{[0-9a-f]+ u0 com.lewydo.orbitdash/\.MainActivity" | sort -u | wc -l
```

Має бути `1`. І з IDE: ▶︎ → Home → іконка → у грі не має бути «другого» меню під
першим (кнопка Back не повинна показувати ще одну копію гри).

Що змінюється в поведінці: тап по іконці на живій грі тепер приходить у
`MainActivity.onNewIntent()` (нічого робити не треба), а не створює Activity.
`exit()` → `finish()` як і був.
