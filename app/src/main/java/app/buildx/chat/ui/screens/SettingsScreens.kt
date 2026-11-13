package app.buildx.chat.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.BuildConfig
import app.buildx.chat.R
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.ChatWallpaperConfig
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.chat.displayTitle
import app.buildx.chat.ui.components.AppearanceRadioRow
import app.buildx.chat.ui.components.GroupedCard
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.NameWithBadge
import app.buildx.chat.ui.components.SettingsRow
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

/**
 * Helper to resolve real live app version & build number from PackageManager + BuildConfig.
 */
@Composable
fun rememberAppVersionText(): Pair<String, Long> {
    val context = LocalContext.current
    return remember(context) {
        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val vName = pkgInfo.versionName ?: BuildConfig.VERSION_NAME
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            vName to vCode
        } catch (_: Throwable) {
            BuildConfig.VERSION_NAME to BuildConfig.VERSION_CODE.toLong()
        }
    }
}

/**
 * S10 Settings Screen (video t=61s..69s + v1.2 Language & Live Version additions).
 * Page background: #F1F3F0 (colors.surfaceMuted).
 * Header: Close X circle button (44dp) + "Settings" (20sp Bold).
 * Profile card: white card, 44dp avatar circle, "View profile" (16sp Bold),
 * "@handle" (14sp SemiBold #0063D3), trailing chevron.
 * Section header: "App settings" (14sp Bold #6E7274).
 * Grouped white card: Notifications, App appearance, App language, Help & support,
 * Data & privacy, App info, Legal & safety.
 * Bottom card: Log out in #C3192E crimson red, followed by live dynamic version footer.
 */
@Composable
fun SettingsScreen(
    profile: UserProfile?,
    appearance: AppAppearance,
    onCloseClick: () -> Unit,
    onViewProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAppAppearanceClick: () -> Unit,
    onHelpSupportClick: () -> Unit,
    onDataPrivacyClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onLegalSafetyClick: () -> Unit,
    onSignOutClick: () -> Unit,
    language: AppLanguage = AppLanguage.SYSTEM,
    onAppLanguageClick: () -> Unit = {},
    onRequestVerificationClick: () -> Unit = {},
    onBlockedAccountsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val rawHandle = profile?.effectiveUsername?.let { if (it.startsWith("@")) it else "@$it" } ?: "@user"
    val handleText = "\u200E$rawHandle"
    val (versionName, versionCode) = rememberAppVersionText()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_close,
                contentDescription = strings.closeButton,
                onClick = onCloseClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.settingsTitle,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        SettingsSectionTitle(stringResource(R.string.settings_account_section))

        // The account surface is deliberately separate: it is an identity
        // destination, not another preference in a long list.
        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onViewProfileClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    if (!profile?.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.avatarUrl,
                            contentDescription = strings.profileTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    NameWithBadge(
                        name = strings.viewProfile,
                        isVerified = profile?.isVerified == true,
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        badgeSize = 16.dp,
                        badgeSpacing = 6.dp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = handleText,
                        color = colors.primaryBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(scaleX = if (strings.isRtl) -1f else 1f, scaleY = 1f)
                )
            }
        }

        SettingsSectionTitle(stringResource(R.string.settings_experience_section))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_bell,
                title = strings.notificationsTitle,
                onClick = onNotificationsClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_settings,
                title = strings.appAppearance,
                subtitle = when (appearance) {
                    AppAppearance.AUTOMATIC -> strings.appearanceAutomatic
                    AppAppearance.LIGHT -> strings.appearanceLight
                    AppAppearance.DARK -> strings.appearanceDark
                },
                onClick = onAppAppearanceClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_language,
                title = strings.appLanguage,
                subtitle = when (language) {
                    AppLanguage.SYSTEM -> strings.languageSystem
                    AppLanguage.ENGLISH -> strings.languageEnglish
                    AppLanguage.ARABIC -> strings.languageArabic
                },
                onClick = onAppLanguageClick,
                showDivider = false
            )
        }

        SettingsSectionTitle(stringResource(R.string.settings_support_safety_section))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_help,
                title = strings.helpAndSupport,
                onClick = onHelpSupportClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_shield,
                title = strings.dataAndPrivacy,
                onClick = onDataPrivacyClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_user_x,
                title = stringResource(R.string.pv2_blocked_accounts),
                onClick = onBlockedAccountsClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_shield,
                title = strings.legalAndSafety,
                onClick = onLegalSafetyClick,
                showDivider = false
            )
        }

        SettingsSectionTitle(stringResource(R.string.settings_buildx_section))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_verified_badge,
                title = if (profile?.isVerified == true) {
                    stringResource(id = R.string.verification_title)
                } else {
                    strings.requestVerificationTitle
                },
                subtitle = if (profile?.isVerified == true) strings.verifiedAccountDescription else null,
                onClick = onRequestVerificationClick,
                iconTint = Color.Unspecified,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_info,
                title = strings.appInfo,
                subtitle = "v$versionName ($versionCode)",
                onClick = onAppInfoClick,
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Log out card (t=66s, crimson red #C3192E)
        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_log_out,
                title = strings.logOut,
                isDestructive = true,
                showChevron = false,
                showDivider = false,
                onClick = onSignOutClick
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Real dynamic app version footer
        Text(
            text = "Build X v$versionName (Build $versionCode) • ${BuildConfig.BUILD_DATE}",
            color = colors.secondaryText.copy(alpha = 0.78f),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    val colors = BuildXTheme.colors
    Text(
        text = title,
        color = colors.secondaryText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp)
    )
}

