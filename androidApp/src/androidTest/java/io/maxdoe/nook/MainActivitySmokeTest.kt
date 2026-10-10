package io.maxdoe.nook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStarts_showsHomeRoot() {
        composeRule.onNodeWithTag(HOME_ROOT_TEST_TAG).assertIsDisplayed()
    }
}
