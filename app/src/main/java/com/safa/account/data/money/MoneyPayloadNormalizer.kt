package com.safa.account.data.money

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonicalizes financial JSON fields immediately before network transmission.
 * This covers both direct online Retrofit calls and sync/outbox replay while
 * leaving multipart, auth and non-JSON requests untouched.
 */
class MoneyPayloadNormalizer : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body ?: return chain.proceed(request)
        val mediaType = body.contentType()
        if (mediaType?.subtype?.contains("json", ignoreCase = true) != true) return chain.proceed(request)

        val buffer = Buffer()
        body.writeTo(buffer)
        val original = buffer.readUtf8()
        if (original.isBlank()) return chain.proceed(request)

        val normalized = try {
            normalizeJson(original)
        } catch (failure: RuntimeException) {
            throw java.io.IOException("Invalid financial payload.", failure)
        } ?: return chain.proceed(request)
        val replacement = normalized.toRequestBody(mediaType.toString().toMediaTypeOrNull())
        return chain.proceed(request.newBuilder().method(request.method, replacement).build())
    }

    companion object {
        private val unsignedAmountFields = setOf(
            "amount", "amount_sar", "amount_bdt", "bdt_disbursed",
            "paid_bdt", "initial_bdt", "remaining_bdt", "balance"
        )
        private val signedAmountFields = setOf("sar_collected")
        private val rateFields = setOf("rate", "customer_rate", "supplier_rate")

        fun normalizeJson(raw: String): String? {
            val trimmed = raw.trim()
            return when {
                trimmed.startsWith("{") -> normalizeObject(JSONObject(trimmed)).toString()
                trimmed.startsWith("[") -> normalizeArray(JSONArray(trimmed)).toString()
                else -> null
            }
        }

        private fun normalizeObject(obj: JSONObject): JSONObject {
            val keys = obj.keys().asSequence().toList()
            for (key in keys) {
                val value = obj.opt(key)
                when {
                    value is JSONObject -> obj.put(key, normalizeObject(value))
                    value is JSONArray -> obj.put(key, normalizeArray(value))
                    value == null || value == JSONObject.NULL -> Unit
                    key in unsignedAmountFields -> obj.put(key, MoneyMath.nonNegativeAmount(value).toPlainString())
                    key in signedAmountFields -> obj.put(key, MoneyMath.amountString(value))
                    key in rateFields -> obj.put(key, MoneyMath.nonNegativeRate(value).toPlainString())
                }
            }
            return obj
        }

        private fun normalizeArray(array: JSONArray): JSONArray {
            for (index in 0 until array.length()) {
                when (val value = array.opt(index)) {
                    is JSONObject -> array.put(index, normalizeObject(value))
                    is JSONArray -> array.put(index, normalizeArray(value))
                }
            }
            return array
        }
    }
}
