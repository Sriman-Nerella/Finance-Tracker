package com.example.financetracker.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.ai.ChatEntryPipeline
import com.example.financetracker.ai.ParseResult
import com.example.financetracker.ai.ParsedEntry
import com.example.financetracker.ai.llm.LlmAvailability
import com.example.financetracker.ai.llm.LlmBackend
import com.example.financetracker.ai.llm.LlmManager
import com.example.financetracker.data.prefs.UiPrefs
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.model.Money
import com.example.financetracker.domain.model.NewTransaction
import com.example.financetracker.domain.model.TxSource
import com.example.financetracker.domain.repo.CategoryRepository
import com.example.financetracker.domain.repo.TransactionRepository
import com.example.financetracker.ui.common.formatInr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A parsed entry awaiting confirmation. [key] identifies it inside its preview card. */
data class DraftEntry(val key: Long, val entry: ParsedEntry)

enum class PreviewStatus { PENDING, SAVING, SAVED, DISCARDED }

sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage

    data class Assistant(override val id: Long, val text: String) : ChatMessage

    /** Parsed records shown for confirmation. Nothing is stored until [ChatViewModel.save]. */
    data class Preview(
        override val id: Long,
        val entries: List<DraftEntry>,
        val refinedByLlm: Boolean,
        val status: PreviewStatus = PreviewStatus.PENDING,
    ) : ChatMessage
}

/** Whether this device can run a local model for chat. */
sealed interface LocalModel {
    data object Checking : LocalModel
    data class Available(val name: String) : LocalModel
    data object Unavailable : LocalModel
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isParsing: Boolean = false,
    val categories: List<Category> = emptyList(),
    val localModel: LocalModel = LocalModel.Checking,
    /** The user's switch in the + sheet. */
    val localAiEnabled: Boolean = true,
) {
    /** True when messages are actually read by the on-device model. */
    val usingLocalAi: Boolean get() = localAiEnabled && localModel is LocalModel.Available
}

