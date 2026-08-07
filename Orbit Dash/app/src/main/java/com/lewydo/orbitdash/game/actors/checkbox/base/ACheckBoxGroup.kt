package com.lewydo.orbitdash.game.actors.checkbox.base

class ACheckBoxGroup {

    var currentCheckedCheckBox: ACheckBoxBase? = null
        private set

    // Програмно вибрати потрібний checkbox
    fun select(checkBox: ACheckBoxBase) {
        currentCheckedCheckBox?.uncheck(invokeBlock = false)
        currentCheckedCheckBox = checkBox
        checkBox.check(invokeBlock = true)
    }

    // Скинути вибір
    fun clear() {
        currentCheckedCheckBox?.uncheck(invokeBlock = false)
        currentCheckedCheckBox = null
    }

    // Чи є хоч один вибраний
    val hasSelection get() = currentCheckedCheckBox != null

    // Internal — викликається з ACheckBoxBase
    internal fun onChecked(checkBox: ACheckBoxBase) {
        currentCheckedCheckBox?.uncheck(invokeBlock = false)
        currentCheckedCheckBox = checkBox
    }
}