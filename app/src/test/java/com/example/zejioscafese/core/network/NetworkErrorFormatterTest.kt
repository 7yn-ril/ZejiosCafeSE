package com.example.zejioscafese.core.network

import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NetworkErrorFormatterTest {

    private val fallback = "Something went wrong."

    // ── host resolution failures ──────────────────────────────────────────────

    @Test
    fun toUserMessage_unknownHostException_returnsNoInternetMessage() {
        val message = NetworkErrorFormatter.toUserMessage(
            exception = UnknownHostException("Unable to resolve host"),
            fallbackMessage = fallback
        )
        assertTrue(message.contains("internet", ignoreCase = true))
        assertFalse(message.contains("ColdBoot", ignoreCase = true))
    }

    @Test
    fun toUserMessage_wrappedUnknownHostException_recursesThroughCause() {
        val inner = UnknownHostException("host not found")
        val outer = RuntimeException("wrapper layer", inner)
        val message = NetworkErrorFormatter.toUserMessage(outer, fallback)
        assertTrue(message.contains("internet", ignoreCase = true))
    }

    @Test
    fun toUserMessage_unresolvedAddressException_returnsNoInternetMessage() {
        val message = NetworkErrorFormatter.toUserMessage(
            exception = UnresolvedAddressException(),
            fallbackMessage = fallback
        )
        assertTrue(message.contains("internet", ignoreCase = true))
    }

    // ── anonymous auth setup issue ────────────────────────────────────────────

    @Test
    fun toUserMessage_anonymousSignInIssue_returnsExceptionMessage() {
        val ex = IllegalStateException(
            "Enable Anonymous Sign-Ins in Supabase Authentication so the app can load data."
        )
        val message = NetworkErrorFormatter.toUserMessage(ex, fallback)
        assertEquals(ex.message, message)
    }

    // ── generic exceptions ────────────────────────────────────────────────────

    @Test
    fun toUserMessage_genericExceptionWithMessage_returnsExceptionMessage() {
        val ex = RuntimeException("Database unavailable")
        val message = NetworkErrorFormatter.toUserMessage(ex, fallback)
        assertEquals("Database unavailable", message)
    }

    @Test
    fun toUserMessage_genericExceptionWithBlankMessage_returnsFallback() {
        val ex = RuntimeException("   ")
        val message = NetworkErrorFormatter.toUserMessage(ex, fallback)
        assertEquals(fallback, message)
    }

    @Test
    fun toUserMessage_genericExceptionWithNullMessage_returnsFallback() {
        val ex = RuntimeException(null as String?)
        val message = NetworkErrorFormatter.toUserMessage(ex, fallback)
        assertEquals(fallback, message)
    }

    // ── deep cause chain ─────────────────────────────────────────────────────

    @Test
    fun toUserMessage_deeplyNestedHostError_stillReturnsInternetMessage() {
        var cause: Throwable = UnknownHostException("No address associated with hostname")
        repeat(8) { cause = RuntimeException("wrap", cause) }
        val message = NetworkErrorFormatter.toUserMessage(cause, fallback)
        assertTrue(message.contains("internet", ignoreCase = true))
    }

    @Test
    fun toUserMessage_causeChainExceedsDepthLimit_returnsFallback() {
        // Use null-message wrappers so the outermost exception has no message;
        // the UnknownHostException is buried beyond MAX_CAUSE_DEPTH (10), so
        // isHostResolutionFailure() returns false and the fallback is used.
        var cause: Throwable = UnknownHostException("buried too deep")
        repeat(15) { cause = RuntimeException(null as String?, cause) }
        val message = NetworkErrorFormatter.toUserMessage(cause, fallback)
        assertEquals(fallback, message)
    }
}
