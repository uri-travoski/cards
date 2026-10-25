package au.sam.cards

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    lateinit var store: Store
    var unlocked by mutableStateOf(false)
    var syncing by mutableStateOf(false)
    var status by mutableStateOf("")
    var changingStorage by mutableStateOf(false)

    private var authWait: CompletableDeferred<Intent?>? = null
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        authWait?.complete(it.data)
    }
    private var stoppedAt = 0L
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        store.pruneImages()
        if (store.lockEnabled) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { App(this) }
        requireUnlock()
        sync(interactive = false)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > 30_000) {
            unlocked = false
            requireUnlock()
            sync(interactive = false)
        }
    }

    fun setLock(on: Boolean) {
        store.lockEnabled = on
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun requireUnlock() {
        if (!store.lockEnabled || BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked = true
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked = true
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock My Cards").setAllowedAuthenticators(authenticators).build()
        )
    }

    /** A Drive access token for the user's Google account, asking for consent only when [interactive]. */
    private suspend fun token(interactive: Boolean): String? {
        if (BuildConfig.MOCK_DRIVE.isNotEmpty()) {
            store.email = "mock@test"
            return "mock"
        }
        val client = Identity.getAuthorizationClient(this)
        val req = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(
                Scope("https://www.googleapis.com/auth/drive.file"), // the visible "My Cards App" folder
                Scope("https://www.googleapis.com/auth/drive.appdata"), // only to move cards from older versions' hidden folder
            ))
            .build()
        var res = client.authorize(req).await()
        if (res.hasResolution()) {
            if (!interactive) return null
            val wait = CompletableDeferred<Intent?>().also { authWait = it }
            authLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(res.pendingIntent!!.intentSender).build())
            res = client.getAuthorizationResultFromIntent(wait.await() ?: return null)
        }
        res.toGoogleSignInAccount()?.email?.let { store.email = it }
        return res.accessToken
    }

    /** Make [kind] the storage. Moving to a different place re-uploads everything there. */
    private fun adopt(kind: String, folder: String = "", s3: S3Config? = null) {
        val changed = kind != store.storage || (kind == "folder" && folder != store.folderUri) || (kind == "s3" && s3 != store.s3)
        if (changed && store.storage.isNotEmpty()) store.resetSync()
        store.storage = kind
        if (kind == "folder") store.folderUri = folder
        if (kind == "s3") store.s3 = s3!!
        changingStorage = false
        sync(interactive = true)
    }

    fun signIn() {
        lifecycleScope.launch {
            try {
                if (token(interactive = true) == null) {
                    status = "Sign-in cancelled"
                    return@launch
                }
                adopt("drive")
            } catch (e: Exception) {
                status = "Sign-in failed: ${e.message}"
            }
        }
    }

    private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) connectFolder(uri)
    }

    fun pickFolder() = folderLauncher.launch(null)

    private fun connectFolder(uri: Uri) {
        if (!BuildConfig.ALLOW_LOCAL && uri.authority in setOf("com.android.externalstorage.documents", "com.android.providers.downloads.documents")) {
            status = "That folder is on this phone. Pick a folder inside a cloud app (Dropbox, OneDrive, Nextcloud...)."
            return
        }
        lifecycleScope.launch {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                withContext(Dispatchers.IO) { SafBackend(this@MainActivity, uri).list() }
                adopt("folder", folder = uri.toString())
            } catch (e: Exception) {
                status = "Can't use that folder: ${e.message}"
            }
        }
    }

    fun connectS3(cfg: S3Config) {
        lifecycleScope.launch {
            status = "Testing connection…"
            try {
                withContext(Dispatchers.IO) { S3Backend(cfg).list() }
                adopt("s3", s3 = cfg)
            } catch (e: Exception) {
                status = "Can't connect: ${e.message}"
            }
        }
    }

    /** Forget the storage and clear this phone. Cloud copies are left alone. */
    fun disconnect() {
        store.wipe()
        status = ""
    }

    private suspend fun backend(interactive: Boolean): Backend? = when (store.storage) {
        "drive" -> token(interactive)?.let { DriveBackend(it, savedFolderId = store.driveFolderId, onFolder = { id -> store.driveFolderId = id }) }
        "folder" -> SafBackend(this, Uri.parse(store.folderUri))
        "s3" -> S3Backend(store.s3)
        else -> null
    }

    fun sync(interactive: Boolean) {
        if (syncing || store.storage.isEmpty()) return
        syncing = true
        lifecycleScope.launch {
            status = try {
                val b = backend(interactive) ?: throw IllegalStateException("Google needs you to sign in again (Settings)")
                val r = withContext(Dispatchers.IO) { Sync.run(store, b) }
                "Synced: ${r.up} up, ${r.down} down" + if (r.removed > 0) ", ${r.removed} removed" else ""
            } catch (e: Exception) {
                "Sync failed: ${e.message}"
            }
            syncing = false
        }
    }
}
