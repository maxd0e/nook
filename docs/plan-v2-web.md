# Nook v2 — Web Plan (for an AI)

Build the Nook web app after the Android client. It is the same private notes
product: Android and web use the same encrypted Nostr events, relay rules, and
Nostr identity, so changes appear on every client using that key.

Read `status.md`, `spec.md`, and this plan before working. Use
`docs/design.md` and the current Android screens in `docs/mock/` as the visual
baseline. The rules from `plan.md` apply here too: work one phase at a time,
test first, keep the implementation small, and never log keys or note content.

## Web rules

1. **Kotlin/Wasm UI.** Add a `wasmJs` target to `:shared` and build a native
   HTML web UI in `:webApp`. Do not use canvas-rendered UI or Compose
   Multiplatform.
2. **Private keys are session-only.** A pasted or newly generated `nsec` may
   exist only in active tab memory. Never write it to IndexedDB, localStorage,
   sessionStorage, cookies, logs, or error reporting. Reloading requires login
   again.
3. **Browser-extension login.** NIP-07 is supported only when `window.nostr`
   provides `getPublicKey`, `signEvent`, `nip44.encrypt`, and `nip44.decrypt`.
   Explain clearly when an extension is missing or lacks a required method.
4. **Reuse shared behavior.** Payloads, event/version rules, conflict handling,
   repository behavior, and relay sync remain shared. Browser-specific UI,
   browser APIs, and browser storage live outside common logic.
5. **No third-party runtime scripts or analytics.** The deployed app is a
   static bundle with a strict Content-Security-Policy. Any npm crypto package
   is bundled at build time and must be specifically approved before adding it.

## Product and design parity

The web client has feature parity with the current Android client unless this
plan explicitly says otherwise.

- Notes and checklists are encrypted kind-`30078` Nostr events; the browser
  database stores ciphertext only. Local mutations save first, are marked
  dirty, and relay sync retries them when a connection returns.
- The list has live local search, pinned items first, then newest items;
  checklist cards show completion progress and cards show relative dates.
- Existing notes and checklists open in read mode. **Edit** enters edit mode;
  **Save** and Back from edit persist pending changes; Back from an untouched
  editor returns to the list. Newly created items start in edit mode.
- Checklist item completion is an intentional immediate-save exception. Active
  items and completed items are separate; clearing completed items, deleting
  an item, and deleting a note or checklist require Cancel/confirm dialogs.
- Markdown remains deliberately small: headings, bold, italic, and bullet
  lists. It is edited as Markdown and rendered in read mode.
- Show `Synced`, `Syncing`, or `Offline` state and safe per-relay health where
  Android does. Keep first-use empty, no-search-results, and offline states.
- Persist the non-sensitive light/dark theme preference across reloads. Logout
  clears notes and credentials but does not reset the theme.

Use the paper/teal colors, serif title treatment, spacing, card shapes, and
compact uppercase section labels from `docs/design.md`. Keep the current
Android visual refinements: icon-only theme and logout controls, a floating
speed dial for Note and Checklist creation, compact editor actions, a slim
formatting toolbar, and paper-like read views.

---

## Step W0 — Decide and mock

**Spike (keep it to a few days):** add the `wasmJs` target and an empty
`:webApp` page. Reuse the current common relay protocol code with Ktor 3.3.3
and a browser `HttpClient` actual backed by `ktor-client-js-wasm-js`.

Verify that:

- shared payload, event, conflict, and fake-relay tests compile and run on the
  web target;
- Ktor WebSockets connect from a browser to each configured `wss://` relay and
  can subscribe, receive events, publish, and reconnect;
- the current shared `KtorRelayTransport` works without transport forks;
- native HTML inputs, selection, IME, keyboard navigation, and screen-reader
  use work in the basic notes workspace;
- the static host can use a CSP whose `connect-src` allows the fixed relays.

Browser WebSockets control low-level ping behavior, so rely on the existing
relay reconnect/status logic rather than Android's OkHttp ping configuration.
If Ktor cannot connect reliably in the supported browsers, document the exact
failure and pause before selecting another transport.

**Mock the web screens** into `docs/mock/web/`: Welcome, pasted-key login,
key backup, unsupported-extension state, a desktop persistent-list/detail
workspace, and the Android-like single-column narrow layout. Include note and
checklist read/edit, empty/no-results/offline, and light/dark states.

**Done when:** Ktor/Wasm relay compatibility and the responsive visual baseline
are verified, and any browser limitation is recorded.

## Step W1 — Shared logic on the web

