package com.example.zejioscafese.core.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

object NetworkErrorFormatter {

    fun toUserMessage(
        exception: Throwable,
        fallbackMessage: String
    ): String {
        return when {
            isAnonymousAuthSetupIssue(exception) -> {
                exception.message?.takeIf(String::isNotBlank) ?: fallbackMessage
            }

            isHostResolutionFailure(exception) -> {
                "No internet connection. Check your network settings and try again."
            }

            isTimeoutFailure(exception) -> {
                "The request timed out. Check your internet connection and try again."
            }

            isRowLevelSecurityFailure(exception) -> {
                "The database security policy blocked this change. Run the latest database policy update, then try again."
            }

            else -> safeMessage(exception) ?: fallbackMessage
        }
    }

    private fun isAnonymousAuthSetupIssue(exception: Throwable): Boolean {
        return exception.message.orEmpty()
            .contains("Enable Anonymous Sign-Ins in Supabase Authentication", ignoreCase = true)
    }

    private fun isHostResolutionFailure(exception: Throwable): Boolean {
        return anyCauseMatches(exception) { cause ->
            val message = cause.message.orEmpty()
            cause is UnknownHostException ||
                cause is UnresolvedAddressException ||
                message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("No address associated with hostname", ignoreCase = true)
        }
    }

    private fun isTimeoutFailure(exception: Throwable): Boolean {
        return anyCauseMatches(exception) { cause ->
            val message = cause.message.orEmpty()
            cause is HttpRequestTimeoutException ||
                message.contains("Request timeout has expired", ignoreCase = true) ||
                message.contains("timed out after", ignoreCase = true)
        }
    }

    private fun isRowLevelSecurityFailure(exception: Throwable): Boolean {
        return anyCauseMatches(exception) { cause ->
            val message = cause.message.orEmpty()
            message.contains("row-level security", ignoreCase = true) ||
                message.contains("violates row level security", ignoreCase = true) ||
                message.contains("violates row-level security", ignoreCase = true) ||
                message.contains("42501")
        }
    }

    private fun safeMessage(exception: Throwable): String? {
        return exception.message
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { message -> message.containsSensitiveTransportDetails() }
    }

    private fun String.containsSensitiveTransportDetails(): Boolean {
        return contains("Authorization", ignoreCase = true) ||
            contains("Bearer ", ignoreCase = true) ||
            contains("Headers:", ignoreCase = true) ||
            contains("URL:", ignoreCase = true)
    }

    private fun anyCauseMatches(
        throwable: Throwable,
        predicate: (Throwable) -> Boolean
    ): Boolean {
        var current: Throwable? = throwable
        var depth = 0

        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (predicate(current)) {
                return true
            }
            current = current.cause
            depth += 1
        }

        return false
    }

    private const val MAX_CAUSE_DEPTH = 10
}
