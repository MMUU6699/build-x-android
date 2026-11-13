package app.buildx.chat.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.buildx.chat.R
import app.buildx.chat.data.model.AccountInfo
import app.buildx.chat.data.model.ReportReason
import app.buildx.chat.ui.components.ActionMenuPopup
import app.buildx.chat.ui.components.ActionMenuItem
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import app.buildx.chat.ui.theme.softShadow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// Shared formatting helpers (real data only)
// ---------------------------------------------------------------------------

fun formatProfileMonthYear(iso: String?): String? {
    val date = parseProfileDate(iso) ?: return null
    return runCatching {
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }.getOrNull()
}

fun formatProfileDate(iso: String?): String? {
    val date = parseProfileDate(iso) ?: return null
    return runCatching {
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }.getOrNull()
}

private fun parseProfileDate(iso: String?): java.time.LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrElse {
        runCatching { java.time.LocalDate.parse(iso.substringBefore("T")) }.getOrNull()
    }
}

fun countryDisplayName(code: String?): String? {
    if (code.isNullOrBlank() || code.length != 2) return null
    return runCatching {
        Locale("", code.uppercase()).getDisplayCountry(Locale.getDefault())
            .ifBlank { null }
    }.getOrNull()
}

fun compactProfileCount(count: Int): String = runCatching {
    android.icu.text.CompactDecimalFormat
        .getInstance(Locale.getDefault(), android.icu.text.CompactDecimalFormat.CompactStyle.SHORT)
        .format(count.toLong())
}.getOrDefault(count.toString())

// ---------------------------------------------------------------------------
// Three-dot menu sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    username: String,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isNoteSubscribed: Boolean,
    onDismiss: () -> Unit,
    onAbout: () -> Unit = {},
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onToggleNoteNotifications: () -> Unit = {},
    onReport: () -> Unit = {},
    onBlock: () -> Unit = {},
    onSettings: () -> Unit = {},
    onVerification: () -> Unit = {},
    onBlockedAccounts: () -> Unit = {}
) {
    val colors = BuildXTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        scrimColor = colors.scrimColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetGrabHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        ) {
            if (isOwnProfile) {
                MenuRow(R.drawable.ic_settings, stringResource(R.string.pv2_settings), onSettings)
                MenuRow(R.drawable.ic_verified_badge, stringResource(R.string.pv2_verification), onVerification, tintUnspecified = true)
                MenuRow(R.drawable.ic_user_x, stringResource(R.string.pv2_blocked_accounts), onBlockedAccounts)
            } else {
                MenuRow(R.drawable.ic_info, stringResource(R.string.pv2_about_this_account), onAbout)
                MenuRow(R.drawable.ic_share, stringResource(R.string.pv2_share_profile), onShare)
                MenuRow(R.drawable.ic_link, stringResource(R.string.pv2_copy_link), onCopyLink)
                if (isFollowing) {
                    NoteNotificationRow(
                        checked = isNoteSubscribed,
                        label = stringResource(R.string.pv2_note_notifications),
                        onToggle = onToggleNoteNotifications
                    )
                }
                MenuRow(R.drawable.ic_flag, stringResource(R.string.pv2_report), onReport)
                MenuRow(
                    iconRes = R.drawable.ic_block,
                    label = stringResource(R.string.pv2_block),
                    onClick = onBlock,
                    isDestructive = true,
                    showDivider = false
                )
            }
        }
    }
}

@Composable
private fun SheetGrabHandle() {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .width(32.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.grabHandle)
    )
}

@Composable
private fun MenuRow(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    showDivider: Boolean = true,
    tintUnspecified: Boolean = false
) {
    val colors = BuildXTheme.colors
    val tint = when {
        isDestructive -> colors.destructiveRed
        tintUnspecified -> androidx.compose.ui.graphics.Color.Unspecified
        else -> colors.primaryText
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                color = if (isDestructive) colors.destructiveRed else colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                style = TextStyle(textDirection = TextDirection.Content)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp, end = 22.dp),
                thickness = 1.dp,
                color = colors.subtleSurfaceBorder
            )
        }
    }
}

