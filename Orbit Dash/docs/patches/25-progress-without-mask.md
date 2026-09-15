# 25 — Прогрес-бар довільної форми без маски

## Що і навіщо

Питання було: «100 динамічних прогрес-барів, маска дивної форми — як?»

Відповідь: **маска-як-прохід їм не потрібна, і форма тут ні до чого.**

`AMask` існує, щоб обрізати **піддерево сцени** — кілька акторів, які між собою блендяться,
і результат треба зрізати по формі. Для цього й потрібен FBO: інакше нема чого різати.
Прогрес-бар — не піддерево. Це **одна форма плюс одна межа заливки**, тобто чиста
попіксельна арифметика: `alpha = форма`, `колір = заливка або трек, залежно від межі`.
Один прохід, нуль FBO.

«Дивність» форми не коштує нічого: форма — це альфа текстури (атлас, запечений
`VfxTexture`, MSDF), шейдер її не знає. Заливка теж може бути якою завгодно —
горизонталь, радіус, кут, відстань уздовж кривої: це один рядок у шейдері, не окремий
механізм.

## Три способи і коли який

| спосіб | draw call'ів на 100 барів | FBO | коли |
|---|---|---|---|
| `AMask` × N | 100 FBO-проходів + 100 квадів | 100 | **ніколи для списку.** Лише коли під маскою справді піддерево |
| `AProgressShape` × N (`VfxImage`) | 100 draw + 200 флашів батча | 0 | 1–5 барів на екрані, різні кольори в кожного |
| `AProgressBatchGroup` | **1 draw** | 0 | список, сітка, будь-яка кількість |

Чому `AProgressShape` дає 200 флашів: `VfxImage` ставить `batch.shader` на актора і знімає
після — це два флаші на кожного. `AProgressBatchGroup` ставить шейдер **один раз на групу**,
а те, що в кожного своє (прогрес), везе у вершині, в каналі `v_color.r`. Юніформи тоді
однакові на всіх, текстура одна — SpriteBatch збирає все в один виклик.

Ціна батч-варіанта: прогрес квантується у 8 біт (крок 0.4 %), і `v_color.rgb` зайнятий
даними, тобто більше не тінт — колір задається `fillColor` / `trackColor` на групу.

**Про 100 іконок** — там питання не стоїть узагалі: 100 звичайних `Image` з одного атласу
це один draw call, нічого робити не треба. Проблема з'являється, тільки якщо кожній
потрібна своя обрізка; тоді це той самий шейдерний шлях, що й тут, або обрізка на CPU
один раз при завантаженні.

## Файли

| файл | що |
|---|---|
| `assets/shader/ui/progressFS.glsl` | НОВИЙ — форма × межа заливки |
| `utils/vfx/effects/ProgressEffect.kt` | НОВИЙ |
| `actors/progress/AProgressShape.kt` | НОВИЙ — один бар, `VfxImage` |
| `actors/progress/AProgressBatchGroup.kt` | НОВИЙ — N барів, один draw |
| `screens/TestScreen.kt` | стенд: 100 гемів, чотири режими, тап перемикає (ТИМЧАСОВЕ) |

Порядок вставки: 1 → 2 → 3 → 4 → 5. Компілюється після 4.

Чинний `AProgress` (`AMask` + рожевий `Image`, що їде по X) — це рівно перший рядок
таблиці. Для одного бара нормально; коли їх стане багато — переписується на
`AProgressBatchGroup` один в один.

---

## 1. `app/src/main/assets/shader/ui/progressFS.glsl` — НОВИЙ ФАЙЛ

