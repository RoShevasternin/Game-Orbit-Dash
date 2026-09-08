# `bleed` рахується сам — задавати нічого не треба

## Що і навіщо

`bleed` — це не смак, а наслідок: скільки текселів розповзається `BlurEffect`, видно з
його ж коду. Чотири проходи по ±4 семпли з кроком `radius` текселів, уздовж осі
складаються проєкції напрямків `1 + 0 + 0.383 + 0.924 = 2.307` — далі
`4 · radius · 2.307` текселів альфа рівно 0. Поділити на `density` — і це юніти.

Тому ефект тепер сам каже, скільки йому треба (`reachTexels()`), а `VfxTexture`
підсумовує ланцюг `post`. У конструкторі `bleed` не вказуєш.

**Явне число потрібне рівно в одному випадку:** параметр ефекту міняється на льоту
(блюр «дихає»). FBO має фіксований розмір, тож там став `bleed` під **максимальний**
радіус.

Перевірено `assembleDebug` у лабораторній копії — зібралось.

---

## 1. `utils/vfx/effects/base/VfxEffect.kt` — ДОДАТИ після `isEnabled`

**Було:**

```kotlin
    open fun stateKey(): Long = 0L
    open val isEnabled: Boolean get() = true
```

**Стало:**

```kotlin
    open fun stateKey(): Long = 0L
    open val isEnabled: Boolean get() = true

    /**
     * Наскільки ефект розповзається ЗА межі свого джерела, у текселях буфера.
     * Потрібно, щоб VfxTexture сам порахував bleed — поле під ефект назовні.
     * 0 = ефект нічого не виносить (маска, тінт).
     */
    open fun reachTexels(): Float = 0f
```

## 2. `utils/vfx/effects/base/BlurEffect.kt` — ДОДАТИ перед `stateKey()`

**Було:**

```kotlin
    override fun stateKey(): Long = radius.toRawBits().toLong()
```

**Стало:**

```kotlin
    /**
     * Чотири проходи по ±4 семпли з кроком radius текселів. Уздовж осі
     * складаються проєкції напрямків: 1 + 0 + 0.383 + 0.924 = 2.307,
     * тобто далі 4·radius·2.307 текселів альфа рівно 0.
     *
     * Це й є bleed, який VfxTexture бере собі: reachTexels() / density юнітів.
     */
    override fun reachTexels(): Float = 4f * radius * 2.307f

    override fun stateKey(): Long = radius.toRawBits().toLong()
```

## 3. `utils/vfx/VfxTexture.kt`

### 3.1 ЗАМІНИТИ параметр `bleed` і додати властивість

**Було:**

```kotlin
    val density: Float            = VfxTextures.DENSITY,
    val bleed  : Float            = 0f,          // поле під post НАЗОВНІ від фігури, на бік
) : Disposable {
```

**Стало:**

```kotlin
    val density: Float            = VfxTextures.DENSITY,
    bleed      : Float?           = null,        // поле під post назовні; null → з ланцюга post
) : Disposable {

    /**
     * Поле під post-ефекти НАЗОВНІ від фігури, на бік, у юнітах.
     *
     * За замовчуванням рахується з ланцюга post — задавати нічого не треба.
     * Явне число потрібне лише коли параметр ефекту МІНЯЄТЬСЯ на льоту: FBO
     * має фіксований розмір, тож став bleed під максимальний радіус.
     */
    val bleed: Float = bleed ?: ceil(post.fold(0f) { acc, e -> acc + e.reachTexels() } / density)
```

### 3.2 ЗАМІНИТИ `outerWidth` / `outerHeight` / `padX` / `padY`

Параметр конструктора `bleed` (тепер `Float?`) перекриває властивість у скоупі
ініціалізаторів — без `this.` не компілюється.

**Було:**

```kotlin
    /** Повний розмір текстури в юнітах: фігура + bleed з обох боків. Для frame-варіанта. */
    val outerWidth  = width  + bleed * 2f
    val outerHeight = height + bleed * 2f

    /** Поле як частка фігури — для OverflowImage. 0 при bleed = 0. */
    val padX get() = if (width  > 0f) bleed / width  else 0f
    val padY get() = if (height > 0f) bleed / height else 0f
```

**Стало:**

