package io.maxdoe.nook

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var pendingSignerResult: ((Nip55Result) -> Unit)? = null
    private val signerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val response =
                when {
                    result.resultCode != Activity.RESULT_OK ->
                        Nip55Result.Failed(
                            "Signer did not complete the request.",
                        )
                    result.data?.getBooleanExtra("rejected", false) == true -> Nip55Result.Rejected
                    else ->
                        result.data?.getStringExtra("result")?.let {
                            Nip55Result.Success(
                                it,
                                result.data?.getStringExtra("event"),
                                result.data?.getStringExtra("package"),
                            )
                        } ?: Nip55Result.Failed("Signer returned no result.")
                }
            pendingSignerResult?.invoke(response)
            pendingSignerResult = null
        }

    private val signerRequester =
        object : Nip55ForegroundRequester {
            override suspend fun request(request: Nip55Request): Nip55Result =
                suspendCancellableCoroutine { continuation ->
                    if (pendingSignerResult != null) {
                        continuation.resume(Nip55Result.Failed("Another signer request is already active."))
                        return@suspendCancellableCoroutine
                    }
                    pendingSignerResult = { result -> if (continuation.isActive) continuation.resume(result) }
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:${request.payload}")).apply {
                            request.packageName?.let { `package` = it }
                            putExtra("type", request.type)
                            request.currentUser?.let { putExtra("current_user", it) }
                            request.peerPublicKey?.let { putExtra("pubkey", it) }
                            request.permissions?.let { putExtra("permissions", it) }
                        }
                    continuation.invokeOnCancellation { pendingSignerResult = null }
                    try {
                        signerLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        pendingSignerResult = null
                        continuation.resume(Nip55Result.Unavailable)
                    }
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var darkTheme by rememberSaveable { mutableStateOf(systemDarkTheme) }
            NookTheme(darkTheme = darkTheme) {
                NookHome(
                    sessionStore = AndroidLoginSessionStore(applicationContext),
                    signerRequester = signerRequester,
                    isDarkTheme = darkTheme,
                    onThemeToggle = { darkTheme = !darkTheme },
                )
            }
        }
    }
}
