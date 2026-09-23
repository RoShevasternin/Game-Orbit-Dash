# DEVLOG 04 · YouTube Shorts — тексти для викладу

Файл для заливки — `devlog-04-video-phonk.mp4` (1080×1920, 30 fps, 29.60 с, з фонком).
Запасний без музики — `devlog-04-video-nomusic.mp4`: тільки звук гри.
Обкладинка — `devlog-04-thumbnail.png`, кінцева картка з «DEVLOG 04» по вертикальному
центру. Той самий файл іде і в TikTok.

Мова назви й опису — **англійська**, як у плейлисті. Плейлист «Orbit Dash» уже є — додати
ролик туди, опис плейлиста не міняти.

Трек **згенеровано скриптом** (`tools/devlog04-audio.py`), не бібліотечний і не ремікс:
Content ID неможливий, атрибуція не потрібна. На відміну від DEVLOG 03, де був ремікс
комерційної пісні.

## Shorts — назва

```
The game has a voice now 🔊 Orbit Dash — Devlog 04
```

49 символів зі 100. Хук першим, назва гри й номер серії далі.

Запасні:

```
Every sound is five numbers 🔊 Orbit Dash — Devlog 04
```

```
No audio files, just code 🔊 Orbit Dash — Devlog 04
```

## Shorts — опис

```
The game has a voice now 🔊 Orbit Dash devlog 04: every sound effect in the game is five numbers in a Kotlin file — frequency, length, wave, volume, slide — and not one audio file ships with the build. The game renders them into wav at first launch and plays them like any other sample.

Two ladders came out of it. Collect gems in a row and each one is a semitone higher than the last, up to a full octave; catch sparks and the combo climbs the same way from 950 Hz. Every step is its own baked file — pitching a sample would have stretched its length and its slide, and in the prototype both were fixed.

Then we found the volume was being applied twice: we multiplied by the phone's media volume ourselves, and Android applies it again in hardware. 50 % on the slider sounded like 25 %, 25 % like 6 %. Now there's one mixer — asset weight × bus slider × master — and the system volume is nowhere in the formula, where it belongs.

Everything you hear in this video is the game itself: the build logged the timestamp of every sound event while recording, and the track was rendered from those same five-number recipes.

Next: the PULSE explosion.

▶ Play it free on Google Play: https://play.google.com/store/apps/details?id=com.lewydo.orbitdash

Two of us — he codes, she designs. Made with 💚 in Ukraine. Follow the build.

Music: generated for this video with a script — 146 BPM, no samples.

#gamedev #indiedev #devlog #indiegame #mobilegame #sounddesign #madeinukraine #orbitdash
```

Хештеги в **описі**, не в назві: YouTube сам піднімає перші три над назвою, а в назві вони
з'їдали б ліміт 100 символів. `#vfx` із DEVLOG 03 замінено на `#sounddesign`.

## Закріплений комент від автора

```
Devlog 03 asked if slow-mo should be icy blue — thanks for the answers 🧊 Now the game has sound, and none of it is a file. Which effect should get its own voice next? The game is live: https://play.google.com/store/apps/details?id=com.lewydo.orbitdash 💚
```

## Налаштування в Studio — як у попередніх випусках

Аудиторія «не для дітей», без змінених AI-даних, ліцензія стандартна, категорія Gaming,
коментарі увімкнені, ремікси дозволені. Мова відео — англійська.

Теги (поле Tags, через кому):

```
orbit dash, devlog, indie game, gamedev, libgdx, kotlin, mobile game, android game, sound design, game audio, procedural audio, sfx, game feel
```

Перші сім — постійні для серії. Решта — за темою випуску: у 03 були `shader, vfx`, тут —
звук. Content ID на кроці «Перевірки» не чекати: трек згенеровано скриптом.

## Відповіді на типові коментарі

Ті самі, що в TikTok — `../../tiktok/devlog-04/devlog-04-copy.md`, тут не дублюються.
Різниця одна: у YouTube у відповідях можна давати повне посилання на Play.

## Перед публікацією — чек-лист

1. Обкладинка — `devlog-04-thumbnail.png`.
2. Опис — з блоку вище, разом із рядком «Music: …» і хештегами.
3. Теги — з розділу «Налаштування в Studio».
4. Додати в плейлист «Orbit Dash».
5. Закріпити комент одразу після публікації.
