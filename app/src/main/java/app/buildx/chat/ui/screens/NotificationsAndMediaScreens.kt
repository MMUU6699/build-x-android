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
import app.buildx.chat.data.model.NotificationRow
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.NotificationPermissionCard
import app.buildx.chat.ui.theme.BuildXTheme
import coil.compose.AsyncImage

/**
 * S8 Notifications Screen with real Supabase In-App Notifications:
 * - Header: Back circle button (44dp), "Notifications" title, and "Mark all as read" button
 * - Permission card hidden behind showPermissionCard feature flag per Owner Q6
 * - Follow and Note notifications with actor avatar, verified badge, and unread blue dot
 * - Center empty state when no notifications
 */
@Composable
fun NotificationsScreen(
    showPermissionCard: Boolean = false,
    notifications: List<NotificationRow> = emptyList(),
    legacyNotifications: List<AppNotificationItem> = emptyList(),
    onMenuOrBackClick: () -> Unit,
    onTurnOnNotifications: () -> Unit = {},
    onDismissPermissionCard: () -> Unit = {},
    onNotificationClick: (NotificationRow) -> Unit = {},
    onMarkAllAsRead: () -> Unit = {},
    onOpenConversation: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    val hasUnread = notifications.any { !it.isRead }

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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderCircleButton(
                    iconRes = R.drawable.ic_back,
                    contentDescription = strings.cancelButton,
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

            if (hasUnread) {
                Text(
                    text = strings.markAllAsRead,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryBlue,
                    modifier = Modifier.clickable { onMarkAllAsRead() }
                )
            }
        }

        // Permission prompt card (hidden behind feature flag per Owner Q6)
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (item.isRead) colors.surfaceMuted else colors.surfaceMuted.copy(alpha = 0.7f))
                            .clickable { onNotificationClick(item) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Actor Avatar (44dp)
                        if (!item.actorAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.actorAvatarUrl,
                                contentDescription = item.actorDisplayName,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user),
                                    contentDescription = null,
                                    tint = colors.secondaryText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Notification Text
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.actorDisplayName ?: item.actorUsername ?: "User",
                                    color = colors.primaryText,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.actorIsVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_verified_badge),
                                        contentDescription = strings.verifiedAccountDescription,
                                        tint = colors.primaryBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val actionDesc = if (item.type == "follow") {
                                strings.startedFollowingYou
                            } else {
                                val snippet = if (!item.noteContent.isNullOrBlank()) ": \"${item.noteContent}\"" else ""
                                "${strings.postedANote}$snippet"
                            }
                            Text(
                                text = actionDesc,
                                color = colors.secondaryText,
                                fontSize = 13.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Unread Blue Dot (8dp)
                        if (!item.isRead) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryBlue)
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
