package com.lewydo.orbitdash.game.actors.vfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ----------------------------------------------------------------------------
//  LAB · Маска через СТЕНСИЛ — без FBO, без жодного додаткового проходу.
//
//  AMask робить так: діти → FBO → шейдер маски → квад назад на екран. Це
//  зміна render target, а на тайловому GPU це найдорожче, що є.
//
//  Стенсил робить так, не виходячи з екранного буфера:
//    1. маска → у стенсил-буфер (кольору не пише, лише 1 там, де маска непрозора)
//    2. діти → як звичайно, але з тестом «малювати лише де стенсил == 1»
//    3. маска ще раз → стенсил назад у 0 (самоочищення: групи можуть іти одна
//       за одною й перекриватись, спільний clear не потрібен)
//
//  Ціна — три флуші батча замість одного, кілька трикутників маски двічі
//  і нуль зайвих буферів. Межа: край маски ТВЕРДИЙ (поріг 0.5). М'який край,
//  блюр, напівпрозорість маски — це альфа, лишається AMask.
//
//  Потребує stencil = 8 в AndroidApplicationConfiguration (GDXFragment).
// ----------------------------------------------------------------------------
class AStencilMask(
    override val screen: AdvancedScreen,
    var mask: TextureRegion,
) : AdvancedGroup() {

    private companion object { const val REF = 1 }

    override fun addActorsOnGroup() {}

    private val maskShader get() = VfxShaderCache.get("shader/base/stencil/stencilMaskFS.glsl", VfxEffect.BATCH_VERT)

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null || !isVisible) return
        validate()

        val gl = Gdx.gl

        // ── 1. маска → стенсил ──────────────────────────────────────────────
        batch.flush()                                   // усе, що було до нас, — на екран
        gl.glEnable(GL20.GL_STENCIL_TEST)
        gl.glStencilMask(0xFF)
        gl.glColorMask(false, false, false, false)
        gl.glStencilFunc(GL20.GL_ALWAYS, REF, 0xFF)
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE)
        drawMask(batch)                                 // flush усередині (зміна шейдера)

        // ── 2. діти → лише де стенсил == REF ───────────────────────────────
        gl.glColorMask(true, true, true, true)
        gl.glStencilFunc(GL20.GL_EQUAL, REF, 0xFF)
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP)
        super.draw(batch, parentAlpha)
        batch.flush()

        // ── 3. маска → 0 (прибрати за собою) ───────────────────────────────
        gl.glColorMask(false, false, false, false)
        gl.glStencilFunc(GL20.GL_ALWAYS, 0, 0xFF)
        gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE)
        drawMask(batch)

        gl.glColorMask(true, true, true, true)
        gl.glDisable(GL20.GL_STENCIL_TEST)
    }

    /** Квад маски в межах групи (transform самої групи не враховуємо — стенд без обертання). */
    private fun drawMask(batch: Batch) {
        val prev = batch.shader
        batch.shader = maskShader                       // setShader → flush
        batch.draw(mask, x, y, width, height)
        batch.shader = prev                             // flush нашого квада, повернення
    }
}
