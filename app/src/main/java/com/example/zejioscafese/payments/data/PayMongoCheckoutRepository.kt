package com.example.zejioscafese.payments.data

import com.example.zejioscafese.BuildConfig
import com.example.zejioscafese.core.supabase.SupabaseProvider
import com.example.zejioscafese.core.supabase.SupabaseSessionHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class PayMongoCheckoutLine(
    val name: String,
    val quantity: Int,
    val lineTotal: Double
)

data class PayMongoCheckoutSession(
    val id: String,
    val checkoutUrl: String,
    val referenceNumber: String?,
    val status: String?
)

data class PayMongoQrPhPayment(
    val paymentIntentId: String,
    val paymentMethodId: String,
    val qrImageUrl: String,
    val referenceNumber: String?,
    val status: String?,
    val testUrl: String?
)

data class PayMongoCheckoutStatus(
    val id: String,
    val status: String?,
    val isPaid: Boolean,
    val paymentReference: String?,
    // The actual instrument the customer chose inside the PayMongo
    // hosted checkout (gcash, maya, card, ...). Null when PayMongo did
    // not surface a resolvable method on the retrieve response — the
    // caller then stores 'paymongo' as a fallback bucket.
    val paymentMethodUsed: String?,
    // Name the buyer typed into the PayMongo billing form. The caller
    // uses this to overwrite the order's customer name when the cashier
    // didn't fill one in on the POS side.
    val billingName: String?
)

class PayMongoCheckoutRepository {

    suspend fun createCheckoutSession(
        orderNumber: String,
        customerName: String?,
        amount: Double,
        lines: List<PayMongoCheckoutLine>
    ): PayMongoCheckoutSession {
        val response = invokeFunction(
            body = buildJsonObject {
                put("action", "create")
                put("order_number", orderNumber)
                put("customer_name", customerName?.takeIf(String::isNotBlank))
                put("amount_centavos", amount.toCentavos())
                put(
                    "line_items",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("name", line.name)
                                    put("quantity", line.quantity.coerceAtLeast(1))
                                    put("line_total_centavos", line.lineTotal.toCentavos())
                                }
                            )
                        }
                    }
                )
            }
        )

        return PayMongoCheckoutSession(
            id = response.requiredString("id"),
            checkoutUrl = response.requiredString("checkout_url"),
            referenceNumber = response.optionalString("reference_number"),
            status = response.optionalString("status")
        )
    }

    suspend fun retrieveCheckoutSession(sessionId: String): PayMongoCheckoutStatus {
        val response = invokeFunction(
            body = buildJsonObject {
                put("action", "retrieve")
                put("checkout_session_id", sessionId)
            }
        )

        return PayMongoCheckoutStatus(
            id = response.requiredString("id"),
            status = response.optionalString("status"),
            isPaid = response["paid"]?.let { it is JsonPrimitive && it.content == "true" } ?: false,
            paymentReference = response.optionalString("payment_reference"),
            paymentMethodUsed = response.optionalString("payment_method_used"),
            billingName = response.optionalString("billing_name")
        )
    }

    suspend fun createQrPhPayment(
        orderNumber: String,
        customerName: String?,
        amount: Double,
        lines: List<PayMongoCheckoutLine>
    ): PayMongoQrPhPayment {
        val response = invokeFunction(
            body = buildJsonObject {
                put("action", "create_qrph")
                put("order_number", orderNumber)
                put("customer_name", customerName?.takeIf(String::isNotBlank))
                put("amount_centavos", amount.toCentavos())
                put(
                    "line_items",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("name", line.name)
                                    put("quantity", line.quantity.coerceAtLeast(1))
                                    put("line_total_centavos", line.lineTotal.toCentavos())
                                }
                            )
                        }
                    }
                )
            }
        )

        return PayMongoQrPhPayment(
            paymentIntentId = response.requiredString("payment_intent_id"),
            paymentMethodId = response.requiredString("payment_method_id"),
            qrImageUrl = response.requiredString("qr_image_url"),
            referenceNumber = response.optionalString("reference_number"),
            status = response.optionalString("status"),
            testUrl = response.optionalString("test_url")
        )
    }

    suspend fun retrievePaymentIntent(paymentIntentId: String): PayMongoCheckoutStatus {
        val response = invokeFunction(
            body = buildJsonObject {
                put("action", "retrieve_payment_intent")
                put("payment_intent_id", paymentIntentId)
            }
        )

        return PayMongoCheckoutStatus(
            id = response.requiredString("id"),
            status = response.optionalString("status"),
            isPaid = response["paid"]?.let { it is JsonPrimitive && it.content == "true" } ?: false,
            paymentReference = response.optionalString("payment_reference"),
            paymentMethodUsed = response.optionalString("payment_method_used"),
            billingName = response.optionalString("billing_name")
        )
    }

    private suspend fun invokeFunction(body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(supabaseUrl.isNotEmpty()) { "Missing SUPABASE_URL in local.properties" }
        require(anonKey.isNotEmpty()) { "Missing SUPABASE_ANON_KEY in local.properties" }

        val token = SupabaseSessionHelper.accessToken(SupabaseProvider.client)
        val connection = (URL("$supabaseUrl/functions/v1/paymongo-checkout").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "POST"
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Authorization", "Bearer $token")
            }

        try {
            connection.outputStream.use { stream ->
                stream.write(Json.encodeToString(JsonObject.serializer(), body).toByteArray(Charsets.UTF_8))
            }
            val responseText = connection.readResponseText()
            val responseJson = runCatching {
                Json.parseToJsonElement(responseText).jsonObject
            }.getOrElse { parseError ->
                val responseSummary = responseText.toSafeSummary()
                throw IllegalStateException(
                    if (responseSummary.isBlank()) {
                        "PayMongo function returned HTTP ${connection.responseCode}, but no readable response body."
                    } else {
                        "PayMongo function returned HTTP ${connection.responseCode}: $responseSummary"
                    },
                    parseError
                )
            }
            if (connection.responseCode !in 200..299) {
                val message = responseJson.optionalString("error")
                    ?: responseJson.optionalString("message")
                    ?: "PayMongo request failed with HTTP ${connection.responseCode}: ${responseText.toSafeSummary()}"
                throw IllegalStateException(message)
            }
            responseJson
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readResponseText(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use(BufferedReader::readText)
        }.orEmpty()
    }

    private fun Double.toCentavos(): Long {
        return (coerceAtLeast(0.0) * 100.0).roundToLong()
    }

    private fun String.toSafeSummary(): String {
        return replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_ERROR_SUMMARY_LENGTH)
    }

    private fun JsonObject.requiredString(key: String): String {
        return optionalString(key)?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("PayMongo response did not include $key.")
    }

    private fun JsonObject.optionalString(key: String): String? {
        return this[key]?.let { value ->
            when (value) {
                is JsonPrimitive -> value.contentOrNull
                else -> value.toString()
            }
        }?.trim()?.takeIf(String::isNotBlank)
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 30_000
        const val MAX_ERROR_SUMMARY_LENGTH = 240
    }
}
