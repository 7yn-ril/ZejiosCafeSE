package com.example.zejioscafese.core.supabase

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

object SupabaseSessionHelper {

    private val sessionMutex = Mutex()

    suspend fun ensureValidSession(
        client: SupabaseClient,
        signInErrorMessage: String = DEFAULT_SIGN_IN_ERROR_MESSAGE
    ): String? {
        return sessionMutex.withLock {
            val auth = client.pluginManager.getPlugin(Auth)
            auth.awaitInitialization()

            val existingSession = auth.currentSessionOrNull()
            when {
                existingSession == null -> signInAnonymously(auth, signInErrorMessage)
                existingSession.expiresAt <= Clock.System.now() -> {
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
    private const val DEFAULT_SIGN_IN_ERROR_MESSAGE =
        "Enable Anonymous Sign-Ins in Supabase Authentication so the app can load data."
}
