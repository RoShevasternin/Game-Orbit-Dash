package com.lewydo.orbitdash.game.utils.stateMachine

// ------------------------------------------------------------------------
// Context
// ------------------------------------------------------------------------
interface IStateMachine {
    fun setState(state: State)
    fun pushState(state: State)
    fun popState()
    fun dismiss()
    fun getCurrentState(): State?
}

// ------------------------------------------------------------------------
// State
// ------------------------------------------------------------------------
abstract class State {
    abstract val stateMachine: IStateMachine

    open fun onEnter() {}
    open fun onExit()  {}
}

// ------------------------------------------------------------------------
// StateMachine
// ------------------------------------------------------------------------
class StateMachine : IStateMachine {

    private var current: State? = null
    private val stack = ArrayDeque<State>()

    override fun setState(state: State) {
        exit()
        stack.clear()
        enter(state)
    }

    override fun pushState(state: State) {
        current?.let { stack.addLast(it) }
        enter(state)
    }

    override fun popState() {
        exit()
        val prev = stack.removeLastOrNull()
        if (prev != null) enter(prev) else current = null
    }

    override fun dismiss() {
        current?.onExit()
        current = stack.removeLastOrNull()
    }

    override fun getCurrentState() = current

    private fun enter(state: State) {
        current = state
        state.onEnter()
    }

    private fun exit() {
        current?.onExit()
    }
}