```glsl
#ifdef GL_ES
precision mediump float;
#endif

// ─────────────────────────────────────────────────────────────────────────────
// PROGRESS — форма з текстури + межа заливки. БЕЗ FBO.
//
//   Маска-як-прохід (AMask) існує, щоб обрізати ПІДДЕРЕВО сцени — кілька
//   акторів, які між собою блендяться. Прогрес-бар це не піддерево: це одна
//   форма і одна межа. Тому все рахується тут, на піксель, за один прохід.
//
//   Форма — альфа текстури: атлас, запечений VfxTexture, MSDF — байдуже, шейдер
//   її не знає. «Дивність» форми нічого не коштує.
//
//   u_fromVertex = 1 — прогрес береться з v_color.r замість юніформа. Тоді 100
//   барів з різним прогресом ідуть ОДНИМ draw call: юніформи однакові на всіх,
//   а те, що відрізняється, їде у вершині. Ціна — 8 біт на прогрес (крок 0.4 %)
//   і v_color.rgb більше не тінт: колір беруть u_fillColor / u_trackColor.
// ─────────────────────────────────────────────────────────────────────────────

varying vec4 v_color;
varying vec2 v_texCoords;
varying vec2 v_localUV;

uniform sampler2D u_texture;
uniform float u_progress;      // 0..1, коли u_fromVertex = 0
uniform float u_fromVertex;    // 1 = прогрес із v_color.r
uniform vec3  u_fillColor;
uniform vec3  u_trackColor;
uniform float u_aa;            // згладжування межі, у частках ширини

void main() {
    float shape = texture2D(u_texture, v_texCoords).a;
    float p     = mix(u_progress, v_color.r, u_fromVertex);
    float fill  = smoothstep(p - u_aa, p + u_aa, v_localUV.x);   // 0 = залито, 1 = трек
    vec3  rgb   = mix(u_fillColor, u_trackColor, fill);
    gl_FragColor = vec4(rgb, shape * v_color.a);
}
```

---

## 2. `app/src/main/java/com/lewydo/orbitdash/game/utils/vfx/effects/ProgressEffect.kt` — НОВИЙ ФАЙЛ

```kotlin
package com.lewydo.orbitdash.game.utils.vfx.effects

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// ProgressEffect — форма з текстури + межа заливки, один прохід, без FBO.
//
// Заміна AMask для прогрес-барів: маска-як-прохід потрібна, щоб обрізати
// піддерево сцени, а бар — це одна форма і одна межа, тобто чиста попіксельна
// арифметика. 100 барів через AMask = 100 FBO-проходів; через цей ефект —
// 100 draw call'ів, а з fromVertex = true один.
//
// Форма — альфа будь-якої текстури (атлас, VfxTexture, MSDF).
// ─────────────────────────────────────────────────────────────────────────────
class ProgressEffect : VfxEffect() {

    companion object {
        /** Згладжування межі заливки, у частках ширини бара. */
        const val AA = 0.006f
    }

    override val fragmentShader = "shader/ui/progressFS.glsl"

    /** 0..1. Ігнорується, коли fromVertex = true. */
    var progress = 0.5f

    /**
     * true — прогрес береться з v_color.r кожної дитини, а не з юніформа.
     * Тоді юніформи однакові на всіх, і бари збираються в ОДИН draw call.
     * Ціна: 8 біт на значення і v_color.rgb більше не тінт.
     */
    var fromVertex = false

    val fillColor  = Color.valueOf("4DD9FF")
    val trackColor = Color.valueOf("2A2E45")

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_progress",   progress)
        shader.setUniformf("u_fromVertex", if (fromVertex) 1f else 0f)
        shader.setUniformf("u_fillColor",  fillColor.r,  fillColor.g,  fillColor.b)
        shader.setUniformf("u_trackColor", trackColor.r, trackColor.g, trackColor.b)
        shader.setUniformf("u_aa",         AA)
    }

    override fun stateKey(): Long {
        var k = 17L
        k = k * 31 + progress.toRawBits()
        k = k * 31 + fillColor.toIntBits()
        k = k * 31 + trackColor.toIntBits()
        return k
    }
}
```

---

## 3. `app/src/main/java/com/lewydo/orbitdash/game/actors/progress/AProgressShape.kt` — НОВИЙ ФАЙЛ

