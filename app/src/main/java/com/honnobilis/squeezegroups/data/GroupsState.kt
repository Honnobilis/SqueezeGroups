package com.honnobilis.squeezegroups.data

import kotlinx.serialization.Serializable

@Serializable
data class GroupsState(
    val groups: List<List<String>> = emptyList(),
    val standalone: List<String> = emptyList(),
    val unreachable: List<String> = emptyList()
) {
    val allPlayers: List<String>
        get() = (groups.flatten() + standalone + unreachable).distinct().sorted()
}

@Serializable
data class GetGroupsResponse(
    val success: Boolean,
    val state: GroupsState? = null,
    val error: String? = null
)

@Serializable
data class SetGroupsRequest(
    val groups: List<List<String>>,
    val mode: String
)

@Serializable
data class SetGroupsResponse(
    val success: Boolean,
    val groups: List<List<String>>? = null,
    val mode: String? = null,
    val error: String? = null
)
