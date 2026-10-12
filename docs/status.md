# Nook Project Status

## Current milestone

**Current: Step 6 — Checklists are complete; device UI verification remains.**

**Next: Complete the Step 3 real-relay confirmation and begin Step 7 — Polish and release check.**

**Step 3 — Relay sync implementation is complete; real-relay manual confirmation remains.**

**Next: Complete the Step 3 real-relay confirmation, then begin Step 4 — Login.**

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
  versions dirty.
- Shared relay sync now uses Ktor WebSockets with Android and iOS engines. It
  subscribes to the fixed relays for the active author and kind `30078`, saves
  valid incoming encrypted events, debounces dirty-event publishing by one
  second, retries after reconnect, and clears dirty state after the first
  successful relay acknowledgment. Login and note UI remain unimplemented.

## Validation and open decisions

- Shared tests cover official NIP-44 v2 conversation-key and deterministic
  encryption vectors, Unicode/long-text round trips, tamper failure, Bech32
  validation, generated keys, event ID/signature verification, rejection of
  foreign-signed self-ciphertext events, and preservation of checklist-item
  extension fields.
- `:androidApp:assembleDebug`, both unit-test tasks, Detekt, ktlint, and
  Android Lint pass after Step 3. iOS target tests remain unchecked on Windows.
- Manually verify a signed kind-`30078` publish acknowledgment from at least
  one configured relay. NIP-11 metadata alone cannot prove per-kind acceptance.
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

## Step 4 update (2026-10-11)

- Completed Android nsec login, one-time new-key backup confirmation, and the
  Welcome UI; successful login leads to a temporary signed-in placeholder.
- Keys are AES-GCM encrypted with an Android Keystore key. Logout clears that
  key, the session preference, and the local encrypted-event database; both
  files are excluded from backups.
- Focused login-controller unit tests and `:androidApp:compileDebugKotlin`
  pass. The Step 3 real-relay acceptance check remains pending.
- The pasted-key UI now clears its in-memory value after successful sign-in,
  when leaving the paste screen, and on logout; its Back control returns to
  Welcome. `:androidApp:compileDebugKotlin` passes after this fix.
- Before Step 5, fix the shared module Android publication: its Android AAR is
  empty although the module's Android compilation output contains the classes.

## Step 5 update (2026-10-11)

- Added the signed-in Notes list and Note editor: live local search, pinned-first
  ordering, encrypted autosave, permanent deletion, pinning, and markdown bold,
  italic, heading, and bullet actions.
- The active Android session now opens the encrypted SQLDelight store and starts
  shared relay sync using the Keystore-protected key. It shuts down sync before
  logout clears local data.
- The generated shared Android AAR was checked and its nested `classes.jar`
  contains the shared Kotlin classes; no packaging change was required.
- Added repository mutation, markdown transformation, and Notes search UI tests.
  Focused shared and Android unit tests compile and pass; device UI verification
  remains pending.
- Step 6 owns checklist creation and its editor; the Phase 5 add action creates
  Notes only.

## Step 6 update (2026-10-11)

- Added encrypted checklist creation and updates, including item editing,
  completion toggling, deletion, and clearing completed items. Existing Nostr
  event versioning, dirty state, and relay sync are reused.
- The Notes add action now offers Note or Checklist. Checklist cards show a
  completion preview, and the editor follows the checklist mock with active
  and completed sections, a progress label, pin/delete controls, and a bottom
  clear-completed action.
- Added shared repository coverage and Android UI coverage for checklist
  creation, progress preview, and completed-item placement.
- Shared/Android unit tests and Detekt pass; ktlint formatting was applied.
  Device UI verification, the final Android Lint/debug-assembly pass, and the
  Step 3 real-relay acceptance check remain pending.

## Step 7 update (2026-10-11)

- Added relay lifecycle status to the shared sync API and surfaced Synced,
  Syncing, and Offline indicators in the Notes list, note editor, and checklist
  editor. The list now has first-use and no-search-results states.
- Refined list spacing to the documented design tokens and replaced the generic
  Android launcher green with the Nook teal adaptive-icon background.
- Added fake-relay coverage for the Offline-to-Syncing-to-Synced transition and
  Compose coverage for empty/offline and no-results states. Shared/Android unit
  tests, Detekt, ktlint, Android Lint, and debug assembly pass.
