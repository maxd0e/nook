package io.maxdoe.nook

import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair

sealed interface LoginScreen {
    data object Welcome : LoginScreen

    data object PasteKey : LoginScreen

    data class BackupKey(
        val nsec: String,
        val savedConfirmed: Boolean = false,
    ) : LoginScreen

    data object SignedIn : LoginScreen
}

interface NsecKeyCodec {
    fun create(): String

    fun normalizeOrNull(value: String): String?
}

object NostrNsecKeyCodec : NsecKeyCodec {
    override fun create(): String = checkNotNull(KeyPair().privKey).toNsec()

    override fun normalizeOrNull(value: String): String? =
        runCatching {
            require(!value.any(Char::isLowerCase) || !value.any(Char::isUpperCase))
            val normalized = value.lowercase()
            require(normalized.startsWith("nsec1"))
            val privateKey = normalized.bechToBytes()
            require(privateKey.size == 32)
            KeyPair(privKey = privateKey)
            normalized
        }.getOrNull()
}

interface LoginSessionStore {
    fun hasValidKey(): Boolean

    fun save(nsec: String)

    fun clear()
}

class LoginSessionController(
    private val store: LoginSessionStore,
    private val keyCodec: NsecKeyCodec = NostrNsecKeyCodec,
) {
    var screen: LoginScreen = LoginScreen.Welcome
        private set

    var pasteError: String? = null
        private set

    fun restore() {
        screen = if (store.hasValidKey()) LoginScreen.SignedIn else LoginScreen.Welcome
    }

    fun showPasteKey() {
        pasteError = null
        screen = LoginScreen.PasteKey
    }

    fun createKey() {
        screen = LoginScreen.BackupKey(keyCodec.create())
    }

    fun setBackupConfirmed(confirmed: Boolean) {
        val backup = screen as? LoginScreen.BackupKey ?: return
        screen = backup.copy(savedConfirmed = confirmed)
    }

    fun completeBackup() {
        val backup = screen as? LoginScreen.BackupKey ?: return
        if (!backup.savedConfirmed) return
        store.save(backup.nsec)
        screen = LoginScreen.SignedIn
    }

    fun signIn(nsec: String) {
        val normalizedNsec = keyCodec.normalizeOrNull(nsec.trim())
        if (normalizedNsec == null) {
            pasteError = "Enter a valid nsec private key."
            return
        }
        store.save(normalizedNsec)
        pasteError = null
        screen = LoginScreen.SignedIn
    }

    fun logout() {
        store.clear()
        pasteError = null
        screen = LoginScreen.Welcome
    }
}
