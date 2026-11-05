package dev.josemiguel.intentional.core.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl

actual fun defaultHttpClient(): HttpClient = HttpClient(Curl) { expectSuccess = false }
