# 47 — Геми й очки: підрахунок, збереження, меню

## Що було не так

- **Геми з рану ніде не нараховувались.** `RunEngine.bank()` існував, але
  його ніхто не кликав; `PlayerModel.addGems(…, RUN)` теж.
- **Рекорд і лічильник ранів не фіксувались.** `PlayerModel.commitRun()`
  ніхто не кликав, у `onDied` стояло `newBest = false // TODO`.
- **Меню завжди показувало `BEST 0 · ◆ 0`.** `APanelMenuState.setStats()`
  ніде не викликався — звідси той «0 у меню, 90 у грі», що ми бачили зранку.
- **Три різні числа гемів у рушії.** HUD показував `gemCount` (кількість
  підібраних), `RunResult.gems` — `floor(gemsRun)`, `bank()` — `round(gemsRun)`.
  Під x2/FRENZY/комбо/апгрейдом вони розходяться.

## Що тепер

| подія | що відбувається |
|---|---|
| **смерть** | `commitRun(score)` → `runs + 1`, рекорд; одразу **збереження**; новий рекорд → лідерборд (тихо) і `run_end.newBest` |
| **тап після смерті (рестарт)** | геми рану → `addGems(…, RUN)` + збереження |
| **вихід з екрана** (назад у меню) | те саме — `GameScreen.hide()` |
| **застосунок у фон після смерті** | те саме — `GameScreen.pause()`; `GDXGame` зберігає вже після нього |
| **HUD у рані** | `◆` — геми, **зібрані за цей ран** (як ти просив), не баланс |
| **меню** | `BEST` і `◆` з `PlayerModel` потоком: оновлюються самі, зокрема після гемів за рекламу; колір `◆` іде за темою |

**Чому геми не одразу при смерті, як рекорд.** Сума рану ще може змінитись:
x2·AD і ревайв на майбутній GameOver-панелі (у прототипі так само — `bank` на
RESTART / MENU). `bank()` віддає суму один раз, тож зайвий виклик нічого не
подвоїть.

**Вихід посеред живого рану** (назад, не після смерті): геми зараховуються,
рекорд — ні, бо ран не завершився смертю. Скажеш інакше — один рядок.

**Пауза посеред живого рану геми НЕ банкує:** після resume ран триває, а
`bank()` віддає суму лише раз — пізніші геми загубились би.

### Рушій

- `bank()` — `floor`, не `round`: нарахувати рівно те, що гравець бачив.
- нове `gemsCollected` — ціле, яке бачить гравець (HUD, `RunResult.gems`,
  `bank()`). Не обнуляється після `bank()`.
- Тести (`RunEngineTest`, пишу сам): `bankPaysOnceExactlyWhatWasShown`,
  `reviveAfterBankStartsFromZero` — **8/8 пройшли**. У проєкт я їх уже
  поклав; вони компілюються після пункту 1.

### Лідерборд — знайдена пастка

`submitScore` кликав `ensureSignedIn`, а той — **інтерактивний вхід Google**.
На першому ж новому рекорді посеред гри вискакувало вікно входу, застосунок
ставав на паузу (у логах `pause`/`resume` і `sign-in failed/declined` —
debug-ключ Play Games не знає). Тепер:

- новий рекорд → `submitScoreIfSignedIn` — **лише якщо вже увійшов**, без вікна;
- RANKS → `showLeaderboard(best)` — вхід за потреби, **потім відправка
  рекорду**, потім UI Google. Гравець, який не увійшов, потрапляє в таблицю,
  щойно сам її відкриє (як у прототипі).
- `MainActivity.submitXp` → `submitBest` (XP у грі немає — назва брехала).

### Перевірено на пристрої (лабораторна копія)

- холодний старт: меню `BEST 0 · ◆ 90` (сейв: gems 90) — раніше було `◆ 0`;
- ран 80 очок, 2 геми: смерть → сейв `BEST 80 RUNS 1`; рестарт → `GEMS 92`;
- кілька ранів поспіль: рекорд не перебивається меншим, геми накопичуються
  (99 → 105 → 110), `RUNS 9`;
