# market/content — усе, що виходить у соцмережі

Три рівні, за призначенням файлу: **чим робимо** (`tools`), **з чого робимо** (`source`),
**що вже опубліковано** (`tiktok`, `youtube`). Один випуск = одна тека всередині платформи.

```
tools/                       інструменти монтажу
  build-devlog-01.py         драйвер DEVLOG 01: сирі записи → готовий mp4 + обкладинка
  build-devlog-02.py         драйвер DEVLOG 02: трекер м'яча для крупних планів, кадрова сітка тактів
  build-devlog-03.py         драйвер DEVLOG 03: зрізи по ударах треку (не по тактах), весь геймплей 0.5×, картки «як зроблено»
  lab-devlog-03.py           лабораторна збірка для запису: автопілот, безсмертя, буст кожні N с, без банку (застосовується до КОПІЇ проєкту)
  render-code.swift          картка з кодом (Menlo, підсвітка) → прозорий PNG; для сегментів «як зроблено»
  render-svg.swift           SVG із Figma → PNG з прозорим тлом (qlmanage кладе на біле)
  build-teaser.py            драйвер ролика #1 (18.09, en/uk) — сировини до нього на диску вже немає
  render-caption.swift       текст → прозорий PNG (CoreText, Inter ExtraBold); --color RRGGBB — колір підпису
  fonts/                     Inter 28pt, усі накреслення (ними ж підписує market/leaderboards)

source/                      сировина: не публікується, але без неї нічого не перезібрати
  footage/                   записи з телефона, 720×1650, 60 fps, автопілот
                             **mp4 поза git** (`.gitignore`): 30–140 МБ на файл, GitHub ріже >100 МБ.
                             Лежать лише локально — не видаляти й тримати в бекапі, старий білд
                             уже не перезняти. У git ідуть тільки логи подій поруч.
    gameplay-90s-combo-x5.mp4        90 с без смерті, драбина комбо до ×5 (REC1)
    gameplay-90s-events.log          події OD_LAB до нього: час кожної іскри й тапу
    gameplay-48s-combo-ladder.mp4    48 с, чиста драбина ×2→×5 (REC3)
    gameplay-48s-events.log          події до нього
    gameplay-55s-orbit3.mp4          55 с на третій орбіті (REC2, логу немає)
    gameplay-90s-neon-boosts.mp4     DEVLOG 02: патчі 55–64, тема NEON, буст кожні 8 с (lab.txt: boostEvery=8)
    gameplay-75s-synth-boosts.mp4    те саме, тема SYNTH, буст кожні 7 с
    stand-progress-bar.mp4           TestScreen: ABarProgress з ковзною текстурою (16 с; нижня смуга — тестова фотка, у ролик не йде)
    gameplay-100s-neon-vignette.mp4  DEVLOG 03: патчі 66–70, NEON, lab-devlog-03 (буст кожні 9 с: MAGNET → GEM ×2 → SLOW-MO)
    gameplay-48s-synth-vignette.mp4  те саме, SYNTH
  cards/                     тексти для карток «як зроблено» (render-code.swift)
    devlog-03-shader.txt             radialGradientFS.glsl — уривок
    devlog-03-kotlin.txt             ABall.kt — запікання хвиль магніта
  music/                     треки під ролики
    phonk-aggressive-drift-night.mp3   Pixabay, alex-morgan, Content License (Shorts/Reels/TikTok)
    brazilian-hype-walen.mp3           freetouse.com, Walen — Brazilian Hype, 130 BPM; атрибуція в описі обов'язкова
    tucked-remix-lewydo.mp3            DEVLOG 03: власний ремікс (Katy Perry — Tucked), 30.14 с, 122.95 BPM; НЕ royalty-free — Content ID можливий
  tuner/                     кадри тюнера 53-spark для сегмента «ми зробили тюнер»
    tuner-53-spark-default.png         повзунки на дефолтах
    tuner-53-spark-tuned.png           ті самі повзунки підкручені
    tuner-53-spark-tuned.html          копія сторінки з не-дефолтними state.values (з неї знято still)

tiktok/
  devlog-01/
    devlog-01-video.mp4      що залито в TikTok: 1080×1920, 27.4 с, БЕЗ звуку (трек додано в застосунку)
    devlog-01-cover.png      обкладинка для сітки профілю
    devlog-01-copy.md        опис, хештеги, закріплений комент, структура ролика по секундах
  devlog-02/
    devlog-02-video-music.mp4  1080×1920, 29.5 с, З музикою — монтаж різаний під трек, у бібліотеці TikTok його може не бути
    devlog-02-cover.png        обкладинка: м'яч із кільцем комбо, «DEVLOG 02» тим самим кеглем, що й 01
    devlog-02-copy.md          опис із атрибуцією треку, хештеги, комент, структура по тактах
  devlog-03/
    devlog-03-video-music.mp4  1080×1920, 30 fps, 30.1 с, З музикою — власний ремікс (Katy Perry — Tucked), монтаж по його ударах
    devlog-03-cover.png        обкладинка: «DEVLOG 03» у вертикальному ЦЕНТРІ кадру — сітка профілю ріже низ (див. нижче)
    devlog-03-copy.md          опис, хештеги, комент, структура по секундах, примітка про Content ID
  teaser-copy.md             тексти ролика #1 (не публікувався під цим акаунтом)

youtube/
  devlog-01/
    devlog-01-video-music.mp4  що заливати в Shorts: те саме відео + вшитий трек, 27.4 с
    devlog-01-copy.md          назва ≤100 символів, опис, теги, налаштування Studio
    devlog-01-thumbnail.png    та сама обкладинка, як прев'ю Shorts
  devlog-02/
    devlog-02-video-music.mp4  той самий файл, що в TikTok
    devlog-02-copy.md          назва, опис з атрибуцією freetouse.com, теги
    devlog-02-thumbnail.png    та сама обкладинка
  devlog-03/
    devlog-03-video-music.mp4  той самий файл, що в TikTok
    devlog-03-copy.md          назва, опис, теги, налаштування Studio
    devlog-03-thumbnail.png    та сама обкладинка
```

