# Патч 54 — Видалити ParticleEffect і все пов'язане з партиклами

19.09.2026 · 1 → 11. Компілюється після 2; 3–9 — видалення файлів; 10–11 — документація.

## Що і навіщо

Твоє рішення від 19 вересня: партиклів у проєкті не буде — усі ефекти робимо кодом (шейдер,
актор, власний `draw()`), масові калібруємо в тюнері. Цей патч прибирає libGDX
`ParticleEffect` **цілком**: менеджер, утиліту, актор-обгортку, пул із «губернатором»,
`confetti.p` і атлас `particles.png`.

Що це було: спадок шаблону з Idle Merge Cubes. Усе вантажилось на `LoaderScreen` (атлас
1447×878 — 386 КБ на диску і **~5 МБ у відеопам'яті**) і **ніде не використовувалось**: жоден
ігровий код не кликав ні `AParticleEffectActor`, ні `AParticleEffectPool`, `particleEffectAll`
ніде не читався. Тобто видалення нічого не ламає — це підтвердила збірка.

### Що саме прибираємо

| що | де |
|---|---|
| два імпорти, `particleEffectAll`, `particleEffectManager` і його створення | `GDXGame.kt` |
| імпорт, закоментований блок лоадера, завантаження й `init()` | `LoaderScreen.kt` |
| `ParticleEffectManager.kt`, `ParticleEffectUtil.kt` | `game/manager/` |
| `AParticleEffectActor.kt` (221 рядок), `AParticleEffectPool.kt` | `game/actors/particleEffect/` — тека зникає |
| `confetti.p`, `particles.atlas`, `particles.png` | `assets/` |

`SpriteManager` атлас `particles` не знав — його тягнув лише `ParticleEffectManager`, тож
там нічого чіпати. Редактор `gdx-particle-editor.jar` лишається в тебе на диску.

### Документація — правило, а не думка в чаті

`CLAUDE.md` отримує розділ **«Ефекти»**: таблиця «що за ефект → чим», що таке тюнер і як
просити текстуру. `docs/decisions.md` — новий **§9** із причинами і чесною межею тюнера
(канвас ≠ GPU: форма, час, кількість — точно; блюр і адитивність — приблизно).

### Незалежно від патча 53

Обидва патчі правлять `CLAUDE.md` і `decisions.md`, але в **різних місцях** (53 — розділ
«Комбо» і §8, 54 — новий розділ «Ефекти» перед «Втрата GL-контексту» і §9 у кінці файлу).
Вставляти можна в будь-якому порядку.

### Перевірено

Лабораторія без партиклів: `sh gradlew assembleDebug` — успішно, `:engine:test` — 16 із 16.
Згадок `particle` у `app/src/main/java` — нуль.

---

## Кроки

### 1. `app/src/main/java/com/lewydo/orbitdash/game/GDXGame.kt`

Два імпорти, particleEffectAll, менеджер і його створення.

**1.1 · ВИДАЛИТИ** — біля рядка 9.

Було:

```kotlin
import com.lewydo.orbitdash.game.manager.MusicManager
import com.lewydo.orbitdash.game.manager.NavigationManager
import com.lewydo.orbitdash.game.manager.ParticleEffectManager
import com.lewydo.orbitdash.game.manager.SoundManager
import com.lewydo.orbitdash.game.manager.SpriteManager
import com.lewydo.orbitdash.game.manager.util.MusicUtil
import com.lewydo.orbitdash.game.manager.util.ParticleEffectUtil
import com.lewydo.orbitdash.game.manager.util.SoundUtil
import com.lewydo.orbitdash.game.manager.util.SpriteUtil
```

Лишається:

```kotlin
import com.lewydo.orbitdash.game.manager.MusicManager
import com.lewydo.orbitdash.game.manager.NavigationManager
import com.lewydo.orbitdash.game.manager.SoundManager
import com.lewydo.orbitdash.game.manager.SpriteManager
import com.lewydo.orbitdash.game.manager.util.MusicUtil
import com.lewydo.orbitdash.game.manager.util.SoundUtil
import com.lewydo.orbitdash.game.manager.util.SpriteUtil
```

**1.2 · ВИДАЛИТИ** — біля рядка 56.

Було:

```kotlin
    val assetsMsdf   by lazy { SpriteUtil.Msdf() }     // MSDF-фігури; ТІЛЬКИ після initAssets()

    //val particleEffectLoader by lazy { ParticleEffectUtil.Loader() }
    val particleEffectAll by lazy { ParticleEffectUtil.All() }

    // ------------------------------------------------------------------------
    // Audio
```

Лишається:

```kotlin
    val assetsMsdf   by lazy { SpriteUtil.Msdf() }     // MSDF-фігури; ТІЛЬКИ після initAssets()

    // ------------------------------------------------------------------------
    // Audio
```

**1.3 · ВИДАЛИТИ** — біля рядка 76.

Було:

```kotlin
    lateinit var musicManager         : MusicManager          private set
    lateinit var soundManager         : SoundManager          private set
    lateinit var particleEffectManager: ParticleEffectManager private set
    lateinit var msdfManager          : MsdfManager           private set

```

Лишається:

```kotlin
    lateinit var musicManager         : MusicManager          private set
    lateinit var soundManager         : SoundManager          private set
    lateinit var msdfManager          : MsdfManager           private set

```

**1.4 · ВИДАЛИТИ** — біля рядка 123.

Було:

```kotlin
        musicManager          = MusicManager(assetManager)
        soundManager          = SoundManager(assetManager)
        particleEffectManager = ParticleEffectManager(assetManager)
        msdfManager           = MsdfManager()
        navigationManager     = NavigationManager(this)
```

Лишається:

```kotlin
        musicManager          = MusicManager(assetManager)
        soundManager          = SoundManager(assetManager)
        msdfManager           = MsdfManager()
        navigationManager     = NavigationManager(this)
```

### 2. `app/src/main/java/com/lewydo/orbitdash/game/screens/LoaderScreen.kt`

Імпорт, закоментований блок, load() і init().

**2.1 · ВИДАЛИТИ** — біля рядка 6.

Було:

```kotlin
import com.lewydo.orbitdash.game.actors.loader.AMainLoader
import com.lewydo.orbitdash.game.manager.MusicManager
import com.lewydo.orbitdash.game.manager.ParticleEffectManager
import com.lewydo.orbitdash.game.manager.SoundManager
import com.lewydo.orbitdash.game.manager.SpriteManager
```

Лишається:

```kotlin
import com.lewydo.orbitdash.game.actors.loader.AMainLoader
import com.lewydo.orbitdash.game.manager.MusicManager
import com.lewydo.orbitdash.game.manager.SoundManager
import com.lewydo.orbitdash.game.manager.SpriteManager
```

**2.2 · ВИДАЛИТИ** — біля рядка 125.

Було:

```kotlin
            //loadGroups()
        }
//        with(gdxGame.particleEffectManager) {
//            loadableParticleEffectList = mutableListOf(ParticleEffectManager.EnumParticleEffect.LOADER.data)
//            load()
//        }
        gdxGame.assetManager.finishLoading()
        gdxGame.spriteManager.initAll()
//        gdxGame.particleEffectManager.init()
    }

```

Лишається:

```kotlin
            //loadGroups()
        }
        gdxGame.assetManager.finishLoading()
        gdxGame.spriteManager.initAll()
    }

```

**2.3 · ВИДАЛИТИ** — біля рядка 151.

Було:

```kotlin
            load()
        }
        with(gdxGame.particleEffectManager) {
            loadableParticleEffectList = ParticleEffectManager.EnumParticleEffect.entries.map { it.data }.toMutableList()
            load()
        }
    }

```

Лишається:

```kotlin
            load()
        }
    }

```

**2.4 · ВИДАЛИТИ** — біля рядка 161.

Було:

```kotlin
        gdxGame.musicManager.init()
        gdxGame.soundManager.init()
        gdxGame.particleEffectManager.init()
    }

```

Лишається:

```kotlin
        gdxGame.musicManager.init()
        gdxGame.soundManager.init()
    }

```

### 3. `app/src/main/java/com/lewydo/orbitdash/game/manager/ParticleEffectManager.kt`

ВИДАЛИТИ файл.

**3.1 · ВИДАЛИТИ ФАЙЛ.**

### 4. `app/src/main/java/com/lewydo/orbitdash/game/manager/util/ParticleEffectUtil.kt`

ВИДАЛИТИ файл.

**4.1 · ВИДАЛИТИ ФАЙЛ.**

### 5. `app/src/main/java/com/lewydo/orbitdash/game/actors/particleEffect/AParticleEffectActor.kt`

ВИДАЛИТИ файл; тека particleEffect/ стане порожньою — прибрати й її.

**5.1 · ВИДАЛИТИ ФАЙЛ.**

### 6. `app/src/main/java/com/lewydo/orbitdash/game/actors/particleEffect/AParticleEffectPool.kt`

ВИДАЛИТИ файл.

**6.1 · ВИДАЛИТИ ФАЙЛ.**

### 7. `app/src/main/assets/particle/confetti/confetti.p`

ВИДАЛИТИ разом із текою assets/particle/.

**7.1 · ВИДАЛИТИ ФАЙЛ.**

### 8. `app/src/main/assets/atlas/particles.atlas`

ВИДАЛИТИ.

**8.1 · ВИДАЛИТИ ФАЙЛ.**

### 9. `app/src/main/assets/atlas/particles.png`

ВИДАЛИТИ — 386 КБ, ~5 МБ VRAM.

**9.1 · ВИДАЛИТИ ФАЙЛ.**

### 10. `CLAUDE.md`

Новий розділ «Ефекти» перед «Втрата GL-контексту».

**10.1 · ДОДАТИ** — біля рядка 214.

Стане (нові рядки серед контексту):

```markdown
альфою, а `unpremulFS.glsl` наприкінці повертає пряму.

## Ефекти

libGDX `ParticleEffect` у проєкті **немає** (видалено 19 вересня 2026, патч 54: менеджер, пул,
`.p`-файли, атлас `particles.png`) — і не повертати. Чому й де межа — `docs/decisions.md` §9.
Усі ефекти — код: шейдер, актор або власний `draw()`.

| що за ефект | чим |
|---|---|
| форма будь-якого розміру: кільце, хвиля, дуга, обводка, світіння | шейдер / msdf (`AWave`, `ProgressRingEffect`, `VfxTexture`) |
| десятки частинок, залежних від стану гри (тема, напрям, множник) | актор із власним `draw()` по `FloatArray` (`ABurst`, `AComet`) |
| масове й декоративне (смерть, конфеті, салют) | те саме, масивів більше; **числа калібруються в тюнері** |

**Тюнер** — сторінка на claude.ai, як сторінка-патч: канвас малює ефект тією самою математикою,
що й актор, повзунки підписані іменами констант Kotlin, «Готово» публікує `state.values` у саму
сторінку. Калібрування робиться там, Claude переносить числа в патч один в один. HTML тюнерів —
`docs/tuners/`. Канвас ≠ GPU: форма, час, кількість, швидкості — точно; блюр і адитивне
змішування — приблизно, добиваються на пристрої одним коефіцієнтом.

**Текстура для ефекту** — у патчі заглушка (`msdf.circle`, білий квад) і рівно що потрібно:
розмір у px, біла на прозорому чи кольорова, SVG для msdf-атласу чи PNG.

## Втрата GL-контексту

```

### 11. `docs/decisions.md`

§9 «Ефекти» у кінець файлу.

**11.1 · ДОДАТИ** — біля рядка 556.

Стане (нові рядки серед контексту):

```markdown
(`OrbitRingEffect`), тож `ProgressRingEffect` — той самий механізм із заповненням `frac`;
щит — те саме кільце з `frac = 1`. AA торця заповнення — з тієї ж `u_aa`, що й краї кільця.

---

## 9. Ефекти

### `ParticleEffect` — ВИДАЛЕНО (19 вересня 2026, патч 54)

Що було: `ParticleEffectManager`, `ParticleEffectUtil`, `AParticleEffectActor` (221 рядок),
`AParticleEffectPool` з «губернатором», `assets/particle/confetti/confetti.p` і атлас
`particles.png` 1447×878 (386 КБ на диску, ~5 МБ у відеопам'яті) — усе це вантажилось на
`LoaderScreen` і **ніде не використовувалось**: спадок шаблону з Idle Merge Cubes.

Рішення: усі ефекти — код. Не тому, що емітер поганий, а тому, що для цієї гри він програє в
обох випадках, де міг би виграти:

- **Дрібне й ігрове** (іскра, хвиля, щит, підбір гема): це форми й стан гри. Форму шейдер
  малює різко на будь-якому розмірі (у Idle Merge Cubes `wave.p` — растрове кільце, розтягнуте
  до 523 px, розмите); стан (тема, множник, напрям) у `.p` запечений, у коді — один рядок.
- **Масове й декоративне** (смерть на 46 частинок, конфеті): єдина справжня перевага редактора —
  тюнити оком, повзунками. Її забирає **тюнер** — сторінка на claude.ai з канвасом, який рахує
  ефект тією самою математикою, що й актор, і повзунками, підписаними іменами констант Kotlin.
  Числа переносяться в патч один в один. Рендер лишається актором із `FloatArray` — нуль
  алокацій, та сама msdf-текстура, батч не рветься.

Ціна, якої більше немає: другий атлас у батчі (зайвий bind і draw call поверх усього поля, поки
летить), `.p` у десктопному редакторі замість чисел поруч із прототипом, окремий пул поверх
нашого пулу акторів, ~5 МБ VRAM на лоадері.

Що тюнер **не** дає, і це записано чесно: канвас у браузері ≠ GPU телефона. Форма, таймінг,
кількість, швидкості, кольори збігаються, бо це числа; блюр і адитивне змішування — приблизно.
Цикл: тюнер → патч → телефон → якщо «світіння тьмяніше, ніж у тюнері» — один коефіцієнт.

Текстури під ефект: у патчі заглушка (`msdf.circle`, білий квад) плюс точна вимога — розмір,
біла/кольорова, SVG чи PNG; після передачі — підміна одним рядком.

Редактор `gdx-particle-editor.jar` лишається на диску; у проєкт не повертається.
```