- новий рекорд 190 — **без** паузи й вікна входу;
- HUD у рані: `◆ 1` — геми рану;
- назад у меню: `BEST 225 · ◆ 110`; force-stop і запуск — те саме.

**Не перевірено:** геми за рекламу в меню (живе оновлення) — та сама підписка,
але рекламу до кінця я не додивлявся; вхід і відправка в Play Games — на
debug-ключі вхід не проходить, перевіряти на internal testing.

### Не зроблено (поза задачею)

- **GameOver-панель** (score, +gems, REVIVE·AD, x2·AD, RESTART, MENU) —
  досі TODO, тап рестартить. `bank()` / `canRevive` під неї готові.
- **DAY** — стріку немає, лишається `DAY 1` (`setDay()` є на потім).

---

## Порядок вставки

1 → 9, компілюється після 9. Кожен блок «Було» — дослівно з твого файлу
(з рядками навколо), «Стало» — що має бути.

## 1. `engine/src/main/kotlin/com/lewydo/orbitdash/engine/RunEngine.kt`

### 1. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 220

Було:
```kotlin
    var score = 0;          private set

    var combo    = 0f;      private set
    // ------------------------------------------------------------------------
```
Стало:
```kotlin
    var score = 0;          private set

    /**
     * Геми, зібрані за ран, — ціле, яке бачить гравець (HUD, RunResult) і яке
     * віддасть bank(). Не обнуляється після bank(): екран смерті показує суму
     * рану, навіть якщо її вже зараховано.
     */
    val gemsCollected: Int get() = floor(gemsRun).toInt()

    var combo    = 0f;      private set
    // ------------------------------------------------------------------------
```

### 2. ЗАМІНИТИ — біля рядка 289

Було:
```kotlin
     * Повертає суму РІВНО ОДИН РАЗ: повторний виклик = 0, захист від
     * подвійного нарахування (кнопка + автобанк при рестарті).
     */
    fun bank(mult: Float = 1f): Int {
        if (banked) return 0
        banked = true
        return Math.round(gemsRun * mult)
    }

```
Стало:
```kotlin
     * Повертає суму РІВНО ОДИН РАЗ: повторний виклик = 0, захист від
     * подвійного нарахування (кнопка + автобанк при рестарті).
     *
     * floor, а не round: гравець бачив floor (HUD, RunResult), і нарахувати
     * треба рівно те, що він бачив, — без «+1 нізвідки».
     */
    fun bank(mult: Float = 1f): Int {
        if (banked) return 0
        banked = true
        return floor(gemsRun * mult).toInt()
    }

```

### 3. ЗАМІНИТИ — біля рядка 693

Було:
```kotlin
    fun buildResult() = RunResult(
        score       = score,
        gems        = floor(gemsRun).toInt(),
        durationSec = time.toInt(),
        deathRing   = ringIndex + 1,
```
Стало:
```kotlin
    fun buildResult() = RunResult(
        score       = score,
        gems        = gemsCollected,
        durationSec = time.toInt(),
        deathRing   = ringIndex + 1,
```

## 2. `app/src/main/java/com/lewydo/orbitdash/game/GDXGame.kt`

### 1. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 183

Було:
```kotlin
    // API
    // ------------------------------------------------------------------------
    private fun collectModelPlayer() {
        coroutine.launch {
```
Стало:
```kotlin
    // API
    // ------------------------------------------------------------------------

    /**
     * Зберегти негайно — після подій, які шкода втратити (кінець рану, геми).
     * Автозбереження раз на 30 с і збереження на паузі лишаються; це — щоб
     * падіння чи вбитий процес між ними не з'їли рекорд.
     */
    fun saveGame() = saveManager.save()

    private fun collectModelPlayer() {
        coroutine.launch {
```

