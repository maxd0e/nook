# Repository Guidelines

## Project Structure & Module Organization

This is an Android/Gradle project currently containing the `:app` module. Application code belongs in `app/src/main/`, with the manifest at `app/src/main/AndroidManifest.xml` and Android resources under `app/src/main/res/`. Put local JVM tests in `app/src/test/` and device/emulator tests in `app/src/androidTest/`. Gradle configuration lives in `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, and `gradle/libs.versions.toml`.

Product requirements and visual references are in `docs/`: read `docs/spec.md` before implementing features and use `docs/mock/` as the UI reference. The spec anticipates future `:shared` and `:androidApp` modules; do not introduce them or new libraries without a clear need and agreement.

## Build, Test, and Development Commands

Run commands from the repository root in PowerShell:

```powershell
.\gradlew.bat assembleDebug          # build a debug APK
.\gradlew.bat test                   # run local unit tests
.\gradlew.bat connectedAndroidTest   # run instrumentation tests on a device/emulator
.\gradlew.bat lint                   # run Android Lint
```

Use Android Studio to run the `app` configuration during UI development. Keep `local.properties` local: it contains machine-specific Android SDK settings.

## Coding Style & Naming Conventions

Write Kotlin with four-space indentation and conventional Kotlin naming: `PascalCase` for classes, composables, and test classes; `camelCase` for functions, properties, and resources where applicable; and lowercase underscore-separated Android resource names (for example, `screen_note_editor.xml`). Keep package names under `io.maxdoe.nook`.

Prefer small, focused classes and clear immutable models. Use Kotlin DSL for Gradle changes and register dependency versions in `gradle/libs.versions.toml`, rather than scattering version strings. The project has no configured formatter; use Android Studio's Kotlin formatting and run lint before review.

## Testing Guidelines

Use JUnit 4 for unit tests and AndroidX JUnit/Espresso for instrumentation tests. Name tests descriptively, e.g. `NoteRepositoryTest` and `savingNote_marksItDirty`. Add unit coverage for new non-UI logic and instrumentation coverage when behavior depends on Android components or views. Run the smallest relevant task while iterating, then `test` and `lint` before submitting.

## Commit & Pull Request Guidelines

Git history is not available in this checkout, so no repository-specific commit convention can be verified. Use concise imperative subjects, such as `Add note list empty state`. Keep commits focused.

Pull requests should explain the user-visible change, note tests run, link the related issue or spec section, and include screenshots or a short recording for UI changes. Call out new dependencies, migrations, and any security implications, especially changes touching keys, encryption, storage, or relay sync.
