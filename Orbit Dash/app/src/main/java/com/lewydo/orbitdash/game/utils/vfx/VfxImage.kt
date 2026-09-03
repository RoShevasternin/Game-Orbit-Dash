package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

/**
 * Зображення з шейдерним ефектом — БЕЗ FBO.
 *
 * Для per-pixel ефектів (HSL, saturation, bagCoins, lavaProgress, circleProgress)
 * де шейдер трансформує один піксель без потреби "бачити" сусідів.
 *
 * ─── Чому effect.batchShader а не effect.shader ──────────────────────────────
 *
 * SpriteBatch передає вершини у WORLD COORDINATES (наприклад x=500, y=1200).
 * Потрібен vertex shader з u_projTrans щоб перетворити world → NDC.
 *
 * Blit.VERT (effect.shader): gl_Position = a_position (без матриці)
 *   → вершини x=500,y=1200 → за межами NDC (-1..1) → все вирізається → невидимо!
 *
 * BATCH_VERT (effect.batchShader): gl_Position = u_projTrans * a_position
 *   → SpriteBatch підставляє правильну матрицю → нормальний рендер ✓
 *
 * ─── GC ───────────────────────────────────────────────────────────────────────
 * VfxContext перевикористовується (перестворюється лише при зміні розміру),
 * нуль алокацій у draw() на стабільному розмірі.
 */
open class VfxImage(
    override val screen: AdvancedScreen,
    drawable           : Drawable?  = null,
    var effect         : VfxEffect? = null,
) : AdvancedGroup() {

    constructor(screen: AdvancedScreen, region : TextureRegion, effect: VfxEffect? = null) :
            this(screen, TextureRegionDrawable(region), effect)

    constructor(screen: AdvancedScreen, texture: Texture, effect: VfxEffect? = null) :
            this(screen, TextureRegionDrawable(texture), effect)

    constructor(screen: AdvancedScreen, patch  : NinePatch, effect: VfxEffect? = null) :
            this(screen, NinePatchDrawable(patch), effect)

    private val image = Image()

    var drawable: Drawable?
        get()      = image.drawable
        set(value) { image.drawable = value }

    init { if (drawable != null) image.drawable = drawable }

    // Перевикористовуваний контекст — оновлюється лише при зміні розміру (0 алокацій/кадр)
    private var ctx = VfxContext(0f, 0f, 1, 1)

    override fun addActorsOnGroup() {
        addAndFillActor(image)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) return

        // Тінт групи → внутрішній Image. Group.draw передає дітям лише parentAlpha,
        // тому без цього рядка v_color завжди білий і будь-який .color марний.
        // Альфу лишаємо 1 — її вже несе parentAlpha (color.a * parentAlpha).
        image.color.set(color.r, color.g, color.b, 1f)

        val fx = effect
        if (fx == null)    { super.draw(batch, parentAlpha); return }
        if (!fx.isEnabled) return

        val sp = fx.batchShader

        if (ctx.width != width || ctx.height != height) {
            ctx = VfxContext(
                width, height,
                width.toInt().coerceAtLeast(1),
                height.toInt().coerceAtLeast(1)
            )
        }

        batch.shader = sp
        fx.setUniforms(sp, ctx)

        // UV bounds → BATCH_VERT нормалізує до v_localUV 0..1
        val region = (image.drawable as? TextureRegionDrawable)?.region
        if (region != null) {
            sp.setUniformf("u_uvMin", region.u,  region.v)
            sp.setUniformf("u_uvMax", region.u2, region.v2)
        } else {
            sp.setUniformf("u_uvMin", 0f, 0f)
            sp.setUniformf("u_uvMax", 1f, 1f)
        }

        super.draw(batch, parentAlpha)
        batch.shader = null
    }
}