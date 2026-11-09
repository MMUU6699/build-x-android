package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.AppNotificationItem
import app.buildx.chat.data.model.AttachmentRow
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.NotificationPermissionCard
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

/**
 * S8 Notifications Screen (video t=56s..61s).
 * Header: Menu/Back circle button (44dp) + "Notifications" title (20sp Bold).
 * Top card: NotificationPermissionCard (#F1F3F0, radius 20dp, bell icon, title, subtitle,
 * full-width #0063D3 "Turn on" pill button, close X button).
 * Center empty state: 40dp bell icon in #6E7274, "No notifications" (17sp Bold #6E7274),
 * "No notifications to show." (15sp Regular #6E7274).
 */
@Composable
fun NotificationsScreen(
    showPermissionCard: Boolean,
    notifications: List<AppNotificationItem>,
    onMenuOrBackClick: () -> Unit,
    onTurnOnNotifications: () -> Unit,
    onDismissPermissionCard: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_menu,
                contentDescription = "Menu",
                onClick = onMenuOrBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.notificationsTitle,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Permission prompt card (t=58s)
        if (showPermissionCard) {
            Spacer(modifier = Modifier.height(6.dp))
            NotificationPermissionCard(
                onTurnOn = onTurnOnNotifications,
                onDismiss = onDismissPermissionCard
            )
        }

        if (notifications.isEmpty()) {
            // Empty state centered in remaining vertical space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bell),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = strings.noNotificationsTitle,
                        color = colors.secondaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.noNotificationsSubtitle,
                        color = colors.secondaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceMuted)
                            .clickable {
                                item.conversationId?.let { onOpenConversation(it) }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.primaryBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bell),
                                contentDescription = null,
                                tint = colors.primaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = colors.primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.body,
                                color = colors.secondaryText,
                                fontSize = 13.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Media Screen (accessible from Drawer -> "Media").
 * Displays all user-uploaded attachments stored in Supabase Storage & attachments table.
 */
@Composable
fun MediaScreen(
    attachments: List<AttachmentRow>,
    onBackClick: () -> Unit,
    onSelectAttachmentForChat: (AttachmentRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderCircleButton(
                iconRes = R.drawable.ic_menu,
                contentDescription = "Menu",
                onClick = onBackClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = strings.mediaTitle,
                color = colors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (attachments.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_image),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = strings.noMediaTitle,
                        color = colors.secondaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.noMediaSubtitle,
                        color = colors.secondaryText,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(attachments, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceMuted)
                            .clickable { onSelectAttachmentForChat(item) }
                    ) {
                        if (item.mimeType.startsWith("image/") && item.publicUrl != null) {
                            AsyncImage(
                                model = item.publicUrl,
                                contentDescription = item.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_paperclip),
                                    contentDescription = null,
                                    tint = colors.primaryText,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.fileName,
                                    color = colors.primaryText,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
