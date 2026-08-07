package com.lewydo.orbitdash.game.utils.overlay

class OverlayManager(
    private val onShowDim: () -> Unit,
    private val onHideDim: () -> Unit,
) {

    // ------------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------------

    data class Config(
        val showDim   : Boolean  = true,  // затемнювати фон?
        val isClosable: Boolean  = true,  // закривати по кліку на dim?
        val onShow    : () -> Unit,
        val onHide    : () -> Unit,
    )

    // ------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------

    private val registry  = mutableMapOf<Enum<*>, Config>()
    private var currentKey: Enum<*>? = null

    val isClosable: Boolean get() = registry[currentKey]?.isClosable ?: true
    val isVisible : Boolean get() = currentKey != null

    // ------------------------------------------------------------------------
    // Register
    // ------------------------------------------------------------------------

    fun register(key: Enum<*>, config: Config) {
        registry[key] = config
    }

    // ------------------------------------------------------------------------
    // Show
    // ------------------------------------------------------------------------

    fun show(key: Enum<*>) {
        if (currentKey == key) return

        val next = registry[key] ?: return
        val prev = registry[currentKey]

        prev?.onHide?.invoke()

        // Dim не мигає якщо обидва оверлеї його потребують
        when {
            prev == null  && next.showDim                  -> onShowDim()
            prev != null  && prev.showDim && !next.showDim -> onHideDim()
            prev != null  && !prev.showDim && next.showDim -> onShowDim()
            // prev.showDim && next.showDim → dim вже є, нічого не робимо
        }

        currentKey = key
        next.onShow()
    }

    // ------------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------------

    fun close() {
        val config = registry[currentKey] ?: return
        config.onHide()
        if (config.showDim) onHideDim()
        currentKey = null
    }

}