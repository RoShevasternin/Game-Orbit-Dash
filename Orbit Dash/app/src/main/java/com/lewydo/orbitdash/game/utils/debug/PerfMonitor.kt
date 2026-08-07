package com.lewydo.orbitdash.game.utils.debug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG
import com.lewydo.orbitdash.util.log

// ═════════════════════════════════════════════════════════════════════════════
//  PerfMonitor — лічильники рендеру в одному місці.
//
//  ЧОМУ SINGLETON: GLProfiler чіпляється до глобального GL-контексту. Два
//  екземпляри рахували б одне й те саме двічі і псували б статистику.
//
//  ДВА РІЗНІ РИТМИ:
//    · sample() — ЩОКАДРУ. Лічильники GL накопичуються і мусять зніматися
//      та обнулятися кожен кадр, інакше вони просто сумуються.
//    · знімок для показу — раз на hudInterval. Цифри, що міняються 60 разів
//      на секунду, прочитати неможливо; до того ж setText на MSDF-лейблі
//      щокадру перебудовує розкладку гліфів — сам собі профайлер.
//
//  ПОКАЗУЄМО СЕРЕДНЄ за інтервал плюс максимум кадру: середнє каже про
//  загальний стан, максимум ловить просадки, які в середньому губляться.
//
//  ЩО ОЗНАЧАЮТЬ ЦИФРИ:
//    draw   — скільки разів батч відправив геометрію. Кожен flush() (зміна
//             шейдера, текстури, blend-режиму) додає один. Головна метрика.
//    bind   — перемикання текстур. Багато = атлас працює погано.
//    sh     — перемикання шейдерів. Кожен VfxImage дає щонайменше два.
// ═════════════════════════════════════════════════════════════════════════════
object PerfMonitor {

    private var profiler: GLProfiler? = null

    var isEnabled = false
        private set

    /** Як часто оновлювати показники для HUD і логів, секунди. */
    var hudInterval = 1f

    /** Чи писати той самий знімок у лог. */
    var isLogEnabled = false

    // ── знімок для показу (оновлюється раз на hudInterval) ───────────────────
    var fps        = 0;  private set
    var avgMs      = 0f; private set
    var maxMs      = 0f; private set
    var drawCalls  = 0;  private set
    var binds      = 0;  private set
    var shaders    = 0;  private set
    var glCalls    = 0;  private set

    /**
     * На якому за ліком кадрі інтервалу стався max — і скільки кадрів усього.
     *
     * ДІАГНОСТИКА: якщо maxAt стабільно 1–2, значить сплеск трапляється одразу
     * ПІСЛЯ попереднього знімка — тобто його спричиняє сам монітор (log +
     * setText), а не гра. Якщо номер гуляє випадково — джерело зовнішнє (GC,
     * система, підвантаження).
     */
    var maxAtFrame = 0; private set
    var framesInInterval = 0; private set

    /** Стає true в момент оновлення знімка — HUD оновлює текст лише тоді. */
    var isDirty = false
        private set

    // ── накопичувачі поточного інтервалу ─────────────────────────────────────
    private var accTime   = 0f
    private var accFrames = 0
    private var accMs     = 0f
    private var accMaxMs  = 0f
    private var accMaxAt  = 0
    private var accDraw   = 0
    private var accBinds  = 0
    private var accShader = 0
    private var accGl     = 0

    fun enable(hudInterval: Float = 1f, log: Boolean = false) {
        if (!IS_DEBUG || isEnabled) return
        profiler = GLProfiler(Gdx.graphics).apply { enable() }
        this.hudInterval = hudInterval
        this.isLogEnabled = log
        isEnabled = true
    }

    fun disable() {
        profiler?.disable()
        profiler = null
        isEnabled = false
        resetAccumulators()
    }

    /**
     * Викликати РІВНО ОДИН РАЗ на кадр — це робить ADebugHud.
     *
     * Знімок містить дані попереднього кадру: лічильники накопичуються під
     * час draw(), а act() наступного кадру їх забирає.
     */
    fun sample(delta: Float) {
        val p = profiler ?: return

        val ms = delta * 1000f

        accFrames++
        accTime  += delta
        accMs    += ms
        if (ms > accMaxMs) { accMaxMs = ms; accMaxAt = accFrames }
        accDraw   += p.drawCalls
        accBinds  += p.textureBindings
        accShader += p.shaderSwitches
        accGl     += p.calls

        p.reset()

        if (accTime < hudInterval) { isDirty = false; return }

        val n = accFrames.coerceAtLeast(1)
        fps       = Gdx.graphics.framesPerSecond
        avgMs     = accMs / n
        maxMs     = accMaxMs
        maxAtFrame = accMaxAt
        framesInInterval = n
        drawCalls = accDraw / n
        binds     = accBinds / n
        shaders   = accShader / n
        glCalls   = accGl / n

        resetAccumulators()
        isDirty = true

        if (isLogEnabled) log("PERF: $oneLine")
    }

    private fun resetAccumulators() {
        accTime = 0f; accFrames = 0; accMs = 0f; accMaxMs = 0f; accMaxAt = 0
        accDraw = 0; accBinds = 0; accShader = 0; accGl = 0
    }

    /** Компактний рядок для логів. */
    val oneLine: String
        get() = "fps=$fps avg=${fmt(avgMs)}ms max=${fmt(maxMs)}ms@$maxAtFrame/$framesInInterval " +
                "draw=$drawCalls binds=$binds shader=$shaders gl=$glCalls"

    /** Два рядки для екранного оверлея. */
    val hudText: String
        get() = "$fps FPS • ${fmt(avgMs)} ms • max ${fmt(maxMs)}\n" +
                "draw $drawCalls • bind $binds • sh $shaders"

    // Locale.US — щоб у логах була крапка, а не кома залежно від системи
    private fun fmt(v: Float) = String.format(java.util.Locale.US, "%.1f", v)
}