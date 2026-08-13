package com.safa.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionContractTest {
    @Test
    fun buildConfigMirrorsCanonicalReleaseIdentity() {
        assertTrue("Production versionCode must advance beyond the legacy release", BuildConfig.VERSION_CODE > 1)
        assertEquals(BuildConfig.VERSION_CODE, BuildConfig.SAFA_RELEASE_VERSION_CODE)
        assertEquals(BuildConfig.VERSION_NAME, BuildConfig.SAFA_RELEASE_VERSION_NAME)
    }
}
