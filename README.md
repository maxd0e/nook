# Nook

Nook is a private Android notes and checklist app. Your notes are stored as
encrypted Nostr events and automatically sync between devices that use the
same key.

> Nook is under active development. Before relying on it for important data,
> review the current project status and complete the outstanding real-device
> checks described there.

## Features

- Create, edit, pin, search, and permanently delete Markdown notes.
- Create checklists, manage their items, track completion, and clear completed
  items.
- Keep working offline; changes are saved locally and synced to configured
  relays when a connection is available.
- Use a locally stored `nsec` or a compatible Android signer app to access your
  identity.
- Choose light or dark appearance; your preference is retained across launches.

## Privacy and security

Each note or checklist is encrypted to your Nostr public key before it is sent
to relays. Relays store ciphertext rather than readable note titles or content.
The Android app protects locally stored keys with Android Keystore and clears
the local session data on logout.

Your `nsec` is the key to your data. Save it securely when creating an account,
never share it, and do not expect a lost key to be recoverable.

## Project structure

- `:shared` contains Kotlin Multiplatform crypto, Nostr, storage, sync, and
  notes logic. It has Android and declared iOS targets.
- `:androidApp` contains the native Android Jetpack Compose application,
  Android key storage, and app navigation.

## Build from source

Prerequisites:

- JDK 11
- Android SDK with API 37 installed

From the repository root in PowerShell:

```powershell
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:testAndroid :androidApp:testDebugUnitTest
.\gradlew.bat :shared:detekt :androidApp:detekt
.\gradlew.bat :shared:ktlintCheck :androidApp:ktlintCheck
.\gradlew.bat :androidApp:lintDebug
```

To run the device smoke test on a connected Android device or emulator:

```powershell
.\gradlew.bat :androidApp:connectedDebugAndroidTest
```

## Documentation

- [Product specification](docs/spec.md)
- [Current milestone, validation, and open decisions](docs/status.md)
- [Phased implementation plan](docs/plan.md)
- [Visual design references](docs/mock/)

## Current status

The Android app implements encrypted notes, checklists, local search, relay
sync, local-key login, and Android signer-app support. Remaining release work
includes real-device relay and visual verification; see
[`docs/status.md`](docs/status.md) for the authoritative, up-to-date details.
