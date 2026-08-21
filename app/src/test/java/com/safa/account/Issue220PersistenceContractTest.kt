package com.safa.account

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue220PersistenceContractTest {
    @Test
    fun businessApiAlwaysGetsPublicAndroidClientIdentifier() {
        val source = File("src/main/java/com/safa/account/data/api/RetrofitClient.kt").readText()
        assertTrue(source.contains("apiKey.trim().ifBlank { BuildConfig.SAFA_API_KEY.trim() }"))
        assertTrue(source.contains("ApiSecurityInterceptor(clientApiKey, apiSecret, tokenManager)"))
        assertTrue(source.contains("require(clientApiKey.isNotBlank())"))
    }

    @Test
    fun previouslyAuthBlockedOutboxIsReplayedWithoutBypassingValidationFailures() {
        val recovery = File("src/main/java/com/safa/account/data/sync/SyncFailureRecovery.kt").readText()
        val worker = File("src/main/java/com/safa/account/data/sync/SafaSyncWorker.kt").readText()

        assertTrue(recovery.contains("it.syncStatus == LocalFirstStore.FAILED"))
        assertTrue(recovery.contains("equals(\"HTTP 401\", ignoreCase = true)"))
        assertTrue(recovery.contains("store.retry(entity, record.localId)"))
        assertFalse(recovery.contains("HTTP 403"))
        assertTrue(worker.contains("SyncFailureRecovery.recoverUnauthorized(applicationContext, tokenManager)"))
    }
}
