package com.amresalehin.emreshots.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =========================================================================
// Emreh Global Geometric Tokens (Soft, organic, controlled scale)
// =========================================================================

object EmrehShapes {
    val ExtraSmall = RoundedCornerShape(8.dp)
    val Small = RoundedCornerShape(10.dp)       // Badges, tags, miniature indicators
    val Badge = Small
    val Medium = RoundedCornerShape(16.dp)      // Secondary cards, controls
    val Card = RoundedCornerShape(20.dp)        // Primary cards, media preview tiles
    val Large = RoundedCornerShape(26.dp)       // Dialogs, floating sheets
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Pill = RoundedCornerShape(100.dp)       // Filter chips, action buttons, search bars
    val SearchPill = Pill
    val Circle = CircleShape
}

// =========================================================================
// Emreh Standard Spacing Scale (8dp grid, consistent baselines)
// =========================================================================

object EmrehSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val section: Dp = 32.dp
}

// =========================================================================
// Tactile Surface Modifiers (Restrained borders, soft tonal separation)
// =========================================================================

/**
 * Creates a soft tactile border suitable for cards, panels, and chips
 */
@Composable
fun tactileBorder(
    width: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
): BorderStroke = BorderStroke(width, color)

/**
 * Modifier extension to apply a soft tactile border around any composable
 */
@Composable
fun Modifier.tactileBorder(
    shape: Shape = EmrehShapes.Card,
    width: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
): Modifier = this.border(width, color, shape)

/**
 * Atmospheric background gradient brush providing subtle, restrained ambient depth.
 */
@Composable
fun atmosphericGradientBrush(): Brush {
    val bg = MaterialTheme.colorScheme.background
    val subtleTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    return Brush.verticalGradient(
        colors = listOf(subtleTint, bg, bg)
    )
}

// =========================================================================
// Reusable Emreh Design Components
// =========================================================================

/**
 * Tactile Card with soft geometry, fine border, and restrained elevation.
 */
@Composable
fun EmrehCard(
    modifier: Modifier = Modifier,
    shape: Shape = EmrehShapes.Card,
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = tactileBorder(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            border = border,
            elevation = elevation
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            border = border,
            elevation = elevation
        ) {
            content()
        }
    }
}

/**
 * Tactile, soft pill button for primary or secondary actions.
 * Complies with 48dp minimum interactive component size.
 */
@Composable
fun EmrehPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isPrimary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (!isPrimary && enabled) {
        tactileBorder(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    } else null

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = EmrehShapes.Pill,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EmrehSpacing.lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(EmrehSpacing.sm))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Soft tactile badge for metadata labels (e.g. EXIF, OCR, AI, Video).
 */
@Composable
fun EmrehBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val finalContainer = accentColor?.copy(alpha = 0.16f) ?: containerColor
    val finalContent = accentColor ?: contentColor

    Surface(
        shape = EmrehShapes.Small,
        color = finalContainer,
        contentColor = finalContent,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = finalContent
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Circular tactile icon control with 48dp minimum interactive target.
 */
@Composable
fun EmrehIconControl(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    badgeCount: Int = 0
) {
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val iconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                CircleShape
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
    }
}

/**
 * Editorial Empty State following the calm, tactile identity.
 */
@Composable
fun EmrehEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(EmrehSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(EmrehShapes.Card)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(tactileBorder(), EmrehShapes.Card),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(EmrehSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(EmrehSpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = EmrehSpacing.md)
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(EmrehSpacing.xl))
            actionButton()
        }
    }
}
