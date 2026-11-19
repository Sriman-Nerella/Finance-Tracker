package com.example.financetracker.ai

import com.example.financetracker.ai.llm.LlmAvailability
import com.example.financetracker.ai.llm.LlmManager
import com.example.financetracker.domain.model.Category
import com.example.financetracker.domain.repo.CategoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/** Rules -> optional on-device LLM refinement -> validation. Falls back to rules on any problem. */
class DefaultChatEntryPipeline(
    private val parser: EntryParser,
    private val llm: LlmManager,
    private val categories: CategoryRepository,
    private val validator: LlmRefinementValidator = LlmRefinementValidator(),
    private val timeoutMs: Long = 4_000L,
    /** User switch from the chat + sheet; when false the rule parser's result is used as-is. */
    private val localAiEnabled: suspend () -> Boolean = { true },
) : ChatEntryPipeline {

    override suspend fun parse(text: String, today: LocalDate): ParseResult {
        val rule = try {
            parser.parse(text, today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ParseResult(emptyList(), listOfNotNull(text.trim().takeIf { it.isNotEmpty() }), false)
        }
        if (rule.entries.isEmpty()) return rule

        val refined = try {
            withTimeout(timeoutMs) { refine(text, rule.entries) }
        } catch (e: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return if (refined != null) rule.copy(entries = refined, refinedByLlm = true) else rule.copy(refinedByLlm = false)
    }

    private suspend fun refine(text: String, entries: List<ParsedEntry>): List<ParsedEntry>? {
        if (!localAiEnabled()) return null
        val model = llm.current()
        if (model.availability() != LlmAvailability.Available) return null
        val allowed = categories.getAll()
        if (allowed.isEmpty()) return null
        val raw = model.generate(buildPrompt(text, entries, allowed)) ?: return null
        return validator.validate(entries, allowed, raw)
    }

    internal fun buildPrompt(text: String, entries: List<ParsedEntry>, allowed: List<Category>): String {
        val entriesJson = JsonArray(
            entries.mapIndexed { i, e ->
                buildJsonObject {
                    put("index", i)
                    put("sourceText", e.sourceText)
                    put("title", e.title)
                    put("note", e.note?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("type", e.type.name)
                    put("categoryId", e.categoryId)
                }
            },
        )
        val cats = allowed.joinToString("\n") { "${it.id} = ${it.name} (${it.type.name})" }
        return buildString {
            appendLine("Clean up the finance entries parsed from the user text.")
            appendLine("Return ONLY a JSON array, no prose. One object per entry, same order:")
            appendLine("""{"index":0,"title":"","note":"","categoryId":"","type":"EXPENSE"}""")
            appendLine("Rules:")
            appendLine("- Keep the same number of items and the same index values.")
            appendLine("- title: short name of the item, max 40 chars.")
            appendLine("- note: leftover detail words from sourceText (not the title, amount or date), max 120 chars, or \"\".")
            appendLine("- categoryId: one id from the allowed list that matches type.")
            appendLine("- Never output amounts or dates.")
            appendLine("Example: sourceText \"movie o.m 200 rs\" -> {\"index\":0,\"title\":\"Movie\",\"note\":\"o.m\",\"categoryId\":\"entertainment\",\"type\":\"EXPENSE\"}")
            appendLine("Allowed categories:")
            appendLine(cats)
            appendLine("<text>")
            appendLine(text.trim())
            appendLine("</text>")
            appendLine("<entries>")
            appendLine(Json.encodeToString(JsonArray.serializer(), entriesJson))
            append("</entries>")
        }
    }
}
