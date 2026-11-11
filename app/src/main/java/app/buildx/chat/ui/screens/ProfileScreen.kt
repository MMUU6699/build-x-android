package app.buildx.chat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.AccountInfo
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

/**
 * Format numbers cleanly:
 * 7_600_000 -> "7.6M" (never "7.6MM")
 * 10_500 -> "10.5K"
 * 450 -> "450"
 */
fun formatNumber(count: Int): String {
    if (count < 1000) return count.toString()
    if (count < 1_000_000) {
        val thousands = count / 1000.0
        val formatted = String.format(Locale.US, "%.1f", thousands)
        return if (formatted.endsWith(".0")) "${thousands.toInt()}K" else "${formatted}K"
    }
    val millions = count / 1_000_000.0
    val formatted = String.format(Locale.US, "%.1f", millions)
    return if (formatted.endsWith(".0")) "${millions.toInt()}M" else "${formatted}M"
}

/**
 * Redesigned ProfileScreen matching Build X design philosophy:
 * - Clean top header bar with handle and VerifiedBadge (only in top bar)
 * - Profile identity row: Avatar (86dp) + floating note thought-bubble with 2 tail dots + bottom-end + badge
 * - Name + 3 side-by-side stats (posts, followers, following)
 * - Bio with highlighted clickable links (no "Joined ..." row)
 * - Two equal-width action buttons: Edit profile | Share profile (Own) or Follow | Message (Other)
 * - Content tab bar with 3x3 grid icon and active underline
 * - Matching empty states for Own and Other profiles
 */
