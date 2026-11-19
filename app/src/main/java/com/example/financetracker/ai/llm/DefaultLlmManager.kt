package com.example.financetracker.ai.llm

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

/**
 * Picks the best backend: Gemini Nano if available, else Gemma (LiteRT-LM) when the user opted in
 * and the model file is present, else NONE. The Gemma opt-in lives in DataStore "llm_prefs".
 */
class DefaultLlmManager(
    context: Context,
    private val scope: CoroutineScope,
) : LlmManager {
    private val appContext = context.applicationContext
    private val nano = GeminiNanoLlm()
    private val gemma = GemmaLiteRtLlm(appContext)
    private val noop = NoopLlm()

    private val _active = MutableStateFlow(LlmBackend.NONE)
    override val activeBackend: Flow<LlmBackend> = _active

    init {
        scope.launch { runCatching { current() } }
    }

    override val gemmaEnabled: Flow<Boolean> = appContext.llmPrefsStore.data
        .map { it[GEMMA_ENABLED_KEY] ?: false }
        .catch { emit(false) }

    private suspend fun isGemmaOptedIn(): Boolean = try {
        appContext.llmPrefsStore.data.first()[GEMMA_ENABLED_KEY] ?: false
    } catch (e: Exception) {
        false
    }

    override suspend fun current(): OnDeviceLlm {
        val chosen: OnDeviceLlm = try {
            when {
                nano.availability() == LlmAvailability.Available -> nano
                isGemmaOptedIn() && gemma.isModelPresent() -> gemma
                else -> noop
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            noop
        }
        _active.value = chosen.backend
        return chosen
    }

    override suspend fun availability(backend: LlmBackend): LlmAvailability = try {
        when (backend) {
            LlmBackend.GEMINI_NANO -> nano.availability()
            LlmBackend.GEMMA_LITERT -> gemma.availability()
            LlmBackend.NONE -> LlmAvailability.Unavailable
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        LlmAvailability.Unavailable
    }

    /** Emits progress 0..1; the flow throws on failure (collector should catch and show an error). */
    override fun download(backend: LlmBackend): Flow<Float> = when (backend) {
        LlmBackend.GEMINI_NANO -> nano.download().onCompletion { refresh() }
        LlmBackend.GEMMA_LITERT -> gemma.download().onCompletion { refresh() }
        LlmBackend.NONE -> emptyFlow()
    }

    override suspend fun setGemmaEnabled(enabled: Boolean) {
        appContext.llmPrefsStore.edit { it[GEMMA_ENABLED_KEY] = enabled }
        if (!enabled) gemma.release()
        refresh()
    }

    /** Whether the user opted in to Gemma. */
    suspend fun isGemmaEnabled(): Boolean = isGemmaOptedIn()

    /** Removes the downloaded Gemma model and frees the engine. */
    suspend fun deleteGemmaModel() {
        gemma.deleteModel()
        refresh()
    }

    /** Frees engine memory (e.g. from onTrimMemory); engines are recreated lazily. */
    fun trimMemory() {
        scope.launch { runCatching { gemma.release() } }
    }

    private fun refresh() {
        scope.launch { runCatching { current() } }
    }
}
