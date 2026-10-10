# Nook Project Status

## Current milestone

**Step 2 — Notes data is complete.**

**Next: Step 3 — Relay sync.** Build the Ktor WebSocket client, subscription,
publish/retry behavior, and fake-relay tests.

## Current state

- `:shared` is a Kotlin Multiplatform module with Android, `iosArm64`, and
  `iosSimulatorArm64` targets. It now provides portable Nostr key generation,
  NIP-19 `nsec`/`npub` conversion, NIP-44 v2 self-encryption/decryption, and
  canonical Nostr event signing/verification.
- Quartz `1.13.1` backs the crypto implementation. Kotlin is `2.4.10`, and the
  Android minimum SDK is API 26 because Quartz requires it.
- The fixed relay configuration contains `wss://relay.damus.io`,
  `wss://nos.lol`, and `wss://relay.primal.net`. Their public NIP-11 metadata
  was reachable without an advertised auth or payment requirement.
- `:androidApp` is a native Jetpack Compose shell. It launches an empty,
  edge-to-edge home surface with the initial Nook light/dark color theme and
  typography.
- The project contains a Compose launch smoke test and an Android application
  identity unit test. Detekt and ktlint are configured for both modules.
- Portable note, checklist, and tombstone payloads preserve unknown JSON fields.
  Kind-`30078` Nook events are self-encrypted, signed, parsed safely, and kept
  in a SQLDelight database as encrypted event fields only.
- The shared repository exposes an in-memory decrypted `Flow` projection and
  local search, resolves conflicts by timestamp then event ID, and marks local
  versions dirty. Relay sync, login, and UI remain unimplemented.

## Validation and open decisions

- Shared tests cover official NIP-44 v2 conversation-key and deterministic
  encryption vectors, Unicode/long-text round trips, tamper failure, Bech32
  validation, generated keys, event ID/signature verification, rejection of
  foreign-signed self-ciphertext events, and preservation of checklist-item
  extension fields.
- `:androidApp:assembleDebug`, both unit-test tasks, Detekt, ktlint, and
  Android Lint pass. iOS target tests remain unchecked on Windows.
- Verify a signed kind-`30078` publish acknowledgment from each chosen relay
  when the WebSocket relay client is built in Step 3; NIP-11 metadata alone
  cannot prove per-kind acceptance.
- Confirm whether kind `30078` remains the appropriate long-term kind.

## Working rules

1. Before adding a feature or broadly scanning the codebase, read this file
   for the current milestone and focused next steps.
2. For feature implementation, also read `spec.md`; use `plan.md`
   for the phase sequence and `` for UI work.
3. Inspect only the files relevant to the active milestone unless this status
   document or the task requires wider context.
4. Update this document after each completed feature milestone with what
   changed, what is next, validation performed, and any blockers or unresolved
   decisions.
