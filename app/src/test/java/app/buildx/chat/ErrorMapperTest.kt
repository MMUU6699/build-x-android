package app.buildx.chat

import app.buildx.chat.data.util.ErrorMapper
import io.github.jan.supabase.auth.exception.AuthSessionMissingException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.UnknownHostException

class ErrorMapperTest {

    // -------------------------------------------------------------
    // HTTP Status Codes
    // -------------------------------------------------------------

    @Test
    fun testHttpStatus400_BadRequest() {
        val en = ErrorMapper.mapStatus(400, isArabic = false)
        val ar = ErrorMapper.mapStatus(400, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(400, isArabic = false)

        assertEquals("Invalid request. Please check your input and try again.", en)
        assertEquals("طلب غير صالح. يرجى التحقق من المدخلات والمحاولة مرة أخرى.", ar)
        assertFalse(details.isRetryable)
        assertEquals(400, details.statusCode)
        assertEquals(R.string.error_bad_request, details.stringResId)
    }

    @Test
    fun testHttpStatus401_Unauthorized() {
        val en = ErrorMapper.mapStatus(401, isArabic = false)
        val ar = ErrorMapper.mapStatus(401, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(401, isArabic = false)

        assertEquals("Session expired. Please sign in again.", en)
        assertEquals("انتهت صلاحية الجلسة. يرجى تسجيل الدخول مجدداً.", ar)
        assertFalse(details.isRetryable)
        assertEquals("Sign in", details.actionLabel)
        assertEquals(401, details.statusCode)
        assertEquals(R.string.error_unauthorized, details.stringResId)
    }

    @Test
    fun testHttpStatus403_Forbidden() {
        val en = ErrorMapper.mapStatus(403, isArabic = false)
        val ar = ErrorMapper.mapStatus(403, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(403, isArabic = false)

        assertEquals("Access denied. You do not have permission for this action.", en)
        assertEquals("تم رفض الوصول. ليس لديك إذن لتنفيذ هذا الإجراء.", ar)
        assertFalse(details.isRetryable)
        assertEquals(403, details.statusCode)
        assertEquals(R.string.error_forbidden, details.stringResId)
    }

    @Test
    fun testHttpStatus404_NotFound() {
        val en = ErrorMapper.mapStatus(404, isArabic = false)
        val ar = ErrorMapper.mapStatus(404, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(404, isArabic = false)

        assertEquals("The requested item was not found.", en)
        assertEquals("لم يتم العثور على العنصر المطلوب.", ar)
        assertFalse(details.isRetryable)
        assertEquals(404, details.statusCode)
        assertEquals(R.string.error_not_found, details.stringResId)
    }

    @Test
    fun testHttpStatus409_Conflict() {
        val en = ErrorMapper.mapStatus(409, isArabic = false)
        val ar = ErrorMapper.mapStatus(409, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(409, isArabic = false)

        assertEquals("This item already exists or conflicts with existing data.", en)
        assertEquals("هذا العنصر موجود بالفعل أو يتعارض مع بيانات حالية.", ar)
        assertFalse(details.isRetryable)
        assertEquals(409, details.statusCode)
        assertEquals(R.string.error_conflict, details.stringResId)
    }

    @Test
    fun testHttpStatus422_Unprocessable() {
        val en = ErrorMapper.mapStatus(422, isArabic = false)
        val ar = ErrorMapper.mapStatus(422, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(422, isArabic = false)

        assertEquals("Unable to process this request. Please review your input.", en)
        assertEquals("تعذر معالجة هذا الطلب. يرجى مراجعة المدخلات.", ar)
        assertFalse(details.isRetryable)
        assertEquals(422, details.statusCode)
        assertEquals(R.string.error_unprocessable, details.stringResId)
    }

    @Test
    fun testHttpStatus429_RateLimited() {
        val en = ErrorMapper.mapStatus(429, isArabic = false)
        val ar = ErrorMapper.mapStatus(429, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(429, isArabic = false)

        assertEquals("Too many requests. Please wait a moment and try again.", en)
        assertEquals("عدد طلبات كبير جداً. يرجى الانتظار قليلاً ثم المحاولة مرة أخرى.", ar)
        assertTrue(details.isRetryable)
        assertEquals("Retry", details.actionLabel)
        assertEquals(429, details.statusCode)
        assertEquals(R.string.error_rate_limited, details.stringResId)
    }

    @Test
    fun testHttpStatus500_ServerError() {
        val en = ErrorMapper.mapStatus(500, isArabic = false)
        val ar = ErrorMapper.mapStatus(500, isArabic = true)
        val details = ErrorMapper.mapStatusDetails(500, isArabic = false)

        assertEquals("Server error. Please try again in a few moments.", en)
        assertEquals("خطأ في الخادم. يرجى المحاولة بعد قليل.", ar)
        assertTrue(details.isRetryable)
        assertEquals("Retry", details.actionLabel)
        assertEquals(500, details.statusCode)
        assertEquals(R.string.error_server, details.stringResId)
    }

    @Test
    fun testHttpStatus503_ServiceUnavailable() {
        val en = ErrorMapper.mapStatus(503, isArabic = false)
        val details = ErrorMapper.mapStatusDetails(503, isArabic = false)

        assertEquals("Server error. Please try again in a few moments.", en)
        assertTrue(details.isRetryable)
        assertEquals(503, details.statusCode)
    }

    // -------------------------------------------------------------
    // Network Failures
    // -------------------------------------------------------------

    @Test
    fun testNetworkFailures() {
        val exceptions = listOf(
            UnknownHostException("Unable to resolve host"),
            ConnectException("Connection refused"),
            NoRouteToHostException("No route to host"),
            PortUnreachableException("Port unreachable"),
            SocketException("Software caused connection abort"),
            IOException("Network pipe broken")
        )

        for (ex in exceptions) {
            val en = ErrorMapper.map(ex, isArabic = false)
            val ar = ErrorMapper.map(ex, isArabic = true)
            val details = ErrorMapper.mapDetails(ex, isArabic = false)

            assertEquals("You are offline. Please check your internet connection.", en)
            assertEquals("أنت غير متصل بالإنترنت. يرجى التحقق من اتصالك.", ar)
            assertTrue("Expected retryable for $ex", details.isRetryable)
            assertEquals("Retry", details.actionLabel)
            assertEquals(R.string.error_network_offline, details.stringResId)
        }
    }

    // -------------------------------------------------------------
    // Timeouts
    // -------------------------------------------------------------

    @Test
    fun testTimeoutExceptions() {
        val coroutineTimeout = runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeout(1) {
                    kotlinx.coroutines.delay(50)
                }
            }
        }.exceptionOrNull()!!

        val exceptions = listOf(
            java.net.SocketTimeoutException("Read timed out"),
            SocketTimeoutException("Socket timeout"),
            ConnectTimeoutException("Connect timeout"),
            HttpRequestTimeoutException("http://localhost", 1000L),
            coroutineTimeout
        )

        for (ex in exceptions) {
            val en = ErrorMapper.map(ex, isArabic = false)
            val ar = ErrorMapper.map(ex, isArabic = true)
            val details = ErrorMapper.mapDetails(ex, isArabic = false)

            assertEquals("The request timed out. Please try again.", en)
            assertEquals("انتهت مهلة الطلب. يرجى المحاولة مرة أخرى.", ar)
            assertTrue("Expected retryable for $ex", details.isRetryable)
            assertEquals("Retry", details.actionLabel)
            assertEquals(R.string.error_timeout, details.stringResId)
        }
    }

    // -------------------------------------------------------------
    // Supabase & Postgres Errors
    // -------------------------------------------------------------

    @Test
    fun testPostgresPermissionDenied_42501() {
        val ex = RestException("error", "permission denied for table profiles (SQLSTATE 42501)", 403, "")
        val en = ErrorMapper.map(ex, isArabic = false)
        val ar = ErrorMapper.map(ex, isArabic = true)
        val details = ErrorMapper.mapDetails(ex, isArabic = false)

        assertEquals("Permission denied. You do not have permission to modify this.", en)
        assertEquals("تم رفض الإذن. ليس لديك صلاحية تعديل هذا العنصر.", ar)
        assertFalse(details.isRetryable)
        assertEquals(R.string.error_permission_denied, details.stringResId)
    }

    @Test
    fun testPostgresUniqueViolation_23505() {
        val ex = RestException("error", "duplicate key value violates unique constraint profiles_handle_key (SQLSTATE 23505)", 409, "")
        val en = ErrorMapper.map(ex, isArabic = false)
        val ar = ErrorMapper.map(ex, isArabic = true)
        val details = ErrorMapper.mapDetails(ex, isArabic = false)

        assertEquals("This username or record is already taken.", en)
        assertEquals("اسم المستخدم أو هذا السجل مستخدم بالفعل.", ar)
        assertFalse(details.isRetryable)
        assertEquals(R.string.error_duplicate_entry, details.stringResId)
    }

    @Test
    fun testPostgresForeignKeyViolation_23503() {
        val ex = RestException("error", "insert or update violates foreign key constraint (SQLSTATE 23503)", 400, "")
        val en = ErrorMapper.map(ex, isArabic = false)
        val ar = ErrorMapper.map(ex, isArabic = true)
        val details = ErrorMapper.mapDetails(ex, isArabic = false)

        assertEquals("Related record was not found.", en)
        assertEquals("السجل المرتبط غير موجود.", ar)
        assertFalse(details.isRetryable)
        assertEquals(R.string.error_foreign_key, details.stringResId)
    }

    @Test
    fun testPostgrestZeroRows_PGRST116() {
        val ex = RestException("error", "JSON object requested, multiple (or no) rows returned (PGRST116)", 406, "")
        val en = ErrorMapper.map(ex, isArabic = false)
        val ar = ErrorMapper.map(ex, isArabic = true)

        assertEquals("The requested item was not found.", en)
        assertEquals("لم يتم العثور على العنصر المطلوب.", ar)
    }

    @Test
    fun testAuthWeakPassword() {
        val ex = AuthWeakPasswordException("Weak password", 400, listOf("Password too short"))
        val en = ErrorMapper.map(ex, isArabic = false)
        val ar = ErrorMapper.map(ex, isArabic = true)
        val details = ErrorMapper.mapDetails(ex, isArabic = false)

        assertEquals("Password is too weak. Please choose a stronger password.", en)
        assertEquals("كلمة المرور ضعيفة جداً. يرجى اختيار كلمة مرور أقوى.", ar)
        assertEquals(R.string.error_weak_password, details.stringResId)
    }

    @Test
    fun testAuthSessionMissing() {
        val ex = AuthSessionMissingException(401)
        val en = ErrorMapper.map(ex, isArabic = false)
        val ar = ErrorMapper.map(ex, isArabic = true)
        val details = ErrorMapper.mapDetails(ex, isArabic = false)

        assertEquals("Please sign in to continue.", en)
        assertEquals("يرجى تسجيل الدخول للمتابعة.", ar)
        assertEquals(401, details.statusCode)
        assertEquals("Sign in", details.actionLabel)
        assertEquals(R.string.error_session_missing, details.stringResId)
    }

    // -------------------------------------------------------------
    // Raw String Messages (Deeplink & Auth Callbacks)
    // -------------------------------------------------------------

    @Test
    fun testOAuthCancelledDeeplink() {
        val msg = "access_denied: User cancelled OAuth flow"
        val en = ErrorMapper.mapMessage(msg, isArabic = false)
        val ar = ErrorMapper.mapMessage(msg, isArabic = true)

        assertEquals("Authentication was cancelled or failed. Please try again.", en)
        assertEquals("تم إلغاء المصادقة أو فشلت. يرجى المحاولة مرة أخرى.", ar)
    }

    @Test
    fun testOAuthBadCallback() {
        val msg = "OAuth state parameter missing"
        val en = ErrorMapper.mapMessage(msg, isArabic = false)
        val ar = ErrorMapper.mapMessage(msg, isArabic = true)

        assertEquals("Authentication was cancelled or failed. Please try again.", en)
        assertEquals("تم إلغاء المصادقة أو فشلت. يرجى المحاولة مرة أخرى.", ar)
    }

    @Test
    fun testInvalidCredentialsMessage() {
        val msg = "Invalid login credentials"
        val en = ErrorMapper.mapMessage(msg, isArabic = false)
        val ar = ErrorMapper.mapMessage(msg, isArabic = true)

        assertEquals("Invalid email or password. Please check your credentials.", en)
        assertEquals("البريد الإلكتروني أو كلمة المرور غير صحيحة. يرجى التحقق من بياناتك.", ar)
    }

    @Test
    fun testEmailNotConfirmedMessage() {
        val msg = "Email address not confirmed"
        val en = ErrorMapper.mapMessage(msg, isArabic = false)
        val ar = ErrorMapper.mapMessage(msg, isArabic = true)

        assertEquals("Please confirm your email address before signing in.", en)
        assertEquals("يرجى تأكيد بريدك الإلكتروني قبل تسجيل الدخول.", ar)
    }

    @Test
    fun testPayloadTooLargeMessage() {
        val msg = "Payload too large: image size exceeded the maximum allowed size"
        val en = ErrorMapper.mapMessage(msg, isArabic = false)
        val ar = ErrorMapper.mapMessage(msg, isArabic = true)

        assertEquals("Could not upload photo. Please choose an image under 5MB.", en)
        assertEquals("تعذر رفع الصورة. يرجى اختيار صورة بحجم أقل من 5 ميغابايت.", ar)
    }

    // -------------------------------------------------------------
    // Unknown & Generic Fallback
    // -------------------------------------------------------------

    @Test
    fun testUnknownThrowable_GenericFallback() {
        val ex = RuntimeException("Some unexpected internal JVM issue")
        val en = ErrorMapper.map(ex, isArabic = false)
        val ar = ErrorMapper.map(ex, isArabic = true)
        val details = ErrorMapper.mapDetails(ex, isArabic = false)

        assertEquals("Something went wrong. Please try again.", en)
        assertEquals("حدث خطأ ما. يرجى المحاولة مرة أخرى.", ar)
        assertTrue(details.isRetryable)
        assertEquals("Retry", details.actionLabel)
        assertEquals(R.string.error_generic, details.stringResId)
    }

    @Test
    fun testNullThrowable() {
        val en = ErrorMapper.map(null, isArabic = false)
        assertEquals("Something went wrong. Please try again.", en)
    }

    // -------------------------------------------------------------
    // Sanitization & Token Redaction
    // -------------------------------------------------------------

    @Test
    fun testSanitizeRemovesJwtBearerTokens() {
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im93Z3B1b2x2ZnFmamhlenVvaHlmIn0.abc123DEF456"
        val raw = "Failed request with Authorization: Bearer $token and token $token"
        val sanitized = ErrorMapper.sanitize(raw)

        assertFalse("Token should be sanitized: $sanitized", sanitized.contains("eyJhbGciOiJIUzI1Ni"))
        assertTrue("Expected [REDACTED_TOKEN] placeholder", sanitized.contains("[REDACTED_TOKEN]"))
    }

    @Test
    fun testSanitizeRemovesRestUrls() {
        val raw = "Error querying https://owgpuolvfqfjhezuohyf.supabase.co/rest/v1/profiles?id=eq.123 for user"
        val sanitized = ErrorMapper.sanitize(raw)

        assertFalse("URL should be sanitized", sanitized.contains("https://owgpuolvfqfjhezuohyf.supabase.co"))
        assertTrue("Expected [URL] placeholder", sanitized.contains("[URL]"))
    }

    @Test
    fun testSanitizeRemovesSqlAndTableReferences() {
        val raw = "REVOKE UPDATE ON public.profiles FROM authenticated; GRANT UPDATE (display_name) ON public.profiles TO authenticated;"
        val sanitized = ErrorMapper.sanitize(raw)

        assertFalse("Table ref should be sanitized", sanitized.contains("public.profiles"))
        assertFalse("SQL grant should be sanitized", sanitized.contains("GRANT UPDATE"))
    }

    @Test
    fun testSanitizeRemovesPostgresErrorCodes() {
        val raw = "Database error 42501 with constraint violation 23505"
        val sanitized = ErrorMapper.sanitize(raw)

        assertFalse(sanitized.contains("42501"))
        assertFalse(sanitized.contains("23505"))
        assertTrue(sanitized.contains("[ERR_CODE]"))
    }

    @Test
    fun testRedactSensitiveHttpHeaders() {
        val headers = mapOf(
            "Authorization" to listOf("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz"),
            "apikey" to listOf("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.anonkey"),
            "Content-Type" to listOf("application/json"),
            "Accept" to listOf("*/*")
        )

        val redacted = ErrorMapper.redactHeaders(headers)

        assertEquals(listOf("[REDACTED]"), redacted["Authorization"])
        assertEquals(listOf("[REDACTED]"), redacted["apikey"])
        assertEquals(listOf("application/json"), redacted["Content-Type"])
        assertEquals(listOf("*/*"), redacted["Accept"])
    }

    @Test
    fun testRedactHeaderValueSingle() {
        assertEquals("[REDACTED]", ErrorMapper.redactHeaderValue("Authorization", "Bearer secret"))
        assertEquals("[REDACTED]", ErrorMapper.redactHeaderValue("apikey", "secret-key"))
        assertEquals("[REDACTED]", ErrorMapper.redactHeaderValue("Cookie", "session=123"))
        assertEquals("application/json", ErrorMapper.redactHeaderValue("Content-Type", "application/json"))
    }

    // -------------------------------------------------------------
    // No Emoji Verification Across All Mapped Outputs
    // -------------------------------------------------------------

    @Test
    fun testNoEmojiInAnyErrorMessage() {
        val emojiRegex = Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]""")

        val testCases = listOf(
            ErrorMapper.mapStatus(400, false),
            ErrorMapper.mapStatus(400, true),
            ErrorMapper.mapStatus(401, false),
            ErrorMapper.mapStatus(401, true),
            ErrorMapper.mapStatus(403, false),
            ErrorMapper.mapStatus(403, true),
            ErrorMapper.mapStatus(404, false),
            ErrorMapper.mapStatus(404, true),
            ErrorMapper.mapStatus(409, false),
            ErrorMapper.mapStatus(409, true),
            ErrorMapper.mapStatus(422, false),
            ErrorMapper.mapStatus(422, true),
            ErrorMapper.mapStatus(429, false),
            ErrorMapper.mapStatus(429, true),
            ErrorMapper.mapStatus(500, false),
            ErrorMapper.mapStatus(500, true),
            ErrorMapper.map(UnknownHostException(), false),
            ErrorMapper.map(UnknownHostException(), true),
            ErrorMapper.map(SocketTimeoutException(""), false),
            ErrorMapper.map(SocketTimeoutException(""), true),
            ErrorMapper.map(RuntimeException("unknown"), false),
            ErrorMapper.map(RuntimeException("unknown"), true)
        )

        for (msg in testCases) {
            assertFalse("Message must not contain emoji: $msg", emojiRegex.containsMatchIn(msg))
        }
    }
}
