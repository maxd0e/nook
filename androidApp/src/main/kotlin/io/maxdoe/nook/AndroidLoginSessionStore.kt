package io.maxdoe.nook

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class AndroidLoginSessionStore(
    context: Context,
) : LoginSessionStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun hasValidKey(): Boolean = nsecOrNull() != null

    override fun nsecOrNull(): String? = encryptedNsec()?.let(NostrNsecKeyCodec::normalizeOrNull)

    override fun credentialsOrNull(): LoginCredentials? =
        nsecOrNull()?.let(LoginCredentials::LocalKey)
            ?: preferences
                .getString(SIGNER_PUBLIC_KEY, null)
                ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                ?.let { publicKey ->
                    preferences
                        .getString(SIGNER_PACKAGE, null)
                        ?.takeIf(String::isNotBlank)
                        ?.let { LoginCredentials.ExternalSigner(publicKey, it) }
                }

    override fun save(nsec: String) {
        preferences.edit().putString(ENCRYPTED_NSEC_KEY, encrypt(nsec)).commit()
    }

    override fun saveSigner(
        publicKey: String,
        packageName: String,
    ) {
        preferences
            .edit()
            .remove(ENCRYPTED_NSEC_KEY)
            .putString(SIGNER_PUBLIC_KEY, publicKey)
            .putString(SIGNER_PACKAGE, packageName)
            .commit()
    }

    override fun clear() {
        preferences.edit().clear().commit()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        appContext.deleteDatabase(DATABASE_NAME)
    }

    private fun encryptedNsec(): String? =
        preferences.getString(ENCRYPTED_NSEC_KEY, null)?.let { encoded ->
            runCatching {
                val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
                val nonceSize = encrypted.first().toInt() and 0xFF
                require(nonceSize in 12..16 && encrypted.size > nonceSize + 1)
                val nonce = encrypted.copyOfRange(1, nonceSize + 1)
                val ciphertext = encrypted.copyOfRange(nonceSize + 1, encrypted.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keyStore().getKey(KEY_ALIAS, null), GCMParameterSpec(128, nonce))
                cipher.doFinal(ciphertext).decodeToString()
            }.getOrNull()
        }

    private fun encrypt(nsec: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encoded = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(nsec.encodeToByteArray())
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    private fun encryptionKey() =
        keyStore().getKey(KEY_ALIAS, null)
            ?: KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply {
                    init(
                        KeyGenParameterSpec
                            .Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build(),
                    )
                }.generateKey()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DATABASE_NAME = "nook.db"
        const val ENCRYPTED_NSEC_KEY = "encrypted_nsec"
        const val SIGNER_PACKAGE = "signer_package"
        const val SIGNER_PUBLIC_KEY = "signer_public_key"
        const val KEY_ALIAS = "io.maxdoe.nook.login_key"
        const val PREFERENCES_NAME = "nook_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
