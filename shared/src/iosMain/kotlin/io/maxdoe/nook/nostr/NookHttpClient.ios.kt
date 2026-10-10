package io.maxdoe.nook.nostr

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

actual fun createNookHttpClient(): HttpClient = HttpClient(Darwin) { install(WebSockets) }