## 3. `app/src/main/java/com/lewydo/orbitdash/game/actors/panel/APanelGameHud.kt`

### 1. ЗАМІНИТИ — біля рядка 82

Було:
```kotlin
     * Єдина точка оновлення. Кличеться з GameScreen.render() щокадру.
     *
     * @param gemsTotal баланс гравця ПЛЮС незараховані геми рану — гравець
     *   має бачити, скільки в нього стане, а не скільки було до старту.
     */
    fun syncFrom(engine: RunEngine, gemsTotal: Int) {
        if (engine.score != lastScore) {
            lastScore = engine.score
```
Стало:
```kotlin
     * Єдина точка оновлення. Кличеться з GameScreen.render() щокадру.
     *
     * Геми — ЗІБРАНІ ЗА РАН, не баланс: у рані важливо, скільки назбирав
     * цього разу; баланс видно в меню.
     */
    fun syncFrom(engine: RunEngine) {
        val gems = engine.gemsCollected

        if (engine.score != lastScore) {
            lastScore = engine.score
```

### 2. ЗАМІНИТИ — біля рядка 91

Було:
```kotlin
        }

        if (gemsTotal != lastGems) {
            lastGems = gemsTotal
            aGemLbl.setText("◆ $gemsTotal")
        }

```
Стало:
```kotlin
        }

        if (gems != lastGems) {
            lastGems = gems
            aGemLbl.setText("◆ $gems")
        }

```

## 4. `app/src/main/java/com/lewydo/orbitdash/game/actors/panel/APanelMenuState.kt`

### 1. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 8

Було:
```kotlin
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

class APanelMenuState(override val screen: AdvancedScreen): AAutoLayout(
```
Стало:
```kotlin
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.game.utils.theme.ThemeSync

class APanelMenuState(override val screen: AdvancedScreen): AAutoLayout(
```

### 2. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 37

Було:
```kotlin

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addLbls()
    }

```
Стало:
```kotlin

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val themeSync = ThemeSync(::syncTheme)

    // Кеш: setText перебудовує розкладку MSDF — лише коли число змінилось
    private var lastBest = -1
    private var lastGems = -1

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addLbls()
        themeSync.sync()
    }

    override fun act(delta: Float) {
        super.act(delta)
        themeSync.sync()
    }

```

### 3. ЗАМІНИТИ — біля рядка 59

Було:
```kotlin
    // API
    // ------------------------------------------------------------------------
    fun setStats(best: Long, gems: Long, day: Int) {
        aBestLbl.setText("BEST $best")
        aGemsLbl.setText("◆ $gems")
        aDayLbl.setText("DAY $day")
    }

}
```
Стало:
```kotlin
    // API
    // ------------------------------------------------------------------------
    /** Рекорд і баланс гемів — з PlayerModel. */
    fun setStats(best: Int, gems: Int) {
        if (best != lastBest) { lastBest = best; aBestLbl.setText("BEST $best") }
        if (gems != lastGems) { lastGems = gems; aGemsLbl.setText("◆ $gems") }
    }

    /** День стріку. Поки стріку немає — лишається «DAY 1» з конструктора. */
    fun setDay(day: Int) {
        aDayLbl.setText("DAY $day")
    }

    // ------------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        aGemsLbl.setTextColor(ThemeManager.current.gem)
    }

}
```

## 5. `app/src/main/java/com/lewydo/orbitdash/services/leaderboard/LeaderboardManager.kt`

### 1. ЗАМІНИТИ — біля рядка 10

