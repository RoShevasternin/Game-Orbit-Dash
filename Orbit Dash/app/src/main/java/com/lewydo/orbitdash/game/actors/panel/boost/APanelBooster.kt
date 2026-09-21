package com.lewydo.orbitdash.game.actors.panel.boost

import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.ui.ABoostHex
import com.lewydo.orbitdash.game.actors.progress.ABarProgress
import com.lewydo.orbitdash.game.content.info
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

// ----------------------------------------------------------------------------
//  ТАЙМЕРНИЙ БУСТ У HUD: шестикутник + смуга часу, що збігає.
//
//  ЧОМУ ВІН ПОТРІБЕН. Щит видно по капсулах, комбо — по кільцю на м'ячі й
//  напису. MAGNET, GEM x2 і SLOW-MO не показували нічого: гравець не знав ні
//  що діє, ні скільки лишилось. Панель закриває рівно цю дірку.
//
//  ПАНЕЛЬ ОДНА, І ЦЕ НЕ СПРОЩЕННЯ. Три таймерні бусти взаємовиключні в рушії
//  (новий гасить попередній), тож більше однієї смуги на екрані не буває
//  ніколи — і рядок із порожніх слотів під «можливі» бусти був би брехнею.
//
//  ХТО АКТИВНИЙ — ПИТАННЯ ДО РУШІЯ (engine.activeBoost / boostFrac), не до
//  панелі. Панель німа: читає стан і малює.
//
//  PULSE сюди не потрапляє навмисно: він разовий — зносить шипи й зникає,
//  показувати «скільки лишилось» нема чого.
//
//  СМУГА — ОДИН АКТОР, а не «доріжка + заповнення». ABarProgress малює обидві
//  в одному квадi, як ARingProgress малює кільця м'яча: два ARoundRect
//  коштували б удвічі більше перемикань шейдера в батчі HUD, а заповнення
//  довелось би щокадру рухати руками. Коли брати смугу, а коли маску —
//  docs/progress.md.
// ----------------------------------------------------------------------------
class APanelBooster(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val HEX_W = 18f
        private const val HEX_H = 21f

        private const val BAR_W = 64f
        private const val BAR_H = 3f

        /** Доріжка — та сама смуга, лише ледь помітна: видно, скільки вже збігло. */
        private const val TRACK_ALPHA = 0.20f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, BAR_W)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aHex = ABoostHex(screen)
    private val aBar = ABarProgress(screen).apply { trackAlpha = TRACK_ALPHA }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    /** Останній показаний буст — щоб не чіпати колір та іконку щокадру. */
    private var lastBoost: RunEngine.Boost? = null

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addHex()
        addBar()

        // Радіус — похідна геометрія у world-юнітах, тому keepScaled, а не
        // sizeChanged(): фактор скейлера існує лише зі stage, і в sizeChanged()
        // капсула вийшла б прямокутником (та сама пастка, що з кільцями м'яча).
        keepScaled { aBar.radius = (BAR_H / 2f).toActual }

        // Панель існує лише поки щось діє — на старті рану її немає.
        isVisible = false
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------

    /** Стан бустів із рушія. Кличеться щокадру з HUD. */
    fun syncFrom(engine: RunEngine) {
        val boost = engine.activeBoost

        isVisible = boost != null
        if (boost == null) {
            lastBoost = null
            return
        }

        if (boost != lastBoost) {
            lastBoost = boost
            aHex.boost = boost
            aBar.fillColor.set(boost.info.color)
        }

        // Час, що збігає, — одне число: актор не рухається й не міняє розміру
        aBar.frac = engine.boostFrac
    }

    /** Новий ран — панелі немає, поки не підібрано таймерний буст. */
    fun reset() {
        isVisible = false
        lastBoost = null
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addHex() = add(aHex) { size(HEX_W, HEX_H); centerX(); topToTop() }
    private fun addBar() = add(aBar) { size(BAR_W, BAR_H); centerX(); bottomToBottom() }

}