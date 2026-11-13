package app.buildx.chat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.buildx.chat.R
import app.buildx.chat.data.model.AccountInfo
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.data.model.resolveProfileBannerType
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.NameWithBadge
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.softShadow
import app.buildx.chat.util.AvatarImageProcessor
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
 * Build X ProfileScreen:
 * - Full-bleed atmospheric top banner (photo or muted looping video) dissolving smoothly into pageBackground
 * - Borderless transparent floating header icons (Back, and Edit Pencil on own profile / More Options on other)
 * - Clean identity hierarchy: Start-aligned 88dp Avatar | Display Name + VerifiedBadge -> @username -> Stats
 * - Zero note thought-bubbles or + badges on Profile (notes remain exclusively on DM Inbox)
 * - Own profile omits action buttons row (top-right pencil opens Edit Profile); Other profile shows Follow + Message
 */
@Composable
fun ProfileScreen(
    currentProfile: UserProfile?,
    targetUsername: String?,
    publicProfile: PublicProfile?,
    isLoading: Boolean,
    errorMessage: String?,
    activeNote: PublicNote? = null,
    onBackClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onOpenNoteSheet: () -> Unit = {},
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
    postsCount: Int = 0,
    isRefreshing: Boolean = false,
    onRefreshProfile: () -> Unit = {}
) {
    val colors = BuildXTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

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
    val bannerUrl = if (isOwnProfile) currentProfile?.bannerUrl else publicProfile?.bannerUrl
    val bannerType = resolveProfileBannerType(
        bannerUrl,
        if (isOwnProfile) currentProfile?.bannerType else publicProfile?.bannerType
    )
    val hasProfileBanner = !bannerUrl.isNullOrBlank() && bannerType != "none"

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

    val isFollowing = publicProfile?.isFollowing == true
    val followsYou = publicProfile?.followsYou == true
    val blockedByMe = !isOwnProfile && publicProfile?.blockedByMe == true
    val targetId = if (isOwnProfile) currentProfile?.id else publicProfile?.id

    val showUnavailable = !isOwnProfile && isProfileUnavailable
    val showError = !isOwnProfile && !isProfileUnavailable && publicProfile == null && errorMessage != null
    val showBannerOnScreen = hasProfileBanner && !isLoading && !showUnavailable && !showError && !blockedByMe

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

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var isAvatarZoomOpen by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val maxPullPx = with(density) { 128.dp.toPx() }
    val refreshTriggerPx = with(density) { 68.dp.toPx() }
    val holdRefreshPx = with(density) { 36.dp.toPx() }
    val pullOffsetAnimatable = remember { Animatable(0f) }
    var isPullDragging by remember { mutableStateOf(false) }

    val currentIsRefreshing by rememberUpdatedState(isRefreshing)
    val currentOnRefreshProfile by rememberUpdatedState(onRefreshProfile)
    val canPullToRefresh by rememberUpdatedState(!isLoading && !showUnavailable && !showError && !blockedByMe)

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            if (!isPullDragging && pullOffsetAnimatable.value < holdRefreshPx) {
                pullOffsetAnimatable.animateTo(
                    targetValue = holdRefreshPx,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } else if (!isPullDragging && pullOffsetAnimatable.value > 0f) {
            pullOffsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val pullNestedScrollConnection = remember(maxPullPx, refreshTriggerPx, holdRefreshPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || currentIsRefreshing || !canPullToRefresh) {
                    return Offset.Zero
                }
                val currentPull = pullOffsetAnimatable.value
                if (available.y < 0f && currentPull > 0f) {
                    val next = updateProfilePullOffsetPx(
                        currentPx = currentPull,
                        dragDeltaPx = available.y,
                        maxPullPx = maxPullPx
                    )
                    isPullDragging = next > 0f
                    coroutineScope.launch { pullOffsetAnimatable.snapTo(next) }
                    return Offset(0f, next - currentPull)
                }
                if (available.y > 0f && scrollState.value == 0) {
                    val next = updateProfilePullOffsetPx(
                        currentPx = currentPull,
                        dragDeltaPx = available.y,
                        maxPullPx = maxPullPx
                    )
                    if (next != currentPull || currentPull > 0f) {
                        isPullDragging = true
                        coroutineScope.launch { pullOffsetAnimatable.snapTo(next) }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isPullDragging && pullOffsetAnimatable.value <= 0f) {
                    return Velocity.Zero
                }
                isPullDragging = false
                val releasedPx = pullOffsetAnimatable.value
                if (releasedPx >= refreshTriggerPx && !currentIsRefreshing) {
                    currentOnRefreshProfile()
                    coroutineScope.launch {
                        pullOffsetAnimatable.animateTo(
                            targetValue = holdRefreshPx,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        if (!currentIsRefreshing && pullOffsetAnimatable.value > 0f) {
                            pullOffsetAnimatable.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                } else if (!currentIsRefreshing && releasedPx > 0f) {
                    coroutineScope.launch {
                        pullOffsetAnimatable.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .navigationBarsPadding()
    ) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(statusBarTopPadding + 62.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MarkLoader(
                            size = 36.dp,
                            isSpinning = true,
                            tint = colors.primaryBlue
                        )
                    }
                }
            }

            showUnavailable || showError -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(statusBarTopPadding + 62.dp))
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
            }

            blockedByMe -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(statusBarTopPadding + 62.dp))
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
            }

            else -> {
                val pullOffsetPx = pullOffsetAnimatable.value
                val pullOffsetDp = with(density) { pullOffsetPx.toDp() }
                val rawPullProgress = (pullOffsetPx / refreshTriggerPx).coerceIn(0f, 1.35f)
                val clampedPullProgress = rawPullProgress.coerceIn(0f, 1f)
                val showPullLoader = isRefreshing || pullOffsetPx > 2f
                val baseIdentityTopPadding = if (showBannerOnScreen) {
                    maxOf(128.dp, statusBarTopPadding + 86.dp)
                } else {
                    statusBarTopPadding + 62.dp
                }
                val loaderTopOffset = if (showBannerOnScreen) {
                    maxOf(statusBarTopPadding + 56.dp, baseIdentityTopPadding + pullOffsetDp - 40.dp)
                } else {
                    statusBarTopPadding + 54.dp + (pullOffsetDp - 36.dp).coerceAtLeast(0.dp) * 0.35f
                }
                val loaderAlpha = if (isRefreshing) {
                    1f
                } else {
                    (pullOffsetPx / (holdRefreshPx * 0.85f)).coerceIn(0f, 1f)
                }
                val canZoomAvatar = !avatarUrl.isNullOrBlank()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullNestedScrollConnection)
                        .verticalScroll(scrollState)
                        .padding(bottom = 32.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (showBannerOnScreen) {
                            ProfileHeroBanner(
                                bannerUrl = bannerUrl,
                                bannerType = bannerType,
                                isDarkTheme = colors.isDark,
                                pageBackground = colors.pageBackground,
                                contentScaleFactor = 1f + clampedPullProgress * 0.08f,
                                extraTopScrimAlpha = clampedPullProgress * 0.22f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(236.dp + pullOffsetDp)
                                    .align(Alignment.TopCenter),
                                contentDescription = stringResource(R.string.profile_banner_cd)
                            )
                        }

                        if (showPullLoader) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = loaderTopOffset)
                                    .alpha(loaderAlpha),
                                contentAlignment = Alignment.Center
                            ) {
                                MarkLoader(
                                    size = 28.dp,
                                    isSpinning = isRefreshing,
                                    manualRotationDeg = if (isRefreshing) null else rawPullProgress * 220f,
                                    tint = if (showBannerOnScreen) Color.White else colors.primaryBlue
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = baseIdentityTopPadding + pullOffsetDp)
                        ) {
                            // ---------------- Profile Identity Row ----------------
                            // Start-aligned 88dp Avatar | Display Name + VerifiedBadge -> @handle -> Stats
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileAvatar(
                                    avatarUrl = avatarUrl,
                                    displayName = displayName,
                                    size = 88.dp,
                                    ringWidth = if (showBannerOnScreen) 2.5.dp else 0.dp,
                                    ringColor = colors.pageBackground,
                                    onClick = when {
                                        canZoomAvatar -> { { isAvatarZoomOpen = true } }
                                        isOwnProfile -> {
                                            {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                        }
                                        else -> null
                                    },
                                    onLongClick = when {
                                        canZoomAvatar -> {
                                            {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isAvatarZoomOpen = true
                                            }
                                        }
                                        else -> null
                                    }
                                )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                NameWithBadge(
                                    name = displayName,
                                    isVerified = isVerified,
                                    badgeSize = 17.dp,
                                    badgeSpacing = 5.dp,
                                    color = colors.primaryText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    badgeContentDescription = stringResource(R.string.profile_verified_account)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "@$cleanHandle",
                                        color = colors.secondaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val densityScale = LocalDensity.current.fontScale
                                    val isCompactStats = maxWidth < 232.dp && densityScale >= 1.15f
                                    val statGap = when {
                                        isCompactStats -> 14.dp
                                        maxWidth < 240.dp -> 18.dp
                                        else -> 26.dp
                                    }
                                    val statLabelSize = if (isCompactStats) 12.sp else 13.5.sp
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(statGap)
                                    ) {
                                        ProfileStat(
                                            count = postsCount,
                                            label = stringResource(R.string.profile_posts),
                                            labelFontSize = statLabelSize,
                                            onClick = {}
                                        )
                                        ProfileStat(
                                            count = followersCount,
                                            label = stringResource(R.string.profile_followers),
                                            labelFontSize = statLabelSize,
                                            onClick = { targetId?.let { onFollowersListClick(it, 1) } }
                                        )
                                        ProfileStat(
                                            count = followingCount,
                                            label = stringResource(R.string.profile_following),
                                            labelFontSize = statLabelSize,
                                            onClick = { targetId?.let { onFollowersListClick(it, 0) } }
                                        )
                                    }
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

                        // ---------------- Action Buttons Row (Other Profile Only) ----------------
                        if (!isOwnProfile) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Button 1: Follow / Follow back / Following
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

                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                thickness = 1.dp,
                                color = colors.hairlineBorder
                            )
                        }
                    }
                }

                // ---------------- Content Area Below Divider ----------------
                if (postsCount > 0) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
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
                            if (isOwnProfile) {
                                Text(
                                    text = stringResource(R.string.profile_create_first_post_title),
                                    fontSize = 19.sp,
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
                            } else {
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

        // ---------------- Floating Borderless Top Bar Over Banner ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileFloatingIconButton(
                iconRes = R.drawable.ic_back,
                contentDescription = stringResource(R.string.pv2_back),
                hasBanner = showBannerOnScreen,
                isDarkTheme = colors.isDark,
                onClick = onBackClick
            )

            Box {
                if (isOwnProfile) {
                    ProfileFloatingIconButton(
                        iconRes = R.drawable.ic_pencil,
                        contentDescription = stringResource(R.string.edit_profile_title),
                        hasBanner = showBannerOnScreen,
                        isDarkTheme = colors.isDark,
                        onClick = onEditProfileClick
                    )
                } else {
                    ProfileFloatingIconButton(
                        iconRes = R.drawable.ic_more_horizontal,
                        contentDescription = stringResource(R.string.pv2_more_options),
                        hasBanner = showBannerOnScreen,
                        isDarkTheme = colors.isDark,
                        onClick = { showMenu = true }
                    )
                }

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

        // Fullscreen Avatar Zoom Overlay (long-press or tap existing avatar; zero bottom buttons)
        ProfileAvatarZoomOverlay(
            visible = isAvatarZoomOpen && !avatarUrl.isNullOrBlank(),
            avatarUrl = avatarUrl,
            displayName = displayName,
            onDismiss = { isAvatarZoomOpen = false }
        )
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
 * Borderless floating circular icon button used on the Profile header.
 * Completely transparent without a banner; renders a subtle dark scrim over banners.
 */
@Composable
internal fun ProfileFloatingIconButton(
    iconRes: Int,
    contentDescription: String,
    hasBanner: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mirrorIcon = isRtl && (iconRes == R.drawable.ic_back || iconRes == R.drawable.ic_chevron_right)
    val bgColor = if (hasBanner) {
        Color.Black.copy(alpha = if (isDarkTheme) 0.28f else 0.20f)
    } else {
        Color.Transparent
    }
    val tintColor = if (hasBanner) Color.White else colors.primaryText

    Box(
        modifier = modifier
            .size(42.dp)
            .pressScaleClickable(onClick = onClick)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tintColor,
            modifier = Modifier
                .size(24.dp)
                .scale(scaleX = if (mirrorIcon) -1f else 1f, scaleY = 1f)
        )
    }
}

/**
 * Full-bleed atmospheric banner supporting photo (CenterCrop) or muted looping video,
 * overlaid with an optional top status-bar protection scrim and a smooth multi-stop
 * gradient dissolve into [pageBackground].
 */
@Composable
internal fun ProfileHeroBanner(
    bannerUrl: String?,
    bannerType: String,
    isDarkTheme: Boolean,
    pageBackground: Color,
    modifier: Modifier = Modifier,
    topScrimEnabled: Boolean = true,
    contentScaleFactor: Float = 1f,
    extraTopScrimAlpha: Float = 0f,
    contentDescription: String? = null
) {
    val resolvedType = resolveProfileBannerType(bannerUrl, bannerType)

    Box(modifier = modifier.clipToBounds()) {
        if (!bannerUrl.isNullOrBlank()) {
            val mediaModifier = Modifier
                .fillMaxSize()
                .scale(contentScaleFactor)
            if (resolvedType == "video") {
                LoopingBannerVideoPlayer(
                    videoUrl = bannerUrl,
                    modifier = mediaModifier
                )
            } else {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = mediaModifier
                )
            }
        }

        if (topScrimEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(
                                    alpha = ((if (isDarkTheme) 0.40f else 0.24f) + extraTopScrimAlpha)
                                        .coerceIn(0f, 0.72f)
                                ),
                                0.55f to Color.Black.copy(
                                    alpha = ((if (isDarkTheme) 0.14f else 0.08f) + extraTopScrimAlpha * 0.7f)
                                        .coerceIn(0f, 0.55f)
                                ),
                                1.00f to Color.Transparent
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.32f to Color.Transparent,
                            0.50f to pageBackground.copy(alpha = 0.12f),
                            0.66f to pageBackground.copy(alpha = 0.42f),
                            0.80f to pageBackground.copy(alpha = 0.76f),
                            0.92f to pageBackground.copy(alpha = 0.95f),
                            1.00f to pageBackground
                        )
                    )
                )
        )
    }
}

