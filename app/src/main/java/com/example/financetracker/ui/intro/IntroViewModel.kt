package com.example.financetracker.ui.intro

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.domain.session.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IntroUiState(
    val busy: Boolean = false,
    /** One-shot error text; the screen shows it in a snackbar. */
    val error: String? = null,
    /** False when GOOGLE_WEB_CLIENT_ID is blank in this build. */
    val googleConfigured: Boolean = true,
)

class IntroViewModel(
    private val session: SessionRepository,
    googleConfigured: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(IntroUiState(googleConfigured = googleConfigured))
    val uiState: StateFlow<IntroUiState> = _state.asStateFlow()

    fun signInWithGoogle(activity: Activity) {
        val current = _state.value
        if (current.busy || !current.googleConfigured) return
        _state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            val failure = try {
                session.signInWithGoogle(activity).exceptionOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            _state.value = _state.value.copy(
                busy = false,
                error = failure?.let { "Sign-in failed: ${it.message?.takeIf { m -> m.isNotBlank() } ?: "try again"}" },
            )
        }
    }

    fun continueAsGuest() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val error = try {
                session.continueAsGuest()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Could not start offline mode: ${e.message ?: "unknown error"}"
            }
            _state.value = _state.value.copy(busy = false, error = error)
        }
    }

    fun errorShown() {
        _state.value = _state.value.copy(error = null)
    }
}
