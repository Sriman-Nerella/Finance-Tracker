package com.example.financetracker.ai.llm

/** Backend NONE: the app runs on rules only. */
class NoopLlm : OnDeviceLlm {
    override val backend: LlmBackend = LlmBackend.NONE
    override suspend fun availability(): LlmAvailability = LlmAvailability.Unavailable
    override suspend fun generate(prompt: String): String? = null
}
