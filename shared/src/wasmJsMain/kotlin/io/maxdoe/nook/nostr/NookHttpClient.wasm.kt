package io.maxdoe.nook.nostr

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets

actual fun createNookHttpClient(): HttpClient = HttpClient(Js) { install(WebSockets) }
