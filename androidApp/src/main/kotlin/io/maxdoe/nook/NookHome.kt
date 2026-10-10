package io.maxdoe.nook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val HOME_ROOT_TEST_TAG = "nook_home"

@Composable
fun NookHome() {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(HOME_ROOT_TEST_TAG),
    ) {}
}
