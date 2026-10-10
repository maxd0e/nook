package io.maxdoe.nook.notes

import kotlin.test.Test
import kotlin.test.assertEquals

class NookSharedTest {
    @Test
    fun sharedModule_isUsableFromCommonCode() {
        assertEquals("Nook", NookShared.NAME)
    }
}
