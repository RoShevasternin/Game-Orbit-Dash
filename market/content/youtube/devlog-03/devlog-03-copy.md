# DEVLOG 03 · YouTube Shorts — тексти для викладу

Файл для заливки — `devlog-03-video-music.mp4` (1080×1920, 30 fps, 30.1 с, **з музикою**:
Katy Perry — Tucked, ремікс lewydo, 122.95 BPM, краш на 7.44 с, дроп на 7.94 с). Обкладинка —
`devlog-03-thumbnail.png` — **кінцева картка** ролика (у відео — кадр 29.0–29.6 с) з
«DEVLOG 03» по вертикальному центру. Той самий файл іде і в TikTok. Структура ролика по
секундах і відповіді на типові коментарі — у `../../tiktok/devlog-03/devlog-03-copy.md`, тут
не дублюються.

Мова назви й опису — **англійська**, як у плейлисті. Плейлист «Orbit Dash» уже є — додати
ролик туди, опис плейлиста не міняти.

Трек — ремікс комерційної пісні, не freetouse, тому трирядкової атрибуції немає — лише рядок
«Music: Katy Perry — Tucked (remix by lewydo)». Ремікс може отримати Content ID-claim:
монетизація йде правовласнику, у деяких регіонах ролик можуть закрити. Рішення за тобою;
запасний варіант — трек із freetouse.com з атрибуцією, як у DEVLOG 02.

## Shorts — назва

```
Never check the HUD again 🪐 Orbit Dash — Devlog 03
```

50 символів зі 100 (🪐 YouTube може рахувати за два — запас усе одно великий). Хук першим —
користь для гравця, назва гри й номер серії далі; у стрічці видно повністю.

Запасні:

```
Every boost now paints the screen 🪐 Orbit Dash — Devlog 03
```

```
Which boost is on? The screen edge tells you 🪐 Orbit Dash — Devlog 03
```

## Shorts — опис

```
Never check the HUD again 🪐 Every timed boost now paints the screen edge in its own colour — purple for magnet, yellow for gem ×2, white for slow-mo — so you know which one is on and when it's about to end. Each has its own character: magnet rings converge onto the orbit, gem ×2 sparkles flash around the edge, slow-mo sends slow white waves while the whole world drops to 0.45× for 6 seconds. And the magnet literally pulls: attraction waves contract onto the ball.

Behind it: the edge glow and the three magnet rings are one full-screen shader pass — about 0.7 ms for all of it, no extra draw calls. The attraction waves were drawn in Figma as three discs with one radial gradient; in the game that's a new radial-gradient shader that takes Figma's stops 1:1, baked into one texture and drawn four times with phase offsets. They batch — one or two draw calls for all four — still 60 FPS. Themes recolour the world — boost colours never change. Kotlin + libGDX, design in Figma, no particle engine: every effect is code.

Sound is next.

▶ Play it free on Google Play: https://play.google.com/store/apps/details?id=com.lewydo.orbitdash

Two of us — he codes, she designs. Made with 💚 in Ukraine. Follow the build.

Music: Katy Perry — Tucked (remix by lewydo)

#gamedev #indiedev #devlog #indiegame #mobilegame #vfx #madeinukraine #orbitdash
```

«Every **timed** boost»: край мають лише MAGNET, GEM ×2 і SLOW-MO; SHIELD і PULSE миттєві
(`dur = 0` у `RunEngine.Boost`), краю в них немає — назва-хук «Every boost…» лишається як
підпис ролика, а в тілі опису уточнюємо. «Sparkles flash around the edge», не «crawl»:
16 іскорок спалахують у псевдовипадкових точках периметра (позиція — від номера циклу, без
random у кадрі), а не повзуть по ньому. «When it's about to end», не «how long it's got»:
край блимає останні 1.6 с, точний час — смуга HUD із DEVLOG 02. «One or two draw calls for
all four», не «zero»: чотири хвилі з однієї текстури батчаться, але замір патча 70 — draw
41–42 проти 40 без магніта.

## Хештеги

```
#gamedev #indiedev #devlog #indiegame #mobilegame #vfx #madeinukraine #orbitdash
```

**Перші три** YouTube показує над назвою — тому попереду `#gamedev #indiedev #devlog`.
`#ui` замінено на `#vfx` — випуск про ефекти. `#Shorts` не додаємо: вертикаль до 3 хв
класифікується сама.

## Закріплений комент

```
Devlog 02 asked if the boost timer was readable at a glance — thanks for the answers. Ours: you shouldn't have to look at it — now the screen edge tells you 🪐 One thing we're not sure about: slow-mo is white. Should it be icy blue instead? Sound is still next 🔊 The game is live: https://play.google.com/store/apps/details?id=com.lewydo.orbitdash 💚
```

## Налаштування в Studio — як у попередніх випусках

Аудиторія «не для дітей», без змінених AI-даних, ліцензія стандартна, категорія Gaming,
коментарі увімкнені, ремікси дозволені. Мова відео — англійська. Теги: `orbit dash, devlog,
indie game, gamedev, libgdx, kotlin, mobile game, shader, vfx, game feel`.

На кроці **«Перевірки»** перед публікацією подивитись, чи не з'явилась претензія Content ID
на музику — і вирішити до публікації, не після. Після заливки — зазирнути в **«Обмеження»**
на сторінці ролика: якщо трек розпізнано, там буде «Претензія на авторські права» і видно,
що саме вона робить — лише монетизація чи блокування по регіонах. Ролик із claim-ом лишається
онлайн (крім регіонів блокування); що робити далі — рішення твоє.

## Чим цей випуск відрізняється від другого

- **Музика — ремікс, не freetouse.** Атрибуція — один рядок «Music: …», без трирядкового
  формату. Ризик Content ID описано вгорі; рішення твоє.
- **Обкладинка — кінцева картка**, а не окрема картинка з написом унизу. У сітці плейлиста
  та профілю кадр обрізається ~12 % зверху й знизу, а лічильник переглядів лягає в лівий
  нижній кут — у DEVLOG 01 і 02 через це не читалось, що це devlog. Тепер «DEVLOG 03» стоїть
  по вертикальному центру кадру і переживає будь-який кроп. Напис перекомпоновано: «DEVLOG»
  150 px і велике «03» 480 px кольором магніту одне під одним — кегль інший, ніж у 01 і 02
  (там 165 px в один рядок), серію тримають слово DEVLOG і той самий шрифт. Після заливки
  перевірити в сітці плейлиста.
- **Дроп пізніше** — 7.94 с проти 1.85 с у другому випуску, і вступ інший: до тарілки на 7.44 с —
  один план без зрізів (старт рану, буст попереду, повільний наїзд, розгін 0.25×), а з дропу
  зрізи стоять рівно по долях.
- **Тема — ефекти, не інтерфейс:** `#vfx` замість `#ui`; у тегах Studio `shader, vfx,
  game feel` замість `game ui`.
