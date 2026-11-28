package dev.nuvio.presence

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenesisQualityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsIsReachableAndNativeBridgeLoads() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Agora não").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Agora não").performClick()
        }
        compose.onNodeWithText("Ajustes").assertIsDisplayed().performClick()
        compose.onNodeWithText("Rich Presence").assertIsDisplayed()
        compose.activityRule.scenario.onActivity {
            assertTrue("JNI or SDK initialization failed: ${NativeRpc.error}", NativeRpc.ready)
            NativeRpc.nativeTick()
        }
    }
}
