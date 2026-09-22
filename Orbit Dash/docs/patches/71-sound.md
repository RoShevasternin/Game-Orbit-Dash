# Патч 71 — Звук і вібро: осцилятор прототипу, запечений у wav

22.09.2026 · 8 файлів (каталог, синтезатор, тест, `SoundUtil`, `GDXGame`, `LoaderScreen`,
`GameScreen`, `APanelMenu`). Запит: «згенеруй звуки такі як там [у JSX] … на всю гру, і на
кнопки, і на ран, на все» + «коли геми підряд, звук збору ніби змінюється».

Сторінка: https://claude.ai/artifact/Ss9MWWsXdt9z5BgA3aShNL
Тюнер: https://claude.ai/artifact/T1F2GevPUTNFiqsf9sQpEb (`docs/tuners/71-sound.html`)

## Що і навіщо

У JSX-прототипі немає жодного аудіофайлу. Кожен звук — п'ять чисел на WebAudio-осциляторі,
`beep(f, dur, type, vol, slide)`, а поруч `buzz(ms)` — вібро. Тут те саме, але порахуване
наперед: `SoundSynth` рендерить кожен рецепт тією самою математикою у wav, кладе в
`local/sfx/v<хеш>/` один раз, а грає його SoundPool як звичайний `Sound`. Це модель
`VfxTexture`: запекти раз, грати багато. Живий синтез через `AudioDevice` на Android — це
AudioTrack із затримкою 50–150 мс і власним мікшером; SoundPool віддає семпл за кілька мс.

Збігається з прототипом один в один: експоненційні рампи частоти (`f → max(40, f + slide)`)
і гучності (`vol → 0.001`), форми sine / square / sawtooth (square і saw band-limited через
polyBLEP, як WebAudio), усі числа. Додано 1 мс згасання в кінці замість клацу на `stop()`.

### Драбини

`semitone(base, n) = base · 2^(n/12)`. Іскра: 950 Гц square 70 мс, n = `combo` після +1,
стеля `MAX_COMBO` = 8. Гем: 700 Гц sine 80 мс, n = довжина ланцюжка — гем у вікні **2 с**
після попереднього на півтон вище, до 12 (октава); пауза довша — з нуля. Лічильник
`gemChain` / `gemChainT` — у `GameScreen`, не в рушії: на рахунок не впливає, тікає реальним
часом як `comboT`. Кожен щабель — окремий wav, не `Sound.play(pitch)`: SoundPool міняє
pitch разом із тривалістю і розтягує slide.

### Підключення

| подія | де | звук | вібро |
|---|---|---|---|
| «TAP TO START» | `LoaderScreen.isFinish` | READY 660 sine | — |
| тап по лоадеру | `LoaderScreen.touchDown` | MENU_IN 520 sine | — |
| кнопка | `SoundUtil.CLICK` | UI_TICK 500 square 50 мс | — |
| PLAY | `APanelMenu` | німа — звучить старт рану | — |
| старт рану | `GameScreen.startRun` | RUN_START 440 sine | — |
| тап у грі | `onTap` | TAP_OUT 300 / TAP_IN 340 square | 8 |
| іскра | `onNearMiss` | COMBO(n) | 12 |
| гем | `onGemPicked` | GEM(n) | — |
| MAGNET / FRENZY / SLOW | `onBoostApplied` | BOOST 700 sine | — |
| SHIELD | `onBoostApplied` | SHIELD_UP 600 sine | — |
| щит з'їв шип | `onShieldSaved` | SHIELD_SAVE 360 square ↓ | 30 |
| PULSE | `onBoostApplied` | PULSE 120 saw ↓ | 40 |
| третя орбіта | `onOrbit3Online` | ORBIT3: 500 sine ↑ + 90 sine ↓ | 30 |
| смерть | `onDied` | DEATH 220 saw ↓ | 60 |
| ревайв | `onRevived` | REVIVE 520 sine ↑ | — |

