# Патч 53 — Хвиля, крапки й мікрофриз у точці спійманої іскри

19.09.2026 · 1 → 7. Компілюється після 5; 6–7 — документація.

## Що і навіщо

Остання незроблена частина комбо з прототипу. Там реакція на спійману іскру — п'ять рядків,
із них у нас були зроблені два (напис і бонус), а три — ні:

```
st.freeze = Math.max(st.freeze, 0.03);      // мікрофриз
wave(x, y, 10, 64, 0.4, "#ffffff", 3);      // хвиля
burst(x, y, "#ffffff", 5);                  // крапки
```

Усі числа перенесені один в один, поділені навпіл: у прототипу поле 640 px, у нас 320 юнітів
поля (`TO_FIELD = 0.5`). Тобто хвиля r 5→32 за 0.4 с, товщина 1.5→0.75; крапки — п'ять,
швидкість 30..210 юн/с, життя 0.4..0.9 с, діаметр 3 + 5·life.

### Партикли — не `ParticleEffect`, і ось чому

У проєкті libGDX-емітер **є цілим конвеєром**: `ParticleEffectManager`, `AParticleEffectPool`
з «губернатором», `AParticleEffectActor`, `assets/particle/confetti.p` і атлас `particles.png`
на 386 КБ, який вантажиться на `LoaderScreen`. І при цьому **жоден ігровий код його не
кличе** — це спадок шаблону, мертвий вантаж.

Для п'яти крапок він коштує дорожче, ніж дає:

- атлас партиклів — **друга текстура в батчі**, тобто зайвий bind і draw call поверх усього
  поля, щокадру поки летить;
- `.p`-файл правиться в **десктопному редакторі** — числа перестають лежати поруч із
  прототипом у коді;
- свій пул поверх нашого власного пулу акторів.

Тому `ABurst` малює крапки **тією самою** `msdf.circle` / `msdf.glow`, що й сама іскра: батч
не рветься, малює власний `draw()` по двох `FloatArray` — нуль алокацій у кадрі. Це точно той
самий прийом, що вже працює в `AComet` («актор малює ~14 квадів рівно тоді, коли летить»).
Емітер має сенс, коли партиклів сотні й потрібні криві життя — у нас їх п'ять.

Одну помилку прототипу при цьому виправлено: там тертя `0.98` **за кадр**, тобто прив'язане
до FPS — на 120 Гц крапки долітали б удвічі далі. У нас `0.98^(dt·60)`: на 60 Гц один в один,
на будь-якому іншому — так само.

### Мікрофриз — це не пауза екрана

30 мс, на які завмирає **світ** у момент удару: м'яч стоїть на місці, шипи не рухаються, —
а ефект удару продовжує анімуватись реальним часом. Прийом старий як аркади: ті самі 30 мс
дають відчуття ваги, якого не дає жоден ефект сам по собі.

У нас він вийшов в один рядок, бо рушій **уже** розділяє два годинники: світовий `wdt` (кут,
радіус, спавн, сутності) і реальний `dt` (комбо, бусти, `invuln`). Мікрофриз — це той самий
важіль, що й SLOW-буст, лише до нуля:

```
val ts = if (hitStopT > 0f) 0f else if (slowT > 0f) 0.6f else 1f
```

**Свідоме відхилення від прототипу.** Там `freeze` зупиняє і `update`, і `updateFx`, тож
хвиля з крапками народжуються і **примерзають** на два кадри. Щоб повторити це, довелося б
глушити `stage.act()` — ефект тягнув би руку в рендер екрана заради двох кадрів. У нас спалах
розходиться **із застиглої** картинки, і це виглядає краще, а не гірше.

### Перевірено

- `sh gradlew assembleDebug` + `:engine:test` — **17 тестів, 0 падінь** (новий:
  `caughtSparkFreezesWorldButNotComboTimer` — спіймана іскра морозить світ рівно на два кадри
  при 60 FPS, а `comboT` тікає й у ці кадри).