**Правило іменування:** `<випуск>-<що це>.<розширення>`. Назва має читатись без цієї теки —
файл часто відкривають із телефона або з чату, де шляху не видно.

**Чому `source` окремо від `tools`.** Інструмент — те, чим роблять, і він переживає випуски;
сировина — те, з чого роблять цей конкретний ролик. Змішати їх означає щоразу гадати, що
можна чіпати, а що зламає монтаж.

**Платформи окремо, бо файли різні.** У TikTok пішла німа версія — музику він вибирає в
застосунку. Для YouTube трек вшивається у файл: в описі Shorts посилання клікається, а звук
із редактора YouTube не сідає на монтаж по кадру.

## Як вшито музику (DEVLOG 01)

Трек — `source/music/phonk-aggressive-drift-night.mp3`, 139.5 BPM (монтаж різано під 140 —
розбіжність за 27 с 0.09 с, не чутно). 808-й бас входить на **13.67 с** треку, тому відрізок
береться з **10.24 с**: удар сідає рівно на 3.43 с, під білий спалах зі слоу-мо. Побічний
виграш — брейк треку накриває сегмент третьої орбіти, а повернення біту падає на 17.1 с, де
починається «9 THEMES».

```bash
ffmpeg -i tiktok/devlog-01/devlog-01-video.mp4 -ss 10.24 -t 27.4 -i source/music/<трек>.mp3 \
  -filter_complex "[1:a]volume=-4.5dB,afade=t=in:st=0:d=0.15,afade=t=out:st=26.7:d=0.7,\
                   alimiter=limit=0.891[a]" \
  -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -movflags +faststart -shortest <вихід>.mp4
```

`volume` рахується з заміру: `ebur128` на самому відрізку дав −9.5 LUFS, ціль −14 (норма
YouTube) → −4.5 дБ. `alimiter` тримає пік нижче −1 dBTP. Відео не перекодовується (`-c:v copy`).

