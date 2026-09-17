# 32 — Пульс бустера на два кільця: хвиля зсередини назовні

## Що і навіщо

Звірив із Figma (`boost_hex`, компонент `5980:72`), у `glow` два плоскі кола без блюру:

| шар | розмір | alpha | у коді |
|---|---|---|---|
| `v1` | 70 × 70 | 0.10 | `aGlow1`, `GLOW_1_SIZE = 70f`, `color.a = 0.10f` ✓ |
| `v2` | 50 × 50 | 0.18 | `aGlow2`, `GLOW_2_SIZE = 50f`, `color.a = 0.18f` ✓ |

Статика вже один в один. (У Figma `v2` зсунуте на 1 px ліворуч — це випадковий nudge, не
задум; у коді обидва центровані, так і лишаємо.)

Анімації в макеті немає, тож далі — моя пропозиція, а не «як у Figma». Зараз обидва
кільця масштабуються **одним** `k` — це те саме одне коло, що дихає, просто намальоване
двічі; другого кільця в русі не видно. Два концентричні кола — це готовий «радар»: якщо
зовнішнє **відстає на чверть періоду** і на піку розширення трохи **гасне**, око читає хвилю,
що розходиться від бустера. Це саме той сигнал, який має нести підбираний об'єкт: «іди сюди».

Спокій (`p = 0`) = точні числа з макета; амплітуда коливається довкола них, тож у середньому
кадрі бустер виглядає як у Figma.

Два регулятори, якщо не сподобається на пристрої:
- `PULSE_LAG = 0f` — кільця в фазі, як зараз;
- `PULSE_FADE = 0f` — лише масштаб, без згасання.

Один файл, три правки.

---

## `ABooster.kt`

**Файл:** `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/ABooster.kt`

### 1. `companion object`

**ЗАМІНИТИ**

```kotlin
    companion object {
        private const val GLOW_1_SIZE = 70f
        private const val GLOW_2_SIZE = 50f

        private const val PULSE_SPEED = 6f
        private const val PULSE_AMP   = 0.12f
    }
```

на

```kotlin
    companion object {
        // Figma boost_hex → glow: два плоскі кола, без блюру
        private const val GLOW_1_SIZE  = 70f      // v1
        private const val GLOW_1_ALPHA = 0.10f
        private const val GLOW_2_SIZE  = 50f      // v2
        private const val GLOW_2_ALPHA = 0.18f

        private const val PULSE_SPEED = 6f
        private const val PULSE_AMP   = 0.12f
        /** Зовнішнє кільце відстає на чверть періоду — хвиля йде зсередини назовні. 0 = у фазі. */
        private const val PULSE_LAG   = (PI / 2).toFloat()
        /** Наскільки кільце гасне на піку розширення. 0 = лише масштаб, без згасання. */
        private const val PULSE_FADE  = 0.4f
    }
```

Імпорт: до `import kotlin.math.sin` **ДОДАТИ** `import kotlin.math.PI`.

### 2. Актори — alpha з констант

**ЗАМІНИТИ**

```kotlin
    private val aGlow1  = Image(gdxGame.assetsMsdf.circle).apply { color.a = 0.10f }
    private val aGlow2  = Image(gdxGame.assetsMsdf.circle).apply { color.a = 0.18f }
```

на

```kotlin
    private val aGlow1  = Image(gdxGame.assetsMsdf.circle).apply { color.a = GLOW_1_ALPHA }
    private val aGlow2  = Image(gdxGame.assetsMsdf.circle).apply { color.a = GLOW_2_ALPHA }
```

### 3. `act()` + новий хелпер

**ЗАМІНИТИ** тіло `act()`

```kotlin
    override fun act(delta: Float) {
        super.act(delta)

        // Пульсація glow — буст «дихає», щоб виділятись серед статичних гемів
        pulseT += delta * PULSE_SPEED
        val k = 1f + PULSE_AMP * sin(pulseT)
        aGlow1.setScale(k)
        aGlow2.setScale(k)
    }
```

на

```kotlin
    override fun act(delta: Float) {
        super.act(delta)

        // Хвиля зсередини назовні: внутрішнє кільце веде, зовнішнє наздоганяє
        // на чверть періоду — буст «розходиться», а не просто дихає
        pulseT += delta * PULSE_SPEED
        pulse(aGlow2, GLOW_2_ALPHA, sin(pulseT))
        pulse(aGlow1, GLOW_1_ALPHA, sin(pulseT - PULSE_LAG))
    }
```

І **ДОДАТИ** в секцію `// Apply` (після `applyInfo()`):

```kotlin
    /**
     * Одна фаза кільця. p ∈ [-1, 1]: на +1 кільце найширше і найтьмяніше,
     * на -1 — стиснуте і найяскравіше. Спокій (p = 0) — точні числа з макета.
     */
    private fun pulse(ring: Image, baseAlpha: Float, p: Float) {
        ring.setScale(1f + PULSE_AMP * p)
        ring.color.a = baseAlpha * (1f - PULSE_FADE * p)
    }
```

`applyInfo()` не чіпаємо: `setColorRGB` міняє лише RGB, alpha лишається за `pulse()`.

---

## Перевірка

Збірка + пристрій. Дивитись на бустер у русі: зовнішнє кільце має «наздоганяти» внутрішнє,
а не рухатись з ним синхронно. Якщо здається надто неспокійним — спершу `PULSE_FADE = 0f`,
потім `PULSE_LAG = 0f`; менше — і буде як було.
