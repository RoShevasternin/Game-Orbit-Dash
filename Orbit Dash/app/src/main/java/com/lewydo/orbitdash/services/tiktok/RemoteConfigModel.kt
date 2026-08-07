package com.lewydo.orbitdash.services.tiktok

import com.google.gson.annotations.SerializedName

data class RemoteConfigModel(
    @SerializedName("tiktok") val tiktok: TikTokConfig? = null,
)

data class TikTokConfig(
    @SerializedName("app_id") val appIdRaw: String? = null,
    @SerializedName("secret") val secret: String? = null,
) {
    val appIds: List<String>
        get() = appIdRaw
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    // валідність: є хоча б один id і є secret
    val isValid: Boolean
        get() = appIds.isNotEmpty() && !secret.isNullOrBlank()
}