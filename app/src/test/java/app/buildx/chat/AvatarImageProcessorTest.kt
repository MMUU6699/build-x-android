package app.buildx.chat

import app.buildx.chat.util.AuthValidation
import app.buildx.chat.util.AvatarImageProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
