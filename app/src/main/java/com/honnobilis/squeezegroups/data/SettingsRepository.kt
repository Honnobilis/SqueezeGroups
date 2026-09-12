package com.honnobilis.squeezegroups.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "squeezegroups_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val USE_TLS = booleanPreferencesKey("use_tls")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val CLIENT_ID = stringPreferencesKey("client_id")
    }

    val settingsFlow: Flow<MqttSettings> = context.dataStore.data.map { prefs ->
        MqttSettings(
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: 1883,
            useTls = prefs[Keys.USE_TLS] ?: false,
            username = prefs[Keys.USERNAME] ?: "",
            password = prefs[Keys.PASSWORD] ?: "",
            clientId = prefs[Keys.CLIENT_ID] ?: "squeezegroups-android"
        )
    }

    suspend fun save(settings: MqttSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOST] = settings.host
            prefs[Keys.PORT] = settings.port
            prefs[Keys.USE_TLS] = settings.useTls
            prefs[Keys.USERNAME] = settings.username
            prefs[Keys.PASSWORD] = settings.password
            prefs[Keys.CLIENT_ID] = settings.clientId
        }
    }
}