- На пристрої (Redmi 220333QNY, 2 px на юніт), хвиля застигнута на `f = 0.5` під замір:
  радіус **50.5 px = 25.25 юн** проти розрахункових 25.25, товщина 3.1 px = 1.53 юн проти
  1.5, кільце суцільне — 36 напрямків із 36. Крапки в польоті видно на живому кадрі.

### Куди лягли нові файли

`game/actors/objects/fx/` — новий підпакет, як уже наявний `objects/decor/`. Чому саме там:
хвиля й крапки живуть у координатах **поля** (їх садить `aOrbitField.positionAt`, як шипи й
геми), але сутностями рушія не є — їх не чіпає `syncEntities`, їх запускає подія. Це те саме,
чим уже є `ASpark` у `objects/`, тільки одноразове. У `actors/vfx/` їм не місце: там
обгортки над інструментами (`ABlur`, `AMask`, `AMsdfImage`), а не ігрові ефекти.

---

## Кроки

### 1. `engine/src/main/kotlin/com/lewydo/orbitdash/engine/RunEngine.kt`

Мікрофриз: константа, поле, ts = 0, списання таймера, виклик у nearMiss.

**1.1 · ДОДАТИ** — біля рядка 63.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
        const val ORB_OFF  = 52f
        const val ORB_PICK = 32f

        /**
         * МІКРОФРИЗ: світ стоїть стільки секунд у момент спійманої іскри.
         * 30 мс = два кадри при 60 FPS — рівно стільки, щоб удар відчувся
         * важким, і замало, щоб здатись лагом. Число з прототипу; там же
         * удар шипом 0.04 і смерть 0.08 — обидва ще не перенесені.
         */
        const val HIT_STOP_NEAR = 0.03f

        // ── debug-розстановка ──
```

**1.2 · ДОДАТИ** — біля рядка 268.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
    var announceT = 0f;     private set   // «ORBIT III ONLINE», виду
    var shake     = 0f;     private set   // сила трясіння камери, виду
    var hitStopT  = 0f;     private set   // мікрофриз: поки > 0, світ стоїть

    private val durMul = 1f + 0.1f * config.upBdur
```

**1.3 · ЗАМІНИТИ** — біля рядка 351.

Було:

```kotlin
        // SLOW-MO уповільнює СВІТ (wdt), але не таймери ефектів (dt) —
        // інакше буст тривав би довше просто тому, що він активний.
        val ts  = if (slowT > 0f) 0.6f else 1f
        val wdt = dt * ts

```

Стало:

```kotlin
        // SLOW-MO уповільнює СВІТ (wdt), але не таймери ефектів (dt) —
        // інакше буст тривав би довше просто тому, що він активний.
        //
        // МІКРОФРИЗ — той самий важіль, але до нуля: на 30 мс кут, радіус,
        // спавн і сутності стоять, а таймери (комбо, бусти, invuln) і актори
        // виду живуть далі. Тому хвиля й партикли встигають розійтись, поки
        // світ завмер, — саме це й читається як удар.
        val ts  = if (hitStopT > 0f) 0f else if (slowT > 0f) 0.6f else 1f
        val wdt = dt * ts

```

**1.4 · ДОДАТИ** — біля рядка 375.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
        announceT = max(0f, announceT - dt)
        shake     = max(0f, shake - dt * 1.6f)
        // Списуємо ПІСЛЯ того, як порахований ts: інакше перший же кадр з'їв
        // би 16 мс із 30 ще до фризу, і замість двох кадрів вийшов би один.
        hitStopT  = max(0f, hitStopT - dt)

        // ORBIT III вмикається з 30-ї секунди — посеред рану, звідси й лерп.
```

**1.5 · ДОДАТИ** — біля рядка 632.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
        val b = (5f * comboMult()).toInt()
        bonus += b
        hitStop(HIT_STOP_NEAR)
        listener?.onNearMiss(e, sparkA, b)
    }

    /**
     * Зупинити світ на [sec]. Довший активний фриз не вкорочуємо — дві події
     * в один кадр (іскра і щит) мають дати довшу з двох пауз, а не останню.
     */
    private fun hitStop(sec: Float) {
        hitStopT = max(hitStopT, sec)
    }

```

