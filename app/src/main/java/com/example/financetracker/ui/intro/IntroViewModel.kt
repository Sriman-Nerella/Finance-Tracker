package com.example.financetracker.ui.intro

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
    /** Sign-in error shown under the form; cleared on the next attempt. */
    val signInError: String? = null,
    /** One-shot error text for guest mode; the screen shows it in a snackbar. */
    val error: String? = null,
    // Google sign-in parked: was `googleConfigured`, false when GOOGLE_WEB_CLIENT_ID is blank.
)

class IntroViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IntroUiState())
    val uiState: StateFlow<IntroUiState> = _state.asStateFlow()

    fun signIn(username: String, password: String) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, signInError = null)
        viewModelScope.launch {
            val failure = try {
                session.signInWithPassword(username, password).exceptionOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            _state.value = _state.value.copy(
                busy = false,
                signInError = failure?.let { it.message?.takeIf { m -> m.isNotBlank() } ?: "Sign-in failed, try again" },
            )
        }
    }

    // fun signInWithGoogle(activity: Activity) { ... }  // parked with Google sign-in

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
