package com.lewydo.orbitdash.game.utils.theme

// ─────────────────────────────────────────────────────────────────────────────
// ThemeSync — перефарбувати актора, лише коли тема справді змінилась.
//
//     private val themeSync = ThemeSync(::syncTheme)
//
//     override fun addActorsOnGroup() { …; themeSync.sync() }       // перший кадр — уже в кольорі
//     override fun act(delta: Float)  { super.act(delta); themeSync.sync() }
//
// sync() порівнює збережену ThemeManager.version з поточною: у спокої — нічого,
// під час лерпу скіна — щокадру. Перший виклик фарбує завжди.
//
// Чому не Action на акторі: press()/fadeParts()/анімації кличуть clearActions()
// і мовчки зняли б синхронізацію. Чому не підписка в ThemeManager: актори з
// пулів живуть поза сценою, відписку легко забути — а тут забувати нічого.
// ─────────────────────────────────────────────────────────────────────────────
class ThemeSync(private val apply: () -> Unit) {

    private var seenVersion = -1

    fun sync() {
        if (seenVersion == ThemeManager.version) return
        seenVersion = ThemeManager.version
        apply()
    }

    /** Колір залежить не лише від теми (override, variant) — перефарбувати на наступному sync(). */
    fun invalidate() { seenVersion = -1 }
}