Було:
```kotlin
// LeaderboardManager — Google Play Games Services v2
//
//   submitScore() — відправити рахунок (XP) у лідерборд
//   showLeaderboard() — відкрити стандартний UI Google
//
//   Sign-in у v2 автоматичний при старті (PlayGamesSdk.initialize).
```
Стало:
```kotlin
// LeaderboardManager — Google Play Games Services v2
//
//   submitScoreIfSignedIn() — рекорд у лідерборд, ТИХО (лише якщо вже увійшов)
//   showLeaderboard(best)   — вхід за потреби → рекорд → стандартний UI Google
//
//   Sign-in у v2 автоматичний при старті (PlayGamesSdk.initialize).
```

### 2. ЗАМІНИТИ — біля рядка 46

Було:
```kotlin

    // ------------------------------------------------------------------------
    // Submit score (XP)
    // ------------------------------------------------------------------------

    fun submitScore(xp: Long) {
        if (xp <= 0) return
        ensureSignedIn {
            PlayGames.getLeaderboardsClient(activity).submitScore(leaderboardId, xp)
            log("Leaderboard: submitted XP=$xp")
        }
    }

```
Стало:
```kotlin

    // ------------------------------------------------------------------------
    // Submit score
    // ------------------------------------------------------------------------

    /**
     * Тихо: лише якщо гравець уже увійшов. Кличеться на новому рекорді, а
     * вікно входу Google посеред гри ставило застосунок на паузу. Хто не
     * увійшов, відправить рекорд, коли сам відкриє RANKS (showLeaderboard).
     * Play Games сам тримає найкращий результат — старший рекорд не затре.
     */
    fun submitScoreIfSignedIn(score: Long) {
        if (score <= 0 || !isAuthenticated) return
        submit(score)
    }

    private fun submit(score: Long) {
        PlayGames.getLeaderboardsClient(activity).submitScore(leaderboardId, score)
        log("Leaderboard: submitted score=$score")
    }

```

### 3. ЗАМІНИТИ — біля рядка 61

Було:
```kotlin
    // ------------------------------------------------------------------------

    fun showLeaderboard() {
        ensureSignedIn {
            PlayGames.getLeaderboardsClient(activity)
                .getLeaderboardIntent(leaderboardId)
```
Стало:
```kotlin
    // ------------------------------------------------------------------------

    /** [best] — рекорд із сейву: відправляємо перед показом, щоб гравець бачив себе. */
    fun showLeaderboard(best: Long) {
        ensureSignedIn {
            if (best > 0) submit(best)
            PlayGames.getLeaderboardsClient(activity)
                .getLeaderboardIntent(leaderboardId)
```

## 6. `app/src/main/java/com/lewydo/orbitdash/MainActivity.kt`

### 1. ЗАМІНИТИ — біля рядка 167

Було:
```kotlin
    }

    fun submitXp(xp: Long) {
        leaderboardManager.submitScore(xp)
    }

    fun showLeaderboard() {
        leaderboardManager.showLeaderboard()
    }

```
Стало:
```kotlin
    }

    /** Новий рекорд — у лідерборд, лише якщо гравець уже увійшов (без вікна входу). */
    fun submitBest(score: Long) = runOnUiThread {
        leaderboardManager.submitScoreIfSignedIn(score)
    }

    /** RANKS: вхід за потреби → поточний рекорд → стандартний UI Google. */
    fun showLeaderboard(best: Long) = runOnUiThread {
        leaderboardManager.showLeaderboard(best)
    }

```

## 7. `app/src/main/java/com/lewydo/orbitdash/game/screens/LeaderboardScreen.kt`

### 1. ЗАМІНИТИ — біля рядка 42

Було:
```kotlin
        animShowScreen()

        // відправляємо актуальний XP і одразу відкриваємо стандартний UI Google
        gdxGame.activity.showLeaderboard()

        stageUI.root.animDelay(0.07f) { gdxGame.navigationManager.back() }
```
Стало:
```kotlin
        animShowScreen()

        // відправляємо рекорд і одразу відкриваємо стандартний UI Google
        gdxGame.activity.showLeaderboard(gdxGame.modelPlayer.best.toLong())

        stageUI.root.animDelay(0.07f) { gdxGame.navigationManager.back() }
```

