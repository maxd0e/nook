@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.maxdoe.nook.web

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLInputElement
import kotlin.js.JsModule
import kotlin.js.JsString
import kotlin.js.Promise

private val scope = MainScope()
private val controller = WebLoginController()

fun main() {
    applyTheme(WebUiBridge.loadTheme())
    render()
}

private fun render() {
    val root = requireNotNull(document.getElementById("nook-root"))
    val screen = controller.screen
    root.innerHTML =
        when (screen) {
            WebLoginScreen.Welcome -> welcomeMarkup()
            WebLoginScreen.PasteKey -> pasteKeyMarkup()
            is WebLoginScreen.BackupKey -> backupKeyMarkup(screen)
            WebLoginScreen.SignedIn -> signedInMarkup()
        }
    bindActions(screen)
}

private fun bindActions(screen: WebLoginScreen) {
    document.getElementById("theme-toggle")?.addEventListener("click", {
        applyTheme(if (document.documentElement?.classList?.contains("dark") == true) "light" else "dark")
    })
    when (screen) {
        WebLoginScreen.Welcome -> {
            document.getElementById("create-key")?.addEventListener("click", {
                controller.createKey()
                render()
            })
            document.getElementById("paste-key")?.addEventListener("click", {
                controller.showPasteKey()
                render()
            })
            document.getElementById("extension-login")?.addEventListener("click", {
                scope.launch {
                    controller.signInWithExtension()
                    render()
                }
            })
        }
        WebLoginScreen.PasteKey -> {
            document.getElementById("back")?.addEventListener("click", {
                controller.showWelcome()
                render()
            })
            document.getElementById("continue")?.addEventListener("click", {
                val value = (document.getElementById("nsec") as? HTMLInputElement)?.value.orEmpty()
                scope.launch {
                    controller.signInLocal(value)
                    render()
                }
            })
        }
        is WebLoginScreen.BackupKey -> {
            document.getElementById("back")?.addEventListener("click", {
                controller.showWelcome()
                render()
            })
            document.getElementById("saved-key")?.addEventListener("change", {
                controller.setBackupConfirmed((it.target as? HTMLInputElement)?.checked == true)
                render()
            })
            document.getElementById("copy-key")?.addEventListener("click", {
                scope.launch {
                    try {
                        WebUiBridge.copyText(screen.nsec).await<JsString>()
                        document.getElementById("copy-feedback")?.textContent = "Copied"
                    } catch (_: Throwable) {
                        document.getElementById("copy-feedback")?.textContent =
                            "Copy failed. Select and copy the key manually."
                    }
                }
            })
            document.getElementById("continue")?.addEventListener("click", {
                scope.launch {
                    controller.completeBackup()
                    render()
                }
            })
        }
        WebLoginScreen.SignedIn ->
            document.getElementById("logout")?.addEventListener("click", {
                scope.launch {
                    controller.logout()
                    render()
                }
            })
    }
}

private fun welcomeMarkup(): String =
    """
    <main class="welcome" aria-labelledby="nook-title">
        ${themeButton()}
        <div class="welcome-content">
            <div class="nook-mark" aria-hidden="true">⌂</div>
            <h1 id="nook-title">Nook</h1>
            <p class="promise">Your encrypted notes stay yours.</p>
            ${errorMarkup()}
            <div class="actions">
                <button id="create-key" type="button">Create a new key</button>
                <button id="paste-key" class="secondary" type="button">I have a Nostr key</button>
                <button id="extension-login" class="text-button" type="button">Sign in with a browser extension</button>
            </div>
        </div>
        <p class="privacy">Your private key never leaves this browser tab.</p>
    </main>
    """.trimIndent()

private fun pasteKeyMarkup(): String =
    """
    <main class="welcome key-flow" aria-labelledby="paste-title">
        ${themeButton()}
        <button id="back" class="back" type="button" aria-label="Back">←</button>
        <div class="welcome-content">
            <p class="eyebrow">Sign in</p>
            <h1 id="paste-title">Your Nostr key</h1>
            <p class="promise">Paste your private <code>nsec</code>. It remains in this tab only.</p>
            ${errorMarkup()}
            <label for="nsec">Private key</label>
            <input id="nsec" type="password" autocomplete="off" autocapitalize="off" spellcheck="false" />
            <button id="continue" type="button">Continue</button>
        </div>
    </main>
    """.trimIndent()

private fun backupKeyMarkup(screen: WebLoginScreen.BackupKey): String =
    """
    <main class="welcome key-flow" aria-labelledby="backup-title">
        ${themeButton()}
        <button id="back" class="back" type="button" aria-label="Back">←</button>
        <div class="welcome-content">
            <p class="eyebrow">Save your private key</p>
            <h1 id="backup-title">Keep this safe</h1>
            <p class="promise">This is the only time Nook can show this key. Lost keys cannot be recovered.</p>
            <code class="key-value">${screen.nsec}</code>
            <button id="copy-key" class="secondary" type="button">Copy private key</button>
            <p id="copy-feedback" class="copy-feedback" aria-live="polite"></p>
            <label class="confirmation"><input id="saved-key" type="checkbox" ${if (screen.savedConfirmed) "checked" else ""} /> I saved my private key</label>
            <button id="continue" type="button" ${if (screen.savedConfirmed) "" else "disabled"}>Continue</button>
        </div>
    </main>
    """.trimIndent()

private fun signedInMarkup(): String =
    """
    <main class="welcome" aria-labelledby="session-title">
        ${themeButton()}
        <div class="welcome-content">
            <div class="nook-mark" aria-hidden="true">⌂</div>
            <h1 id="session-title">Nook is ready</h1>
            <p class="promise">Your encrypted session is syncing in this browser tab.</p>
            <p class="eyebrow">Notes and checklists arrive in the next web workspace step.</p>
            <button id="logout" class="secondary" type="button">Log out</button>
        </div>
    </main>
    """.trimIndent()

private fun errorMarkup(): String =
    controller.error?.let { "<p class=\"error\" role=\"alert\">${it.htmlEscaped()}</p>" }.orEmpty()

private fun String.htmlEscaped(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private fun themeButton(): String =
    "<button id=\"theme-toggle\" class=\"theme-toggle\" type=\"button\" aria-label=\"Toggle theme\">◐</button>"

private fun applyTheme(theme: String) {
    document.documentElement?.classList?.toggle("dark", theme == "dark")
    WebUiBridge.saveTheme(theme)
}

@JsModule("./webUiBridge.mjs")
private external object WebUiBridge {
    fun loadTheme(): String

    fun saveTheme(theme: String)

    fun copyText(value: String): Promise<JsString>
}
