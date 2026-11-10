package app.buildx.chat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.components.GroupedCard
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * P3 Edit Profile Screen:
 * - Header: Close X, "Edit profile", Save button
 * - Avatar with camera picker button
 * - Display name (1-30 chars)
 * - Username (lowercase, 14-day cooldown check or one-time review bypass)
 * - Bio (multiline, max 160 chars, max 4 newlines)
 * - Unsaved changes discard confirmation dialog
 */
@Composable
fun EditProfileScreen(
    profile: UserProfile?,
    isUploadingAvatar: Boolean = false,
    onSaveProfile: (displayName: String, username: String?, bio: String?) -> Unit,
    onPickAvatarClick: () -> Unit,
    onBackClick: () -> Unit,
    checkUsernameAvailable: suspend (String) -> Boolean
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    var displayName by remember(profile?.id) { mutableStateOf(profile?.displayName ?: "") }
    var currentUsername by remember(profile?.id) { mutableStateOf(profile?.effectiveUsername ?: "") }
    var newUsernameInput by remember(profile?.id) { mutableStateOf(profile?.effectiveUsername ?: "") }
    var bioText by remember(profile?.id) { mutableStateOf(profile?.bio ?: "") }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameValidationError by remember { mutableStateOf<String?>(null) }

    // 14-day cooldown calculation
    val canChangeUsername = remember(profile) {
        val lastChanged = profile?.usernameChangedAt
        val isReviewed = profile?.usernameReviewedAt != null
        if (!isReviewed) {
            // One-time review bypass
            true
        } else if (lastChanged != null) {
            try {
                val lastInstant = Instant.parse(lastChanged)
                val daysSince = ChronoUnit.DAYS.between(lastInstant, Instant.now())
                daysSince >= 14
            } catch (_: Throwable) {
                true
            }
        } else {
            true
        }
    }

    // Has changes
    val hasChanges = remember(displayName, newUsernameInput, bioText, profile) {
        val origName = profile?.displayName ?: ""
        val origUser = profile?.effectiveUsername ?: ""
        val origBio = profile?.bio ?: ""
        displayName.trim() != origName.trim() ||
        newUsernameInput.trim() != origUser.trim() ||
        bioText.trim() != origBio.trim()
    }

    BackHandler(enabled = hasChanges) {
        showDiscardDialog = true
    }

    // Debounced username validation and availability check
    LaunchedEffect(newUsernameInput) {
        val clean = newUsernameInput.trim().removePrefix("@").lowercase()
        if (clean == currentUsername.lowercase()) {
            isUsernameAvailable = null
            usernameValidationError = null
            return@LaunchedEffect
        }

        // Validate format: 3-24 chars, [a-z0-9._], no double dots, no edge dots
        if (!clean.matches(Regex("^[a-z0-9._]{3,24}$")) || clean.contains("..") || clean.startsWith(".") || clean.endsWith(".")) {
            usernameValidationError = strings.usernameInvalid
            isUsernameAvailable = false
            return@LaunchedEffect
        }

        usernameValidationError = null
        isCheckingUsername = true
        delay(400L) // 400ms debounce
        try {
            val available = checkUsernameAvailable(clean)
            isUsernameAvailable = available
        } catch (_: Throwable) {
            isUsernameAvailable = false
        } finally {
            isCheckingUsername = false
        }
    }

    val isBioWithinLimits = remember(bioText) {
        val lineBreaks = bioText.count { it == '\n' }
        bioText.length <= 160 && lineBreaks <= 4
    }

    val isFormValid = remember(displayName, newUsernameInput, isUsernameAvailable, isBioWithinLimits, hasChanges) {
        val nameValid = displayName.trim().isNotEmpty() && displayName.length <= 30
        val userValid = if (newUsernameInput.trim().lowercase() == currentUsername.lowercase()) {
            true
        } else {
            isUsernameAvailable == true && usernameValidationError == null
        }
        nameValid && userValid && isBioWithinLimits && hasChanges
    }

    val isDisplayNameRtl = isRtlText(displayName, defaultRtl = strings.isRtl)
    val isBioRtl = isRtlText(bioText, defaultRtl = strings.isRtl)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Close X, "Edit profile", Save button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCircleButton(
                    iconRes = R.drawable.ic_close,
                    contentDescription = strings.cancelButton,
                    onClick = {
                        if (hasChanges) showDiscardDialog = true else onBackClick()
                    }
                )

                Text(
                    text = strings.editProfileTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )

                TextButton(
                    onClick = {
                        val userParam = if (newUsernameInput.trim().lowercase() != currentUsername.lowercase()) {
                            newUsernameInput.trim().removePrefix("@").lowercase()
                        } else null
                        onSaveProfile(displayName.trim(), userParam, bioText.trim().ifEmpty { null })
                    },
                    enabled = isFormValid
                ) {
                    Text(
                        text = strings.saveButton,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFormValid) colors.primaryBlue else colors.secondaryText.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Avatar circle with camera edit badge
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clickable(enabled = !isUploadingAvatar) { onPickAvatarClick() },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    if (!profile?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.avatarUrl,
                            contentDescription = strings.changePhoto,
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .border(2.dp, colors.cardBackground, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(colors.cardBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user),
                                contentDescription = strings.changePhoto,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    // Uploading overlay indicator
                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }

                    // Camera badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.primaryBlue)
                            .border(2.dp, colors.cardBackground, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera),
                            contentDescription = strings.changePhoto,
                            tint = colors.onPrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.changePhoto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryBlue,
                    modifier = Modifier.clickable(enabled = !isUploadingAvatar) { onPickAvatarClick() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Form Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display Name Card
                GroupedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.displayNameLabel,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (isDisplayNameRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            BasicTextField(
                                value = displayName,
                                onValueChange = { if (it.length <= 30) displayName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primaryText,
                                    textAlign = TextAlign.Start
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Username Card
                GroupedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.handleLabel,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.secondaryText
                            )

                            if (isCheckingUsername) {
                                CircularProgressIndicator(
                                    color = colors.primaryBlue,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "@",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.secondaryText
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                BasicTextField(
                                    value = newUsernameInput,
                                    onValueChange = {
                                        if (canChangeUsername && it.length <= 24) {
                                            newUsernameInput = it.lowercase().filter { ch ->
                                                ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '_'
                                            }
                                        }
                                    },
                                    enabled = canChangeUsername,
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = BuildXFontFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (canChangeUsername) colors.primaryText else colors.secondaryText,
                                        textAlign = TextAlign.Start
                                    ),
                                    cursorBrush = SolidColor(colors.primaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Availability and Cooldown notices
                        if (!canChangeUsername) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.usernameCooldownNotice,
                                fontSize = 12.sp,
                                color = colors.secondaryText
                            )
                        } else if (usernameValidationError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = usernameValidationError ?: "",
                                fontSize = 12.sp,
                                color = colors.destructiveRed
                            )
                        } else if (newUsernameInput.trim().lowercase() != currentUsername.lowercase()) {
                            if (isUsernameAvailable == true) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = strings.usernameAvailable,
                                    fontSize = 12.sp,
                                    color = Color(0xFF107C41)
                                )
                            } else if (isUsernameAvailable == false && !isCheckingUsername) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = strings.usernameUnavailable,
                                    fontSize = 12.sp,
                                    color = colors.destructiveRed
                                )
                            }
                        }
                    }
                }

                // Bio Card (multiline, max 160 chars, max 4 line breaks)
                GroupedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.bioLabel,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.secondaryText
                            )

                            val charCount = bioText.length
                            val lineCount = bioText.count { it == '\n' }
                            Text(
                                text = "$charCount/160  ($lineCount/4)",
                                fontSize = 11.5.sp,
                                color = if (isBioWithinLimits) colors.secondaryText else colors.destructiveRed
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (isBioRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            BasicTextField(
                                value = bioText,
                                onValueChange = {
                                    val lines = it.count { ch -> ch == '\n' }
                                    if (it.length <= 160 && lines <= 4) {
                                        bioText = it
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    fontSize = 15.sp,
                                    color = colors.primaryText,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Start
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (bioText.isEmpty()) {
                                            Text(
                                                text = strings.bioPlaceholder,
                                                fontSize = 15.sp,
                                                color = colors.secondaryText
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Discard Unsaved Changes Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = strings.discardChangesTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            },
            text = {
                Text(
                    text = strings.discardChangesBody,
                    fontSize = 14.sp,
                    color = colors.secondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text(
                        text = strings.discardButton,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.destructiveRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(
                        text = strings.keepEditingButton,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primaryBlue
                    )
                }
            },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