### 2. `engine/src/test/kotlin/com/lewydo/orbitdash/engine/RunEngineTest.kt`

Тест: світ стоїть два кадри, таймер комбо — ні.

**2.1 · ДОДАТИ** — біля рядка 279.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
    }

    /**
     * МІКРОФРИЗ. Спіймана іскра зупиняє СВІТ на 30 мс — рівно два кадри при
     * 60 FPS (кут і шип стоять), — але таймер комбо тікає реальним часом і в
     * ці кадри. Якби фриз зупиняв і його, пауза мовчки дарувала б вікно комбо.
     */
    @Test
    fun caughtSparkFreezesWorldButNotComboTimer() {
        val e = RunEngine(RunEngine.Config(), seed = 1L)
        check(e.debugSpawnSpike())
        val spike = e.entities.single()

        var t = 0f
        while (e.buildResult().nearMisses == 0 && t < 3f) { e.update(1f / 60f); t += 1f / 60f }
        check(e.buildResult().nearMisses == 1) { "іскру не спіймано" }
        e.tap()                                   // геть від шипа: міряємо фриз, а не смерть

        assertEquals("фриз зведено в кадрі спіймання", RunEngine.HIT_STOP_NEAR, e.hitStopT, 0f)

        var frozen = 0
        repeat(4) {
            val angle0  = e.angle
            val spikeA0 = spike.a
            val comboT0 = e.comboT

            e.update(1f / 60f)

            if (e.angle == angle0) { frozen++; assertEquals("шип теж стоїть", spikeA0, spike.a, 0f) }
            assertEquals("комбо тікає реальним часом", comboT0 - 1f / 60f, e.comboT, 1e-4f)
        }

        assertEquals("30 мс = два кадри по 16.7", 2, frozen)
        assertEquals(RunEngine.Phase.RUN, e.phase)
    }

    /** DEBUG · EZ COMBO: прохід повз шип на сусідньому кільці зараховує без іскри; без прапорця — ні. */
    @Test
```

### 3. `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/fx/AWave.kt`

НОВИЙ. Кільцева хвиля на ProgressRingEffect.

**3.1 · НОВИЙ ФАЙЛ** — увесь файл:

```kotlin
package com.lewydo.orbitdash.game.actors.objects.fx

import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.ProgressRingEffect

