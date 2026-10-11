@file:Suppress("LongMethod", "MaxLineLength")

package io.maxdoe.nook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

const val HOME_ROOT_TEST_TAG = "nook_home"
private const val LOGIN_ROOT_TEST_TAG = "login_root"

@Composable
fun NookHome(sessionStore: LoginSessionStore) {
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
            noteSession = sessionStore.nsecOrNull()?.let { NookNoteSession(it, context) }
        }
        updateScreen()
    }

    DisposableEffect(noteSession) {
        onDispose { coroutineScope.launch { noteSession?.close() } }
    }

    Surface(Modifier.fillMaxSize().testTag(HOME_ROOT_TEST_TAG), color = MaterialTheme.colorScheme.background) {
        when (val current = screen) {
            LoginScreen.Welcome ->
                WelcomeScreen({
                    controller.createKey()
                    updateScreen()
                }, {
                    controller.showPasteKey()
                    updateScreen()
                })
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
                            noteSession = sessionStore.nsecOrNull()?.let { NookNoteSession(it, context) }
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
                        noteSession = sessionStore.nsecOrNull()?.let { NookNoteSession(it, context) }
                    }
                    updateScreen()
                })
            LoginScreen.SignedIn ->
                noteSession?.let { session ->
                    NookNotes(session.repository) {
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
) {
    Column(screenModifier(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Spacer(Modifier.height(118.dp))
            NookLogo()
            Spacer(Modifier.height(42.dp))
            Text("Nook", style = MaterialTheme.typography.displayLarge, fontSize = 68.sp)
            Spacer(Modifier.height(44.dp))
            Text(
                "Private notes and checklists. Synced through Nostr relays, readable only with your key.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 25.sp,
                lineHeight = 35.sp,
            )
        }
        Column {
            PrimaryButton("Create a new key", onCreateKey)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = onPasteKey,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(25.dp),
            ) {
                Text("I have a Nostr key", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(50.dp))
            Text(
                "Notes are encrypted on your device before they leave it.",
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

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
            Modifier.fillMaxWidth().height(72.dp).then(
                if (testTag ==
                    null
                ) {
                    Modifier
                } else {
                    Modifier.testTag(testTag)
                },
            ),
        enabled = enabled,
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(),
    ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun screenModifier(): Modifier =
    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 32.dp)

@Composable
private fun NookLogo() {
    Canvas(Modifier.size(128.dp)) {
        val px = this.density

        fun dp(value: Float) = value * px
        drawRoundRect(
            Color(0xFF1E4D4A),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(dp(28f)),
        )
        val paper = Color(0xFFF6F1E7)
        drawRect(
            paper,
            androidx.compose.ui.geometry
                .Offset(dp(34f), dp(29f)),
            androidx.compose.ui.geometry
                .Size(dp(60f), dp(75f)),
        )
        val fold =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(dp(76f), dp(29f))
                lineTo(dp(94f), dp(47f))
                lineTo(dp(76f), dp(47f))
                close()
            }
        drawPath(fold, Color(0xFFCFC5AE), style = Fill)
        drawCircle(
            Color(0xFF1E4D4A),
            dp(8f),
            androidx.compose.ui.geometry
                .Offset(dp(64f), dp(68f)),
        )
        val keyhole =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(dp(59f), dp(73f))
                lineTo(dp(69f), dp(73f))
                lineTo(dp(72f), dp(92f))
                lineTo(dp(56f), dp(92f))
                close()
            }
        drawPath(keyhole, Color(0xFF1E4D4A), style = Fill)
    }
}
