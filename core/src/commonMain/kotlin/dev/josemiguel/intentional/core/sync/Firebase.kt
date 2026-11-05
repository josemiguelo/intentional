package dev.josemiguel.intentional.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * Firebase over its REST APIs, so every device (Android and the Kotlin/Native
 * daemon alike) runs this same code: Auth signs in with the account's email
 * and password and keeps the ID token fresh, and the Realtime Database is
 * read and written as JSON under /users/<uid>/, where the rules
 * (firebase/database.rules.json) let only that account in.
 */
class Firebase(private val config: SyncConfig, private val http: HttpClient) {

    private var uid: String? = null
    private var idToken: String? = null
    private var refreshToken: String? = null
    private var expiresAt = 0L

    /** GET a path under the user's tree; null when nothing is there. */
    suspend fun read(path: String, nowMs: Long): JsonElement? {
        val res = withToken(nowMs) { token -> http.get(url(path)) { parameter("auth", token) } }
        val body = res.bodyAsText()
        if (!res.status.isSuccess()) throw SyncException("read $path: ${res.status} $body")
        return Json.parseToJsonElement(body).takeUnless { it == JsonNull }
    }

    /** PUT a value; false when the rules refuse it (a locked limit). */
    suspend fun write(path: String, value: JsonElement, nowMs: Long): Boolean {
        val res = withToken(nowMs) { token ->
            http.put(url(path)) {
                parameter("auth", token)
                contentType(ContentType.Application.Json)
                setBody(value.toString())
            }
        }
        if (res.status.value == 401) return false
        if (!res.status.isSuccess()) throw SyncException("write $path: ${res.status} ${res.bodyAsText()}")
        return true
    }

    private fun url(path: String): String {
        val base = config.databaseUrl.trimEnd('/')
        val user = uid ?: error("not signed in")
        return "$base/users/$user/$path.json"
    }

    /**
     * Signs in, or refreshes the token a minute before it expires. Done ahead
     * of time, so a 401 from the database means the rules said no.
     */
    private suspend fun withToken(nowMs: Long, request: suspend (String) -> HttpResponse): HttpResponse {
        if (idToken == null || nowMs > expiresAt - 60_000) {
            if (refreshToken == null) signIn(nowMs) else refresh(nowMs)
        }
        return request(idToken!!)
    }

    private suspend fun signIn(nowMs: Long) {
        val res = http.post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword") {
            parameter("key", config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("email", config.email)
                put("password", config.password)
                put("returnSecureToken", true)
            }.toString())
        }
        val o = parsedOrThrow(res, "sign in")
        uid = o["localId"]!!.jsonPrimitive.content
        idToken = o["idToken"]!!.jsonPrimitive.content
        refreshToken = o["refreshToken"]!!.jsonPrimitive.content
        expiresAt = nowMs + o["expiresIn"]!!.jsonPrimitive.content.toLong() * 1000
    }

    private suspend fun refresh(nowMs: Long) {
        val res = http.post("https://securetoken.googleapis.com/v1/token") {
            parameter("key", config.apiKey)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("grant_type=refresh_token&refresh_token=$refreshToken")
        }
        if (!res.status.isSuccess()) {
            // A revoked or stale refresh token: start over with the password.
            refreshToken = null
            return signIn(nowMs)
        }
        val o = Json.parseToJsonElement(res.bodyAsText()).jsonObject
        uid = o["user_id"]!!.jsonPrimitive.content
        idToken = o["id_token"]!!.jsonPrimitive.content
        refreshToken = o["refresh_token"]!!.jsonPrimitive.content
        expiresAt = nowMs + o["expires_in"]!!.jsonPrimitive.long * 1000
    }

    private suspend fun parsedOrThrow(res: HttpResponse, what: String) =
        if (res.status.isSuccess()) Json.parseToJsonElement(res.bodyAsText()).jsonObject
        else throw SyncException("$what: ${res.status} ${res.bodyAsText()}")
}

class SyncException(message: String) : Exception(message)
