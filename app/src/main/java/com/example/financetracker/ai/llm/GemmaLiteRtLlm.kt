package com.example.financetracker.ai.llm

import android.app.ActivityManager
import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Gemma via LiteRT-LM. The .litertlm model (ungated, Apache-licensed Gemma 4 E2B, from Hugging Face
 * litert-community) is downloaded on user opt-in into filesDir/models/ (never bundled in the APK).
 *
 * One single-thread executor owns the Engine: init, streaming, cancel and close all run on it.
 */
class GemmaLiteRtLlm(
    private val context: Context,
    /** GPU needs the two uses-native-library manifest entries; CPU is the safe default. */
    private val preferGpu: Boolean = false,
) : OnDeviceLlm {
    override val backend: LlmBackend = LlmBackend.GEMMA_LITERT

    private val modelDir get() = File(context.filesDir, "models")
    val modelFile: File get() = File(modelDir, MODEL_FILE_NAME)
    private val partFile: File get() = File(modelDir, "$MODEL_FILE_NAME.part")

    private val progress = MutableStateFlow<Float?>(null)
    private val downloadLock = Mutex()

    private val engineMutex = Mutex()
    private var engine: Engine? = null
    private val executor by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "gemma-litert").apply { isDaemon = true } }
    }
    private val engineDispatcher by lazy { executor.asCoroutineDispatcher() }

    /** True when a fully downloaded (verified + renamed) model file exists. */
    fun isModelPresent(): Boolean = modelFile.let { it.isFile && it.length() >= MIN_VALID_BYTES }

    private fun deviceHasEnoughRam(): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        info.totalMem >= MIN_TOTAL_RAM_BYTES
    } catch (e: Exception) {
        false
    }

    override suspend fun availability(): LlmAvailability {
        if (!deviceHasEnoughRam()) return LlmAvailability.Unavailable
        progress.value?.let { return LlmAvailability.Downloading(it) }
        return if (isModelPresent()) LlmAvailability.Available
        else LlmAvailability.Downloadable(EXPECTED_BYTES)
    }

    override suspend fun generate(prompt: String): String? {
        if (!isModelPresent()) return null
        return try {
            withContext(engineDispatcher) {
                engineMutex.withLock {
                    val e = ensureEngine() ?: return@withLock null
                    val conversation = e.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.2),
                        ),
                    )
                    try {
                        val sb = StringBuilder()
                        try {
                            conversation.sendMessageAsync(prompt).collect { sb.append(it.toString()) }
                        } catch (ce: CancellationException) {
                            // Stop the native reply before the conversation is closed.
                            withContext(NonCancellable) { runCatching { conversation.cancelProcess() } }
                            throw ce
                        }
                        sb.toString().takeIf { it.isNotBlank() }
                    } finally {
                        runCatching { conversation.close() }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    /** Must be called on the engine dispatcher under [engineMutex]. */
    private fun ensureEngine(): Engine? {
        engine?.let { return it }
        val cache = context.cacheDir.path
        val gpu: () -> Backend = { Backend.GPU() }
        val cpu: () -> Backend = { Backend.CPU() }
        val backends: List<() -> Backend> = if (preferGpu) listOf(gpu, cpu) else listOf(cpu)
        for (b in backends) {
            try {
                val created = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = b(), cacheDir = cache))
                created.initialize() // blocks for seconds; we're on the background executor
                engine = created // store only after initialize succeeded
                return created
            } catch (e: Throwable) {
                // never close an engine whose initialize threw
            }
        }
        return null
    }

    /** Closes the engine to free memory (it is recreated lazily on next generate). */
    suspend fun release() {
        try {
            withContext(engineDispatcher) {
                engineMutex.withLock {
                    val e = engine
                    engine = null
                    if (e != null) runCatching { e.close() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    /** Removes the model file (and any partial download). */
    suspend fun deleteModel() {
        release()
        withContext(Dispatchers.IO) {
            runCatching { modelFile.delete() }
            runCatching { partFile.delete() }
        }
    }

    /**
     * Streams the model download, emitting progress 0..1. Resume-safe: data goes to a .part file
     * (resumed with an HTTP Range request) and is renamed only after the size matches the server's.
     * Throws IOException on failure (the .part file is kept so the next call resumes).
     */
    fun download(): Flow<Float> = flow {
        if (isModelPresent()) {
            emit(1f)
            return@flow
        }
        if (!downloadLock.tryLock()) {
            // Another collector is already downloading: mirror its progress until it finishes.
            while (true) {
                val p = progress.value ?: break
                emit(p)
                kotlinx.coroutines.delay(500)
            }
            return@flow
        }
        try {
            modelDir.mkdirs()
            progress.value = 0f
            emit(0f)
            downloadToPart { p ->
                progress.value = p
                emit(p)
            }
            val dest = modelFile
            if (dest.exists()) dest.delete()
            if (!partFile.renameTo(dest)) throw IOException("Could not finalize model file")
            progress.value = null
            emit(1f)
        } finally {
            progress.value = null
            downloadLock.unlock()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadToPart(onProgress: suspend (Float) -> Unit) {
        var attempt = 0
        while (true) {
            attempt++
            val part = partFile
            var existing = if (part.isFile) part.length() else 0L
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                if (existing > 0) conn.setRequestProperty("Range", "bytes=$existing-")
                conn.connect()
                val total: Long
                val append: Boolean
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        append = true
                        total = conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                            ?: (existing + conn.contentLengthLong)
                    }
                    HttpURLConnection.HTTP_OK -> {
                        append = false
                        existing = 0L
                        total = conn.contentLengthLong
                    }
                    416 -> {
                        // Stale/oversized part file: start over once.
                        part.delete()
                        if (attempt >= 2) throw IOException("HTTP 416")
                        continue
                    }
                    else -> throw IOException("Model download failed: HTTP $code")
                }
                if (total < MIN_VALID_BYTES) throw IOException("Unexpected model size: $total")

                var done = existing
                var lastEmitted = -1f
                val buf = ByteArray(64 * 1024)
                conn.inputStream.use { input ->
                    FileOutputStream(part, append).use { out ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val p = (done.toFloat() / total).coerceIn(0f, 1f)
                            if (p - lastEmitted >= 0.005f) {
                                lastEmitted = p
                                onProgress(p)
                            }
                        }
                        out.flush()
                    }
                }
                if (part.length() != total) {
                    throw IOException("Incomplete download: ${part.length()} of $total bytes")
                }
                return
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        const val MODEL_REPO = "litert-community/gemma-4-E2B-it-litert-lm"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL = "https://huggingface.co/$MODEL_REPO/resolve/main/$MODEL_FILE_NAME"

        /** Size reported by the server (Content-Length) when this was written: ~2.6 GB. */
        const val EXPECTED_BYTES = 2_588_147_712L
        private const val MIN_VALID_BYTES = 1_000_000_000L
        private const val MIN_TOTAL_RAM_BYTES = 5_000_000_000L

        private const val SYSTEM_INSTRUCTION =
            "You clean up personal finance entries. Reply with strict JSON only, no prose, no markdown."
    }
}
