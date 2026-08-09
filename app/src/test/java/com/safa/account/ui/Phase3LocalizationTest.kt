package com.safa.account.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.safa.account.data.api.TokenManager
import com.safa.account.data.repository.AppRepository
import com.safa.account.ui.viewmodel.HundiViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3LocalizationTest {

    private lateinit var viewModel: HundiViewModel
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        tokenManager = TokenManager(context)
        val repository = mock(AppRepository::class.java)
        viewModel = HundiViewModel(repository, tokenManager)
    }

    @Test
    fun `verify language toggle changes between BN and EN cleanly`() {
        viewModel.setLanguage("BN")
        assertEquals("BN", viewModel.currentLanguage.value)
        assertEquals("ড্যাশবোর্ড", viewModel.t("dashboard"))

        viewModel.setLanguage("EN")
        assertEquals("EN", viewModel.currentLanguage.value)
        assertEquals("Dashboard", viewModel.t("dashboard"))
    }

    @Test
    fun `verify no duplicated bilingual labels in translation table`() {
        // Ensure translation values don't contain ugly duplicated "বাংলা (English)" patterns
        val bnValue = viewModel.t("dashboard", "BN")
        assertFalse("BN string should not contain English in parentheses", bnValue.contains("(Dashboard)"))

        val enValue = viewModel.t("dashboard", "EN")
        assertFalse("EN string should not contain Bengali in parentheses", enValue.contains("(ড্যাশবোর্ড)"))
    }
}
