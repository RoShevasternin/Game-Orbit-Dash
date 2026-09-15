package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.ScreenUtils
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

/**
 * Scene2D актор з ping-pong ефект пайплайном — INLINE РЕНДЕРИНГ.
 *
 * ─── autoStatic (НОВЕ) ─────────────────────────────────────────────────────
 *
 * Проблема: динамічні VfxGroup (маски прогрес-барів) перемальовували FBO
 * ЩОКАДРУ навіть коли контент не змінювався. Але робити їх повністю static
 * не можна — контент (fill прогресу) анімується.
 *
 * autoStatic = true вирішує:
 *   • кешує результат як static
 *   • АВТОМАТИЧНО перемальовує поки будь-яка дитина має активні Actions
 *   • коли анімація завершилась → кеш, 0 FBO роботи
 *
 * Для прогрес-бара: під час анімації fill → перемальовує; коли прогрес
 * стабільний → один quad. Це прибирає постійну FBO роботу для масок.
 *
 * ─── Втрата GL-контексту ───────────────────────────────────────────────────
 *
 * Після resume() FBO живий як об'єкт, але його colorBufferTexture — новий,
 * а staticRegion тримає стару. Хеш контенту цього не бачить. Тому draw()
 * порівнює FboStack.contextGeneration зі збереженим і при розбіжності скидає
 * кеш; спадкоємці з власними GL-only ресурсами перестворюють їх у
 * onContextLost() (ABlurBack — текстуру знімка).
 */
