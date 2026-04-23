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
                "The emulator or device cannot reach the internet right now. " +
                    "Restart it and try again. If you are using the tablet emulator, run .\\run_tablet.ps1 -ColdBoot."
            }

            isTimeoutFailure(exception) -> {
                "The request to Supabase timed out. The emulator or device may have lost internet or DNS. " +
                    "Restart it and try again."
            }

            else -> exception.message?.takeIf(String::isNotBlank) ?: fallbackMessage
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