## 8. `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt`

### 1. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 27

Було:
```kotlin
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.util.log

```
Стало:
```kotlin
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.services.analytics.AnalyticsManager
import com.lewydo.orbitdash.util.log

```

### 2. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 172

Було:
```kotlin
    }

    override fun render(delta: Float) {
        super.render(delta)
```
Стало:
```kotlin
    }

    /** Екран іде (меню, назад) — ран далі не піде, геми рану в баланс. */
    override fun hide() {
        bankRun()
        super.hide()
    }

    /**
     * Застосунок іде у фон. GDXGame зберігає ПІСЛЯ screen.pause(), тож геми,
     * забанкані тут, потраплять у сейв. Лише після смерті: живий ран після
     * resume триває, а bank() віддає суму один раз — пізніші геми загубились би.
     */
    override fun pause() {
        if (engine.phase == RunEngine.Phase.DEAD) bankRun()
        super.pause()
    }

    override fun render(delta: Float) {
        super.render(delta)
```

### 3. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 260

Було:
```kotlin
     */
    private fun startRun() {
        releaseAllActors()

```
Стало:
```kotlin
     */
    private fun startRun() {
        bankRun()                 // геми попереднього рану — до того, як рушій зміниться
        releaseAllActors()

```

### 4. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 280

Було:
```kotlin

    /**
     * Перемикачі дебаг-панелі живуть в екрані, а не в рушії: новий ран — новий
     * RunEngine, і без цього кнопка лишалась би «ON», а рушій — у дефолтах.
```
Стало:
```kotlin

    /**
     * Геми рану → баланс гравця. Рушій віддає суму рівно раз, тож зайвий
     * виклик (рестарт, потім вихід) нічого не подвоїть.
     */
    private fun bankRun() {
        val gems = engine.bank()
        if (gems <= 0) return
        gdxGame.modelPlayer.addGems(gems, AnalyticsManager.GemSource.RUN)
        gdxGame.saveGame()
    }

    /**
     * Перемикачі дебаг-панелі живуть в екрані, а не в рушії: новий ран — новий
     * RunEngine, і без цього кнопка лишалась би «ON», а рушій — у дефолтах.
```

### 5. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 293

Було:
```kotlin
            quickDeaths = if (result.durationSec < 15) quickDeaths + 1 else 0

            gdxGame.analytics.runEnd(
                score       = result.score,
```
Стало:
```kotlin
            quickDeaths = if (result.durationSec < 15) quickDeaths + 1 else 0

            // Рекорд і лічильник ранів — одразу. Геми — пізніше, у bankRun():
            // до рестарту чи виходу сума ще може змінитись (x2·AD, ревайв).
            val newBest = gdxGame.modelPlayer.commitRun(result.score)
            gdxGame.saveGame()
            if (newBest) gdxGame.activity.submitBest(result.score.toLong())

            gdxGame.analytics.runEnd(
                score       = result.score,
```

### 6. ЗАМІНИТИ — біля рядка 298

Було:
```kotlin
                gemsEarned  = result.gems,
                deathRing   = result.deathRing,
                newBest     = false,   // TODO: порівняти з PlayerData.best
            )

            // TODO: GameOver-панель (score, +gems, REVIVE·AD, X2·AD, RESTART, MENU)
            log("DEAD score=${result.score} gems=${result.gems} t=${result.durationSec}s ring=${result.deathRing}")
        }

```
Стало:
```kotlin
                gemsEarned  = result.gems,
                deathRing   = result.deathRing,
                newBest     = newBest,
            )

            // TODO: GameOver-панель (score, +gems, REVIVE·AD, X2·AD, RESTART, MENU)
            log("DEAD score=${result.score} gems=${result.gems} t=${result.durationSec}s ring=${result.deathRing} best=$newBest")
        }

```

### 7. ЗАМІНИТИ — біля рядка 360

