package com.lewydo.orbitdash.game.actors.debug

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.actor.setOnClickListener
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.debug.dragToMove
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG

// ═════════════════════════════════════════════════════════════════════════════
//  ADebugIconBar — стовпчик ромбів «іконка → дія» поруч із ADebugPanel.
//
//  Як і ADebugPanel, НІЧОГО не знає про гру: колір, іконку й дію дає екран.
//  На GameScreen це «підкинути буст / шип» — у кольорі й з іконкою самого
//  об'єкта, щоб перевіряти вигляд, не читаючи підписів.
//
//    private val aDebugIconBar by lazy {
//        ADebugIconBar(this, listOf(
//            ADebugIconBar.Item(icon, color, iconW = 28f, iconH = 32f) { engine.debugSpawnSpike() },
//        ))
//    }
//
//    addDebugPanel(aDebugPanel)
//    addDebugIconBar(aDebugIconBar, aDebugPanel)   // ПІСЛЯ панелі: вона — якір
//
//  Ромб — gdxGame.assetsMsdf.gem: той самий запечений регіон, що й в AGem,
//  нуль нових текстур. Іконка темна поверх кольору — читається й на білому SLOW.
//
//  Клік — setOnClickListener(stopEvent = false): подія мусить спливти до
//  стовпчика, інакше перетяг не почнеться з ромба. Клік після перетягу не
//  прилітає — DragToMove скасовує тач-фокус, і ClickListener отримує touchUp
//  «за межами» (Stage ставить координати в Integer.MIN_VALUE).
// ═════════════════════════════════════════════════════════════════════════════
class ADebugIconBar(
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

    /**
     * Один ромб. Розмір іконки — від викликача: арт різний (PNG бустів має
     * поля навколо гліфа, запечений шип — ні), одне число на всіх не підходить.
     */
    class Item(
        val icon   : TextureRegion,
        val color  : Color,
        val iconW  : Float,
        val iconH  : Float,
        val onClick: () -> Unit,
    )

    companion object {
        /** Сторона ромба. 34 юніти ≈ 100 px на 1080 — палець влучає без прицілювання. */
        const val CELL = 34f
    }

    override fun addActorsOnGroup() {
        if (!IS_DEBUG) { isVisible = false; return }

        items.forEach { item ->
            val cell = ACell(screen, item)
            cell.setSize(CELL, CELL)
            add(cell)
            cell.setOnClickListener(stopEvent = false) { item.onClick() }
        }
    }

    /** Ромб у кольорі + темна іконка по центру. */
    private class ACell(
        override val screen: AdvancedScreen,
        private val item: Item,
    ) : AConstraintLayout(screen) {

        private val aDiamond = Image(gdxGame.assetsMsdf.gem)
        private val aIcon    = Image(item.icon)

        override fun addActorsOnGroup() {
            aDiamond.setColorRGB(item.color)
            add(aDiamond) { fillParent() }

            aIcon.setColorRGB(GameColor.background)
            add(aIcon) { size(item.iconW, item.iconH); center() }
        }
    }
}

// ----------------------------------------------------------------------------
// Helper
// ----------------------------------------------------------------------------
/**
 * Ставить стовпчик ЛІВОРУЧ від панелі, низом до її низу. Поки стовпчик не
 * чіпали, він їде за панеллю (якір); потягнув сам стовпчик — живе окремо,
 * позиція лягає в Preferences під своїм ключем.
 */
fun AConstraintLayout.addDebugIconBar(bar: ADebugIconBar, panel: ADebugPanel) {
    if (!IS_DEBUG) return
    bar.setSize(1f, 1f)   // HUG по обох осях; нульовий розмір add() не пропустить
    add(bar) { endToStart(panel, margin = 8f); bottomToBottom(panel) }

    bar.dragToMove("${bar.screen::class.simpleName}.iconBar")
}