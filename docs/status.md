# Nook Project Status

## Release automation preparation (2026-10-15)

- Added the tag-gated Android `v*` release workflow. It runs Android quality
  checks, requires the protected `zapstore-production` environment before
  signing, creates or updates the GitHub Release with an APK and checksum, and
  publishes the signed APK through Zapstore using only a NIP-46 bunker secret.
- Added committed Zapstore metadata, a first-release changelog, and the MIT
  license. The workflow never stores a keystore, password, bunker URL, or
  private key in the repository.
- Before tagging `v0.1.0`, provision the release keystore and all protected
  environment secrets, run the one-time Zapstore certificate-link operation,
  complete the documented manual Android and web acceptance checks, then push
  `master` followed by the tag.
- Local validation passes: shared and Android unit tests, Detekt, ktlint,
  Android Lint, debug assembly, and a signed release build with an ephemeral
  test keystore. Publication still requires the protected production keystore
  material and bunker URL.

## Android/web release-parity polish (2026-10-15)

- The signed-in Android Notes list now shows the compact Nook logo and wordmark
  at left, with the persisted light/dark action and icon-only logout at right.
- Sync state and the expandable per-relay health disclosure share a dedicated
  status row, matching the web workspace arrangement.
- Checklist item entry keeps focus and the software keyboard ready for the next
  item after an add; note editing now has web-matched bullet and numbered-list
  vector controls, with numbered markdown display support.
- The note formatting toolbar now uses compact, aligned B/I/H/list/numbered
  controls directly above the Android navigation bar without covering native
  Back or Home controls.
- Numbered-list insertion now continues an adjacent sequence (1, 2, 3) instead
  of restarting every item at 1.
- The compact numbered-list action now renders literal two-row `1.` and `2.`
  glyphs in Compose, avoiding unreadable VectorDrawable number paths.
- Pressing Enter from a numbered note item now inserts the next number and
  keeps the cursor ready for the next entry.
- Android and web editors now also continue `- ` bullet items on Enter.
- The web editor now uses the same readable literal 1/2 numbered-list control
  and continues numbered items on Enter.
- Added focused Compose and markdown regression coverage. Pending validation:
  Android compilation/tests and release quality checks.

## GitHub Pages beta release preparation (2026-10-15)

- The web deployment workflow now enables GitHub Pages through GitHub Actions
  when needed and deploys the production Wasm bundle from `master`.
- The welcome screen labels the public site as a beta preview. The local
  production distribution and strict-CSP startup test pass.
- Before a full release, manually verify real-relay and NIP-07 sessions plus
  Chrome, Firefox, and Safari sign-in, typing, responsive layout, and logout
  storage cleanup.

## Web checklist inline-add editing update (2026-10-15)

- Adding a checklist item in the web editor now persists the item while keeping
  the editor active for consecutive entries; the toolbar Save action still
  switches to the read-only view.
- A new note can now switch to a checklist after its title is entered, retaining
  that title; body text still prevents a type switch until cleared.
- Checklist rows now appear immediately on add and retain focus in the next
  entry field while serialized background saves protect newer item-label edits
  from older save completions and background workspace redraws.
- Playwright coverage verifies two consecutive additions remain editable before
  an explicit Save displays both persisted items, and verifies title-preserving
  switching plus the body-text guard and optimistic label preservation.

## Web workspace and deletion reliability update (2026-10-15)

- Replaced the web workspace's floating add control with a rectangular New note
  control above the note sections. A new empty note can switch to checklist
  editing before it is saved.
- Added distinct bullet-list and numbered-list editor controls, widened the
  bounded desktop editor, and reserved space so the formatting toolbar does
  not cover body text. Relay status now shares the sync-status row at its
  right edge.
- Tombstones now permanently suppress every version of the same item ID,
  preventing a stale device edit from restoring a deleted note after refresh
  or relay replay. Repository coverage includes this persisted reload case.

## Web logout route reset (2026-10-14)

- Logging out from a routed note or checklist now clears the browser fragment
  while resetting the in-memory route to Notes.
