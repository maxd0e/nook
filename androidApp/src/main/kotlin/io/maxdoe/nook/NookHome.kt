@file:Suppress("LongMethod", "MaxLineLength")

package io.maxdoe.nook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

const val HOME_ROOT_TEST_TAG = "nook_home"
private const val LOGIN_ROOT_TEST_TAG = "login_root"

@Composable
fun NookHome(
    sessionStore: LoginSessionStore,
    signerRequester: Nip55ForegroundRequester,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onThemeToggle: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(sessionStore) { LoginSessionController(sessionStore) }
    var screen by remember { mutableStateOf(controller.screen) }
    var noteSession by remember { mutableStateOf<NookNoteSession?>(null) }
    var pastedKey by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf<String?>(null) }

    fun updateScreen() {
        screen = controller.screen
        pasteError = controller.pasteError
    }

    LaunchedEffect(controller) {
        controller.restore()
        if (controller.screen == LoginScreen.SignedIn) {
            noteSession = sessionStore.credentialsOrNull()?.let { NookNoteSession(it, context, signerRequester) }
        }
        updateScreen()
    }

    DisposableEffect(noteSession) {
        val sessionToClose = noteSession
        onDispose { coroutineScope.launch { sessionToClose?.close() } }
    }

    Surface(Modifier.fillMaxSize().testTag(HOME_ROOT_TEST_TAG), color = MaterialTheme.colorScheme.background) {
        when (val current = screen) {
            LoginScreen.Welcome ->
                WelcomeScreen(
                    onCreateKey = {
                        controller.createKey()
                        updateScreen()
                    },
                    onPasteKey = {
                        controller.showPasteKey()
                        updateScreen()
                    },
                    onSigner = {
                        coroutineScope.launch {
                            val result =
                                signerRequester.request(
                                    Nip55Request(type = "get_public_key", permissions = NIP55_PERMISSIONS),
                                )
                            when (result) {
                                is Nip55Result.Success -> {
                                    controller.completeSignerPairing(
                                        result.result.lowercase(),
                                        result.packageName.orEmpty(),
                                    )
                                    if (controller.screen == LoginScreen.SignedIn) {
                                        noteSession =
                                            sessionStore.credentialsOrNull()?.let {
                                                NookNoteSession(it, context, signerRequester)
                                            }
                                    }
                                }
                                Nip55Result.Rejected -> controller.signerPairingFailed("Signer pairing was cancelled.")
                                Nip55Result.Unavailable ->
                                    controller.signerPairingFailed(
                                        "No signer app is installed. Install one and try again.",
                                    )
                                is Nip55Result.Failed ->
                                    controller.signerPairingFailed(
                                        "Signer pairing failed. Try again.",
                                    )
                            }
                            updateScreen()
                        }
                    },
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle,
                )
            LoginScreen.PasteKey ->
                PasteKeyScreen(
                    value = pastedKey,
                    error = pasteError,
                    onValueChange = { pastedKey = it },
                    onBack = {
                        pastedKey = ""
                        controller.restore()
                        updateScreen()
                    },
                    onContinue = {
                        controller.signIn(pastedKey)
                        if (controller.screen == LoginScreen.SignedIn) {
                            pastedKey = ""
                            noteSession =
                                sessionStore.credentialsOrNull()?.let { NookNoteSession(it, context, signerRequester) }
                        }
                        updateScreen()
                    },
                )
            is LoginScreen.BackupKey ->
                BackupKeyScreen(current.nsec, current.savedConfirmed, {
                    controller.setBackupConfirmed(it)
                    updateScreen()
                }, {
                    controller.completeBackup()
                    if (controller.screen ==
                        LoginScreen.SignedIn
                    ) {
                        noteSession =
                            sessionStore.credentialsOrNull()?.let { NookNoteSession(it, context, signerRequester) }
                    }
                    updateScreen()
                })
            LoginScreen.SignedIn ->
                noteSession?.let { session ->
                    NookNotes(session.repository, session.syncStatus, session.syncHealth) {
                        coroutineScope.launch {
                            session.close()
                            noteSession = null
                            pastedKey = ""
                            controller.logout()
                            updateScreen()
                        }
                    }
                } ?: SignedInScreen {
                    pastedKey = ""
                    controller.logout()
                    updateScreen()
                }
        }
    }
}