Кнопки: `Btn` у прототипі німий; «тік» 500 square 50 мс грає на оферах і виборі скіна —
його поставлено на всі кнопки замість `click.mp3`. Тумблер — з dev-перемикача прототипу.
`click.mp3`, `check_box.mp3`, `SoundManager.EnumSound` більше ніхто не читає — знести
окремим кроком після прослуховування на пристрої. Магазин і місії: BUY, UNLOCK, CLAIM,
PURCHASE, NAME_SAVE названі й запечені, підключати нема куди.

## Файли

| # | файл | що |
|---|---|---|
| 1 | `app/src/main/java/com/lewydo/orbitdash/game/content/SfxCatalog.kt` | НОВИЙ. `Wave`, `Beep`, `Sfx`, `SfxCatalog` — рецепти з JSX, драбини `combo(n)` / `gem(n)`, `all` |
| 2 | `app/src/main/java/com/lewydo/orbitdash/game/manager/SoundSynth.kt` | НОВИЙ. Рецепт → PCM (polyBLEP, рампи) → wav → `Sound`; кеш `local/sfx/v<хеш>/`; `Disposable` |
| 3 | `app/src/test/java/com/lewydo/orbitdash/game/manager/SoundSynthTest.kt` | НОВИЙ. 9 JVM-тестів чистої частини |
| 4 | `app/src/main/java/com/lewydo/orbitdash/game/manager/util/SoundUtil.kt` | `sound(Sfx)` / `play(Sfx)`, `SYNTH_GAIN`; CLICK і CHECK_BOX — із синтезу |
| 5 | `app/src/main/java/com/lewydo/orbitdash/game/GDXGame.kt` | поле `soundSynth`, `create()`, `dispose()` |
| 6 | `app/src/main/java/com/lewydo/orbitdash/game/screens/LoaderScreen.kt` | `bakeAll` у `loadAssets()`, READY, MENU_IN |
| 7 | `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt` | `sfx()`, ланцюжок гемів, усі колбеки рушія, вібро-константи |
| 8 | `app/src/main/java/com/lewydo/orbitdash/game/actors/panel/APanelMenu.kt` | PLAY без кліку |

Порядок: 1 → 8 разом; компілюється після 5 (`SoundUtil` читає `gdxGame.soundSynth`).

## Пакети

`content/` — довідник без libGDX, як `BoostCatalog`; імпортує лише `RunEngine.MAX_COMBO`.
`manager/` — `SoundSynth` виготовляє `Sound` із рецепта, як `SoundManager` із файлу. Поле
`GDXGame`, не `object`: Activity може вмерти при живому процесі — `create()` зробить новий,
`dispose()` відпустить семпли старого.

## Ціна

Перший запуск пише 41 wav (≈0.45 МБ, 5 с аудіо), далі читає. Інший хеш — інша тека, стара
стирається. SoundPool тримає все в пам'яті (<1 МБ). Кадр не платить: запит у канал, `play()`
на IO, як і було.

## Перевірка

- `assembleDebug` — BUILD SUCCESSFUL (лабораторія, 22.09 13:05).
- `:app:testDebugUnitTest --tests SoundSynthTest` — 9/9: довжина `dur·SR`, огинальна до
  −60 дБ і нуль у кінці, square 500 Гц без slide = 50 переходів через нуль, sine 700→950
  (14 → 19 переходів на 10 мс), стеля 40 Гц при slide вниз, октава на 12-му щаблі, мікс
  двох шарів, заголовок wav і нормалізація до 32767, детермінізм і ключ кешу.
- Не перевірено: гучність відносно музики на пристрої — `SYNTH_GAIN` у `SoundUtil`.

## Рішення

- **Запікати, не синтезувати наживо.** `AudioDevice` = AudioTrack, 50–150 мс затримки й
  свій мікшер; тап мусить звучати в тому ж кадрі. Та сама логіка, що `VfxTexture`.
- **Щабель = файл.** `Sound.play(pitch)` скорочує тривалість і розтягує slide пропорційно;
  у прототипі обидва сталі. 22 маленькі файли — дешевше за компроміс.
- **Ланцюжок гемів — у виді.** Не правило гри: не міняє ні рахунку, ні гемів. У рушій
  піде тоді, коли впливатиме на цифри.
- **`click.mp3` не видаляти в цьому патчі.** Відкат до старого кліку — один рядок; знести
  після прослуховування.
