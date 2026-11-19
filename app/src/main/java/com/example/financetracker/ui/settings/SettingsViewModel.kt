package com.example.financetracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.ai.llm.LlmAvailability
import com.example.financetracker.ai.llm.LlmBackend
import com.example.financetracker.ai.llm.LlmManager
import com.example.financetracker.data.prefs.ThemeMode
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.session.SessionRepository
import com.example.financetracker.domain.session.SessionState
import com.example.financetracker.domain.session.SyncController
import com.example.financetracker.domain.session.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val session: SessionState = SessionState.Undecided,
    val sync: SyncStatus = SyncStatus.Disabled,
    val activeBackend: LlmBackend = LlmBackend.NONE,
    /** Null until the first availability check completes. */
    val geminiAvailability: LlmAvailability? = null,
    val gemmaAvailability: LlmAvailability? = null,
    /** Backend whose model is currently downloading, if any. */
    val downloading: LlmBackend? = null,
    /** Null while the download has not reported progress yet (show indeterminate). */
    val downloadProgress: Float? = null,
    val gemmaEnabled: Boolean = false,
    val allowance: Money? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val localAiEnabled: Boolean = true,
    /** One-line error/info message for the last action. */
    val message: String? = null,
)

class SettingsViewModel(
    private val session: SessionRepository,
    private val sync: SyncController,
    private val llm: LlmManager,
    private val prefs: UiPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.session.collect { s -> _state.update { it.copy(session = s) } }
        }
        viewModelScope.launch {
            sync.status.collect { s -> _state.update { it.copy(sync = s) } }
        }
        viewModelScope.launch {
            llm.activeBackend.collect { b -> _state.update { it.copy(activeBackend = b) } }
        }
        viewModelScope.launch {
            llm.gemmaEnabled.collect { e -> _state.update { it.copy(gemmaEnabled = e) } }
        }
        viewModelScope.launch {
            prefs.allowance.collect { a -> _state.update { it.copy(allowance = a) } }
        }
        viewModelScope.launch {
            prefs.themeMode.collect { m -> _state.update { it.copy(themeMode = m) } }
        }
        viewModelScope.launch {
            prefs.localAiEnabled.collect { e -> _state.update { it.copy(localAiEnabled = e) } }
        }
        refreshAvailability()
    }

    fun setAllowance(value: Money?) {
        viewModelScope.launch { prefs.setAllowance(value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setLocalAiEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setLocalAiEnabled(enabled) }
    }

    fun refreshAvailability() {
        viewModelScope.launch {
            val gemini = safely { llm.availability(LlmBackend.GEMINI_NANO) }
            val gemma = safely { llm.availability(LlmBackend.GEMMA_LITERT) }
            _state.update {
                it.copy(
                    geminiAvailability = gemini ?: LlmAvailability.Unavailable,
                    gemmaAvailability = gemma ?: LlmAvailability.Unavailable,
                )
            }
        }
    }

    fun syncNow() {
        sync.syncNow()
    }

    fun download(backend: LlmBackend) {
        if (_state.value.downloading != null) return
        _state.update { it.copy(downloading = backend, downloadProgress = null, message = null) }
        viewModelScope.launch {
            try {
                llm.download(backend).collect { p ->
                    _state.update { it.copy(downloadProgress = p.coerceIn(0f, 1f)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Download failed: ${e.message ?: "unknown error"}") }
            } finally {
                _state.update { it.copy(downloading = null, downloadProgress = null) }
            }
            refreshAvailability()
        }
    }

    fun setGemmaEnabled(enabled: Boolean) {
        _state.update { it.copy(gemmaEnabled = enabled) }
        viewModelScope.launch {
            try {
                llm.setGemmaEnabled(enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't change Gemma setting: ${e.message ?: "unknown error"}") }
            }
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
