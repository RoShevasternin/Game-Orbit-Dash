# DEVLOG 02 · @lewydo_game — тексти для викладу

Файл ролика — `devlog-02-video-music.mp4` (1080×1920, 30 fps, 29.5 с, **з музикою**:
Walen — Brazilian Hype, 130 BPM, −14.1 LUFS). Обкладинка — `devlog-02-cover.png`.

**Цього разу музика вшита в файл**, а не додається в застосунку: монтаж різаний під цей
конкретний трек (дроп на 1.85 с — зріз «BEFORE → AFTER»), і в бібліотеці TikTok його може
не бути. Заливати з оригінальним звуком, трек у редакторі не додавати.

Трек — freetouse.com, ліцензія вимагає вказати автора в описі. Рядок нижче — обов'язковий.

## Опис поста (рекомендую)

```
Boosts got a timer 🪐 Orbit Dash devlog 02: the combo ring takes your ball's colour, magnet / gem ×2 / slow-mo show how long they've got, and it's all one shader. Already live on Google Play — search "Orbit Dash". Two of us, made with 💚 in Ukraine.

Music: Brazilian Hype by Walen — freetouse.com/music
```

Хук у перших словах — «Boosts got a timer» — це і назва зміни, і те, що гравець бачить у
першу секунду ролика. Гра вже в Play Market — «already live» замість «coming soon».

## Варіанти

```
Which boost is on? Now you can tell 🪐 Orbit Dash devlog 02: timers for magnet, gem ×2 and slow-mo, combo ring in your colour, one shader behind every bar. Live on Google Play — search "Orbit Dash" 💚

Music: Brazilian Hype by Walen — freetouse.com/music
```

```
Before / after 🪐 Orbit Dash devlog 02: the combo ring was white — now it's the colour of your ball, in every theme. Plus timers for every boost. Free on Google Play — search "Orbit Dash". Made with 💚 in Ukraine.

Music: Brazilian Hype by Walen — freetouse.com/music
```

## Хештеги (6–8, не більше)

```
#gamedev #indiedev #indiegame #devlog #mobilegame #ui #madeinukraine #orbitdash
```

`#satisfying` з першого випуску замінено на `#ui`: ролик про інтерфейс, і саме за цим тегом
дивляться дизайнери й розробники.

## Закріплений комент від автора

```
Devlog 01 asked what the spark should sound like — thanks, sound is next 🔊 Now: is the boost timer readable at a glance, or should it be bigger? 💚
```

Комент закриває петлю з першого випуску (там питали про звук) і ставить нове питання —
про те, що показано в цьому ролику. Відповіді на нього реально впливають на розмір смуги в HUD.

**Ліміт коментів — 150 символів**, і закріпленого, і відповідей: довші TikTok ховає
під «ще», а їх читають на ходу.

## Відповіді на типові коментарі

- «When release?» → `It's already out — free on Google Play, search "Orbit Dash" 🪐 This update is live.`
- «Where's the sound?» → `Next devlog. Devlog 01 collected the ideas, we're picking one 🔊`
- «What's "one shader for every bar"?» → `The boost timer, the combo ring and any masked shape come from one little shader family — zero textures, crisp at any size.`
- «What engine?» → `libGDX + Kotlin. Design in Figma. No particle engine — every effect is code.`
- «Why is slow-mo white?» → `Boost colours are fixed: purple = magnet, yellow = gem ×2, white = slow-mo, cyan = shield. Themes recolour the world, never the boosts.`

## Перед публікацією — чек-лист

1. Обкладинку **завантажити** `devlog-02-cover.png` — у сітці профілю поруч із «DEVLOG 01»
   має читатись «DEVLOG 02».
2. Звук — **оригінальний з файлу**, трек із бібліотеки не додавати.
3. Опис — з першого блоку, з рядком «Music: …». Хештеги — усі 8. Закріплений комент —
   одразу після публікації.

## Структура ролика (щоб знати, що де)

Сітка — **130 BPM**, такт 1.846 с; кожен зріз — на межі такту.

| с | такт | що | підпис |
|---|---|---|---|
| 0–1.8 | 0 | стара збірка (DEVLOG 01): кільце комбо біле, правого HUD немає | BEFORE |
| 1.8–3.7 | 1 | **дроп**: та сама сцена, кільце й супутники кольору м'яча | AFTER · the ring takes the ball's colour |
| 3.7–5.5 | 2 | підбір магніту — панель буста вискакує рівно на долю після зрізу | BOOSTS GOT A TIMER |
| 5.5–7.4 | 3 | HUD крупно: шестикутник M + смуга збігає | MAGNET (фіолетовий) |
| 7.4–9.2 | 4 | те саме, gem ×2 | GEM ×2 (жовтий) |
| 9.2–11.1 | 5 | те саме, slow-mo — смуга збігає вдвічі швидше | SLOW-MO (білий) |
| 11.1–12.9 | 6 | кільце щита на м'ячі — кольором бустера | SHIELD (блакитний) |
| 12.9–14.8 | 7 | NEON загальним: комбо ×5 + щит + панель | — |
| 14.8–16.6 | 8 | зріз зі спалахом на SYNTH — рожевий м'яч, жовта панель | EVERY THEME / ITS OWN COLOUR |
| 16.6–18.5 | 9 | SYNTH крупно: кільце комбо рожеве | — |
| 18.5–22.2 | 10–11 | стенд: смуга з ковзною текстурою на тлі фону | ONE SHADER / FOR EVERY BAR |
| 22.2–25.8 | 12–13 | від'їзд, гем-френзі, комбо ×5 | ALREADY IN THE GAME |
| 25.8–29.5 | 14–15 | кінцева картка під димом, у чорне | DEVLOG 02 · Next: sound. · ORBIT DASH — free on Google Play · @lewydo_game |
