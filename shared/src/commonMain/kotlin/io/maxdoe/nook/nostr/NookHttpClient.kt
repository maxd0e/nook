package io.maxdoe.nook.nostr

import io.ktor.client.HttpClient

expect fun createNookHttpClient(): HttpClient