open class VfxGroup(
    override val screen: AdvancedScreen
) : AdvancedGroup() {

    companion object {
        /** Дно авто-густини як частка екранної. Нижче гине деталь ДІТЕЙ, не блюру. */
        const val DENSITY_FLOOR = 0.5f
    }

    // ─── Ефекти ───────────────────────────────────────────────────────────────

    private val _effects         = mutableListOf<VfxEffect>()
    val effects: List<VfxEffect> = _effects

    fun addEffect(effect: VfxEffect): VfxGroup    { _effects.add(effect); rerenderOnce(); return this }
    fun removeEffect(effect: VfxEffect): VfxGroup { _effects.remove(effect); rerenderOnce(); return this }
    fun clearEffects(): VfxGroup                  { _effects.clear(); rerenderOnce(); return this }

    inline fun <reified T : VfxEffect> getEffect(): T? = effects.filterIsInstance<T>().firstOrNull()

    // ─── Static / autoStatic ───────────────────────────────────────────────────

    open var isStaticEffect = false
        set(value) {
            if (!value) releaseCached()
            needsUpdate = true
            field = value
        }

    fun rerenderOnce() { needsUpdate = true }

    /**
     * autoCache (ON за замовчуванням).
     * Кешує результат FBO і перемальовує ЛИШЕ коли реально щось змінилось:
     *   • transform/колір/видимість дітей (progressImage.x = ..., Actions)
     *   • параметри ефектів (effect.stateKey): колір HSL, progress, lava time
     *
     * Тому безпечно default-on:
     *   • статична маска/blur → кеш, 0 FBO роботи
     *   • лава (time щокадру) → stateKey міняється → авто-перемальовує, НЕ застигає
     *
     * Вимкнути (autoCache = false) тільки якщо група сама керує інвалідацією
     * (напр. ABlurBack зі скріншотом екрану — контент ззовні, хеш не бачить).
     */
    open var autoCache = true
        set(value) { field = value; needsUpdate = true }

    /**
     * Поле під ефекти НАЗОВНІ від меж групи, на бік, у world-юнітах — модель
     * Figma: межі шару = контент, блюр / тінь виходять за них. FBO більший на
     * bleed з кожного боку, результат малюється зі зсувом; hit-box, layout і
     * діти лишаються про контент. 0 — як було.
     *
     * Лишати 0 у ABlurBack і AMask: знімок екрана береться рівно з width×height,
     * а маска лягає на весь буфер разом із полем. bleed — для блюру й світіння
     * над власними дітьми.
     */
    var bleed = 0f
        set(value) {
            if (field == value) return
            field = value
            if (width > 0f && height > 0f) setupCamera()
            needsUpdate = true
        }

    /**
     * Текселів буфера на world-юніт.
     *
     * null (за замовчуванням) — АВТО: максимум із того, що просять ефекти
     * (VfxEffect.preferredDensity()), стеля — екранна густина, і тільки кроком ½
     * від неї (screen, /2, /4, /8 …). Квантування навмисне: VfxPool ніколи не
     * звільняє буфери, тож анімація blur без нього наплодила б bucket на кожен
     * проміжний розмір. Група з одним блюром 40 на 1080p отримує /4 замість
     * повної — у 16 разів менше пам'яті й філу; група з маскою — повну, як було.
     *
     * Явне число — як задано (не вище екранної). Потрібне, коли ефект міняється
     * на льоту і хочеш зафіксувати роздільність під найменше значення.
     */
    var density: Float? = null
        set(value) {
            if (field == value) return
            field = value
            needsUpdate = true
        }

    private var lastCacheKey = Long.MIN_VALUE

    private var cachedFbo : FrameBuffer? = null
    private var pendingFbo: FrameBuffer? = null
    private var needsUpdate              = true

    /** Покоління контексту, під яке зроблений кеш. Стартує з поточного, щоб не скидати даремно. */
    private var contextGen = FboStack.contextGeneration

    private val staticRegion  = TextureRegion()
    private val dynamicRegion = TextureRegion()

    // ─── Pre-allocated (per-instance → вкладеність-safe) ────────────────────────

    private val camera   = OrthographicCamera()
    private val identity = Matrix4().idt()
    private val tmpProj  = Matrix4()
    private val tmpTrans = Matrix4()

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    override fun addActorsOnGroup() { setupCamera() }

    override fun sizeChanged() {
        super.sizeChanged()
        if (width > 0f && height > 0f) { setupCamera(); needsUpdate = true }
    }

    override fun dispose() {
        releaseCached()
        pendingFbo?.let { runCatching { screen.renderPipeline.vfxPool.free(it) } }
        pendingFbo = null
        super.dispose()
    }

    /**
     * Контекст перестворено. Кеш групи вже скинуто; тут спадкоємець перестворює
     * власні GL-only ресурси (текстури знімків тощо). За замовчуванням — нічого.
     */
    protected open fun onContextLost() {}

    // ─── Draw (INLINE) ───────────────────────────────────────────────────────

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) return

        if (_effects.isEmpty()) { super.draw(batch, parentAlpha); return }
        if (stage == null || !isVisible) return

        pendingFbo?.let { screen.renderPipeline.vfxPool.free(it); pendingFbo = null }

        // Контекст перестворено → кешований FBO має нову colorBufferTexture, а
        // staticRegion тримає стару. Хеш контенту цього не бачить — скидаємо руками.
        if (contextGen != FboStack.contextGeneration) {
            contextGen   = FboStack.contextGeneration
            releaseCached()
            lastCacheKey = Long.MIN_VALUE
            needsUpdate  = true
            onContextLost()
        }

        // autoCache: перемальовуємо лише коли змінився контент дітей АБО параметри ефектів
        if (autoCache) {
            val key = contentHash(this) * 1099511628211L + effectsStateKey()
            if (key != lastCacheKey) { needsUpdate = true; lastCacheKey = key }
        }

        // Статичний + кеш готовий + не треба оновлювати → 1 quad, 0 FBO роботи
        if ((isStaticEffect || autoCache) && !needsUpdate && cachedFbo != null) {
            drawResult(batch, staticRegion, parentAlpha)
            return
        }

        val pool = screen.renderPipeline.vfxPool

        // Одна густина на обидві осі: viewport ізотропний, а буфер із різним
        // кроком по X і Y зламав би блюр (u_groupSize рахує крок із bufferW).
        val vp            = stage!!.viewport
        val screenDensity = vp.screenWidth.toFloat() / vp.worldWidth.coerceAtLeast(1f)

        // Термінальний ефект (маска) сам малює у буфер ВИХІДНОЇ роздільності:
        // різкість потрібна його виходу, а не входу. Решта ланцюга працює в
        // дешевому робочому буфері. Такий ефект мусить бути останнім — тиха
        // помилка тут дала б м'який край маски й жодного сліду.
        var terminal: VfxEffect? = null
        for (i in _effects.indices) {
            if (_effects[i].outputDensity() == null) continue
            if (i != _effects.lastIndex) throw IllegalStateException(
                "${this::class.simpleName}: ${_effects[i]::class.simpleName} — " +
                        "термінальний ефект, мусить бути останнім у ланцюгу"
            )
            terminal = _effects[i]
        }

        val workD  = resolveDensity(screenDensity)
        val outD   = terminal?.outputDensity()?.coerceAtMost(screenDensity) ?: workD
        val outerW = width  + bleed * 2f
        val outerH = height + bleed * 2f
        val bufW   = (outerW * workD).toInt().coerceAtLeast(1)
        val bufH   = (outerH * workD).toInt().coerceAtLeast(1)
        val outW   = if (terminal == null) bufW else (outerW * outD).toInt().coerceAtLeast(1)
        val outH   = if (terminal == null) bufH else (outerH * outD).toInt().coerceAtLeast(1)
        val ctx    = VfxContext(outerW, outerH, bufW, bufH, pool)

        tmpProj.set(batch.projectionMatrix)
        tmpTrans.set(batch.transformMatrix)
        val savedR   = batch.color.r
        val savedG   = batch.color.g
        val savedB   = batch.color.b
        val savedA   = batch.color.a
        val savedSrc = batch.blendSrcFunc
        val savedDst = batch.blendDstFunc

        val pingPong = PingPong(pool, bufW, bufH)

        batch.flush()
        FboStack.push(pingPong.dst)
        ScreenUtils.clear(Color.CLEAR, true)

        batch.projectionMatrix = camera.combined
        batch.transformMatrix  = identity
        batch.setBlendFunctionSeparate(
            GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
            GL20.GL_ONE,       GL20.GL_ONE_MINUS_SRC_ALPHA
        )

        drawChildrenWithoutTransform(batch, 1f)

        batch.flush()
        FboStack.pop()
        pingPong.swap()

        val t        = terminal
        val chainEnd = if (t == null) _effects.size else _effects.lastIndex
        for (i in 0 until chainEnd) _effects[i].render(pingPong, ctx)

        // Термінальний: робочий буфер → вихідний (сам піднімає B-сплайном, якщо
        // розміри різні). Розміри збіглися (AMask) — беремо вже орендований dst,
        // зайвого бакета в пулі не з'являється.
        val resultFbo: FrameBuffer
        if (t == null) {
            pool.free(pingPong.dst)
            resultFbo = pingPong.src
        } else {
            val out = if (outW == bufW && outH == bufH) pingPong.dst else pool.obtain(outW, outH)
            t.renderToOutput(pingPong.src, out, ctx)
            pool.free(pingPong.src)
            if (out !== pingPong.dst) pool.free(pingPong.dst)
            resultFbo = out
        }

        // ─── КРИТИЧНО: повернути прив'язку батч-шейдера ─────────────────────────
        // Blit.clearAndRender() викликав shader.bind() (ефект-шейдер, raw GL).
        // SpriteBatch не знає що його GL-програму замінили — наступний flush
        // малюватиме ефект-шейдером (Blit.VERT: gl_Position = a_position → трактує
        // світові координати як NDC → все зникає або лізе в кут екрану).
        //
        // batch.shader getter повертає дефолтний шейдер якщо кастомного нема,
        // тож .bind() перевстановлює саме ту програму яку очікує SpriteBatch.
        // Далі batch.projectionMatrix = tmpProj викличе setupMatrices і поставить
        // u_projTrans на цю (тепер активну) програму.
        batch.shader.bind()

        val region: TextureRegion
        if (isStaticEffect || autoCache) {
            releaseCached()
            cachedFbo   = resultFbo
            updateRegion(staticRegion, resultFbo)
            region      = staticRegion
            needsUpdate = false
        } else {
            pendingFbo = resultFbo
            updateRegion(dynamicRegion, resultFbo)
            region     = dynamicRegion
        }

        batch.projectionMatrix = tmpProj
        batch.transformMatrix  = tmpTrans
        batch.setBlendFunction(savedSrc, savedDst)
        batch.setColor(savedR, savedG, savedB, savedA)

        drawResult(batch, region, parentAlpha)
    }

    private fun drawResult(batch: Batch, region: TextureRegion, parentAlpha: Float) {
        val a       = color.a * parentAlpha
        val prevSrc = batch.blendSrcFunc
        val prevDst = batch.blendDstFunc

        batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(color.r * a, color.g * a, color.b * a, a)
        // Результат ширший за групу на bleed з кожного боку — те саме, що OverflowImage
        val b = bleed
        batch.draw(region,
            x - b, y - b, originX + b, originY + b,
            width + b * 2f, height + b * 2f,
            scaleX, scaleY, rotation)
        batch.setBlendFunction(prevSrc, prevDst)
        batch.setColor(Color.WHITE)
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Густина буфера: явна — як є; авто — найбільша вимога ефектів, округлена
     * ВГОРУ до кроку ½ від екранної. Ефекти без думки (null) не голосують;
     * якщо не голосує ніхто — екранна, як було до патча.
     */
    private fun resolveDensity(screenDensity: Float): Float {
        density?.let { return it.coerceIn(0.01f, screenDensity) }

        var need = 0f
        for (i in _effects.indices) {
            val p = _effects[i].preferredDensity() ?: continue
            if (p > need) need = p
        }
        if (need <= 0f) return screenDensity

        // Ділимо навпіл, поки наступний крок покриває вимогу ефектів І не падає
        // нижче половини екранної. Дно не про блюр, а про ДІТЕЙ: вони
        // растеризуються в цей буфер, і деталь, дрібніша за тексель, гине ще до
        // першого проходу. Заміряно 14.09.2026 на смугах 2 юніти: при ½ екранної
        // RMS проти повної 0.0019, при ¼ — 0.111 (видно оком).
        // Треба нижче — задай density явно: там ти знаєш свій контент.
        val floor = screenDensity * DENSITY_FLOOR
        var d = screenDensity
        while (d * 0.5f >= need && d * 0.5f >= floor) d *= 0.5f
        return d
    }

    private fun setupCamera() {
        // Камера на (−bleed..width+bleed) × (−bleed..height+bleed): діти малюються
        // у своїх координатах, а навколо лишається поле під ефект.
        camera.setToOrtho(false, width + bleed * 2f, height + bleed * 2f)
        camera.position.set(width / 2f, height / 2f, 0f)
        camera.update()
    }

    private fun releaseCached() {
        cachedFbo?.let {
            runCatching { screen.renderPipeline.vfxPool.free(it) }
            cachedFbo = null
        }
    }

    private fun updateRegion(region: TextureRegion, fbo: FrameBuffer) {
        region.setTexture(fbo.colorBufferTexture)
        region.u  = 0f;  region.v  = 1f
        region.u2 = 1f;  region.v2 = 0f
    }

    // Хеш стану піддерева — детектує зміну контенту (позиція/розмір/масштаб/колір/видимість)
    private fun contentHash(group: Group): Long {
        var h = 1125899906842597L
        val kids = group.children
        for (i in 0 until kids.size) {
            val c: Actor = kids[i]
            h = h * 31 + c.x.toRawBits().toLong()
            h = h * 31 + c.y.toRawBits().toLong()
            h = h * 31 + c.width.toRawBits().toLong()
            h = h * 31 + c.height.toRawBits().toLong()
            h = h * 31 + c.scaleX.toRawBits().toLong()
            h = h * 31 + c.scaleY.toRawBits().toLong()
            h = h * 31 + c.rotation.toRawBits().toLong()
            h = h * 31 + c.color.toFloatBits().toRawBits().toLong()
            h = h * 31 + (if (c.isVisible) 1L else 0L)
            // Стан ефектів дочірніх VfxImage/VfxGroup (напр. лава time всередині маски)
            if (c is VfxImage) c.effect?.let { h = h * 31 + it.stateKey() }
            if (c is VfxGroup) {
                for (e in c.effects) h = h * 31 + e.stateKey()
                h = h * 31 + c.bleed.toRawBits().toLong()   // інакше кеш батька не оновиться
            }
            if (c is Group) h = h * 31 + contentHash(c)
        }
        return h
    }

    // Сумарний хеш параметрів усіх ефектів (лава time, HSL колір, progress...)
    private fun effectsStateKey(): Long {
        var h = 1469598103934665603L
        for (i in _effects.indices) h = h * 31 + _effects[i].stateKey()
        return h
    }

}