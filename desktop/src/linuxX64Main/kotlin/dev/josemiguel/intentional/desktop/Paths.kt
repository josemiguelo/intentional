package dev.josemiguel.intentional.desktop

/** Where the desktop guard keeps things, by the XDG base directories. */
object Paths {
    private val config get() = env("XDG_CONFIG_HOME") ?: "$home/.config"
    private val state get() = env("XDG_STATE_HOME") ?: "$home/.local/state"

    /** The groups, as the phone stores them; on a desktop "packages" are Hyprland window classes. */
    val groups get() = "$config/intentional/groups.json"

    val usageDir get() = "$state/intentional/usage"

    /** Sync's secrets (SyncConfig); without it the guard keeps to this machine. */
    val syncConfig get() = "$config/intentional/sync.json"

    /** Apps that open through a pause, with their friction (app id to Friction ordinal). */
    val considered get() = "$config/intentional/considered.json"

    val consideredSynced get() = "$state/intentional/considered-synced.json"

    /** The settings every device shares (wind-down, pause lengths); written by sync. */
    val settings get() = "$config/intentional/settings.json"

    val settingsSynced get() = "$state/intentional/settings-synced.json"

    /** The groups as last synced: what tells a local edit from a remote one. */
    val groupsSynced get() = "$state/intentional/groups-synced.json"

    /** The other devices' time today, as last fetched, for `status`. */
    fun remoteUsage(day: String) = "$state/intentional/usage/$day.remote.json"

    fun usage(day: String) = "$usageDir/$day.json"

    /** The daemon's socket, where the extension's native host connects. */
    val socket get() = "${env("XDG_RUNTIME_DIR") ?: error("XDG_RUNTIME_DIR unset")}/intentional.sock"
}
