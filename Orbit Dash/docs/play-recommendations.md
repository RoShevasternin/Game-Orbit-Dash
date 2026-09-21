# Рекомендації Play Console на релізі 8 (1.2.0) — що з них наше

21.09.2026. Розбір чотирьох «actions recommended» із Release dashboard. Це **поради**,
не вимоги: жодна не блокує публікацію й не позначена як policy issue.

Важливе про адресу: вони прив'язані до **релізу 8 (1.2.0)** — тобто до того, що зараз
у гравців. Код відтоді змінювався, але жодна з чотирьох проблем сама по собі не зникла.

## 1 + 2. Edge-to-edge (два пункти — одна причина)

**Наш код правильний.** `MainActivity.onCreate` кличе `enableEdgeToEdge()`
(`MainActivity.kt:68`, у проєкті з 07.08.2026 — тобто вже було у збірці 8), інсети читає
сучасним `ViewCompat.setOnApplyWindowInsetsListener` + `WindowInsetsCompat`, бари ховає
через `WindowInsetsControllerCompat` (рядки 73–85). Жодного `setStatusBarColor`,
`setNavigationBarColor`, `setDecorFitsSystemWindows` чи `SYSTEM_UI_FLAG_*` у нашому коді
немає — перевірено grep'ом по `app/src/main`. `windowOptOutEdgeToEdgeEnforcement` теж немає,
`activity_main.xml` без `fitsSystemWindows`.

**Звідки скарга — деталі з консолі.** Названі три речі:
`android.view.Window.setStatusBarColor`, `android.view.Window.setNavigationBarColor`,
`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`.

Це **нутрощі самого `enableEdgeToEdge()`**. Розпакував `androidx.activity 1.13.0` (версія
з `libs.versions.toml`, у графі `activity-ktx 1.13.0`) і подивився класи:

| клас androidx | що кличе |
|---|---|
| `EdgeToEdgeApi23`, `EdgeToEdgeApi26`, `EdgeToEdgeApi29`, `EdgeToEdgeApi35` | `setStatusBarColor`, `setNavigationBarColor` |
| `EdgeToEdgeApi28`, `EdgeToEdgeApi30` | `layoutInDisplayCutoutMode` = SHORT_EDGES |

Інакше на Android нижче 15 прозорих барів і малювання під вирізом не зробити — це
compat-шлях, і він деприкейтнутий лише з API 35. Тобто Google рекомендує мігрувати з
API, які викликає **його ж бібліотека** в методі, який Google і радить кликати.

З п'яти «місць старту» зі звіту по нашому `mapping.txt` осмислено розшифрувалось одне —
`d60` → `androidx.activity.EdgeToEdge$$ExternalSyntheticLambda0`, і воно вказує точно туди
ж. Решта (`b60`, `y50`, `z50`, `w3`) дає класи, які такого викликати не можуть
(`ETC1TextureData`, `DynamiteModule`, `AbtComponent`) — очевидно, R8 злив/пересинтезував
класи, і клас-рівневий мапінг там не працює.

**Що з цим робити:** нічого. Прибрати `enableEdgeToEdge()` не варіант: без нього на
пристроях до Android 15 гра не малюватиме під вирізом, а поставити SHORT_EDGES руками —
це рівно той самий параметр зі списку. Чекати на androidx.

**Хибний слід (для протоколу):** спершу я списав це на libGDX — у
`gdx-backend-android 1.14.2` справді є посилання на `View.setSystemUiVisibility`
(`AndroidApplication`, `AndroidFragmentApplication`) і на `getDefaultDisplay()` /
`getRealMetrics()`. Але Play назвав не їх. Урок той самий, що й з bitmap: не вгадувати,
поки не розкрито «View details».

## 3. Resizability / orientation — це справді наше

`AndroidManifest.xml:35`: `android:screenOrientation="portrait"`, і поруч
`tools:ignore="DiscouragedApi,LockedOrientationActivity"` — тобто Lint про це вже сварився
і його свідомо заглушили. `android:resizeableActivity` не оголошений.

Гра портретна за побудовою: сцена — 360 юнітів завширшки, HUD, орбіти й поле рахуються
від цього. Ландшафт означає не «зняти замок», а перерахувати композицію екрана.

