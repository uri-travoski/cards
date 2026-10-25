package au.sam.cards

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import java.security.MessageDigest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private fun icon(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black)).build()

val FilterIcon: ImageVector by lazy { icon("filter", "M10,18h4v-2h-4v2zM3,6v2h18V6H3zm3,7h12v-2H6v2z") }
val SyncIcon: ImageVector by lazy {
    icon("sync", "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z")
}
val HelpIcon: ImageVector by lazy {
    icon("help", "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z")
}

private class Section(val title: String, val body: String, val extra: (@Composable () -> Unit)? = null)

private val CONSOLE = "https://console.cloud.google.com/"

private fun sha1(ctx: Context): String = try {
    val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val cert = info.signingInfo!!.apkContentsSigners[0].toByteArray()
    MessageDigest.getInstance("SHA-1").digest(cert).joinToString(":") { "%02X".format(it) }
} catch (e: Exception) { "unavailable" }

@Composable
private fun DriveDetails() {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val sha = remember { sha1(ctx) }
    fun copy(label: String, v: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, v))
        Toast.makeText(ctx, "$label copied", Toast.LENGTH_SHORT).show()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RowShape) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Values to paste into Google (this installed app):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Package name", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Text(ctx.packageName, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            OutlinedButton({ copy("Package name", ctx.packageName) }, shape = ButtonShape) { Text("Copy package name") }
            Text("SHA-1 certificate fingerprint", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Text(sha, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            OutlinedButton({ copy("SHA-1", sha) }, shape = ButtonShape) { Text("Copy SHA-1") }
            Button({ uri.openUri(CONSOLE) }, shape = ButtonShape) { Text("Open Google Cloud Console") }
        }
    }
}

