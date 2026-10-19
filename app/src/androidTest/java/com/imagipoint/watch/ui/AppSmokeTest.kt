package com.imagipoint.watch.ui

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.imagipoint.watch.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke coverage for startup, round-screen Compose navigation, and persisted
 * preferences. Bluetooth HID pairing and actual session startup require real hardware.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndNavigatesCoreScreens() {
        compose.onNodeWithText("IMAGIPOINT").assertIsDisplayed()
        compose.onNodeWithText("No computer selected").assertIsDisplayed()

        compose.onNodeWithText("Set up").performClick()
        compose.onNodeWithText("Connection").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Sensitivity").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithContentDescription("Calibration").performClick()
        compose.onNodeWithText("Calibration").assertIsDisplayed()
        compose.onNodeWithText("Neutral posture").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Choose a computer"))
        compose.onNodeWithText("Choose a computer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithText("IMAGIPOINT").assertIsDisplayed()
    }

    @Test
    fun diagnosticsTogglePersistsToSharedPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("imagipoint_settings", Context.MODE_PRIVATE)
        val initiallyEnabled = prefs.getString("show_diagnostics", null) == "true"

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Diagnostics strip"))
        compose.onNodeWithContentDescription("Diagnostics strip").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            prefs.getString("show_diagnostics", null) == (!initiallyEnabled).toString()
        }

        assertTrue(
            "Diagnostics toggle was not persisted",
            prefs.getString("show_diagnostics", null) == (!initiallyEnabled).toString(),
        )

        // Restore the original value so later smoke screenshots start from a known state.
        compose.onNodeWithContentDescription("Diagnostics strip").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            prefs.getString("show_diagnostics", null) == initiallyEnabled.toString()
        }
        assertFalse(
            "Diagnostics toggle was not restored",
            prefs.getString("show_diagnostics", null) == (!initiallyEnabled).toString(),
        )
    }
}
