package com.safa.account.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.safa.account.ui.components.AppOutlinedButton
import com.safa.account.ui.components.AppPrimaryButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilitySemanticsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `primary critical action exposes enabled click semantics`() {
        composeTestRule.setContent {
            AppPrimaryButton(text = "Save", onClick = {})
        }

        composeTestRule.onNodeWithText("Save")
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    fun `secondary critical action exposes enabled click semantics`() {
        composeTestRule.setContent {
            AppOutlinedButton(text = "Cancel", onClick = {})
        }

        composeTestRule.onNodeWithText("Cancel")
            .assertIsEnabled()
            .assertHasClickAction()
    }
}
