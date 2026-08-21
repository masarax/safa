package com.safa.account

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSelectionContractTest {
    @Test
    fun authorizedAccountIsResolvedAutomaticallyWithoutChooser() {
        val syncSource = File("src/main/java/com/safa/account/data/api/SyncManager.kt").readText()
        val tokenSource = File("src/main/java/com/safa/account/data/api/TokenManager.kt").readText()

        assertTrue(syncSource.contains("accounts.firstOrNull { it.isOwner }?.accountId"))
        assertTrue(syncSource.contains("?: accounts.firstOrNull()?.accountId"))
        assertFalse(syncSource.contains("Select an account before synchronization"))
        assertTrue(tokenSource.contains("accountIdFromAccessToken"))
        assertTrue(tokenSource.contains("putInt(KEY_ACTIVE_ACCOUNT_ID, automaticAccountId)"))
    }

    @Test
    fun authenticatedUiDoesNotExposeBusinessAccountSelection() {
        val activitySource = File("src/main/java/com/safa/account/MainActivity.kt").readText()

        assertFalse(activitySource.contains("showAccountDialog"))
        assertFalse(activitySource.contains("Select business account"))
        assertFalse(activitySource.contains("ব্যবসার অ্যাকাউন্ট নির্বাচন"))
        assertFalse(activitySource.contains("appbar_account_switch"))
        assertFalse(activitySource.contains("business_account_${'$'}{account.accountId}"))
        assertTrue(activitySource.contains("Automatic account bootstrap failed"))
    }

    @Test
    fun routineSetupProbeDoesNotReplaceLoginWithLoadingCard() {
        val loginSource = File("src/main/java/com/safa/account/ui/screens/LoginScreen.kt").readText()

        assertFalse(loginSource.contains("first_run_setup_checking"))
        assertFalse(loginSource.contains("Checking server setup status"))
        assertFalse(loginSource.contains("সার্ভার সেটআপ অবস্থা যাচাই হচ্ছে"))
        assertTrue(loginSource.contains("if (setupPhase != null)"))
        assertTrue(loginSource.contains("first_run_setup_card"))
    }
}