```kotlin
package com.lewydo.orbitdash.game.actors.progress

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.effects.ProgressEffect

/**
 * Прогрес-бар довільної форми — один актор, один шейдер, БЕЗ FBO.
 *
 * Форма — альфа регіона: заокруглення з VfxTexture, гем із msdf-атласу, будь-що.
 *
 * Ціна: власний шейдер на актора, тобто два флаші батча на кожен (batch.shader
 * ставиться і знімається в VfxImage.draw). Для одного-двох барів це ніщо. Для
 * списку на сотню — бери AProgressBatchGroup: там шейдер ставиться раз.
 */
class AProgressShape(
    override val screen: AdvancedScreen,
    region: TextureRegion,
) : VfxImage(screen, region, ProgressEffect()) {

    private val fx get() = effect as ProgressEffect

    /** 0..1 */
    var progress: Float
        get()      = fx.progress
        set(value) { fx.progress = value }
}
```

---

## 4. `app/src/main/java/com/lewydo/orbitdash/game/actors/progress/AProgressBatchGroup.kt` — НОВИЙ ФАЙЛ

```kotlin
package com.lewydo.orbitdash.game.actors.progress

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.ProgressEffect

/**
 * N прогрес-барів однієї форми — ОДИН draw call на всіх.
 *
 * Як це працює: шейдер ставиться на батч ОДИН раз на всю групу, юніформи теж
 * одні на всіх, а те, що в кожного своє — прогрес — їде у вершині, в каналі
 * v_color.r. Діти — звичайні Image з одного регіона, тому SpriteBatch збирає їх
 * в один виклик.
 *
 * Чим це відрізняється від AProgressShape: там шейдер і юніформ на КОЖНОГО
 * актора, тож 100 барів = 100 draw call'ів і 200 флашів. Тут — 1 і 0.
 *
 * Ціна: прогрес квантується у 8 біт (крок 0.4 %), а v_color.rgb зайнятий
 * даними й більше не тінт — колір задається fillColor / trackColor на групу.
 */
class AProgressBatchGroup(
    override val screen: AdvancedScreen,
    private val region : TextureRegion,
) : AdvancedGroup() {

    val effect = ProgressEffect().apply { fromVertex = true }

    private val ctx  = VfxContext(1f, 1f, 1, 1)
    private val bars = ArrayList<Image>()

    val size: Int get() = bars.size

    override fun addActorsOnGroup() {}

    /** Додати бар. Повертає індекс для setProgress(). */
    fun addBar(x: Float, y: Float, w: Float, h: Float): Int {
        val img = Image(region).apply {
            setBounds(x, y, w, h)
            color.r = 0f          // ← прогрес живе тут
        }
        addActor(img)
        bars += img
        return bars.size - 1
    }

    /** 0..1 */
    fun setProgress(index: Int, value: Float) { bars[index].color.r = value }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) return

        val sp = effect.batchShader
        batch.shader = sp
        effect.setUniforms(sp, ctx)

        // BATCH_VERT нормалізує a_texCoord0 у v_localUV 0..1 у межах регіона
        sp.setUniformf("u_uvMin", region.u,  region.v)
        sp.setUniformf("u_uvMax", region.u2, region.v2)

        super.draw(batch, parentAlpha)

        batch.shader = null
    }
}
```

---

## 5. `app/src/main/java/com/lewydo/orbitdash/game/screens/TestScreen.kt` — ЗАМІНИТИ ФАЙЛ (стенд, тимчасовий екран)

Форма — гем із msdf-атласу, запечений у звичайну альфа-текстуру: одна на всі 100.
Тап перемикає режим, підпис угорі каже який.