Плюс із **targetSdk 37**: на Android 16 великі екрани (планшети, розкладені фолди) замок
орієнтації **ігнорують у будь-якому разі** — система покаже гру так, як вважає за потрібне.
Тобто питання не «чи дозволити», а «чи перевірили ми, як воно там виглядає».

**Дія:** прогнати гру на планшетному емуляторі в ландшафті й вирішити — лишати як є
(рекомендація, не вимога) чи вкладатись у адаптив.

## 4. Bitmap image optimization — чужі SDK, робити нічого

Мова не про наші ассети взагалі: скарга на те, що застосунок **качає зображення з мережі
й декодує їх вручну** (`BitmapFactory.decodeStream`). Гра з мережі картинок не тягне —
`BitmapFactory`, `HttpURLConnection`, `openConnection`, `Glide`, `coil` у
`app/src/main/java` не зустрічаються жодного разу.

Обфусковані імена зі звіту розшифровані по `app/build/outputs/mapping/release/mapping.txt`
тієї самої збірки 8 (18.09.2026, 18:23):

| у звіті | насправді | чия бібліотека |
|---|---|---|
| `pb2.n` (декодує) | `com.google.android.gms.internal.measurement.zzadn` | Firebase Analytics |
| `h13.w`, `ie4.run`, `kq4.run`, `ol3.e`, `zh5.w` | `com.google.android.gms.internal.ads.*` | AdMob |
| `rd1.invokeSuspend` | `com.google.firebase.sessions.settings.RemoteSettingsFetcher$doConfigFetch$2` | Firebase Sessions |
| `com.tiktok.util.HttpRequestUtil.doPost` | — | TikTok Business SDK |

Тобто Google радить узяти image-loading бібліотеку для коду власних SDK. Прибрати це можна
лише прибравши рекламу й аналітику.

**Дія:** нічого. Позначити «не корисно» в консолі.

Для протоколу, наші зображення й так дрібні: найбільше — `assets/atlas/brand.png`
628×1338 (668 КБ), msdf-шрифти 888×888, у `res/` 15 файлів, найбільший 65 КБ. Текстури
йдуть у GL власним завантажувачем libGDX, повз `android.graphics.Bitmap`.

## Підсумок

| # | чиє | що робити |
|---|---|---|
| 1, 2 edge-to-edge | androidx.activity — нутрощі `enableEdgeToEdge()` | нічого, чекати оновлення androidx |
| 3 orientation | наше, свідомо | перевірити на планшеті, рішення за нами |
| 4 bitmap | AdMob + Firebase + TikTok SDK | нічого, «не корисно» |

## Що відповіли Google (21.09)

Форма «Why isn't this useful?», ліміт 250 знаків. Галочка «This issue is incorrect»
ставиться лише там, де детекція справді хибна — у 1, 2 і 3 вона правильна, спірна тільки
причина.

| # | галочки | текст |
|---|---|---|
| 1 | Other | We already call enableEdgeToEdge() in onCreate and handle insets with WindowInsetsCompat; the game also hides the system bars. The only legacy calls left are inside the libGDX backend, which we cannot change. |
| 2 | Incorrect + Other | All three come from androidx.activity 1.13.0 itself: EdgeToEdgeApi23/26/29/35 call setStatusBarColor and setNavigationBarColor, EdgeToEdgeApi28/30 set SHORT_EDGES. That is enableEdgeToEdge(). Our code calls no window APIs. |
| 3 | Requires extensive changes + Other | Detection is correct. This is a portrait-only arcade: the playfield and HUD are laid out on a fixed 360-unit portrait scene. Landscape support means redesigning the screen, not dropping a manifest flag. |
| 4 | Incorrect + Other | None of the listed call sites are ours. Deobfuscated via our R8 mapping: pb2 = gms.internal.measurement (Firebase Analytics), h13/ie4/kq4/ol3/zh5 = gms.internal.ads (AdMob), rd1 = Firebase Sessions, plus TikTok SDK. Our game loads no network images. |

Відповідь нічого не закриває: діалог прямо каже, що пункти лишаться в Android vitals.
Це сигнал про пріоритети, не тікет.

**Корисний прийом на майбутнє:** обфусковані імена класів зі звітів Play (`pb2`, `rd1`)
розшифровуються по `app/build/outputs/mapping/release/mapping.txt` тієї збірки, про яку
йдеться: `grep -E "^[^ ].* -> pb2:$" mapping.txt`. Без цього кроку такі звіти читаються
як здогадки.
