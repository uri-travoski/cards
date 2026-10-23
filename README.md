# Cards

Android app (Kotlin + Compose, package `au.sam.cards`) for bank, credit and loyalty cards, replacing Mobile Pocket.
Cards live in the user's own Google Drive (hidden `appDataFolder`, scope `drive.appdata`), so signing in with the
same Google account on any phone restores everything.

- Per card: type, name, number, holder, expiry, CVV, PIN, notes, colour, front/back photos, barcode (scan with
  Google's code scanner or type; drawn with ZXing at full brightness).
- Sync (`Sync.kt`): each card is `card-<id>-<updated>.json`, images are `img-*.jpg`. Newest `updated` wins; deletions
  propagate. Runs on launch, after every save/delete, and on the Sync button. Works offline, syncs later.
- App lock with the phone's screen lock (also sets FLAG_SECURE, so no screenshots). Toggle in Settings.

## One-time Google setup (required before sign-in works)
1. console.cloud.google.com: create a project, enable **Google Drive API**.
2. OAuth consent screen: External, add yourself as a test user (or publish).
3. Credentials > Create OAuth client ID > **Android**, package `au.sam.cards`, SHA-1 of the signing key
   (`keytool -list -v -keystore ~/.android/debug.keystore -storepass android`; the release APK is debug-signed).
No client ID goes in the code; Google matches package + SHA-1.

## Build
`./gradlew assembleRelease` (APK in app/build/outputs/apk/release/).

## Testing without Google
`tools/mock_drive.py` fakes the Drive API. Put `cards.mockDrive=http://10.0.2.2:8099` in local.properties,
rebuild, use an emulator. Never ship a build with that line set.
