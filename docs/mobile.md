# Mobile apps (Kotlin Multiplatform)

`mobile/` is a Compose Multiplatform app for Android and iOS. The UI, view logic and Firebase access are shared (`composeApp/`); pure domain code lives in `domain/` and is tested on JVM and iOS. The apps offer the same features as the web app: attendee (ask, vote, pseudonym, my questions) and organizer (live queue, talk control, full setup).

| Module | Contents |
| --- | --- |
| `domain` | types, ordering, talk codes, pseudonyms, branding contrast, JSON import, CSV — no Firebase |
| `composeApp` | screens, navigation, `QaBackend` contract + Firestore implementation (GitLive `firebase-kotlin-sdk`) |
| `androidApp` | `MainActivity`, Firebase initialisation, Google sign-in (Credential Manager), share/open-url |
| `iosApp` | SwiftUI shell, Firebase initialisation, the Xcode project (generated, see below) |

UI text comes from the web dictionaries: `node tools/gen-kotlin-strings.mjs` regenerates `composeApp/.../i18n/GeneratedStrings.kt` (CI checks it is up to date).

## Requirements

JDK 17+, Android SDK (API 37, `local.properties` → `sdk.dir=…`), Xcode 16+ for iOS.

## Android

```sh
cd mobile
./gradlew :androidApp:assembleDebug -Phandsup.useEmulator=true     # against the local Firebase emulators
./gradlew :androidApp:assembleRelease                              # installable APK
```

Build properties (all optional; pass with `-P` or in `~/.gradle/gradle.properties`):

| Property | Default | |
| --- | --- | --- |
| `handsup.useEmulator` | `false` | talk to the emulators (`10.0.2.2` from an Android emulator) |
| `handsup.firebase.apiKey` / `appId` / `projectId` | demo values | your Firebase Android app |
| `handsup.defaultEvent` | `fresonfest-2026` | event opened on launch |
| `handsup.webBaseUrl` | `https://gdg-aranjuez.github.io/handsup` | used for links to share |
| `handsup.googleWebClientId` | empty | OAuth *web* client id; enables the Google button |

The release APK is signed with the debug key unless `mobile/keystore.properties` exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; git-ignored). Distribute the APK from a QR code; Play Store listing is a later step.

## iOS

```sh
cd mobile/iosApp
gem install xcodeproj            # once
ruby generate_project.rb         # regenerates iosApp.xcodeproj
open iosApp.xcodeproj
```

Run on a simulator from Xcode, or:

```sh
xcodebuild -project iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath build build
```

Simulator builds are signed ad hoc (do **not** pass `CODE_SIGNING_ALLOWED=NO`: Firebase Auth needs the keychain entitlement and fails silently without it).

`Config.xcconfig` holds the Firebase options and defaults to the emulators (`127.0.0.1`); override in `Config.local.xcconfig` (git-ignored). The Kotlin framework is built by the *Build Kotlin framework* phase; Firebase comes from Swift Package Manager.

Distribution without App Store review: archive in Xcode and upload to App Store Connect for **internal TestFlight** testers only (team members). Public TestFlight and the App Store need review and are post-event.

## Google sign-in

Android uses Credential Manager and needs `handsup.googleWebClientId` (the *Web client* in Firebase Auth → Google provider). iOS does not ship the GoogleSignIn SDK yet: organizers use **e-mail + password** (*Create account* sends a verification e-mail; the rules require a verified e-mail and an organizer invite). Attendees are always anonymous on mobile.

## Tests

```sh
./gradlew :domain:jvmTest :domain:iosSimulatorArm64Test :composeApp:testAndroidHostTest
```