// ─────────────────────────────────────────────────────────────────────────────
//  AWave — кільцева хвиля в точці події. Порт wave() з прототипу:
//  радіус росте ease-out, альфа й товщина падають лінійно, життя 0.4 с.
//
//  ЧОМУ ProgressRingEffect, А НЕ НОВИЙ ШЕЙДЕР: хвиля — це та сама обводка кола,
//  що вже малює кільце комбо й щит у ABall, лише з frac = 1 і радіусом, який
//  їде. Новий .glsl додав би третій однаковий шейдер і ще один перемикач у
//  батчі; тут же — ті самі юніформи, той самий білий 4×4 регіон під ними.
//
//  ЧИСЛА — З ПРОТОТИПУ, ПОДІЛЕНІ НАВПІЛ: у нього поле 640 px, у нас 320 юнітів
//  поля (TO_FIELD = 0.5). wave(10, 64, 0.4, 3) → r 5..32, товщина 1.5 → 0.75.
//
//  Межі актора = КВАД під максимальний радіус (як RING_QUAD у ABall): шейдер
//  малює всередині квада, тож він мусить умістити r 32 + півтовщини + AA.
// ─────────────────────────────────────────────────────────────────────────────
class AWave(screen: AdvancedScreen) : VfxImage(
    screen,
    screen.drawerUtil.getRegion(),
    ProgressRingEffect(),
) {

    companion object {
        /** Квад під хвилю: 2×(R_TO + W_FROM/2 + AA 1.5) з запасом. */
        const val QUAD = 72f

        private const val R_FROM = 5f      // 10 engine
        private const val R_TO   = 32f     // 64 engine
        private const val W_FROM = 1.5f    // 3 engine
        private const val W_MIN  = 0.75f   // «+1.5» прототипу — хвиста не тоншає
        private const val LIFE   = 0.4f    // с
        private const val ALPHA  = 0.9f
    }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val fx = effect as ProgressRingEffect

    /** Скільки хвиля вже живе. LIFE і більше — актор вільний. */
    private var time = LIFE

    init {
        fx.frac = 1f          // повне кільце: заповнення тут ні до чого
        isVisible = false
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /** Запустити хвилю з нуля. Позицію ставить викликач ДО цього. */
    fun fire() {
        time = 0f
        isVisible = true
        toFront()
        apply(0f)
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!isVisible) return

        // Реальний час: світ на 30 мс завмирає (RunEngine.hitStopT), а хвиля —
        // ні. Саме через це вона й розходиться із застиглого кадру.
        time += delta
        if (time >= LIFE) { isVisible = false; return }
        apply(time / LIFE)
    }

    // ------------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------------

    /** [f] — прогрес 0..1. Радіус ease-out quad, решта — лінійно, як у прототипі. */
    private fun apply(f: Float) {
        val ease = 1f - (1f - f) * (1f - f)
        fx.radius    = R_FROM + (R_TO - R_FROM) * ease
        fx.thickness = W_FROM * (1f - f) + W_MIN
        color.a      = (1f - f) * ALPHA
    }
}
```

### 4. `app/src/main/java/com/lewydo/orbitdash/game/actors/objects/fx/ABurst.kt`

НОВИЙ. П'ять крапок, власний draw, адитивно.

**4.1 · НОВИЙ ФАЙЛ** — увесь файл:

```kotlin
package com.lewydo.orbitdash.game.actors.objects.fx

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import kotlin.math.pow

// ═════════════════════════════════════════════════════════════════════════════
//  ABurst — розліт білих крапок у точці події. Порт burst() з прототипу.
//
//  ЧОМУ НЕ libGDX ParticleEffect: у проєкті він є (ParticleEffectManager,
//  AParticleEffectPool, particles.atlas), але жоден ігровий код його не кличе,
//  і не дарма. Партикл-емітер — це свій атлас (ще 386 КБ і ЩЕ ОДНА текстура в
//  батчі, тобто зайвий draw call поверх msdf), свій .p-файл, який правиться в
//  десктопному редакторі, і купа параметрів, з яких нам потрібні п'ять чисел.
//  Тут п'ять крапок із ТОЇ САМОЇ msdf-текстури, що й іскра: батч не рветься,
//  числа стоять у коді поруч із прототипом, редактор не потрібен.
//
//  ЧОМУ ВЛАСНИЙ draw(), А НЕ П'ЯТЬ АКТОРІВ: рівно та сама причина, що в AComet
//  («актор малює ~14 квадів рівно тоді, коли летить») — п'ять Image'ів довелося
//  б створювати, класти в лейаут і скидати позиції, а тут два масиви float і
//  жодної алокації в кадрі.
//
//  ЧИСЛА — З ПРОТОТИПУ, ПОДІЛЕНІ НАВПІЛ (поле 640 px → 320 юнітів поля):
//  швидкість 60..420 → 30..210, розмір 3 + 5·life → 1.5 + 2.5·life радіуса.
//  Кут — рівномірно 0..360: розліт НЕ залежить від напряму руху, бо іскра
//  «лопається» на місці, а не бризкає за м'ячем.
// ═════════════════════════════════════════════════════════════════════════════
class ABurst(override val screen: AdvancedScreen) : AdvancedGroup() {

