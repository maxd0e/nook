package io.maxdoe.nook.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.maxdoe.nook.data.db.NookDatabase
import io.maxdoe.nook.nostr.SignedNostrEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlDelightEncryptedEventStoreTest {
    @Test
    fun storesOnlyEncryptedNostrEventFieldsAndDirtyState() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            NookDatabase.Schema.create(driver)
            val store = SqlDelightEncryptedEventStore(driver)
            val event =
                SignedNostrEvent(
                    id = "event-id",
                    publicKey = "pubkey",
                    createdAt = 10,
                    kind = 30_078,
                    tags = listOf(listOf("d", "nook:note-id")),
                    content = "encrypted-payload",
                    signature = "signature",
                )

            store.insert(StoredNostrEvent(event, "nook:note-id", dirty = true))

            val stored = store.all().single()
            assertEquals("encrypted-payload", stored.event.content)
            assertTrue(stored.dirty)
            store.markClean(event.id)
            assertFalse(store.all().single().dirty)
        }
}
