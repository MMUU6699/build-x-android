package app.buildx.chat.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.ui.theme.BuildXFontFamily
import app.buildx.chat.ui.theme.BuildXTheme
import kotlinx.coroutines.delay

/**
 * P10 Username Review Bottom Sheet:
 * One-time prompt on first launch post-update (backed by username_reviewed_at)
 * and in-profile banner sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameReviewSheet(
    currentUsername: String,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onKeepCurrent: () -> Unit,
    onConfirmNewUsername: (String) -> Unit,
    checkUsernameAvailable: suspend (String) -> Boolean
) {
    if (!isOpen) return

    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var usernameInput by remember(currentUsername) {
        mutableStateOf(currentUsername)
    }
    var isChecking by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf<Boolean?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(usernameInput) {
        val clean = usernameInput.trim().removePrefix("@").lowercase()
        if (clean == currentUsername.lowercase()) {
            isAvailable = null
            validationError = null
            return@LaunchedEffect
        }

        if (!clean.matches(Regex("^[a-z0-9._]{3,24}$")) || clean.contains("..") || clean.startsWith(".") || clean.endsWith(".")) {
            validationError = strings.usernameInvalid
            isAvailable = false
            return@LaunchedEffect
        }

        validationError = null
        isChecking = true
        delay(400L)
        try {
            isAvailable = checkUsernameAvailable(clean)
        } catch (_: Throwable) {
            isAvailable = false
        } finally {
            isChecking = false
        }
    }

    val isNewValid = usernameInput.trim().lowercase() != currentUsername.lowercase() &&
                     isAvailable == true && validationError == null

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
            Text(
                text = strings.usernameReviewBannerTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = strings.usernameReviewBannerSubtitle,
                fontSize = 14.sp,
                color = colors.secondaryText,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "@",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        BasicTextField(
                            value = usernameInput,
                            onValueChange = {
                                if (it.length <= 24) {
                                    usernameInput = it.lowercase().filter { ch ->
                                        ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '_'
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = BuildXFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.primaryText,
                                textAlign = TextAlign.Start
                            ),
                            cursorBrush = SolidColor(colors.primaryBlue),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (isChecking) {
                        MarkLoader(
                            size = 16.dp,
                            isSpinning = true,
                            tint = colors.primaryBlue
                        )
                    }
                }
            }

            // Feedback status text
            if (validationError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = validationError ?: "",
                    fontSize = 12.sp,
                    color = colors.destructiveRed
                )
            } else if (usernameInput.trim().lowercase() != currentUsername.lowercase()) {
                if (isAvailable == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.usernameAvailable,
                        fontSize = 12.sp,
                        color = Color(0xFF107C41)
                    )
                } else if (isAvailable == false && !isChecking) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.usernameUnavailable,
                        fontSize = 12.sp,
                        color = colors.destructiveRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Keep Current Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceMuted)
                        .clickable {
                            onKeepCurrent()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.keepCurrentButton,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText
                    )
                }

                // Confirm New Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isNewValid) colors.primaryBlue else colors.surfaceMuted)
                        .clickable(enabled = isNewValid) {
                            onConfirmNewUsername(usernameInput.trim().removePrefix("@").lowercase())
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.saveButton,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isNewValid) colors.onPrimaryBlue else colors.secondaryText.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
