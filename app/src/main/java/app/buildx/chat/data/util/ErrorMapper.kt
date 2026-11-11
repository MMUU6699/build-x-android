package app.buildx.chat.data.util

import android.content.Context
import app.buildx.chat.R
import io.github.jan.supabase.auth.exception.AuthRestException
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
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Locale

/**
 * Central ErrorMapper for Build X.
 *
 * Translates Throwables, HTTP status codes, Supabase/PostgREST/Auth exceptions,
 * and raw error messages into localized, user-safe strings.
 *
 * Ensures no JWT tokens, database codes (e.g. 42501, 23505), SQL fragments,
 * or internal API endpoints are ever displayed to the user or leaked to logs.
 */
object ErrorMapper {

    data class MappedError(
        val message: String,
        val isRetryable: Boolean = false,
        val actionLabel: String? = null,
        val stringResId: Int = R.string.error_generic,
        val statusCode: Int? = null
    )

    // Regex patterns for sensitive data redaction
    private val JWT_REGEX = Regex("""(?i)\b(Bearer\s+)?eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+""")
    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""")
    private val DB_CODE_REGEX = Regex("""\b(42[0-9]{3}|23[0-9]{3}|PGRST[0-9]{3})\b""")
    private val SQL_SNIPPET_REGEX = Regex("""(?i)(GRANT|REVOKE|INSERT INTO|UPDATE|DELETE FROM|SELECT)\s+[a-zA-Z0-9_.* ]+""")
    private val TABLE_REF_REGEX = Regex("""(?i)\bpublic\.[a-zA-Z0-9_]+\b""")
    private val APIKEY_REGEX = Regex("""(?i)(apikey[:=]\s*)[a-zA-Z0-9._-]+""")

    private val SENSITIVE_HEADERS = setOf("authorization", "apikey", "cookie", "set-cookie", "x-supabase-auth")

    fun isCurrentArabic(): Boolean {
        return Locale.getDefault().language.lowercase().startsWith("ar")
    }

    private fun isContextArabic(context: Context): Boolean {
        val locales = context.resources.configuration.locales
        val loc = if (!locales.isEmpty) locales[0] else Locale.getDefault()
        return loc.language.lowercase().startsWith("ar")
    }

    /**
     * Map a Throwable to a localized user-facing message.
     */
    fun map(throwable: Throwable?, isArabic: Boolean = isCurrentArabic()): String {
        return mapDetails(throwable, isArabic).message
    }

    /**
     * Map a Throwable using an Android Context to look up string resources.
     */
    fun map(throwable: Throwable?, context: Context?): String {
        if (context == null) return map(throwable, isCurrentArabic())
        val isArabic = isContextArabic(context)
        val details = mapDetails(throwable, isArabic)
        return runCatching { context.getString(details.stringResId) }.getOrDefault(details.message)
    }

    /**
     * Map an HTTP status code to a localized user-facing message.
     */
    fun mapStatus(statusCode: Int, isArabic: Boolean = isCurrentArabic()): String {
        return mapStatusDetails(statusCode, isArabic).message
    }

    /**
     * Map an HTTP status code using an Android Context.
     */
    fun mapStatus(statusCode: Int, context: Context?): String {
        if (context == null) return mapStatus(statusCode, isCurrentArabic())
        val isArabic = isContextArabic(context)
        val details = mapStatusDetails(statusCode, isArabic)
        return runCatching { context.getString(details.stringResId) }.getOrDefault(details.message)
    }

    /**
     * Map a raw message (e.g. from a deep link error_description or SSE error event) to a localized user-facing string.
     */
    fun mapMessage(raw: String?, isArabic: Boolean = isCurrentArabic()): String {
        return mapMessageDetails(raw, isArabic).message
    }

    /**
     * Map a raw message using an Android Context.
     */
    fun mapMessage(raw: String?, context: Context?): String {
        if (context == null) return mapMessage(raw, isCurrentArabic())
        val isArabic = isContextArabic(context)
        val details = mapMessageDetails(raw, isArabic)
        return runCatching { context.getString(details.stringResId) }.getOrDefault(details.message)
    }

    /**
     * Detailed mapping of a Throwable with retryable flag and action label.
     */
    fun mapDetails(throwable: Throwable?, isArabic: Boolean = isCurrentArabic()): MappedError {
        if (throwable == null) {
            return genericError(isArabic)
        }

        // Cancellation should not be treated as a visible error if it's normal coroutine cancellation
        if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
            return MappedError(
                message = if (isArabic) "تم إلغاء العملية." else "Operation cancelled.",
                isRetryable = false,
                stringResId = R.string.error_generic
            )
        }

        // Timeouts
        if (isTimeout(throwable)) {
            return MappedError(
                message = if (isArabic) "انتهت مهلة الطلب. يرجى المحاولة مرة أخرى." else "The request timed out. Please try again.",
                isRetryable = true,
                actionLabel = if (isArabic) "إعادة المحاولة" else "Retry",
                stringResId = R.string.error_timeout
            )
        }

        // Network / connectivity errors
        if (isNetworkFailure(throwable)) {
            return MappedError(
                message = if (isArabic) "أنت غير متصل بالإنترنت. يرجى التحقق من اتصالك." else "You are offline. Please check your internet connection.",
                isRetryable = true,
                actionLabel = if (isArabic) "إعادة المحاولة" else "Retry",
                stringResId = R.string.error_network_offline
            )
        }

        // Supabase Auth specific exceptions (subclasses of RestException, check before generic RestException)
        if (throwable is AuthWeakPasswordException) {
            return MappedError(
                message = if (isArabic) "كلمة المرور ضعيفة جداً. يرجى اختيار كلمة مرور أقوى." else "Password is too weak. Please choose a stronger password.",
                isRetryable = false,
                stringResId = R.string.error_weak_password,
                statusCode = 400
            )
        }

        if (throwable is AuthSessionMissingException) {
            return MappedError(
                message = if (isArabic) "يرجى تسجيل الدخول للمتابعة." else "Please sign in to continue.",
                isRetryable = false,
                actionLabel = if (isArabic) "تسجيل الدخول" else "Sign in",
                stringResId = R.string.error_session_missing,
                statusCode = 401
            )
        }

        // Supabase RestException & subclasses
        if (throwable is RestException) {
            val status = throwable.statusCode
            if (status > 0) {
                // Check if the body or description contains specific DB codes
                val desc = throwable.description ?: throwable.error ?: throwable.message ?: ""
                val specific = checkSpecificDbOrAuthError(desc, status, isArabic)
                if (specific != null) return specific
                return mapStatusDetails(status, isArabic)
            }
        }

        // Ktor ResponseException (HTTP status from client call)
        if (throwable is ResponseException) {
            val status = throwable.response.status.value
            return mapStatusDetails(status, isArabic)
        }

        // Inspect exception message for recognized error patterns
        val rawMessage = throwable.message
        if (!rawMessage.isNullOrBlank()) {
            val parsed = checkSpecificDbOrAuthError(rawMessage, null, isArabic)
            if (parsed != null) return parsed
        }

        return genericError(isArabic)
    }

    /**
     * Detailed mapping of a raw message string (e.g. from deep link query or SSE event).
     */
    fun mapMessageDetails(raw: String?, isArabic: Boolean = isCurrentArabic()): MappedError {
        if (raw.isNullOrBlank()) {
            return genericError(isArabic)
        }

        val specific = checkSpecificDbOrAuthError(raw, null, isArabic)
        if (specific != null) return specific

        // If it looks like a clean user-facing sentence already (and not a stack trace or code), sanitize and return
        val sanitized = sanitize(raw)
        if (sanitized != raw || raw.contains("Exception") || raw.contains("at ") || raw.contains("http", ignoreCase = true)) {
            return genericError(isArabic)
        }

        return MappedError(
            message = sanitized,
            isRetryable = false,
            stringResId = R.string.error_generic
        )
    }

    /**
     * Map HTTP status codes directly.
     */
    fun mapStatusDetails(statusCode: Int, isArabic: Boolean = isCurrentArabic()): MappedError {
        return when (statusCode) {
            400 -> MappedError(
                message = if (isArabic) "طلب غير صالح. يرجى التحقق من المدخلات والمحاولة مرة أخرى." else "Invalid request. Please check your input and try again.",
                isRetryable = false,
                stringResId = R.string.error_bad_request,
                statusCode = 400
            )
            401 -> MappedError(
                message = if (isArabic) "انتهت صلاحية الجلسة. يرجى تسجيل الدخول مجدداً." else "Session expired. Please sign in again.",
                isRetryable = false,
                actionLabel = if (isArabic) "تسجيل الدخول" else "Sign in",
                stringResId = R.string.error_unauthorized,
                statusCode = 401
            )
            403 -> MappedError(
                message = if (isArabic) "تم رفض الوصول. ليس لديك إذن لتنفيذ هذا الإجراء." else "Access denied. You do not have permission for this action.",
                isRetryable = false,
                stringResId = R.string.error_forbidden,
                statusCode = 403
            )
            404 -> MappedError(
                message = if (isArabic) "لم يتم العثور على العنصر المطلوب." else "The requested item was not found.",
                isRetryable = false,
                stringResId = R.string.error_not_found,
                statusCode = 404
            )
            409 -> MappedError(
                message = if (isArabic) "هذا العنصر موجود بالفعل أو يتعارض مع بيانات حالية." else "This item already exists or conflicts with existing data.",
                isRetryable = false,
                stringResId = R.string.error_conflict,
                statusCode = 409
            )
            422 -> MappedError(
                message = if (isArabic) "تعذر معالجة هذا الطلب. يرجى مراجعة المدخلات." else "Unable to process this request. Please review your input.",
                isRetryable = false,
                stringResId = R.string.error_unprocessable,
                statusCode = 422
            )
            429 -> MappedError(
                message = if (isArabic) "عدد طلبات كبير جداً. يرجى الانتظار قليلاً ثم المحاولة مرة أخرى." else "Too many requests. Please wait a moment and try again.",
                isRetryable = true,
                actionLabel = if (isArabic) "إعادة المحاولة" else "Retry",
                stringResId = R.string.error_rate_limited,
                statusCode = 429
            )
            in 500..599 -> MappedError(
                message = if (isArabic) "خطأ في الخادم. يرجى المحاولة بعد قليل." else "Server error. Please try again in a few moments.",
                isRetryable = true,
                actionLabel = if (isArabic) "إعادة المحاولة" else "Retry",
                stringResId = R.string.error_server,
                statusCode = statusCode
            )
            else -> genericError(isArabic, statusCode)
        }
    }

    private fun checkSpecificDbOrAuthError(text: String, statusCode: Int?, isArabic: Boolean): MappedError? {
        val lower = text.lowercase()

        // PostgreSQL 42501 permission denied
        if (text.contains("42501") || lower.contains("permission denied") || lower.contains("insufficient_privilege")) {
            return MappedError(
                message = if (isArabic) "تم رفض الإذن. ليس لديك صلاحية تعديل هذا العنصر." else "Permission denied. You do not have permission to modify this.",
                isRetryable = false,
                stringResId = R.string.error_permission_denied,
                statusCode = statusCode ?: 403
            )
        }

        // PostgreSQL 23505 unique violation
        if (text.contains("23505") || lower.contains("duplicate key") || lower.contains("unique constraint") || lower.contains("already taken")) {
            return MappedError(
                message = if (isArabic) "اسم المستخدم أو هذا السجل مستخدم بالفعل." else "This username or record is already taken.",
                isRetryable = false,
                stringResId = R.string.error_duplicate_entry,
                statusCode = statusCode ?: 409
            )
        }

        // PostgreSQL 23503 foreign key violation
        if (text.contains("23503") || lower.contains("foreign key constraint") || lower.contains("violates foreign key")) {
            return MappedError(
                message = if (isArabic) "السجل المرتبط غير موجود." else "Related record was not found.",
                isRetryable = false,
                stringResId = R.string.error_foreign_key,
                statusCode = statusCode ?: 400
            )
        }

        // PostgREST 0 rows
        if (text.contains("PGRST116") || lower.contains("0 rows")) {
            return MappedError(
                message = if (isArabic) "لم يتم العثور على العنصر المطلوب." else "The requested item was not found.",
                isRetryable = false,
                stringResId = R.string.error_not_found,
                statusCode = statusCode ?: 404
            )
        }

        // Auth invalid credentials
        if (lower.contains("invalid login credentials") || lower.contains("invalid_grant") || lower.contains("invalid credentials")) {
            return MappedError(
                message = if (isArabic) "البريد الإلكتروني أو كلمة المرور غير صحيحة. يرجى التحقق من بياناتك." else "Invalid email or password. Please check your credentials.",
                isRetryable = false,
                stringResId = R.string.error_invalid_credentials,
                statusCode = 400
            )
        }

        // Auth user already registered
        if (lower.contains("user already registered") || lower.contains("already exists")) {
            return MappedError(
                message = if (isArabic) "يوجد حساب مسجل بالفعل بهذا البريد الإلكتروني." else "An account with this email already exists.",
                isRetryable = false,
                stringResId = R.string.error_user_already_exists,
                statusCode = 409
            )
        }

        // Auth weak password
        if (lower.contains("weak password") || lower.contains("password should be at least")) {
            return MappedError(
                message = if (isArabic) "كلمة المرور ضعيفة جداً. يرجى اختيار كلمة مرور أقوى." else "Password is too weak. Please choose a stronger password.",
                isRetryable = false,
                stringResId = R.string.error_weak_password,
                statusCode = 400
            )
        }

        // Auth email not confirmed
        if (lower.contains("email not confirmed") || lower.contains("email address not confirmed")) {
            return MappedError(
                message = if (isArabic) "يرجى تأكيد بريدك الإلكتروني قبل تسجيل الدخول." else "Please confirm your email address before signing in.",
                isRetryable = false,
                stringResId = R.string.error_email_not_confirmed,
                statusCode = 400
            )
        }

        // Rate limit
        if (lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("over_request_rate_limit")) {
            return MappedError(
                message = if (isArabic) "عدد طلبات كبير جداً. يرجى الانتظار قليلاً ثم المحاولة مرة أخرى." else "Too many requests. Please wait a moment and try again.",
                isRetryable = true,
                actionLabel = if (isArabic) "إعادة المحاولة" else "Retry",
                stringResId = R.string.error_rate_limited,
                statusCode = 429
            )
        }

        // OAuth / Deeplink errors
        if (lower.contains("access_denied") || lower.contains("cancelled") || lower.contains("canceled")) {
            return MappedError(
                message = if (isArabic) "تم إلغاء المصادقة أو فشلت. يرجى المحاولة مرة أخرى." else "Authentication was cancelled or failed. Please try again.",
                isRetryable = false,
                stringResId = R.string.error_auth_callback
            )
        }

        if (lower.contains("bad_oauth_callback") || lower.contains("oauth state parameter missing") || lower.contains("invalid_request")) {
            return MappedError(
                message = if (isArabic) "تم إلغاء المصادقة أو فشلت. يرجى المحاولة مرة أخرى." else "Authentication was cancelled or failed. Please try again.",
                isRetryable = false,
                stringResId = R.string.error_auth_callback
            )
        }

        if (lower.contains("email link is invalid or has expired") || lower.contains("link expired")) {
            return MappedError(
                message = if (isArabic) "رابط المصادقة غير صالح أو منتهي الصلاحية. يرجى طلب رابط جديد." else "Authentication link is invalid or has expired. Please request a new link.",
                isRetryable = false,
                stringResId = R.string.error_auth_callback
            )
        }

        // Session missing / expired
        if (lower.contains("session missing") || lower.contains("refresh_token_not_found") || lower.contains("invalid refresh token") || lower.contains("token expired")) {
            return MappedError(
                message = if (isArabic) "انتهت صلاحية الجلسة. يرجى تسجيل الدخول مجدداً." else "Session expired. Please sign in again.",
                isRetryable = false,
                actionLabel = if (isArabic) "تسجيل الدخول" else "Sign in",
                stringResId = R.string.error_unauthorized,
                statusCode = 401
            )
        }

        // Storage limit
        if (lower.contains("payload too large") || lower.contains("entity too large") || lower.contains("exceeded the maximum allowed size")) {
            return MappedError(
                message = if (isArabic) "تعذر رفع الصورة. يرجى اختيار صورة بحجم أقل من 5 ميغابايت." else "Could not upload photo. Please choose an image under 5MB.",
                isRetryable = false,
                stringResId = R.string.error_avatar_upload,
                statusCode = 413
            )
        }

        return null
    }

    private fun isTimeout(t: Throwable): Boolean {
        return t is java.net.SocketTimeoutException ||
                t is SocketTimeoutException ||
                t is ConnectTimeoutException ||
                t is HttpRequestTimeoutException ||
                t is TimeoutCancellationException
    }

    private fun isNetworkFailure(t: Throwable): Boolean {
        return t is UnknownHostException ||
                t is ConnectException ||
                t is NoRouteToHostException ||
                t is PortUnreachableException ||
                t is SocketException ||
                t is HttpRequestException ||
                (t is IOException && t !is java.io.FileNotFoundException)
    }

    private fun genericError(isArabic: Boolean, statusCode: Int? = null): MappedError {
        return MappedError(
            message = if (isArabic) "حدث خطأ ما. يرجى المحاولة مرة أخرى." else "Something went wrong. Please try again.",
            isRetryable = true,
            actionLabel = if (isArabic) "إعادة المحاولة" else "Retry",
            stringResId = R.string.error_generic,
            statusCode = statusCode
        )
    }

    /**
     * Sanitizes any text string by removing JWT bearer tokens, URLs,
     * PostgreSQL error codes, SQL statements, and table references.
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw
        text = text.replace(JWT_REGEX, "[REDACTED_TOKEN]")
        text = text.replace(APIKEY_REGEX, "$1[REDACTED_KEY]")
        text = text.replace(URL_REGEX, "[URL]")
        text = text.replace(SQL_SNIPPET_REGEX, "[SQL]")
        text = text.replace(TABLE_REF_REGEX, "[RESOURCE]")
        text = text.replace(DB_CODE_REGEX, "[ERR_CODE]")
        return text.trim()
    }

    /**
     * Redacts sensitive HTTP header values (Authorization, apikey, cookie).
     */
    fun redactHeaderValue(name: String, value: String): String {
        return if (SENSITIVE_HEADERS.contains(name.lowercase().trim())) {
            "[REDACTED]"
        } else {
            value
        }
    }

    /**
     * Returns a copy of header map with sensitive header values redacted.
     */
    fun redactHeaders(headers: Map<String, List<String>>): Map<String, List<String>> {
        return headers.mapValues { (key, values) ->
            if (SENSITIVE_HEADERS.contains(key.lowercase().trim())) {
                listOf("[REDACTED]")
            } else {
                values
            }
        }
    }
}
