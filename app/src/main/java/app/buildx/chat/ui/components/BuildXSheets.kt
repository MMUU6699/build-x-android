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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.ReasoningStep
import app.buildx.chat.data.model.StagedAttachment
import app.buildx.chat.ui.theme.BuildXTheme
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
            text = "Steps",
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
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.summary,
                            color = colors.secondaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 20.sp
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
                        contentDescription = "Done",
                        tint = colors.secondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Done",
                    color = colors.secondaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
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
@Composable
fun HeaderOverflowPopup(
    isPinned: Boolean,
    onShareLink: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors

    Column(
        modifier = modifier
            .width(188.dp)
            .softShadow(
                color = colors.composerShadowColor,
                cornerRadius = 16.dp,
                blurRadius = 20.dp,
                offsetY = 6.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.hairlineBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 4.dp)
    ) {
        OverflowMenuRow(
            label = "Share a link",
            iconRes = R.drawable.ic_link,
            onClick = onShareLink,
            showDivider = true
        )
        OverflowMenuRow(
            label = if (isPinned) "Unpin" else "Pin",
            iconRes = R.drawable.ic_pin,
            onClick = onTogglePin,
            showDivider = true
        )
        OverflowMenuRow(
            label = "Rename",
            iconRes = R.drawable.ic_pencil,
            onClick = onRename,
            showDivider = true
        )
        OverflowMenuRow(
            label = "Delete chat",
            iconRes = R.drawable.ic_trash,
            isDestructive = true,
            onClick = onDelete,
            showDivider = false
        )
    }
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
                color = colors.hairlineBorder
            )
        }
    }
}

/**
 * S6 Attachment Sheet Content (tap Plus in composer):
 * Background #F1F3F0 (surfaceMuted), top radius 28dp, grab handle 32x4dp.
 * Header row: "Library" (bold gray) + "See all" (primary blue #0063D3).
 * Horizontal strip: first tile Camera (white, radius 16dp, 102dp square), then recent gallery
 * thumbnails with selection circle at top-right.
 * Below: a white GroupedCard (radius 20dp) with two rows:
 * - "Recently uploaded" (clock-history icon)
 * - "Files" (paperclip icon)
 * with hairline divider inset.
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

        // Header row: "Library" (bold gray) + "See all" (blue) or "Add (N)" when selected
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                color = colors.secondaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (selectedUris.isNotEmpty()) {
                Text(
                    text = "Attach (${selectedUris.size})",
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
                    text = "See all",
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
                // First tile: Camera (white, radius 16dp, 102dp x 102dp)
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBackground)
                        .pressScaleClickable(onClick = onCameraTileClick),
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
                    // Selection circle at top-right (22dp)
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
                title = "Recently uploaded",
                onClick = onRecentlyUploadedClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_paperclip,
                title = "Files",
                onClick = onFilesClick,
                showDivider = false
            )
        }
    }
}

/**
 * S11 Voice Dictation Bar:
 * Replaces composer bottom row while Android SpeechRecognizer is actively listening.
 * Left: 36dp Close X circle button.
 * Center: 36dp pill with live dotted/vertical-bar waveform driven by real microphone RMS dB.
 * Right: 36dp Confirm/Send circle button (disabled gray until speech recognized, then blue #0063D3).
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
                offsetY = 4.dp
            )
            .clip(RoundedCornerShape(28.dp))
            .background(colors.cardBackground)
            .border(1.dp, colors.hairlineBorder.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Close X circle (36dp)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(1.dp, colors.hairlineBorder, CircleShape)
                    .pressScaleClickable(onClick = onCancel),
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

            // Center: Waveform pill (36dp tall, 1dp hairline border)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardBackground)
                    .border(1.dp, colors.hairlineBorder, RoundedCornerShape(18.dp))
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

            // Right: Send / Confirm circle (36dp)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (hasSpeech) colors.primaryBlue else colors.surfaceMuted)
                    .pressScaleClickable(enabled = hasSpeech, onClick = onConfirm),
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
