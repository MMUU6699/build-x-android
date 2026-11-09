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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.theme.BuildXTheme

/**
 * S11 Auth Screen (Sign In / Sign Up) designed in the exact Build X visual language:
 * #FFFFFF background, 64dp rotating 8-petal ring mark, #F1F3F0 rounded input fields,
 * #0063D3 full-width primary pill button, and error banner if authentication fails.
 */
@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, displayName: String, handle: String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MarkLoader(
            size = 64.dp,
            isSpinning = isLoading
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Build X",
            color = colors.primaryText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSignUpMode) {
                "Create your account to sync conversations across devices"
            } else {
                "Sign in to continue to your conversations"
            },
            color = colors.secondaryText,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.destructiveRed.copy(alpha = 0.1f))
                    .clickable { onDismissError() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = colors.destructiveRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isSignUpMode) {
            AuthTextField(
                value = displayName,
                onValueChange = { displayName = it },
                hintText = "Display name"
            )
            Spacer(modifier = Modifier.height(12.dp))
            AuthTextField(
                value = handle,
                onValueChange = { handle = it.removePrefix("@") },
                hintText = "Handle (e.g. alex)"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            hintText = "Email address",
            keyboardType = KeyboardType.Email
        )

        Spacer(modifier = Modifier.height(12.dp))

        AuthTextField(
            value = password,
            onValueChange = { password = it },
            hintText = "Password (6+ characters)",
            keyboardType = KeyboardType.Password,
            isPassword = true
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Primary blue pill CTA (#0063D3)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (isLoading) colors.primaryBlue.copy(alpha = 0.6f) else colors.primaryBlue
                )
                .clickable(enabled = !isLoading) {
                    if (isSignUpMode) {
                        val finalName = displayName.ifBlank { email.substringBefore("@") }
                        val finalHandle = handle.ifBlank { email.substringBefore("@") }
                        onSignUp(email.trim(), password, finalName.trim(), finalHandle.trim())
                    } else {
                        onSignIn(email.trim(), password)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    isLoading -> "Please wait..."
                    isSignUpMode -> "Create account"
                    else -> "Sign in"
                },
                color = colors.onPrimaryBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isSignUpMode) "Already have an account? " else "New to Build X? ",
                color = colors.secondaryText,
                fontSize = 14.sp
            )
            Text(
                text = if (isSignUpMode) "Sign in" else "Create account",
                color = colors.primaryBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    onDismissError()
                    isSignUpMode = !isSignUpMode
                }
            )
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hintText: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    val colors = BuildXTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceMuted)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = hintText,
                color = colors.secondaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = SolidColor(colors.primaryBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
