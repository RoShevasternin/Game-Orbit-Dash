package com.lewydo.orbitdash.game.actors

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup

// ═════════════════════════════════════════════════════════════════════════════
//  AScrollPane
//
//  Розумний ScrollPane який автоматично реагує на зміну розміру контенту.
//  Не треба викликати invalidate() вручну ззовні.
//
//  ПРОБЛЕМА стандартного ScrollPane:
//    Він кешує розмір контенту і не перераховує layout автоматично
//    коли контент змінює розмір. Позиції елементів оновлюються тільки
//    при скролі пальцем.
//
//  РІШЕННЯ:
//    act() кожен кадр порівнює поточний w/h контенту зі snapshot.
//    Якщо змінився → invalidate() → layout() перерахує позиції.
//
//  ВИКОРИСТАННЯ:
//    val aScrollPane = AScrollPane(aContentVerticalGroup)
//    // Все — більше нічого не треба. Контент змінює розмір → скрол реагує.
// ═════════════════════════════════════════════════════════════════════════════

class AScrollPane(
    val content: AdvancedGroup,
    scrollX: Boolean = false,
    scrollY: Boolean = true,
) : ScrollPane(content), Disposable {

    private var lastW = -1f
    private var lastH = -1f

    init {
        setScrollingDisabled(!scrollX, !scrollY)
        setOverscroll(false, false)
        setFadeScrollBars(false)
    }

    override fun act(delta: Float) {
        super.act(delta)

        val w = content.width
        val h = content.height

        if (w != lastW || h != lastH) {
            lastW = w
            lastH = h
            invalidate()
        }
    }

    override fun dispose() {
        content.dispose()
    }
}