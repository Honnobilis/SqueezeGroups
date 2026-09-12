@file:OptIn(ExperimentalMaterial3Api::class)

package com.honnobilis.squeezegroups.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.honnobilis.squeezegroups.data.GroupsState
import com.honnobilis.squeezegroups.mqtt.ConnectionStatus

private val NAME_COL_WIDTH = 150.dp
private val GROUP_COL_WIDTH = 64.dp

@Composable
fun HomeScreen(
    uiState: UiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onApplyGroups: (List<List<String>>, GroupMode) -> Unit,
    onOpenSettings: () -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // 0 = no group. 1..groupCount = column index.
    val assignment = remember { mutableStateMapOf<String, Int>() }
    var groupCount by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) } // 0 = targeted, 1 = world

    LaunchedEffect(uiState.groupsState) {
        val state = uiState.groupsState ?: return@LaunchedEffect
        assignment.clear()
        state.groups.forEachIndexed { index, players ->
            players.forEach { assignment[it] = index + 1 }
        }
        state.standalone.forEach { assignment.putIfAbsent(it, 0) }
        state.unreachable.forEach { assignment.putIfAbsent(it, 0) }
        groupCount = state.groups.size
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SqueezeGroups") },
                actions = {
                    IconButton(onClick = onRefresh, enabled = uiState.connectionStatus is ConnectionStatus.Connected) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionBar(uiState.connectionStatus, onConnect, onDisconnect)

            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            CurrentStateCard(uiState.groupsState)

            HorizontalDivider()

            val players = uiState.groupsState?.allPlayers ?: emptyList()
            val unreachablePlayers = uiState.groupsState?.unreachable?.toSet() ?: emptySet()

            GroupBuilderTable(
                players = players,
                unreachablePlayers = unreachablePlayers,
                groupCount = groupCount,
                assignment = assignment,
                onAddGroup = { groupCount += 1 },
                onRemoveLastGroup = {
                    if (groupCount > 0) {
                        assignment.keys.filter { assignment[it] == groupCount }.forEach { assignment[it] = 0 }
                        groupCount -= 1
                    }
                }
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mode:")
                ModeButton("Targeted", mode == 0) { mode = 0 }
                ModeButton("World", mode == 1) { mode = 1 }
            }
            Text(
                if (mode == 0) {
                    "Targeted: only touches mentioned players; fails if one of them is already grouped with someone left out."
                } else {
                    "World: unsyncs every player first, then applies exactly the groups below."
                },
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    val groups = assignment.entries
                        .filter { it.value > 0 }
                        .groupBy { it.value }
                        .toSortedMap()
                        .values
                        .map { entries -> entries.map { it.key } }
                    onApplyGroups(groups, if (mode == 0) GroupMode.TARGETED else GroupMode.WORLD)
                },
                enabled = uiState.connectionStatus is ConnectionStatus.Connected && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply groups")
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun ConnectionBar(status: ConnectionStatus, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (label, color) = when (status) {
            is ConnectionStatus.Connected -> "Connected" to Color(0xFF2E7D32)
            is ConnectionStatus.Connecting -> "Connecting…" to Color(0xFFF9A825)
            is ConnectionStatus.Disconnected -> "Disconnected" to Color(0xFF9E9E9E)
            is ConnectionStatus.Failed -> "Failed: ${status.message}" to Color(0xFFC62828)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text("  $label")
        }
        if (status is ConnectionStatus.Connected) {
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        } else {
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun CurrentStateCard(state: GroupsState?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Current state", style = MaterialTheme.typography.titleMedium)
            if (state == null) {
                Text("No data yet — connect to load players.", style = MaterialTheme.typography.bodyMedium)
            } else {
                if (state.groups.isEmpty() && state.standalone.isEmpty() && state.unreachable.isEmpty()) {
                    Text("No players reported by LMS.", style = MaterialTheme.typography.bodyMedium)
                }
                state.groups.forEachIndexed { index, players ->
                    Text("Group ${index + 1}: ${players.joinToString(", ")}")
                }
                if (state.standalone.isNotEmpty()) {
                    Text("Standalone: ${state.standalone.joinToString(", ")}")
                }
                if (state.unreachable.isNotEmpty()) {
                    Text(
                        "Unreachable: ${state.unreachable.joinToString(", ")}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupBuilderTable(
    players: List<String>,
    unreachablePlayers: Set<String>,
    groupCount: Int,
    assignment: MutableMap<String, Int>,
    onAddGroup: () -> Unit,
    onRemoveLastGroup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Groups", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (groupCount > 0) {
                TextButton(onClick = onRemoveLastGroup) { Text("Remove last") }
            }
            Button(onClick = onAddGroup) { Text("+ Add group") }
        }

        if (players.isEmpty()) {
            Text("No players known yet — connect to load the player list.", style = MaterialTheme.typography.bodyMedium)
            return
        }

        Text(
            "Select a player in at most one group column. Unchecked players stay standalone.",
            style = MaterialTheme.typography.bodySmall
        )

        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Player", modifier = Modifier.width(NAME_COL_WIDTH), style = MaterialTheme.typography.labelLarge)
                for (col in 1..groupCount) {
                    Text(
                        "G$col",
                        modifier = Modifier.width(GROUP_COL_WIDTH),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            players.forEach { player ->
                val isUnreachable = player in unreachablePlayers
                val rowColor = if (isUnreachable) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    Color.Unspecified
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isUnreachable) "$player (unreachable)" else player,
                        color = rowColor,
                        modifier = Modifier.width(NAME_COL_WIDTH)
                    )
                    for (col in 1..groupCount) {
                        Box(modifier = Modifier.width(GROUP_COL_WIDTH), contentAlignment = Alignment.Center) {
                            Checkbox(
                                checked = assignment[player] == col,
                                onCheckedChange = { checked -> assignment[player] = if (checked) col else 0 },
                                enabled = !isUnreachable
                            )
                        }
                    }
                }
            }
        }
    }
}
