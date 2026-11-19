package com.example.financetracker.ai.llm

import kotlinx.coroutines.flow.Flow

enum class LlmBackend { GEMINI_NANO, GEMMA_LITERT, NONE }

sealed interface LlmAvailability {
    data object Available : LlmAvailability
    /** Supported but model must be downloaded first. */
    data class Downloadable(val approxBytes: Long?) : LlmAvailability
    data class Downloading(val progress: Float?) : LlmAvailability
    data object Unavailable : LlmAvailability
}

interface OnDeviceLlm {
    val backend: LlmBackend
    suspend fun availability(): LlmAvailability
    /** Single-turn text generation. Returns null on any failure. */
    suspend fun generate(prompt: String): String?
}

/** Picks the best available backend and exposes model management for Settings. */
interface LlmManager {
    val activeBackend: Flow<LlmBackend>
    suspend fun current(): OnDeviceLlm
    suspend fun availability(backend: LlmBackend): LlmAvailability
    /** Starts/continues download of the given backend's model; emits progress 0..1. */
    fun download(backend: LlmBackend): Flow<Float>
    /** User opt-in for Gemma download (persisted). */
    val gemmaEnabled: Flow<Boolean>
    suspend fun setGemmaEnabled(enabled: Boolean)
}