@Composable
fun ProfileScreen(
    currentProfile: UserProfile?,
    targetUsername: String?,
    publicProfile: PublicProfile?,
    isLoading: Boolean,
    errorMessage: String?,
    activeNote: PublicNote?,
    onBackClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onOpenNoteSheet: () -> Unit,
    onReviewUsernameClick: () -> Unit,
    onFollowClick: (String) -> Unit,
    onFollowingClick: (String) -> Unit,
    onFollowersListClick: (String, Int) -> Unit,
    onRetry: () -> Unit,
    isSubscribedToNotes: Boolean = publicProfile?.noteSubscribed == true,
    onToggleNotification: (String) -> Unit = {},
    isFollowPending: Boolean = false,
    isProfileUnavailable: Boolean = false,
    accountInfo: AccountInfo? = null,
    isAccountInfoLoading: Boolean = false,
    accountInfoError: String? = null,
    isSubmittingReport: Boolean = false,
    reportError: String? = null,
    isBlocking: Boolean = false,
    onLoadAccountInfo: (String) -> Unit = {},
    onSubmitReport: (String, ReportReason, String?) -> Unit = { _, _, _ -> },
    onBlockUser: (String) -> Unit = {},
    onUnblockUser: (String) -> Unit = {},
    onShareProfile: (String) -> Unit = {},
    onCopyLink: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenVerification: () -> Unit = {},
    onOpenBlockedAccounts: () -> Unit = {},
    onMessageClick: () -> Unit = {},
    onAvatarPicked: ((ByteArray, Uri?) -> Unit)? = null,
    postsCount: Int = 0
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isOwnProfile = remember(currentProfile, targetUsername) {
        targetUsername == null ||
            targetUsername.equals(currentProfile?.username, ignoreCase = true) ||
            targetUsername.equals(currentProfile?.handle, ignoreCase = true)
    }

    val displayName = if (isOwnProfile) {
        currentProfile?.displayName ?: "User"
    } else {
        publicProfile?.displayName ?: targetUsername ?: "User"
    }
    val username = if (isOwnProfile) {
        currentProfile?.effectiveUsername ?: "user"
    } else {
        publicProfile?.username ?: targetUsername ?: "user"
    }
    val cleanHandle = username.removePrefix("@")

    val bioText = if (isOwnProfile) currentProfile?.bio else publicProfile?.bio
    val avatarUrl = if (isOwnProfile) currentProfile?.avatarUrl else publicProfile?.avatarUrl
    val isVerified = if (isOwnProfile) {
        currentProfile?.isVerified == true
    } else {
        publicProfile?.isVerified == true
    }
    val followingCount = if (isOwnProfile) {
        currentProfile?.followingCount ?: 0
    } else {
        publicProfile?.followingCount ?: 0
    }
    val followersCount = if (isOwnProfile) {
        currentProfile?.followersCount ?: 0
    } else {
        publicProfile?.followersCount ?: 0
    }
    val currentNote = if (isOwnProfile) activeNote else publicProfile?.activeNote

    val isFollowing = publicProfile?.isFollowing == true
    val followsYou = publicProfile?.followsYou == true
    val blockedByMe = !isOwnProfile && publicProfile?.blockedByMe == true
    val targetId = if (isOwnProfile) currentProfile?.id else publicProfile?.id

    var showMenu by remember { mutableStateOf(false) }
    var showUnfollowPopup by remember { mutableStateOf(false) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }

    // In-app Avatar picker for Own Profile
    var profileCropBytes by remember { mutableStateOf<ByteArray?>(null) }
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
                        profileCropBytes = bytes
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ---------------- Top Header Bar ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = stringResource(R.string.pv2_back),
                        onClick = onBackClick
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isOwnProfile) "@$cleanHandle" else cleanHandle,
                                color = colors.primaryText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isVerified) {
                                Spacer(modifier = Modifier.width(5.dp))
                                VerifiedBadge(
                                    size = 16.dp,
                                    contentDescription = stringResource(R.string.profile_verified_account)
                                )
                            }
                        }
                    }
                }

                Box {
                    if (isOwnProfile) {
                        HeaderCircleButton(
                            iconRes = R.drawable.ic_menu,
                            contentDescription = stringResource(R.string.settings_title),
                            onClick = onOpenSettings
                        )
                    } else {
                        HeaderCircleButton(
                            iconRes = R.drawable.ic_more_horizontal,
                            contentDescription = stringResource(R.string.pv2_more_options),
                            onClick = { showMenu = true }
                        )
                    }

                    // Anchored dropdown menu for Other Profile
                    if (!isOwnProfile && showMenu) {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .background(colors.cardBackground)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.profile_share_profile),
                                        fontSize = 14.sp,
                                        color = colors.primaryText
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onShareProfile(username)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.profile_copy_username),
                                        fontSize = 14.sp,
                                        color = colors.primaryText
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("username", "@$cleanHandle"))
                                    onCopyLink(cleanHandle)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.pv2_report),
                                        fontSize = 14.sp,
                                        color = colors.primaryText
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showReportSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.pv2_block),
                                        fontSize = 14.sp,
                                        color = Color(0xFFE53935)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showBlockDialog = true
                                }
                            )
                        }
                    }
                }
            }

            val showUnavailable = !isOwnProfile && isProfileUnavailable
            val showError = !isOwnProfile && !isProfileUnavailable && publicProfile == null && errorMessage != null

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.primaryBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                showUnavailable || showError -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = stringResource(R.string.profile_user_not_found_title),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.profile_user_not_found_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: stringResource(R.string.profile_user_not_found_subtitle),
                            fontSize = 14.sp,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                blockedByMe -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_block),
                            contentDescription = stringResource(R.string.pv2_blocked_by_me_title),
                            tint = colors.secondaryText,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.pv2_blocked_by_me_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.pv2_blocked_by_me_subtitle),
                            fontSize = 14.sp,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceMuted)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(12.dp))
                                .clickable { targetId?.let(onUnblockUser) }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.pv2_unblock),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText
                            )
                        }
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // ---------------- Profile Identity Row ----------------
                    // Avatar + Floating Note Thought-Bubble on Start | Name + Stats on End
                    val hasNoteBubble = (currentNote != null && currentNote.content.isNotBlank()) || isOwnProfile

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Start: Avatar Box + Floating Thought-Bubble + + Badge
                        Box(
                            modifier = Modifier.width(100.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            // Avatar Circle (86.dp) with + badge anchor
                            Box(
                                modifier = Modifier
                                    .padding(top = if (hasNoteBubble) 18.dp else 0.dp)
                                    .size(86.dp)
                            ) {
                                ProfileAvatar(
                                    avatarUrl = avatarUrl,
                                    displayName = displayName,
                                    size = 86.dp,
                                    onClick = {
                                        if (isOwnProfile) {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    }
                                )

                                // Bottom-End + Badge on Own Profile
                                if (isOwnProfile) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = (-2).dp, y = (-2).dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(colors.pageBackground)
                                            .padding(2.5.dp)
                                            .clip(CircleShape)
                                            .background(colors.primaryText)
                                            .clickable(onClick = onOpenNoteSheet),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_plus),
                                            contentDescription = stringResource(R.string.profile_leave_a_note),
                                            tint = colors.pageBackground,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Floating Note Thought-Bubble (Overlapping top of avatar)
                            if (hasNoteBubble) {
                                ThoughtBubbleWithTail(
                                    noteContent = currentNote?.content,
                                    isOwnProfile = isOwnProfile,
                                    onClick = { if (isOwnProfile) onOpenNoteSheet() },
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // End Column — Display Name & Stats Row
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = displayName,
                                color = colors.primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(28.dp)
                            ) {
                                ProfileStat(
                                    count = postsCount,
                                    label = stringResource(R.string.profile_posts),
                                    onClick = {}
                                )
                                ProfileStat(
                                    count = followersCount,
                                    label = stringResource(R.string.profile_followers),
                                    onClick = { targetId?.let { onFollowersListClick(it, 1) } }
                                )
                                ProfileStat(
                                    count = followingCount,
                                    label = stringResource(R.string.profile_following),
                                    onClick = { targetId?.let { onFollowersListClick(it, 0) } }
                                )
                            }
                        }
                    }

                    // ---------------- Bio & Clickable Links ----------------
                    if (!bioText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ClickableBioText(
                            bio = bioText,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    // ---------------- Action Buttons Row (Two Equal-Width Buttons) ----------------
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isOwnProfile) {
                            // Button 1: Edit profile
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceMuted)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                                    .pressScaleClickable(onClick = onEditProfileClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_edit),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                            }

                            // Button 2: Share profile
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceMuted)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                                    .pressScaleClickable(onClick = { onShareProfile(username) }),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_share_profile),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                            }
                        } else {
                            // Button 1: Follow / Follow back / Following ▾
                            if (!isFollowing) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .softShadow(
                                            color = colors.primaryBlue.copy(alpha = 0.22f),
                                            cornerRadius = 14.dp,
                                            blurRadius = 8.dp,
                                            offsetY = 2.dp
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.primaryBlue)
                                        .pressScaleClickable(onClick = { targetId?.let(onFollowClick) }),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (followsYou) stringResource(R.string.profile_follow_back) else stringResource(R.string.profile_follow),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimaryBlue
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surfaceMuted)
                                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                                        .pressScaleClickable(onClick = { showUnfollowPopup = true }),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.profile_following_status),
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primaryText
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_caret_down),
                                            contentDescription = null,
                                            tint = colors.primaryText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Button 2: Message
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.surfaceMuted)
                                    .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(14.dp))
                                    .pressScaleClickable(onClick = onMessageClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_message),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                            }
                        }
                    }

                    // ---------------- Review Username Banner (Own Profile) ----------------
                    if (isOwnProfile && currentProfile?.usernameReviewedAt == null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceMuted)
                                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.profile_username_review_title),
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = stringResource(R.string.profile_username_review_subtitle),
                                    color = colors.secondaryText,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.primaryBlue)
                                    .pressScaleClickable(onClick = onReviewUsernameClick)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_review),
                                    color = colors.onPrimaryBlue,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // ---------------- Content Tab Bar (3x3 Grid Tab + Active Indicator) ----------------
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        // 3x3 Grid Tab (120dp wide on start side)
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_apps_grid),
                                    contentDescription = stringResource(R.string.profile_posts),
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(2.dp)
                                        .background(colors.primaryText)
                                )
                            }
                        }

                        // Full-width hairline divider
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 1.dp,
                            color = colors.hairlineBorder
                        )
                    }

                    // ---------------- Content Area Below Divider ----------------
                    if (postsCount > 0) {
                        // Placeholder for 3-column media grid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.profile_no_media),
                                color = colors.secondaryText,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        // Clean Empty States matching Target UI
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 56.dp, horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isOwnProfile) {
                                // Own Profile Empty State
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_create_first_post_title),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(R.string.profile_create_first_post_subtitle),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.secondaryText,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.primaryBlue)
                                            .pressScaleClickable(onClick = onOpenNoteSheet)
                                            .padding(horizontal = 24.dp, vertical = 11.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.profile_create_action),
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onPrimaryBlue
                                        )
                                    }
                                }
                            } else {
                                // Other Profile Empty State
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .border(1.5.dp, colors.primaryText.copy(alpha = 0.75f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_camera),
                                            contentDescription = null,
                                            tint = colors.primaryText.copy(alpha = 0.85f),
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.profile_no_posts_yet),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Image Crop Screen Overlay (when avatar is picked from ProfileScreen)
        AnimatedVisibility(
            visible = profileCropBytes != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val cropBytes = profileCropBytes
            if (cropBytes != null) {
                ImageCropScreen(
                    imageBytes = cropBytes,
                    onCropDone = { jpegBytes, cacheUri ->
                        profileCropBytes = null
                        onAvatarPicked?.invoke(jpegBytes, cacheUri)
                    },
                    onCancel = {
                        profileCropBytes = null
                    }
                )
            }
        }
    }

    // Unfollow Confirmation Popup
    if (showUnfollowPopup && targetId != null) {
        UnfollowPopup(
            username = username,
            onUnfollow = { onFollowingClick(targetId) },
            onDismiss = { showUnfollowPopup = false }
        )
    }

    // Block Confirmation Dialog
    if (showBlockDialog && targetId != null) {
        BlockAccountDialog(
            username = username,
            isBlocking = isBlocking,
            onConfirm = { onBlockUser(targetId) },
            onDismiss = { showBlockDialog = false }
        )
    }

    // Report Sheet
    if (showReportSheet && targetId != null) {
        ReportAccountSheet(
            username = username,
            isSubmitting = isSubmittingReport,
            errorMessage = reportError,
            onSubmit = { reason, details ->
                onSubmitReport(targetId, reason, details)
            },
            onBlock = { onBlockUser(targetId) },
            onDismiss = { showReportSheet = false }
        )
    }

    // About Account Sheet
    if (showAboutSheet && targetId != null) {
        AboutAccountSheet(
            accountInfo = accountInfo,
            isLoading = isAccountInfoLoading,
            errorMessage = accountInfoError,
            onRetry = { onLoadAccountInfo(targetId) },
            onDismiss = { showAboutSheet = false }
        )
    }
}

