package com.lewydo.orbitdash.game.utils.debug

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import kotlin.math.max

// ═════════════════════════════════════════════════════════════════════════════
//  ПЕРЕТЯГ СЛУЖБОВИХ ОВЕРЛЕЇВ — щоб дебаг не затуляв те, що дивишся.
//
//    aStatsLbl.dragToMove("${screen::class.simpleName}.hud")
//
//  ТРИ РЕЧІ, ЯКІ ТУТ НЕОЧЕВИДНІ:
//
//  1. ПЕРЕД ПЕРШИМ ЗСУВОМ АКТОРА ЗНІМАЄМО З ЛЕЙАУТУ (detach). Інакше найближчий
//     resolve поверне його на місце: констрейнти переприкладаються на зміну
//     розміру групи І на зміну розміру дитини. HUD міняє текст раз на секунду —
//     напис стрибав би назад у куток прямо під пальцем.
//
//  2. НА ПОЧАТКУ ДРАГУ ЗАБИРАЄМО ТАЧ У РЕШТИ СЛУХАЧІВ (cancelTouchFocusExcept).
//     Палець стоїть нерухомо ВІДНОСНО кнопки — панель їде разом із ним, і
//     власний поріг кнопки (scrollThreshold) не спрацьовує. Без цього кожен
//     перетяг закінчувався б натисканням кнопки, за яку тягнеш.
//
//  3. ЗСУВ РАХУЄМО ВІД ТОЧКИ ЗАХОПЛЕННЯ (x - dragStartX), а не від попереднього
//     кадру. Актор їде за пальцем, тож його локальні координати після кожного
//     moveBy повертаються до точки захоплення — дельта виходить чиста, без
//     накопичення похибки.
//
//  Позиція лежить у Preferences під ключем «екран + оверлей»: пережила
//  install -r — і оверлей там, де ти його лишив.
// ═════════════════════════════════════════════════════════════════════════════

/** Поріг початку драгу, дизайн-одиниці: менше — тягнеться від будь-якого дотику. */
private const val DRAG_START = 6f

/** Прозорість під час перетягу — видно, що актор «у руці». */
private const val DRAG_ALPHA = 0.55f

/**
 * Дозволяє тягати актора пальцем у межах його батька.
 * [key] — під ним позиція лягає в Preferences; має бути унікальним на екран.
 */
fun Actor.dragToMove(key: String) {
    addListener(DragMoveListener(this, key))

    val saved = DebugLayout.load(key) ?: return

    // Відновлюємо на першому act(): на момент add() панель ще HUG-нульова,
    // а батько може не мати розміру — clamp зіпсував би позицію.
    addAction(Actions.run {
        detachFromLayout()
        setPosition(saved.x, saved.y)
        clampInsideParent()
    })
}

private class DragMoveListener(
    private val target: Actor,
    private val key   : String,
) : DragListener() {

    private var savedAlpha = 1f

    init { tapSquareSize = DRAG_START }

    override fun dragStart(event: InputEvent, x: Float, y: Float, pointer: Int) {
        target.detachFromLayout()
        event.stage?.cancelTouchFocusExcept(this, event.listenerActor)

        savedAlpha = target.color.a
        target.color.a = DRAG_ALPHA
    }

    override fun drag(event: InputEvent, x: Float, y: Float, pointer: Int) {
        target.moveBy(x - dragStartX, y - dragStartY)
        target.clampInsideParent()
    }

    override fun dragStop(event: InputEvent, x: Float, y: Float, pointer: Int) {
        target.color.a = savedAlpha
        DebugLayout.save(key, target.x, target.y)
    }
}

/** Зняти з керування констрейнтами, лишивши дитиною групи. */
private fun Actor.detachFromLayout() {
    (parent as? AConstraintLayout)?.detach(this)
}

/** Не даємо заїхати за край: оверлей завжди лишається повністю на екрані. */
private fun Actor.clampInsideParent() {
    val p = parent ?: return
    if (p.width <= 0f || p.height <= 0f) return

    x = x.coerceIn(0f, max(0f, p.width  - width))
    y = y.coerceIn(0f, max(0f, p.height - height))
}

/**
 * Де лежать оверлеї. Окремий файл Preferences — щоб службові позиції ніколи
 * не змішувались із даними гравця.
 *
 * Preferences НЕ кешуємо в полі: об'єкт прив'язаний до Gdx.app, а Android може
 * перестворити Activity, лишивши процес живим. Gdx.app.getPreferences() і так
 * віддає той самий інстанс — кеш тут не економить, лише протухає.
 */
object DebugLayout {

    private const val FILE = "orbit_dash_debug_layout"

    private val prefs get() = Gdx.app.getPreferences(FILE)

    fun save(key: String, x: Float, y: Float) = prefs.run {
        putFloat("$key.x", x)
        putFloat("$key.y", y)
        flush()
    }

    fun load(key: String): Vector2? = prefs.run {
        if (!contains("$key.x")) null
        else Vector2(getFloat("$key.x"), getFloat("$key.y"))
    }

    /** Скинути все — оверлеї повернуться на місця з хелперів. */
    fun reset() = prefs.run { clear(); flush() }
}