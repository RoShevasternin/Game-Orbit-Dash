package com.lewydo.orbitdash.game.data

import kotlinx.serialization.Serializable

@Serializable
data class PlayerData(
    // Версія схеми — підвищуй при НЕсумісних змінах (зміна типу поля тощо).
    // Сумісні зміни (нове поле з дефолтом) версію змінювати НЕ потребують.
    val schemaVersion : Int = PlayerDataMigration.CURRENT_VERSION,

    val xp     : Long = 0L,
    val skinId : Int  = 0,
)