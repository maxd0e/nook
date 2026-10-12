package io.maxdoe.nook

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStarts_showsHomeRoot() {
        composeRule.onNodeWithTag(HOME_ROOT_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun welcome_themeToggleIsIconOnly_andRemovesEncryptionFooter() {
        composeRule.onNodeWithContentDescription("Nook logo").assertIsDisplayed()
        val darkThemeAction = composeRule.onAllNodesWithContentDescription("Switch to dark theme")
        val lightThemeAction = composeRule.onAllNodesWithContentDescription("Switch to light theme")
        assertEquals(1, darkThemeAction.fetchSemanticsNodes().size + lightThemeAction.fetchSemanticsNodes().size)
        if (darkThemeAction.fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("Switch to dark theme").performClick()
            composeRule.onNodeWithContentDescription("Switch to light theme").assertIsDisplayed()
        } else {
            composeRule.onNodeWithContentDescription("Switch to light theme").performClick()
            composeRule.onNodeWithContentDescription("Switch to dark theme").assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("Notes are encrypted on your device before they leave it.").assertCountEquals(0)
    }

    @Test
    fun themeToggle_persistsAfterActivityRecreation() {
        val darkThemeAction = composeRule.onAllNodesWithContentDescription("Switch to dark theme")
        val wasDark = darkThemeAction.fetchSemanticsNodes().isEmpty()

        if (wasDark) {
            composeRule.onNodeWithContentDescription("Switch to light theme").performClick()
        } else {
            composeRule.onNodeWithContentDescription("Switch to dark theme").performClick()
        }
        composeRule.activity.recreate()

        if (wasDark) {
            composeRule.onNodeWithContentDescription("Switch to dark theme").assertIsDisplayed()
        } else {
            composeRule.onNodeWithContentDescription("Switch to light theme").assertIsDisplayed()
        }
    }
}
