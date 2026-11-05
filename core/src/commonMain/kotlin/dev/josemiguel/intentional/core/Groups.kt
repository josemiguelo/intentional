package dev.josemiguel.intentional.core

/**
 * The budget rules over a list of groups, kept free of storage: each change
 * returns the new list for the caller to save. An app or site belongs to at
 * most one group; apps and sites in no group are unlimited.
 */
object Budget {
    const val MAX_LIMIT_MINUTES = 60
    const val MIN_LIMIT_MINUTES = 5
    const val LIMIT_CHANGE_COOLDOWN_MS = 7L * DAY_MS
}

private const val DAY_MS = 86_400_000L

/** Whether [domain] covers [host]: the site itself or any subdomain of it. */
fun siteCovers(domain: String, host: String): Boolean =
    host == domain || host.endsWith(".$domain")

/** The domain a host is stored as: "m.facebook.com" means the site, not just its mobile host. */
fun siteDomain(host: String): String = host.removePrefix("m.").takeIf { '.' in it } ?: host

fun List<AppGroup>.groupOf(packageName: String): AppGroup? =
    firstOrNull { packageName in it.packages }

fun List<AppGroup>.groupOfSite(host: String): AppGroup? =
    firstOrNull { g -> g.sites.any { siteCovers(it, host) } }

/** Adds an empty group at the maximum budget, unless the name is blank or taken. */
fun List<AppGroup>.withGroup(name: String): List<AppGroup> {
    if (name.isBlank() || any { it.name.equals(name.trim(), ignoreCase = true) }) return this
    return this + AppGroup(name.trim(), emptySet(), Budget.MAX_LIMIT_MINUTES, 0L)
}

fun List<AppGroup>.withoutGroup(name: String): List<AppGroup> = filterNot { it.name == name }

/** Moves an app into [groupName], or out of every group if null. */
fun List<AppGroup>.withApp(packageName: String, groupName: String?): List<AppGroup> = map { g ->
    val without = g.packages - packageName
    if (g.name == groupName) g.copy(packages = without + packageName) else g.copy(packages = without)
}

/** Moves a domain into [groupName], or out of every group if null. */
fun List<AppGroup>.withSite(domain: String, groupName: String?): List<AppGroup> = map { g ->
    val without = g.sites - domain
    if (g.name == groupName) g.copy(sites = without + domain) else g.copy(sites = without)
}

fun List<AppGroup>.withoutSite(groupName: String, domain: String): List<AppGroup> =
    map { g -> if (g.name == groupName) g.copy(sites = g.sites - domain) else g }

fun AppGroup.canChangeLimit(nowMs: Long): Boolean =
    nowMs - limitChangedAt >= Budget.LIMIT_CHANGE_COOLDOWN_MS

/** Whole days until the budget can change again, rounded up; 0 when it already can. */
fun AppGroup.daysUntilChangeable(nowMs: Long): Int {
    val remaining = Budget.LIMIT_CHANGE_COOLDOWN_MS - (nowMs - limitChangedAt)
    return ((remaining + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(0)
}

/** Sets a group's budget, clamped, unless it changed within the last week. */
fun List<AppGroup>.withLimit(name: String, minutes: Int, nowMs: Long): List<AppGroup> = map { g ->
    if (g.name == name && g.canChangeLimit(nowMs)) {
        g.copy(
            limitMinutes = minutes.coerceIn(Budget.MIN_LIMIT_MINUTES, Budget.MAX_LIMIT_MINUTES),
            limitChangedAt = nowMs,
        )
    } else g
}
