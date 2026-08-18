package com.lewydo.orbitdash.game.data

import kotlinx.serialization.Serializable

@Serializable
data class PlayerData(
    // Версія схеми — підвищуй при НЕсумісних змінах (зміна типу поля тощо).
    // Сумісні зміни (нове поле з дефолтом) версію змінювати НЕ потребують.
    val schemaVersion : Int = PlayerDataMigration.CURRENT_VERSION,

    /**
     * Стабільний анонімний ID гравця. Народжується один раз при першому
     * запуску, живе в сейві: переживає перевстановлення через бекап,
     * але НЕ прив'язаний до акаунта — це не PII. Споживачі: userId в
     * аналітиці, ключ лідерборда.
     */
    val pid    : String = "",

    val xp     : Long = 0L,
    val skinId : Int  = 0,
)