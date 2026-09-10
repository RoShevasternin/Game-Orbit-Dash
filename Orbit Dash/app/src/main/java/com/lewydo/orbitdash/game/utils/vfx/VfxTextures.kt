package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.utils.WIDTH_UI

// ─────────────────────────────────────────────────────────────────────────────
// VfxTextures — реєстр і спільні ресурси для VfxTexture.
//
// Глобальний, як Blit і VfxShaderCache: не прив'язаний до екрана, живе від
// старту до dispose гри. Саме тому спільна текстура переживає перехід між
// екранами — на відміну від кешу VfxGroup, який живе в пулі екрана.
//
// update() викликається з GDXGame.render() ДО сцени. Там жоден батч не
// відкритий, viewport уже заданий, а всі Image, що тримають регіони, у цьому ж
// кадрі побачать свіжий результат.
// ─────────────────────────────────────────────────────────────────────────────
object VfxTextures : Disposable {

    /**
     * Пікселів текстури на один world-юніт — з РЕАЛЬНОГО екрана: сцена завширшки
     * WIDTH_UI юнітів лягає на Gdx.graphics.width пікселів. На 1080p це 3, на
     * 1440p — 4. PNG так не вміє: експорт @3x назавжди лишиться @3x.
     *
     * by lazy, а не перерахунок на resize: гра портретна, а перепікання всіх
     * текстур під нову щільність — окрема історія з іншою ціною.
     *
     * Стеля 4: планшет 2560 px запросив би 7 px/юніт і десятки мегабайт заради
     * країв, яких не видно. Підлога 2 — нижче вже видно сходинки.
     */
    val DENSITY: Float by lazy {
        val px = Gdx.graphics.width.toFloat()
        if (px > 0f) (px / WIDTH_UI).coerceIn(2f, 4f) else 3f
    }

    /**
     * GL_MAX_TEXTURE_SIZE цього GPU — стеля для буфера VfxTexture. Гарантований
     * мінімум GLES2 — 2048; більший буфер на слабкому пристрої — чорна текстура.
     * by lazy: читати можна лише з GL-потоку, а перший VfxTexture і так там.
     */
    val maxTextureSize: Int by lazy {
        val buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16)
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, buf)
        buf.get(0).takeIf { it > 0 } ?: 2048
    }


    // ─── Спільні ресурси рендеру ─────────────────────────────────────────────

    /** Пул під ping-pong post-ефектів. Власний, бо пул екрана помирає з екраном. */
    internal val pool = VfxPool()

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

    /** Повернення прямої альфи після ланцюга post-ефектів. */
    internal val unpremulShader: ShaderProgram
        get() = VfxShaderCache.get("shader/base/unpremul/unpremulFS.glsl", Blit.VERT)

    // ─── 1×1 текстури: керовані, libGDX сам відновить після втрати контексту ──

    private var whitePm : Pixmap?  = null
    private var whiteTex: Texture? = null
    private var emptyPm : Pixmap?  = null
    private var emptyTex: Texture? = null

    /**
     * managed = true: після втрати контексту libGDX перезаллє текстуру з pixmap,
     * і той самий об'єкт Texture знову валідний. Тому pixmap тримаємо живим —
     * він і є джерело відновлення. Звичайна Texture(pixmap) НЕ керована.
     */
    private fun managed1x1(r: Float, g: Float, b: Float, a: Float): Pair<Pixmap, Texture> {
        val pm = Pixmap(1, 1, Pixmap.Format.RGBA8888).also { it.setColor(r, g, b, a); it.fill() }
        return pm to Texture(PixmapTextureData(pm, null, false, false, true))
    }

    /** Білий квад — база за замовчуванням, коли shape-ефект малює форму сам. */
    internal val white: TextureRegion
        get() = TextureRegion(whiteTex ?: managed1x1(1f, 1f, 1f, 1f).let { (pm, tex) ->
            whitePm = pm; whiteTex = tex; tex
        })

    /**
     * 1×1 прозорий піксель — один на всю гру. Його захоплюють назавжди
     * (SpriteUtil.All.LIGHT, стилі кнопок), тому він керований — див. managed1x1.
     * Також стартовий стан region у VfxTexture до першого рендеру.
     */
    val emptyTexture: Texture
        get() = emptyTex ?: managed1x1(0f, 0f, 0f, 0f).let { (pm, tex) ->
            emptyPm = pm; emptyTex = tex; tex
        }

    // ─── Реєстр ──────────────────────────────────────────────────────────────

    private val all = ArrayList<VfxTexture>()

    internal fun register(t: VfxTexture)   { all.add(t) }
    internal fun unregister(t: VfxTexture) { all.remove(t) }

    /** Скільки спільних текстур живе зараз — для дебаг-HUD. */
    val count: Int get() = all.size

    /**
     * Перемалювати те, що змінилось. Викликати з GDXGame.render() ДО сцени.
     * Незмінені текстури коштують один порівняльний хеш — і все.
     */
    fun update() {
        for (i in all.indices) all[i].update()
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun dispose() {
        for (t in all.toList()) runCatching { t.dispose() }   // dispose() робить unregister
        all.clear()
        pool.dispose()
        batchOrNull?.let { runCatching { it.dispose() } }; batchOrNull = null
        whiteTex?.let { runCatching { it.dispose() } }; whiteTex = null
        whitePm ?.let { runCatching { it.dispose() } }; whitePm  = null
        emptyTex?.let { runCatching { it.dispose() } }; emptyTex = null
        emptyPm ?.let { runCatching { it.dispose() } }; emptyPm  = null
    }
}