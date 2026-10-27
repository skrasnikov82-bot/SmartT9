# SmartT9 Android keyboard

Android IME keyboard for Russian and English. Offline suggestions and optional opt-in AI correction.

The optional API key is entered on-device, encrypted with Android Keystore, and never committed to this repository. AI correction sends only the text selected for correction or the current line on explicit tap.

Build and release: `.github/workflows/build-apk.yml` creates an APK and publishes it in Releases.

Note: this is a test application. Password fields disable AI and word suggestions; other sensitive data should not be sent to the AI correction service.
