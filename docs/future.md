# Future steps

Deliberately out of scope for the FresON Fest 2026 launch.

- **Custom domain**: serve from a GDG Aranjuez subdomain instead of `gdg-aranjuez.github.io/handsup/`, and add it to Firebase Auth authorized domains.
- **Blaze plan**: switch from Spark with a €5 budget alert to remove the daily read cap. Stays Functions-free until needed (see ADR 0002).
- **Cloud Functions**: LLM guideline check on new Questions, scheduled 90-day purge, push notifications for Answers.
- **App stores**: Google Play and the Apple App Store after the event. Until then, Android is a signed APK and iOS is internal TestFlight only.
- **Self-hosted server**: Ktor + SQLite + SSE in one Docker image behind `QaBackend` (see ADR 0001).
- **Restrict API keys** to the Android/iOS apps and the Pages origin in Google Cloud Console.