    companion object {
        /** Стеля крапок в одному бурсті. Іскрі треба 5, решта — запас під інші події. */
        private const val MAX = 8

        private const val SPEED_MIN = 30f     // 60 engine
        private const val SPEED_MAX = 210f    // 420 engine
        private const val LIFE_MIN  = 0.4f
        private const val LIFE_MAX  = 0.9f

        /** Діаметр крапки = DOT_BASE + DOT_LIFE·life: гасне і стискається разом. */
        private const val DOT_BASE = 3f       // 6 engine
        private const val DOT_LIFE = 5f       // 10 engine

        /** Ореол навколо крапки — та сама пропорція, що в ASpark (18 на 6). */
        private const val GLOW_K = 3f
        private const val GLOW_A = 0.55f

        /**
         * Тертя прототипу — 0.98 ЗА КАДР, тобто прив'язане до FPS. Переводимо
         * в за-секунду через його ж 60 FPS: на 60 виходить один в один, на 120
         * крапки більше не летять удвічі далі.
         */
        private const val DRAG_PER_FRAME = 0.98f
    }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    //
    //  Масиви, а не список об'єктів: бурст спалахує в найгарячіший момент
    //  кадру, і алокація тут — те саме GC-смикання, від якого пули.
    //
    private val px   = FloatArray(MAX)
    private val py   = FloatArray(MAX)
    private val vx   = FloatArray(MAX)
    private val vy   = FloatArray(MAX)
    private val life = FloatArray(MAX)

    private var count = 0

    /** Регіони беремо ЖИВІ, не копії: після втрати GL-контексту VfxTexture
     *  перестворює текстуру під тим самим region-об'єктом (див. docs/vfx.md). */
    private val dot  get() = gdxGame.assetsMsdf.circle
    private val glow get() = gdxGame.assetsMsdf.glow

    init { isVisible = false }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /** Дітей немає — усе малює draw(). */
    override fun addActorsOnGroup() = Unit

    /** Розсипати [n] крапок із центру актора. Позицію ставить викликач ДО цього. */
    fun fire(n: Int = 5) {
        count = n.coerceIn(1, MAX)
        for (i in 0 until count) {
            val a  = MathUtils.random(0f, MathUtils.PI2)
            val sp = MathUtils.random(SPEED_MIN, SPEED_MAX)
            px[i] = 0f
            py[i] = 0f
            vx[i] = MathUtils.cos(a) * sp
            vy[i] = MathUtils.sin(a) * sp
            life[i] = MathUtils.random(LIFE_MIN, LIFE_MAX)
        }
        isVisible = true
        toFront()
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!isVisible) return

        // Реальний час, як і хвиля: мікрофриз зупиняє світ, не ефекти
        val drag = DRAG_PER_FRAME.pow(delta * 60f)
        var alive = 0

        for (i in 0 until count) {
            if (life[i] <= 0f) continue
            life[i] -= delta
            if (life[i] <= 0f) continue

            px[i] += vx[i] * delta
            py[i] += vy[i] * delta
            vx[i] *= drag
            vy[i] *= drag
            alive++
        }

        if (alive == 0) { count = 0; isVisible = false }
    }

    // ------------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------------
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (batch == null || count == 0) return

        val a = color.a * parentAlpha
        if (a <= 0.004f) return

        // Центр актора — точка події; крапки летять за його межі, як glow у
        // решті об'єктів поля. Координати — батьківські, тому x/y додаємо.
        val cx = x + width * 0.5f
        val cy = y + height * 0.5f

        val prev = batch.packedColor
        // Адитивне змішування — «lighter» прототипу: крапки складаються зі
        // світінням шипа, а не перекривають його. Той самий прийом, що в AComet.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        for (i in 0 until count) {
            val l = life[i]
            if (l <= 0f) continue

            val d  = DOT_BASE + DOT_LIFE * l
            val al = a * l.coerceAtMost(1f)
            val sx = cx + px[i]
            val sy = cy + py[i]

            val g = d * GLOW_K
            batch.setColor(1f, 1f, 1f, al * GLOW_A)
            batch.draw(glow, sx - g * 0.5f, sy - g * 0.5f, g, g)

            batch.setColor(1f, 1f, 1f, al)
            batch.draw(dot, sx - d * 0.5f, sy - d * 0.5f, d, d)
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.packedColor = prev
    }
}
```

### 5. `app/src/main/java/com/lewydo/orbitdash/game/screens/GameScreen.kt`

Пули ефектів, showSparkFx, виклик з onNearMiss.

**5.1 · ДОДАТИ** — біля рядка 19.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
import com.lewydo.orbitdash.game.actors.objects.ASpark
import com.lewydo.orbitdash.game.actors.objects.ASpike
import com.lewydo.orbitdash.game.actors.objects.fx.ABurst
import com.lewydo.orbitdash.game.actors.objects.fx.AWave
import com.lewydo.orbitdash.game.actors.orbit.AOrbitField
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
```

