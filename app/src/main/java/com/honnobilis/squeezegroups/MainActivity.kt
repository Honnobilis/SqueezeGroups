package com.honnobilis.squeezegroups

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.honnobilis.squeezegroups.ui.HomeScreen
import com.honnobilis.squeezegroups.ui.SettingsScreen
import com.honnobilis.squeezegroups.ui.SqueezeGroupsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SqueezeGroupsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    val uiState by viewModel.uiState.collectAsState()

                    if (showSettings || uiState.settings.host.isBlank()) {
                        SettingsScreen(
                            settings = uiState.settings,
                            onSave = { settings ->
                                viewModel.saveSettings(settings)
                                showSettings = false
                            },
                            onCancel = { showSettings = false }
                        )
                    } else {
                        HomeScreen(
                            uiState = uiState,
                            onConnect = { viewModel.connect() },
                            onDisconnect = viewModel::disconnect,
                            onRefresh = viewModel::refresh,
                            onApplyGroups = viewModel::applyGroups,
                            onOpenSettings = { showSettings = true },
                            onMessageShown = viewModel::messageShown
                        )
                    }
                }
            }
        }
    }
}
