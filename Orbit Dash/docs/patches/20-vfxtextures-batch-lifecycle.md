# 20 — `VfxTextures.batch`: «No buffer allocated!» після перестворення Activity

## Що і навіщо

```
GdxRuntimeException: No buffer allocated!
  at IndexBufferObject.bind → Mesh.render → SpriteBatch.flush → SpriteBatch.end
  at VfxTexture.drawBase (VfxTexture.kt:286) ← VfxTextures.update ← GDXGame.render
```

`VfxTextures` — `object`, він переживає `GDXGame`. Його `batch` був `by lazy`:
`GDXGame.dispose()` (через `disposeAll(… VfxTextures …)`) диспозить `SpriteBatch`, але
lazy не скидається. Коли Android знищує Activity, лишаючи процес (довгий фон,
«Don't keep activities»), наступний `GDXGame.create()` бере той самий мертвий батч, і
перший `VfxTexture.update()` падає в `drawBase` → `sb.end()`.

Решта ресурсів у тому ж object після `dispose()` перестворюються правильно: `pool`
(`VfxPool.dispose` чистить обидва списки), `whiteTex` / `emptyTex` (nullable, get-or-create),
`VfxShaderCache` (`cache.clear()`), `Blit` (`mesh = null`). `DENSITY` і `maxTextureSize`
`by lazy` — чисті числа, після нового контексту ті самі. Винуватець один.

Фікс — той самий прийом, що вже стоїть для `whiteTex` / `emptyTex`: nullable поле,
створення на вимогу, `dispose()` обнуляє.

## Що перевірено, а що ні

- Збірка з фіксом проходить; звичайний запуск і `TestScreen` працюють (знімок «після»).
- Відтворити перестворення Activity/фрагмента **через adb не вдалося**: на цьому HyperOS
  ані `always_finish_activities`, ані `font_scale`, ані `wm density`, ані
  `am start --activity-clear-task` не перестворили `MainActivity` (hash `ActivityRecord`
  до/після той самий, `am_on_destroy_called` — нуль). Тому «до/після» на пристрої
  не заміряно — діагноз стоїть на коді: з цим стеком `bufferHandle == 0` дає лише
  `dispose()`, а `VfxTextures.dispose()` кличе лише `GDXGame.dispose()`.
- З event-log: PID 14372 зі стеку стартував 14:39:10 як звичайний процес і жив ≥ 4 хв —
  краш був не на першому кадрі свіжого процесу, що узгоджується з перестворенням
  `GDXGame` усередині живого процесу (найімовірніше — «Apply Changes / Restart Activity»
  з Android Studio, або будь-яке інше перестворення `GDXFragment`).
- `GDXFragment.onCreateView` створює **новий `GDXGame` щоразу** — тож будь-який шлях, де
  фрагмент перестворюється без смерті процесу, веде сюди.

---

## `VfxTextures.kt`

`app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/VfxTextures.kt`

**ЗАМІНИТИ**

```kotlin
    /** Один SpriteBatch на всі бази — малюємо рівно один квад за раз. */
    internal val batch: SpriteBatch by lazy { SpriteBatch(1) }
```

**на**

```kotlin
    /**
     * Один SpriteBatch на всі бази — малюємо рівно один квад за раз.
     *
     * НЕ by lazy. VfxTextures — object і переживає GDXGame: коли Android знищує
     * Activity, лишаючи процес, GDXGame.dispose() диспозить батч, а наступний
     * create() узяв би той самий мертвий об'єкт — «No buffer allocated!» на
     * першому VfxTexture.update(). Тому створюємо на вимогу, а dispose() обнуляє —
     * так само, як whiteTex / emptyTex нижче.
     */
    private var batchOrNull: SpriteBatch? = null
    internal val batch: SpriteBatch
        get() = batchOrNull ?: SpriteBatch(1).also { batchOrNull = it }
```

У `dispose()` **ЗАМІНИТИ**

```kotlin
        runCatching { batch.dispose() }
```

**на**

```kotlin
        batchOrNull?.let { runCatching { it.dispose() } }; batchOrNull = null
```

---

## Як відтворити самому

```bash
adb shell settings put global always_finish_activities 1   # «Don't keep activities»
# запустити гру, вийти на Home, повернутись — Activity перестворюється в тому ж процесі
adb logcat -d | grep "No buffer allocated"
adb shell settings put global always_finish_activities 0   # повернути!
```

## Дотичне

Стенд у `TestScreen.addMsdfSandbox()` створює `VfxTexture` при кожному відкритті екрана
і ніколи не диспозить — реєстр `VfxTextures.all` росте разом із FBO. У грі так не робити:
`VfxTexture` — поле в `SpriteUtil` (живе з грою) або dispose при закритті екрана.