**5.2 · ДОДАТИ** — біля рядка 83.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
        private const val FLOAT_LIFE  = 1.11f  // с
        private const val FLOAT_RISE  = 28f    // юнітів за все життя (25/с × 1.11)

        // ── спалах у точці спійманої іскри (хвиля + крапки) ──
        //  Обидва живуть менше за напис (0.4 і до 0.9 с проти 1.11), тому їх
        //  треба менше: три комбо поспіль за 0.4 с — це вже не гра, а EZ COMBO.
        private const val POOL_WAVES  = 3
        private const val POOL_BURSTS = 3
        /** Квад бурста — точка: крапки малюються за межами, як glow в об'єктах. */
        private const val BURST_SIZE  = 8f
    }

```

**5.3 · ДОДАТИ** — біля рядка 98.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
    // Спливні написи над полем («COMBO x3»). Не пул із поверненням: мітка сама
    // гасне після 1.11 с, а isVisible каже, що її можна взяти під наступну подію.
    // Спалах іскри: хвиля й розліт крапок. Той самий «пул без повернення», що
    // й написи, — актор сам гасне і сам стає вільним (isVisible == false).
    private val aWaves  by lazy { List(POOL_WAVES)  { AWave(this)  } }
    private val aBursts by lazy { List(POOL_BURSTS) { ABurst(this) } }

    private val styleFloat by lazy { MsdfStyle(gdxGame.msdfManager, gdxGame.msdfManager.fontInter_Bold, FLOAT_SIZE) }
    private val aFloats    by lazy {
```

**5.4 · ЗАМІНИТИ** — біля рядка 308.

Було:

```kotlin
        aOrbitField.addActor(aBall)

        // Написи — діти поля: позиція рахується в його ж координатах
        for (f in aFloats) aOrbitField.addActor(f)
    }
```

Стало:

```kotlin
        aOrbitField.addActor(aBall)

        // Ефекти й написи — діти поля: позиція рахується в його ж координатах.
        // Спершу спалах, потім написи: напис має лишатись поверх крапок.
        for (w in aWaves)  { w.setSize(AWave.QUAD, AWave.QUAD); aOrbitField.addActor(w) }
        for (b in aBursts) { b.setSize(BURST_SIZE, BURST_SIZE); aOrbitField.addActor(b) }
        for (f in aFloats) aOrbitField.addActor(f)
    }
```

**5.5 · ЗАМІНИТИ** — біля рядка 394.

Було:

```kotlin
        override fun onOrbit3Online() { log("ORBIT III ONLINE") }
        override fun onNearMiss(e: RunEngine.Entity, sparkAngle: Float, bonus: Int) {
            // Напис стає в точці ІСКРИ, не шипа — саме за цим рушій і віддає кут
            showFloat("COMBO x${engine.multiplier}", e.rr * RunEngine.TO_FIELD, -sparkAngle)
            log("COMBO! +$bonus")
        }
```

Стало:

```kotlin
        override fun onOrbit3Online() { log("ORBIT III ONLINE") }
        override fun onNearMiss(e: RunEngine.Entity, sparkAngle: Float, bonus: Int) {
            // Усе — в точці ІСКРИ, не шипа: саме за цим рушій і віддає кут
            val r = e.rr * RunEngine.TO_FIELD
            showSparkFx(r, -sparkAngle)
            showFloat("COMBO x${engine.multiplier}", r, -sparkAngle)
            log("COMBO! +$bonus")
        }
```

