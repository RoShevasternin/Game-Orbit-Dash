package com.lewydo.orbitdash.game.actors.debug

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.actor.setOnClickListener
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.debug.dragToMove
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG

// ═════════════════════════════════════════════════════════════════════════════
//  ADebugSwatchBar — стовпчик кольорових кружечків «колір → дія».
//
//  Як ADebugPanel / ADebugIconBar, НІЧОГО не знає про гру: кольори, ознаку
//  «вибрано» і дію дає екран. На MenuScreen це теми — тап перемикає палітру
//  через ThemeManager.switchTo(), тож видно той самий плавний перехід, що й
//  при виборі скіна.
//
//    private val aDebugThemeBar by lazy {
//        ADebugSwatchBar(this, ThemeManager.palettes.mapIndexed { id, p ->
//            ADebugSwatchBar.Item(p.player, p.gem, isActive = { ThemeManager.currentId == id }) {
//                ThemeManager.switchTo(id)
//            }
//        })
//    }
//
//    addDebugSwatchBar(aDebugThemeBar)
//
//  Кружечок — заливка main + крапка accent у центрі: дві ролі палітри, бо за
//  одним кольором схожі теми (ICE / GOLD) не розрізнити. Вибраний — у білому
//  кільці: під заливкою лежить більший білий круг, видно лише його край.
//
//  Клік — setOnClickListener(stopEvent = false), як в ADebugIconBar: подія
//  мусить спливти до стовпчика, інакше перетяг не почнеться з кружечка.
// ═════════════════════════════════════════════════════════════════════════════
class ADebugSwatchBar(
    override val screen: AdvancedScreen,
    private val items: List<Item>,
) : AAutoLayout(
    screen     = screen,
    direction  = Direction.VERTICAL,
    gapMain    = 6f,
    alignCross = AlignCross.CENTER,
    sizingW    = Sizing.HUG,
    sizingH    = Sizing.HUG,
) {

    /** Один кружечок. [isActive] питається щокадру — екран сам знає, що вибрано. */
    class Item(
        val main    : Color,
        val accent  : Color,
        val isActive: () -> Boolean,
        val onClick : () -> Unit,
    )

    companion object {
        /** Діаметр. 28 юнітів ≈ 84 px на 1080 — вісім штук не з'їдають екран. */
        const val CELL = 28f

        private const val RING = 2.5f   // товщина білого кільця вибраного
        private const val DOT  = 9f     // крапка accent
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        if (!IS_DEBUG) { isVisible = false; return }

        items.forEach { item ->
            val cell = ACell(screen, item)
            cell.setSize(CELL, CELL)
            add(cell)
            cell.setOnClickListener(stopEvent = false) { item.onClick() }
        }
    }

    // ------------------------------------------------------------------------
    // Cell
    // ------------------------------------------------------------------------
    /** Біле кільце (лише вибраний) → заливка main → крапка accent. */
    private class ACell(
        override val screen: AdvancedScreen,
        private val item: Item,
    ) : AConstraintLayout(screen) {

        private val aRing = Image(gdxGame.assetsMsdf.circle)
        private val aFill = Image(gdxGame.assetsMsdf.circle)
        private val aDot  = Image(gdxGame.assetsMsdf.circle)

        override fun addActorsOnGroup() {
            add(aRing) { fillParent() }

            aFill.setColorRGB(item.main)
            add(aFill) { size(CELL - 2f * RING); center() }

            aDot.setColorRGB(item.accent)
            add(aDot) { size(DOT); center() }
        }

        override fun act(delta: Float) {
            super.act(delta)
            aRing.isVisible = item.isActive()
        }
    }
}

// ----------------------------------------------------------------------------
// Helper
// ----------------------------------------------------------------------------
/**
 * Ставить стовпчик у правий верхній кут — там на меню вільно (HUD зліва,
 * емблема й титули по центру). Тягається пальцем, позиція — у Preferences.
 */
fun AConstraintLayout.addDebugSwatchBar(bar: ADebugSwatchBar) {
    if (!IS_DEBUG) return
    bar.setSize(1f, 1f)   // HUG по обох осях; нульовий розмір add() не пропустить
    add(bar) { endToEnd(margin = 10f); topToTop(margin = 10f) }

    bar.dragToMove("${bar.screen::class.simpleName}.swatchBar")
}