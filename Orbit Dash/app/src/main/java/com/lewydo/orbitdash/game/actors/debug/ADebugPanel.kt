package com.lewydo.orbitdash.game.actors.debug

import com.badlogic.gdx.graphics.Color
import com.lewydo.orbitdash.game.actors.button.ADefButton
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.actor.setOnClickListener
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG

// ═════════════════════════════════════════════════════════════════════════════
//  ADebugPanel — стовпчик службових кнопок для перевірки механік руками.
//
//  Панель НІЧОГО не знає про гру: приймає список «підпис → дія» і малює
//  кнопки. Тому один клас обслуговує будь-який екран зі своїм набором.
//
//    private val aDebugPanel by lazy {
//        ADebugPanel(this, listOf(
//            ADebugPanel.Item("KILL")  { engine.kill() },
//            ADebugPanel.Item("+GEM")  { engine.spawnGem() },
//        ))
//    }
//
//    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
//        ...
//        addDebugPanel(aDebugPanel)
//    }
//
//  У релізі нічого не додається: перевірка IS_DEBUG усередині хелпера
//  і в addActorsOnGroup, тож на екранах if-и писати не треба.
//
//  Кнопки навмисно НИЖЧІ за ігрові (H_DEBUG < ADefButton.H_SMALL) — панель
//  не має претендувати на місце в композиції й одразу читається як службова.
// ═════════════════════════════════════════════════════════════════════════════
class ADebugPanel(
    override val screen: AdvancedScreen,
    private val items: List<Item>,
) : AAutoLayout(
    screen     = screen,
    direction  = Direction.VERTICAL,
    gapMain    = 6f,
    alignCross = AlignCross.STRETCH,
    sizingH    = Sizing.HUG,
) {

    /** Одна кнопка. Блок отримує саму кнопку — можна міняти її підпис. */
    class Item(val label: String, val onClick: (ADefButton) -> Unit)

    companion object {
        const val DEFAULT_WIDTH = 96f
        private const val H_DEBUG = 30f
    }

    private val msdf by lazy { gdxGame.msdfManager }

    private val style by lazy {
        MsdfStyle(msdf, msdf.fontInter_Medium, 9f, Color.WHITE)
            .apply { letterSpacing = 6f }
    }

    override fun addActorsOnGroup() {
        if (!IS_DEBUG) { isVisible = false; return }

        items.forEach { item ->
            val btn = ADefButton(screen, item.label, style)
            btn.height = H_DEBUG
            btn.radius = 8f
            add(btn)
            btn.setOnClickListener() { item.onClick(btn) }
        }
    }

}

// ----------------------------------------------------------------------------
// Helper
// ----------------------------------------------------------------------------
/** Додає панель у правий низ екрана. У релізі — нуль вартості. */
fun AConstraintLayout.addDebugPanel(panel: ADebugPanel) {
    if (!IS_DEBUG) return
    panel.setSize(ADebugPanel.DEFAULT_WIDTH, 1f)   // ширина фіксована, висота HUG
    add(panel) { endToEnd(margin = 10f); bottomToBottom(margin = 10f) }
}
