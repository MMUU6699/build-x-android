package app.buildx.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.buildx.chat.data.model.AccountInfo
import app.buildx.chat.data.model.AppAppearance
import app.buildx.chat.data.model.AppLanguage
import app.buildx.chat.data.model.BlockedUser
import app.buildx.chat.data.model.PublicNote
import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.ui.screens.BlockedAccountsScreen
import app.buildx.chat.ui.screens.EditProfileScreen
import app.buildx.chat.ui.screens.ProfileScreen
import app.buildx.chat.ui.theme.BuildXTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Profile v2 fidelity suite.
 *
 * Covers both supported widths (360dp / 411dp), light and dark themes, English
 * LTR and Arabic RTL, and a 1.3 font scale so the layout can be checked against
 * the design system without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel6)
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class ProfileV2ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir = File("../docs/design/screenshots/profile_v2").apply { mkdirs() }

    @Before
    fun setupImageLoader() {
        TestBannerImageLoader.install()
    }

    @After
    fun tearDownImageLoader() {
        TestBannerImageLoader.reset()
    }

    private fun capture(fileName: String) {
        composeRule.mainClock.advanceTimeBy(700L)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            file = File(outputDir, fileName),
            roborazziOptions = RoborazziOptions()
        )
    }

    /** Forces resource strings (and therefore the whole tree) into the given locale. */
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
        val resultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
        val localizedDensity = Density(density = density.density, fontScale = fontScale)
        androidx.compose.runtime.DisposableEffect(locale) {
            val previous = Locale.getDefault()
            Locale.setDefault(locale)
            onDispose { Locale.setDefault(previous) }
        }
        if (resultRegistryOwner != null) {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides resultRegistryOwner,
                LocalDensity provides localizedDensity
            ) {
                content()
            }
        } else {
            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalDensity provides localizedDensity
            ) {
                content()
            }
        }
    }

    private fun ownProfile() = UserProfile(
        id = "u_own",
        handle = "layla",
        username = "layla",
        displayName = "Layla Haddad",
        bio = "Product designer in Amman. Writing about type, colour and calm interfaces.\nPortfolio: https://layla.design",
        followingCount = 1284,
        followersCount = 12400,
        isVerified = false,
        usernameReviewedAt = "2026-10-02T09:00:00Z",
        createdAt = "2020-03-14T10:00:00Z"
    )

    private fun visitedProfile(isFollowing: Boolean = false) = PublicProfile(
        id = "u_omar",
        username = "omar_f",
        displayName = "Omar Farouk",
        bio = "Engineer, cyclist and coffee person. I build small tools for Arabic typography and publish notes about them every week.\nhttps://omar.example.com",
        followingCount = 312,
        followersCount = 76000,
        isVerified = true,
        isFollowing = isFollowing,
        followsYou = true,
        countryCode = "JO",
        createdAt = "2019-08-21T10:00:00Z"
    )

    private fun note() = PublicNote(
        id = "n1",
        userId = "u_own",
        content = "Shipping the new profile today",
        expiresAt = "2026-11-12T12:00:00Z"
    )

    @Composable
    private fun OwnProfile(
        appearance: AppAppearance,
        language: AppLanguage,
        locale: Locale,
        fontScale: Float
    ) {
        LocalizedContent(locale, fontScale) {
            BuildXTheme(appearance = appearance, language = language) {
                ProfileScreen(
                    currentProfile = ownProfile(),
                    targetUsername = null,
                    publicProfile = null,
                    isLoading = false,
                    errorMessage = null,
                    activeNote = note(),
                    onBackClick = {},
                    onEditProfileClick = {},
                    onOpenNoteSheet = {},
                    onReviewUsernameClick = {},
                    onFollowClick = {},
                    onFollowingClick = {},
                    onFollowersListClick = { _, _ -> },
                    onRetry = {},
                    accountInfo = AccountInfo(
                        joinedAt = "2020-03-14T10:00:00Z",
                        countryCode = "JO",
                        usernameChangeCount = 1,
                        isSelf = true
                    )
                )
            }
        }
    }

    @Composable
    private fun VisitedProfile(
        appearance: AppAppearance,
        language: AppLanguage,
        locale: Locale,
        fontScale: Float,
        isFollowing: Boolean = false
    ) {
        LocalizedContent(locale, fontScale) {
            BuildXTheme(appearance = appearance, language = language) {
                ProfileScreen(
                    currentProfile = ownProfile(),
                    targetUsername = "omar_f",
                    publicProfile = visitedProfile(isFollowing = isFollowing),
                    isLoading = false,
                    errorMessage = null,
                    activeNote = null,
                    onBackClick = {},
                    onEditProfileClick = {},
                    onOpenNoteSheet = {},
                    onReviewUsernameClick = {},
                    onFollowClick = {},
                    onFollowingClick = {},
                    onFollowersListClick = { _, _ -> },
                    onRetry = {}
                )
            }
        }
    }

    // ---------------------------------------------------------------- 411dp

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_light_ltr_own() {
        composeRule.setContent {
            OwnProfile(AppAppearance.LIGHT, AppLanguage.ENGLISH, Locale.ENGLISH, 1f)
        }
        capture("pv2_411_light_ltr_own.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_dark_ltr_own() {
        composeRule.setContent {
            OwnProfile(AppAppearance.DARK, AppLanguage.ENGLISH, Locale.ENGLISH, 1f)
        }
        capture("pv2_411_dark_ltr_own.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_light_rtl_visited() {
        composeRule.setContent {
            VisitedProfile(AppAppearance.LIGHT, AppLanguage.ARABIC, Locale("ar"), 1f)
        }
        capture("pv2_411_light_rtl_visited.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_dark_rtl_visited_following() {
        composeRule.setContent {
            VisitedProfile(AppAppearance.DARK, AppLanguage.ARABIC, Locale("ar"), 1f, isFollowing = true)
        }
        capture("pv2_411_dark_rtl_visited_following.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_light_ltr_visited_font_1_3() {
        composeRule.setContent {
            VisitedProfile(AppAppearance.LIGHT, AppLanguage.ENGLISH, Locale.ENGLISH, 1.3f)
        }
        capture("pv2_411_light_ltr_visited_font13.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_dark_rtl_own_font_1_3() {
        composeRule.setContent {
            OwnProfile(AppAppearance.DARK, AppLanguage.ARABIC, Locale("ar"), 1.3f)
        }
        capture("pv2_411_dark_rtl_own_font13.png")
    }

    // ---------------------------------------------------------------- 360dp

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_light_ltr_visited() {
        composeRule.setContent {
            VisitedProfile(AppAppearance.LIGHT, AppLanguage.ENGLISH, Locale.ENGLISH, 1f)
        }
        capture("pv2_360_light_ltr_visited.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_dark_rtl_own() {
        composeRule.setContent {
            OwnProfile(AppAppearance.DARK, AppLanguage.ARABIC, Locale("ar"), 1f)
        }
        capture("pv2_360_dark_rtl_own.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_light_rtl_visited_font_1_3() {
        composeRule.setContent {
            VisitedProfile(AppAppearance.LIGHT, AppLanguage.ARABIC, Locale("ar"), 1.3f)
        }
        capture("pv2_360_light_rtl_visited_font13.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_dark_ltr_own_font_1_3() {
        composeRule.setContent {
            OwnProfile(AppAppearance.DARK, AppLanguage.ENGLISH, Locale.ENGLISH, 1.3f)
        }
        capture("pv2_360_dark_ltr_own_font13.png")
    }

    // ------------------------------------------------------------ edge states

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_unavailable() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    ProfileScreen(
                        currentProfile = ownProfile(),
                        targetUsername = "gone",
                        publicProfile = null,
                        isLoading = false,
                        errorMessage = null,
                        activeNote = null,
                        onBackClick = {},
                        onEditProfileClick = {},
                        onOpenNoteSheet = {},
                        onReviewUsernameClick = {},
                        onFollowClick = {},
                        onFollowingClick = {},
                        onFollowersListClick = { _, _ -> },
                        onRetry = {},
                        isProfileUnavailable = true
                    )
                }
            }
        }
        capture("pv2_411_unavailable.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_blocked_by_me() {
        val blocked = visitedProfile().copy(blockedByMe = true)
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    ProfileScreen(
                        currentProfile = ownProfile(),
                        targetUsername = "omar_f",
                        publicProfile = blocked,
                        isLoading = false,
                        errorMessage = null,
                        activeNote = null,
                        onBackClick = {},
                        onEditProfileClick = {},
                        onOpenNoteSheet = {},
                        onReviewUsernameClick = {},
                        onFollowClick = {},
                        onFollowingClick = {},
                        onFollowersListClick = { _, _ -> },
                        onRetry = {}
                    )
                }
            }
        }
        capture("pv2_411_blocked_by_me.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_skeleton() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    ProfileScreen(
                        currentProfile = ownProfile(),
                        targetUsername = "omar_f",
                        publicProfile = null,
                        isLoading = true,
                        errorMessage = null,
                        activeNote = null,
                        onBackClick = {},
                        onEditProfileClick = {},
                        onOpenNoteSheet = {},
                        onReviewUsernameClick = {},
                        onFollowClick = {},
                        onFollowingClick = {},
                        onFollowersListClick = { _, _ -> },
                        onRetry = {}
                    )
                }
            }
        }
        capture("pv2_411_skeleton.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_blocked_accounts_light() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    BlockedAccountsScreen(
                        blockedUsers = listOf(
                            BlockedUser(
                                id = "u_omar",
                                username = "omar_f",
                                displayName = "Omar Farouk",
                                isVerified = true,
                                blockedAt = "2026-11-01T10:00:00Z"
                            ),
                            BlockedUser(
                                id = "u_sara",
                                username = "sara_k",
                                displayName = "Sara Khalil",
                                blockedAt = "2026-10-18T10:00:00Z"
                            )
                        ),
                        isLoading = false,
                        errorMessage = null,
                        hasMore = false,
                        onBackClick = {},
                        onUnblock = {},
                        onLoadMore = {},
                        onRetry = {}
                    )
                }
            }
        }
        capture("pv2_411_blocked_accounts_light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_blocked_accounts_dark_rtl() {
        composeRule.setContent {
            LocalizedContent(Locale("ar"), 1.3f) {
                BuildXTheme(appearance = AppAppearance.DARK, language = AppLanguage.ARABIC) {
                    BlockedAccountsScreen(
                        blockedUsers = listOf(
                            BlockedUser(
                                id = "u_omar",
                                username = "omar_f",
                                displayName = "Omar Farouk",
                                isVerified = true,
                                blockedAt = "2026-11-01T10:00:00Z"
                            )
                        ),
                        isLoading = false,
                        errorMessage = null,
                        hasMore = false,
                        onBackClick = {},
                        onUnblock = {},
                        onLoadMore = {},
                        onRetry = {}
                    )
                }
            }
        }
        capture("pv2_360_blocked_accounts_dark_rtl.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_edit_profile_light() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    EditProfileScreen(
                        profile = ownProfile(),
                        onSaveProfile = { _, _, _ -> },
                        onPickAvatarClick = {},
                        onBackClick = {},
                        checkUsernameAvailable = { true },
                        onCountrySelected = {}
                    )
                }
            }
        }
        capture("pv2_411_edit_profile_light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_edit_profile_dark_rtl_font_1_3() {
        composeRule.setContent {
            LocalizedContent(Locale("ar"), 1.3f) {
                BuildXTheme(appearance = AppAppearance.DARK, language = AppLanguage.ARABIC) {
                    EditProfileScreen(
                        profile = ownProfile(),
                        onSaveProfile = { _, _, _ -> },
                        onPickAvatarClick = {},
                        onBackClick = {},
                        checkUsernameAvailable = { true },
                        onCountrySelected = {}
                    )
                }
            }
        }
        capture("pv2_360_edit_profile_dark_rtl_font13.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_light_ltr_banner_own() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    ProfileScreen(
                        currentProfile = ownProfile().copy(
                            avatarUrl = TestBannerImageLoader.TEST_AVATAR_URL,
                            bannerUrl = TestBannerImageLoader.TEST_BANNER_URL,
                            bannerType = "image",
                            isVerified = true
                        ),
                        targetUsername = null,
                        publicProfile = null,
                        isLoading = false,
                        errorMessage = null,
                        activeNote = null,
                        onBackClick = {},
                        onEditProfileClick = {},
                        onOpenNoteSheet = {},
                        onReviewUsernameClick = {},
                        onFollowClick = {},
                        onFollowingClick = {},
                        onFollowersListClick = { _, _ -> },
                        onRetry = {}
                    )
                }
            }
        }
        capture("pv2_411_light_ltr_banner_own.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_dark_rtl_banner_visited_font_1_3() {
        composeRule.setContent {
            LocalizedContent(Locale("ar"), 1.3f) {
                BuildXTheme(appearance = AppAppearance.DARK, language = AppLanguage.ARABIC) {
                    ProfileScreen(
                        currentProfile = ownProfile(),
                        targetUsername = "omar_f",
                        publicProfile = visitedProfile().copy(
                            avatarUrl = TestBannerImageLoader.TEST_AVATAR_URL,
                            bannerUrl = TestBannerImageLoader.TEST_BANNER_URL,
                            bannerType = "image",
                            isVerified = true
                        ),
                        isLoading = false,
                        errorMessage = null,
                        activeNote = null,
                        onBackClick = {},
                        onEditProfileClick = {},
                        onOpenNoteSheet = {},
                        onReviewUsernameClick = {},
                        onFollowClick = {},
                        onFollowingClick = {},
                        onFollowersListClick = { _, _ -> },
                        onRetry = {}
                    )
                }
            }
        }
        capture("pv2_360_dark_rtl_banner_visited_font13.png")
    }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel6)
    fun pv2_411_edit_profile_banner_light() {
        composeRule.setContent {
            LocalizedContent(Locale.ENGLISH, 1f) {
                BuildXTheme(appearance = AppAppearance.LIGHT) {
                    EditProfileScreen(
                        profile = ownProfile().copy(
                            avatarUrl = TestBannerImageLoader.TEST_AVATAR_URL,
                            bannerUrl = TestBannerImageLoader.TEST_BANNER_URL,
                            bannerType = "image",
                            isVerified = true
                        ),
                        onSaveProfile = { _, _, _ -> },
                        onPickAvatarClick = {},
                        onBackClick = {},
                        checkUsernameAvailable = { true },
                        onCountrySelected = {}
                    )
                }
            }
        }
        capture("pv2_411_edit_profile_banner_light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun pv2_360_edit_profile_banner_dark_rtl_font_1_3() {
        composeRule.setContent {
            LocalizedContent(Locale("ar"), 1.3f) {
                BuildXTheme(appearance = AppAppearance.DARK, language = AppLanguage.ARABIC) {
                    EditProfileScreen(
                        profile = ownProfile().copy(
                            avatarUrl = TestBannerImageLoader.TEST_AVATAR_URL,
                            bannerUrl = TestBannerImageLoader.TEST_BANNER_URL,
                            bannerType = "image",
                            isVerified = true
                        ),
                        onSaveProfile = { _, _, _ -> },
                        onPickAvatarClick = {},
                        onBackClick = {},
                        checkUsernameAvailable = { true },
                        onCountrySelected = {}
                    )
                }
            }
        }
        capture("pv2_360_edit_profile_banner_dark_rtl_font13.png")
    }
}
