package app.buildx.chat.ui.screens

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.components.AppearanceRadioRow
import app.buildx.chat.ui.components.GroupedCard
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.SettingsRow
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

/**
 * S10 Settings Screen (video t=61s..69s).
 * Page background: #F1F3F0 (colors.surfaceMuted).
 * Header: Close X circle button (44dp) + "Settings" (20sp Bold).
 * Profile card: white card, 44dp avatar circle, "View profile" (16sp Bold),
 * "@handle" (14sp SemiBold #0063D3), trailing chevron.
 * Section header: "App settings" (14sp Bold #6E7274).
 * Grouped white card: Notifications, App appearance, Help & support,
 * Data & privacy, App info, Legal & safety.
 * Bottom card: Log out in #C3192E crimson red.
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
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val handleText = profile?.handle?.let { if (it.startsWith("@")) it else "@$it" } ?: "@user"

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
                contentDescription = "Close settings",
                onClick = onCloseClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Settings",
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // View profile card (t=62s)
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
                            contentDescription = "Profile photo",
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
                    Text(
                        text = "View profile",
                        color = colors.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
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
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "App settings",
            color = colors.secondaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )

        // Grouped App settings card (6 rows only — no dead rows)
        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SettingsRow(
                iconRes = R.drawable.ic_bell,
                title = "Notifications",
                onClick = onNotificationsClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_settings,
                title = "App appearance",
                subtitle = when (appearance) {
                    AppAppearance.AUTOMATIC -> "Automatic"
                    AppAppearance.LIGHT -> "Light"
                    AppAppearance.DARK -> "Dark"
                },
                onClick = onAppAppearanceClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_help,
                title = "Help & support",
                onClick = onHelpSupportClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_shield,
                title = "Data & privacy",
                onClick = onDataPrivacyClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_info,
                title = "App info",
                onClick = onAppInfoClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_shield,
                title = "Legal & safety",
                onClick = onLegalSafetyClick,
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
                title = "Log out",
                isDestructive = true,
                showChevron = false,
                showDivider = false,
                onClick = onSignOutClick
            )
        }
    }
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
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors

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
                text = "App appearance",
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
                label = "Automatic",
                selected = selectedAppearance == AppAppearance.AUTOMATIC,
                onClick = { onSelectAppearance(AppAppearance.AUTOMATIC) },
                showDivider = true
            )
            AppearanceRadioRow(
                label = "Light",
                selected = selectedAppearance == AppAppearance.LIGHT,
                onClick = { onSelectAppearance(AppAppearance.LIGHT) },
                showDivider = true
            )
            AppearanceRadioRow(
                label = "Dark",
                selected = selectedAppearance == AppAppearance.DARK,
                onClick = { onSelectAppearance(AppAppearance.DARK) },
                showDivider = false
            )
        }
    }
}

/**
 * S10 Sub-page: App Info Screen (video t=69s).
 * Header: Back < circle button (44dp) + "App info" (20sp Bold).
 * White grouped card with "App version" and "App language".
 */
@Composable
fun AppInfoScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors

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
                text = "App info",
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
                    text = "App version",
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "1.0.0 (Build 100)",
                    color = colors.secondaryText,
                    fontSize = 14.sp
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                thickness = 1.dp,
                color = colors.hairlineBorder
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 15.dp)
            ) {
                Text(
                    text = "App language",
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "System default (English / Arabic)",
                    color = colors.secondaryText,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * S10 Sub-page: Profile Edit Screen ("View profile").
 * Allows editing display_name, @handle, and picking a new avatar uploaded to Supabase Storage.
 */
@Composable
fun ProfileEditScreen(
    profile: UserProfile?,
    onSaveProfile: (displayName: String, handle: String) -> Unit,
    onPickAvatarClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    var displayName by remember(profile?.displayName) {
        mutableStateOf(profile?.displayName ?: "")
    }
    var handle by remember(profile?.handle) {
        mutableStateOf(profile?.handle ?: "")
    }

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
                text = "Profile",
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Avatar circle
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(colors.cardBackground)
                    .clickable(onClick = onPickAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (!profile?.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile?.avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_user),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Change photo",
                color = colors.primaryBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onPickAvatarClick)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        GroupedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Display name",
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
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
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp),
                thickness = 1.dp,
                color = colors.hairlineBorder
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Handle",
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = handle,
                    onValueChange = { handle = it.removePrefix("@") },
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
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.primaryBlue)
                .clickable {
                    if (displayName.isNotBlank() && handle.isNotBlank()) {
                        onSaveProfile(displayName.trim(), handle.trim())
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Save changes",
                color = colors.onPrimaryBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
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
                text = "Data & privacy",
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
                title = "Export my data",
                subtitle = "Download your profile, conversations, and messages as JSON",
                onClick = onExportDataClick,
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_trash,
                title = "Delete all chats",
                subtitle = "Permanently remove all saved conversations",
                isDestructive = true,
                onClick = { confirmDeleteChats = true },
                showDivider = true
            )
            SettingsRow(
                iconRes = R.drawable.ic_trash,
                title = "Delete account",
                subtitle = "Permanently delete your account and all associated data",
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
            title = { Text("Delete all chats?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all of your conversations and messages from the database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteChats = false
                        onDeleteAllChatsClick()
                    }
                ) {
                    Text("Delete all", color = colors.destructiveRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChats = false }) {
                    Text("Cancel", color = colors.primaryText)
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
            title = { Text("Delete account?", fontWeight = FontWeight.Bold) },
            text = { Text("Your profile, conversations, messages, and attachments will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAccount = false
                        onDeleteAccountClick()
                    }
                ) {
                    Text("Delete account", color = colors.destructiveRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAccount = false }) {
                    Text("Cancel", color = colors.primaryText)
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
                text = "Help & support",
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
                    text = "Thinking vs Instant modes",
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the Thinking pill in the composer to switch between deeper multi-step reasoning (Thinking) and fast responses (Instant). Tap Show thinking on any completed response to inspect the step-by-step reasoning timeline.",
                    color = colors.secondaryText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Temporary incognito chats",
                    color = colors.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Open the navigation drawer and tap the incognito button next to Search to start a temporary session. Temporary chats are never saved to your conversation history.",
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
                text = "Legal & safety",
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
                    text = "Terms of Service & Privacy Policy",
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
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.primaryText,
        title = { Text("Rename chat", fontWeight = FontWeight.Bold) },
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
                Text("Save", color = colors.primaryBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.secondaryText)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
        title = { Text("Delete chat?", fontWeight = FontWeight.Bold) },
        text = { Text("This conversation and its messages will be permanently deleted.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = colors.destructiveRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.primaryText)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
        title = { Text("Shared link active", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Anyone with this read-only link can view a snapshot of this conversation:")
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
                    Text("Revoke", color = colors.destructiveRed, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { onCopyLink(shareUrl) }) {
                    Text("Copy", color = colors.primaryBlue, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onShareSystem(shareUrl) }) {
                    Text("Share", color = colors.primaryBlue, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.secondaryText)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
        title = { Text("Recently uploaded", fontWeight = FontWeight.Bold) },
        text = {
            if (attachments.isEmpty()) {
                Text("No previous uploads found in your account.")
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
                Text("Close", color = colors.primaryBlue, fontWeight = FontWeight.Bold)
            }
        }
    )
}
