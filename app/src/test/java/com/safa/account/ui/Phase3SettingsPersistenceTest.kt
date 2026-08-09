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
class Phase3SettingsPersistenceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `verify dark mode persistence across ViewModel instances`() {
        val tm1 = TokenManager(context)
        val repo = mock(AppRepository::class.java)
        val vm1 = HundiViewModel(repo, tm1)

        // Enable dark mode
        vm1.setDarkMode(true)
        assertTrue("vm1 isDarkMode should be true", vm1.isDarkMode.value)

        // Simulate app restart by instantiating new TokenManager and ViewModel
        val tm2 = TokenManager(context)
        val vm2 = HundiViewModel(repo, tm2)

        assertTrue("vm2 isDarkMode should persist as true after restart", vm2.isDarkMode.value)
    }

    @Test
    fun `verify language persistence across ViewModel instances`() {
        val tm1 = TokenManager(context)
        val repo = mock(AppRepository::class.java)
        val vm1 = HundiViewModel(repo, tm1)

        vm1.setLanguage("EN")
        assertEquals("EN", vm1.currentLanguage.value)

        val tm2 = TokenManager(context)
        val vm2 = HundiViewModel(repo, tm2)

        assertEquals("EN", vm2.currentLanguage.value)
    }
}
