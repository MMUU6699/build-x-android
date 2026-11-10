package app.buildx.chat.ui.screens

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (identifier: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, displayName: String, handle: String) -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onToggleLanguage: () -> Unit = {},
    onToggleAppearance: () -> Unit = {},
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
            // Main Landing Screen with Lume Studio background video, slow logo rotation, and wavy text
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
            ) {
                // Background Video from Lume Studio (hero-light.mp4)
                BackgroundVideoPlayer(
                    videoResId = R.raw.hero_light,
                    modifier = Modifier.fillMaxSize()
                )

                // Elegant ambient overlay matching Lume (opacity 50% in light mode, 60% in dark mode)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (colors.isDark) Color.Black.copy(alpha = 0.62f)
                            else colors.pageBackground.copy(alpha = 0.42f)
                        )
                )

                // Foreground Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar with Language and Theme Toggle Buttons (positioned respecting language/RTL)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderCircleButton(
                            iconRes = R.drawable.ic_language,
                            contentDescription = strings.appLanguage,
                            onClick = onToggleLanguage
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        HeaderCircleButton(
                            iconRes = R.drawable.ic_appearance,
                            contentDescription = strings.appAppearance,
                            onClick = onToggleAppearance
                        )
                    }

                    // Center Area: Lume Rosette Logo (slower than medium rotation) + Wavy Text
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Lume Rosette Logo with calm, smooth rotation (14s duration)
                        MarkLoader(
                            size = 88.dp,
                            isSpinning = false,
                            tint = colors.primaryText
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = strings.askBuildXAbout,
                            color = colors.primaryText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Wavy undulating text effect matching Lume Studio's "Designs That Captivate, Brands That Shine"
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
                                delay(3200L)
                                currentExampleIndex = (currentExampleIndex + 1) % examples.size
                            }
                        }

                        LumeWavyText(
                            text = examples[currentExampleIndex],
                            textColor = colors.primaryBlue,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // Bottom Pinned Black Container (~32dp top rounded corners)
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

/**
 * Background Video Player playing Lume's ambient video in a seamless, silent loop.
 */
@Composable
fun BackgroundVideoPlayer(
    @RawRes videoResId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPrepared by remember { mutableStateOf(false) }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            isLooping = true
            setVolume(0f, 0f) // Mute audio
        }
    }

    DisposableEffect(videoResId) {
        val afd = context.resources.openRawResourceFd(videoResId)
        try {
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mediaPlayer.setOnPreparedListener { mp ->
                isPrepared = true
                mp.start()
            }
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            try { afd.close() } catch (_: Exception) {}
        }

        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.reset()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        try {
                            mediaPlayer.setSurface(Surface(surface))
                            if (isPrepared && !mediaPlayer.isPlaying) {
                                mediaPlayer.start()
                            }
                        } catch (_: Exception) {}
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        try { mediaPlayer.setSurface(null) } catch (_: Exception) {}
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Wavy text effect recreating Lume Studio's TextEffect ("fade-in-blur" with staggered word reveals).
 */
@Composable
fun LumeWavyText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val words = remember(text) { text.split(" ") }
    var visibleWordsCount by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        visibleWordsCount = 0
        for (i in 1..words.size) {
            delay(65L)
            visibleWordsCount = i
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        words.forEachIndexed { index, word ->
            val isVisible = index < visibleWordsCount
            val alpha by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                label = "wordAlpha$index"
            )
            val scale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0.92f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                label = "wordScale$index"
            )

            Text(
                text = word,
                color = textColor,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(alpha)
                    .scale(scale)
                    .padding(horizontal = 2.5.dp)
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
