# DEVLOG 03 · @lewydo_game — тексти для викладу

Файл ролика — `devlog-03-video-music.mp4` (1080×1920, 30 fps, 30.1 с, **з музикою**:
Katy Perry — Tucked, ремікс lewydo, 122.95 BPM, краш на 7.44 с, дроп на 7.94 с). Обкладинка —
`devlog-03-cover.png` — **кінцева картка** ролика (у відео — кадр 29.0–29.6 с), не окрема
картинка; чому саме вона — у чек-листі.

Музика знову вшита в файл: монтаж різаний під цей ремікс (краш на 7.44 с — білий спалах, дроп
на 7.94 с — швидкість повертається з 0.25× на 0.5× і кільця магніту), у бібліотеці TikTok його
немає. Заливати з оригінальним звуком, трек у редакторі не додавати. Рядок «Music: …» в описі —
обов'язковий.

Трек — ремікс комерційної пісні, не freetouse. На YouTube він може отримати Content ID-claim
(монетизація йде правовласнику, у деяких регіонах ролик можуть закрити), а TikTok, якщо
розпізнає, може вимкнути звук. Рішення за тобою; запасний варіант — трек із freetouse.com
з атрибуцією, як у DEVLOG 02.

## Опис поста (рекомендую)

```
Never check the HUD again 🪐 Orbit Dash devlog 03: every timed boost paints the screen edge in its colour, so you know which one's on — and when it's about to end. Now you can see the magnet pull. Free on Google Play — search "Orbit Dash". Made with 💚 in Ukraine.

Music: Katy Perry — Tucked (remix by lewydo)
```

292 символи з 300 разом із рядком музики. Хук у перших словах — «Never check the HUD again» —
це користь для гравця, а не назва фічі: у DEVLOG 02 ми питали, чи читається таймер буста, а
тепер на нього взагалі не треба дивитись. «Every **timed** boost» — край мають лише три бусти
з таймером (MAGNET, GEM ×2, SLOW-MO); SHIELD і PULSE миттєві, у них краю немає. Край каже,
**який** буст увімкнено і що він ось-ось скінчиться (блимає останні 1.6 с); точний час — досі
смуга в HUD із DEVLOG 02, тому «how long it's got» в опис не пишемо. Магніт тягнув геми завжди —
нове те, що тягу тепер **видно**, звідси «now you can see the magnet pull». Гра вже в Play
Market — «free on Google Play», без обіцянок релізу.

## Варіанти

```
Every boost now paints the screen 🪐 Orbit Dash devlog 03: purple magnet, yellow gem ×2, white slow-mo — one look at the edge and you know what's on and when it's about to end. Themes change, boost colours don't. Free on Google Play — search "Orbit Dash" 💚

Music: Katy Perry — Tucked (remix by lewydo)
```

Хук — перший підпис ролика слово в слово: те, що видно в першу секунду. 285 символів.
Перелік трьох бустів одразу за хуком і є уточненням, які саме.

```
Now you can see the magnet pull 🪐 Orbit Dash devlog 03: attraction waves on the ball, colour on the screen edge for every boost — all code, zero particles, 60 FPS. Drawn in Figma, shipped in Kotlin. Free on Google Play — search "Orbit Dash" 💚

Music: Katy Perry — Tucked (remix by lewydo)
```

Варіант для розробницької аудиторії — якщо перші два випуски тягнули коменти про рушій.
280 символів. «All code», не «all shader»: шейдер малює лише край і кільця, іскорки GEM ×2 —
власний `draw()`, хвилі магніту — запечена текстура.

## Хештеги (6–8, не більше)

```
#gamedev #indiedev #indiegame #devlog #mobilegame #vfx #madeinukraine #orbitdash
```

`#ui` з другого випуску замінено на `#vfx`: ролик про ефекти — світіння, кільця, хвилі,
шейдер — і саме за цим тегом його шукатимуть ті, кому цікаво «як зроблено».

## Закріплений комент від автора

```
Devlog 02 asked if the boost timer was readable — now you don't look at it, the screen edge tells you 🪐 But slow-mo is white. Icy blue? 💚
```

137 символів. Комент закриває петлю з другого випуску (там питали про читабельність таймера)
і ставить питання, відповідь на яке реально впливає на гру: колір SLOW-MO — один рядок у
`BoostCatalog`, у прототипі був крижаний `#A0D2FF`, ми обрали білий. Хай вирішать глядачі.

**Ліміт коментів — 150 символів**, і закріпленого, і відповідей: довші TikTok ховає
під «ще», а їх читають на ходу.

## Відповіді на типові коментарі

