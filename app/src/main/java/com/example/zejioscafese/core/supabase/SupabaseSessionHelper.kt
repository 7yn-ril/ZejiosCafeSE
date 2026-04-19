package com.example.zejioscafese.core.supabase

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

object SupabaseSessionHelper {

    private val sessionMutex = Mutex()

    @Volatile
    private var lastInvalidatedAccessToken: String? = null

    suspend fun ensureValidSession(
        client: SupabaseClient,
        signInErrorMessage: String = DEFAULT_SIGN_IN_ERROR_MESSAGE
    ): String? = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            val auth = client.pluginManager.getPlugin(Auth)
            auth.awaitInitialization()

            val existingSession = auth.currentSessionOrNull()
            val nowWithBuffer = Clock.System.now().plus(SESSION_REFRESH_BUFFER)
            when {
                existingSession == null -> signInAnonymously(auth, signInErrorMessage)
                existingSession.expiresAt <= nowWithBuffer -> {
                    try {
                        auth.refreshCurrentSession()
                    } catch (exception: Exception) {
                        Log.w(
                            TAG,
                            "Failed to refresh expired Supabase session. Recreating anonymous session.",
                            exception
                        )
                        signInAnonymously(auth, signInErrorMessage)
                    }
                }
            }

            if (auth.currentSessionOrNull() == null) {
                signInAnonymously(auth, signInErrorMessage)
            }

            if (!auth.isAutoRefreshRunning) {
                runCatching { auth.startAutoRefreshForCurrentSession() }
                    .onFailure { exception ->
                        Log.w(TAG, "Failed to start Supabase auto-refresh for the current session.", exception)
                    }
            }

            auth.currentUserOrNull()?.id
        }
    }

    suspend fun <T> withJwtRetry(
        client: SupabaseClient,
        signInErrorMessage: String = DEFAULT_SIGN_IN_ERROR_MESSAGE,
        block: suspend () -> T
    ): T {
        ensureValidSession(client, signInErrorMessage)
        return try {
            block()
        } catch (exception: Exception) {
            if (!isAuthFailure(exception)) throw exception
            Log.w(
                TAG,
                "Supabase rejected the stored session. Forcing re-authentication and retrying once.",
                exception
            )
            invalidateStaleSession(client, signInErrorMessage)
            ensureValidSession(client, signInErrorMessage)
            block()
        }
    }

    /**
     * Clears the cached session only if we have not already replaced it for another
     * concurrent caller. Without this dedup, every parallel repository request would
     * trigger its own clearSession + signInAnonymously round-trip, stacking up a
     * chain of serialized sign-ins that starves the main thread.
     */
    private suspend fun invalidateStaleSession(
        client: SupabaseClient,
        signInErrorMessage: String
    ) {
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                val auth = client.pluginManager.getPlugin(Auth)
                auth.awaitInitialization()
                val currentToken = auth.currentSessionOrNull()?.accessToken
                if (currentToken != null && currentToken == lastInvalidatedAccessToken) {
                    return@withLock
                }
                lastInvalidatedAccessToken = currentToken
                runCatching { auth.clearSession() }
                    .onFailure { exception ->
                        Log.w(TAG, "Failed to clear rejected Supabase session.", exception)
                    }
            }
        }
    }

    private fun isAuthFailure(exception: Throwable): Boolean {
        var cause: Throwable? = exception
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            val className = cause::class.simpleName.orEmpty()
            val message = cause.message.orEmpty()
            val matches = className.contains("Unauthorized", ignoreCase = true) ||
                message.contains("JWT", ignoreCase = true) ||
                message.contains("PGRST301") ||
                message.contains("invalid_token", ignoreCase = true) ||
                message.contains("\"code\":401") ||
                message.contains(" 401 ") ||
                (message.contains("expired", ignoreCase = true) &&
                    message.contains("token", ignoreCase = true))
            if (matches) return true
            cause = cause.cause
            depth += 1
        }
        return false
    }

    private suspend fun signInAnonymously(
        auth: Auth,
        signInErrorMessage: String
    ) {
        runCatching { auth.clearSession() }

        try {
            auth.signInAnonymously()
        } catch (exception: Exception) {
            throw IllegalStateException(signInErrorMessage, exception)
        }
    }

    private const val TAG = "SupabaseSessionHelper"
    private const val MAX_CAUSE_DEPTH = 5
    private val SESSION_REFRESH_BUFFER = 60.seconds
    private const val DEFAULT_SIGN_IN_ERROR_MESSAGE =
        "Enable Anonymous Sign-Ins in Supabase Authentication so the app can load data."
}
