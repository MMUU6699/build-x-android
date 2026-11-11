package app.buildx.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buildx.chat.ui.components.NameWithBadge
import app.buildx.chat.ui.components.VerifiedBadge
import app.buildx.chat.ui.components.formatVerifiedDate
import app.buildx.chat.ui.theme.BuildXTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerifiedBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testVerifiedBadgeRendersAtMultipleSizes() {
        val testSizes = listOf(14.dp, 16.dp, 20.dp, 24.dp, 48.dp, 64.dp)

        composeTestRule.setContent {
            BuildXTheme {
                Column {
                    testSizes.forEachIndexed { index, size ->
                        VerifiedBadge(
                            size = size,
                            contentDescription = "Badge $index"
                        )
                    }
                }
            }
        }

        testSizes.forEachIndexed { index, _ ->
            composeTestRule.onNodeWithContentDescription("Badge $index")
                .assertIsDisplayed()
        }
    }

    @Test
    fun testNameWithBadge_UnverifiedUser() {
        composeTestRule.setContent {
            BuildXTheme {
                NameWithBadge(
                    name = "Jane Doe",
                    isVerified = false,
                    badgeContentDescription = "Verified badge"
                )
            }
        }

        composeTestRule.onNodeWithText("Jane Doe").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Verified badge").assertDoesNotExist()
    }

    @Test
    fun testNameWithBadge_VerifiedUser_SingleLine() {
        composeTestRule.setContent {
            BuildXTheme {
                Box(modifier = Modifier.width(300.dp)) {
                    NameWithBadge(
                        name = "Sarah Connor",
                        isVerified = true,
                        badgeSize = 16.dp,
                        badgeContentDescription = "Verified account"
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Sarah Connor").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Verified account", useUnmergedTree = true).assertExists()
    }

    @Test
    fun testNameWithBadge_VerifiedUser_Multiline() {
        composeTestRule.setContent {
            BuildXTheme {
                Box(modifier = Modifier.width(180.dp)) {
                    NameWithBadge(
                        name = "Dr. Christopher Alexander",
                        isVerified = true,
                        maxLines = 2,
                        fontSize = 20.sp,
                        badgeSize = 18.dp,
                        badgeContentDescription = "Verified account"
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Verified account", useUnmergedTree = true).assertExists()
    }

    @Test
    fun testNameWithBadge_RtlSupport() {
        composeTestRule.setContent {
            BuildXTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.width(250.dp)) {
                        NameWithBadge(
                            name = "محمد أحمد",
                            isVerified = true,
                            badgeContentDescription = "حساب موثق"
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("حساب موثق").assertIsDisplayed()
    }

    @Test
    fun testVerifiedDateFormattingHelper() {
        val isoTimestamp = "2026-11-10T12:00:00Z"
        val instant = Instant.parse(isoTimestamp)
        val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val expected = localDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

        // Test production formatVerifiedDate function directly
        assertEquals(expected, formatVerifiedDate(isoTimestamp))
        assertNotNull(formatVerifiedDate("2026-11-10"))
        assertNull(formatVerifiedDate(null))
        assertNull(formatVerifiedDate(""))
        assertNull(formatVerifiedDate("   "))
    }
}
