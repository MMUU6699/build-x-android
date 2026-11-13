package app.buildx.chat.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import app.buildx.chat.R
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage

/**
 * S4 Steps Sheet Content:
 * Bottom sheet over a 30% scrim:
 * - Grab handle (~32x4dp)
 * - Centered bold title "Steps"
 * - Timeline: dot per step with vertical connector line, bold gray step title + regular gray body
 * - Final row "Done" with check-circle icon
 */
@Composable
fun StepsSheetContent(
    steps: List<ReasoningStep>,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val firstStepRtl = remember(steps, strings.isRtl) {
        val sample = steps.firstOrNull()?.let { "${it.title} ${it.summary}" } ?: ""
        isRtlText(sample, defaultRtl = strings.isRtl)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (firstStepRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(colors.cardBackground)
                .padding(top = 10.dp, bottom = 32.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Grab handle (32x4dp)
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.stepsTitle,
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                steps.forEach { step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Timeline dot + vertical connector
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(24.dp)
                        ) {
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.secondaryText)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(54.dp)
                                    .background(colors.hairlineBorder)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = step.title,
                                color = colors.secondaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = step.summary,
                                color = colors.secondaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }

                // Final "Done" row with check-circle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check_circle),
                            contentDescription = strings.doneLabel,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.doneLabel,
                        color = colors.secondaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

/**
 * S5 Header Overflow Popup Card (anchored below More button):
 * Radius ~16dp, soft shadow.
 * Rows: Share a link, Pin / Unpin, Rename, Delete chat (crimson red #C3192E).
 * Label left, trailing icon right.
 */
/**
 * Reusable menu item model for popup menus (Header, User Bubble, Assistant Actions).
 */
data class ActionMenuItem(
    val label: String,
    @DrawableRes val iconRes: Int,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Reusable anchored action popup matching the app's design system:
 * - White card, ~16dp radius, soft shadow, subtle border
 * - Label at start, trailing icon at end
 * - Destructive red for destructive actions
 */
@Composable
fun ActionMenuPopup(
    items: List<ActionMenuItem>,
    modifier: Modifier = Modifier,
    popupWidth: Dp = 188.dp,
    borderWidth: Dp = 1.dp,
    shadowBlurRadius: Dp = 22.dp
) {
    val colors = BuildXTheme.colors
    Column(
        modifier = modifier
            .width(popupWidth)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 16.dp,
                blurRadius = shadowBlurRadius,
                offsetY = 6.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .border(borderWidth, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) {
        items.forEachIndexed { index, item ->
            OverflowMenuRow(
                label = item.label,
                iconRes = item.iconRes,
                onClick = item.onClick,
                isDestructive = item.isDestructive,
                showDivider = index < items.lastIndex
            )
        }
    }
}

/**
 * Calculates above vs below anchor position with safety margins so the popup never clips
 * at screen edges in RTL/LTR, with keyboards open, or under high font scaling.
 */
class BubblePopupPositionProvider(
    private val isRtl: Boolean
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val marginPx = 24
        val x = if (isRtl) {
            anchorBounds.left.coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx))
        } else {
            (anchorBounds.right - popupContentSize.width).coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx))
        }

        val spaceBelow = windowSize.height - anchorBounds.bottom
        val y = if (spaceBelow >= popupContentSize.height + marginPx || spaceBelow >= anchorBounds.top) {
            anchorBounds.bottom + 8
        } else {
            anchorBounds.top - popupContentSize.height - 8
        }

        return IntOffset(x, y.coerceIn(marginPx, (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)))
    }
}

/**
 * Header overflow popup (delegates to ActionMenuPopup).
 */
@Composable
fun HeaderOverflowPopup(
    isPinned: Boolean,
    onShareLink: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = BuildXTheme.strings
    val items = listOf(
        ActionMenuItem(label = strings.shareLink, iconRes = R.drawable.ic_link, onClick = onShareLink),
        ActionMenuItem(label = if (isPinned) strings.unpinChat else strings.pinChat, iconRes = R.drawable.ic_pin, onClick = onTogglePin),
        ActionMenuItem(label = strings.renameChat, iconRes = R.drawable.ic_pencil, onClick = onRename),
        ActionMenuItem(label = strings.deleteChat, iconRes = R.drawable.ic_trash, isDestructive = true, onClick = onDelete)
    )
    ActionMenuPopup(items = items, modifier = modifier.width(196.dp))
}

@Composable
private fun OverflowMenuRow(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    showDivider: Boolean = false
) {
    val colors = BuildXTheme.colors
    val tint = if (isDestructive) colors.destructiveRed else colors.primaryText
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = tint,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                style = TextStyle(textDirection = TextDirection.Content),
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = colors.subtleSurfaceBorder
            )
        }
    }
}

