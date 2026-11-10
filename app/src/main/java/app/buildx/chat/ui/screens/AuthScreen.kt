package app.buildx.chat.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.R
import app.buildx.chat.ui.components.HeaderCircleButton
import app.buildx.chat.ui.components.MarkLoader
import app.buildx.chat.ui.components.pressScaleClickable
import app.buildx.chat.ui.theme.BuildXTheme
import app.buildx.chat.util.AuthValidation
import kotlinx.coroutines.delay

enum class AuthViewMode {
    WELCOME,
    SIGN_IN,
    SIGN_UP
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (identifier: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, displayName: String, handle: String) -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BuildXTheme.colors
    val strings = BuildXTheme.strings

    var viewMode by remember { mutableStateOf(AuthViewMode.WELCOME) }

    // Form states
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    when (viewMode) {
        AuthViewMode.WELCOME -> {
            // Main Landing Screen matching user's Image 1
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top & Center Branding Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // New 12-spoke rotating mark in place of "Build X" text
                    MarkLoader(
                        size = 84.dp,
                        isSpinning = true,
                        useBrandGradient = true
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = strings.askBuildXAbout,
                        color = colors.primaryText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Undulating cycling text animation for examples
                    val examples = remember(strings) {
                        listOf(
                            strings.exampleCoding,
                            strings.exampleIdeas,
                            strings.exampleThinking,
                            strings.exampleResearch,
                            strings.exampleDrafting
                        )
                    }
                    var currentExampleIndex by remember { mutableIntStateOf(0) }

                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(2800L)
                            currentExampleIndex = (currentExampleIndex + 1) % examples.size
                        }
                    }

                    AnimatedContent(
                        targetState = examples[currentExampleIndex],
                        transitionSpec = {
                            (slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()) togetherWith
                                (slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut())
                        },
                        label = "rollingExampleText"
                    ) { targetText ->
                        Text(
                            text = targetText,
                            color = colors.primaryBlue,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                // Bottom Pinned Black Container (~32dp top rounded corners) matching Image 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(Color.Black)
                        .padding(horizontal = 20.dp, vertical = 26.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. "Continue with Google" pill button (White pill, black text)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .pressScaleClickable(
                                    enabled = !isLoading,
                                    onClick = onGoogleSignIn
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.continueWithGoogle,
                                    color = Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. "Sign up" pill button (Light grey pill #E5E7EB)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5E7EB))
                                .pressScaleClickable(
                                    enabled = !isLoading,
                                    onClick = {
                                        onDismissError()
                                        localError = null
                                        viewMode = AuthViewMode.SIGN_UP
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.signUpButton,
                                color = Color(0xFF111827),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. "Log in" pill button (Dark bordered pill)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF111113))
                                .border(1.2.dp, Color(0xFF2C2C30), CircleShape)
                                .pressScaleClickable(
                                    enabled = !isLoading,
                                    onClick = {
                                        onDismissError()
                                        localError = null
                                        viewMode = AuthViewMode.SIGN_IN
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.logInButton,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        AuthViewMode.SIGN_IN -> {
            // Dedicated Sign In screen with username OR email
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top bar with back button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = strings.closeButton,
                        onClick = { viewMode = AuthViewMode.WELCOME }
                    )
                    Spacer(modifier = Modifier.size(44.dp))
                }

                MarkLoader(
                    size = 64.dp,
                    isSpinning = isLoading
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = strings.welcomeBackHeading,
                    color = colors.primaryText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = strings.welcomeBackSubtitle,
                    color = colors.secondaryText,
                    fontSize = 14.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                val activeError = localError ?: errorMessage
                if (!activeError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.destructiveRed.copy(alpha = 0.1f))
                            .clickable {
                                onDismissError()
                                localError = null
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = activeError,
                            color = colors.destructiveRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                AuthTextField(
                    value = identifier,
                    onValueChange = {
                        identifier = it
                        localError = null
                    },
                    hintText = strings.usernameOrEmail,
                    keyboardType = KeyboardType.Text
                )

                Spacer(modifier = Modifier.height(12.dp))

                AuthTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    hintText = strings.passwordLabel,
                    keyboardType = KeyboardType.Password,
                    isPassword = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Blue Pill CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLoading) colors.primaryBlue.copy(alpha = 0.6f) else colors.primaryBlue
                        )
                        .pressScaleClickable(
                            enabled = !isLoading,
                            onClick = {
                                if (identifier.isBlank() || password.isBlank()) {
                                    localError = "Please enter your username/email and password."
                                    return@pressScaleClickable
                                }
                                onSignIn(identifier.trim(), password)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoading) strings.doneLabel else strings.logInButton,
                        color = colors.onPrimaryBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "New to Build X? ",
                        color = colors.secondaryText,
                        fontSize = 14.sp
                    )
                    Text(
                        text = strings.signUpButton,
                        color = colors.primaryBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            onDismissError()
                            localError = null
                            viewMode = AuthViewMode.SIGN_UP
                        }
                    )
                }
            }
        }

        AuthViewMode.SIGN_UP -> {
            // Dedicated Sign Up screen requiring unique username & anti-disposable check
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeaderCircleButton(
                        iconRes = R.drawable.ic_back,
                        contentDescription = strings.closeButton,
                        onClick = { viewMode = AuthViewMode.WELCOME }
                    )
                    Spacer(modifier = Modifier.size(44.dp))
                }

                MarkLoader(
                    size = 64.dp,
                    isSpinning = isLoading
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = strings.createAccountHeading,
                    color = colors.primaryText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = strings.createAccountSubtitle,
                    color = colors.secondaryText,
                    fontSize = 14.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                val activeError = localError ?: errorMessage
                if (!activeError.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.destructiveRed.copy(alpha = 0.1f))
                            .clickable {
                                onDismissError()
                                localError = null
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = activeError,
                            color = colors.destructiveRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Mandatory Username Input with leading "@"
                AuthTextField(
                    value = if (handle.isEmpty()) "" else "@$handle",
                    onValueChange = {
                        handle = it.removePrefix("@").lowercase().replace(" ", "")
                        localError = null
                    },
                    hintText = "@username",
                    keyboardType = KeyboardType.Text
                )

                Spacer(modifier = Modifier.height(12.dp))

                AuthTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        localError = null
                    },
                    hintText = "Full name (optional)",
                    keyboardType = KeyboardType.Text
                )

                Spacer(modifier = Modifier.height(12.dp))

                AuthTextField(
                    value = email,
                    onValueChange = {
                        email = it.trim()
                        localError = null
                    },
                    hintText = "Email address",
                    keyboardType = KeyboardType.Email
                )

                Spacer(modifier = Modifier.height(12.dp))

                AuthTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    hintText = "Password (6+ characters)",
                    keyboardType = KeyboardType.Password,
                    isPassword = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Blue Pill CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLoading) colors.primaryBlue.copy(alpha = 0.6f) else colors.primaryBlue
                        )
                        .pressScaleClickable(
                            enabled = !isLoading,
                            onClick = {
                                val cleanHandle = handle.trim().removePrefix("@").lowercase()
                                val handleErr = AuthValidation.validateUsername(cleanHandle)
                                if (handleErr != null) {
                                    localError = handleErr
                                    return@pressScaleClickable
                                }

                                val emailErr = AuthValidation.validateEmail(email)
                                if (emailErr != null) {
                                    localError = emailErr
                                    return@pressScaleClickable
                                }

                                val passErr = AuthValidation.validatePassword(password)
                                if (passErr != null) {
                                    localError = passErr
                                    return@pressScaleClickable
                                }

                                onSignUp(
                                    email.trim(),
                                    password,
                                    displayName.trim().ifBlank { cleanHandle },
                                    cleanHandle
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoading) strings.doneLabel else strings.signUpButton,
                        color = colors.onPrimaryBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Already have an account? ",
                        color = colors.secondaryText,
                        fontSize = 14.sp
                    )
                    Text(
                        text = strings.logInButton,
                        color = colors.primaryBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            onDismissError()
                            localError = null
                            viewMode = AuthViewMode.SIGN_IN
                        }
                    )
                }
            }
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