- Release blocker: run the two-physical-device real-relay checklist and compare
  every light/dark screen against the supplied mockups. The local Android SDK
  has no `adb.exe`, so that manual check could not be started here.

## Relay transport update (2026-10-12)

- Replaced Ktor's Android engine with Ktor's OkHttp engine because the Android
  engine does not support WebSockets. The Android manifest now declares Internet
  permission, and the OkHttp WebSocket client sends a 20-second keepalive ping.
- Fixed the Compose session lifecycle that immediately closed a newly created
  relay session after login or restore. The installed debug build now reaches
  `Synced` on the connected Android phone against the configured relays.
- Relay sync now exposes safe per-relay connection health for the UI and retries
  disconnected relays without logging keys or note content. The common Ktor
  transport and iOS Darwin engine remain unchanged.
- Run the two-device create/edit/delete relay check before release; iOS target
  tests remain unchecked on Windows.

## Visual rework update (2026-10-12)

- Reworked the Android Compose visuals around the supplied Nook mockups: the
  paper/teal token set, typography hierarchy, Welcome composition, Notes cards,
  relative list dates, and note/checklist editor proportions now follow the
  reference design.
- Existing key entry, backup, empty, search, and offline states retain their
  behavior while inheriting the same visual system; the signer-app label is
  present for mock parity but remains non-interactive because signer login is
  outside v1 scope.
- Light mode follows the supplied mocks and dark mode remains system-driven
  with corresponding contrast-safe colors. Validate on a physical device at the
  390x844 reference viewport as part of the outstanding release visual check.

## v1.1 signer update (2026-10-12)

- Added a portable `Signer` contract and `LocalKeySigner`; existing local-key
  encryption/signing now runs through that boundary.
- Added Android NIP-55 pairing and signer-session persistence. Signer mode
  stores only the paired public key and signer package, never an nsec.
- The Welcome signer action now explains pairing, requests only the Nook
  permissions, and handles cancellation, missing signers, and malformed
  pairing results without storing a session.
- Added the NIP-55 manifest query, ContentResolver daily-use transport with
  foreground fallback, result validation, and focused local-signer/login tests.
- Remaining: exercise the Amber real-device checklist, including remembered
  permissions, revoked permissions, unavailable signer recovery, and
  cross-device relay verification.

## Editor and checklist visual refinement update (2026-10-12)

- Reduced the note and checklist editor type scale and spacing to match the
  supplied 390x844 mockups: serif remains limited to titles and markdown
  headings, while note body and checklist text use a compact sans-serif scale.
- Reworked checklist editing into borderless, compact rows with lightweight
  completion controls and an inline add-item field; the completed section and
  bottom clear action retain the mock's sparse composition.
- Permanent note, checklist, checklist-item, and clear-completed actions now
  present contextual Cancel/confirm dialogs before mutating local data.
- Added Compose coverage for cancelling and confirming note deletion and for
  checklist-item/clear-completed confirmation. `:androidApp:compileDebugAndroidTestKotlin`,
  `:androidApp:testDebugUnitTest`, `:androidApp:ktlintCheck`,
  `:androidApp:lintDebug`, and `:androidApp:assembleDebug` pass. Connected
  device execution remains unavailable locally because Android bridge access is
  blocked.

## Theme preference persistence fix (2026-10-12)

- The selected light or dark theme now persists in app preferences across app
  launches. It remains separate from the login session, so logging out does
  not reset the user's display choice.
- Added an Android Compose regression test that toggles the theme, recreates
  the activity, and verifies the saved choice is restored.

## Editor pin and action-icon fix (2026-10-12)

- Fixed Note and Checklist editors so pinning an unchanged saved item now
  persists immediately; pending content changes must still save successfully
  before the pin state is changed.
- Replaced the Unicode action glyphs with accessible pushpin and trash-can
  vector buttons aligned with the supplied editor mock.
- The pushpin now uses an outlined icon while unpinned and a filled icon while
  pinned, in both editors.
- Added Compose regression coverage for pinning unchanged notes and checklists;
  `:androidApp:compileDebugAndroidTestKotlin`, `:androidApp:ktlintCheck`, and
  `:androidApp:assembleDebug` pass. Connected-device execution remains blocked
  locally by Android device bridge access.