/**
 * S10 Sub-page: App Appearance Screen (video t=64s).
 * Header: Back < circle button (44dp) + "App appearance" (20sp Bold).
 * White grouped card with 3 radio rows: Automatic, Light, Dark.
 */
@Composable
fun AppAppearanceScreen(
    selectedAppearance: AppAppearance,
    onSelectAppearance: (AppAppearance) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    defaultWallpaper: ChatWallpaperConfig = ChatWallpaperConfig(),
    onChatWallpaperClick: () -> Unit = {}
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.appAppearance,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            AppearanceRadioRow(
                label = strings.appearanceAutomatic,
                selected = selectedAppearance == AppAppearance.AUTOMATIC,
                onClick = { onSelectAppearance(AppAppearance.AUTOMATIC) },
                showDivider = true
            )
            AppearanceRadioRow(
                label = strings.appearanceLight,
                selected = selectedAppearance == AppAppearance.LIGHT,
                onClick = { onSelectAppearance(AppAppearance.LIGHT) },
                showDivider = true
            )
            AppearanceRadioRow(
                label = strings.appearanceDark,
                selected = selectedAppearance == AppAppearance.DARK,
                onClick = { onSelectAppearance(AppAppearance.DARK) },
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_wallpaper,
                title = stringResource(R.string.dm_wallpaper),
                subtitle = defaultWallpaper.displayTitle(stringResource(R.string.dm_wallpaper_custom_label)),
                onClick = onChatWallpaperClick,
                showDivider = false
            )
        }
    }
}

/**
 * S10 Sub-page: App Language Screen.
 * Allows switching between System default, English, and Arabic with full RTL/LTR layout updates.
 */
@Composable
fun AppLanguageScreen(
    selectedLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.appLanguage,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            AppearanceRadioRow(
                label = strings.languageSystem,
                selected = selectedLanguage == AppLanguage.SYSTEM,
                onClick = { onSelectLanguage(AppLanguage.SYSTEM) },
                showDivider = true
            )
            AppearanceRadioRow(
                label = strings.languageEnglish,
                selected = selectedLanguage == AppLanguage.ENGLISH,
                onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                showDivider = true
            )
            AppearanceRadioRow(
                label = strings.languageArabic,
                selected = selectedLanguage == AppLanguage.ARABIC,
                onClick = { onSelectLanguage(AppLanguage.ARABIC) },
                showDivider = false
            )
        }
    }
}

/**
 * S10 Sub-page: App Info Screen (video t=69s).
 * Header: Back < circle button (44dp) + "App info" (20sp Bold).
 * White grouped card with real dynamic "App version" and clickable "App language".
 */
@Composable
fun AppInfoScreen(
    onBackClick: () -> Unit,
    language: AppLanguage = AppLanguage.SYSTEM,
    onLanguageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val (versionName, versionCode) = rememberAppVersionText()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.appInfo,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 15.dp)
            ) {
                Text(
                    text = strings.appVersionLabel,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$versionName (Build $versionCode • ${BuildConfig.BUILD_DATE})",
                    color = colors.secondaryText,
                    fontSize = 14.sp
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                thickness = 1.dp,
                color = colors.hairlineBorder
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLanguageClick)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.appLanguage,
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when (language) {
                            AppLanguage.SYSTEM -> strings.languageSystem
                            AppLanguage.ENGLISH -> strings.languageEnglish
                            AppLanguage.ARABIC -> strings.languageArabic
                        },
                        color = colors.secondaryText,
                        fontSize = 14.sp
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(scaleX = if (strings.isRtl) -1f else 1f, scaleY = 1f)
                )
            }
        }
    }
}

/**
 * S10 Sub-page: Data & Privacy Screen.
 * Provides real controls: Export my data (JSON share), Delete all chats, Delete account.
 */