```kotlin
package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.progress.AProgressBatchGroup
import com.lewydo.orbitdash.game.actors.progress.AProgressShape
import com.lewydo.orbitdash.game.actors.vfx.AMask
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import kotlin.math.sin

/**
 * СТЕНД: 100 прогрес-барів дивної форми — чотири способи. Тап перемикає.
 *
 *   0. AMask × 100          — маска як FBO-прохід на кожного (як НЕ треба)
 *   1. AProgressShape × 100 — шейдер на актора, юніформ на актора
 *   2. AProgressBatchGroup  — один шейдер на всіх, прогрес у вершині
 *   3. Image × 100          — просто іконки з атласу, для орієнтира
 *
 * Форма в усіх одна — гем із msdf-атласу, запечений у VfxTexture. «Дивність»
 * форми не коштує нічого: це альфа текстури, шейдер її не знає.
 */
class TestScreen : AdvancedScreen() {

    companion object {
        private const val COLS = 10
        private const val ROWS = 10
        private const val CELL = 34f
        private const val SIZE = 30f
        private const val N    = COLS * ROWS

        private val MODES = listOf(
            "0/4  AMask x100  —  FBO-прохід на кожного",
            "1/4  AProgressShape x100  —  шейдер на актора",
            "2/4  AProgressBatchGroup  —  1 шейдер, 1 draw",
            "3/4  Image x100  —  просто іконки з атласу",
        )
    }

    private val aStarField by lazy { AStarField(this) }

    /** Форма: гем із msdf, запечений у звичайну альфа-текстуру. Одна на всі 100. */
    private val shapeTex = VfxTexture(SIZE, SIZE, gdxGame.assetsMsdf.gem_msdf, gdxGame.assetsMsdf.effect)

    private val msdf  by lazy { gdxGame.msdfManager }
    private val style by lazy { MsdfStyle(msdf, msdf.fontInter_Medium, 11f, Color.WHITE) }
    private val aModeLbl by lazy { AMsdfLabel(MODES[0], style).apply { autoSize = true } }

    private val stage2 = Group()

    private var mode = 0
    private var time = 0f

    private val masks  = ArrayList<Image>()          // діти-заливки режиму 0
    private val shapes = ArrayList<AProgressShape>() // режим 1
    private var batchGroup: AProgressBatchGroup? = null

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        animShowScreen()
        disposableSet.add(shapeTex)
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        add(stage2) { size(COLS * CELL, ROWS * CELL); centerX(); centerY() }
        add(aModeLbl) { startToStart(margin = 14f); topToTop(margin = 62f) }
        addDebugHud(ADebugHud(this@TestScreen))
        buildMode()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mode = (mode + 1) % MODES.size
        buildMode()
        return true
    }

    override fun render(delta: Float) {
        super.render(delta)
        time += delta
        // Кожен бар живе своїм темпом — контент реально динамічний
        when (mode) {
            0 -> for (i in masks.indices)  masks[i].x  = -SIZE + SIZE * p(i)
            1 -> for (i in shapes.indices) shapes[i].progress = p(i)
            2 -> batchGroup?.let { g -> for (i in 0 until g.size) g.setProgress(i, p(i)) }
        }
    }

    private fun p(i: Int) = 0.5f + 0.5f * sin(time * 1.3f + i * 0.31f)

    // ------------------------------------------------------------------------
    // Стенд
    // ------------------------------------------------------------------------
    private fun buildMode() {
        stage2.clearChildren()
        masks.clear(); shapes.clear(); batchGroup = null
        aModeLbl.setText(MODES[mode])

        when (mode) {
            // ── 0. Маска як FBO-прохід на кожного ────────────────────────────
            // Під маскою — суцільна заливка, що їде по X. Саме так зараз
            // зроблено AProgress. Для одного бара нормально, для сотні — ні.
            0 -> repeat(N) { i ->
                val fill = Image(drawerUtil.getTexture(Color.valueOf("4DD9FF"))).apply {
                    setBounds(-SIZE, 0f, SIZE, SIZE)
                }
                val mask = AMask(this@TestScreen, shapeTex.region).apply {
                    setBounds(col(i), row(i), SIZE, SIZE)
                    addActor(fill)
                }
                stage2.addActor(mask)
                masks += fill
            }

            // ── 1. Один шейдер, але свій на кожного актора ───────────────────
            1 -> repeat(N) { i ->
                val bar = AProgressShape(this@TestScreen, shapeTex.region).apply {
                    setBounds(col(i), row(i), SIZE, SIZE)
                }
                stage2.addActor(bar)
                shapes += bar
            }

            // ── 2. Один шейдер на всіх, прогрес у вершині ────────────────────
            2 -> {
                val g = AProgressBatchGroup(this@TestScreen, shapeTex.region).apply {
                    setSize(COLS * CELL, ROWS * CELL)
                }
                repeat(N) { i -> g.addBar(col(i), row(i), SIZE, SIZE) }
                stage2.addActor(g)
                batchGroup = g
            }

            // ── 3. Орієнтир: просто 100 картинок з одного регіона ────────────
            3 -> repeat(N) { i ->
                stage2.addActor(Image(shapeTex.region).apply {
                    setBounds(col(i), row(i), SIZE, SIZE)
                })
            }
        }
    }

    private fun col(i: Int) = (i % COLS) * CELL
    private fun row(i: Int) = (i / COLS) * CELL

    // ------------------------------------------------------------------------
    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.disable()
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }
}
```

