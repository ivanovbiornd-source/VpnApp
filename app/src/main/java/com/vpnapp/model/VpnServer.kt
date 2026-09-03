package com.vpnapp.model

import com.google.gson.annotations.SerializedName

data class VpnServer(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("country") val country: String,
    @SerializedName("flag") val flag: String,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("protocol") val protocol: String, // "wireguard", "openvpn", "proxy"
    @SerializedName("config") val config: String,     // base64-encoded config
    @SerializedName("ping") var ping: Int = -1,
    @SerializedName("load") val load: Int = 0,        // server load %
    var isSelected: Boolean = false
)

data class SubscriptionConfig(
    @SerializedName("version") val version: Int,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("subscription_url") val subscriptionUrl: String?,
    @SerializedName("servers") val servers: List<VpnServer>
)
