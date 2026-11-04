package dev.josemiguel.launcher

import org.json.JSONArray
import org.json.JSONObject

/**
 * A named set of apps and websites that share one daily time budget — e.g.
 * "social" holding Instagram + TikTok + X and instagram.com, capped at 45
 * minutes total. [sites] are domains, each covering its subdomains too.
 * [limitChangedAt] is the epoch-millis of the last budget edit; the budget can
 * only be changed once a week (see [Prefs.canChangeGroupLimit]).
 */
data class AppGroup(
    val name: String,
    val packages: Set<String>,
    val limitMinutes: Int,
    val limitChangedAt: Long,
    val sites: Set<String> = emptySet(),
)

fun List<AppGroup>.toGroupsJson(): String {
    val array = JSONArray()
    forEach { group ->
        array.put(
            JSONObject()
                .put("name", group.name)
                .put("packages", JSONArray(group.packages.toList()))
                .put("limit", group.limitMinutes)
                .put("changedAt", group.limitChangedAt)
                .put("sites", JSONArray(group.sites.toList()))
        )
    }
    return array.toString()
}

fun parseGroups(raw: String): List<AppGroup> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        val pkgs = o.getJSONArray("packages")
        val sites = o.optJSONArray("sites") ?: JSONArray()
        AppGroup(
            name = o.getString("name"),
            packages = (0 until pkgs.length()).map { pkgs.getString(it) }.toSet(),
            limitMinutes = o.getInt("limit"),
            limitChangedAt = o.getLong("changedAt"),
            sites = (0 until sites.length()).map { sites.getString(it) }.toSet(),
        )
    }
}
