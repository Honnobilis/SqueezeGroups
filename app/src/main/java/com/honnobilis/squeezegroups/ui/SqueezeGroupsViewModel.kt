package com.honnobilis.squeezegroups.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.honnobilis.squeezegroups.data.GetGroupsResponse
import com.honnobilis.squeezegroups.data.GroupsState
import com.honnobilis.squeezegroups.data.MqttSettings
import com.honnobilis.squeezegroups.data.SetGroupsRequest
import com.honnobilis.squeezegroups.data.SetGroupsResponse
import com.honnobilis.squeezegroups.data.SettingsRepository
import com.honnobilis.squeezegroups.mqtt.ConnectionStatus
import com.honnobilis.squeezegroups.mqtt.MqttRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class GroupMode { TARGETED, WORLD }

data class UiState(
    val settings: MqttSettings = MqttSettings(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val groupsState: GroupsState? = null,
    val isLoading: Boolean = false,
    val message: String? = null
)

class SqueezeGroupsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val mqttRepository = MqttRepository()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            mqttRepository.status.collect { status ->
                _uiState.value = _uiState.value.copy(connectionStatus = status)
            }
        }
        viewModelScope.launch {
            val initial = settingsRepository.settingsFlow.first()
            if (initial.host.isNotBlank()) connect(initial)
        }
    }

    fun saveSettings(settings: MqttSettings) {
        viewModelScope.launch {
            settingsRepository.save(settings)
        }
        connect(settings)
    }

    fun connect(settingsOverride: MqttSettings? = null) {
        viewModelScope.launch {
            val settings = settingsOverride ?: _uiState.value.settings
            try {
                mqttRepository.connect(settings)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "Connection failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        mqttRepository.disconnect()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val raw = mqttRepository.request(
                    topic = MqttRepository.TOPIC_GROUPS_GET,
                    responseTopic = MqttRepository.TOPIC_GROUPS_GET_RESPONSE,
                    payload = "{}"
                )
                val response = json.decodeFromString<GetGroupsResponse>(raw)
                if (response.success && response.state != null) {
                    _uiState.value = _uiState.value.copy(groupsState = response.state, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = response.error ?: "Unknown error fetching state"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Refresh failed: ${e.message}")
            }
        }
    }

    fun applyGroups(groups: List<List<String>>, mode: GroupMode) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val request = SetGroupsRequest(groups = groups, mode = mode.name.lowercase())
                val raw = mqttRepository.request(
                    topic = MqttRepository.TOPIC_GROUPS_SET,
                    responseTopic = MqttRepository.TOPIC_GROUPS_SET_RESPONSE,
                    payload = json.encodeToString(request)
                )
                val response = json.decodeFromString<SetGroupsResponse>(raw)
                if (response.success) {
                    _uiState.value = _uiState.value.copy(isLoading = false, message = "Groups applied")
                    refresh()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = response.error ?: "Unknown error applying groups"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Apply failed: ${e.message}")
            }
        }
    }

    fun messageShown() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        mqttRepository.disconnect()
    }
}
