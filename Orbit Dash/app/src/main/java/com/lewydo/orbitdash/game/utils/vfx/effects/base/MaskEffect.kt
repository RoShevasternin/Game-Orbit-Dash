package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext

// ============================================================================
// MaskEffect — ЗАМІНА класу у VfxEffects.kt
// Додай імпорт угорі файлу: import com.badlogic.gdx.graphics.g2d.TextureRegion
// ============================================================================

/**
 * Маскування alpha-текстурою. Приймає Texture АБО TextureRegion (з атласу).
 *
 * Внутрішньо все зберігається як TextureRegion — для standalone Texture
 * створюється full-регіон (0,0,1,1), тому шейдер працює однаково.
 * UV регіону передаються в u_maskUv → семплиться тільки ділянка атласу.
 *
 * API назад-сумісний: maskTexture: Texture? працює як раніше.
 */
class MaskEffect() : VfxEffect() {

    constructor(texture: Texture?) : this() { maskTexture = texture }
    constructor(region: TextureRegion?) : this() { maskRegion = region }

    override val fragmentShader = "shader/base/mask/maskFS.glsl"

    /** Маска як регіон (атлас або full-текстура). Головне сховище. */
    var maskRegion: TextureRegion? = null

    /** Назад-сумісний доступ як Texture. set загортає у full-регіон. */
    var maskTexture: Texture?
        get()      = maskRegion?.texture
        set(value) { maskRegion = value?.let { TextureRegion(it) } }

    override fun render(pingPong: PingPong, ctx: VfxContext) {
        val region = maskRegion ?: return  // pass-through

        Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
            s.setUniformi("u_texture", 0)          // unit 0 вже bind-нутий Blit (src)

            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1)
            region.texture.bind(1)
            s.setUniformi("u_mask", 1)             // unit 1 = сторінка маски

            // UV-межі регіону: для full-текстури це (0,0,1,1) — стара поведінка
            s.setUniformf("u_maskUv", region.u, region.v, region.u2, region.v2)

            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)  // повертаємо активний unit
        }
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