@Composable
private fun WelcomeScreen(
    onCreateKey: () -> Unit,
    onPasteKey: () -> Unit,
    onSigner: () -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
) {
    Box(screenModifier()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Spacer(Modifier.height(124.dp))
                NookLogo()
                Spacer(Modifier.height(25.dp))
                Text("Nook", style = MaterialTheme.typography.displayLarge, fontSize = 52.sp)
                Spacer(Modifier.height(26.dp))
                Text(
                    "Private notes and checklists. Synced through Nostr relays, readable only with your key.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    lineHeight = 27.sp,
                )
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                PrimaryButton("Create a new key", onCreateKey)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPasteKey,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("I have a Nostr key", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Sign in with a signer app",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSigner)
                            .padding(vertical = 22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
        IconButton(
            onClick = onThemeToggle,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp).testTag("theme_toggle"),
        ) {
            Icon(
                painter = painterResource(if (isDarkTheme) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon),
                contentDescription = if (isDarkTheme) "Switch to light theme" else "Switch to dark theme",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val NIP55_PERMISSIONS =
    "[{\"type\":\"get_public_key\"},{\"type\":\"sign_event\",\"kind\":30078},{\"type\":\"nip44_encrypt\"},{\"type\":\"nip44_decrypt\"}]"

@Composable
private fun PasteKeyScreen(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(screenModifier().testTag(LOGIN_ROOT_TEST_TAG)) {
        Text(
            "‹ Back",
            modifier = Modifier.padding(top = 24.dp).clickable(onClick = onBack),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(68.dp))
        Text("Use your Nostr key", style = MaterialTheme.typography.displayLarge, fontSize = 38.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Paste your nsec private key. It stays encrypted on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            lineHeight = 27.sp,
        )
        Spacer(Modifier.height(34.dp))
        OutlinedTextField(
            value,
            onValueChange,
            label = {
                Text("nsec private key")
            },
            isError = error != null,
            supportingText =
                error?.let {
                    {
                        Text(it)
                    }
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                ),
            modifier = Modifier.fillMaxWidth().testTag("nsec_input"),
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", onContinue, "nsec_continue")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BackupKeyScreen(
    nsec: String,
    confirmed: Boolean,
    onConfirmChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(screenModifier().testTag(LOGIN_ROOT_TEST_TAG)) {
        Spacer(Modifier.height(68.dp))
        Text("Save your private key", style = MaterialTheme.typography.displayLarge, fontSize = 38.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            "This is the only time Nook will show this key. If you lose it, your notes cannot be recovered.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp,
            lineHeight = 27.sp,
        )
        Spacer(Modifier.height(30.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                ).padding(18.dp),
        ) {
            Text(nsec, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(confirmed, onConfirmChange, Modifier.testTag("backup_confirmed"))
            Spacer(Modifier.width(8.dp))
            Text("I saved my private key", fontSize = 17.sp)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Continue", onContinue, "backup_continue", confirmed)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SignedInScreen(onLogout: () -> Unit) {
    Column(
        screenModifier().testTag("signed_in_placeholder"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NookLogo()
        Spacer(Modifier.height(28.dp))
        Text("You’re signed in", style = MaterialTheme.typography.displayLarge, fontSize = 36.sp)
        Spacer(Modifier.height(12.dp))
        Text("Your notes will appear here in the next step.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        OutlinedButton(onLogout, modifier = Modifier.testTag("logout")) { Text("Log out") }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    testTag: String? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth().height(52.dp).then(
                if (testTag ==
                    null
                ) {
                    Modifier
                } else {
                    Modifier.testTag(testTag)
                },
            ),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(),
    ) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun screenModifier(): Modifier =
    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 28.dp)

@Composable
private fun NookLogo() {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.nook_logo),
        contentDescription = "Nook logo",
        modifier = Modifier.size(64.dp),
    )
}