/**
 * Pure aspect-preserving CenterCrop scale factors for TextureView transforms.
 */
internal fun computeBannerVideoCenterCropScale(
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int
): Pair<Float, Float> {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
        return 1f to 1f
    }
    val scaleX = viewWidth.toFloat() / videoWidth.toFloat()
    val scaleY = viewHeight.toFloat() / videoHeight.toFloat()
    val maxScale = maxOf(scaleX, scaleY)
    val scaledW = maxScale * videoWidth.toFloat()
    val scaledH = maxScale * videoHeight.toFloat()
    return (scaledW / viewWidth.toFloat()) to (scaledH / viewHeight.toFloat())
}

/**
 * Lifecycle-aware muted looping banner video player backed by TextureView + MediaPlayer,
 * with CenterCrop Matrix scaling and async local disk caching.
 */
@Composable
internal fun LoopingBannerVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resolvedSource by remember(videoUrl) { mutableStateOf<String?>(null) }
    var activeSurface by remember(videoUrl) { mutableStateOf<Surface?>(null) }
    var textureViewRef by remember(videoUrl) { mutableStateOf<TextureView?>(null) }
    var mediaPlayerRef by remember(videoUrl) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(videoUrl) { mutableStateOf(false) }
    var viewSize by remember(videoUrl) { mutableStateOf(0 to 0) }
    var videoSize by remember(videoUrl) { mutableStateOf(0 to 0) }

    fun applyTransform() {
        val tv = textureViewRef ?: return
        val (vw, vh) = viewSize
        val (vidW, vidH) = videoSize
        if (vw <= 0 || vh <= 0 || vidW <= 0 || vidH <= 0) return
        val (sx, sy) = computeBannerVideoCenterCropScale(vw, vh, vidW, vidH)
        val matrix = Matrix().apply {
            setScale(sx, sy, vw / 2f, vh / 2f)
        }
        tv.setTransform(matrix)
    }

    LaunchedEffect(videoUrl) {
        resolvedSource = null
        val source = withContext(Dispatchers.IO) {
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                val cacheFile = AvatarImageProcessor.getBannerVideoCacheFile(context.cacheDir, videoUrl)
                if (!cacheFile.exists() || cacheFile.length() == 0L) {
                    val partFile = File(context.cacheDir, "${cacheFile.name}.part")
                    try {
                        val conn = (URL(videoUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 10_000
                            readTimeout = 15_000
                            instanceFollowRedirects = true
                        }
                        try {
                            if (conn.responseCode in 200..299) {
                                conn.inputStream.use { input ->
                                    FileOutputStream(partFile).use { out ->
                                        input.copyTo(out)
                                        out.flush()
                                    }
                                }
                                if (partFile.length() > 0L) {
                                    partFile.renameTo(cacheFile)
                                }
                            }
                        } finally {
                            conn.disconnect()
                        }
                    } catch (_: Throwable) {
                        runCatching { partFile.delete() }
                    }
                }
                if (cacheFile.exists() && cacheFile.length() > 0L) {
                    cacheFile.absolutePath
                } else {
                    videoUrl
                }
            } else {
                videoUrl
            }
        }
        resolvedSource = source
    }

    DisposableEffect(resolvedSource, lifecycleOwner) {
        val src = resolvedSource
        if (src.isNullOrBlank()) {
            onDispose {}
        } else {
            val mp = MediaPlayer()
            mediaPlayerRef = mp
            isPrepared = false

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                        if (isPrepared) {
                            runCatching { if (!mp.isPlaying) mp.start() }
                        }
                    }
                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        runCatching { if (mp.isPlaying) mp.pause() }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            try {
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                activeSurface?.takeIf { it.isValid }?.let { mp.setSurface(it) }
                if (src.startsWith("content://") || src.startsWith("file://")) {
                    mp.setDataSource(context, Uri.parse(src))
                } else {
                    mp.setDataSource(src)
                }
                mp.setOnVideoSizeChangedListener { _, width, height ->
                    videoSize = width to height
                    applyTransform()
                }
                mp.setOnPreparedListener { prepared ->
                    isPrepared = true
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        runCatching { prepared.start() }
                    }
                }
                mp.setOnErrorListener { _, _, _ -> true }
                mp.prepareAsync()
            } catch (_: Throwable) {
                isPrepared = false
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mediaPlayerRef = null
                isPrepared = false
                runCatching {
                    mp.setSurface(null)
                    if (mp.isPlaying) mp.stop()
                    mp.reset()
                }
                runCatching { mp.release() }
            }
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            runCatching { activeSurface?.release() }
            activeSurface = null
            textureViewRef = null
        }
    }

    Box(modifier = modifier.background(Color(0xFF14181F))) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    textureViewRef = this
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            viewSize = width to height
                            val newSurface = Surface(surfaceTexture)
                            activeSurface?.let { old -> runCatching { old.release() } }
                            activeSurface = newSurface
                            runCatching {
                                mediaPlayerRef?.setSurface(newSurface)
                                if (isPrepared && mediaPlayerRef?.isPlaying == false &&
                                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                ) {
                                    mediaPlayerRef?.start()
                                }
                            }
                            applyTransform()
                        }

                        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            viewSize = width to height
                            applyTransform()
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            runCatching { mediaPlayerRef?.setSurface(null) }
                            runCatching { activeSurface?.release() }
                            activeSurface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
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
 * Computes the next rubber-banded pull offset in pixels for ProfileScreen pull-to-refresh.
 * Downward drags apply [damping] up to [maxPullPx]; upward drags collapse 1:1 down to zero.
 */
