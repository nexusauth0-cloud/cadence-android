package com.cadence.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cadence.app.ui.components.UpdateDialog
import com.cadence.core.domain.update.UpdateChannel
import com.cadence.core.domain.update.UpdateInfo
import com.cadence.core.domain.update.UpdateState
import com.cadence.core.domain.update.SemanticVersion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 5D update-modal matrix. Pins:
 *  - an Available update shows the store CTA plus version/notes;
 *  - a critical update hides the "Later" dismiss affordance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UpdateDialogUiTest {

    @get:Rule
    val compose: ComposeContentTestRule = createComposeRule()

    private fun info(critical: Boolean) = UpdateInfo(
        version = SemanticVersion(2, 0, 0),
        channel = UpdateChannel.STABLE,
        releaseNotes = "Bug fixes and polish.",
        minSupported = SemanticVersion(1, 0, 0),
        critical = critical,
        storeUrl = "https://play.google.com/store/apps/details?id=com.cadence.app",
    )

    @Test
    fun `non-critical update shows store CTA and later`() {
        compose.setContent {
            UpdateDialog(
                state = UpdateState.Available(info(critical = false)),
                onCheck = {},
                onDismiss = {},
                onOpenUpdate = {},
            )
        }

        compose.onNodeWithText("Version 2.0.0").assertIsDisplayed()
        compose.onNodeWithText("Bug fixes and polish.").assertIsDisplayed()
        compose.onNodeWithText("Get the update").assertIsDisplayed()
        compose.onNodeWithText("Later").assertIsDisplayed()
    }

    @Test
    fun `critical update hides later affordance`() {
        compose.setContent {
            UpdateDialog(
                state = UpdateState.Available(info(critical = true)),
                onCheck = {},
                onDismiss = {},
                onOpenUpdate = {},
            )
        }

        compose.onNodeWithText("Get the update").assertIsDisplayed()
        compose.onNodeWithText("Later").assertDoesNotExist()
        compose.onNodeWithText("This update is required to keep using Cadence.").assertIsDisplayed()
    }
}
