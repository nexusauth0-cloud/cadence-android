package com.cadence.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.cadence.app.ui.components.MiniPlayer
import com.cadence.app.ui.components.QueueSheet
import com.cadence.core.database.QueueAddedReason
import com.cadence.core.database.QueueItem
import com.cadence.core.database.QueueItemMeta
import com.cadence.core.database.QueueKind
import com.cadence.core.database.QueueSession
import com.cadence.core.model.ProviderId
import com.cadence.core.model.Track
import com.cadence.core.model.TrackRef
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 5D UI-state matrix over the player/mini-player/queue surface. Pins the
 * honest-to-user behaviors:
 *  - the offline badge only appears for a verified local download;
 *  - the queue sheet announces playlist context, the now-playing row, and
 *    gives screen readers spoken positions for reordering.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayerUiMatrixTest {

    @get:Rule
    val compose: ComposeContentTestRule = createComposeRule()

    private val provider = ProviderId("demo")
    private val track = Track(ref = TrackRef(provider, "a"), title = "Alpha", artists = emptyList())

    private fun session(): QueueSession =
        QueueSession(
            kind = QueueKind.PLAYLIST,
            continuationSerialized = "My Mix",
            currentIndex = 1,
            tracks = listOf(
                QueueItem(0, TrackRef(provider, "a"), QueueItemMeta("Alpha", "Artist A"), QueueAddedReason.SEED),
                QueueItem(1, TrackRef(provider, "b"), QueueItemMeta("Beta", "Artist B"), QueueAddedReason.SEED),
                QueueItem(2, TrackRef(provider, "c"), QueueItemMeta("Gamma", "Artist C"), QueueAddedReason.SEED),
            ),
        )

    @Test
    fun `mini player hidden when not visible`() {
        compose.setContent {
            MiniPlayer(
                track = track,
                visible = false,
                playing = true,
                offlineAvailable = false,
                onTogglePlayPause = {},
                onOpen = {},
            )
        }
        compose.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test
    fun `mini player lists title and offline badge only when downloaded`() {
        compose.setContent {
            MiniPlayer(
                track = track,
                visible = true,
                playing = true,
                offlineAvailable = true,
                onTogglePlayPause = {},
                onOpen = {},
            )
        }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithText("Available offline").assertIsDisplayed()
    }

    @Test
    fun `mini player never claims offline when not a download`() {
        compose.setContent {
            MiniPlayer(
                track = track,
                visible = true,
                playing = false,
                offlineAvailable = false,
                onTogglePlayPause = {},
                onOpen = {},
            )
        }
        compose.onNodeWithText("Available offline").assertDoesNotExist()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun `queue sheet announces playlist context and now playing`() {
        compose.setContent {
            QueueSheet(
                session = session(),
                nowRef = TrackRef(provider, "b"),
                onPlay = {},
                onRemove = {},
                onMoveDown = {},
                onMoveUp = {},
                onClear = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithText("Playing from My Mix").assertIsDisplayed()
        compose.onNodeWithText("Now playing").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move Beta down").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Beta from queue").assertIsDisplayed()
    }
}
