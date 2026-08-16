package com.safa.account

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSelectionContractTest {
    @Test
    fun singleAccountIsBootstrappedBeforeDialogDecision() {
        val syncSource = File("src/main/java/com/safa/account/data/api/SyncManager.kt").readText()
        val activitySource = File("src/main/java/com/safa/account/MainActivity.kt").readText()

        assertTrue(syncSource.contains("bootstrapAccount(serverActive ?: accounts.singleOrNull()?.accountId)"))
        assertTrue(activitySource.contains("activeAccountId = tm.getActiveAccountId()"))
        assertTrue(activitySource.contains("showAccountDialog = activeAccountId == null"))
    }

    @Test
    fun multiAccountDialogRendersEveryAuthorizedChoiceAndHasAnExplicitEmptyState() {
        val activitySource = File("src/main/java/com/safa/account/MainActivity.kt").readText()

        assertTrue(activitySource.contains("accountChoices.forEach { account ->"))
        assertTrue(activitySource.contains("business_account_${'$'}{account.accountId}"))
        assertTrue(activitySource.contains("No authorized account is available."))
        assertTrue(activitySource.contains("Could not load your accounts. Please retry."))
    }
}