- «When release?» → `It's already out — free on Google Play, search "Orbit Dash" 🪐 This update is live.`
- «Where's the sound?» → `Still next. Devlog 01 collected the ideas, this one was about seeing the boosts — hearing them comes next 🔊`
- «Is this a particle effect?» → `No particle engine at all. The edge glow and magnet rings are one full-screen shader pass, the sparkles are a hand-written draw().`
- «What are the rings around the ball?» → `Magnet attraction waves. Three discs with one radial gradient in Figma — in the game, one baked texture drawn four times, contracting onto the ball.`
- «Doesn't a full-screen glow kill the frame rate?» → `Edge glow and three magnet rings are one full-screen pass — about 0.7 ms, no extra draw calls. 60 FPS on the test phone.`
- «How do I know it's about to end?» → `The edge blinks in the last 1.6 seconds, faster as it runs out. You feel it before you'd read the bar.`
- «What engine?» → `libGDX + Kotlin. Design in Figma. No particle engine — every effect is code.`
- «Why is slow-mo white?» → `Boost colours are fixed: purple = magnet, yellow = gem ×2, white = slow-mo. White vs icy blue — tell us in the pinned comment 💚`
- «Song?» → `Katy Perry — Tucked, our own remix.`

## Перед публікацією — чек-лист

1. **Обкладинка — кінцева картка**, де «DEVLOG 03» стоїть по вертикальному центру кадру:
   у редакторі TikTok «Обкладинка → вибрати з відео» на **29.0–29.6 с** (усі рядки картки вже
   на місці, затемнення ще не почалось; сам напис «DEVLOG 03» видно з 27.7 с), або
   завантажити `devlog-03-cover.png` — та сама картка окремим PNG. Не брати кадр із гри й не
   малювати окрему картинку з написом унизу: сітка профілю обрізає ~12 % зверху й знизу і кладе
   лічильник переглядів у лівий нижній кут, тому напис біля нижнього краю зрізається — так
   сталося з DEVLOG 01 і 02, у сітці не видно, що це devlog. Після заливки відкрити профіль і
   перевірити, що «DEVLOG 03» читається цілком.
2. Звук — **оригінальний з файлу**, трек із бібліотеки не додавати. Якщо TikTok після заливки
   показує «звук недоступний» або приглушив — це розпізнаний ремікс; тоді або перезалити з
   freetouse-треком (монтаж доведеться перерізати під його сітку), або лишити як є (рішення твоє).
3. Опис — з першого блоку, з рядком «Music: …». Хештеги — усі 8. Закріплений комент —
   одразу після публікації.

## Структура ролика (щоб знати, що де)

Сітка — **122.95 BPM**, такт 1.952 с від 0.128 с, доля 0.488 с. **Вступ — один план без
жодного зрізу** до тарілки на 7.44 с; з дропу на 7.94 с (початок такту 4) зрізи стоять
**рівно по долях**: де спокійніше — по дві, «на-на-на-на» — по одній. Увесь геймплей — 0.5×
(60 → 30 fps без інтерполяції), розгін перед тарілкою — 0.25×, 1× — лише картки.

| с | такт | що | підпис |
|---|---|---|---|
| 0–5.98 | 0–2 | **вступ, один план**: старт рану, м'яч на внутрішньому кільці збирає геми, повільний наїзд; на такті 2 попереду з'являється буст «M» | ORBIT DASH · DEVLOG 03 (0.3–1.9) · EVERY BOOST (з такту 1) · NOW PAINTS THE SCREEN (з такту 2) |
| 5.98–7.44 | 3 | розгін 0.25×: та сама рамка, м'яч підповзає до буста | — |
| 7.44 | — | **тарілка** = торкання буста: білий спалах, ще 0.25× — край уже входить | — |
| 7.94–9.89 | 4 | **дроп**: зріз на загальний план, фіолетовий край заливає екран (2 долі) → хвилі на м'ячі дуже крупно (2 долі); швидкість назад на 0.5× | MAGNET · ATTRACTION WAVES |
| 9.89–11.84 | 5 | GEM ×2 **по долі**: загальний (спалах) / крупно / загальний / крупно | GEM ×2 |
| 11.84–13.79 | 6 | SLOW-MO по дві долі: загальний (спалах) / м'яч у сповільненому світі крупно | SLOW-MO · the whole world at 0.45× |
| 13.79–15.74 | 7 | три кольори **по долі**: магніт · gem ×2 · slow-mo крупно · магніт загальним | — |
| 15.74–17.70 | 8 | теми **по долі**: SYNTH / SYNTH крупно / NEON / NEON крупно — той самий фіолетовий магніт | THEMES CHANGE |
| 17.70–19.65 | 9 | SYNTH по дві долі: GEM ×2 і SLOW-MO — жовтий і білий ті самі | BOOST COLOURS DON'T |
| 19.65–21.60 | 10 | магніт тягне геми крупно / комбо загальним | they pull the gems in |
| 21.60–23.55 | 11 | загальний; великий удар 22.34 — спалах, хвилі крупно, у темряву | — |
| 23.55–27.46 | 12–13 | **брейкдаун**, як зроблено: макет м'яча з магнітом із Figma → картка `radialGradientFS.glsl` → картка `ABall.kt` → телефон, хвилі крупно | DRAWN IN FIGMA / ONE SHADER / gradient stops from Figma, 1:1 / BAKED INTO ONE TEXTURE · zero extra draw calls |
| 27.46–30.1 | 14–15 | кінцева картка: «DEVLOG» 150 px + «03» 480 px кольором магніту по вертикальному центру, під ним м'яч із хвилями — звідси обкладинка | DEVLOG 03 · Next: sound. · ORBIT DASH — free on Google Play · @lewydo_game · made with 💚 in Ukraine |