private val SECTIONS: List<Section> = listOf(
    Section("What this app does",
        "Cards keeps your bank (debit and credit) and loyalty cards in one place. Your cards are saved in the cloud, so on a new phone you install the app, connect the same storage and every card comes back. The phone only keeps a copy so it works offline.\n\nYou choose one place to keep them: Google Drive, a folder in a cloud app (Dropbox, OneDrive...), or your own storage bucket (S3, Backblaze B2, Cloudflare R2). Setup for each is below."),
    Section("Which storage should I pick?",
        "• Cloud folder: easiest. No accounts to create if you already use Dropbox, OneDrive, Nextcloud, Box, MEGA or pCloud. Takes one minute.\n• Google Drive: sign in with your Google account on every phone, but needs a one-time setup in Google Cloud (about 10 minutes, free, done once on any computer or phone browser). Steps below.\n• S3 / B2 / R2 bucket: for people who already have a bucket. Needs an endpoint and access keys."),
    Section("Google Drive: why setup is needed",
        "Google only lets an app read and write Drive if the app is registered with Google. Cards is not published by a company, so you register it yourself, once, in your own free Google Cloud project. The app can then only touch its own hidden folder in your Drive, never your other files.\n\nYou do this once. After that, every phone just taps Google Drive and signs in. The steps below use a web browser; a computer is easiest but a phone works.",
        { DriveDetails() }),
    Section("Google Drive: step by step (one time)",
        "A. Create a project\n1. Open the Google Cloud Console (button above) and sign in with the Google account that will hold the cards.\n2. Accept the terms if asked. Tap the project picker at the top, then New project.\n3. Name it Cards and tap Create. Make sure it is selected at the top.\n\nB. Turn on the Drive API\n4. Open the menu, APIs & Services, then Library.\n5. Search for Google Drive API, open it and tap Enable.\n\nC. Set up the consent screen\n6. Menu, APIs & Services, OAuth consent screen (it may be called Google Auth Platform, Branding).\n7. Get started. App name: Cards. User support email: your email. Audience: External. Contact email: your email. Agree and Create.\n8. Open Data access (or Scopes), Add or remove scopes, search for drive.appdata, tick the one named .../auth/drive.appdata (See, create and delete its own configuration data in your Google Drive), Update, Save. This is the only permission the app asks for.\n9. Open Audience and tap Publish app (Push to production), then Confirm. This scope needs no Google review, and publishing stops sign-in expiring after 7 days. If you prefer to stay in Testing, add your own Google account under Test users instead and expect to sign in again weekly.\n\nD. Create the Android client\n10. Menu, APIs & Services, Credentials (or Google Auth Platform, Clients). Create credentials, OAuth client ID.\n11. Application type: Android. Name: Cards.\n12. Package name: paste the one from the box above (au.sam.cards).\n13. SHA-1 certificate fingerprint: paste the SHA-1 from the box above. It must match exactly.\n14. Tap Create. You do not need to copy any client ID anywhere. Google matches the app by package name and SHA-1.\n\nE. Sign in\n15. Wait about 5 minutes (sometimes up to an hour) for Google to apply it.\n16. In Cards open Settings, Change storage, tap Google Drive and choose your account. Tap Allow.\n17. When Google says Google hasn't verified this app (only if you did not publish it), tap Advanced, then Go to Cards (unsafe). That is expected for your own app."),
    Section("Google Drive: on a new phone, and if it fails",
        "New phone: install the same APK, tap Google Drive and sign in with the same account. No Cloud Console steps again, as long as the APK is signed with the same key (the SHA-1 in the box above is the same). If you install an APK signed with a different key, add its SHA-1 as another Android client in the Console.\n\nSign-in fails:\n• Developer error or code 10: package name or SHA-1 does not match the client. Compare them with the box above and wait a few minutes after creating the client.\n• Access blocked, app not verified: publish the app (step 9) or add yourself as a test user.\n• API has not been used: enable Google Drive API (step 5) in the same project.\n• 403 or insufficient permission: the drive.appdata scope is missing (step 8)."),
    Section("Cloud folder: step by step",
        "Works with any cloud app that appears in Android's file picker: Dropbox, OneDrive, Nextcloud, Box, MEGA, pCloud, Proton Drive and others. Google Drive can work too but use the Google Drive option instead.\n\n1. Install the cloud app from the Play Store and sign in to it. Keep it installed.\n2. In Cards open Settings, Change storage, tap Cloud folder.\n3. Android's file picker opens. Tap the menu (three lines top left, or the three dots top right, then Show internal storage if needed).\n4. Choose your cloud app from the list (for example Dropbox). If it is missing, the app does not support the picker.\n5. Open the folder you want, or tap the new-folder icon and create one called Cards.\n6. Tap Use this folder, then Allow.\n\nThe app tests it by writing there. Cards are saved as small files in that folder plus photos. Do not rename or delete them in the cloud app.\n\nIf it says the folder is on this phone, you picked Downloads, Documents or internal storage. Go back and choose a folder under your cloud app's name.\n\nNew phone: install and sign in to the same cloud app, tap Cloud folder, pick the same folder. Your cards appear after the first sync.\n\nSome cloud apps only download files when asked. If sync is slow, open the cloud app once so it finishes syncing."),
    Section("S3, Backblaze B2 or Cloudflare R2: step by step",
        "Fill in five boxes: Endpoint, Region, Bucket, Access key, Secret key. Where to get them:\n\nBackblaze B2 (free up to 10 GB)\n1. Sign up at backblaze.com, open B2 Cloud Storage, Buckets, Create a Bucket. Keep it Private. Name it, for example my-cards-123.\n2. Open the bucket details and note its Endpoint, such as s3.us-west-004.backblazeb2.com. The region is the middle part, us-west-004.\n3. App Keys, Add a New Application Key. Limit it to your bucket, Read and Write. Copy keyID (access key) and applicationKey (secret). The secret is shown once.\n4. In Cards: Endpoint https://s3.us-west-004.backblazeb2.com, Region us-west-004, your bucket name, keyID, applicationKey.\n\nCloudflare R2 (free up to 10 GB)\n1. In the Cloudflare dashboard open R2, Create bucket.\n2. R2 overview shows your Account ID. Endpoint is https://ACCOUNTID.r2.cloudflarestorage.com\n3. Manage API tokens, Create API token, permission Object Read & Write, limited to your bucket. Copy Access Key ID and Secret Access Key.\n4. In Cards: that endpoint, Region auto, bucket name, the two keys.\n\nAmazon S3\n1. In the S3 console create a bucket (block all public access stays on).\n2. In IAM create a user with a policy allowing s3:ListBucket on the bucket and s3:GetObject, s3:PutObject, s3:DeleteObject on bucket/cards/*. Create an access key for it.\n3. In Cards: Endpoint https://s3.REGION.amazonaws.com, Region such as ap-southeast-2, bucket name, keys.\n\nTap Connect. The app lists the bucket to test the details first and shows the error if something is wrong (403 means wrong keys or permissions, 404 means wrong bucket name or endpoint). Cards live under a cards/ folder in the bucket.\n\nNew phone: enter the same five values again. Save them in a password manager."),
    Section("Changing storage later",
        "Settings (gear icon), Change storage, pick another option. All cards are uploaded to the new place. The old place is not cleaned up, delete those files yourself if you want."),
    Section("Adding a card",
        "Tap the orange + button.\n1. Choose the type (Bank for debit and credit cards, Loyalty, Other) and enter a name.\n2. Photos: tap Front or Back to take a photo or pick one. After taking it, drag the corners, edges or the whole frame to crop, Rotate if needed, then tap Use.\n3. Colour: tap the colour wheel, drag to any colour, and use the slider for brightness.\n4. Number, holder, CVV, PIN and notes: fill in what you want to keep. All optional.\n5. Expiry: filled from the photo if found, check it. Or type MM/YY (for example 08/27), or tap Read expiry from photo to read it from your card photo and check it. The field warns if the date is in the past.\n6. Barcode (loyalty cards): tap Scan and point the camera at the code, or tap From photo to find the code in a picture, or type the number. The type (Code 128, EAN-13, QR and so on) is detected for you; you can change it with the format buttons.\n7. Tap Save. The card syncs to your storage straight away."),
    Section("Automatic fill from a photo",
        "When you add a front or back photo (and crop it), the app reads it on the phone and fills in what it finds: card number (only numbers that pass the standard card check), expiry, name on card, barcode, the card name (Visa, Mastercard, Amex...) and the type. It only fills empty fields and never replaces what you typed. Nothing is sent anywhere, the reading is done offline.\n\nIMPORTANT: always double-check. Reading text from a photo can swap digits or letters, miss a character or pick the wrong date, especially on embossed cards, with glare or a low-quality photo. An orange box lists what was filled. Compare every filled field with your card before you tap Save. The CVV and PIN are never read, type them yourself.\n\nFor best results use a sharp, well-lit photo, crop close to the card, and take the front and back separately. Loyalty cards are detected by their barcode, and the barcode number is filled in."),
    Section("Using a card",
        "Tap a card in the list to open it. The barcode is drawn large with the screen at full brightness so shop scanners can read it. Tap a field to copy it. CVV and PIN stay hidden until you tap them. Tap the front or back photo to view it full screen, pinch to zoom, tap again to close. Edit changes the card; Delete removes it after a confirmation, and it is removed from your other phones on their next sync."),
    Section("Finding cards",
        "Type in the search box to search names, numbers and notes. Tap the filter icon next to it to show one type only, or All. Active cards are listed first and Expired cards below, each group A to Z, two per row. Expired cards are dimmed and show the expiry date."),
    Section("Sync",
        "Cards sync when the app opens and after every save or delete. Tap the sync icon (two circling arrows) to sync now. It works offline and catches up later. The small line under the title shows the last result or error. If the same card is edited on two phones, the latest edit wins."),
    Section("Security and privacy",
        "The app locks with your fingerprint, face or screen lock and locks again after 30 seconds in the background. Screenshots are blocked. You can turn the lock off in Settings.\n\nThe app does not yet encrypt cards itself. They travel over HTTPS and your provider encrypts them on its disks, but whoever can open the storage account can read them, including CVV and PIN. Protect that account with a strong password and two-step verification, and consider leaving CVV and PIN out."),
    Section("Settings (gear icon)",
        "Text size makes everything bigger or smaller (Small, Normal, Large, Huge). Help is here too (Help and setup guide). Shows where cards are saved and the last sync time. Change storage, turn the lock on or off, or Remove cards from this phone. Removing only clears this phone, your cloud copy stays, and connecting again brings the cards back."),
    Section("Troubleshooting",
        "• Nothing syncs: check the internet and tap the sync icon. Read the line under the title.\n• Cards missing on a new phone: connect the same account, folder or bucket, not a new one.\n• Barcode will not scan in a shop: the app already sets full brightness. Clean the screen and hold the phone flat and steady.\n• Cloud folder refused: you picked a folder on the phone. Pick one under a cloud app.\n• Moving from another app: there is no import yet. Add cards by hand, or photograph them and read expiry and barcode from the photo."),
)

@Composable
fun HelpDialog(onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Help", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClose) { Icon(Icons.Default.Close, "Close") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SECTIONS.forEach { sec ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(sec.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(sec.body, fontSize = 14.sp)
                            sec.extra?.invoke()
                        }
                    }
                }
            }
        }
    }
}
