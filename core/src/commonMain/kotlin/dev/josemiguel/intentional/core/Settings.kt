package dev.josemiguel.intentional.core

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The settings every device shares (sync): the wind-down window and the
 * pause lengths behind the friction ladder. Times are minutes past midnight.
 * The defaults are the phone's.
 */
@Serializable
data class Settings(
    val windDown: Boolean = true,
    val windDownStart: Int = 22 * 60,
    val windDownEnd: Int = 6 * 60,
    val pauseSeconds: Int = 8,
    val longPauseSeconds: Int = 25,
) {
    val windDownStartTime: LocalTime get() = LocalTime(windDownStart / 60, windDownStart % 60)
    val windDownEndTime: LocalTime get() = LocalTime(windDownEnd / 60, windDownEnd % 60)

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(raw: String): Settings = json.decodeFromString(serializer(), raw)
    }
}
