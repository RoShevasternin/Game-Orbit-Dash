package com.lewydo.orbitdash.game.actors.layout.constraintLayout

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import kotlin.math.abs
import kotlin.math.min

// ═════════════════════════════════════════════════════════════════════════════
//  AAnchorOf — MIRROR-якір: повторює межі актора з ІНШОЇ групи.
//
//  Констрейнти читають anchor.x/y як координати у СВОЄМУ лейауті. Чужий актор
//  віддає координати свого батька, тому пряме topToBottom(aMain.aTitle2Lbl)
//  дало б безглузді числа. Місток переводить кути через stage — і разом з ними
//  враховує вкладеність, зсув та scale проміжних груп.
//
//  ДОДАВАТИ ПІСЛЯ ДЖЕРЕЛА: Group.act() йде по дітях у порядку додавання, тож
//  місток, доданий раніше за групу джерела, відстає на кадр (самовиправляється).
//
//  ЖИТТЄВИЙ ЦИКЛ:
//    source зник       → межі ЗАСТИГАЮТЬ; для лейаута місток живий і нерухомий
//    місток зняли      → AConstraintLayout.childrenChanged() обнулить посилання,
//                        вузол залежного лишиться живим
//    Тримає СИЛЬНЕ посилання на source — знімай разом із екраном.
// ═════════════════════════════════════════════════════════════════════════════
class AAnchorOf(private val source: Actor) : AAnchor() {

    companion object {
        /**
         * Поріг реакції. Round-trip localToStage → stageToLocal через предків зі
         * скейлом дає float-дрейф і може ОСЦИЛЮВАТИ між двома значеннями.
         * checkAnchors() порівнює точним !=, тож без порогу вузол був би dirty
         * щокадру — вічний перерахунок лейаута на нерухомій сцені.
         */
        private const val EPS = 0.01f
    }

    private val p0 = Vector2()
    private val p1 = Vector2()

    override fun act(delta: Float) {
        super.act(delta)

        val holder = parent ?: return
        if (source.stage == null) return   // джерело ще (або вже) не на сцені

        // Швидкий шлях: спільний батько без трансформацій — координати вже наші,
        // жодного round-trip, а отже й жодного дрейфу.
        if (source.parent === holder &&
            source.scaleX == 1f && source.scaleY == 1f && source.rotation == 0f
        ) {
            applyBounds(source.x, source.y, source.width, source.height)
            return
        }

        p0.set(0f, 0f)
        source.localToStageCoordinates(p0)
        holder.stageToLocalCoordinates(p0)

        p1.set(source.width, source.height)
        source.localToStageCoordinates(p1)
        holder.stageToLocalCoordinates(p1)

        applyBounds(
            min(p0.x, p1.x), min(p0.y, p1.y),
            abs(p1.x - p0.x), abs(p1.y - p0.y),
        )
    }

    private fun applyBounds(nx: Float, ny: Float, nw: Float, nh: Float) {
        if (abs(x - nx) > EPS || abs(y - ny) > EPS ||
            abs(width - nw) > EPS || abs(height - nh) > EPS
        ) setBounds(nx, ny, nw, nh)
    }
}