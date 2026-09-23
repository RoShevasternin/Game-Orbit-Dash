# DEVLOG 04 · TikTok — тексти для викладу

Два файли, один монтаж:

- `devlog-04-video-phonk.mp4` — з фонком (29.60 с, −14.6 LUFS). **Для TikTok беремо цей.**
- `devlog-04-video-nomusic.mp4` — тільки звук гри, без музики (−18.3 LUFS). Для сторіс,
  для реплаїв у коментарях і на випадок, якщо захочеш накласти трендовий трек у редакторі.

Обкладинка — `devlog-04-cover.png` (кінцева картка, «DEVLOG 04» у вертикальному центрі;
у сітці профілю поруч із 01–03 читається серія).

Трек **згенеровано скриптом** (`tools/devlog04-audio.py`, 146 BPM, прогресія F#m-D-A-E з лід-хуком і
сайдчейном), не бібліотечний і не ремікс — Content ID неможливий у принципі. Вихідник:
`source/music/phonk-146-generated-lewydo.mp3`.

Звук у ролику — **сам рушій**: лабораторна збірка писала мітку кожної події, і доріжку
зібрано тими самими рецептами `SfxCatalog`, що грають у грі.

## Опис

```
The game has a voice now 🔊 Orbit Dash devlog 04: every sound in the game is five numbers in a Kotlin file — not one audio file shipped. Collect gems in a row and the pitch climbs a semitone each time; catch sparks and the combo does the same. Free on Google Play — search "Orbit Dash". Made with 💚 in Ukraine.
```

294 символи з 300. Хук у перших словах — «The game has a voice now», далі несподіване
«not one audio file», і лише потім механіка.

Запасні:

```
Every sound in this game is five numbers 🔊 Orbit Dash devlog 04: no audio files at all — the game renders its own effects at first launch. Gems in a row climb a semitone each; so does every spark you catch. Free on Google Play — search "Orbit Dash" 💚
```

```
We gave the game a voice without shipping a single sound file 🔊 Orbit Dash devlog 04: five numbers per effect, a pitch ladder for gems and combos, and a volume bug that made 50 % feel like 25 %. Free on Google Play — search "Orbit Dash" 💚
```

## Хештеги

```
#gamedev #indiedev #devlog #indiegame #mobilegame #sounddesign #madeinukraine #orbitdash
```

`#vfx` із третього випуску замінено на `#sounddesign`: випуск про звук, і саме звідти
приходить аудиторія, якій це цікаво.

## Закріплений комент від автора

```
Devlog 03 asked if slow-mo should be icy blue — thanks for the answers 🧊 Now the game has sound. Which effect should get its own voice next? 💚
```

**Ліміт коментів — 150 символів**, і закріпленого, і відповідей: довші TikTok ховає
під «ще», а їх читають на ходу.

## Відповіді на типові коментарі

- «When release?» → `It's already out — free on Google Play, search "Orbit Dash" 🪐 This update is live.`
- «No audio files, really?» → `Really. Each effect is five numbers — frequency, length, wave, volume, slide. The game renders them to wav on first launch.`
- «Why not just use sound files?» → `Five numbers live next to the event in the code. Changing a sound is changing a number, not regenerating a binary.`
- «What's the pitch thing?» → `Gems in a row: each one is a semitone higher, up to an octave. Same for sparks — 950 Hz, climbing.`
- «What was the volume bug?» → `We multiplied by the phone's volume ourselves — and Android does it again. 50 % on the slider sounded like 25 %.`
- «What engine?» → `libGDX + Kotlin. Design in Figma. No particle engine, and now no audio files either.`
- «Song?» → `Generated for this video with a script — 146 BPM, no samples.`

## Перед публікацією — чек-лист

1. Обкладинку **завантажити** `devlog-04-cover.png` — у сітці профілю поруч із «DEVLOG 03»
   має читатись «DEVLOG 04».
2. Опис — з першого блоку. Хештеги — усі 8. Закріплений комент — одразу після публікації.
3. Звук у ролику вже вшитий; трек TikTok **не** додавати — він заглушить ефекти гри,
   а вони тут і є контент.