@Composable
fun DataPrivacyScreen(
    onExportDataClick: () -> Unit,
    onDeleteAllChatsClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    var confirmDeleteChats by remember { mutableStateOf(false) }
    var confirmDeleteAccount by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.dataAndPrivacy,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_share,
                title = strings.exportMyData,
                subtitle = strings.exportMyDataSubtitle,
                onClick = onExportDataClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_trash,
                title = strings.deleteAllChats,
                subtitle = strings.deleteAllChatsSubtitle,
                isDestructive = true,
                onClick = { confirmDeleteChats = true },
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_trash,
                title = strings.deleteAccount,
                subtitle = strings.deleteAccountSubtitle,
                isDestructive = true,
                onClick = { confirmDeleteAccount = true },
                showDivider = false
            )
        }
    }

    if (confirmDeleteChats) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChats = false },
            containerColor = colors.cardBackground,
            titleContentColor = colors.primaryText,
            textContentColor = colors.secondaryText,
            title = { Text(strings.deleteAllChats, fontWeight = FontWeight.Bold) },
            text = { Text(strings.deleteAllChatsSubtitle) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteChats = false
                        onDeleteAllChatsClick()
                    }
                ) {
                    Text(strings.deleteButton, color = colors.destructiveRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChats = false }) {
                    Text(strings.cancelButton, color = colors.primaryText)
                }
            }
        )
    }

    if (confirmDeleteAccount) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAccount = false },
            containerColor = colors.cardBackground,
            titleContentColor = colors.primaryText,
            textContentColor = colors.secondaryText,
            title = { Text(strings.deleteAccount, fontWeight = FontWeight.Bold) },
            text = { Text(strings.deleteAccountSubtitle) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAccount = false
                        onDeleteAccountClick()
                    }
                ) {
                    Text(strings.deleteButton, color = colors.destructiveRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAccount = false }) {
                    Text(strings.cancelButton, color = colors.primaryText)
                }
            }
        )
    }
}

/**
 * S10 Sub-page: Help & Support Screen.
 */
@Composable
fun HelpSupportScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.helpAndSupport,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "${strings.thinkingMode} / ${strings.instantMode}",
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${strings.thinkingDescription}. ${strings.instantDescription}.",
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.incognitoSubtitle,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.incognitoEmptyNotice,
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * S10 Sub-page: Legal & Safety Screen.
 */
@Composable
fun LegalSafetyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceMuted)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_back,
                contentDescription = "Back",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.legalAndSafety,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = strings.dataAndPrivacy,
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your conversations and attachments are protected with row-level security so only your authenticated account can read or modify them. Shared links provide read-only access and can be revoked at any time from the conversation menu.",
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Open-source typography & icons",
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Typography uses Plus Jakarta Sans and Noto Sans Arabic under the SIL Open Font License 1.1.",
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * Dialogs for S5 Overflow actions (Rename, Delete confirmation, Share link, Recent uploads).
 */
@Composable
fun RenameChatDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.primaryText,
        title = { Text(strings.renameDialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(colors.primaryBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
                Text(strings.saveButton, color = colors.primaryBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton, color = colors.secondaryText)
            }
        }
    )
}

@Composable
fun DeleteChatDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
        title = { Text(strings.deleteDialogTitle, fontWeight = FontWeight.Bold) },
        text = { Text(strings.deleteDialogBody) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.deleteButton, color = colors.destructiveRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton, color = colors.primaryText)
            }
        }
    )
}

@Composable
fun ShareLinkDialog(
    shareUrl: String,
    onCopyLink: (String) -> Unit,
    onShareSystem: (String) -> Unit,
    onRevokeLink: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
        title = { Text(strings.shareDialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(strings.shareDialogBody)
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceMuted)
                        .padding(12.dp)
                ) {
                    Text(
                        text = shareUrl,
                        color = colors.primaryBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRevokeLink) {
                    Text(strings.revokeLinkButton, color = colors.destructiveRed, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { onCopyLink(shareUrl) }) {
                    Text(strings.copyLinkButton, color = colors.primaryBlue, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onShareSystem(shareUrl) }) {
                    Text(strings.shareButton, color = colors.primaryBlue, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeButton, color = colors.secondaryText)
            }
        }
    )
}

@Composable
fun RecentUploadsDialog(
    attachments: List<AttachmentRow>,
    onSelect: (AttachmentRow) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
        title = { Text(strings.recentlyUploaded, fontWeight = FontWeight.Bold) },
        text = {
            if (attachments.isEmpty()) {
                Text(strings.noMediaSubtitle)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    attachments.take(10).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceMuted)
                                .clickable { onSelect(item) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (item.mimeType.startsWith("image/")) R.drawable.ic_image else R.drawable.ic_paperclip
                                ),
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = item.fileName,
                                color = colors.primaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.closeButton, color = colors.primaryBlue, fontWeight = FontWeight.Bold)
            }
        }
    )
}
