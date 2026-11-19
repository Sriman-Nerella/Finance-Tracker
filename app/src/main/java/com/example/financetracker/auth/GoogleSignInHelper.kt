package com.example.financetracker.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.financetracker.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class GoogleIdentity(
    val idToken: String,
    /** Raw nonce: goes to Supabase. Google received its SHA-256 hash. */
    val rawNonce: String,
    val email: String?,
    val displayName: String?,
)

/** User-presentable failure (message is safe to show). */
class SignInException(message: String, val cancelled: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)

class GoogleSignInHelper(private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID) {

    val isConfigured: Boolean get() = webClientId.isNotBlank()

    suspend fun getIdentity(activity: Activity): Result<GoogleIdentity> {
        if (!isConfigured) {
            return Result.failure(SignInException("Google sign-in isn't configured yet (missing web client ID)."))
        }
        val rawNonce = newNonce()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(sha256Hex(rawNonce))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val g = GoogleIdTokenCredential.createFrom(cred.data)
                Result.success(GoogleIdentity(g.idToken, rawNonce, g.id, g.displayName))
            } else {
                Result.failure(SignInException("Unexpected credential type returned."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(SignInException("Sign-in cancelled.", cancelled = true, cause = e))
        } catch (e: NoCredentialException) {
            Result.failure(
                SignInException("No Google account found on this device. Add one in Settings and try again.", cause = e)
            )
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(SignInException("Couldn't read the Google sign-in response.", cause = e))
        } catch (e: GetCredentialException) {
            val msg = e.message.orEmpty()
            val friendly = if (msg.contains("developer", true) || msg.contains("client", true) || msg.contains("10:"))
                "Google sign-in is misconfigured (check the OAuth client ID and the app's signing SHA-1)."
            else "Google sign-in failed. ${msg.ifBlank { "Please try again." }}"
            Result.failure(SignInException(friendly, cause = e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(SignInException("Google sign-in failed: ${e.message ?: "unknown error"}", cause = e))
        }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