- The browser logout/storage scenario verifies the post-logout URL alongside
  the existing session and IndexedDB cleanup checks.

## Web routed-note reliability fix (2026-10-14)

- Web note and checklist cards now retain the selected item while changing the
  hash route, so a routing render cannot clear the just-opened detail view.
- Browser coverage repeatedly opens a note and verifies both its URL and read
  view; web formatting and browser tests validate the change.

## Web workspace layout refinement (2026-10-14)

- The signed-in web side panel now uses the Nook logo and wordmark, with an
  icon-only accessible logout control and Android-style Search field.
- The note and checklist close action now appears at the upper right directly
  beside delete. The floating add control uses a centered vector plus icon.
- `:webApp:ktlintCheck`, `:webApp:wasmJsBrowserTest`, and the full workspace
  Playwright suite pass.

## Web visual parity update (2026-10-14)

- The web Welcome screen now renders the bundled Nook SVG, and the former
  bottom browser-extension guidance notice has been removed. Compatibility
  warnings and browser-extension sign-in behavior remain unchanged.
- Web action controls now use the Android vector paths for theme, logout,
  edit/save, pin, and delete. Note and checklist editors expose a labeled
  Notes control that returns to the signed-in Notes list.
- `:webApp:ktlintCheck` and `:webApp:wasmJsBrowserTest` pass. The Playwright
  Welcome, notes, checklist, and responsive-navigation scenarios pass; the
  pre-existing browser-storage/logout cleanup scenario still times out while
  waiting for IndexedDB cleanup and requires separate investigation.

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

## v2 web W3 E2E repair update (2026-10-14)

- Fixed the remaining `WebLoginSession.kt` formatting violations and the
  companion `Main.kt` chain-format violation; `:webApp:ktlintCheck` passes.
- Workspace E2E now builds the development bundle explicitly and serves it
  without the production CSP, which permits its development Webpack runtime
  while leaving the release CSP test intact. The suite uses exact formatting
  toolbar selectors, avoids a duplicate checklist save, and shuts down its
  local HTTP server without leaving held connections.
- IndexedDB active-account cleanup now deletes the indexed keys in one
  transaction rather than walking a cursor, resolving the browser logout
  cleanup path. The workspace suite passes its note, checklist, responsive,
  ciphertext-only, and logout flows; the production bundle CSP-startup test
  also passes. `:webApp:wasmJsBrowserTest` passes.

## v2 web E2E follow-up (2026-10-13)

- The Playwright workspace setup injects an E2E-only browser flag. It opens the
  test session with no relay URLs, so workflow tests cannot publish generated
  activity to public Nostr relays. The suite also reopens newly saved items before
  testing pinning, verifies that IndexedDB contains a ciphertext record before
  logout, and polls until the active account's records are removed after logout.
- The generated-key confirmation path retains the generated key pair in memory
  rather than parsing the displayed private key again before opening a session.
- **Known gap:** `npm.cmd run test:e2e` remains red. Sign-in reaches the empty
  workspace under the isolated configuration, but creating a note does not finish
  its Wasm save path, so the note remains in edit mode. Fix this before W3 is
  considered complete or these tests are treated as a required green suite. No
  real-relay validation was attempted.

## v2 web W2 update (2026-10-13)

- Added a native Kotlin/Wasm Welcome, pasted-`nsec` entry, generated-key
  backup, session placeholder, light/dark preference, clipboard copy, and
  logout flow in `:webApp`.
- Local-key credentials exist only in the active controller/session; they are
  never written to browser storage. Logout stops relay sync, clears the
  IndexedDB ciphertext store, and releases the in-memory session while keeping
  the non-sensitive theme preference.
- Added a narrow NIP-07 adapter that requires `getPublicKey`, `signEvent`, and
  NIP-44 encrypt/decrypt methods before sign-in. Extension sessions delegate
  signing and encryption to `window.nostr`.
