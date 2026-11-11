package app.buildx.chat.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.buildx.chat.R

/**
 * VerifiedBadge: Renders the official Build X verified account seal.
 *
 * Uses `tint = Color.Unspecified` to ensure the white checkmark remains crisply
 * visible against the primary blue rosette across all display sizes (14dp, 16dp, 20dp,
 * 24dp, 48dp, 64dp) in both light and dark themes.
 */
@Composable
fun VerifiedBadge(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = stringResource(id = R.string.verified_account)
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_verified_badge),
        contentDescription = contentDescription,
        tint = Color.Unspecified,
        modifier = modifier.size(size)
    )
}

/**
 * NameWithBadge: Layout helper for displaying a user display name with an inline verified badge.
 *
 * Key guarantees:
 * 1. Clean wrapping: The badge wraps cleanly with the last word of [name] rather than
 *    stranding alone on a new line.
 * 2. Never ellipsized: When text overflows and is truncated, the badge is never clipped or
 *    ellipsized; the text truncates with an ellipsis and the badge remains fully visible
 *    immediately adjacent to it.
 * 3. Bidirectional support: Uses TextDirection.Content for automatic per-text script detection
 *    (Arabic RTL / Latin LTR) and respects layout direction mirroring.
 */
@Composable
fun NameWithBadge(
    name: String,
    isVerified: Boolean,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 16.dp,
    badgeSpacing: Dp = 4.dp,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    badgeContentDescription: String? = null
) {
    val resolvedStyle = remember(style, fontSize, fontWeight, fontFamily, color, textAlign) {
        style.copy(
            fontSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize,
            fontWeight = fontWeight ?: style.fontWeight,
            fontFamily = fontFamily ?: style.fontFamily,
            color = if (color != Color.Unspecified) color else style.color,
            textAlign = textAlign ?: style.textAlign,
            textDirection = TextDirection.Content
        )
    }

    if (!isVerified) {
        Text(
            text = name,
            modifier = modifier,
            style = resolvedStyle,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }

    if (maxLines == 1) {
        // Single-line layout: text occupies available space minus badge and spacing.
        // If text overflows, it cleanly ellipsizes without ever cutting off the badge.
        SubcomposeLayout(modifier = modifier) { constraints ->
            val badgePx = badgeSize.roundToPx()
            val spacingPx = badgeSpacing.roundToPx()
            val badgeTotalPx = badgePx + spacingPx

            val badgePlaceable = subcompose("badge") {
                VerifiedBadge(
                    size = badgeSize,
                    contentDescription = badgeContentDescription
                )
            }.first().measure(Constraints.fixed(badgePx, badgePx))

            val maxTextWidth = if (constraints.hasBoundedWidth) {
                (constraints.maxWidth - badgeTotalPx).coerceAtLeast(0)
            } else {
                Constraints.Infinity
            }

            val textConstraints = constraints.copy(
                minWidth = 0,
                maxWidth = maxTextWidth
            )

            val textPlaceable = subcompose("text") {
                Text(
                    text = name,
                    style = resolvedStyle,
                    maxLines = 1,
                    overflow = overflow
                )
            }.first().measure(textConstraints)

            val totalWidth = if (constraints.hasBoundedWidth) {
                (textPlaceable.width + spacingPx + badgePlaceable.width).coerceIn(
                    constraints.minWidth,
                    constraints.maxWidth
                )
            } else {
                textPlaceable.width + spacingPx + badgePlaceable.width
            }

            val totalHeight = maxOf(textPlaceable.height, badgePlaceable.height)
                .coerceIn(constraints.minHeight, constraints.maxHeight)

            layout(totalWidth, totalHeight) {
                textPlaceable.placeRelative(
                    x = 0,
                    y = (totalHeight - textPlaceable.height) / 2
                )
                badgePlaceable.placeRelative(
                    x = textPlaceable.width + spacingPx,
                    y = (totalHeight - badgePlaceable.height) / 2
                )
            }
        }
        return
    }

    // Multiline layout (maxLines > 1):
    // Construct an inline content text where the last word of name and the badge are joined
    // by a non-breaking space (U+00A0). This ensures the last word and badge wrap together.
    val density = LocalDensity.current
    val badgeSp = remember(badgeSize, density) {
        with(density) { badgeSize.toSp() }
    }

    val inlineBadgeId = "verified_badge_inline"
    val trimmedName = remember(name) { name.trimEnd() }
    val lastSpaceIndex = remember(trimmedName) { trimmedName.lastIndexOf(' ') }

    val prefix = remember(trimmedName, lastSpaceIndex) {
        if (lastSpaceIndex >= 0) trimmedName.substring(0, lastSpaceIndex + 1) else ""
    }
    val lastWord = remember(trimmedName, lastSpaceIndex) {
        if (lastSpaceIndex >= 0) trimmedName.substring(lastSpaceIndex + 1) else trimmedName
    }

    val annotatedString = remember(prefix, lastWord, inlineBadgeId) {
        buildAnnotatedString {
            if (prefix.isNotEmpty()) {
                append(prefix)
            }
            append(lastWord)
            append('\u00A0')
            appendInlineContent(inlineBadgeId, "[verified]")
        }
    }

    val inlineContent = remember(inlineBadgeId, badgeSp, badgeSize, badgeContentDescription) {
        mapOf(
            inlineBadgeId to InlineTextContent(
                placeholder = Placeholder(
                    width = badgeSp,
                    height = badgeSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                VerifiedBadge(
                    size = badgeSize,
                    contentDescription = badgeContentDescription
                )
            }
        )
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = resolvedStyle,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Formats an ISO 8601 timestamp (e.g. "2026-11-10T12:00:00Z" or "2026-11-10")
 * into a localized date string (e.g. "Nov 10, 2026").
 */
fun formatVerifiedDate(verifiedAt: String?): String? {
    if (verifiedAt.isNullOrBlank()) return null
    return try {
        val instant = java.time.Instant.parse(verifiedAt)
        val zone = java.time.ZoneId.systemDefault()
        val localDate = instant.atZone(zone).toLocalDate()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.getDefault())
        localDate.format(formatter)
    } catch (_: Exception) {
        try {
            val localDate = java.time.LocalDate.parse(verifiedAt.substringBefore("T"))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.getDefault())
            localDate.format(formatter)
        } catch (_: Exception) {
            verifiedAt.substringBefore("T")
        }
    }
}