**5.6 · ДОДАТИ** — біля рядка 490.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```kotlin
    }

    /**
     * Спалах на місці спійманої іскри: кільцева хвиля + розліт крапок. Світ у
     * цю мить стоїть (RunEngine.HIT_STOP_NEAR), тож перші два кадри ефект
     * розходиться із застиглої картинки — це й читається як удар.
     *
     * Немає вільного актора — ефект просто пропускаємо, як і напис: обірвати
     * чужий спалах на півдорозі гірше, ніж не показати цей.
     */
    private fun showSparkFx(radiusDesign: Float, angleDeg: Float) {
        aWaves.firstOrNull { !it.isVisible }?.let {
            aOrbitField.positionAt(it, radiusDesign, angleDeg)
            it.fire()
        }
        aBursts.firstOrNull { !it.isVisible }?.let {
            aOrbitField.positionAt(it, radiusDesign, angleDeg)
            it.fire()
        }
    }

    /** HUD читає рушій сам: рахунок, геми рану, комбо, щит. */
    private fun syncHud() {
```

**5.7 · ЗАМІНИТИ** — біля рядка 550.

Було:

```kotlin
    /** Новий ран — усі актори назад у пул, id старого рану більше не існують. */
    private fun releaseAllActors() {
        // Написи минулого рану дограли б поверх нового поля
        for (f in aFloats) { f.clearActions(); f.isVisible = false }

        seenIds.clear()
```

Стало:

```kotlin
    /** Новий ран — усі актори назад у пул, id старого рану більше не існують. */
    private fun releaseAllActors() {
        // Написи й спалахи минулого рану дограли б поверх нового поля
        for (f in aFloats) { f.clearActions(); f.isVisible = false }
        for (w in aWaves)  w.isVisible = false
        for (b in aBursts) b.isVisible = false

        seenIds.clear()
```

### 6. `CLAUDE.md`

Абзац «ще не перенесено» → що тепер є і як воно влаштоване.

**6.1 · ЗАМІНИТИ** — біля рядка 322.

Було:

```markdown
Окремий актор-ореол розглянуто й відкинуто — `docs/decisions.md` §8.

Ще не перенесено з прототипу: хвиля й партикли в точці іскри (зараз там лише напис) і
мікрофриз 30 мс.

## Перед релізом
```

Стало:

```markdown
Окремий актор-ореол розглянуто й відкинуто — `docs/decisions.md` §8.

**Спалах у точці іскри** (патч 53) — три речі разом, усі числа з прототипу, поділені навпіл
(поле 640 px → 320 юнітів): кільцева хвиля `AWave` (r 5→32, 0.4 с, той самий
`ProgressRingEffect`, що й кільця м'яча), розліт п'яти крапок `ABurst` (власний `draw`,
адитивно, msdf-крапка й ореол — як в `AComet`) і **мікрофриз 30 мс**.

Мікрофриз — `RunEngine.hitStopT`: поки він > 0, `ts = 0`, тобто стоїть **світ** (кут, радіус,
спавн, сутності), а таймери на `dt` (комбо, бусти, `invuln`) і актори виду живуть далі. Саме
тому хвиля й крапки встигають розійтись із застиглого кадру — це й читається як удар.
У прототипі фриз зупиняв і ефекти теж (вони «примерзали» на два кадри); ми свідомо цього не
повторюємо — `docs/decisions.md` §8.

Партикли — **не** libGDX `ParticleEffect`: чому саме, там же.

Ще не перенесено з прототипу: звук (`beep` на висоті від комбо) і вібро (`buzz(12)`).

## Перед релізом
```

### 7. `docs/decisions.md`

§8: чому не ParticleEffect і чому фриз не морозить ефекти.

**7.1 · ДОДАТИ** — біля рядка 530.

Стане (жирні рядки — нові, решта — контекст, щоб знайти місце):

