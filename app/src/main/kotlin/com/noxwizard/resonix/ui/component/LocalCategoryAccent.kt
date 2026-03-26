package com.noxwizard.resonix.ui.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Provides a callback that allows any composable deep in the tree
 * to temporarily override the app accent color (e.g., on Mood/Genre card tap).
 * Pass null to clear the override.
 */
val LocalCategoryAccentCallback = compositionLocalOf<((Color?) -> Unit)> { {} }
