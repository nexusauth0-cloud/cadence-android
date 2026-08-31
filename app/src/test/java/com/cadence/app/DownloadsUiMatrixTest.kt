package com.cadence.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.cadence.app.ui.components.DownloadToggle
import com.cadence.core.database.DownloadFailureReason
import com.cadence.core.database.OfflineAvailability
import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-state matrix (Phase 5C). Pins the honest offline-truth semantics of the
 * download toggle: "available offline" is ONLY rendered for a verified
 * COMPLETED download; downloading/queued/failed/cancelled never claim it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DownloadsUiMatrixTest {

    @get:Rule
    val compose: ComposeContentTestRule = createComposeRule()

    private val ref = TrackRef(ProviderId("demo"), "t1")
    private val track = Track(
        ref = ref,
        title = "Alpha",
        artists = listOf(),
        durationMs = 120_000,
    )

    private fun toggle(
        availability: OfflineAvailability,
        onDownload: Boolean = false,
        onCancel: Boolean = false,
        onRemove: Boolean = false,
        onRetry: Boolean = false,
    ) = compose.setContent {
        val flow = MutableStateFlow(availability)
        DownloadToggle(
            track = track,
            availability = flow,
            onDownload = { onDownload },
            onCancel = { onCancel },
            onRemove = { onRemove },
            onRetry = { onRetry },
        )
    }

    @Test
    fun `not downloaded shows download action, never offline claim`() {
        toggle(OfflineAvailability.NotDownloaded)
        compose.onNodeWithContentDescription("Download Alpha").assertIsDisplayed()
        compose.onNodeWithText("Available offline").assertDoesNotExist()
    }

    @Test
    fun `downloading shows progress, never offline claim`() {
        toggle(OfflineAvailability.Downloading(progressBytes = 512, totalBytes = 1024))
        compose.onNodeWithContentDescription("Download Alpha").assertDoesNotExist()
        compose.onNodeWithText("Available offline").assertDoesNotExist()
    }

    @Test
    fun `failed download offers retry, never offline claim`() {
        toggle(
            OfflineAvailability.Failed(DownloadFailureReason.NETWORK),
            onRetry = true,
        )
        compose.onNodeWithContentDescription("Retry download for Alpha").assertIsDisplayed()
        compose.onNodeWithText("Available offline").assertDoesNotExist()
    }

    @Test
    fun `verified complete download offers remove`() {
        toggle(
            OfflineAvailability.Available(localUri = "file:///x.mp3", fileSizeBytes = 2048),
            onRemove = true,
        )
        compose.onNodeWithContentDescription("Alpha is downloaded. Remove?").assertIsDisplayed()
    }
}
