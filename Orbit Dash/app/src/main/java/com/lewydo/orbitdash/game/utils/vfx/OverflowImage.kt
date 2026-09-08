package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

// ─────────────────────────────────────────────────────────────────────────────
// OverflowImage — Image, чий регіон ШИРШИЙ за актора: зайве малюється назовні.
//
//   Модель Figma: межі шару = фігура, а ефект (блюр, тінь, світіння) виходить
//   за них. Тут те саме: setSize / fillParent / hit-box — про фігуру, а поле
//   регіона (msdf-відступ або bleed під ефект) лягає ЗА межі прямокутника.
//
//   padX / padY — поле з КОЖНОГО боку як частка ширини / висоти актора.
//   open: AMsdfImage перевизначає їх геттерами, щоб рахувати з живого регіона.
//
//   Звідки частки:
//     AMsdfImage          — з клітинки msdf: 0.5·pxRange / (cell − pxRange)
//     VfxTexture.image()  — bleed / width, bleed / height
//
//   Межі: звичайна Group не кліпає, вихід за межі безпечний. Усередині VfxGroup
//   поле обріже FBO — там потрібен bleed самої групи. Scaling / Align в Image
//   не підтримуються: малюється рівно width × height плюс поле.
// ─────────────────────────────────────────────────────────────────────────────
open class OverflowImage(
    region: TextureRegion? = null,
    padX: Float = 0f,
    padY: Float = 0f,
) : Image(region?.let { TextureRegionDrawable(it) }) {

    open val padX: Float = padX
    open val padY: Float = padY

    override fun draw(batch: Batch, parentAlpha: Float) {
        val d = drawable as? TextureRegionDrawable ?: run { super.draw(batch, parentAlpha); return }
        validate()

        val mx = width  * padX
        val my = height * padY

        val c = color
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha)
        d.draw(batch,
            x - mx, y - my,                       // квад більший за актора на поле
            originX + mx, originY + my,           // origin — той самий, у новій системі
            width + mx * 2f, height + my * 2f,
            scaleX, scaleY, rotation)
    }
}