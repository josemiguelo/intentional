package dev.josemiguel.intentional.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a device needs to sync: the Firebase project (its Realtime Database
 * URL and web API key), the one account every device signs in as, the key
 * that encrypts what leaves the device, and this device's own id. The same
 * file on every device except [device]; it holds secrets, so it lives with
 * the app's private data, never in a repo.
 */
@Serializable
data class SyncConfig(
    val databaseUrl: String,
    val apiKey: String,
    val email: String,
    val password: String,
    /** 32 random bytes, base64: AES-256-GCM for usage and groups, and the group ids' HMAC. */
    val key: String,
    /** This device in the usage tree, e.g. "phone", "tv", "t2-macbook". */
    val device: String,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        fun parse(raw: String): SyncConfig = json.decodeFromString(serializer(), raw)
    }
}
