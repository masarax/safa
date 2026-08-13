package com.safa.account.data.api

import com.safa.account.data.api.dto.MobilePinLoginRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.POST

class Phase32LoginApiContractTest {
    @Test
    fun `login uses the canonical production auth endpoint`() {
        val method = ApiService::class.java.declaredMethods.first { it.name == "login" }
        val post = method.getAnnotation(POST::class.java)

        assertTrue("ApiService.login must use Retrofit POST", post != null)
        assertEquals("auth/login", post.value)
        assertTrue(method.parameterTypes.any { it == MobilePinLoginRequest::class.java })
    }

    @Test
    fun `login request contains only mobile and pin credentials`() {
        val fields = MobilePinLoginRequest::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

        assertEquals(setOf("mobile", "pin"), fields)
    }
}
