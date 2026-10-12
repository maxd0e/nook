package io.maxdoe.nook.web

import kotlinx.browser.document

fun main() {
    document.getElementById("nook-root")?.innerHTML =
        """
        <main class="welcome" aria-labelledby="nook-title">
            <p class="eyebrow">Private notes on Nostr</p>
            <h1 id="nook-title">Nook</h1>
            <p>Your encrypted notes stay yours.</p>
            <label for="nsec">Nostr private key</label>
            <input id="nsec" type="password" autocomplete="off" spellcheck="false" />
            <button type="button">Continue</button>
        </main>
        """.trimIndent()
}
