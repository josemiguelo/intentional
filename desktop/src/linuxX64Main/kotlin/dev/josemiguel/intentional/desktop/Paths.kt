package dev.josemiguel.intentional.desktop

/** Where the desktop guard keeps things, by the XDG base directories. */
object Paths {
    private val config get() = env("XDG_CONFIG_HOME") ?: "$home/.config"
    private val state get() = env("XDG_STATE_HOME") ?: "$home/.local/state"

    /** The groups, as the phone stores them; on a desktop "packages" are Hyprland window classes. */
    val groups get() = "$config/intentional/groups.json"

    val usageDir get() = "$state/intentional/usage"

    fun usage(day: String) = "$usageDir/$day.json"

    /** The daemon's socket, where the extension's native host connects. */
    val socket get() = "${env("XDG_RUNTIME_DIR") ?: error("XDG_RUNTIME_DIR unset")}/intentional.sock"
}