```markdown
Не перенесено (наступний крок): супутники навколо м'яча при комбо і дуга таймера комбо.

### Спалах іскри: хвиля, крапки, мікрофриз (19 вересня 2026, патч 53)

Остання незроблена частина комбо з прототипу. Там уся реакція на спіймання — п'ять рядків:
`freeze = 0.03`, `wave(x, y, 10, 64, 0.4, "#ffffff", 3)`, `burst(x, y, "#ffffff", 5)`, попап і
звук. Перенесено перші три, числа поділені навпіл (у прототипу поле 640 px, у нас 320 юнітів).

**libGDX `ParticleEffect` — ВІДКИНУТО.** У проєкті він є цілим конвеєром (`ParticleEffectManager`,
`AParticleEffectPool`, `AParticleEffectActor`, `assets/particle/confetti.p`, атлас
`particles.png` 386 КБ), і жоден ігровий код його не кличе — це спадок шаблону. Для п'яти
крапок він коштує: **другу текстуру в батчі** (атлас партиклів ≠ msdf-атлас, тобто зайвий
draw call і bind поверх усього поля), `.p`-файл, який правиться в десктопному редакторі
замість коду, і шар пулу з «губернатором» поверх нашого власного пулу акторів. Натомість
`ABurst` малює крапки **тією самою** `msdf.circle` / `msdf.glow`, що й іскра: батч не рветься,
числа стоять поруч із прототипом у коментарях, редактор не потрібен. Емітер має сенс там, де
партиклів сотні й потрібні криві життя, — це не наш випадок і не стане ним скоро.

**П'ять акторів на бурст — теж відкинуто**, з тієї ж причини, що в `AComet` («актор малює
~14 квадів рівно тоді, коли летить»): власний `draw()` по двох `FloatArray` не робить жодної
алокації в кадр, а п'ять `Image` довелося б створювати, класти в лейаут і скидати позиції.

**Тертя прототипу — 0.98 за КАДР**, тобто прив'язане до FPS: на 120 Гц крапки долітали б
удвічі далі. Переведено в за-секунду через його ж 60 (`0.98^(dt·60)`) — на 60 Гц один в один.

**Мікрофриз зупиняє світ, але не ефекти — свідоме відхилення від прототипу.** Там головний
цикл під час `freeze` не кличе ні `update`, ні `updateFx`, тож хвиля й крапки народжуються і
**примерзають** на два кадри. У нас рушій уже розділяє світовий час (`wdt`) і реальний (`dt`),
тож фриз — це `ts = 0` в одному рядку, де народжується `wdt`: кут, радіус, спавн і сутності
стоять, а `comboT`, бусти й актори сцени йдуть далі. Повторювати «примерзання» довелося б
глушінням `stage.act()` — тобто ефект тягнув би руку в рендер екрана заради двох кадрів.
Вигляд від цього кращий: спалах розходиться **із застиглої** картинки, а не разом із нею.

Що НЕ робили і чому:

- **Свій шейдер під хвилю** — `ProgressRingEffect` із `frac = 1` це вже воно: та сама обводка
  кола, що в кільці комбо й щиті. Новий `.glsl` дав би третій однаковий шейдер у батчі.
- **Адитивний блендинг для хвилі** — у прототипі `lighter` на обох ефектах. Крапки малюються
  власним `draw()`, тож там це безкоштовно (як в `AComet`); хвиля ж іде через `VfxImage`, і
  перемикання блендингу рвало б батч заради одного тонкого кільця. Біле по темному — різниця
  на око не читається.
- **Тримати `bleed`/`density`** — хвиля не має post-ефектів, це чиста обводка в кваді 72.

Заміряно на пристрої (Redmi 220333QNY, 2 px/юніт, хвиля застигнута на `f = 0.5`): радіус
50.5 px = **25.25 юн** проти розрахункових 25.25, товщина 3.1 px = 1.53 юн проти 1.5,
кільце суцільне — 36 напрямків із 36.

### Кільця й супутники комбо — в `ABall`, а не окремим актором (18 вересня 2026, патч 51)

```
