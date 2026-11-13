package app.buildx.chat.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.WallpaperCategory
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import kotlin.math.roundToInt

/**
 * Full-screen curated Chat Wallpaper & Custom Gallery picker.
 * Renders a Telegram-style live 2-bubble preview card, Dimming slider (0%..70%),
 * Blur toggle, Gallery photo importer, 6 filterable categories across all 24
 * artistic built-in presets, and per-chat or global apply actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWallpaperPickerScreen(
    initialConfig: ChatWallpaperConfig,
    isSpecificChat: Boolean,
    isApplying: Boolean = false,
    onBackClick: () -> Unit,
    onApplyWallpaper: (pickedImageUri: Uri?, config: ChatWallpaperConfig, applyToAll: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val chatColors = remember(colors) { colors.toChatColors() }

    var selectedId by remember(initialConfig) { mutableStateOf(initialConfig.normalizedId) }
    var customPreviewUri by remember(initialConfig) { mutableStateOf<Uri?>(null) }
    var customUrl by remember(initialConfig) { mutableStateOf(initialConfig.customUrl) }
    var dimLevel by remember(initialConfig) { mutableFloatStateOf(initialConfig.dim.coerceIn(0f, 0.70f)) }
    var blurEnabled by remember(initialConfig) { mutableStateOf(initialConfig.blur) }
    var selectedCategory by remember { mutableStateOf<WallpaperCategory?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            customPreviewUri = uri
            customUrl = uri.toString()
            selectedId = "custom"
        }
    }

    val livePreviewConfig = remember(selectedId, customPreviewUri, customUrl, dimLevel, blurEnabled) {
        ChatWallpaperConfig(
            wallpaperId = selectedId,
            customUrl = customPreviewUri?.toString() ?: customUrl,
            dim = dimLevel.coerceIn(0f, 0.70f),
            blur = blurEnabled
        )
    }

    val visiblePresets = remember(selectedCategory) {
        val cat = selectedCategory
        if (cat == null) {
            ChatWallpaperCatalog.allSelectablePresets
        } else {
            val matching = ChatWallpaperCatalog.artisticPresets.filter { it.category == cat }
            if (cat == WallpaperCategory.SIGNATURE) listOf(ChatWallpaperCatalog.defaultPreset) + matching else matching
        }
    }

    val activeTitle = remember(livePreviewConfig) {
        if (livePreviewConfig.isCustom) {
            null
        } else {
            ChatWallpaperCatalog.findById(livePreviewConfig.wallpaperId).title
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        // Header bar (clean back button + title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = stringResource(R.string.dm_back),
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.dm_wallpaper),
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BuildXFontFamily,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Pinned live 210.dp preview card with corner glassmorphic controls & realistic bubbles
        WallpaperLivePreviewCard(
            config = livePreviewConfig,
            activeTitle = activeTitle,
            isDark = colors.isDark,
            blurEnabled = blurEnabled,
            onToggleBlur = { blurEnabled = !blurEnabled },
            onResetDefault = {
                selectedId = "default"
                customPreviewUri = null
                customUrl = null
                dimLevel = 0f
                blurEnabled = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp)
        ) {
            // 1. Unified Gallery Import + Dimming Control Strip
            item(key = "controls_strip") {
                val dimPercent = ((dimLevel / 0.70f) * 70f).roundToInt().coerceIn(0, 70)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.cardBackground)
                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surfaceMuted)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(999.dp))
                                .pressScaleClickable {
                                    galleryPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_image),
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.dm_wallpaper_choose_gallery),
                                color = colors.primaryText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = BuildXFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dm_wallpaper_dimming),
                                color = colors.secondaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = BuildXFontFamily,
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.surfaceMuted)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$dimPercent%",
                                    color = colors.primaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = BuildXFontFamily
                                )
                            }
                        }
                    }

                    Slider(
                        value = dimLevel,
                        onValueChange = { dimLevel = it.coerceIn(0f, 0.70f) },
                        valueRange = 0f..0.70f,
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .softShadow(
                                        color = colors.headerHaloShadowColor,
                                        cornerRadius = 9.dp,
                                        blurRadius = 6.dp,
                                        offsetY = 1.dp
                                    )
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(2.dp, colors.primaryBlue, CircleShape)
                            )
                        },
                        track = { sliderState ->
                            val fraction = (sliderState.value / 0.70f).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.surfaceMuted)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.primaryBlue)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
            }

            // 2. Category Filter Strip
            item(key = "category_strip") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "cat_all") {
                        WallpaperCategoryChip(
                            label = stringResource(R.string.dm_wallpaper_cat_all),
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null }
                        )
                    }
                    items(WallpaperCategory.entries, key = { it.name }) { category ->
                        WallpaperCategoryChip(
                            label = stringResource(ChatWallpaperCatalog.categoryLabelRes(category)),
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }

            // 3. Swatch Grid (3 columns)
            val rows = visiblePresets.chunked(3)
            items(rows.size, key = { idx -> "wp_row_$idx" }) { rowIdx ->
                val rowPresets = rows[rowIdx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val isSelected = selectedId == preset.id
                        WallpaperPresetSwatch(
                            preset = preset,
                            selected = isSelected,
                            isDark = colors.isDark,
                            chatColors = chatColors,
                            onClick = {
                                selectedId = preset.id
                                customPreviewUri = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowPresets.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Bottom Sticky Action Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardBackground)
                .border(0.7.dp, colors.hairlineBorder)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            if (isApplying) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.primaryBlue.copy(alpha = 0.85f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    MarkLoader(
                        size = 18.dp,
                        isSpinning = true,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.dm_wallpaper_uploading),
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BuildXFontFamily
                    )
                }
            } else if (isSpecificChat) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surfaceMuted)
                            .border(0.9.dp, colors.subtleSurfaceBorder, RoundedCornerShape(24.dp))
                            .pressScaleClickable {
                                onApplyWallpaper(customPreviewUri, livePreviewConfig, true)
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.dm_wallpaper_apply_all),
                            color = colors.primaryText,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BuildXFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.primaryBlue)
                            .pressScaleClickable {
                                onApplyWallpaper(customPreviewUri, livePreviewConfig, false)
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.dm_wallpaper_apply_chat),
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BuildXFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.primaryBlue)
                        .pressScaleClickable {
                            onApplyWallpaper(customPreviewUri, livePreviewConfig, true)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dm_wallpaper_apply_all),
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BuildXFontFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.primaryText else colors.surfaceMuted)
            .border(
                width = 0.8.dp,
                color = if (selected) colors.primaryText else colors.subtleSurfaceBorder,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.pageBackground else colors.primaryText,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontFamily = BuildXFontFamily,
            maxLines = 1
        )
    }
}

@Composable
private fun WallpaperLivePreviewCard(
    config: ChatWallpaperConfig,
    activeTitle: String?,
    isDark: Boolean,
    blurEnabled: Boolean,
    onToggleBlur: () -> Unit,
    onResetDefault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val chatColors = remember(colors) { colors.toChatColors() }
    val blurPillBg by animateColorAsState(
        targetValue = if (blurEnabled) colors.primaryBlue else colors.cardBackground.copy(alpha = 0.88f),
        animationSpec = tween(durationMillis = 180),
        label = "wallpaper_blur_pill_bg"
    )
    val blurPillBorder by animateColorAsState(
        targetValue = if (blurEnabled) colors.primaryBlue else colors.subtleSurfaceBorder,
        animationSpec = tween(durationMillis = 180),
        label = "wallpaper_blur_pill_border"
    )
    val blurPillContentColor by animateColorAsState(
        targetValue = if (blurEnabled) Color.White else colors.primaryText,
        animationSpec = tween(durationMillis = 180),
        label = "wallpaper_blur_pill_fg"
    )
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .height(232.dp)
            .softShadow(
                color = colors.headerHaloShadowColor,
                cornerRadius = 22.dp,
                blurRadius = 16.dp,
                offsetY = 3.dp
            )
            .clip(cardShape)
            .border(1.dp, colors.subtleSurfaceBorder, cardShape)
    ) {
        ChatWallpaperLayer(
            config = config,
            isDark = isDark,
            fallbackBackground = chatColors.pageBackground,
            modifier = Modifier.fillMaxSize()
        )

        // Top corner glassmorphic pills: preset badge (start) + Reset / Blur (end)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.cardBackground.copy(alpha = 0.88f))
                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = activeTitle ?: stringResource(R.string.dm_wallpaper_custom_label),
                    color = colors.primaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BuildXFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!config.isDefault) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.cardBackground.copy(alpha = 0.88f))
                            .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(999.dp))
                            .pressScaleClickable(onClick = onResetDefault)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dm_wallpaper_reset_default),
                            color = colors.primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BuildXFontFamily,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(blurPillBg)
                        .border(0.8.dp, blurPillBorder, RoundedCornerShape(999.dp))
                        .pressScaleClickable(onClick = onToggleBlur)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_blur),
                        contentDescription = null,
                        tint = blurPillContentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.dm_wallpaper_blur),
                        color = blurPillContentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BuildXFontFamily,
                        maxLines = 1
                    )
                }
            }
        }

        // Bottom realistic incoming bubble + outgoing bubble (with quoted reply & 23:01 checkmarks)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .widthIn(max = 244.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
                    .background(chatColors.receivedBubbleBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dm_wallpaper_preview_incoming),
                        color = chatColors.receivedBubbleText,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = BuildXFontFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "23:00",
                        color = chatColors.receivedMetaText,
                        fontSize = 10.sp,
                        fontFamily = BuildXFontFamily
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .widthIn(max = 258.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                    .background(chatColors.sentBubbleBg)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(bottom = 5.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(chatColors.quotedBgSent)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .fillMaxHeight()
                                .background(chatColors.quotedBarSent)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dm_you),
                                color = chatColors.quotedBarSent,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = BuildXFontFamily,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.dm_wallpaper_preview_incoming),
                                color = chatColors.sentBubbleText.copy(alpha = 0.86f),
                                fontSize = 11.sp,
                                fontFamily = BuildXFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dm_wallpaper_preview_outgoing),
                            color = chatColors.sentBubbleText,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                            fontFamily = BuildXFontFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "23:01",
                                color = chatColors.sentMetaText,
                                fontSize = 10.sp,
                                fontFamily = BuildXFontFamily
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dm_double_check),
                                contentDescription = null,
                                tint = chatColors.readCheckAccent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperPresetSwatch(
    preset: ChatWallpaperPreset,
    selected: Boolean,
    isDark: Boolean,
    chatColors: ChatColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.primaryBlue else colors.subtleSurfaceBorder,
        animationSpec = tween(durationMillis = 180),
        label = "swatch_ring"
    )

    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 0.8.dp,
                color = borderColor,
                shape = shape
            )
            .pressScaleClickable(onClick = onClick)
    ) {
        if (preset.id == "default") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
            )
        } else {
            ChatWallpaperArtwork(
                preset = preset,
                isDark = isDark,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Mini chat bubble silhouettes illustrating contrast over each backdrop
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Start)
                    .fillMaxWidth(0.58f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomEnd = 9.dp, bottomStart = 3.dp))
                    .background(chatColors.receivedBubbleBg.copy(alpha = 0.90f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .fillMaxWidth(0.64f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 9.dp, bottomEnd = 3.dp))
                    .background(colors.primaryBlue.copy(alpha = 0.92f))
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.primaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.56f)
                    )
                )
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                text = preset.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BuildXFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, name = "Wallpaper Picker - Light")
@Composable
private fun PreviewChatWallpaperPickerLight() {
    BuildXTheme(appearance = AppAppearance.LIGHT) {
        ChatWallpaperPickerScreen(
            initialConfig = ChatWallpaperConfig(wallpaperId = "classic_sky", dim = 0.1f, blur = false),
            isSpecificChat = true,
            onBackClick = {},
            onApplyWallpaper = { _, _, _ -> }
        )
    }
}

@Preview(showBackground = true, name = "Wallpaper Picker - Dark")
@Composable
private fun PreviewChatWallpaperPickerDark() {
    BuildXTheme(appearance = AppAppearance.DARK) {
        ChatWallpaperPickerScreen(
            initialConfig = ChatWallpaperConfig(wallpaperId = "aurora_midnight", dim = 0.25f, blur = false),
            isSpecificChat = true,
            onBackClick = {},
            onApplyWallpaper = { _, _, _ -> }
        )
    }
}
