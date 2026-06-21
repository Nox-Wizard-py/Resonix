package com.noxwizard.resonix.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ResonixLoadingIndicatorSize(val dp: Dp) {
    Small(24.dp),
    Medium(36.dp),
    Large(48.dp)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResonixLoadingIndicator(
    modifier: Modifier = Modifier,
    size: ResonixLoadingIndicatorSize = ResonixLoadingIndicatorSize.Medium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp // Ignored, legacy compatibility
) {
    LoadingIndicator(
        modifier = modifier.size(size.dp),
        color = color
    )
}
