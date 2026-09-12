package com.honnobilis.squeezegroups.mqtt

import com.honnobilis.squeezegroups.data.MqttSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Failed(val message: String) : ConnectionStatus
}

class MqttNotConnectedException : Exception("Not connected to broker")

/**
 * Thin async wrapper around the LMS Group Manager MQTT API.
 * Only the request/response pairs currently used by the UI are wired up.
 */
class MqttRepository {

    private var client: IMqttAsyncClient? = null
    private val requestMutex = Mutex()
    private val pendingResponses = mutableMapOf<String, CompletableDeferred<String>>()

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status

    suspend fun connect(settings: MqttSettings) {
        disconnect()
        _status.value = ConnectionStatus.Connecting

        val newClient = MqttAsyncClient(settings.serverUri, settings.clientId, MemoryPersistence())
        client = newClient

        newClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                _status.value = ConnectionStatus.Connected
                if (reconnect) {
                    // Paho does not auto-resubscribe after a dropped-connection reconnect.
                    newClient.subscribe(RESPONSE_TOPICS.toTypedArray(), IntArray(RESPONSE_TOPICS.size) { 1 })
                }
            }

            override fun connectionLost(cause: Throwable?) {
                _status.value = ConnectionStatus.Failed(cause?.message ?: "Connection lost")
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                pendingResponses.remove(topic)?.complete(String(message.payload))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 10
            keepAliveInterval = 30
            if (settings.username.isNotBlank()) {
                userName = settings.username
                password = settings.password.toCharArray()
            }
        }

        try {
            awaitAction { listener -> newClient.connect(options, null, listener) }
            awaitAction { listener ->
                newClient.subscribe(RESPONSE_TOPICS.toTypedArray(), IntArray(RESPONSE_TOPICS.size) { 1 }, null, listener)
            }
            _status.value = ConnectionStatus.Connected
        } catch (e: Exception) {
            _status.value = ConnectionStatus.Failed(e.message ?: "Connect failed")
            client = null
            throw e
        }
    }

    fun disconnect() {
        val current = client ?: return
        client = null
        pendingResponses.values.forEach { it.cancel() }
        pendingResponses.clear()
        try {
            if (current.isConnected) current.disconnect()
        } catch (_: Exception) {
            // already disconnected
        }
        _status.value = ConnectionStatus.Disconnected
    }

    /** Publishes [payload] to [topic] and suspends until a message arrives on [responseTopic]. */
    suspend fun request(topic: String, responseTopic: String, payload: String, timeoutMs: Long = 10_000): String {
        val mqttClient = client ?: throw MqttNotConnectedException()

        return requestMutex.withLock {
            val deferred = CompletableDeferred<String>()
            pendingResponses[responseTopic] = deferred
            try {
                val message = MqttMessage(payload.toByteArray()).apply { qos = 1 }
                awaitAction { listener -> mqttClient.publish(topic, message, null, listener) }
                withTimeout(timeoutMs) { deferred.await() }
            } finally {
                pendingResponses.remove(responseTopic)
            }
        }
    }

    private suspend fun awaitAction(action: (IMqttActionListener) -> Unit) {
        suspendCancellableCoroutine<Unit> { cont ->
            action(object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    if (cont.isActive) cont.resumeWithException(exception ?: Exception("MQTT action failed"))
                }
            })
        }
    }

    companion object {
        const val TOPIC_GROUPS_GET = "lms/groups/get"
        const val TOPIC_GROUPS_GET_RESPONSE = "lms/groups/get/response"
        const val TOPIC_GROUPS_SET = "lms/groups/set"
        const val TOPIC_GROUPS_SET_RESPONSE = "lms/groups/set/response"

        val RESPONSE_TOPICS = listOf(TOPIC_GROUPS_GET_RESPONSE, TOPIC_GROUPS_SET_RESPONSE)
    }
}
