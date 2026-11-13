package app.buildx.chat

import app.buildx.chat.data.model.PublicProfile
import app.buildx.chat.data.model.UserProfile
import app.buildx.chat.data.model.resolveProfileBannerType
import app.buildx.chat.ui.screens.computeBannerVideoCenterCropScale
import app.buildx.chat.util.AuthValidation
import app.buildx.chat.util.AvatarImageProcessor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarImageProcessorTest {

    @Test
    fun testInSampleSizeCalculation_12MegaPixels() {
        // Typical 12MP camera photo: 4032 x 3024
        val sampleSize = AvatarImageProcessor.calculateInSampleSize(
            origWidth = 4032,
            origHeight = 3024,
            reqWidth = 1920,
            reqHeight = 1920
        )
        // 4032 / 2 = 2016 >= 1920, 3024 / 2 = 1512 < 1920 -> sampleSize = 2
        assertEquals(2, sampleSize)
    }

    @Test
    fun testInSampleSizeCalculation_48MegaPixels() {
        // High-res 48MP camera photo: 8000 x 6000
        val sampleSize = AvatarImageProcessor.calculateInSampleSize(
            origWidth = 8000,
            origHeight = 6000,
            reqWidth = 1920,
            reqHeight = 1920
        )
        // 8000 / 2 = 4000 >= 1920; 8000 / 4 = 2000 >= 1920 -> sampleSize = 4
        assertEquals(4, sampleSize)
    }

    @Test
    fun testInSampleSizeCalculation_NormalSizeImage() {
        // Standard full HD photo: 1920 x 1080
        val sampleSize = AvatarImageProcessor.calculateInSampleSize(
            origWidth = 1920,
            origHeight = 1080,
            reqWidth = 1920,
            reqHeight = 1920
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun testInSampleSizeCalculation_SmallImage() {
        // Small photo: 400 x 400
        val sampleSize = AvatarImageProcessor.calculateInSampleSize(
            origWidth = 400,
            origHeight = 400,
            reqWidth = 1920,
            reqHeight = 1920
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun testBioValidationLimits_ProductionCode() {
        // Calls production AuthValidation methods
        val validBio = "Hello, world!\nLine 2\nLine 3\nLine 4\nLine 5" // 4 newlines = 5 lines
        assertFalse(AuthValidation.isBioLinesExceeded(validBio))
        assertTrue(AuthValidation.isValidBio(validBio))

        val invalidBioLines = "1\n2\n3\n4\n5\n6" // 5 newlines = 6 lines
        assertTrue(AuthValidation.isBioLinesExceeded(invalidBioLines))
        assertFalse(AuthValidation.isValidBio(invalidBioLines))

        val tooLongBio = "A".repeat(161)
        assertFalse(AuthValidation.isValidBio(tooLongBio))
        assertTrue(AuthValidation.isValidBio("A".repeat(160)))
    }

    @Test
    fun testUsernameFormatValidation_ProductionCode() {
        // Calls production AuthValidation.isValidUsername directly
        assertTrue(AuthValidation.isValidUsername("mustafa"))
        assertTrue(AuthValidation.isValidUsername("john.doe"))
        assertTrue(AuthValidation.isValidUsername("developer_123"))
        assertFalse(AuthValidation.isValidUsername("ab")) // too short (<3)
        assertFalse(AuthValidation.isValidUsername("a".repeat(25))) // too long (>24)
        assertFalse(AuthValidation.isValidUsername(".mustafa")) // starts with dot
        assertFalse(AuthValidation.isValidUsername("mustafa.")) // ends with dot
        assertFalse(AuthValidation.isValidUsername("john..doe")) // double dot
        assertFalse(AuthValidation.isValidUsername("user@name")) // invalid char
    }

    @Test
    fun testDisplayNameValidation_ProductionCode() {
        // Calls production AuthValidation.isValidDisplayName directly
        assertTrue(AuthValidation.isValidDisplayName("Mustafa"))
        assertTrue(AuthValidation.isValidDisplayName("A".repeat(30)))
        assertFalse(AuthValidation.isValidDisplayName(""))
        assertFalse(AuthValidation.isValidDisplayName("   "))
        assertFalse(AuthValidation.isValidDisplayName("A".repeat(31)))
    }

    @Test
    fun testValidateBannerVideoLimits_BoundaryConditions() {
        assertNull(AvatarImageProcessor.validateBannerVideoLimits(durationMs = 0L, sizeBytes = 1L))
        assertNull(
            AvatarImageProcessor.validateBannerVideoLimits(
                durationMs = AvatarImageProcessor.MAX_BANNER_VIDEO_DURATION_MS,
                sizeBytes = AvatarImageProcessor.MAX_BANNER_VIDEO_BYTES
            )
        )

        val tooLong = AvatarImageProcessor.validateBannerVideoLimits(
            durationMs = AvatarImageProcessor.MAX_BANNER_VIDEO_DURATION_MS + 1L,
            sizeBytes = 1024L
        )
        assertTrue(tooLong is AvatarImageProcessor.BannerVideoValidation.TooLong)

        val tooLarge = AvatarImageProcessor.validateBannerVideoLimits(
            durationMs = 5_000L,
            sizeBytes = AvatarImageProcessor.MAX_BANNER_VIDEO_BYTES + 1L
        )
        assertTrue(tooLarge is AvatarImageProcessor.BannerVideoValidation.TooLarge)

        assertEquals(
            AvatarImageProcessor.BannerVideoValidation.Unreadable,
            AvatarImageProcessor.validateBannerVideoLimits(durationMs = -1L, sizeBytes = 1024L)
        )
        assertEquals(
            AvatarImageProcessor.BannerVideoValidation.Unreadable,
            AvatarImageProcessor.validateBannerVideoLimits(durationMs = 2_000L, sizeBytes = 0L)
        )
    }

    @Test
    fun testResolveProfileBannerType_NormalizationAndFallback() {
        assertEquals("none", resolveProfileBannerType(null, "image"))
        assertEquals("none", resolveProfileBannerType("   ", "video"))
        assertEquals("image", resolveProfileBannerType("https://cdn.example/b.png", " IMAGE "))
        assertEquals("video", resolveProfileBannerType("https://cdn.example/b", " Video "))
        assertEquals(
            "video",
            resolveProfileBannerType("https://cdn.example/u1/banners/clip.mp4?token=abc", "none")
        )
        assertEquals(
            "video",
            resolveProfileBannerType("https://cdn.example/u1/banners/clip.WEBM", "")
        )
        assertEquals(
            "image",
            resolveProfileBannerType("https://cdn.example/u1/banners/photo.jpg", null)
        )
    }

    @Test
    fun testProfileBannerJsonSerialization_UserAndPublicProfiles() {
        val json = Json { ignoreUnknownKeys = true }

        val userProfile = json.decodeFromString<UserProfile>(
            """
            {
              "id": "u-1",
              "username": "alex_dev",
              "handle": "alex_dev",
              "display_name": "Alex",
              "banner_url": "https://cdn.example/u-1/banners/banner_1.mp4",
              "banner_type": "video"
            }
            """.trimIndent()
        )
        assertTrue(userProfile.hasBanner)
        assertEquals("video", userProfile.normalizedBannerType)

        val publicProfile = json.decodeFromString<PublicProfile>(
            """
            {
              "id": "u-2",
              "username": "sara_k",
              "display_name": "Sara",
              "banner_url": "https://cdn.example/u-2/banners/banner_2.jpg",
              "banner_type": "image"
            }
            """.trimIndent()
        )
        assertTrue(publicProfile.hasBanner)
        assertEquals("image", publicProfile.normalizedBannerType)

        val emptyBanner = json.decodeFromString<PublicProfile>(
            """
            {
              "id": "u-3",
              "username": "omar",
              "display_name": "Omar"
            }
            """.trimIndent()
        )
        assertFalse(emptyBanner.hasBanner)
        assertEquals("none", emptyBanner.normalizedBannerType)
    }

    @Test
    fun testComputeBannerVideoCenterCropScale_PreservesAspectAndFillsViewport() {
        assertEquals(1f to 1f, computeBannerVideoCenterCropScale(0, 500, 1920, 1080))
        assertEquals(1f to 1f, computeBannerVideoCenterCropScale(1000, 500, 0, 1080))

        // Same 2:1 aspect ratio -> exact fill
        val (sameX, sameY) = computeBannerVideoCenterCropScale(1000, 500, 2000, 1000)
        assertEquals(1f, sameX, 0.001f)
        assertEquals(1f, sameY, 0.001f)

        // Portrait 9:16 video inside landscape 2:1 banner -> width fills (1f), height scales > 1f
        val (portX, portY) = computeBannerVideoCenterCropScale(1000, 500, 1080, 1920)
        assertEquals(1f, portX, 0.001f)
        assertTrue(portY > 3.5f)
        assertEquals((portX * 1000f) / 1080f, (portY * 500f) / 1920f, 0.001f)

        // Ultra-wide 21:9 video inside 4:3 banner -> height fills (1f), width scales > 1f
        val (wideX, wideY) = computeBannerVideoCenterCropScale(400, 300, 2100, 900)
        assertEquals(1f, wideY, 0.001f)
        assertTrue(wideX > 1.7f)
        assertEquals((wideX * 400f) / 2100f, (wideY * 300f) / 900f, 0.001f)
    }

    @Test
    fun testUpdateProfilePullOffsetPx_RubberBandAndBounds() {
        val maxPull = 300f
        // Downward pull applies 0.46f rubber-band damping
        val pulled = app.buildx.chat.ui.screens.updateProfilePullOffsetPx(
            currentPx = 0f,
            dragDeltaPx = 100f,
            maxPullPx = maxPull
        )
        assertEquals(46f, pulled, 0.01f)

        // Cannot exceed maxPullPx
        val clamped = app.buildx.chat.ui.screens.updateProfilePullOffsetPx(
            currentPx = 280f,
            dragDeltaPx = 200f,
            maxPullPx = maxPull
        )
        assertEquals(maxPull, clamped, 0.01f)

        // Upward drag collapses 1:1 without damping down to 0
        val collapsed = app.buildx.chat.ui.screens.updateProfilePullOffsetPx(
            currentPx = 100f,
            dragDeltaPx = -40f,
            maxPullPx = maxPull
        )
        assertEquals(60f, collapsed, 0.01f)

        val zeroed = app.buildx.chat.ui.screens.updateProfilePullOffsetPx(
            currentPx = 20f,
            dragDeltaPx = -50f,
            maxPullPx = maxPull
        )
        assertEquals(0f, zeroed, 0.01f)
    }
}
