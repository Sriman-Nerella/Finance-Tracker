package com.example.financetracker.ai.llm

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemini Nano through the ML Kit GenAI Prompt API (AICore).
 * Never throws: unsupported devices map to Unavailable / null.
 */
class GeminiNanoLlm : OnDeviceLlm {
    override val backend: LlmBackend = LlmBackend.GEMINI_NANO

    private val lock = Mutex()
    private var model: GenerativeModel? = null

    private suspend fun client(): GenerativeModel? = lock.withLock {
        model ?: try {
            Generation.getClient().also { model = it }
        } catch (e: Exception) {
            null
        } catch (e: LinkageError) {
            null
        }
    }

    override suspend fun availability(): LlmAvailability {
        val m = client() ?: return LlmAvailability.Unavailable
        return try {
            when (m.checkStatus()) {
                FeatureStatus.AVAILABLE -> LlmAvailability.Available
                FeatureStatus.DOWNLOADABLE -> LlmAvailability.Downloadable(null)
                FeatureStatus.DOWNLOADING -> LlmAvailability.Downloading(null)
                else -> LlmAvailability.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LlmAvailability.Unavailable
        }
    }

    override suspend fun generate(prompt: String): String? {
        val m = client() ?: return null
        return try {
            val response = m.generateContent(
                generateContentRequest(TextPart(prompt)) {
                    temperature = 0.1f
                    topK = 10
                    candidateCount = 1
                    maxOutputTokens = 512
                },
            )
            response.candidates.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Downloads the Gemini Nano feature if it is downloadable; emits progress 0..1.
     * Completes immediately when already available/unsupported; throws on download failure.
     */
    fun download(): Flow<Float> = flow {
        val m = client()
        if (m == null) return@flow
        val status = try {
            m.checkStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            FeatureStatus.UNAVAILABLE
        }
        if (status == FeatureStatus.AVAILABLE) {
            emit(1f)
            return@flow
        }
        if (status != FeatureStatus.DOWNLOADABLE && status != FeatureStatus.DOWNLOADING) return@flow

        var total = 0L
        m.download().collect { s ->
            when (s) {
                is DownloadStatus.DownloadStarted -> {
                    total = s.bytesToDownload
                    emit(0f)
                }
                is DownloadStatus.DownloadProgress -> {
                    if (total > 0) emit((s.totalBytesDownloaded.toFloat() / total).coerceIn(0f, 1f))
                }
                DownloadStatus.DownloadCompleted -> emit(1f)
                is DownloadStatus.DownloadFailed -> throw s.e
            }
        }
    }

    /** Releases the ML Kit client (call when trimming memory). */
    suspend fun close() = lock.withLock {
        try {
            model?.close()
        } catch (_: Throwable) {
        }
        model = null
    }
}
