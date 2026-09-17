# 36 — Ореол поля: alpha не туди, і текстура не потрібна

## Що і навіщо

**Баг: `also` міняє alpha всього поля, а не ореолу.**

```kotlin
private val aHaloImg = Image(gdxGame.assetsMsdf.halo).also { color.a = 0.05f }
```

`also` не перевизначає `this`: усередині лямбди `color` — це `this@AOrbitField.color`, а
ореол доступний лише як `it`. У підсумку прозорість 0.05 отримує **поле**. Від нього
множаться всі діти: кільця, м'яч (`GameScreen:248`) і сутності (`GameScreen:382`). А сам
ореол лишається непрозорим кружком кольору м'яча. `setColorRGB` у `syncRings()` alpha не
чіпає, тож кожен кадр це не виправляє. Правильно — `apply` (там `this` = Image) або
`it.color.a`.

**Текстура `halo_tex` зайва.** `VfxTexture(300f, 300f, circle_msdf, effect)` без `post`
створює FBO 300 × `DENSITY` на бік. На екрані 1080 це ≈900², тобто ~3 МБ пам'яті на один
плаский кружок. За таблицею в CLAUDE.md «одне, велике» малюється через `AMsdfImage`:
0 пам'яті, край різкий на будь-якому розмірі.

**Три орбіти — усе гаразд.** Ореол статичний, 300 у дизайн-юнітах поля. Зовнішня орбіта
має 320 в обох розкладках (`LAYOUT_2` і `LAYOUT_3`), тож ореол завжди на 10 всередині неї.
Glow активної зовнішньої орбіти заходить усередину лише на 7 (14/2), до ореолу не дістає.
Внутрішні кільця (130, 225) лежать поверх ореолу, бо він доданий першим. Під час переходу
2 → 3 ореол не змінюється, і так має бути: зовнішнє кільце стоїть на місці.

## 1. `SpriteUtil.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/manager/util/SpriteUtil.kt`, клас `Msdf`, у кінці.

**ВИДАЛИТИ**

```kotlin

        val halo_tex = VfxTexture(300f, 300f, circle_msdf, effect)
        val halo     = halo_tex.region
```

## 2. `AOrbitField.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/orbit/AOrbitField.kt`

### 2.1 Імпорт

**ЗАМІНИТИ**

```kotlin
import com.badlogic.gdx.scenes.scene2d.ui.Image
```

на

```kotlin
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
```

### 2.2 `companion object`

**ЗАМІНИТИ**

```kotlin
        const val HALO_SIZE = 300f

```

на

```kotlin
        /** Ореол під кільцями: диск кольору м'яча, design. На 10 всередині зовнішньої орбіти (320) в обох розкладках. */
        private const val HALO_SIZE  = 300f
        private const val HALO_ALPHA = 0.05f

```

### 2.3 Actors

**ЗАМІНИТИ**

```kotlin
    private val aHaloImg = Image(gdxGame.assetsMsdf.halo).also { color.a = 0.05f }
```

на

```kotlin
    // apply, не also: в also `color` — це колір ПОЛЯ, і прозорим ставало все поле
    private val aHaloImg = AMsdfImage(screen, gdxGame.assetsMsdf.circle_msdf).apply { color.a = HALO_ALPHA }
```

## Перевірка

`./gradlew assembleDebug`. На екрані: кільця й м'яч мають повну яскравість, під ними —
ледь помітний диск кольору м'яча. Увімкни ORBIT III у дебаг-панелі: диск лишається на
місці, внутрішні кільця лягають поверх нього.
