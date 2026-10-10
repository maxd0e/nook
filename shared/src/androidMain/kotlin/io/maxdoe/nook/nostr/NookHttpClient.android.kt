package io.maxdoe.nook.nostr

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.websocket.WebSockets

actual fun createNookHttpClient(): HttpClient = HttpClient(Android) { install(WebSockets) }
