package com.noxwizard.resonix.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noxwizard.resonix.R

/**
 * A Material 3 Expressive style settings group component
 * @param title The title of the settings group
 * @param items List of settings items to display
 */
@Composable
fun Material3SettingsGroup(
    title: String? = null,
    items: List<Material3SettingsItem>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section title
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp)
            )
        }
        
        // Settings card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    Material3SettingsItemRow(
                        item = item,
                        showDivider = index < items.size - 1
                    )
                }
            }
        }
    }
}

/**
 * Individual settings item row with Material 3 styling
 */
@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = !item.disabled && item.onClick != null,
                    onClick = { item.onClick?.invoke() }
                )
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (item.icon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (item.disabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else if (item.isHighlighted) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .alpha(if (item.disabled) 0.5f else 1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.showBadge) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            }
                        ) {
                            Icon(
                                painter = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (item.isHighlighted && !item.disabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (item.isHighlighted && !item.disabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))
            }
            
            // Title and description
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (item.disabled) 0.4f else 1f)
            ) {
                // Title content (can be Text or custom composable)
                item.title()

                // Subtitle if provided (simple string)
                item.subtitle?.let { sub ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Description if provided (custom composable)
                item.description?.let { desc ->
                    Spacer(modifier = Modifier.height(2.dp))
                    desc()
                }
            }
            
            // Trailing Content
            if (item.trailingContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                Box(Modifier.alpha(if (item.disabled) 0.4f else 1f)) {
                    item.trailingContent.invoke()
                }
            }
            
            // Navigation Chevron if item is clickable, not disabled, and has no trailing content
            if (item.onClick != null && !item.disabled && item.trailingContent == null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    painter = painterResource(id = R.drawable.lucide_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).alpha(if (item.disabled) 0.4f else 1f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Divider
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = if (item.icon != null) 76.dp else 20.dp,
                    end = 20.dp
                ),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Data class for Material 3 settings item
 */
data class Material3SettingsItem(
    val icon: Painter? = null,
    val title: @Composable () -> Unit,
    val subtitle: String? = null,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val disabled: Boolean = false,
    val onClick: (() -> Unit)? = null
)

