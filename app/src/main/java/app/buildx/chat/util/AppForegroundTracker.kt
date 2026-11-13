package app.buildx.chat.util

/**
 * Single source of truth for "is the user actually looking at the app, and at
 * which screen". Notification suppression and the assistant reply rule both
 * depend on it, so it is updated from the activity lifecycle and from the
 * navigation graph.
 */
object AppForegroundTracker {

    @Volatile
    var isForeground: Boolean = false
        private set

    @Volatile
    var currentRoute: String? = null
        private set

    fun onActivityStarted() {
        isForeground = true
    }

    fun onActivityStopped() {
        isForeground = false
    }

    fun updateRoute(route: String?) {
        currentRoute = route
    }

    /**
     * True only when the app is in the foreground and the active destination
     * starts with [prefix]. Backgrounded apps always return false so a pending
     * notification is never swallowed.
     */
    fun isViewing(prefix: String): Boolean =
        isForeground && currentRoute?.startsWith(prefix) == true
}