## Notes overview refinement update (2026-10-12)

- Removed the redundant Notes heading, reduced the visual weight of the search field, and changed logout to an accessible icon-only control.
- Notes-list Pinned and All Notes labels now use the compact uppercase styling from the supplied Notes mock; checklist-editor section labels are unchanged.
- Added Compose coverage for the icon-only logout and list section labels; `:androidApp:compileDebugAndroidTestKotlin`, `:androidApp:ktlintCheck`, and `:androidApp:assembleDebug` pass.

## Direct signer selection update (2026-10-12)

- The Welcome signer action now launches Android's NIP-55 signer selection
  directly; the redundant in-app signer explanation and waiting screens were
  removed.
- Pairing success and all existing cancellation, unavailable-signer, and
  malformed-result handling remain unchanged.
- Added Compose coverage for the direct Welcome action; `:androidApp:testDebugUnitTest`,
  `:androidApp:compileDebugAndroidTestKotlin`, and `:androidApp:ktlintCheck` pass.

## Welcome visual alignment update (2026-10-12)

- Replaced the hand-drawn Welcome mark with the supplied Nook SVG converted to
  an Android vector resource, removed the encryption footer, and changed the
  theme control to a single accessible moon/sun action icon. Welcome actions
  now reserve the Android navigation-bar inset so system controls cannot cover
  them.
- Added Welcome UI assertions for the logo, theme-toggle state, and removed
  footer. `:androidApp:assembleDebug`,
  `:androidApp:compileDebugAndroidTestKotlin`, and
  `:androidApp:ktlintCheck` pass; connected-device execution remains blocked
  locally by Android device bridge access.

## Signer reliability fix (2026-10-12)

- Made Nook event encryption, signing, decryption, repository mutations, and
  event refresh suspendable; external signer approval no longer blocks the
  Compose main thread.
- Missing signer activities now resolve as unavailable, and rejected or failed
  signer save requests remain in the editor with a recoverable error message.
- Shared and Android unit tests plus debug assembly pass. Real-device Amber
  approval and rejection checks remain pending.

## Editor back-navigation fix (2026-10-12)

- Fixed the Notes and Checklist editors' top-left Back controls so unchanged
  editors and untouched drafts return to the Notes list; save failures still
  keep the editor open with its recoverable error message.
- Added Compose UI regression coverage for leaving an unchanged note and an
  untouched new note; updated existing test fixtures to invoke suspend
  repository writes correctly.
- `:androidApp:compileDebugAndroidTestKotlin` and `:androidApp:ktlintCheck`
  pass. The connected-device test task remains blocked locally because the
  Android device bridge reports access denied.

## Read-only notes and checklists update (2026-10-12)

- Existing notes and checklists now open as rendered, paper-like read views;
  newly created items still begin in edit mode. Compact top-bar Edit and Save
  actions replace the previous always-editable flow.
- Removed the 500 ms editor autosave. Content updates now persist and relay-sync
  only from Save or Back from edit; direct checklist completion toggles remain
  an intentional immediate-save exception.
- Added supported markdown rendering for headings, bold, italic, and bullet
  lists, plus read-only checklist rows and a Compose regression test that
  verifies no note write occurs before Save.
- `:androidApp:compileDebugKotlin`,
  `:androidApp:compileDebugAndroidTestKotlin`, and `:androidApp:ktlintCheck`
  pass. Device execution remains unavailable due to local Android bridge access.

## v1.1 signer update (2026-10-12)

- Added a portable `Signer` contract and `LocalKeySigner`; existing local-key
  encryption/signing now runs through that boundary.
- Added Android NIP-55 pairing and signer-session persistence. Signer mode
  stores only the paired public key and signer package, never an nsec.
- The Welcome signer action now explains pairing, requests only the Nook
  permissions, and handles cancellation, missing signers, and malformed
  pairing results without storing a session.
- Added the NIP-55 manifest query, ContentResolver daily-use transport with
  foreground fallback, result validation, and focused local-signer/login tests.
- Remaining: exercise the Amber real-device checklist, including remembered
  permissions, revoked permissions, unavailable signer recovery, and
  cross-device relay verification.
