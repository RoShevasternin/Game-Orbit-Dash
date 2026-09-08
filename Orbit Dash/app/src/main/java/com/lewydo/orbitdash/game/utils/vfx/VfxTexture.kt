package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MsdfShapeEffect
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect
import kotlin.math.ceil

// ─────────────────────────────────────────────────────────────────────────────
// VfxTexture — живе спільне джерело текстури.
//
// Не актор. Володіє одним виділеним FBO, малює в нього базу (білий квад або
// будь-який регіон) через shape-ефект, проганяє post-ефекти й віддає
// СТАБІЛЬНИЙ region. Скільки завгодно Image тримають цей регіон і батчаться
// між собою. Змінив параметр ефекту — наступним кадром оновились усі.
//
//   val glow = VfxTexture(84f, 84f, shape = CircleEffect().apply { radius = 20f; blur = 22f })
//   Image(glow.region)                              // ×N — один draw call
//   glow.effect<CircleEffect>()?.blur = 30f         // усі N оновляться
//
// ─── shape і post — дві різні категорії ефектів ─────────────────────────────
//   shape — МАЛЮЄ форму: Circle / Polygon / RoundRect / Gradient. Працює через
//           SpriteBatch і BATCH_VERT, читає v_localUV. Рівно один.
//   post  — ОБРОБЛЯЄ картинку: Blur / Mask. Працює через Blit і Blit.VERT,
//           читає u_texture. Ланцюг, у порядку списку.
//   Шейдер із v_localUV під Blit.VERT не злінкується — поплутати не вийде тихо.
//
// ─── Розмір і bleed — модель Figma ──────────────────────────────────────────
//   width/height — розмір ФІГУРИ у world-юнітах, як у Figma: квад бази
//   розширюється на msdf-поле, і фігура лягає рівно в (0,0)-(width,height).
//   bleed — поле під post-ефекти НАЗОВНІ від фігури, на бік. FBO = фігура +
//   bleed з обох боків. Рахується САМ із ланцюга post; задавати вручну треба
//   лише під ефект, чий параметр міняється на льоту (тоді — під максимум).
//   Msdf-поле бази при bleed = 0 вилітає за FBO; при bleed > 0 лягає в поле
//   і лишається прозорим завдяки discard у msdf-шейдері.
//   Скільки треба: ≈ 9 × radius / density юнітів (чотири проходи по 4 кроки
//   по radius текселів); краще з запасом.
//
//   Малювати:
//     image()                       — шар: межі = фігура, ефект назовні
//     Image(region) outerW × outerH — frame, що обіймає ефект (для вирівнювання)
//
//   Це РОЗДІЛЬНІСТЬ текстури: density пікселів на юніт, регіон розтягнеться
//   під актора.
//
// ─── Коли рендер ────────────────────────────────────────────────────────────
//   Конструктор GL не чіпає — лише реєструє. FBO створюється й малюється у
//   VfxTextures.update() на початку наступного кадру. До того region вказує на
//   прозорий 1×1 — Image, створений раніше, просто нічого не намалює один кадр.
//   Перемальовування — тільки коли змінився stateKey() ефектів, викликано
//   invalidate(), або зріс FboStack.contextGeneration.
// ─────────────────────────────────────────────────────────────────────────────
class VfxTexture(
    val width  : Float,                          // розмір ФІГУРИ, як у Figma
    val height : Float,
    base       : TextureRegion?   = null,        // null → білий квад
    val shape  : VfxEffect?       = null,        // малює базу
    val post   : List<VfxEffect>  = emptyList(), // обробляє результат
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

    /** Стабільний регіон: той самий об'єкт назавжди, вміст під ним оновлюється. */
    val region = TextureRegion(VfxTextures.emptyTexture)

    /** База. Зміна → перемалювання наступним кадром. */
    var base: TextureRegion? = base
        set(value) { field = value; invalidate() }

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

    private val bufW = ceil(outerWidth  * density).toInt().coerceAtLeast(1)
    private val bufH = ceil(outerHeight * density).toInt().coerceAtLeast(1)

    /** Контекст post-ефектів — увесь буфер, разом із bleed. */
    private val ctx      = VfxContext(outerWidth, outerHeight, bufW, bufH)
    /** Контекст shape-ефекту — сама фігура: u_size у RoundRectEffect має бути її розміром. */
    private val shapeCtx = VfxContext(width, height, bufW, bufH)

    private val proj = Matrix4()

    private var fbo       : FrameBuffer? = null
    private var lastKey    = Long.MIN_VALUE
    private var contextGen = FboStack.contextGeneration

    init { VfxTextures.register(this) }

    // ─── API ─────────────────────────────────────────────────────────────────

    /** Знайти ефект за типом — спершу shape, потім post. */
    inline fun <reified T : VfxEffect> effect(): T? =
        (shape as? T) ?: post.firstOrNull { it is T } as? T

    /** Змусити перемалювати наступним кадром (напр. після зміни того, чого stateKey не бачить). */
    fun invalidate() { lastKey = Long.MIN_VALUE }

    /**
     * Актор у моделі Figma: межі = фігура width×height, bleed з ефектом виходить
     * назовні. Потрібна рамка, що обіймає ефект, — звичайний Image(region)
     * розміром outerWidth × outerHeight.
     */
    fun image() = OverflowImage(region, padX, padY)

    /**
     * Та сама фігура й ті самі ефекти — ІНША роздільність. Для випадку «в
     * SpriteUtil запечено 48, а тут треба 160»: копія під потрібний розмір,
     * оригінал не чіпається.
     *
     * Ефекти ДІЛЯТЬСЯ (ті самі інстанси): змінив blur в одному — оновились
     * обидва наступним кадром. Тому й resized, а не copy. Потрібна незалежна —
     * збери через конструктор з новими інстансами ефектів.
     *
     * Це нова текстура зі своїм FBO і своєю пам'яттю — не викликай у циклі
     * чи в draw(); тримай як поле, як і оригінал.
     */
    fun resized(
        width  : Float = this.width,
        height : Float = this.height,
        density: Float = this.density,
        bleed  : Float? = this.bleed,
    ) = VfxTexture(width, height, base, shape, post, density, bleed)

    // ─── Update (з VfxTextures.update(), ДО сцени) ───────────────────────────

    internal fun update() {
        // Контекст перестворено → FBO робимо заново: так само, як ShapeAtlas робив
        // reload(). Дешево, і не залежить від того, наскільки акуратно драйвер
        // відновив старий.
        if (contextGen != FboStack.contextGeneration) {
            contextGen = FboStack.contextGeneration
            releaseFbo()
        }

        val key = stateKey()
        if (fbo != null && key == lastKey) return

        render(fbo ?: createFbo())
        lastKey = key
    }

    private fun stateKey(): Long {
        var h = 1469598103934665603L
        h = h * 31 + (shape?.stateKey() ?: 0L)
        for (i in post.indices) h = h * 31 + post[i].stateKey()
        return h
    }

    private fun createFbo(): FrameBuffer =
        FrameBuffer(Pixmap.Format.RGBA8888, bufW, bufH, false).also {
            it.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            fbo = it
            // Текстура FBO лежить «догори ногами» відносно звичайних картинок —
            // перевертаємо регіон по вертикалі (як VfxGroup і ShapeAtlas).
            region.setTexture(it.colorBufferTexture)
            region.setRegion(0, 0, bufW, bufH)
            region.flip(false, true)
        }

    private fun releaseFbo() {
        fbo?.let { runCatching { it.dispose() } }
        fbo = null
    }

    // ─── Render ──────────────────────────────────────────────────────────────

    private fun render(target: FrameBuffer) {
        if (post.isEmpty()) {
            // Пряма альфа одразу в наш FBO — те, що очікує звичайний Image.
            drawBase(target, premultiplied = false)
            return
        }

        // З post-ефектами: усередині ланцюга — премультиплікована альфа (інакше
        // блюр дає темну облямівку), наприкінці unpremul повертає пряму.
        val pp = PingPong(VfxTextures.pool, bufW, bufH)
        drawBase(pp.dst, premultiplied = true)
        pp.swap()
        for (i in post.indices) post[i].render(pp, ctx)
        Blit.blit(pp.src, target, VfxTextures.unpremulShader)
        pp.free()
    }

    /**
     * Намалювати базу в target одним квадом. Без blending — щоб (1,1,1,α) від
     * shape-шейдера лягло як є; з premultiplied — окремий blend для rgb і a,
     * як у VfxGroup, щоб далі ланцюг працював коректно.
     */
    private fun drawBase(target: FrameBuffer, premultiplied: Boolean) {
        val sb  = VfxTextures.batch
        val src = base ?: VfxTextures.white
        val fx  = shape

        FboStack.push(target)
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // Проєкція у world-юнітах: квад (0,0)-(width,height) заповнює FBO цілком,
        // а шейдер отримує u_size у тих самих юнітах, що й у VfxImage.
        // Проєкція у world-юнітах: фігура — квад (0,0)-(width,height), навколо
        // неї bleed. Разом вони заповнюють FBO цілком.
        proj.setToOrtho2D(-bleed, -bleed, outerWidth, outerHeight)
        sb.projectionMatrix = proj
        if (premultiplied) {
            sb.enableBlending()
            sb.setBlendFunctionSeparate(
                GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE,       GL20.GL_ONE_MINUS_SRC_ALPHA
            )
        } else {
            sb.disableBlending()
        }
        sb.shader = fx?.batchShader
        sb.begin()
        if (fx != null) {
            val sp = fx.batchShader
            fx.setUniforms(sp, shapeCtx)   // u_size = фігура, не весь буфер
            // UV бази → BATCH_VERT нормалізує до v_localUV 0..1 (як VfxImage)
            sp.setUniformf("u_uvMin", src.u,  src.v)
            sp.setUniformf("u_uvMax", src.u2, src.v2)
        }
        // MSDF-регіон несе поле pxRange/2 текселів з кожного боку. Малюємо квад
        // БІЛЬШИМ за FBO рівно на це поле: воно вилітає за viewport і обрізається,
        // а в текстуру лягає сама фігура, край у край. Те саме, що OverflowImage
        // робить у AMsdfImage; без цього у 186×101 запеклася б фігура 163×80.
        // Множник по осях різний — клітинка може бути несиметричною (64×39).
        val px = if (base == null) 0f else (fx as? MsdfShapeEffect)?.pxRange ?: 0f
        if (px > 0f) {
            val mx = width  * 0.5f * px / (src.regionWidth  - px)
            val my = height * 0.5f * px / (src.regionHeight - px)
            sb.draw(src, -mx, -my, width + mx * 2f, height + my * 2f)
        } else {
            sb.draw(src, 0f, 0f, width, height)
        }
        sb.end()

        // Спільний батч — повертаємо йому дефолтний стан
        sb.shader = null
        sb.enableBlending()
        sb.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        FboStack.pop()
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    /** Споживачі, що ще тримають region, стануть прозорими, а не впадуть. */
    override fun dispose() {
        VfxTextures.unregister(this)
        releaseFbo()
        region.setTexture(VfxTextures.emptyTexture)
        region.setRegion(0, 0, 1, 1)
    }
}