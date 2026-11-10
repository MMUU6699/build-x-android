package app.buildx.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.ui.theme.isRtlText
import coil.compose.AsyncImage

/**
 * P5 Public Note Bottom Sheet:
 * Allows user to create, replace, or delete their 24-hour public status note (max 60 chars).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicNoteSheet(
    profile: UserProfile?,
    activeNote: PublicNote?,
    isOpen: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onCreateNote: (String) -> Unit,
    onDeleteNote: () -> Unit
) {
    if (!isOpen) return

    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var noteInput by remember(activeNote) {
        mutableStateOf(activeNote?.content ?: "")
    }

    val isInputRtl = isRtlText(noteInput, defaultRtl = strings.isRtl)
    val hasActiveNote = activeNote != null && activeNote.content.isNotBlank()
    val canPost = noteInput.trim().isNotEmpty() && noteInput.length <= 60 &&
                  (!hasActiveNote || noteInput.trim() != activeNote.content.trim())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(colors.grabHandle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            // Sheet Title & 24h Expiry notice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.leaveANote,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )

                Text(
                    text = "${noteInput.length}/60",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (noteInput.length <= 60) colors.secondaryText else colors.destructiveRed
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = strings.noteExpiresNotice,
                fontSize = 13.sp,
                color = colors.secondaryText
            )

            Spacer(modifier = Modifier.height(20.dp))

            // User Avatar + Note Input Bubble
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (!profile?.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile?.avatarUrl,
                        contentDescription = profile?.displayName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_user),
                            contentDescription = null,
                            tint = colors.secondaryText,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Input Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (isInputRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        BasicTextField(
                            value = noteInput,
                            onValueChange = {
                                if (it.length <= 60) noteInput = it
                            },
                            singleLine = false,
                            maxLines = 3,
                            textStyle = TextStyle(
                                fontFamily = BuildXFontFamily,
                                fontSize = 15.sp,
                                color = colors.primaryText,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(colors.primaryBlue),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (noteInput.isEmpty()) {
                                        Text(
                                            text = strings.notePlaceholder,
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

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasActiveNote) {
                    // Delete Note button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(colors.surfaceMuted)
                            .clickable {
                                onDeleteNote()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.deleteNoteButton,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.destructiveRed
                        )
                    }
                }

                // Share / Update Note Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(if (canPost) colors.primaryBlue else colors.surfaceMuted)
                        .clickable(enabled = canPost && !isLoading) {
                            onCreateNote(noteInput.trim())
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = colors.onPrimaryBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = strings.postNoteButton,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canPost) colors.onPrimaryBlue else colors.secondaryText.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
