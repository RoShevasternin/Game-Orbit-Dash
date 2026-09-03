package com.lewydo.orbitdash.game.actors.panel.boost

import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

// ----------------------------------------------------------------------------
//  РЯД ЗАРЯДІВ ЩИТА.
//
//  СКІЛЬКИ КАПСУЛ ІСНУЄ — визначає МАКСИМУМ, досягнутий у цьому рані, не 3.
//  Три порожні клітинки на старті читаються як «тобі чогось не докупили» —
//  тиск без інформації. Прокачка 1 → одна капсула. Підібрав SHIELD → зʼявився
//  другий слот, і це маленький момент прогресу.
//
//  Порожній слот завжди означає «тут БУВ заряд»: витрата гасить капсулу, але
//  не прибирає її, інакше панель смикала б шириною при кожному ударі.
//
//  ХОВАТИ ЧЕРЕЗ isVisible НЕ МОЖНА: AAutoLayout не перевіряє видимість, і
//  невидима дитина далі займає місце. Тому капсули саме додаються й
//  прибираються — самі обʼєкти при цьому живуть увесь ран.
//
//  Ширина HUG: панель рівно така, скільки капсул зараз показано. У парі з
//  endToEnd() у HUD це тримає ряд притиснутим до правого краю незалежно
//  від кількості.
// ----------------------------------------------------------------------------
class APanelShield(override val screen: AdvancedScreen) : AAutoLayout(
    screen    = screen,
    direction = Direction.HORIZONTAL,
    alignMain = AlignMain.CENTER,
    gapMain   = 8f,
) {

    companion object {
        const val MAX_SLOTS = 3

        private const val PIP_W = 16f
        private const val PIP_H = 10f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.Y, PIP_H)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    /** Пул: обʼєкти створюються один раз, у лейаут потрапляють за потреби. */
    private val listShieldPip = List(MAX_SLOTS) { AShieldPip(screen) }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    // -1, щоб перший же syncFrom(0, 0) точно відпрацював і побудував склад.
    private var slots  = -1
    private var filled = -1

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        // Порожньо на старті: поки щита не було, панелі не існує.
        applySlots(0)
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (listShieldPip.first().parent == null) return
        listShieldPip.forEach { it.setSizeScaled(PIP_W, PIP_H) }
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------

    /**
     * Стан щита з рушія. Кличеться щокадру — ранній вихід робить це дешевим.
     *
     * @param max     скільки зарядів було максимум у цьому рані (0..3)
     * @param current скільки зараз горить
     */
    fun syncFrom(max: Int, current: Int) {
        val newSlots  = max.coerceIn(0, MAX_SLOTS)
        val newFilled = current.coerceIn(0, newSlots)

        if (newSlots == slots && newFilled == filled) return

        // Витрата заряду — блимаємо тією капсулою, що щойно згасла.
        // Визначаємо за переходом, а не подією: панель лишається чистим
        // споживачем стану і не тягне на себе колбеки рушія.
        val isSpent = newSlots == slots && newFilled == filled - 1

        if (newSlots != slots) applySlots(newSlots)

        slots  = newSlots
        filled = newFilled

        applyFilled(newFilled)

        if (isSpent) listShieldPip.getOrNull(newFilled)?.animFlash()
    }

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------

    /** Перебудова складу. Обʼєкти не перестворюються — лише міняють батька. */
    private fun applySlots(count: Int) {
        clearChildren()

        for (i in 0 until count) {
            val pip = listShieldPip[i]
            pip.setSizeScaled(PIP_W, PIP_H)
            add(pip)
        }
    }

    private fun applyFilled(count: Int) {
        for (i in 0 until slots) {
            listShieldPip[i].setState(
                if (i < count) AShieldPip.State.FULL else AShieldPip.State.EMPTY
            )
        }
    }

}