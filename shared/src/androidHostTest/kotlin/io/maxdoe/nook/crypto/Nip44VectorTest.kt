package io.maxdoe.nook.crypto

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import kotlin.test.Test
import kotlin.test.assertEquals

/** Quartz parity checks retained on the Android host target; browser coverage exercises the same common cipher API. */
class Nip44VectorTest {
    @Test
    fun officialConversationKeyVector_matches() {
        val privateKey = "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268".hexToByteArray()
        val publicKey = "c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133".hexToByteArray()

        assertEquals(
            "3dfef0ce2a4d80a25e7a328accf73448ef67096f65f79588e358d9a0eb9013f1",
            Nip44v2().getConversationKey(privateKey, publicKey).toHexKey(),
        )
    }

    @Test
    fun officialEncryptionVector_matches() {
        val payload =
            Nip44v2()
                .encryptWithNonce(
                    "a",
                    "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d".hexToByteArray(),
                    "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray(),
                ).encodePayload()

        assertEquals(
            "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtD" +
                "KfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb",
            payload,
        )
    }
}
