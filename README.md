# Cards (Open Source)

**[Download the latest APK](https://github.com/uri-travoski/cards/releases/latest/download/cards.apk)** (Android 8+, 64-bit ARM). Open the file on your phone and allow installing from your browser.

Android app (Kotlin + Compose, package `au.sam.cards`) for bank, credit and loyalty cards, replacing Mobile Pocket.
Cards always live in the cloud, never only on the phone (the phone keeps a cache). Three storage options, chosen on
first run and changeable in Settings (switching re-uploads everything to the new place):
- **Google Drive**: hidden `appDataFolder`, scope `drive.appdata`; sign in with the same account on any phone.
- **Cloud folder**: a folder picked with Android's file picker inside a cloud app (Dropbox, OneDrive, Nextcloud...).
  Folders on the phone itself (Downloads, internal storage) are refused in release builds.
- **S3-compatible bucket** (AWS S3, Backblaze B2, Cloudflare R2, MinIO): endpoint, region, bucket, key pair; objects
  live under `cards/`. Requests are signed with AWS Signature V4 (`Backend.kt`).

- Per card: type, name, number, holder, expiry, CVV, PIN, notes, colour (wheel), front/back photos (cropped after
  capture; tap a photo in the details to view it full screen with pinch zoom), barcode (scan with Google's code
  scanner, detect from a photo, or type; the type is detected; drawn with ZXing at full brightness).
- Auto-fill: adding a card photo reads number (Luhn-checked), expiry, name on card, barcode, brand and type on the phone, fills only empty fields and always shows a "check these details" warning. CVV/PIN are never read.
- Expiry: typed (MM/YY) or read from the photo with ML Kit OCR (latest date found). Cards past the end of their
  expiry month are marked Expired and listed below the Active ones; each group is A to Z.
- Not encrypted by the app: card data (including CVV/PIN) is readable by whoever runs the storage.
- Sync (`Sync.kt`): each card is `card-<id>-<updated>.json`, images are `img-*.jpg`. Newest `updated` wins; deletions
  propagate. Runs on launch, after every save/delete, and on the Sync button. Works offline, syncs later.
- Help (question-mark icon): full setup guide for each storage, including the Google Cloud steps with this install's package and SHA-1.
- Settings has Check for updates / Update now, which compares with the latest GitHub release and opens the APK link.
- Orange Material 3 theme (light and dark) and an adaptive launcher icon.
- App lock with the phone's screen lock (also sets FLAG_SECURE, so no screenshots). Toggle in Settings.

## One-time Google setup (required before sign-in works)
1. console.cloud.google.com: create a project, enable **Google Drive API**.
2. OAuth consent screen: External, add yourself as a test user (or publish).
3. Credentials > Create OAuth client ID > **Android**, package `au.sam.cards`, SHA-1 of the signing key
   (`keytool -list -v -keystore ~/.android/debug.keystore -storepass android`; the release APK is debug-signed).
No client ID goes in the code; Google matches package + SHA-1.

## Build
`./gradlew assembleRelease` (APK in app/build/outputs/apk/release/, arm64 only, ~20 MB because the OCR and barcode
models are bundled so they work offline). Debug builds include x86_64 for the emulator.

## Testing without the real services
Emulator only; never ship a build with these local.properties lines set.
- `tools/mock_drive.py` fakes Drive: `cards.mockDrive=http://10.0.2.2:8099` (skips Google sign-in).
- `tools/mock_s3.py` fakes S3 and verifies the Signature V4 (`selftest` argument checks the verifier against AWS's
  published example). Enter `http://10.0.2.2:8098`, region auto, bucket test, key AKIATEST / SECRETTEST.
- `cards.allowLocalFolder=true` lets the folder option accept a folder on the phone (no cloud app on an emulator).
- Debug builds allow http; release builds do not.
