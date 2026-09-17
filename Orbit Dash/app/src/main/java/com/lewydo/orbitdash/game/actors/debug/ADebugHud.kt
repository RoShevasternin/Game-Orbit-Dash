package com.lewydo.orbitdash.game.actors.debug

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.debug.PerfMonitor
import com.lewydo.orbitdash.game.utils.debug.dragToMove
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.global.IS_DEBUG

// ═════════════════════════════════════════════════════════════════════════════
//  ADebugHud — оверлей зі статистикою рендеру.
//
//  Підключення на будь-якому екрані — один рядок:
//
//    private val aDebugHud by lazy { ADebugHud(this) }
//
//    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
//        ...
//        addDebugHud(aDebugHud)
//    }
//
//  У релізі клас нічого не робить: PerfMonitor.enable() виходить одразу,
//  а сам HUD ховається. Тому виносити виклики під if (IS_DEBUG)
//  на кожному екрані не треба — перевірка вже всередині.
// ═════════════════════════════════════════════════════════════════════════════
class ADebugHud(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf by lazy { gdxGame.msdfManager }

    private val style = MsdfStyle(msdf, msdf.fontInter_Medium, 10f, Color.WHITE.cpy().apply { a = 0.45f })

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aStatsLbl = AMsdfLabel("debug", style).apply { autoSize = true }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        // childrenOnly, а не disable(): група на весь екран і мусить лишатись
        // наскрізною, але сам напис має ловити дотик — за нього ми й тягнемо.
        touchable = Touchable.childrenOnly

        if (!IS_DEBUG) { isVisible = false; return }

        PerfMonitor.enable(hudInterval = 1f, log = false)

        //aStatsLbl.debug()
        //aStatsLbl.wrap = true
        add(aStatsLbl) { startToStart(margin = 14f); topToTop(margin = 14f) }

        aStatsLbl.touchable = Touchable.enabled
        aStatsLbl.dragToMove("${screen::class.simpleName}.hud")
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!IS_DEBUG || !PerfMonitor.isEnabled) return

        // Рівно один виклик на кадр — тут і ніде більше, інакше лічильники
        // обнуляться посеред кадру і статистика поїде
        PerfMonitor.sample(delta)

        // setText лише коли знімок реально оновився (раз на секунду):
        // MSDF перебудовує розкладку гліфів на кожен виклик
        if (PerfMonitor.isDirty) aStatsLbl.setText(PerfMonitor.hudText)
    }
}



// ------------------------------------------------------------------------
// Helper
// ------------------------------------------------------------------------
/** Хелпер: додає HUD поверх усього, з нульовою вартістю в релізі. */
fun AConstraintLayout.addDebugHud(hud: ADebugHud) {
    if (!IS_DEBUG) return
    add(hud) { fillParent() }
}