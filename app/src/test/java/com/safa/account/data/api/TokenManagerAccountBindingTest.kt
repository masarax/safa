package com.safa.account.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenManagerAccountBindingTest {
    private lateinit var context: Context
    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tokenManager = TokenManager(context)
        tokenManager.clearAllTokens()
    }

    @Test
    fun `login access token account claim becomes active account`() {
        val accessToken = jwtWithPayload("""{"account_id":42}""")

        tokenManager.saveAllTokens(
            accessToken = accessToken,
            refreshToken = "refresh",
            deviceToken = "device",
            sessionToken = "session",
            fingerprintToken = "fingerprint"
        )

        assertEquals(42, tokenManager.getActiveAccountId())
    }

    @Test
    fun `token without account claim does not discard existing account context`() {
        tokenManager.saveActiveAccountId(42)

        tokenManager.saveAllTokens(
            accessToken = jwtWithPayload("""{"sub":7}"""),
            refreshToken = "refresh-2",
            deviceToken = "device",
            sessionToken = "session",
            fingerprintToken = "fingerprint"
        )

        assertEquals(42, tokenManager.getActiveAccountId())
    }

    private fun jwtWithPayload(payload: String): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{}".toByteArray(Charsets.UTF_8))
        val body = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "$header.$body.signature"
    }
}