internal fun updateProfilePullOffsetPx(
    currentPx: Float,
    dragDeltaPx: Float,
    maxPullPx: Float,
    damping: Float = 0.46f
): Float {
    if (maxPullPx <= 0f) return 0f
    return if (dragDeltaPx >= 0f) {
        (currentPx + dragDeltaPx * damping).coerceIn(0f, maxPullPx)
    } else {
        (currentPx + dragDeltaPx).coerceIn(0f, maxPullPx)
    }
}

/**
 * Full-screen avatar zoom overlay opened by tapping or long-pressing an existing profile avatar.
 * Renders the blurred backdrop behind a centered 268dp circular avatar with spring scaling
 * and zero bottom action buttons. Tap anywhere or press Back to dismiss.
 */
@Composable
private fun ProfileAvatarZoomOverlay(
    visible: Boolean,
    avatarUrl: String?,
    displayName: String,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible && !avatarUrl.isNullOrBlank(),
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 160)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
    ) {
        val avatarScale by transition.animateFloat(
            transitionSpec = {
                if (targetState == EnterExitState.PostExit) {
                    tween(durationMillis = 160)
                } else {
                    spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                }
            },
            label = "avatarZoomScale"
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> 0.42f
                EnterExitState.Visible -> 1f
                EnterExitState.PostExit -> 0.82f
            }
        }
        val dismissalInteraction = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = dismissalInteraction,
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(56.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
            )
            if (!avatarUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .scale(avatarScale)
                        .size(268.dp)
                        .softShadow(
                            color = Color.Black.copy(alpha = 0.36f),
                            cornerRadius = 134.dp,
                            blurRadius = 28.dp,
                            offsetY = 12.dp
                        )
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                ) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Avatar with fallback, optional ring separator over hero banner, tap and long-press actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp,
    ringWidth: Dp = 0.dp,
    ringColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val ringModifier = if (ringWidth > 0.dp) {
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(ringColor)
            .padding(ringWidth)
    } else {
        Modifier.size(size)
    }
    val innerModifier = Modifier
        .fillMaxSize()
        .clip(CircleShape)
        .then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                )
            } else {
                Modifier
            }
        )

    Box(modifier = ringModifier) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = innerModifier.border(1.dp, colors.subtleSurfaceBorder, CircleShape)
            )
        } else {
            Box(
                modifier = innerModifier
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
}

/**
 * Single Stat Column (count on top, label below).
 */
@Composable
private fun ProfileStat(
    count: Int,
    label: String,
    labelFontSize: TextUnit = 13.5.sp,
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
            fontSize = labelFontSize,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
