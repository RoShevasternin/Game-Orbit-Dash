# 52 — Кільця м'яча не малювались: похідна геометрія через `keepScaled`

## Що і навіщо

Після патча 51 кільця над м'ячем не малювались. Причина моя: радіуси й товщини я ставив у
`sizeChanged()` через `toActual`, а `AdvancedGroup.tryInitGroup()` рахує фактор скейлера
лише зі stage; `GameScreen` робить `aBall.setSize(22, 22)` **до** `addActor`, тож
`sizeChanged()` бачив `factor = 1` — `40.toActual = 40` world у кваді 55, обидва кільця за
межами квада. Пізніше stage з'явився, фактор порахувався, `addActorsOnGroup()` викликано —
а `sizeChanged()` більше ні.

Ліки — `keepScaled { }` в `addActorsOnGroup()`: виконується з фактором і переприкладається
на кожен resize. Пастку записано в `CLAUDE.md`, «Одиниці та масштабування».

## Куди

`app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ABall.kt`.

**ДОДАТИ** у кінець `addActorsOnGroup()`, перед `themeSync.sync()`:

```kotlin
        // Похідна геометрія кілець — через keepScaled, а не sizeChanged():
        // GameScreen ставить розмір м'яча ДО addActor, а фактор скейлера
        // рахується лише зі stage — sizeChanged() бачив factor = 1 і виносив
        // обидва кільця за квад. keepScaled виконується тут, уже з фактором,
        // і переприкладається на кожен resize.
        keepScaled {
            fxShield.radius    = SHIELD_R.toActual
            fxShield.thickness = SHIELD_W.toActual
            fxCombo.radius     = COMBO_R.toActual
            fxCombo.thickness  = COMBO_W.toActual
        }
```

**ВИДАЛИТИ** `override fun sizeChanged()` цілком.

## Перевірено

Лабораторія: `assembleDebug` зелений. Пристрій Redmi 220333QNY (2 px/юніт), вісім кадрів,
замір по знімку: кільце комбо r ≈ 44.5 px (22.2 design, SVG 22), товщина ≈ 5 px з AA
(SVG 2.2); кільце щита суцільне на r ≈ 32 px (16, SVG 15.8); супутник між м'ячем і кільцем.
Шейдер без помилок, 60 FPS. `EZ COMBO` вимкнено після знімків.

## Результат вставки (18.09.2026, 17:40)

Обидві зміни — «авто», вставлено; `assembleDebug` зелений; збірка з проєкту поставлена на
Redmi 220333QNY замість лабораторної. Користувач: «топ, мені подобається».