**Tests first:**
- the existing `commonTest` suite (payload, event build/parse, conflict rule, sync with the fake relay) passes on the `wasmJs` target
- NIP-44 v2 vectors, self-encryption round trips, Bech32, and event signing pass with the browser crypto implementation
- raw IndexedDB contains encrypted events rather than note or checklist plaintext; events survive reload and logout removes them
- browser relay sync reconnects and exposes the correct Offline → Syncing → Synced status

**Build:**
- move any Android/Quartz-only crypto dependency behind the existing portable signer/crypto boundary; use an audited, build-bundled browser-compatible implementation only after explicit approval
- an IndexedDB encrypted-event store that implements the shared storage contract; decrypted projections and search remain in memory
- a `wasmJs` Ktor client actual; keep `KtorRelayTransport` and the relay protocol shared

**Done when:** all tests pass on `wasmJs`, and one manual check against a real relay works.

**Approved dependency (2026-10-13):** use the npm `nostr-tools` package as a
build-bundled Wasm browser-crypto bridge. It may provide local-key generation,
NIP-19 conversion, NIP-44 v2 self-encryption/decryption, and Nostr event
signing/verification. Do not load it from a CDN or add a third-party runtime
script. NIP-07 sessions still delegate those operations to `window.nostr`.

## Step W2 — Web login (follow the Welcome mock)

**Tests first:**
- pasting a valid nsec logs in and keeps the key in memory only; the test inspects localStorage, sessionStorage, IndexedDB, and cookies and finds no key
- reloading the page asks for the key again
- "Create a new key" cannot continue until "I saved it" is confirmed; Copy writes the displayed key to the clipboard and Back discards the unsaved key
- extension login uses a fake `window.nostr`: it works only when `getPublicKey`, `signEvent`, `nip44.encrypt`, and `nip44.decrypt` are available, and clearly explains missing extension or NIP-44 support
- logout wipes local encrypted data and credentials but preserves the chosen theme

**Build:** Web Welcome, pasted-key entry, key backup, session handling, logout,
and a NIP-07 signer adapter. Offer Create a new key, I have a Nostr key, and
browser-extension sign-in. Local-key sessions perform encryption and signing
only in memory; extension sessions delegate every operation to the extension.
**Done when:** tests pass and the page matches the mock.

## Step W3 — Notes and checklists (follow the web mocks)

**Tests first** (Playwright, with a fake relay):
- list shows pinned first, then newest first; search filters live
- list shows relative dates plus first-use empty, no-search-results, offline, and relay-status states
- create, read, edit, save, pin and delete a note; create and use a checklist (read, edit, check, add, delete, completed section, clear completed)
- existing items are read-only until Edit; Save and Back persist pending changes; untouched Back exits; checklist completion remains an immediate-save exception
- cancel and confirm paths work for note, checklist, checklist-item, and clear-completed deletion
- the editor toolbar (bold, italic, heading, bullet list) produces the expected markdown and reads it back
- layout: persistent list/detail panes on desktop width, single column route flow on phone width

**Build:** list, search, speed dial, paper-like note and checklist read/edit
views, markdown toolbar and renderer, confirmation dialogs, sync status, and
responsive navigation. Use semantic HTML controls, visible focus behavior, and
accessible names for icon-only actions.
**Done when:** tests pass and the screens match the mocks.

## Step W4 — Android and web together

**Check by hand with a real relay:**
- create a note and a checklist on Android, log in on the web with the same key: both appear
- edit, pin, complete checklist items, and delete on either client; verify every change arrives on the other
- repeat with a session-key login and a NIP-07/NIP-44 extension login
- verify unavailable relays, reconnect, sync indicators, and a long encrypted checklist without leaking sensitive content to logs or browser storage

Fix any interop problems at the shared level (same payload, same `d` tag, unknown fields kept).
**Done when:** the checklist passes both directions.

## Step W5 — Polish and release

**Build:** favicon from the Nook mark, final light/dark comparison, unsupported
browser and extension messaging, static-host deployment configuration, and a
strict Content-Security-Policy with no third-party runtime scripts.
**Test:** manually check Chrome, Firefox, and Safari for sign-in, typing,
keyboard navigation, screen-reader essentials, responsive layout, relay sync,
and logout cleanup. Confirm no private key or plaintext note content remains in
browser storage after logout.
**Done when:** the manual runs pass and the site is deployed.

---

## Known risks

- **No web target in Quartz:** the web needs its own crypto implementation (W1).
- **Kotlin/Wasm browser support:** W0 verifies the compiler, Ktor transport, DOM
  interop, and supported browsers before feature work begins.
- **Extension login:** every decrypt goes through the extension, which may prompt or be slow (W4).
- **Browser differences:** Safari needs its own manual check.

## Progress log

One line per finished step: `YYYY-MM-DD  Step Wn  what was done`