```kotlin
    /**
     * Повний розмір текстури в юнітах: фігура + bleed з обох боків. Для frame-варіанта.
     *
     * this.bleed — навмисно явно: параметр конструктора з тим самим іменем
     * (Float?) перекриває властивість у скоупі ініціалізаторів.
     */
    val outerWidth  = width  + this.bleed * 2f
    val outerHeight = height + this.bleed * 2f

    /** Поле як частка фігури — для OverflowImage. 0 при bleed = 0. */
    val padX get() = if (width  > 0f) this.bleed / width  else 0f
    val padY get() = if (height > 0f) this.bleed / height else 0f
```

### 3.3 ЗАМІНИТИ рядок у коментарі класу

**Було:**

```kotlin
//   bleed — поле під post-ефекти НАЗОВНІ від фігури, на бік. FBO = фігура +
//   bleed з обох боків. Без bleed блюру нема куди розпливтись — його зріже.
```

**Стало:**

```kotlin
//   bleed — поле під post-ефекти НАЗОВНІ від фігури, на бік. FBO = фігура +
//   bleed з обох боків. Рахується САМ із ланцюга post; задавати вручну треба
//   лише під ефект, чий параметр міняється на льоту (тоді — під максимум).
```

Далі в тому ж коментарі рядок про «≈ 9 × radius / density юнітів» можна прибрати —
цього більше не рахуєш руками.

### 3.4 ЗАМІНИТИ сигнатуру `resized()`

```kotlin
        bleed  : Float = this.bleed,
    ) = VfxTexture(width, height, base, shape, post, density, bleed)
```
→
```kotlin
        bleed  : Float? = this.bleed,
    ) = VfxTexture(width, height, base, shape, post, density, bleed)
```

Копія успадковує bleed оригіналу. Змінюєш `density` — передай `bleed = null`, щоб
перерахувалось: `radius` у текселях, тож у юнітах ширина світіння залежить від `density`.

## 4. `manager/util/SpriteUtil.kt` — ЗАМІНИТИ приклад

**Було:**

```kotlin
        /**
         * Світіння: та сама фігура, розмита. Фігура 186×101, блюр виходить на
         * bleed назовні. density 1 — світінню роздільність не потрібна, а крок
         * блюру понад 2 текселі дає смуги (docs/msdf-usage.md §4): ширше
         * світіння — нижча density, не більший radius. bleed ≈ 9·2/1 = 18 → 24.
         */
        val aaaGlow = VfxTexture(186f, 101f, base = aaa, shape = effect,
            post = listOf(BlurEffect(radius = 2f)), density = 1f, bleed = 24f)
```

**Стало:**

```kotlin
        /**
         * Світіння: та сама фігура, розмита. Межі — 186×101, блюр виходить
         * назовні; bleed VfxTexture рахує сам (тут 19).
         *
         * radius лишається 2: крок понад 2 текселі дає смуги. Ширину світіння
         * крутиш через density — вона ж і роздільність, якої світінню не треба:
         * density 1 → ~19 юнітів назовні, density 0.5 → ~37, density 2 → ~10.
         */
        val aaaGlow = VfxTexture(186f, 101f, base = aaa, shape = effect,
            post = listOf(BlurEffect(radius = 2f)), density = 1f)
```

---

## Як тепер вибирати числа

**`bleed`** — не чіпаєш. Хочеш подивитись, скільки вийшло — `tex.bleed`.

**`radius`** — завжди `2f`. Крок понад 2 текселі між семплами дає смуги; це вже
міряли (`density 3, radius 6` → шорсткість 0.50; `density 1, radius 2` → 0.00).

**`density`** — ось цим і задаєш ширину світіння. Вона одночасно роздільність, якої
світінню не треба, тож нижча density = ширше й дешевше:

| `density` | світіння назовні | `bleed` (авто) | буфер для фігури 186×101 |
|---|---|---|---|
| `3f` | ~6 юнітів | 7 | 600×345 px |
| `2f` | ~9 | 10 | 412×242 |
| `1f` | ~18 | 19 | 224×139 |
| `0.5f` | ~37 | 37 | 130×88 |

Тобто спершу вирішуєш, **на скільки юнітів має світити**, і береш рядок таблиці.
Загальна формула, якщо `radius` усе-таки не 2: світіння назовні ≈ `9.23 × radius / density`.

## Перевірка

```bash
./gradlew assembleDebug
```

На стенді `glow.debug()` — рамка лишається 186×101, світіння виходить за неї на ~19
і **не обрізане** прямою лінією. Постав `density = 0.5f` — світіння стане вдвічі
ширшим, рамка тією самою.
