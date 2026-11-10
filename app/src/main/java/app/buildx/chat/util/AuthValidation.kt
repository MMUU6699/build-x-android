package app.buildx.chat.util

/**
 * Real client-side filtering and anti-spam validation for registration and authentication.
 * Filters disposable/temporary emails, validates username format, and enforces password standards.
 */
object AuthValidation {

    private val DISPOSABLE_EMAIL_DOMAINS = setOf(
        "mailinator.com", "tempmail.com", "temp-mail.org", "10minutemail.com",
        "guerrillamail.com", "guerrillamail.info", "sharklasers.com", "grr.la",
        "guerrillamail.biz", "guerrillamail.de", "guerrillamail.net", "guerrillamail.org",
        "yopmail.com", "yopmail.fr", "yopmail.net", "cool.fr.nf", "jetable.fr.nf",
        "throwawaymail.com", "dispostable.com", "fake.com", "trashmail.com",
        "trashmail.net", "trashmail.me", "mohmal.com", "burnermail.io",
        "getairmail.com", "mytemp.email", "nada.ltd", "tempmailaddress.com",
        "generator.email", "emailondeck.com", "crazymailing.com", "maildrop.cc",
        "temp-mail.io", "fakemailgenerator.com", "mohmal.in", "inboxkitten.com"
    )

    fun isDisposableEmail(email: String): Boolean {
        val clean = email.trim().lowercase()
        val domain = clean.substringAfter("@", "")
        return domain in DISPOSABLE_EMAIL_DOMAINS
    }

    fun isValidEmailFormat(email: String): Boolean {
        val clean = email.trim()
        val pattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        return clean.matches(Regex(pattern))
    }

    fun isValidUsername(username: String): Boolean {
        val clean = username.lowercase().trim().removePrefix("@")
        if (clean.length !in 3..24) return false
        if (!clean.matches(Regex("^[a-z0-9._]+$"))) return false
        if (clean.startsWith(".") || clean.endsWith(".") || clean.contains("..")) return false
        return true
    }

    fun isWeakPassword(password: String): Boolean {
        if (password.length < 6) return true
        val commonWeak = setOf(
            "123456", "password", "12345678", "qwerty", "111111", "123123",
            "admin", "buildx", "abcdef", "123456789"
        )
        return password.lowercase() in commonWeak
    }

    fun validateEmail(email: String): String? {
        val clean = email.trim()
        if (clean.isBlank()) return "Please enter your email address."
        if (!isValidEmailFormat(clean)) return "Please enter a valid email format."
        if (isDisposableEmail(clean)) return "Disposable / temporary email addresses are not allowed."
        return null
    }

    fun validateUsername(username: String): String? {
        val clean = username.lowercase().trim().removePrefix("@")
        if (clean.length < 3) return "Username must be at least 3 characters."
        if (clean.length > 24) return "Username cannot exceed 24 characters."
        if (!clean.matches(Regex("^[a-z0-9._]+$"))) return "Username can only contain lowercase letters, numbers, dots and underscores."
        if (clean.startsWith(".") || clean.endsWith(".")) return "Username cannot start or end with a dot."
        if (clean.contains("..")) return "Username cannot contain consecutive dots."
        return null
    }

    fun validatePassword(password: String): String? {
        if (password.length < 6) return "Password must be at least 6 characters."
        if (isWeakPassword(password)) return "Please choose a stronger password."
        return null
    }
}
