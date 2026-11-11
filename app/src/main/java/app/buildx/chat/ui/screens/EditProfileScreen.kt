package app.buildx.chat.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.buildx.chat.R
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.components.GroupedCard
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.viewmodel.BuildXViewModel
import app.buildx.chat.util.AuthValidation
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Edit Profile Screen (T5):
 * - Layout: Soft keyboard resilience (.imePadding, .navigationBarsPadding, pinned Save button,
 *   auto-scroll focused field into view, generous bottom space).
 * - Label: "Username" (replaces legacy "Handle").
 * - Clean Bio counter: shows clean "11/160", with line limit warning only if > 4 lines.
 *   Save button enabled only when valid and changed.
 * - In-app image cropping component with square 512x512 output, circular preview mask,
 *   pan/pinch zoom, minimum zoom clamped to fill crop box, Cancel/Done pills, and friendly error dialog.
 * - Avatar preprocessing and upload flow with optimistic update and rollback on failure.
 * - "Remove photo" action with confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EditProfileScreen(
    profile: UserProfile?,
    isUploadingAvatar: Boolean = false,
    onSaveProfile: (displayName: String, username: String?, bio: String?) -> Unit,
    onPickAvatarClick: (() -> Unit)? = null,
    onRemoveAvatarClick: (() -> Unit)? = null,
    onUploadAvatarBytes: ((ByteArray, Uri?) -> Unit)? = null,
    onBackClick: () -> Unit,
    checkUsernameAvailable: suspend (String) -> Boolean,
    onCountrySelected: ((String?) -> Unit)? = null,
    viewModel: BuildXViewModel? = null
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val localViewModel = viewModel

    var displayName by remember(profile?.id) { mutableStateOf(profile?.displayName ?: "") }
    val currentUsername by remember(profile?.id) { mutableStateOf(profile?.effectiveUsername ?: "") }
    var newUsernameInput by remember(profile?.id) { mutableStateOf(profile?.effectiveUsername ?: "") }
    var bioText by remember(profile?.id) { mutableStateOf(profile?.bio ?: "") }
    var countryCode by remember(profile?.id) { mutableStateOf(profile?.countryCode) }
    var showCountryPicker by remember { mutableStateOf(false) }

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showAvatarOptionsSheet by remember { mutableStateOf(false) }
    var showRemovePhotoConfirm by remember { mutableStateOf(false) }
    var selectedCropBytes by remember { mutableStateOf<ByteArray?>(null) }

    var isCheckingUsername by remember { mutableStateOf(false) }
    var isUsernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameValidationError by remember { mutableStateOf<String?>(null) }

    // BringIntoView requesters for auto-scrolling focused fields
    val displayNameBringIntoView = remember { BringIntoViewRequester() }
    val usernameBringIntoView = remember { BringIntoViewRequester() }
    val bioBringIntoView = remember { BringIntoViewRequester() }

    // Photo picker launcher for avatar selection -> reads raw bytes once on Dispatchers.IO to prevent exhausted stream bugs
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                withContext(Dispatchers.Main) {
                    if (bytes != null && bytes.isNotEmpty()) {
                        selectedCropBytes = bytes
                    }
                }
            }
        }
    }

    // 14-day cooldown calculation
    val canChangeUsername = remember(profile) {
        val lastChanged = profile?.usernameChangedAt
        val isReviewed = profile?.usernameReviewedAt != null
        if (!isReviewed) {
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

    BackHandler(enabled = hasChanges && selectedCropBytes == null) {
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

        // Validate format via AuthValidation
        if (!AuthValidation.isValidUsername(clean)) {
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

    val isLinesExceeded = AuthValidation.isBioLinesExceeded(bioText)
    val isBioWithinLimits = AuthValidation.isValidBio(bioText)

    val isFormValid = remember(displayName, newUsernameInput, isUsernameAvailable, isBioWithinLimits, hasChanges, isCheckingUsername) {
        val nameValid = AuthValidation.isValidDisplayName(displayName)
        val userValid = if (newUsernameInput.trim().lowercase() == currentUsername.lowercase()) {
            true
        } else {
            isUsernameAvailable == true && usernameValidationError == null
        }
        nameValid && userValid && isBioWithinLimits && hasChanges && !isCheckingUsername
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Pinned Header: Close X, "Edit profile", Save button (never hidden by keyboard)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCircleButton(
                    iconRes = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.edit_profile_cancel),
                    onClick = {
                        if (hasChanges) showDiscardDialog = true else onBackClick()
                    }
                )

                Text(
                    text = stringResource(R.string.edit_profile_title),
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
                        text = stringResource(R.string.edit_profile_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFormValid) colors.primaryBlue else colors.secondaryText.copy(alpha = 0.5f)
                    )
                }
            }

            // Scrollable Form content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Avatar circle with camera edit badge
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val hasExistingAvatar = !profile?.avatarUrl.isNullOrBlank()

                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clickable(enabled = !isUploadingAvatar) {
                                if (hasExistingAvatar) {
                                    showAvatarOptionsSheet = true
                                } else {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        if (hasExistingAvatar) {
                            AsyncImage(
                                model = profile?.avatarUrl,
                                contentDescription = stringResource(R.string.edit_profile_change_photo),
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
                                    contentDescription = stringResource(R.string.edit_profile_change_photo),
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
                                contentDescription = stringResource(R.string.edit_profile_change_photo),
                                tint = colors.onPrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.edit_profile_change_photo),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryBlue,
                        modifier = Modifier.clickable(enabled = !isUploadingAvatar) {
                            if (hasExistingAvatar) {
                                showAvatarOptionsSheet = true
                            } else {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        }
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
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .bringIntoViewRequester(displayNameBringIntoView)
                        ) {
                            Text(
                                text = stringResource(R.string.edit_profile_display_name_label),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.secondaryText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            BasicTextField(
                                value = displayName,
                                onValueChange = { if (it.length <= 30) displayName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primaryText,
                                    textAlign = TextAlign.Start,
                                    textDirection = TextDirection.Content
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            coroutineScope.launch {
                                                displayNameBringIntoView.bringIntoView()
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    // Username Card (Renamed from "Handle" to "Username")
                    GroupedCard {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .bringIntoViewRequester(usernameBringIntoView)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.edit_profile_username_label),
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
                                            textAlign = TextAlign.Start,
                                            textDirection = TextDirection.Ltr
                                        ),
                                        cursorBrush = SolidColor(colors.primaryBlue),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    coroutineScope.launch {
                                                        usernameBringIntoView.bringIntoView()
                                                    }
                                                }
                                            }
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

                    // Bio Card (multiline, clean counter, max 160 chars, max 4 line breaks)
                    GroupedCard {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .bringIntoViewRequester(bioBringIntoView)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.edit_profile_bio_label),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.secondaryText
                                )

                                val charCount = bioText.length
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isLinesExceeded) {
                                        Text(
                                            text = stringResource(R.string.edit_profile_max_lines_exceeded),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.destructiveRed
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "•",
                                            fontSize = 11.5.sp,
                                            color = colors.destructiveRed
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = "$charCount/160",
                                        fontSize = 11.5.sp,
                                        fontWeight = if (charCount > 160 || isLinesExceeded) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (charCount <= 160 && !isLinesExceeded) colors.secondaryText else colors.destructiveRed
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            BasicTextField(
                                value = bioText,
                                onValueChange = {
                                    if (it.length <= 160) {
                                        bioText = it
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    fontSize = 15.sp,
                                    color = colors.primaryText,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Start,
                                    textDirection = TextDirection.Content
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            coroutineScope.launch {
                                                bioBringIntoView.bringIntoView()
                                            }
                                        }
                                    },
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (bioText.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.edit_profile_bio_placeholder),
                                                fontSize = 15.sp,
                                                color = colors.secondaryText,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Country/region Card (optional, explicit choice only)
                GroupedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCountryPicker = true }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pv2_country_label),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = countryDisplayName(countryCode)
                                    ?: stringResource(R.string.pv2_country_not_shown),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (countryCode == null) colors.secondaryText else colors.primaryText,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = colors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Generous bottom space to easily scroll past the fields above soft keyboard
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Image Crop Screen (in-app square crop with circular preview mask, pan & zoom)
        AnimatedVisibility(
            visible = selectedCropBytes != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val cropBytes = selectedCropBytes
            if (cropBytes != null) {
                ImageCropScreen(
                    imageBytes = cropBytes,
                    onCropDone = { jpegBytes, cacheUri ->
                        selectedCropBytes = null
                        if (onUploadAvatarBytes != null) {
                            onUploadAvatarBytes(jpegBytes, cacheUri)
                        } else {
                            localViewModel?.uploadProfileAvatar(jpegBytes, cacheUri)
                        }
                    },
                    onCancel = {
                        selectedCropBytes = null
                    }
                )
            }
        }
    }

    // Avatar Action Sheet (Choose photo, Remove photo, Cancel)
    if (showAvatarOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAvatarOptionsSheet = false },
            sheetState = sheetState,
            containerColor = colors.cardBackground,
            dragHandle = null,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Choose Photo Option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceMuted)
                        .clickable {
                            showAvatarOptionsSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.edit_profile_choose_photo),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText
                    )
                }

                // Remove Photo Option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceMuted)
                        .clickable {
                            showAvatarOptionsSheet = false
                            showRemovePhotoConfirm = true
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.edit_profile_remove_photo),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.destructiveRed
                    )
                }

                // Cancel Option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showAvatarOptionsSheet = false },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.edit_profile_cancel),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.secondaryText
                    )
                }
            }
        }
    }

    // Country/region picker
    if (showCountryPicker) {
        CountryPickerSheet(
            isOpen = showCountryPicker,
            selectedCode = countryCode,
            onSelect = { code ->
                countryCode = code
                onCountrySelected?.invoke(code)
            },
            onDismiss = { showCountryPicker = false }
        )
    }

    // Remove Photo Confirmation Dialog
    if (showRemovePhotoConfirm) {
        AlertDialog(
            onDismissRequest = { showRemovePhotoConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.edit_profile_remove_photo_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.edit_profile_remove_photo_desc),
                    fontSize = 14.sp,
                    color = colors.secondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemovePhotoConfirm = false
                        if (onRemoveAvatarClick != null) {
                            onRemoveAvatarClick()
                        } else {
                            localViewModel?.removeProfileAvatar()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.edit_profile_remove_action),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.destructiveRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePhotoConfirm = false }) {
                    Text(
                        text = stringResource(R.string.edit_profile_cancel),
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

    // Discard Unsaved Changes Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.edit_profile_discard_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.edit_profile_discard_desc),
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
                        text = stringResource(R.string.edit_profile_discard_action),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.destructiveRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(
                        text = stringResource(R.string.edit_profile_keep_editing),
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
