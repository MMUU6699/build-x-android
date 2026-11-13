package app.buildx.chat

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.buildx.chat.data.model.AccountInfo
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import app.buildx.chat.ui.screens.AboutAccountSheet
import app.buildx.chat.ui.theme.BuildXTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Full-screen "About this profile" page fidelity suite.
 *
 * Covers light English, dark Arabic and the expanded former-usernames state so
 * every metadata row is verified against the design system.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel6)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class ProfileAboutScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir = File("../docs/design/screenshots/profile_about").apply { mkdirs() }

    private fun sampleAccountInfo() = AccountInfo(
        joinedAt = "2026-11-11T11:58:28.043338+00:00",
        countryCode = "IQ",
        verifiedAt = "2026-11-12T09:00:00+00:00",
        usernameChangeCount = 2,
        followsYou = true,
        isSelf = false,
        userId = "3b254c79-8fd9-48e7-8110-f4aa285e28ef",
        username = "shopramahoo",
        displayName = "Shopra Mahoo",
        avatarUrl = null,
        bio = "Design and code.",
        isVerified = true,
        formerUsernames = listOf("shopra_old", "shopra_2025")
    )

    @Composable
    private fun LocalizedContent(locale: Locale, fontScale: Float, content: @Composable () -> Unit) {
        val context = LocalContext.current
        val localizedContext = remember(context, locale) {
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            context.createConfigurationContext(config)
        }
        val density = LocalDensity.current
        val localizedDensity = Density(density = density.density, fontScale = fontScale)
        androidx.compose.runtime.DisposableEffect(locale) {
            val previous = Locale.getDefault()
            Locale.setDefault(locale)
            onDispose { Locale.setDefault(previous) }
        }
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalDensity provides localizedDensity,
            content = content
        )
    }

    private fun capture(fileName: String) {
        composeRule.mainClock.advanceTimeBy(700L)
        composeRule.waitForIdle()
        composeRule.onNode(isDialog()).captureRoboImage(
            file = File(outputDir, fileName),
            roborazziOptions = RoborazziOptions()
        )
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun about_profile_light_english() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    AboutAccountSheet(
                        accountInfo = sampleAccountInfo(),
                        isLoading = false,
                        errorMessage = null,
                        onRetry = {},
                        onDismiss = {}
                    )
                }
            }
        }
        capture("about_profile_light_en.png")
    }

    @Test
    @Config(qualifiers = "ar-rSA-w360dp-h800dp-xhdpi")
    fun about_profile_dark_arabic_font_1_3() {
        composeRule.setContent {
            LocalizedContent(Locale("ar"), 1.3f) {
                BuildXTheme(appearance = AppAppearance.DARK, language = AppLanguage.ARABIC) {
                    AboutAccountSheet(
                        accountInfo = sampleAccountInfo(),
                        isLoading = false,
                        errorMessage = null,
                        onRetry = {},
                        onDismiss = {}
                    )
                }
            }
        }
        capture("about_profile_dark_ar_font13.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun about_profile_former_usernames_expanded() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    AboutAccountSheet(
                        accountInfo = sampleAccountInfo(),
                        isLoading = false,
                        errorMessage = null,
                        onRetry = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.onNodeWithText("Former usernames").performClick()
        capture("about_profile_former_expanded_light_en.png")
    }
}