@Composable
private fun NoteNotificationRow(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit
) {
    val colors = BuildXTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bell),
                contentDescription = null,
                tint = colors.primaryText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            BuildXToggle(checked = checked, onCheckedChange = { onToggle() })
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 60.dp, end = 22.dp),
            thickness = 1.dp,
            color = colors.subtleSurfaceBorder
        )
    }
}

@Composable
fun BuildXToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = BuildXTheme.colors
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(200),
        label = "toggleThumb"
    )
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) colors.primaryBlue else colors.hairlineBorder)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.cardBackground)
        )
    }
}

// ---------------------------------------------------------------------------
// About this account
// ---------------------------------------------------------------------------

@Composable
fun AboutAccountSheet(
    accountInfo: AccountInfo?,
    isLoading: Boolean,
    errorMessage: String?,
    fallbackDisplayName: String = "",
    fallbackUsername: String = "",
    fallbackAvatarUrl: String? = null,
    fallbackJoinedAt: String? = null,
    fallbackCountryCode: String? = null,
    fallbackIsVerified: Boolean = false,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    var showFormerUsernames by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.pageBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = stringResource(R.string.pv2_back),
                            tint = colors.primaryText,
                            modifier = Modifier
                                .size(21.dp)
                                .then(if (isRtl) Modifier.rotate(180f) else Modifier)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pabout_title),
                        color = colors.primaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp)
                ) {
                    Spacer(modifier = Modifier.height(14.dp))

                    val resolvedDisplayName = accountInfo?.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: fallbackDisplayName
                    val resolvedHandle = accountInfo?.username
                        ?.takeIf { it.isNotBlank() }
                        ?: fallbackUsername.removePrefix("@")
                    val resolvedAvatar = accountInfo?.avatarUrl ?: fallbackAvatarUrl
                    val resolvedVerified = accountInfo?.isVerified ?: fallbackIsVerified

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProfileAvatar(
                            avatarUrl = resolvedAvatar,
                            displayName = resolvedDisplayName,
                            size = 84.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = resolvedDisplayName.ifBlank {
                                    if (resolvedHandle.isNotBlank()) "@$resolvedHandle" else ""
                                },
                                color = colors.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            if (resolvedVerified) {
                                Spacer(modifier = Modifier.width(6.dp))
                                VerifiedBadge(size = 16.dp)
                            }
                        }
                        if (resolvedHandle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "\u2066@$resolvedHandle\u2069",
                                color = colors.secondaryText,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.pabout_footnote),
                            color = colors.secondaryText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            style = TextStyle(textDirection = TextDirection.Content),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                MarkLoader(
                                    size = 28.dp,
                                    isSpinning = true,
                                    tint = colors.primaryBlue
                                )
                            }
                        }

                        accountInfo == null && !isLoading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.pabout_error_title),
                                    color = colors.primaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = errorMessage,
                                        color = colors.secondaryText,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                PillButton(
                                    label = stringResource(R.string.pabout_retry),
                                    onClick = onRetry,
                                    filled = false
                                )
                            }
                        }

                        else -> {
                            val joined = formatProfileMonthYear(accountInfo?.joinedAt ?: fallbackJoinedAt)
                            val basedIn = countryDisplayName(accountInfo?.countryCode ?: fallbackCountryCode)
                                ?: stringResource(R.string.pabout_not_specified)
                            val verifiedSince = formatProfileMonthYear(accountInfo?.verifiedAt)
                                ?: formatProfileMonthYear(accountInfo?.joinedAt ?: fallbackJoinedAt)
                            val former = accountInfo?.formerUsernames.orEmpty()
                            val changeCount = accountInfo?.usernameChangeCount ?: former.size

                            AboutInfoRow(
                                iconRes = R.drawable.ic_calendar,
                                label = stringResource(R.string.pabout_joined),
                                subtitle = joined ?: stringResource(R.string.pabout_not_specified)
                            )
                            AboutInfoRow(
                                iconRes = R.drawable.ic_globe,
                                label = stringResource(R.string.pabout_based_in),
                                subtitle = basedIn
                            )
                            AboutInfoRow(
                                iconRes = R.drawable.ic_user,
                                label = stringResource(R.string.pabout_former_usernames),
                                subtitle = if (former.isEmpty()) {
                                    stringResource(R.string.pabout_no_former)
                                } else {
                                    null
                                },
                                trailingCount = changeCount,
                                showChevron = former.isNotEmpty(),
                                onClick = if (former.isNotEmpty()) {
                                    { showFormerUsernames = !showFormerUsernames }
                                } else {
                                    null
                                }
                            )
                            if (showFormerUsernames && former.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.cardBackground)
                                        .border(0.8.dp, colors.subtleSurfaceBorder, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    former.forEach { previous ->
                                        Text(
                                            text = "@$previous",
                                            color = colors.primaryText,
                                            fontSize = 14.5.sp,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            if (resolvedVerified) {
                                AboutInfoRow(
                                    iconRes = R.drawable.ic_verified_badge,
                                    label = stringResource(R.string.pabout_verified_since),
                                    subtitle = verifiedSince
                                        ?: stringResource(R.string.pabout_not_specified),
                                    tintUnspecified = true
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

/**
 * Two-line metadata row used by the About page: leading 24dp icon, label with a
 * subtitle underneath, and an optional trailing count plus chevron.
 */
@Composable
private fun AboutInfoRow(
    iconRes: Int,
    label: String,
    subtitle: String? = null,
    trailingCount: Int? = null,
    showChevron: Boolean = false,
    tintUnspecified: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = BuildXTheme.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (tintUnspecified) androidx.compose.ui.graphics.Color.Unspecified else colors.primaryText,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    style = TextStyle(textDirection = TextDirection.Content)
                )
            }
        }
        if (trailingCount != null) {
            Text(
                text = trailingCount.toString(),
                color = colors.secondaryText,
                fontSize = 15.sp
            )
        }
        if (showChevron) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier
                    .size(18.dp)
                    .then(if (isRtl) Modifier.rotate(180f) else Modifier)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Report flow (3 steps)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportAccountSheet(
    username: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onSubmit: (ReportReason, String?) -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableIntStateOf(0) }
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var details by remember { mutableStateOf("") }
    var wasSubmitting by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(isSubmitting, errorMessage) {
        if (wasSubmitting && !isSubmitting && errorMessage == null) {
            step = 2
        }
        wasSubmitting = isSubmitting
    }

    val reasons = listOf(
        ReportReason.SPAM to stringResource(R.string.pv2_report_reason_spam),
        ReportReason.IMPERSONATION to stringResource(R.string.pv2_report_reason_impersonation),
        ReportReason.FAKE_ACCOUNT to stringResource(R.string.pv2_report_reason_fake),
        ReportReason.INAPPROPRIATE_CONTENT to stringResource(R.string.pv2_report_reason_inappropriate),
        ReportReason.HARASSMENT to stringResource(R.string.pv2_report_reason_harassment),
        ReportReason.OTHER to stringResource(R.string.pv2_report_reason_other)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        scrimColor = colors.scrimColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetGrabHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 26.dp)
        ) {
            Text(
                text = stringResource(R.string.pv2_report_title, username),
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(18.dp))

            when (step) {
                0 -> {
                    Text(
                        text = stringResource(R.string.pv2_report_step_reason),
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    reasons.forEach { (reason, label) ->
                        ReasonRow(
                            label = label,
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                    }
                    if (selectedReason == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.pv2_report_select_reason),
                            color = colors.secondaryText,
                            fontSize = 12.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    PillButton(
                        label = stringResource(R.string.pv2_report_next),
                        onClick = { step = 1 },
                        filled = true,
                        enabled = selectedReason != null
                    )
                }
                1 -> {
                    Text(
                        text = stringResource(R.string.pv2_report_step_details),
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val isDetailsRtl = isRtlText(details, defaultRtl = BuildXTheme.strings.isRtl)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceMuted)
                            .padding(14.dp)
                    ) {
                        if (details.isEmpty()) {
                            Text(
                                text = stringResource(R.string.pv2_report_details_hint),
                                color = colors.secondaryText,
                                fontSize = 14.sp
                            )
                        }
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalLayoutDirection provides if (isDetailsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            BasicTextField(
                                value = details,
                                onValueChange = { if (it.length <= 500) details = it },
                                textStyle = TextStyle(
                                    fontFamily = BuildXFontFamily,
                                    fontSize = 14.sp,
                                    color = colors.primaryText,
                                    textDirection = if (isDetailsRtl) TextDirection.Rtl else TextDirection.Ltr
                                ),
                                cursorBrush = SolidColor(colors.primaryBlue),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.pv2_report_counter, details.length, 500),
                        color = colors.secondaryText,
                        fontSize = 11.5.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = colors.destructiveRed,
                            fontSize = 12.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    PillButton(
                        label = if (isSubmitting) {
                            stringResource(R.string.pv2_report_submitting)
                        } else {
                            stringResource(R.string.pv2_report_submit)
                        },
                        onClick = {
                            val reason = selectedReason ?: return@PillButton
                            onSubmit(reason, details.trim().ifEmpty { null })
                        },
                        filled = true,
                        enabled = !isSubmitting,
                        loading = isSubmitting
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.pv2_report_received_title),
                        color = colors.primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pv2_report_received_body),
                        color = colors.secondaryText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.pv2_report_block_title, username),
                        color = colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.pv2_report_block_body),
                        color = colors.secondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    PillButton(
                        label = stringResource(R.string.pv2_report_block_cta),
                        onClick = {
                            onBlock()
                            onDismiss()
                        },
                        filled = false,
                        destructive = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PillButton(
                        label = stringResource(R.string.pv2_report_done),
                        onClick = onDismiss,
                        filled = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BuildXTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 6.5.dp else 1.5.dp,
                    color = if (selected) colors.primaryBlue else colors.hairlineBorder,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = colors.primaryText,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Start
        )
    }
}

// ---------------------------------------------------------------------------
// Block confirmation
// ---------------------------------------------------------------------------

@Composable
fun BlockAccountDialog(
    username: String,
    isBlocking: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    Dialog(
        onDismissRequest = { if (!isBlocking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .softShadow(
                    color = colors.composerShadowColor,
                    cornerRadius = 22.dp,
                    blurRadius = 30.dp,
                    offsetY = 10.dp
                )
                .clip(RoundedCornerShape(22.dp))
                .background(colors.cardBackground)
                .padding(22.dp)
        ) {
            Text(
                text = stringResource(R.string.pv2_block_title, username),
                color = colors.primaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pv2_block_body),
                color = colors.secondaryText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(20.dp))
            PillButton(
                label = if (isBlocking) {
                    stringResource(R.string.pv2_blocking)
                } else {
                    stringResource(R.string.pv2_block_confirm)
                },
                onClick = onConfirm,
                filled = true,
                destructive = true,
                enabled = !isBlocking,
                loading = isBlocking
            )
            Spacer(modifier = Modifier.height(8.dp))
            PillButton(
                label = stringResource(R.string.pv2_block_cancel),
                onClick = onDismiss,
                filled = false,
                enabled = !isBlocking
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Unfollow popup
// ---------------------------------------------------------------------------

@Composable
fun UnfollowPopup(
    username: String,
    onUnfollow: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            ActionMenuPopup(
                items = listOf(
                    ActionMenuItem(
                        label = stringResource(R.string.pv2_unfollow_username, username),
                        iconRes = R.drawable.ic_user_x,
                        isDestructive = true,
                        onClick = {
                            onDismiss()
                            onUnfollow()
                        }
                    )
                ),
                modifier = Modifier.width(240.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable pill button (design-system, not Material)
// ---------------------------------------------------------------------------

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    loading: Boolean = false
) {
    val colors = BuildXTheme.colors
    val background = when {
        destructive && filled -> colors.destructiveRed
        destructive -> colors.surfaceMuted
        filled -> colors.primaryBlue
        else -> colors.surfaceMuted
    }
    val contentColor = when {
        destructive && filled -> colors.onPrimaryBlue
        destructive -> colors.destructiveRed
        filled -> colors.onPrimaryBlue
        else -> colors.primaryText
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) background else background.copy(alpha = 0.55f))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                MarkLoader(
                    size = 16.dp,
                    isSpinning = true,
                    tint = contentColor
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}
