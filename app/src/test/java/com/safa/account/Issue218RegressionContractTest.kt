package com.safa.account

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue218RegressionContractTest {
    @Test
    fun transactionEditReusesCreateTwoStepFlowAndPrefillsExistingValues() {
        val source = File("src/main/java/com/safa/account/ui/screens/CustomerScreen.kt").readText()

        assertFalse(source.contains("} else if (txToEdit != null) {"))
        assertTrue(source.contains("inputAmountSar = MoneyMath.amountString(tx.amountSar)"))
        assertTrue(source.contains("inputCustomerRate = MoneyMath.rateDisplayString(tx.customerRate)"))
        assertTrue(source.contains("inputSupplierRate = MoneyMath.rateDisplayString(tx.supplierRate)"))
        assertTrue(source.contains("inputReceiverAccountType = tx.receiverAccountType"))
        assertTrue(source.contains("inputReceiverAccountNo = tx.receiverAccountNo"))
        assertTrue(source.contains("inputNotes = tx.notes"))
        assertTrue(source.contains("inputTimestamp = tx.timestamp"))
        assertTrue(source.contains("isAddingTransaction = true"))
        assertTrue(source.contains("viewModel.updateRemittance(updated)"))
    }

    @Test
    fun newTransactionsDefaultToCanonicalPaidState() {
        val viewModelSource = File("src/main/java/com/safa/account/ui/viewmodel/SafaViewModel.kt").readText()
        val modelSource = File("src/main/java/com/safa/account/data/model/Models.kt").readText()

        assertTrue(viewModelSource.contains("status: String = \"Delivered\""))
        assertTrue(modelSource.contains("val status: String = \"Delivered\""))
    }

    @Test
    fun brandingUploadIsSingleShotAndWaitsForServerResult() {
        val settingsSource = File("src/main/java/com/safa/account/ui/screens/SettingsScreen.kt").readText()
        val viewModelSource = File("src/main/java/com/safa/account/ui/viewmodel/SafaViewModel.kt").readText()

        val pickerStart = settingsSource.indexOf("val launcher = rememberLauncherForActivityResult")
        val pickerEnd = settingsSource.indexOf("Column(modifier", pickerStart)
        val pickerBlock = settingsSource.substring(pickerStart, pickerEnd)
        assertFalse(pickerBlock.contains("uploadAppLogoToServer"))
        assertTrue(settingsSource.contains("isSavingBranding"))
        assertTrue(settingsSource.contains("logoSaveError"))
        assertTrue(settingsSource.contains("viewModel.uploadAppLogoToServer(context, Uri.parse(tempAppLogo)) { success, message ->"))
        assertTrue(viewModelSource.contains("LogoUploadPreparer.prepare"))
        assertTrue(viewModelSource.contains("onResult(false, safeServerFailure(\"Upload logo\", response.code()))"))
    }

    @Test
    fun logoutAndBiometricUseExplicitSessionLifecycleBarrier() {
        val tokenSource = File("src/main/java/com/safa/account/data/api/TokenManager.kt").readText()
        val lifecycleSource = File("src/main/java/com/safa/account/data/api/AuthLifecycleCoordinator.kt").readText()
        val loginSource = File("src/main/java/com/safa/account/ui/screens/LoginScreen.kt").readText()
        val viewModelSource = File("src/main/java/com/safa/account/ui/viewmodel/SafaViewModel.kt").readText()

        assertTrue(tokenSource.contains("fun beginLogout()"))
        assertTrue(tokenSource.contains("fun finishLogout()"))
        assertTrue(tokenSource.contains("fun isLogoutInProgress(): Boolean"))
        assertTrue(lifecycleSource.contains("tokenManager.finishLogout()"))
        assertTrue(loginSource.contains("!it.isLogoutInProgress()"))
        assertTrue(loginSource.contains("tokenManager?.isLogoutInProgress() == true"))
        assertTrue(viewModelSource.contains("tokenManager?.enableBiometricQuickUnlock(operator.id, operator.mobile)"))
        assertTrue(viewModelSource.contains("tokenManager?.disableBiometricQuickUnlock()"))
    }

    @Test
    fun loginNetworkEnvelopeIsNotCollapsedIntoGenericConnectivityFailure() {
        val source = File("src/main/java/com/safa/account/data/network/ApiLoginError.kt").readText()
        assertTrue(source.contains("is LoginNetworkException -> t.error"))
    }
}