Було:
```kotlin
    }

    /**
     * Геми показуємо СУМОЮ: баланс гравця плюс незараховані за цей ран.
     * Гравець має бачити, скільки в нього СТАНЕ, а не скільки було до старту —
     * інакше підбір гема нічого не міняє на екрані й читається як баг.
     */
    private fun syncHud() {
        aPanelGameHud.syncFrom(
            engine    = engine,
            gemsTotal = gdxGame.modelPlayer.gems + engine.gemCount,
        )
    }

```
Стало:
```kotlin
    }

    /** HUD читає рушій сам: рахунок, геми рану, комбо, щит. */
    private fun syncHud() {
        aPanelGameHud.syncFrom(engine)
    }

```

## 9. `app/src/main/java/com/lewydo/orbitdash/game/screens/MenuScreen.kt`

### 1. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 30

Було:
```kotlin
import com.lewydo.orbitdash.services.analytics.AnalyticsManager
import com.lewydo.orbitdash.util.log

// ----------------------------------------------------------------------------
```
Стало:
```kotlin
import com.lewydo.orbitdash.services.analytics.AnalyticsManager
import com.lewydo.orbitdash.util.log
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ----------------------------------------------------------------------------
```

### 2. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 115

Було:
```kotlin
        super.show()
        wirePanelMenu()

        // Реклама живе на UI-потоці, сцена — на GL. Кожен сигнал доступності
```
Стало:
```kotlin
        super.show()
        wirePanelMenu()
        collectStats()

        // Реклама живе на UI-потоці, сцена — на GL. Кожен сигнал доступності
```

### 3. ДОДАТИ (контекст — рядки навколо, вони вже є) — біля рядка 151

Було:
```kotlin
        aStarField.animRippleAt(v.x, v.y)
        return false
    }

```
Стало:
```kotlin
        aStarField.animRippleAt(v.x, v.y)
        return false
    }

    // ------------------------------------------------------------------------
    // Stats
    // ------------------------------------------------------------------------

    /**
     * BEST і геми — потоком, а не разовим читанням: геми за рекламу приходять,
     * поки меню відкрите, а на холодному старті сейв дочитується вже після show().
     * Скоуп екрана гасне в dispose() — підписка не переживе меню.
     */
    private fun collectStats() {
        val player = gdxGame.modelPlayer
        coroutine?.launch {
            combine(player.bestFlow, player.gemsFlow) { best, gems -> best to gems }
                .collect { (best, gems) -> runGDX { aPanelMenuState.setStats(best, gems) } }
        }
    }

```

---

## Або скопіювати з лабораторії

Поки жива сесія. Лабораторія — твій проєкт станом на 13:15 17.09 плюс ці зміни.
Якщо ти відтоді міняв ці файли — вставляй руками.

```bash
LAB=/private/tmp/claude-501/-Users-admin-Apps-Game-Orbit-Dash-Orbit-Dash/f03d26ed-12c3-428e-bcbc-fd80ce4b4d8f/scratchpad/lab
for f in \
  engine/src/main/kotlin/com/lewydo/orbitdash/engine/RunEngine.kt \
  app/src/main/java/com/lewydo/orbitdash/MainActivity.kt \
  app/src/main/java/com/lewydo/orbitdash/game/GDXGame.kt \
  app/src/main/java/com/lewydo/orbitdash/game/actors/panel/APanelGameHud.kt \
  app/src/main/java/com/lewydo/orbitdash/game/actors/panel/APanelMenuState.kt \
  app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt \
  app/src/main/java/com/lewydo/orbitdash/game/screens/LeaderboardScreen.kt \
  app/src/main/java/com/lewydo/orbitdash/game/screens/MenuScreen.kt \
  app/src/main/java/com/lewydo/orbitdash/services/leaderboard/LeaderboardManager.kt; do
  cp "$LAB/$f" "$f"
done
```

(запускати з `Orbit Dash/`)
