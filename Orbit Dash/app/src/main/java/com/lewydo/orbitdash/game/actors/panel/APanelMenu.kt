package com.lewydo.orbitdash.game.actors.panel

import com.lewydo.orbitdash.game.actors.button.ADefButton
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame

class APanelMenu(override val screen: AdvancedScreen) : AAutoLayout(
    screen     = screen,
    direction  = Direction.VERTICAL,
    gapMain    = 10f,
    alignCross = AlignCross.STRETCH,
    sizingH    = Sizing.HUG,
) {

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf = gdxGame.msdfManager

    private val styleLarge = MsdfStyle(msdf, msdf.fontInter_ExtraBold, 18f).apply { letterSpacing = 12f }
    private val styleSmall = styleLarge.copy(size = 12f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    val aPlayBtn   = ADefButton(screen, "PLAY", styleLarge, ADefButton.Variant.PRIMARY)
    val aBoostBtn  = ADefButton(screen, "PLAY + MAGNET · AD", styleSmall)
    val aShopBtn   = ADefButton(screen, "SHOP",  styleSmall)
    val aDailyBtn  = ADefButton(screen, "DAILY", styleSmall)
    val aRanksBtn  = ADefButton(screen, "RANKS", styleSmall)
    val aGemsBtn   = ADefButton(screen, "+25 GEMS · AD", styleSmall, ADefButton.Variant.ACCENT)

    /** Рядок із трьох — вкладений горизонтальний лейаут, кнопки ділять ширину. */
    private val aRow = AAutoLayout(
        screen    = screen,
        direction = Direction.HORIZONTAL,
        gapMain   = 10f,
        sizingH   = Sizing.HUG,
        alignMain = AlignMain.SPACE_BETWEEN
    )

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addPlayBtn()
        addBoostBtn()
        addRowBtn()
        addGemsBtn()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addPlayBtn() {
        aPlayBtn.height = ADefButton.H_LARGE
        add(aPlayBtn)

        aPlayBtn.setOnClickListener {

        }
    }

    private fun addBoostBtn() {
        aBoostBtn.height = ADefButton.H_SMALL
        add(aBoostBtn)
    }

    private fun addRowBtn() {
        listOf(aShopBtn, aDailyBtn, aRanksBtn).forEach {
            it.height = ADefButton.H_SMALL
            aRow.add(it) { grow = 1f }
        }
        add(aRow)

        aDailyBtn.isBadgeVisible = true
    }

    private fun addGemsBtn() {
        aGemsBtn.height = ADefButton.H_SMALL
        add(aGemsBtn)
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------
    fun setDailyBadge(visible: Boolean) { aDailyBtn.isBadgeVisible = visible }
}