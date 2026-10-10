package io.maxdoe.nook

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun applicationId_isNookPackage() {
        assertEquals("io.maxdoe.nook", BuildConfig.APPLICATION_ID)
    }
}
