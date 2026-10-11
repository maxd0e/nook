package io.maxdoe.nook.nostr

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import java.util.concurrent.TimeUnit

actual fun createNookHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                pingInterval(20, TimeUnit.SECONDS)
            }
        }
        install(WebSockets)
    }