class ChatViewModel(
    private val pipeline: ChatEntryPipeline,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val llm: LlmManager,
    private val prefs: UiPrefs,
) : ViewModel() {

    private var nextId = 1L
    private fun newId(): Long = nextId++

    private val _state = MutableStateFlow(
        ChatUiState(
            messages = listOf(
                ChatMessage.Assistant(
                    id = 0L,
                    text = "Tell me what you spent, like “lunch 200, auto 60”. " +
                        "To log another day, put the date on the first line:\nnov 19\ntea 15rs, lunch 200rs",
                ),
            ),
        ),
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categories.observeAll().collect { list ->
                _state.update { it.copy(categories = list) }
            }
        }
        viewModelScope.launch {
            prefs.localAiEnabled.collect { on -> _state.update { it.copy(localAiEnabled = on) } }
        }
        viewModelScope.launch {
            val model = try {
                when {
                    llm.availability(LlmBackend.GEMINI_NANO) == LlmAvailability.Available ->
                        LocalModel.Available("Gemini Nano")
                    llm.gemmaEnabled.first() && llm.availability(LlmBackend.GEMMA_LITERT) == LlmAvailability.Available ->
                        LocalModel.Available("Gemma")
                    else -> LocalModel.Unavailable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LocalModel.Unavailable
            }
            _state.update { it.copy(localModel = model) }
        }
    }

    fun setLocalAiEnabled(enabled: Boolean) {
        _state.update { it.copy(localAiEnabled = enabled) }
        viewModelScope.launch { prefs.setLocalAiEnabled(enabled) }
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun send() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() || current.isParsing) return
        val userId = newId()
        _state.update {
            it.copy(
                input = "",
                isParsing = true,
                messages = it.messages + ChatMessage.User(userId, text),
            )
        }
        viewModelScope.launch {
            val result = try {
                pipeline.parse(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isParsing = false) }
                appendAssistant("Something went wrong while reading that. Please try again.")
                return@launch
            }
            _state.update { it.copy(isParsing = false) }
            reply(result)
        }
    }

    /** Replaces a draft entry's fields (only while the preview is pending). */
    fun updateDraft(messageId: Long, key: Long, entry: ParsedEntry) {
        mutatePreview(messageId) { preview ->
            if (preview.status != PreviewStatus.PENDING) {
                preview
            } else {
                preview.copy(entries = preview.entries.map { if (it.key == key) it.copy(entry = entry) else it })
            }
        }
    }

    /** Removes one draft entry from the preview (only while pending). */
    fun removeDraft(messageId: Long, key: Long) {
        mutatePreview(messageId) { preview ->
            if (preview.status != PreviewStatus.PENDING) {
                preview
            } else {
                preview.copy(entries = preview.entries.filterNot { it.key == key })
            }
        }
    }

    /** Stores the preview's entries as CHAT records. */
    fun save(messageId: Long) {
        val preview = previewById(messageId) ?: return
        if (preview.status != PreviewStatus.PENDING || preview.entries.isEmpty()) return
        val drafts = preview.entries
        mutatePreview(messageId) { it.copy(status = PreviewStatus.SAVING) }
        viewModelScope.launch {
            try {
                val created = transactions.add(drafts.map { it.entry.toNewTransaction() })
                mutatePreview(messageId) { it.copy(status = PreviewStatus.SAVED) }
                val count = created.size
                appendAssistant("Saved $count ${if (count == 1) "record" else "records"}")
            } catch (e: CancellationException) {
                mutatePreview(messageId) { it.copy(status = PreviewStatus.PENDING) }
                throw e
            } catch (e: Exception) {
                mutatePreview(messageId) { it.copy(status = PreviewStatus.PENDING) }
                appendAssistant("Couldn't save: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun discard(messageId: Long) {
        mutatePreview(messageId) {
            if (it.status == PreviewStatus.PENDING) it.copy(status = PreviewStatus.DISCARDED) else it
        }
    }

    private fun reply(result: ParseResult) {
        if (result.entries.isNotEmpty()) {
            val drafts = result.entries.map { DraftEntry(newId(), it) }
            _state.update {
                it.copy(
                    messages = it.messages + ChatMessage.Preview(
                        id = newId(),
                        entries = drafts,
                        refinedByLlm = result.refinedByLlm,
                    ),
                )
            }
        }
        if (result.unparsed.isNotEmpty()) {
            appendAssistant(
                "Couldn't find an amount in: ${result.unparsed.joinToString(", ")}. " +
                    "Resend with an amount, e.g. \"coffee 120\".",
            )
        }
        if (result.entries.isEmpty() && result.unparsed.isEmpty()) {
            appendAssistant("I didn't find any records. Try something like:\nnov 19\ntea 15rs, lunch 200rs")
        }
    }

    private fun appendAssistant(text: String) {
        val id = newId()
        _state.update { it.copy(messages = it.messages + ChatMessage.Assistant(id, text)) }
    }

    private fun previewById(messageId: Long): ChatMessage.Preview? =
        _state.value.messages.filterIsInstance<ChatMessage.Preview>().firstOrNull { it.id == messageId }

    private fun mutatePreview(messageId: Long, transform: (ChatMessage.Preview) -> ChatMessage.Preview) {
        _state.update { s ->
            s.copy(
                messages = s.messages.map { m ->
                    if (m is ChatMessage.Preview && m.id == messageId) transform(m) else m
                },
            )
        }
    }
}

private fun ParsedEntry.toNewTransaction(): NewTransaction = NewTransaction(
    amount = amount,
    type = type,
    title = title,
    note = note,
    categoryId = categoryId,
    occurredOn = occurredOn,
    source = TxSource.CHAT,
)

private val SUMMARY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** e.g. "3 records for Nov 19 — ₹415". */
internal fun previewSummary(entries: List<ParsedEntry>): String {
    if (entries.isEmpty()) return "No records left"
    val total = entries.fold(Money.ZERO) { acc, e -> acc + e.amount }
    val dates: List<LocalDate> = entries.map { it.occurredOn }.distinct()
    val count = if (entries.size == 1) "1 record" else "${entries.size} records"
    val whenText = if (dates.size == 1) {
        "for ${dates.first().format(SUMMARY_DATE_FORMAT)}"
    } else {
        "across ${dates.size} dates"
    }
    return "$count $whenText — ${total.formatInr()}"
}
