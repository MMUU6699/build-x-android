package app.buildx.chat.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import coil.compose.AsyncImage

/**
 * P1 (Own Profile) & P2 (Other User Profile) Screen.
 * Layout adheres strictly to reference screenshots:
 * - Top header: Back arrow, pencil edit icon (own) or bell icon (other), share button
 * - Main row: Display name, @username with blue verified badge (16dp), Following and Followers stats
 * - Top right: 84dp Avatar with floating Public Note speech bubble and optional "+" badge
 * - Multiline bio with auto-linked URLs
 * - Action button (Follow / Follow back / Following with Unfollow popup)
 * - Review username banner (for own profile if username_reviewed_at is null)
 * - Media / Content section placeholder
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
    onRetry: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val context = LocalContext.current

    val isOwnProfile = remember(currentProfile, targetUsername) {
        targetUsername == null || 
        targetUsername.equals(currentProfile?.username, ignoreCase = true) ||
        targetUsername.equals(currentProfile?.handle, ignoreCase = true)
    }

    var showUnfollowDialog by remember { mutableStateOf(false) }

    // Resolve profile details
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

    val bioText = if (isOwnProfile) {
        currentProfile?.bio
    } else {
        publicProfile?.bio
    }

    val avatarUrl = if (isOwnProfile) {
        currentProfile?.avatarUrl
    } else {
        publicProfile?.avatarUrl
    }

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

    val currentNote = if (isOwnProfile) {
        activeNote
    } else {
        publicProfile?.activeNote
    }

    val isFollowing = publicProfile?.isFollowing == true
    val followsYou = publicProfile?.followsYou == true

    val isNameRtl = remember(displayName, strings.isRtl) {
        isRtlText(displayName, defaultRtl = strings.isRtl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar: Back, Action Icon (Pencil or Bell), Share Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderCircleButton(
                    iconRes = R.drawable.ic_back,
                    contentDescription = strings.cancelButton,
                    onClick = onBackClick
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isOwnProfile) {
                        HeaderCircleButton(
                            iconRes = R.drawable.ic_pencil_edit,
                            contentDescription = strings.editProfileTitle,
                            onClick = onEditProfileClick
                        )
                    } else {
                        HeaderCircleButton(
                            iconRes = R.drawable.ic_bell,
                            contentDescription = strings.notificationsTitle,
                            onClick = {
                                // Toggle notifications or show info
                            }
                        )
                    }

                    HeaderCircleButton(
                        iconRes = R.drawable.ic_share,
                        contentDescription = strings.shareButton,
                        onClick = {
                            val shareText = String.format(strings.shareProfileText, username, username)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, strings.shareButton))
                        }
                    )
                }
            }

            if (isLoading) {
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
            } else if (errorMessage != null && !isOwnProfile && publicProfile == null) {
                // User not found / error state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user),
                        contentDescription = strings.userNotFoundTitle,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.userNotFoundTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.userNotFoundSubtitle,
                        fontSize = 14.sp,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(onClick = onRetry) {
                        Text(
                            text = strings.retryButton,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primaryBlue
                        )
                    }
                }
            } else {
                // Main Profile Info Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left block (Right in RTL): Name, @username + verified, Following & Followers
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            // Display Name
                            CompositionLocalProvider(
                                LocalLayoutDirection provides if (isNameRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                            ) {
                                Text(
                                    text = displayName,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = androidx.compose.ui.text.TextStyle(
                                        textDirection = if (isNameRtl) TextDirection.Rtl else TextDirection.Ltr
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // @username + Verified badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "@$username",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.secondaryText
                                    )
                                }
                                if (isVerified) {
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_verified_badge),
                                        contentDescription = strings.verifiedAccountDescription,
                                        tint = colors.primaryBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Stats: Following and Followers
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier.clickable {
                                        val targetId = if (isOwnProfile) currentProfile?.id else publicProfile?.id
                                        if (targetId != null) onFollowersListClick(targetId, 0)
                                    }
                                ) {
                                    Text(
                                        text = formatNumber(followingCount),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = strings.followingLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.secondaryText
                                    )
                                }

                                Column(
                                    modifier = Modifier.clickable {
                                        val targetId = if (isOwnProfile) currentProfile?.id else publicProfile?.id
                                        if (targetId != null) onFollowersListClick(targetId, 1)
                                    }
                                ) {
                                    Text(
                                        text = formatNumber(followersCount),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText
                                    )
                                    Text(
                                        text = strings.followersLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.secondaryText
                                    )
                                }
                            }
                        }

                        // Right block (Left in RTL): 84dp Avatar with Public Note bubble
                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Floating Public Note speech bubble (if active) or prompt (if own profile)
                                if (currentNote != null && currentNote.content.isNotBlank()) {
                                    NoteBubble(
                                        content = currentNote.content,
                                        onClick = {
                                            if (isOwnProfile) onOpenNoteSheet()
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                } else if (isOwnProfile) {
                                    NotePromptBubble(
                                        prompt = strings.spillTheTeaPrompt,
                                        onClick = onOpenNoteSheet
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                // 84dp Circular Avatar
                                Box(
                                    contentAlignment = Alignment.BottomEnd,
                                    modifier = Modifier.size(84.dp)
                                ) {
                                    if (!avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = avatarUrl,
                                            contentDescription = displayName,
                                            modifier = Modifier
                                                .size(84.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, colors.pageBackground, CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(84.dp)
                                                .clip(CircleShape)
                                                .background(colors.surfaceMuted),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_user),
                                                contentDescription = displayName,
                                                tint = colors.secondaryText,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }

                                    // Plus button for own profile if no note exists
                                    if (isOwnProfile && currentNote == null) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(colors.primaryBlue)
                                                .border(2.dp, colors.pageBackground, CircleShape)
                                                .clickable { onOpenNoteSheet() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_plus),
                                                contentDescription = strings.leaveANote,
                                                tint = colors.onPrimaryBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Button (Follow / Follow Back / Following) for Other Profile
                    if (!isOwnProfile) {
                        Spacer(modifier = Modifier.height(18.dp))
                        val targetId = publicProfile?.id ?: ""
                        if (isFollowing) {
                            // "Following" button with dropdown caret
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(colors.surfaceMuted)
                                    .clickable { showUnfollowDialog = true },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.followingStatus,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primaryText
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_caret_down),
                                    contentDescription = null,
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            // "Follow" or "Follow back" CTA button
                            val buttonText = if (followsYou) strings.followBackAction else strings.followAction
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(colors.primaryBlue)
                                    .clickable { onFollowClick(targetId) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = buttonText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryBlue
                                )
                            }
                        }
                    }

                    // Bio Text Block
                    if (!bioText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val isBioRtl = isRtlText(bioText, defaultRtl = strings.isRtl)
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (isBioRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            Text(
                                text = bioText,
                                fontSize = 14.5.sp,
                                color = colors.primaryText,
                                lineHeight = 21.sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    textDirection = if (isBioRtl) TextDirection.Rtl else TextDirection.Ltr
                                )
                            )
                        }
                    }

                    // Review Username Card (For own profile when username_reviewed_at is null)
                    if (isOwnProfile && currentProfile?.usernameReviewedAt == null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceMuted)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.usernameReviewBannerTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = strings.usernameReviewBannerSubtitle,
                                    fontSize = 13.sp,
                                    color = colors.secondaryText
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.primaryBlue)
                                    .clickable { onReviewUsernameClick() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.reviewButton,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onPrimaryBlue
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    HorizontalDivider(color = colors.hairlineBorder)
                    Spacer(modifier = Modifier.height(36.dp))

                    // Media Grid Section Placeholder
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_image),
                            contentDescription = strings.noMediaTitle,
                            tint = colors.secondaryText.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = strings.noMediaTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.secondaryText
                        )
                    }
                }
            }
        }
    }

    // Unfollow Confirmation Dialog
    if (showUnfollowDialog) {
        AlertDialog(
            onDismissRequest = { showUnfollowDialog = false },
            title = {
                Text(
                    text = String.format(strings.unfollowConfirmation, username),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnfollowDialog = false
                        val targetId = publicProfile?.id ?: ""
                        onFollowingClick(targetId)
                    }
                ) {
                    Text(
                        text = strings.unfollowAction,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.destructiveRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowDialog = false }) {
                    Text(
                        text = strings.cancelButton,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.secondaryText
                    )
                }
            },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * Speech bubble displaying an active public note over the avatar.
 */
@Composable
private fun NoteBubble(
    content: String,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = content,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Prompt bubble ("Spill the tea") above avatar when no note exists.
 */
@Composable
private fun NotePromptBubble(
    prompt: String,
    onClick: () -> Unit
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardBackground)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = prompt,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.secondaryText
        )
    }
}

private fun formatNumber(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0).removeSuffix(".0M") + "M"
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0).removeSuffix(".0K") + "K"
        else -> count.toString()
    }
}