/**
 * Thought-bubble card overlapping the top-start of the avatar with two tail dots.
 */
@Composable
private fun ThoughtBubbleWithTail(
    noteContent: String?,
    isOwnProfile: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val hasActiveNote = !noteContent.isNullOrBlank()

    Column(
        modifier = modifier
            .offset(y = (-18).dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Thought Bubble Card Surface
        Box(
            modifier = Modifier
                .widthIn(min = 82.dp, max = 116.dp)
                .softShadow(
                    color = colors.headerHaloShadowColor,
                    cornerRadius = 18.dp,
                    blurRadius = 14.dp,
                    offsetY = 3.dp
                )
                .clip(RoundedCornerShape(18.dp))
                .background(colors.cardBackground)
                .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasActiveNote) {
                Text(
                    text = noteContent!!,
                    color = colors.primaryText,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = stringResource(R.string.profile_just_curious_prompt),
                    color = colors.secondaryText,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        // Thought-Bubble Tail Dots (resting on top edge of avatar)
        Row(
            modifier = Modifier
                .padding(start = 18.dp)
                .offset(y = (-2).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.6.dp, colors.subtleSurfaceBorder, CircleShape)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .offset(y = 2.dp)
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .border(0.6.dp, colors.subtleSurfaceBorder, CircleShape)
            )
        }
    }
}

/**
 * Clickable Bio Text highlighting URLs in primaryBlue with Intent.ACTION_VIEW clickability.
 */
@Composable
private fun ClickableBioText(
    bio: String,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val context = LocalContext.current
    val urlPattern = remember { Pattern.compile("https?://\\S+") }
    val matcher = urlPattern.matcher(bio)

    val annotated = buildAnnotatedString {
        var lastIdx = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val url = matcher.group()

            if (start > lastIdx) {
                append(bio.substring(lastIdx, start))
            }

            pushLink(LinkAnnotation.Url(url))
            withStyle(
                SpanStyle(
                    color = colors.primaryBlue,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.None
                )
            ) {
                append(url)
            }
            pop()
            lastIdx = end
        }
        if (lastIdx < bio.length) {
            append(bio.substring(lastIdx))
        }
    }

    Text(
        text = annotated,
        color = colors.primaryText,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        modifier = modifier
    )
}

/**
 * Avatar with fallback and optional onClick.
 */
@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    displayName: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val mod = Modifier
        .size(size)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = mod.border(1.dp, colors.subtleSurfaceBorder, CircleShape)
        )
    } else {
        Box(
            modifier = mod
                .background(colors.surfaceMuted)
                .border(1.dp, colors.subtleSurfaceBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_user),
                contentDescription = displayName,
                tint = colors.secondaryText,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/**
 * Single Stat Column (count on top, label below).
 */
@Composable
private fun ProfileStat(
    count: Int,
    label: String,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = formatNumber(count),
            color = colors.primaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}