## Як зібрано DEVLOG 02

```bash
python3 tools/build-devlog-02.py <scratch> devlog-02-video.mp4 devlog-02-cover.png   # німий майстер + обкладинка
ffmpeg -i devlog-02-video.mp4 -ss 20.769 -t 29.53 -i source/music/brazilian-hype-walen.mp3 \
  -filter_complex "[1:a]volume=-5.0dB,afade=t=in:st=0:d=0.12,afade=t=out:st=28.93:d=0.60,\
                   alimiter=limit=0.891:level=false[a]" \
  -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -movflags +faststart -shortest devlog-02-video-music.mp4
```

Драйверу потрібен скомпільований `render-caption` у `<scratch>/video/caption`
(`swiftc -O -o <scratch>/video/caption tools/render-caption.swift`).

Трек — **рівно 130 BPM** (пошук по сітці 128–132 з кроком 0.02: максимум енергії онсетів на
130.00, фаза 0), такт 1.8462 с. Відрізок береться з **20.769 с** (такт 11): один тихий такт, і на
22.615 с (такт 12) повертається бас — у ролику це 1.846 с, зріз «BEFORE → AFTER». Уся секція
до 50.3 с щільна, брейкдаун треку починається на 53-й — до нього не доходимо.

`volume` — із заміру: `ebur128` на відрізку дав −9.0 LUFS, ціль −14 → −5.0 дБ; вийшло −14.1.
**`alimiter … :level=false` обов'язково**: за умовчанням `level=true` і лімітер сам піднімає
сигнал до порога — DEVLOG 01 через це вийшов на −13.1 замість −14.

Дві речі в драйвері, яких не було в першому:

- **Кадрова сітка тактів.** Такт = 55.3846 кадру при 30 fps; `-t 1.846` ріже 55 кадрів, і за
  16 тактів зрізи з'їжджали на 0.11 с. Тепер сегмент задається в тактах, а довжина в кадрах
  рахується від початку ролика (`bars_to_frames`), тож межа такту не пливе.
- **Трекер м'яча.** Крупний план — це `crop`, що їде за м'ячем: позиція шукається по кольору
  гравця з `ThemeManager` (NEON `00E5FF`, SYNTH `FF3EC8`), кластер — найближчий до попереднього
  кадру, згладжування ±4 кадри, і все це стає кусково-лінійним виразом для `crop` (сума
  обрізаних пандусів `clip((t-t0)/dt,0,1)`, без вкладених `if`). Кроп 520×924 — ширший за
  м'яч навмисно: на зовнішньому кільці м'яч доходить до x=60/660, і вужчий кроп упирався в
  край кадру. Для обкладинки трекер не годиться (на одному кадрі нема від чого відштовхнутись) —
  там центроїд майже білих пікселів ядра.

## Як зібрано DEVLOG 03

```bash
# 1 · лабораторна збірка для запису (копія проєкту, не сам проєкт)
rsync -a --exclude build --exclude .gradle --exclude .idea "Orbit Dash/" <scratch>/lab-dev3/
python3 tools/lab-devlog-03.py <scratch>/lab-dev3 && (cd <scratch>/lab-dev3 && sh gradlew assembleDebug -q)
adb install -r <scratch>/lab-dev3/app/build/outputs/apk/debug/app-debug.apk
adb shell setprop debug.od.theme 0; adb shell setprop debug.od.boost 9      # NEON; SYNTH = 4
adb shell monkey -p com.lewydo.orbitdash -c android.intent.category.LAUNCHER 1; sleep 9; adb shell input tap 360 1000
adb shell screenrecord --time-limit 100 --bit-rate 14000000 /sdcard/rec.mp4  # лоадер → одразу ран, автопілот
# ПІСЛЯ запису — повернути проєктну збірку: adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2 · інструменти
swiftc -O -o <scratch>/video/caption tools/render-caption.swift
swiftc -O -o <scratch>/video/rcode   tools/render-code.swift
swiftc -O -o <scratch>/video/rsvg    tools/render-svg.swift

# 3 · німий майстер + обкладинка, потім трек
python3 tools/build-devlog-03.py <scratch> devlog-03-video.mp4 devlog-03-cover.png
ffmpeg -i devlog-03-video.mp4 -i "remix - Katy Perry - Tucked.mp3" \
  -filter_complex "[1:a]volume=-2.5dB,afade=t=in:st=0:d=0.05,afade=t=out:st=29.5:d=0.6,\
                   alimiter=limit=0.891:level=false[a]" \
  -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -movflags +faststart -shortest devlog-03-video-music.mp4
```

