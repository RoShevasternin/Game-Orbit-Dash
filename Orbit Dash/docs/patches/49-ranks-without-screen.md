# 49 — RANKS відкриває Google напряму, `LeaderboardScreen` видалено

## Що і навіщо

`LeaderboardScreen` — трамплін, а не екран. Уся його робота була в `show()`:
відкрити Google-UI і через `animDelay(0.07f)` зробити `back()`. Його `aBackBtn` і
заголовок `LEADERBOARD` гравець не бачив ніколи — `animShow` іде `TIME_ANIM_SCREEN`,
довше за життя самого екрана.

Шкода не в зайвому файлі. `navigate(to = Leaderboard, from = Menu)` кладе `MenuScreen`
у `backStack`, а `back()` дістає звідти **ім'я** і робить `getScreenByName` →
`MenuScreen()` — новий екран. Кожен захід у RANKS перестворював меню: нові актори,
нові `by lazy`, повторний `animShowScreen`, брендблок із нуля. І це заради Activity,
яка й так лягає **поверх** гри — меню під нею живе й чекає повернення.

Порядок вставки: 1 → 4. Екран видаляти **останнім** — поки в `NavigationManager` є
гілка, без файлу не скомпілюється.

## 1. `MenuScreen.kt` — RANKS кличе сервіс напряму

Файл: `app/src/main/java/com/lewydo/orbitdash/game/screens/MenuScreen.kt`,
`addPanelMenu()`, у хвості блоку `apply { … }` — поруч із `onShop` / `onDaily`.

**ЗАМІНИТИ**

```kotlin
onRanks = {
    animHideScreen {
        gdxGame.navigationManager.navigate(
            toScreenName   = LeaderboardScreen::class.java.name,
            fromScreenName = MenuScreen::class.java.name,
        )
    }
}
```

**НА**

```kotlin
onRanks = {
    // Лідерборди показує Google своєю Activity поверх гри — власний екран
    // не потрібен. Меню лишається під нею і чекає повернення, а не
    // перестворюється через navigate → back.
    gdxGame.activity.showLeaderboards(gdxGame.modelPlayer.leaderboardScores())
}
```

Імпорт прибирати не треба: `LeaderboardScreen` лежав у тому ж пакеті
`game.screens`, тож у `MenuScreen` його імпорту й не було.

## 2. `NavigationManager.kt` — екрана більше немає

Файл: `app/src/main/java/com/lewydo/orbitdash/game/manager/NavigationManager.kt`.

**ВИДАЛИТИ** імпорт:

```kotlin
import com.lewydo.orbitdash.game.screens.LeaderboardScreen
```

**ВИДАЛИТИ** гілку в `getScreenByName()` (разом із трьома порожніми рядками під нею):

```kotlin
        LeaderboardScreen::class.java.name -> LeaderboardScreen()
```

**ЗАМІНИТИ** у блоці-комментарі «ЗВІДКИ ПРИЙШЛИ» два рядки — RANKS там більше не
згадується, бо переходу немає:

```kotlin
    //  морф, і анімація появи його зламала б. З GameScreen чи RANKS брендблоку
    //  не існувало, і він має з'явитись сам.
```

```kotlin
    //  морф, і анімація появи його зламала б. З GameScreen брендблоку не
    //  існувало, і він має з'явитись сам.
```

## 3. `LeaderboardManager.kt` — оберег від подвійного тапу

Файл: `app/src/main/java/com/lewydo/orbitdash/services/leaderboard/LeaderboardManager.kt`.

Раніше від подвійного тапу беріг сам трамплін: `animHideScreen` робив
`rootConstraintLayout.disable()`. Тепер меню лишається живим і клікабельним увесь
час, поки Google **асинхронно** готує `allLeaderboardsIntent` (а якщо гравець не
увійшов — ще й поки висить діалог входу). Другий тап у цю щілину дав би другий
`startActivityForResult`.

**ДОДАТИ** поле під `isAuthenticated`:

```kotlin
    /**
     * Відкриття вже в дорозі. Раніше від подвійного тапу беріг екран-трамплін
     * (він гасив меню), тепер меню лишається живим і клікабельним увесь час,
     * поки Google асинхронно готує intent.
     */
    private var isOpening = false
```

**ЗАМІНИТИ** `showAll()` і підпис `ensureSignedIn()`. Прапорець скидається на **всіх
трьох** виходах — intent готовий, intent не вдався, від входу відмовились: немає
шляху, де він залипне й RANKS перестане відкриватись.

```kotlin
    fun showAll(scores: LeaderboardScores) {
        if (isOpening) return
        isOpening = true

        ensureSignedIn(onFailed = { isOpening = false }) {
            submitAll(scores)
            PlayGames.getLeaderboardsClient(activity)
                .allLeaderboardsIntent
                .addOnSuccessListener { intent: Intent ->
                    isOpening = false
                    // startActivityForResult обов'язковий навіть без результату —
                    // API так отримує identity пакета (вимога Google).
                    activity.startActivityForResult(intent, RC_LEADERBOARD_UI)
                }
                .addOnFailureListener { e ->
                    isOpening = false
                    log("Leaderboard: showAll failed: ${e.message}")
                }
        }
    }
```

```kotlin
    private fun ensureSignedIn(onFailed: () -> Unit = {}, onReady: () -> Unit) {
```

і в `else`-гілці `addOnCompleteListener`, після `log("Leaderboard: sign-in failed/declined")`:

```kotlin
                    onFailed()
```

## 4. Видалити файл

`app/src/main/java/com/lewydo/orbitdash/game/screens/LeaderboardScreen.kt` — цілком.

## Перевірено

Лабораторна копія проєкту, `./gradlew assembleDebug` — **BUILD SUCCESSFUL**. Єдине
попередження старе, у `AParticleEffectActor` (unchecked cast), патч його не чіпає.

На пристрої лишається глянути: RANKS відкриває список Google, `back` повертає в **те
саме** меню — без переанімації входу й без стрибка брендблоку.

## Результат вставки (18.09.2026, 09:35)

Вибір зі сторінки: `1.1` — «сам», решта дев'ять — «авто».

| зміна | що з нею |
|---|---|
| `1.1` MenuScreen `onRanks` | **не знайдено у файлі** — `onRanks` досі з `animHideScreen` і `navigate` |
| `2.1` – `2.3` NavigationManager | вставлено |
| `3.1` – `3.5` LeaderboardManager | вставлено |
| `4.1` видалити `LeaderboardScreen.kt` | **притримано** |

`./gradlew assembleDebug` після восьми змін — **BUILD SUCCESSFUL**.

Чому `4.1` тоді притримано, хоч він і «авто»: `MenuScreen` без `1.1` досі згадував
`LeaderboardScreen::class.java.name`, і видалення файлу впало б на unresolved reference.
Тобто `4.1` можливий лише після `1.1`.

### Закрито, 09:44

`1.1` вставлено вручну, звірено у файлі. Після нього видалено
`app/src/main/java/com/lewydo/orbitdash/game/screens/LeaderboardScreen.kt` — згадок
класу в `app/src` не лишилось жодної. `./gradlew assembleDebug` — **BUILD SUCCESSFUL**.

Файл мав і незакомічені правки (`styleTitle` 160f → 16f, прибраний `setSize` у
`addBackBtn`) — вони пішли разом із ним, бо весь екран зайвий. Версія з `HEAD`
(коміт `6d5723a`) лишається в історії, якщо колись знадобиться подивитись.

На пристрої лишається глянути одне: RANKS відкриває список Google, `back` повертає в
**те саме** меню — без переанімації входу й без стрибка брендблоку.
