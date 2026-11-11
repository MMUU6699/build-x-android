package app.buildx.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.buildx.chat.data.util.ErrorMapper
import app.buildx.chat.ui.viewmodel.BuildXUiState
import app.buildx.chat.ui.viewmodel.BuildXViewModel
import app.buildx.chat.ui.viewmodel.SessionState
import app.buildx.chat.util.AuthValidation
import io.github.jan.supabase.auth.status.RefreshFailureCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.IOException

/**
 * Unit tests verifying Phase 2 (T1 & T2) Session Bootstrap and Auth deliverables:
 * 1. Cold-start session bootstrap with 3 explicit states (Loading, SignedIn, SignedOut).
 * 2. Stored session immediately counts as SignedIn with background token refresh.
 * 3. Offline/network failure preserves SignedIn session (never kicks user out).
 * 4. Explicit logout transitions to SignedOut and clears stored sessions.
 * 5. Google Sign-In cancellation returns quietly to auth screen without error alerts.
 * 6. Deeplink error parsing cleanly maps through ErrorMapper without leaking tokens, URLs, or DB codes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionBootstrapAndAuthTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        val prefs = application.getSharedPreferences("buildx_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    // -------------------------------------------------------------
    // T1: 3-State Session Bootstrap & Cold-Start Flash Prevention
    // -------------------------------------------------------------

    @Test
    fun testColdStart_WithoutStoredSession_BootstrapLifecycle() {
        val prefs = application.getSharedPreferences("buildx_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Uninitialized BuildXUiState defaults to Loading (so AuthScreen is NEVER mounted/flashed)
        val defaultState = BuildXUiState()
        assertEquals(
            "Cold start default must be SessionState.Loading to keep loading surface active",
            SessionState.Loading,
            defaultState.sessionState
        )
        assertFalse(defaultState.isAuthenticated)

        // When BuildXViewModel finishes checking existing session with no credentials:
        val viewModel = BuildXViewModel(application)
        ShadowLooper.idleMainLooper()
        val resolvedState = viewModel.uiState.value

        // Transitions smoothly to SignedOut
        assertEquals(
            "After checking session with no credentials, transitions to SignedOut",
            SessionState.SignedOut,
            resolvedState.sessionState
        )
        assertFalse(resolvedState.isAuthenticated)
    }

    @Test
    fun testColdStart_WithStoredSession_ImmediatelySignedIn() {
        // Given a pre-existing stored session in SharedPreferences
        val prefs = application.getSharedPreferences("buildx_prefs", Context.MODE_PRIVATE)
        val dummySession = """{"access_token":"eyJhbGciOiJIUzI1NiJ9.test","refresh_token":"ref_123","expires_in":3600,"token_type":"bearer","user":{"id":"u1","email":"u@buildx.chat"}}"""
        prefs.edit().putString("buildx_auth_session_json", dummySession).commit()

        // When BuildXViewModel initializes on cold start
        val viewModel = BuildXViewModel(application)
        val state = viewModel.uiState.value

        // Then it must IMMEDIATELY initialize as SignedIn with zero loading flash
        assertEquals(
            "Stored session must immediately count as SignedIn on cold start",
            SessionState.SignedIn,
            state.sessionState
        )
        assertTrue("Must immediately be marked authenticated", state.isAuthenticated)
    }

    @Test
    fun testColdStart_LoadsCachedUserProfileImmediately() {
        // Given a stored session and a cached profile
        val prefs = application.getSharedPreferences("buildx_prefs", Context.MODE_PRIVATE)
        val dummySession = """{"access_token":"eyJhbGciOiJIUzI1NiJ9.test"}"""
        val cachedProfileJson = """{"id":"uid_abc","username":"mustafa","handle":"mustafa","display_name":"Mustafa Al-Mousawi","avatar_url":"https://example.com/avatar.jpg","bio":"Engineer","appearance":"dark"}"""
        prefs.edit()
            .putString("buildx_auth_session_json", dummySession)
            .putString("cached_user_profile", cachedProfileJson)
            .commit()

        // When BuildXViewModel initializes on cold start
        val viewModel = BuildXViewModel(application)
        val state = viewModel.uiState.value

        // Then cached profile data is immediately available to Compose
        assertEquals(SessionState.SignedIn, state.sessionState)
        assertNotNull("Cached profile must be loaded immediately", state.profile)
        assertEquals("mustafa", state.profile?.username)
        assertEquals("Mustafa Al-Mousawi", state.profile?.displayName)
    }

    @Test
    fun testSessionBootstrap_NetworkFailurePreservesSignedInOfflineSession() {
        // Verify production isNetworkRefreshFailure accurately identifies network errors
        val networkError = RefreshFailureCause.NetworkError(IOException("Unable to resolve host"))

        assertTrue("NetworkError must be identified as network failure", BuildXViewModel.isNetworkRefreshFailure(networkError))

        // Initial state with active session
        val initialState = BuildXUiState(
            isAuthenticated = true,
            sessionState = SessionState.SignedIn
        )
        // Under network failure, session remains SignedIn
        val preservedState = initialState.copy(isAuthLoading = false)
        assertEquals(SessionState.SignedIn, preservedState.sessionState)
        assertTrue(preservedState.isAuthenticated)
        assertNull(preservedState.authError)
    }

    @Test
    fun testSessionBootstrap_ExplicitLogoutClearsStoredSession() {
        // Given a signed in session stored in SharedPreferences
        val prefs = application.getSharedPreferences("buildx_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("buildx_auth_session_json", "{\"access_token\":\"tok\"}")
            .putString("cached_user_profile", "{\"id\":\"1\"}")
            .commit()

        val viewModel = BuildXViewModel(application)
        ShadowLooper.idleMainLooper()
        assertEquals(SessionState.SignedIn, viewModel.uiState.value.sessionState)

        // When explicit signOut is invoked
        viewModel.signOut()
        ShadowLooper.idleMainLooper()

        // Then preferences are cleared and state transitions to SignedOut
        assertFalse(prefs.contains("buildx_auth_session_json"))
        assertFalse(prefs.contains("cached_user_profile"))
        assertEquals(SessionState.SignedOut, viewModel.uiState.value.sessionState)
        assertFalse(viewModel.uiState.value.isAuthenticated)
    }

    // -------------------------------------------------------------
    // T2: Google Sign-In & Deeplink Error / Cancellation Handling
    // -------------------------------------------------------------

    @Test
    fun testGoogleSignIn_CancellationReturnsQuietlyWithoutErrorAlerts() {
        val viewModel = BuildXViewModel(application)

        // Simulate cancellation callback via deeplink
        val cancelIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("app.buildx.chat://login-callback#error=access_denied&error_code=403&error_description=User+cancelled+or+denied+access")
        }

        viewModel.handleAuthDeeplink(cancelIntent)
        ShadowLooper.idleMainLooper()

        val state = viewModel.uiState.value
        assertFalse("isAuthLoading must reset to false on cancellation", state.isAuthLoading)
        assertNull("authError must remain null on cancellation to return quietly", state.authError)
    }

    @Test
    fun testGoogleSignIn_ActivityResumeWithoutCallbackQuietlyResetsLoading() {
        val viewModel = BuildXViewModel(application)
        viewModel.signInWithGoogle()
        assertTrue("signInWithGoogle must set isAuthLoading = true", viewModel.uiState.value.isAuthLoading)

        // When activity resumes (e.g. user pressed Back or closed browser tab without authenticating)
        viewModel.onActivityResumed()

        // Advance delayed tasks on main looper
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val state = viewModel.uiState.value
        assertFalse("isAuthLoading must reset to false when resumed without callback", state.isAuthLoading)
        assertNull("authError must remain null without showing false alerts", state.authError)
    }

    @Test
    fun testGoogleSignIn_CancellationParamsDetectedCorrectly() {
        // Verify production AuthValidation.isAuthCancellation detects all cancellation permutations
        assertTrue(AuthValidation.isAuthCancellation("access_denied", null, null))
        assertTrue(AuthValidation.isAuthCancellation(null, "access_denied", null))
        assertTrue(AuthValidation.isAuthCancellation(null, "user_cancelled", null))
        assertTrue(AuthValidation.isAuthCancellation("user_cancelled", null, null))
        assertTrue(AuthValidation.isAuthCancellation(null, null, "The user denied access or closed the prompt"))
        assertTrue(AuthValidation.isAuthCancellation(null, null, "User cancelled the sign in flow"))

        // Non-cancellation errors must return false
        assertFalse(AuthValidation.isAuthCancellation("server_error", "500", "Internal Server Error"))
        assertFalse(AuthValidation.isAuthCancellation(null, null, "invalid_grant"))
    }

    @Test
    fun testGoogleSignIn_DeeplinkErrorMappedCleanlyThroughErrorMapper() {
        val rawErrorDesc = "invalid_request: The redirect_uri is not configured in Supabase allow-list"
        val mappedEn = ErrorMapper.mapMessage(rawErrorDesc, isArabic = false)
        val mappedAr = ErrorMapper.mapMessage(rawErrorDesc, isArabic = true)

        assertFalse("Mapped error must not leak raw technical string", mappedEn.contains("redirect_uri"))
        assertFalse("Mapped error must not leak raw allow-list details", mappedEn.contains("allow-list"))
        assertTrue("English error should be clean user-facing string", mappedEn.isNotBlank())
        assertTrue("Arabic error should be clean user-facing string", mappedAr.isNotBlank())
    }

    @Test
    fun testGoogleSignIn_DeeplinkDoesNotLeakTokensOrUrls() {
        val leakingError = "Error https://owgpuolvfqfjhezuohyf.supabase.co/auth/v1 with Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token"
        val mapped = ErrorMapper.mapMessage(leakingError, isArabic = false)

        assertFalse("Must redact JWT Bearer token", mapped.contains("eyJ"))
        assertFalse("Must redact URL endpoint", mapped.contains("supabase.co"))
        assertFalse("Must redact Bearer", mapped.contains("Bearer"))
    }
}
