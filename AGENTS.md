# Repository Guidelines

## Project Structure & Module Organization

This Android/Gradle project contains `:shared` and `:androidApp`. Portable Kotlin Multiplatform logic belongs in `shared/src/commonMain/`; keep it free of `java.*` and `android.*`. The Android Compose application belongs in `androidApp/src/main/`, with local JVM tests in `androidApp/src/test/`, device/emulator tests in `androidApp/src/androidTest/`, and shared tests in `shared/src/commonTest/`. Gradle configuration lives in `build.gradle.kts`, `settings.gradle.kts`, each module's `build.gradle.kts`, and `gradle/libs.versions.toml`.

Product requirements and visual references are in `docs/`: read `docs/spec.md` before implementing features and use `docs/mock/` as the UI reference. Do not introduce libraries without a clear need and agreement.

## Working Context

Before adding a feature or broadly scanning the codebase, read the root
`docs/status.md` file. It is the concise source of truth for the current milestone,
what has already been completed, and the focused next task. Use it to limit
inspection to files relevant to the active work, rather than scanning the
entire repository for routine context gathering.

`docs/status.md` supplements, rather than replaces, the required feature review of
`docs/spec.md`, the phased workflow in `docs/plan.md`, and UI references in
`docs/mock/`. Update `docs/status.md` after each completed feature milestone with
the completed work, next work, validation, and blockers or open decisions.

## Build, Test, and Development Commands

Run commands from the repository root in PowerShell:

```powershell
.\gradlew.bat :androidApp:assembleDebug                         # build a debug APK
.\gradlew.bat :shared:testAndroid :androidApp:testDebugUnitTest # run unit tests
.\gradlew.bat :androidApp:connectedDebugAndroidTest             # run the device smoke test
.\gradlew.bat :shared:detekt :androidApp:detekt                 # run static analysis
.\gradlew.bat :shared:ktlintCheck :androidApp:ktlintCheck       # check Kotlin formatting
.\gradlew.bat :androidApp:lintDebug                             # run Android Lint
```

Run `:androidApp:ktlintFormat` only when deliberately applying formatter changes; it rewrites source files. Use Android Studio to run the `androidApp` configuration during UI development. Keep `local.properties` local: it contains machine-specific Android SDK settings.

## Coding Style & Naming Conventions

Write Kotlin with four-space indentation and conventional Kotlin naming: `PascalCase` for classes, composables, and test classes; `camelCase` for functions, properties, and resources where applicable; and lowercase underscore-separated Android resource names (for example, `screen_note_editor.xml`). Keep package names under `io.maxdoe.nook`.

Prefer small, focused classes and clear immutable models. Use Kotlin DSL for Gradle changes and register dependency versions in `gradle/libs.versions.toml`, rather than scattering version strings. Follow ktlint formatting and run Detekt, ktlint, and Android Lint before review.

## Testing Guidelines

Use `kotlin.test` in `:shared`, JUnit 4 in Android local tests, and AndroidX JUnit/Compose test APIs for instrumentation tests. Name tests descriptively, e.g. `NoteRepositoryTest` and `savingNote_marksItDirty`. Add unit coverage for new non-UI logic and instrumentation coverage when behavior depends on Android components or views. Run the smallest relevant task while iterating; before submitting, run both unit-test tasks, Detekt, ktlint, and Android Lint. Run the connected smoke test when an emulator or device is available.

## Commit & Pull Request Guidelines

Git history is not available in this checkout, so no repository-specific commit convention can be verified. Use concise imperative subjects, such as `Add note list empty state`. Keep commits focused.

Pull requests should explain the user-visible change, note tests run, link the related issue or spec section, and include screenshots or a short recording for UI changes. Call out new dependencies, migrations, and any security implications, especially changes touching keys, encryption, storage, or relay sync.

Web server is live at http://127.0.0.1:8080/ 