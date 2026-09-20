# DEVLOG 01 · YouTube Shorts — тексти для викладу

Файл для заливки — `devlog-01-video-music.mp4` (1080×1920, 30 fps, 27.4 с, **з музикою**:
фонк 139.5 BPM, дроп на 3.43 с, −13.1 LUFS). Обкладинка — `devlog-01-thumbnail.png`.
Німий майстер, що пішов у TikTok, — `../../tiktok/devlog-01/devlog-01-video.mp4`. Структура ролика по секундах,
відповіді на типові коментарі й музична сітка 140 BPM — у `../../tiktok/devlog-01/devlog-01-copy.md`, тут не
дублюються.

Мова назви й опису в YouTube виставлена **англійська** — усі тексти нижче англійською.

## Плейлист «Orbit Dash» — опис

```
Orbit Dash — a one-tap mobile arcade, built in public. One tap switches your orbit: dodge the spikes, catch the spark, climb the combo to ×5.

Every devlog here: new effects, real builds recorded off the phone, and the tools we make to tune them. Kotlin + libGDX, design in Figma, every effect is code — no particle engine.

He codes. She designs. Made with 💚 in Ukraine.

▶ Play Orbit Dash free on Google Play: https://play.google.com/store/apps/details?id=com.lewydo.orbitdash
```

**Порядок відео за умовчанням — «спочатку найстаріші».** За умовчанням YouTube ставить
найновіші; девлог — серія, глядач має починати з 01, а не з останнього випуску.

## Shorts — назва

```
The whole game is one tap 🪐 Orbit Dash — Devlog 01
```

51 символ зі 100. Хук першим, назва гри й номер серії — далі; у стрічці рядок видно повністю.

Запасні:

```
Catch the spark, combo ×5 🪐 Orbit Dash — Devlog 01
```

```
One tap. Nine themes. Zero sound (yet) 🪐 Orbit Dash Devlog 01
```

## Shorts — опис

```
The whole game is one tap 🪐 Switch orbits, dodge the spikes, catch the spark — the combo climbs to ×5.

Orbit Dash is a one-tap mobile arcade. Two of us are building it in the open: Kotlin + libGDX, design in Figma, and every effect is code — no particle engine. We even built a slider tuner, so the numbers go from the page straight into the game.

No sound yet — that's Devlog 02.

▶ Play it free on Google Play: https://play.google.com/store/apps/details?id=com.lewydo.orbitdash

Made with 💚 in Ukraine. Follow the build.

#gamedev #indiedev #devlog #indiegame #mobilegame #madeinukraine #orbitdash
```

## Хештеги

```
#gamedev #indiedev #devlog #indiegame #mobilegame #madeinukraine #orbitdash
```

**Перші три** YouTube показує над назвою відео — тому попереду `#gamedev #indiedev #devlog`,
а не `#orbitdash`: за брендом нас ще ніхто не шукає. `#Shorts` не додаємо — вертикаль до 3 хв
класифікується сама, а хештег лише з'їдає видимий слот.

## Закріплений комент

```
The game is live on Google Play — https://play.google.com/store/apps/details?id=com.lewydo.orbitdash 🪐 No sound yet: what should catching the spark sound like? A click, a chime, a bass drop? 💚 Best answer goes into Devlog 02.
```

Той самий, що в TikTok: питання збирає коментарі й одразу анонсує наступний випуск.

## Чим YouTube відрізняється від TikTok

- **Окремий заголовок** ≤100 символів — у TikTok його немає, там усе в описі. Опис TikTok для
  заголовка задовгий, тому рядок написаний окремо.
- **Хештеги живуть в описі**, перші три піднімаються над назвою.
- **Обкладинка** — `cover_devlog_01_lewydo.png` (1080×1920). Якщо в Studio для Shorts немає
  завантаження свого файлу — вибрати кадр кінцевої картки.
- **Заливати тільки оригінальний mp4** з цієї теки, не скачаний із TikTok: вотермарка чужої
  платформи — привід для YouTube притиснути видачу.
- **Посилання клікабельне.** В описі Shorts URL працює як посилання — у TikTok посилання в
  описі не клікаються взагалі, тому там воно живе тільки в біо.
- **Звук.** У TikTok трек вибирається в застосунку; тут файл піде німим. Або вшити музику в
  майстер локально (ffmpeg, дроп на 3.43 с — сітка 140 BPM уже порахована), або додати трек у
  мобільному редакторі YouTube, але тоді монтаж зійде з біту.
