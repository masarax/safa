package com.safa.account.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.safa.account.ui.components.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase3DesignSystemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `AppPrimaryButton renders text parameter properly`() {
        var clicked = false
        composeTestRule.setContent {
            AppPrimaryButton(text = "Save Record", onClick = { clicked = true })
        }

        composeTestRule.onNodeWithText("Save Record").assertExists()
        composeTestRule.onNodeWithText("Save Record").performClick()
        assertTrue("Click callback should trigger", clicked)
    }

    @Test
    fun `AppOutlinedButton renders text parameter properly`() {
        var clicked = false
        composeTestRule.setContent {
            AppOutlinedButton(text = "Cancel Action", onClick = { clicked = true })
        }

        composeTestRule.onNodeWithText("Cancel Action").assertExists()
        composeTestRule.onNodeWithText("Cancel Action").performClick()
        assertTrue("Click callback should trigger", clicked)
    }

    @Test
    fun `AppStatusChip renders status text`() {
        composeTestRule.setContent {
            AppStatusChip(text = "Completed", statusType = "SUCCESS")
        }

        composeTestRule.onNodeWithText("Completed").assertExists()
    }
}
