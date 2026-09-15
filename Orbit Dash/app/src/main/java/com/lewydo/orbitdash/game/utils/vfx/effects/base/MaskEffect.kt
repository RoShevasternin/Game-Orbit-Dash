package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache

/**
 * Маскування alpha-текстурою. Приймає Texture АБО TextureRegion (з атласу).
 *
 * Внутрішньо все зберігається як TextureRegion — для standalone Texture
 * створюється full-регіон (0,0,1,1), тому шейдер працює однаково.
 * UV регіону передаються в u_maskUv → семплиться тільки ділянка атласу.
 *
 * ─── Термінальний ефект (патч 24) ───────────────────────────────────────────
 * Маска вимагає різкості не від ВХОДУ, а від власного ВИХОДУ: байдуже, якої
 * роздільності прийшло розмите тло — важливо, щоб її край ліг у різкий буфер.
 * Тому вона не голосує в preferredDensity() (це про робочий буфер), а каже
 * своє через outputDensity() і сама малює у вихідний, піднімаючи джерело
 * кубічним B-сплайном, якщо воно менше.
 *
 * До патча 24 тут стояло preferredDensity() = +∞, і екранну густину отримував
 * УВЕСЬ ланцюг: у ABlurBack повнорозмірними ставали три проходи замість одного.
 *
 * Маски нема (maskRegion == null) — нема й ефекту: outputDensity() = null,
 * група поводиться як звичайний ABlur.
 */
class MaskEffect() : VfxEffect() {

    constructor(texture: Texture?) : this() { maskTexture = texture }
    constructor(region: TextureRegion?) : this() { maskRegion = region }

    override val fragmentShader = "shader/base/mask/maskFS.glsl"

    /** Та сама маска + кубічний B-сплайн: джерело менше за приймач. */
    private val upsampleShader get() = VfxShaderCache.get("shader/base/mask/maskUpsampleFS.glsl", Blit.VERT)

    /** Маска як регіон (атлас або full-текстура). Головне сховище. */
    var maskRegion: TextureRegion? = null

    /** Назад-сумісний доступ як Texture. set загортає у full-регіон. */
    var maskTexture: Texture?
        get()      = maskRegion?.texture
        set(value) { maskRegion = value?.let { TextureRegion(it) } }

    /** Різким має бути ВИХІД маски → стеля екрана. Без маски ефекту нема. */
    override fun outputDensity(): Float? = if (maskRegion != null) Float.POSITIVE_INFINITY else null

    /**
     * Робочий буфер → вихідний. Розміри різні — B-сплайн і маска одним проходом;
     * збіглися (AMask, блюру нема) — старий maskFS, байт у байт як до патча 24.
     */
    override fun renderToOutput(src: FrameBuffer, dst: FrameBuffer, ctx: VfxContext) {
        val region     = maskRegion ?: return
        val isUpsample = src.width != dst.width || src.height != dst.height

        Blit.blit(src, dst, if (isUpsample) upsampleShader else shader) { s ->
            s.setUniformi("u_texture", 0)          // unit 0 вже bind-нутий Blit (src)

            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1)
            region.texture.bind(1)
            s.setUniformi("u_mask", 1)             // unit 1 = сторінка маски

            // UV-межі регіону: для full-текстури це (0,0,1,1) — стара поведінка
            s.setUniformf("u_maskUv", region.u, region.v, region.u2, region.v2)

            if (isUpsample) s.setUniformf("u_srcSize", src.width.toFloat(), src.height.toFloat())

            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)  // повертаємо активний unit
        }
    }

    /**
     * Ланцюг без вихідного буфера (VfxTexture.post) — маска звичайним проходом
     * 1:1 на ping-pong. Рівно те, що робив render() до патча 24.
     */
    override fun render(pingPong: PingPong, ctx: VfxContext) {
        if (maskRegion == null) return             // pass-through
        renderToOutput(pingPong.src, pingPong.dst, ctx)
        pingPong.swap()
    }

    // autoCache: детектує зміну і текстури, і UV регіону
    override fun stateKey(): Long {
        val r = maskRegion ?: return 0L
        var h = r.texture.hashCode().toLong()
        h = h * 31 + r.u.toRawBits().toLong()
        h = h * 31 + r.v.toRawBits().toLong()
        h = h * 31 + r.u2.toRawBits().toLong()
        h = h * 31 + r.v2.toRawBits().toLong()
        return h
    }
}