- Focused Wasm login-controller tests and the Wasm browser test task pass. The
  W1 real-relay acceptance check and browser-manual NIP-07 coverage remain
  required before web release.

## v2 web security hardening update (2026-10-13)

- IndexedDB event reads and logout cleanup are now scoped to the active
  signer's public key. A browser profile can retain another account's
  ciphertext without that account's dirty events being loaded or published.
- NIP-07 signing now rejects extension responses unless their signer key,
  signature, timestamp, kind, tags, and content exactly match the requested
  event. Added a Wasm regression test for a validly signed but altered event.
- `:webApp:ktlintCheck` and `:webApp:wasmJsBrowserTest` pass.

## v2 web editor reliability update (2026-10-13)

- Web editor drafts now track their last successfully saved state. Back leaves untouched
  drafts without writing, and a failed save keeps a changed draft open instead of
  navigating away.
- Checklist edit rows now expose editable text inputs; changed labels save with the
  rest of the checklist.

## v2 web W3 verification update (2026-10-13)

- Added the responsive web visual baseline, safe per-relay status disclosure,
  a W4 Android-to-web real-relay checklist, and Playwright scenarios for the
  notes, checklists, confirmations, responsive navigation, and ciphertext-only
  browser storage flows.
- W3 remains incomplete: after generated-key backup confirmation, the browser
  session does not advance to the workspace under Playwright. The new workflow
  regression tests expose this session-opening blocker; investigate the
  IndexedDB/session bridge before attempting W4 real-relay validation.
- `:webApp:wasmJsBrowserTest` and `:webApp:ktlintCheck` pass. The expanded
  Playwright suite currently has one Welcome-only passing scenario and four
  expected failures at the documented sign-in blocker.
- Once W3 is unblocked, W4 uses a fresh test identity to verify Android↔web
  create/edit/pin/checklist/delete convergence, reconnect/status behavior, a
  long checklist, and both local-nsec and NIP-07/NIP-44 web sessions. Record
  only environment and pass/fail data; never keys, note content, ciphertext,
  or event IDs. Confirm that browser storage contains ciphertext only while
  signed in and that logout removes the active account's records.

## Private-key backup copy update (2026-10-12)

- The one-time "Save your private key" screen now includes a full-width
  "Copy private key" action that copies the displayed `nsec` to the Android
  clipboard without persisting any additional key material, plus a Back route
  that discards the unsaved generated key and returns to Welcome.
- Added controller and Compose coverage for the backup actions and Back route.
  `:androidApp:testDebugUnitTest`, `:androidApp:assembleDebug`, and
  `:androidApp:ktlintCheck` pass.
- Corrected the backup Back control's malformed chevron glyph; it now matches
  the existing Paste-key Back control.
- `:androidApp:compileDebugAndroidTestKotlin` remains blocked by the existing
  unresolved `assertDoesNotExist` import in `NookNotesTest`, outside this
  change.

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

## Notes add-action motion update (2026-10-12)

- Replaced the Notes add dropdown with an animated vertical speed dial: Note and Checklist actions now expand upward from the `+` control, which rotates into a close control while open.
- Raised the add control from a 28dp to a 52dp bottom inset and added an outside-tap dismiss target with accessible add/close labels.
- Added Compose coverage for opening a Note from the speed dial and closing it with the main control; `:androidApp:compileDebugAndroidTestKotlin`, `:androidApp:assembleDebug`, and `:androidApp:ktlintCheck` pass.

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

## Editor navigation-bar inset fix (2026-10-12)

- The note formatting toolbar and checklist bottom action area now reserve the
  Android navigation-bar inset, keeping their actions above the system Back and
  Home controls on edge-to-edge devices.

## Checklist add keyboard fix (2026-10-12)

- Adding a checklist item now clears input focus and dismisses the software
  keyboard. A Compose regression test covers the focus reset.
- `:androidApp:compileDebugAndroidTestKotlin` and
  `:androidApp:ktlintCheck` pass. The test source also now uses the supported
  collection assertion in place of an unavailable `assertDoesNotExist` import.

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
