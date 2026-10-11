package io.maxdoe.nook.nostr

import kotlin.test.Test
import kotlin.test.assertTrue

class NookHttpClientTest {
    @Test
    fun androidClientUsesWebSocketCapableOkHttpEngine() {
        val client = createNookHttpClient()

        try {
            assertTrue(client.engine::class.qualifiedName.orEmpty().contains("OkHttp"))
        } finally {
            client.close()
        }
    }
}
