package com.lewydo.orbitdash.game.data

import com.lewydo.orbitdash.util.log
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

// ----------------------------------------------------------------------------
// AppJson — ЄДИНИЙ безпечний Json для всієї персистентності
//
//   Усі save/load мають використовувати саме AppJson (не дефолтний Json),
//   інакше зміни схеми PlayerData можуть крашити гру старих юзерів.
//
//   ignoreUnknownKeys  — видалені поля в новій версії НЕ крашать старий save
//   coerceInputValues  — null/невалідне значення → дефолт (а не виняток)
//   isLenient          — толерантний парсинг
//   encodeDefaults     — дефолти теж пишуться у файл (стабільний формат)
// ----------------------------------------------------------------------------

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient         = true
    encodeDefaults    = true
}

// ----------------------------------------------------------------------------
// Безпечне декодування
// ----------------------------------------------------------------------------
//
// Якщо JSON пошкоджений або несумісний (напр. змінили тип поля) — НЕ крашимо
// гру, а повертаємо default. Це остання лінія оборони; основний захист —
// дефолти полів + ignoreUnknownKeys + @SerialName при перейменуваннях.

fun <T> decodeOrDefault(
    deserializer: DeserializationStrategy<T>,
    raw         : String?,
    default     : T,
    tag         : String = "AppJson",
): T {
    if (raw.isNullOrBlank()) return default
    return try {
        AppJson.decodeFromString(deserializer, raw)
    } catch (e: Exception) {
        log("[$tag] decode failed (несумісний/пошкоджений save) → default. ${e.message}")
        default
    }
}