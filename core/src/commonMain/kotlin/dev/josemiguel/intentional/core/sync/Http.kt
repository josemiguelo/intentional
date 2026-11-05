package dev.josemiguel.intentional.core.sync

import io.ktor.client.HttpClient

/** OkHttp on the JVM (Android), libcurl on Linux. */
expect fun defaultHttpClient(): HttpClient