---

## Заміряно на пристрої 14.09.2026 (Redmi 24117RN76E, 1080×2400, 60 Hz)

Стенд зі §5: 100 гемів 30×30 у сітці 10×10, у кожного свій прогрес, що міняється **щокадру**.

| режим | FPS | мс | draw | bind | shader switch |
|---|---|---|---|---|---|
| 0. `AMask` × 100 | **14** | 33.3 | 303 | 403 | 207 |
| 1. `AProgressShape` × 100 | **61** | 16.7 | 103 | 103 | 207 |
| 2. `AProgressBatchGroup` | **61** | 16.6 | **4** | **4** | 9 |
| 3. `Image` × 100 (орієнтир) | **61** | 16.6 | 4 | 4 | 7 |

(У draw/bind входить усе на екрані: зоряне тло, HUD, банер. Тобто в режимі 2 сто барів —
це **один** виклик, рівно як сто звичайних картинок у режимі 3.)

Три висновки:

1. **`AMask` × 100 — 14 FPS.** Не «повільно», а непридатно: 300 draw і 400 bind на сотню
   барів, бо кожна маска це свій FBO-прохід (намалювати дитину в буфер, бліт маски, вивести
   результат) і своя FBO-текстура, яка ні з чим не батчиться.
2. **Шейдер на актора (режим 1) — уже 61 FPS**, хоч там 100 draw і 200 перемикань шейдера.
   Тобто головний ворог — не draw call'и, а FBO-проходи. Це той самий висновок, що й у
   патчі 23 («ціна це проходи, не пікселі»), лише з іншого боку.
3. **Батч-група — рівно ціна звичайних картинок.** 100 динамічних барів довільної форми
   коштують стільки ж, скільки 100 статичних іконок: один виклик.

## Як користуватись

Один бар довільної форми:

```kotlin
private val bar = AProgressShape(screen, gdxGame.assetsAll.bar_shape)   // альфа з атласу
...
add(bar) { size(120f, 16f); center() }
bar.progress = 0.7f
```

Список:

```kotlin
private val bars = AProgressBatchGroup(screen, gdxGame.assetsAll.bar_shape).apply {
    effect.fillColor.set(GameColor.cyan)
}
...
val idx = bars.addBar(x, y, 120f, 16f)      // повертає індекс
bars.setProgress(idx, 0.42f)                 // щокадру скільки завгодно разів
```

Форма зі своєї фігури Figma — той самий шлях, що в `docs/msdf-shapes.md`: SVG → msdf-атлас →
запекти `VfxTexture(w, h, msdf.myShape, msdf.effect)` і віддати `.region` у конструктор.
Або просто альфа-PNG в атласі, якщо масштаб фіксований.