/**
 * App-style bottom sheet for Select text (same style as the Steps sheet):
 * - 30% scrim
 * - Rounded top corners 28dp
 * - Centered grab handle
 * - Full text wrapped in a SelectionContainer for selectable text copying
 */
@Composable
fun SelectTextSheetContent(
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(colors.cardBackground)
            .padding(bottom = 24.dp)
    ) {
        // Grab handle (~32x4dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.grabHandle)
            )
        }

        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.selectTextTitle,
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )
            HeaderCircleButton(
                iconRes = R.drawable.ic_close,
                contentDescription = strings.closeButton,
                onClick = onClose
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = colors.subtleSurfaceBorder,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Selectable text container
        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start,
                style = TextStyle(
                    fontFamily = BuildXFontFamily,
                    textDirection = TextDirection.Content
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

/**
 * S6 Attachment Sheet Content (tap Plus in composer):
 * Background #F1F3F0 (surfaceMuted), top radius 28dp, grab handle 32x4dp.
 */
@Composable
fun AttachmentSheetContent(
    recentGalleryImages: List<StagedAttachment>,
    selectedUris: Set<String>,
    onSeeAllClick: () -> Unit,
    onCameraTileClick: () -> Unit,
    onToggleSelectGalleryImage: (String) -> Unit,
    onConfirmSelectedGalleryImages: () -> Unit,
    onRecentlyUploadedClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(colors.surfaceMuted)
            .padding(top = 10.dp, bottom = 28.dp)
    ) {
        // Grab handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.grabHandle)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Header row: "Library" (bold gray) + "See all" (blue) or "Attach (N)" when selected
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.libraryTitle,
                color = colors.secondaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            if (selectedUris.isNotEmpty()) {
                Text(
                    text = "${strings.attachSelectedPrefix} (${selectedUris.size})",
                    color = colors.primaryBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onConfirmSelectedGalleryImages)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Text(
                    text = strings.seeAllButton,
                    color = colors.primaryBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSeeAllClick)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal strip: Camera tile + recent MediaStore thumbnails
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .pressScaleClickable(onClick = onCameraTileClick)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = "Camera",
                        tint = colors.primaryText,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            items(recentGalleryImages, key = { it.uri.toString() }) { item ->
                val uriStr = item.uri.toString()
                val isSelected = selectedUris.contains(uriStr)

                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBackground)
                        .clickable { onToggleSelectGalleryImage(uriStr) }
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) colors.primaryBlue else Color.Black.copy(alpha = 0.25f)
                            )
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // White grouped card (radius 20dp) with "Recently uploaded" and "Files"
        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_clock_history,
                title = strings.recentlyUploaded,
                onClick = onRecentlyUploadedClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_paperclip,
                title = strings.filesLabel,
                onClick = onFilesClick,
                showDivider = false
            )
        }
    }
}

/**
 * S11 Voice Dictation Bar:
 * Replaces composer bottom row while Android SpeechRecognizer is actively listening.
 */
@Composable
fun VoiceDictationBar(
    amplitudes: List<Float>,
    recognizedText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val hasSpeech = recognizedText.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 28.dp,
                blurRadius = 24.dp,
                offsetY = 5.dp
            )
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardBackground)
            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (hasSpeech) {
            Text(
                text = recognizedText,
                color = colors.primaryText,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Close X circle (38dp)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .pressScaleClickable(onClick = onCancel)
                        .softShadow(
                            color = colors.innerControlShadowColor,
                            cornerRadius = 19.dp,
                            blurRadius = 6.dp,
                            offsetY = 1.5.dp
                        )
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .border(1.2.dp, colors.hairlineBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Cancel voice input",
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Center: Waveform pill (38dp tall, 1.2dp hairline border)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(colors.cardBackground)
                        .border(1.2.dp, colors.hairlineBorder, RoundedCornerShape(19.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    ) {
                        val count = amplitudes.size.coerceAtLeast(1)
                        val totalW = size.width
                        val step = totalW / count
                        val barW = 2.5.dp.toPx()
                        val maxH = size.height

                        amplitudes.forEachIndexed { idx, normAmp ->
                            val h = (normAmp.coerceIn(0.15f, 1f) * maxH).coerceAtLeast(3.dp.toPx())
                            val x = idx * step + (step - barW) / 2f
                            val y = (maxH - h) / 2f
                            drawRoundRect(
                                color = colors.secondaryText,
                                topLeft = Offset(x, y),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Send / Confirm circle (38dp)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .pressScaleClickable(enabled = hasSpeech, onClick = onConfirm)
                        .clip(CircleShape)
                        .background(if (hasSpeech) colors.primaryBlue else colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_up),
                        contentDescription = "Confirm voice input",
                        tint = if (hasSpeech) colors.onPrimaryBlue else colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
