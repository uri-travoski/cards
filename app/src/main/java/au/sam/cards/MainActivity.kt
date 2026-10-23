package au.sam.cards

import android.content.Intent
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
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Cards").setAllowedAuthenticators(authenticators).build()
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
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))
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

    fun signIn() {
        lifecycleScope.launch {
            try {
                if (token(interactive = true) == null) {
                    status = "Sign-in cancelled"
                    return@launch
                }
                store.signedIn = true
                store.offline = false
                sync(interactive = true)
            } catch (e: Exception) {
                status = "Sign-in failed: ${e.message}"
            }
        }
    }

    fun signOut() {
        store.signedIn = false
        store.email = ""
        store.wipe()
        status = ""
    }

    fun sync(interactive: Boolean) {
        if (syncing || !store.signedIn) return
        syncing = true
        lifecycleScope.launch {
            status = try {
                val t = token(interactive) ?: throw IllegalStateException("Google needs you to sign in again (Settings)")
                val r = withContext(Dispatchers.IO) { Sync.run(store, Drive(t)) }
                "Synced: ${r.up} up, ${r.down} down" + if (r.removed > 0) ", ${r.removed} removed" else ""
            } catch (e: Exception) {
                "Sync failed: ${e.message}"
            }
            syncing = false
        }
    }
}
