package com.honnobilis.squeezegroups.data

data class MqttSettings(
    val host: String = "",
    val port: Int = 1883,
    val useTls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val clientId: String = "squeezegroups-android"
) {
    val serverUri: String
        get() = "${if (useTls) "ssl" else "tcp"}://$host:$port"
}
