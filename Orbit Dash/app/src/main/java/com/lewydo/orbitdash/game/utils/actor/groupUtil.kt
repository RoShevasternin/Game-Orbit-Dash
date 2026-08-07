package com.lewydo.orbitdash.game.utils.actor

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.lewydo.orbitdash.game.actors.layout.AlignH
import com.lewydo.orbitdash.game.actors.layout.AlignV

// ------------------------------------------------------------------------
// Add Actors
// ------------------------------------------------------------------------
fun Group.addAndFillActor(actor: Actor) {
    addActor(actor)
    actor.setSize(width, height)
}

fun Group.addAndFillActors(actors: List<Actor>) { actors.forEach { addAndFillActor(it) } }
fun Group.addAndFillActors(vararg actors: Actor) { actors.forEach { addAndFillActor(it) } }
fun Group.addActors(vararg actors: Actor)        { actors.forEach { addActor(it) } }
fun Group.addActors(actors: List<Actor>)         { actors.forEach { addActor(it) } }

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

fun Group.addActorAligned(actor: Actor, h: AlignH = AlignH.LEFT, v: AlignV = AlignV.BOTTOM) {
    addActor(actor)
    val x = when (h) {
        AlignH.LEFT   -> 0f
        AlignH.CENTER -> (width - actor.width) / 2f
        AlignH.RIGHT  -> width - actor.width
        AlignH.SPREAD -> 0f
    }
    val y = when (v) {
        AlignV.BOTTOM -> 0f
        AlignV.CENTER -> (height - actor.height) / 2f
        AlignV.TOP    -> height - actor.height
        AlignV.SPREAD -> 0f
    }
    actor.setPosition(x, y)
}

fun Group.addActorWithConstraints(actor: Actor, block: ConstraintLayoutParams.() -> Unit) {
    val lp = ConstraintLayoutParams().apply(block)
    addActor(actor)

    val w = actor.width
    val h = actor.height

    require(w > 0 && h > 0) { "Спочатку встанови width and height!" }

    // Допоміжна функція для визначення координат відносно батька
    fun getLeft(target: Actor): Float = if (target == this) 0f else target.x
    fun getRight(target: Actor): Float = if (target == this) width else target.x + target.width
    fun getBottom(target: Actor): Float = if (target == this) 0f else target.y
    fun getTop(target: Actor): Float = if (target == this) height else target.y + target.height

    // ---------- HORIZONTAL ----------
    val x = when {
        // Центрування між двома межами (Bias)
        lp.startToStartOf != null && lp.endToEndOf != null -> {
            val left = getLeft(lp.startToStartOf!!) + lp.marginStart
            val right = getRight(lp.endToEndOf!!) - lp.marginEnd
            left + (right - left - w) * lp.horizontalBias
        }
        lp.startToEndOf != null && lp.endToStartOf != null -> {
            val left = getRight(lp.startToEndOf!!) + lp.marginStart
            val right = getLeft(lp.endToStartOf!!) - lp.marginEnd
            left + (right - left - w) * lp.horizontalBias
        }
        // Одиночні прив'язки
        lp.startToStartOf != null -> getLeft(lp.startToStartOf!!) + lp.marginStart
        lp.startToEndOf   != null -> getRight(lp.startToEndOf!!) + lp.marginStart
        lp.endToStartOf   != null -> getLeft(lp.endToStartOf!!) - w - lp.marginEnd
        lp.endToEndOf     != null -> getRight(lp.endToEndOf!!) - w - lp.marginEnd
        else -> lp.marginStart
    }

    // ---------- VERTICAL ----------
    val y = when {
        // Центрування (наприклад, між іншим актором і низом екрана)
        (lp.topToTopOf != null || lp.topToBottomOf != null) && (lp.bottomToTopOf != null || lp.bottomToBottomOf != null) -> {
            val topBound = when {
                lp.topToTopOf != null -> getTop(lp.topToTopOf!!) - lp.marginTop
                else -> getBottom(lp.topToBottomOf!!) - lp.marginTop
            }
            val bottomBound = when {
                lp.bottomToBottomOf != null -> getBottom(lp.bottomToBottomOf!!) + lp.marginBottom
                else -> getTop(lp.bottomToTopOf!!) + lp.marginBottom
            }
            bottomBound + (topBound - bottomBound - h) * lp.verticalBias
        }
        // Одиночні прив'язки
        lp.topToTopOf       != null -> getTop(lp.topToTopOf!!) - h - lp.marginTop
        lp.topToBottomOf    != null -> getBottom(lp.topToBottomOf!!) - h - lp.marginTop
        lp.bottomToTopOf    != null -> getTop(lp.bottomToTopOf!!) + lp.marginBottom
        lp.bottomToBottomOf != null -> getBottom(lp.bottomToBottomOf!!) + lp.marginBottom
        else -> lp.marginBottom
    }

    actor.setPosition(x, y)
}




class ConstraintLayoutParams {
    // ---------- HORIZONTAL ----------
    var startToStartOf: Actor? = null
    var startToEndOf  : Actor? = null
    var endToStartOf  : Actor? = null
    var endToEndOf    : Actor? = null

    var horizontalBias: Float = 0.5f // 0 = start, 1 = end, 0.5 = center
        set(value) { field = value.coerceIn(0f,1f) }

    var marginStart: Float = 0f
    var marginEnd  : Float = 0f

    // ---------- VERTICAL ----------
    var topToTopOf      : Actor? = null
    var topToBottomOf   : Actor? = null
    var bottomToTopOf   : Actor? = null
    var bottomToBottomOf: Actor? = null

    var verticalBias: Float = 0.5f // 0 = bottom, 1 = top, 0.5 = center
        set(value) { field = value.coerceIn(0f,1f) }

    var marginTop   : Float = 0f
    var marginBottom: Float = 0f
}