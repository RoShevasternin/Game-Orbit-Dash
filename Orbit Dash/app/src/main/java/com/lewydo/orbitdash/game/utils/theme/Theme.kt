package com.lewydo.orbitdash.game.utils.theme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// ТЕМА = 5 КОЛЬОРІВ-РОЛЕЙ: bg, ring, player, gem, spike.
//
// Актори НЕ тримають своїх кольорів — щокадру читають ThemeManager.current
// (див. syncTheme() в ABall/AGem/AOrbitEmblem). Тому зміна теми — це один
// виклик switchTo(id): current сам плавно перелерпається за ~0.35 с,
// і все, що намальоване білими спрайтами/шейдерами, перефарбується.
//
// Нова тема = один рядок у palettes. Нуль нових ассетів.
// ─────────────────────────────────────────────────────────────────────────────

class Palette(
    val name: String,
    val cost: Int,
    bg: String, ring: String, player: String, gem: String, spike: String,
) {
    val bg     : Color = Color.valueOf(bg)
    val ring   : Color = Color.valueOf(ring)
    val player : Color = Color.valueOf(player)
    val gem    : Color = Color.valueOf(gem)
    val spike  : Color = Color.valueOf(spike)
}

/** Мутабельний набір ролей — те, що читає рендер щокадру. */
class ThemeColors {
    val bg     = Color()
    val ring   = Color()
    val player = Color()
    val gem    = Color()
    val spike  = Color()

    fun set(p: Palette) {
        bg.set(p.bg); ring.set(p.ring); player.set(p.player); gem.set(p.gem); spike.set(p.spike)
    }

    fun lerp(p: Palette, t: Float) {
        bg.lerp(p.bg, t); ring.lerp(p.ring, t); player.lerp(p.player, t)
        gem.lerp(p.gem, t); spike.lerp(p.spike, t)
    }
}

object ThemeManager {

    val palettes = listOf(
        Palette("NEON",   0,   "0E1024", "2B3060", "00E5FF", "FFD54A", "FF3D68"),
        Palette("SUNSET", 150, "241220", "542A44", "FF9E4D", "FFE08A", "FF4D6D"),
        Palette("TOXIC",  150, "0C1A10", "235C31", "7DFF5E", "EAFF5E", "FF5E5E"),
        Palette("ICE",    150, "0E1622", "2B4A6F", "BFE9FF", "6EC6FF", "FF7B9C"),
        Palette("SYNTH",  250, "160222", "4A1566", "FF3EC8", "19F7E2", "B44DFF"),
        Palette("MAGMA",  250, "190805", "58221A", "FF8B2E", "FFD76B", "FF2E55"),
        Palette("VOID",   400, "070310", "2A1B52", "A86BFF", "64F0C8", "FF4D8D"),
        Palette("GOLD",   600, "14100A", "5C4A22", "FFE27A", "7AE0FF", "FF5470"),
    )

    /** Читай це в акторах. Уже проінтерпольоване. */
    val current = ThemeColors()

    var currentId = 0
        private set

    private var target = palettes[0]
    private const val LERP_SPEED = 8f   // ≈0.35 с на повний перехід

    /** Чи існує така палітра — використовуй перед збереженням id. */
    fun isValidId(id: Int) = id in palettes.indices

    /** Ім'я палітри для аналітики/UI. Кривий id ковтаємо, як і всюди тут. */
    fun nameOf(id: Int) = palettes[id.coerceIn(0, palettes.lastIndex)].name

    /** Миттєво, без анімації: старт гри з уже завантаженим save. */
    fun initWith(id: Int) {
        currentId = id.coerceIn(0, palettes.lastIndex)
        target = palettes[currentId]
        current.set(target)
    }

    /** Гравець вибрав скін — перехід доанімується сам в update(). */
    fun switchTo(id: Int) {
        currentId = id.coerceIn(0, palettes.lastIndex)
        target = palettes[currentId]
    }

    /** Раз на кадр, поруч із ShaderClock.update() у GDXGame.render(). */
    fun update(delta: Float = Gdx.graphics.deltaTime) {
        current.lerp(target, (LERP_SPEED * delta).coerceAtMost(1f))
    }
}