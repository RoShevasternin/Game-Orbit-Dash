package com.lewydo.orbitdash.game.actors.layout.constraintLayout

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable

/**
 * Невидимий актор-якір: існує лише як набір координат для констрейнтів.
 *
 * Два підвиди: TARGET — межі задає лейаут (add(anchor) { … }); MIRROR
 * (AAnchorOf) — межі виводяться з чужого актора. Не взаємозамінні.
 */
open class AAnchor : Actor() {
    init {
        isVisible = false
        touchable = Touchable.disabled
    }
}