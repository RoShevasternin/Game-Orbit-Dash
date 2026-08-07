package com.lewydo.orbitdash.game.actors.background

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import kotlin.math.sqrt

// ═════════════════════════════════════════════════════════════════════════════
//  AComet — рідкісна комета поверх зоряного поля.
//
//  ЧОМУ АКТОР, А НЕ ШЕЙДЕР: комета одна і живе секунду-дві. У шейдері вона
//  коштувала б обчислень на КОЖНОМУ пікселі екрана весь час, навіть коли її
//  немає. Актор малює ~14 квадів рівно тоді, коли летить.
//
//  ГОЛОВНЕ ПРО РИТМ: інтервал 18–45 с і поява НЕ одразу. Комета має бути
//  подією, яку помічаєш краєм ока і думаєш «о, здалося?». Якщо літатиме
//  щоп'ять секунд — стане шумом, а фон має заспокоювати, а не смикати.
// ═════════════════════════════════════════════════════════════════════════════
class AComet(override val screen: AdvancedScreen) : AdvancedGroup() {

    companion object {
        private const val SEGMENTS = 10
    }

    /** Межі паузи між кометами, секунди. */
    var minDelay = 18f
    var maxDelay = 45f

    /**
     * Швидкість польоту у world-одиницях за секунду.
     * Для дрібної комети менша: інакше вона просто мигне і зникне.
     */
    var speedMin = 360f
    var speedMax = 560f

    /**
     * Довжина хвоста. Орієнтир — 12–20 діаметрів голови: коротший хвіст
     * читається як «зоря поїхала», довший — знову як великий обʼєкт.
     */
    var tailMin = 26f
    var tailMax = 52f

    /**
     * Радіус голови у world-одиницях.
     *
     * КАЛІБРУВАННЯ ПІД ЗОРІ: у шейдері зоря має радіус 0.012–0.038 клітинки,
     * а клітинка при density = 16 це ≈ 50 world-одиниць → зоря ≈ 1–4 одиниці
     * у діаметрі. Тому 2.2 тут дає голову рівно «на рівні яскравої зорі»,
     * і комета виглядає як зоря, що зірвалася з місця, а не як обʼєкт іншого
     * масштабу. Хочеш помітнішу — 3–4; більше вже інша вагова категорія.
     */
    var headSize = 2.2f

    /** Кожна 5-та комета — «яскрава»: більша і кольорова. */
    var brightChance = 0.2f

    private val glow: TextureRegion by lazy { TextureRegion(gdxGame.assetsLoader.item_glow) }
    private val tint = Color(1f, 1f, 1f, 1f)

    private var timer = 0f
    private var delay = 0f

    private var active = false
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var tail = 120f
    private var size = 9f
    private var life = 0f
    private var lifeMax = 1f
    private var bright = false

    override fun addActorsOnGroup() {
        delay = MathUtils.random(minDelay * 0.35f, maxDelay * 0.6f)   // перша — швидше
    }

    override fun act(delta: Float) {
        super.act(delta)

        if (!active) {
            timer += delta
            if (timer >= delay) launchComet()
            return
        }

        life += delta
        x += vx * delta
        y += vy * delta

        if (life >= lifeMax) {
            active = false
            timer = 0f
            delay = MathUtils.random(minDelay, maxDelay)
        }
    }

    private fun launchComet() {
        active = true
        life = 0f
        timer = 0f
        bright = MathUtils.random() < brightChance

        val speed = MathUtils.random(speedMin, speedMax)
        tail = MathUtils.random(tailMin, tailMax) * (if (bright) 1.5f else 1f)
        size = headSize * (if (bright) 1.6f else 1f)

        // Напрямок завжди вниз, але кут ШИРОКИЙ: від пологого до крутого.
        // Вузький діапазон робив усі комети однаковими.
        val fromLeft = MathUtils.randomBoolean()
        val angle = if (fromLeft) MathUtils.random(-70f, -15f)
        else          MathUtils.random(-165f, -110f)

        vx = MathUtils.cosDeg(angle) * speed
        vy = MathUtils.sinDeg(angle) * speed

        val margin = tail + 40f

        // Старт за межами екрана, щоб комета «влітала», а не зʼявлялась.
        //
        // ПОЛОВИНА — ЗБОКУ. Якщо стартувати лише згори (як було), комета
        // входить у кадр аж на чверті свого життя і встигає показати тільки
        // верхню частину екрана. Бічний старт на випадковій висоті дає
        // прольоти через середину й низ.
        if (MathUtils.randomBoolean(0.45f)) {
            x = MathUtils.random(-0.1f, 1.1f) * width
            y = height + margin
        } else {
            x = if (fromLeft) -margin else width + margin
            y = MathUtils.random(0.15f, 1.0f) * height
        }

        // Живе рівно стільки, скільки треба, щоб вийти за межі — не більше
        lifeMax = timeToExit(margin)
    }

    /** Час до виходу за межі екрана за поточним вектором руху. */
    private fun timeToExit(margin: Float): Float {
        var t = 12f
        if (vx >  1f) t = minOf(t, (width + margin - x) / vx)
        if (vx < -1f) t = minOf(t, (-margin - x) / vx)
        if (vy >  1f) t = minOf(t, (height + margin - y) / vy)
        if (vy < -1f) t = minOf(t, (-margin - y) / vy)
        return t.coerceIn(0.5f, 10f)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (batch == null || !active) return

        val a = color.a * parentAlpha
        if (a <= 0.004f) return

        // Поява і згасання — у СЕКУНДАХ, а не в частках життя. Життя тепер
        // різне (від 0.5 до кількох секунд), і фіксовані частки з'їдали б
        // майже весь політ у коротких комет.
        val fade = MathUtils.clamp(life / 0.15f, 0f, 1f) *
                MathUtils.clamp((lifeMax - life) / 0.35f, 0f, 1f)

        if (bright) tint.set(ThemeManager.current.player) else tint.set(1f, 1f, 1f, 1f)

        val len = sqrt(vx * vx + vy * vy).coerceAtLeast(1f)
        val dx = vx / len
        val dy = vy / len

        val prev = batch.packedColor
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        // Хвіст: сегменти позаду голови, кожен менший і прозоріший
        for (i in SEGMENTS downTo 1) {
            val t = i / SEGMENTS.toFloat()
            val sx = x - dx * tail * t
            val sy = y - dy * tail * t
            val s = size * (1f - t * 0.85f) * 2.6f
            // Дрібна комета потребує трохи вищої альфи, інакше хвіст зникає
            val alpha = a * fade * (1f - t) * (1f - t) * 0.62f
            batch.setColor(tint.r, tint.g, tint.b, alpha)
            batch.draw(glow, sx - s * 0.5f, sy - s * 0.5f, s, s)
        }

        // Голова: широкий німб + гаряче біле ядро
        batch.setColor(tint.r, tint.g, tint.b, a * fade * 0.85f)
        batch.draw(glow, x - size * 1.8f, y - size * 1.8f, size * 3.6f, size * 3.6f)

        batch.setColor(1f, 1f, 1f, a * fade)
        batch.draw(glow, x - size * 0.55f, y - size * 0.55f, size * 1.1f, size * 1.1f)

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.packedColor = prev
    }
}