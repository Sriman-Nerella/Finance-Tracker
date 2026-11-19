package com.example.financetracker.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Material window width classes: compact < 600dp (phones), medium 600–839dp, expanded ≥ 840dp (tablets). */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun currentWidthClass(): WidthClass {
    val widthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }.value
    return when {
        widthDp < 600f -> WidthClass.COMPACT
        widthDp < 840f -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }
}
