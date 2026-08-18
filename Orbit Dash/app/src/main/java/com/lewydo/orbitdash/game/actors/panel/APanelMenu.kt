package com.lewydo.orbitdash.game.actors.panel

import com.lewydo.orbitdash.game.actors.button.ADefButton
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame

// ----------------------------------------------------------------------------
//  СКЛАД СТАТИЧНИЙ, ДОСТУПНІСТЬ — ДИНАМІЧНА.
//
//  Рекламні кнопки (aBoostBtn, aGemsBtn) не додаються й не видаляються, а
//  гасяться через disable(): touchable = disabled + альфа 0.4. Причини:
//    • висота панелі не стрибає — а вона HUG, тож будь-яка зміна складу
//      смикала б і позицію, і політ появи меню;
//    • гравець бачить, що офер існує, просто зараз не готовий — замість
//      миготіння «кнопки зникли → з'явились» після кожного показу.
//
//  ОФЕР ЖИВЕ, ПОКИ ЙОГО НЕ СПОЖИЛИ. Рол відбувається один раз при створенні
//  панелі і далі — лише після успішного показу, окремо для кожного офера
//  (rerollBoost / rerollGems). Реролити на появі реклами не можна: гравець
//  тягнеться пальцем до «+100 GEMS», у цей момент довантажується ролик, і
//  число стає «+30» — виглядає як приманка, хоч це просто випадковість.
//
//  Потоки: refresh() чіпає сцену → викликати ТІЛЬКИ з GL-потоку. Колбек від
//  AdMob приходить на UI — стрибок робить викликач (MenuScreen через runGDX).
// ----------------------------------------------------------------------------
class APanelMenu(override val screen: AdvancedScreen) : AAutoLayout(
    screen     = screen,
    direction  = Direction.VERTICAL,
    gapMain    = 10f,
    alignCross = AlignCross.STRETCH,
    sizingH    = Sizing.HUG,
) {

    // ------------------------------------------------------------------------
    // Static
    // ------------------------------------------------------------------------
    /** Стартові бусти, які може запропонувати рекламна кнопка. */
    enum class StartBoost(val label: String) {
        MAGNET("MAGNET"),
        SHIELD("SHIELD"),
        FRENZY("GEM x2"),
        SLOW  ("SLOW-MO");

        companion object { fun random() = entries.random() }
    }

    companion object {
        private const val GEMS_MIN_STEP = 5   // 25..100 з кроком 5:
        private const val GEMS_LOW      = 5   // «+65» читається як офер,
        private const val GEMS_HIGH     = 20  // «+63» — як баг генератора
    }

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
    val aBoostBtn  = ADefButton(screen, "", styleSmall)
    val aShopBtn   = ADefButton(screen, "SHOP",  styleSmall)
    val aDailyBtn  = ADefButton(screen, "DAILY", styleSmall)
    val aRanksBtn  = ADefButton(screen, "RANKS", styleSmall)
    val aGemsBtn   = ADefButton(screen, "", styleSmall, ADefButton.Variant.ACCENT)

    /** Рядок із трьох — вкладений горизонтальний лейаут, кнопки ділять ширину. */
    private val aRow = AAutoLayout(
        screen    = screen,
        direction = Direction.HORIZONTAL,
        gapMain   = 10f,
        sizingH   = Sizing.HUG,
        alignMain = AlignMain.SPACE_BETWEEN
    )

    // ------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------
    /** Поточний стан оферів. Читати можна будь-коли, міняти — лише через API. */
    var adsAvailable = false              ; private set
    var rolledBoost  = StartBoost.random(); private set
    var rolledGems   = rollGems()         ; private set

    // ------------------------------------------------------------------------
    // Callbacks — навігація і реклама належать екрану, панель лише повідомляє
    // ------------------------------------------------------------------------
    var onPlay      : () -> Unit           = {}
    var onPlayBoost : (StartBoost) -> Unit = {}
    var onGems      : (Int) -> Unit        = {}
    var onShop      : () -> Unit           = {}
    var onDaily     : () -> Unit           = {}
    var onRanks     : () -> Unit           = {}

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        buildRowOnce()
        wireListeners()
        applyOfferTexts()
        buildOnce()

        // adsAvailable = false, а refresh(false) вилетів би на ранньому виході —
        // тому стартовий стан виставляємо напряму.
        setAdButtonsEnabled(adsAvailable)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addPlayBtn() {
        aPlayBtn.height = ADefButton.H_LARGE
        add(aPlayBtn)
    }

    private fun addBoostBtn() {
        aBoostBtn.height = ADefButton.H_SMALL
        add(aBoostBtn)
    }

    private fun addRowBtns() {
        add(aRow)
    }

    private fun addGemsBtn() {
        aGemsBtn.height = ADefButton.H_SMALL
        add(aGemsBtn)
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Доступність рекламних оферів. Склад меню не міняє — лише вмикає/гасить
     * кнопки. Викликати з GL-потоку: на show() екрана і з колбека
     * onAvailabilityChanged (через runGDX).
     */
    fun refresh(available: Boolean) {
        if (available == adsAvailable) return
        adsAvailable = available
        setAdButtonsEnabled(available)
    }

    /** Свіжий офер бусту після спожитого показу — старий уже «проданий». */
    fun rerollBoost() {
        rolledBoost = StartBoost.random()
        applyOfferTexts()
    }

    /** Свіжий офер гемів після успішного клейму. */
    fun rerollGems() {
        rolledGems = rollGems()
        applyOfferTexts()
    }

    fun setDailyBadge(visible: Boolean) { aDailyBtn.isBadgeVisible = visible }

    // ------------------------------------------------------------------------
    // Build
    // ------------------------------------------------------------------------

    /** Склад меню НЕЗМІННИЙ: PLAY → буст → SHOP/DAILY/RANKS → геми. */
    private fun buildOnce() {
        addPlayBtn()
        addBoostBtn()
        addRowBtns()
        addGemsBtn()
    }

    /** Діти рядка додаються один раз — так само, як і решта складу. */
    private fun buildRowOnce() {
        listOf(aShopBtn, aDailyBtn, aRanksBtn).forEach {
            it.height = ADefButton.H_SMALL
            aRow.add(it) { grow = 1f }
        }
    }

    /** Лістенери ставимо один раз: кнопки не перестворюються. */
    private fun wireListeners() {
        aPlayBtn.setOnClickListener  { onPlay() }
        aBoostBtn.setOnClickListener { onPlayBoost(rolledBoost) }
        aGemsBtn.setOnClickListener  { onGems(rolledGems) }
        aShopBtn.setOnClickListener  { onShop() }
        aDailyBtn.setOnClickListener { onDaily() }
        aRanksBtn.setOnClickListener { onRanks() }
    }

    // ------------------------------------------------------------------------
    // Offers
    // ------------------------------------------------------------------------

    /** Візуальний стан рекламних кнопок. Уся анімація вже в ADefButton. */
    private fun setAdButtonsEnabled(enabled: Boolean) {
        if (enabled) {
            aBoostBtn.enable()
            aGemsBtn.enable()
        } else {
            aBoostBtn.disable()
            aGemsBtn.disable()
        }
    }

    private fun applyOfferTexts() {
        aBoostBtn.label.setText("PLAY + ${rolledBoost.label} · AD")
        aGemsBtn.label.setText("+$rolledGems GEMS · AD")
    }

    private fun rollGems() = (GEMS_LOW..GEMS_HIGH).random() * GEMS_MIN_STEP

}