**Трек** — власний ремікс автора (Katy Perry — Tucked), 30.14 с, **122.95 BPM**, такт 1.952 с,
такт 0 на 0.128 с. Ролик = трек. **Вступ 0–7.44 — один план без зрізів** (старт рану → попереду
з'являється буст → повільний наїзд → з такту 3 розгін 0.25×), тарілка на **7.44** = торкання
буста (білий спалах), сильна доля дропу на **7.94** = зріз на загальний план і колір заливає
екран; далі зрізи **рівно по долях** (0.488 с; по дві, де спокійніше, по одній — «на-на-на-на»);
брейкдаун без басу 23.55–27.46 (там «як зроблено»), бас повертається на 27.73 під кінцеву
картку. Перша версія різала інтро по синкопованих акцентах і мала bloom — він сказав «не в
такт» і «поганий синій ефект» (розмита яскравість на темно-синьому = блакитна імла); обидва
прибрано. Темп — автокореляція онсетів на дропі (три смуги: full / низ < 150 Гц / верх > 5 кГц,
8 мс), фаза — перебір з кроком ¼ хопа.

`volume` — із заміру: `ebur128` на треку дав −11.5 LUFS, ціль −14 → −2.5 дБ; вийшло
**−14.1 LUFS, пік −1.7 dBTP**. Трек не з бібліотеки: ремікс комерційного треку може отримати
Content ID на YouTube (монетизація правовласнику, можливе блокування в частині країн) і бути
приглушеним у TikTok — рішення автора; альтернатива — трек із freetouse.com, як у DEVLOG 02.

Три речі в драйвері, яких не було в другому:

- **Швидкість для ока.** Гра швидка — **весь геймплей 0.5×** (60 → 30 fps без інтерполяції),
  розгін перед дропом 0.25× (`minterpolate`), 1× лише на картках. Глядач має встигнути
  побачити ефект.
- **Заставка в безпечній зоні.** Сітка профілю TikTok/YouTube показує 9:16 у комірці ~3:4:
  ріже ~240 px зверху й знизу (з 1920) і кладе лічильник переглядів у лівий низ. У DEVLOG 01
  «DEVLOG 01» стояв знизу і обрізався; у DEVLOG 02 обкладинку взагалі не вибрано (у сітці
  кадр «AFTER»). Тепер «DEVLOG 03» — у вертикальному центрі кінцевої картки (y 540–1220),
  і той самий кадр іде окремим PNG. **У TikTok обкладинку вибирати кадром із кінця ролика
  (27.5–29.5 с).**
- **Картки «як зроблено».** SVG із Figma (`render-svg.swift`, прозоре тло), код
  (`render-code.swift`, Menlo з підсвіткою кольорами гри), і крупний план телефона.
- **Підписи — окремим проходом** поверх склейки за абсолютним часом: при зрізах по долі
  підпис, прив'язаний до сегмента, блимав би на кожному зрізі. Склейка — `-c copy`, один
  перекод на весь ролик.

**Запис аналізується по кадрах** (`analyze.py` у скретчпаді, логіка — у драйвері): буст
читається з хекса панелі (540..720 × 90..270) І з тінту нижньої смуги екрана (віньєтка
буста фарбує низ: MAGNET rgb ≈ 32/25/55 проти 9/11/25 без буста). Таймлайн NEON:
MAGNET 4.2 · GEM ×2 13.2 · MAGNET 15.9 · SLOW-MO 22.0 · MAGNET 31.0 · GEM ×2 40.0 · SLOW-MO 48.9.
