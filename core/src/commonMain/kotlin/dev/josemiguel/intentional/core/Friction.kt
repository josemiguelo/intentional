package dev.josemiguel.intentional.core

/**
 * The friction ladder for considered apps, chosen per app while motivated.
 * Deterrence is dose-responsive (a pause deters ~13% of launches, typing
 * ~27-48%), so heavier rungs exist — but every rung stays escapable except
 * NIGHT_LOCK during wind-down. Durations are per-platform settings (the
 * Android app's `Prefs.pauseSeconds` / `Prefs.longPauseSeconds`).
 */
enum class Friction(val label: String) {
    PAUSE("pause"),
    LONG_PAUSE("long pause"),
    TYPE_NAME("type its name"),
    NIGHT_LOCK("locked at night");

    fun next(): Friction = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(ordinal: Int) = entries[ordinal.coerceIn(0, entries.size - 1)]
    }
}
