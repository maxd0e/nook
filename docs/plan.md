# Nook — Build Plan (for an AI)

Build Nook v1 (Android) from `../../../Downloads/spec.md`, one small step at a time.

## Rules

1. **One phase at a time.** Do only the current phase, then stop and report what you did and anything unclear.
2. **Test first, feature second.** For each step: write the tests, run them and see them fail, build the feature, run all tests until green, commit.
3. **Follow the mockups.** Before building a screen, open its picture in `docs/mock/` and match it (layout, spacing, colors, type, shapes; tokens are in `docs/mock/design.md`). When the screen works, compare it to the mock and fix differences.
4. **Keep `:shared` portable (iOS-ready).** Logic goes in `:shared` `commonMain`: no `java.*` or `android.*` there; platform bits go behind `expect`/`actual` or an interface. UI stays native in `:androidApp`.
5. **Keep it simple.** Build only what the spec lists. No extra features, layers or libraries; ask before adding a dependency.
6. **Never log or print** keys or note contents.
7. If something in the spec is unclear or doesn't work, **ask**.

Tests stay small: unit tests for logic (in `:shared` `commonTest`), a few UI tests for the main flows. No screenshot or extra test infrastructure.

---

## Step 0 — Project

**Build:** Gradle project with `:shared` (Kotlin Multiplatform: android, iosArm64, iosSimulatorArm64) and `:androidApp` (Jetpack Compose). Packages in `:shared`: `crypto`, `nostr`, `data`, `notes`. Light/dark theme with the colors and fonts from `docs/mock/README.md`, empty home screen.
**Test first:** a test in `:shared` `commonTest` runs, and a smoke test that the app starts.
**Done when:** it builds and runs, tests pass, and the iOS targets compile (if a Mac or macOS CI runner is available; otherwise note that this is unchecked).

## Step 1 — Keys and encryption (in `:shared`)

**Tests first:**
- NIP-44 official test vectors, plus a round trip to self (empty, long and unicode text)
- decrypting tampered data fails safely
- nsec/npub bech32 encode and decode; invalid input rejected
- generating a key gives a valid pair
- an event gets signed correctly (id and signature verify)

**Build:** key helpers, NIP-44 self-encrypt/decrypt, event signing. Try Quartz first; if it doesn't pass the tests easily, write the small pieces yourself. Note the choice and pick the 3 relays (test that they accept kind 30078).
**Done when:** all tests pass.

## Step 2 — Notes data (in `:shared`)

**Tests first:**
- note, checklist and tombstone payloads convert to JSON and back; unknown fields survive
- an event is built correctly (kind 30078, `d` tag `nook:<uuid>`, encrypted content) and parsed back
- events with a foreign `d` tag or undecryptable content are ignored
- the newest `created_at` wins, ties go to the lowest id, a new version uses `max(now, previous + 1)`
- the local database keeps events encrypted, marks edits `dirty`, and can list decrypted notes (pinned first)

**Build:** payload models, event builder and parser, SQLDelight database, a repository that exposes the note list (as a `Flow`) and search (in memory).
**Done when:** all tests pass.

## Step 3 — Relay sync (in `:shared`)

**Tests first** (use a fake in-memory relay, no real network):
- initial sync downloads and stores events
- an edit is published, and a second simulated device receives it
- dirty edits made offline are sent after reconnect
- a delete (tombstone) removes the note on the other device
- the same event arriving twice is handled once

**Build:** relay client (Ktor WebSockets) for the 3 relays, subscribe and publish, retry of dirty items, debounce of about 1 second.
**Done when:** the tests pass, and one manual check against a real relay works.

## Step 4 — Login (`docs/mock/01-welcome.png`)

**Tests first:**
- pasting a valid nsec logs in; an invalid one shows an error
- "create a new key" cannot continue until "I saved it" is confirmed
- logout deletes the key and all local data

**Build:** Welcome screen as mocked, but without the "Sign in with a signer app" button (v1 has no signer). nsec paste screen and nsec backup screen in the same style. Key storage with Android Keystore protection (`expect`/`actual`), logout.
**Done when:** tests pass, and the Welcome screen matches the mock.

## Step 5 — Notes screens (`02-notes-list.png`, `03-note-editor.png`)

**Tests first:**
- the list shows pinned first, then newest first; search filters live
- creating, editing, pinning and deleting a note updates the list
- the editor toolbar (bold, italic, heading, bullet list) produces the expected markdown and reads it back

**Build:** notes list with search and "+" button (offers Note or Checklist), note editor with the slim toolbar (without the numbered-list button), pin and delete.
**Done when:** tests pass, changes sync between two devices, and both screens match the mocks.

## Step 6 — Checklists (`04-checklist.png`)

**Tests first:**
- add, edit, check and delete items; checked items move to a "Completed" section; "Clear completed" removes them
- a checklist round-trips through the payload and shows in the list with its "x of y done" preview

**Build:** checklist screen and list card.
**Done when:** tests pass, checklists sync like notes, and the screen matches the mock.

## Step 7 — Polish and release check

**Build:** sync status indicator, empty and offline states, app icon from the logo, dark theme (use the proposal in `docs/mock/README.md`). Compare every screen to its mock once more.
**Test:** a short manual checklist on a real phone with two devices: create a key, create a note and a checklist on device A, log in with the same nsec on device B, see both, edit on B, see it on A, delete, log out and confirm nothing is left.
**Done when:** the manual checklist passes.

---

## After v1 (separate small plans, not now)

1. **v2 Web:** add a web target to `:shared`, native web UI, NIP-07 login.
2. **iOS:** SwiftUI app on `:shared`, after v1 and v2.

## Progress log

One line per finished step: `YYYY-MM-DD  Step N  what was done`

2026-10-10  Step 0  Added KMP shared module, Compose Android app shell, quality checks, and smoke tests
2026-10-10  Step 1  Added Quartz-backed portable Nostr keys, NIP-44 encryption, Bech32, event signing, relay defaults, and tests
2026-10-10  Step 2  Added encrypted Nook payloads/events, SQLDelight storage, repository/search, and tests
