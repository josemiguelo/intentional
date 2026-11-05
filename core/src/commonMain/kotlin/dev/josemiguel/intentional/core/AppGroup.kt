package dev.josemiguel.intentional.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A named set of apps and websites that share one daily time budget — e.g.
 * "social" holding Instagram + TikTok + X and instagram.com, capped at 45
 * minutes total. [sites] are domains, each covering its subdomains too.
 * [limitChangedAt] is the epoch-millis of the last budget edit; the budget can
 * only be changed once a week (see [canChangeLimit]).
 */
@Serializable
data class AppGroup(
    val name: String,
    val packages: Set<String>,
    @SerialName("limit") val limitMinutes: Int,
    @SerialName("changedAt") val limitChangedAt: Long,
    val sites: Set<String> = emptySet(),
)

// The stored format predates this module: keep writing every field, and read
// groups saved before sites existed.
private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val groupsSerializer = ListSerializer(AppGroup.serializer())

fun List<AppGroup>.toGroupsJson(): String = json.encodeToString(groupsSerializer, this)

fun parseGroups(raw: String): List<AppGroup> = json.decodeFromString(groupsSerializer, raw)
