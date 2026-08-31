package com.cadence.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cadence.app.ui.components.LibrarySectionRow
import com.cadence.app.ui.components.SavedHeartButton
import com.cadence.app.ui.components.TrackOverflowMenu
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-state matrix (Phase 5B). Robolectric-backed Compose render checks that pin
 * the honest-state semantics of the Library/Playlist UI without ExoPlayer:
 *   * heart reflects real saved-state and never claims "offline available";
 *   * overflow menu exposes the Add-to-playlist affordance;
 *   * section rows show real counts, and honest "Soon" where data does not exist
 *     (Albums/Artists) instead of fake numbers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LibraryUiMatrixTest {

    @get:Rule
    val compose: ComposeContentTestRule = createComposeRule()

    @Test
    fun `unsaved heart offers to save, never claims offline`() {
        var clicked = false
        compose.setContent {
            SavedHeartButton(saved = false, onToggle = { clicked = true })
        }
        compose.onNodeWithContentDescription("Save to library").assertIsDisplayed().performClick()
        assert(clicked)
        compose.onNodeWithText("Available offline").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test
    fun `saved heart reports remove from library`() {
        compose.setContent {
            SavedHeartButton(saved = true, onToggle = {})
        }
        compose.onNodeWithContentDescription("Remove from library").assertIsDisplayed()
    }

    @Test
    fun `overflow menu opens add to playlist`() {
        var triggered = false
        compose.setContent {
            TrackOverflowMenu(onAddToPlaylist = { triggered = true })
        }
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Add to playlist").assertIsDisplayed().performClick()
        assert(triggered)
    }

    @Test
    fun `section row shows real count`() {
        compose.setContent {
            LibrarySectionRow(label = "Songs", count = "3", onClick = {})
        }
        compose.onNodeWithContentDescription("Songs, 3").assertIsDisplayed()
    }

    @Test
    fun `section row shows honest Soon for albums and artists`() {
        compose.setContent {
            LibrarySectionRow(label = "Albums", onClick = {})
            LibrarySectionRow(label = "Artists", onClick = {})
        }
        compose.onNodeWithContentDescription("Albums").assertIsDisplayed()
        compose.onNodeWithContentDescription("Artists").assertIsDisplayed()
        compose.onAllNodesWithText("Soon").assertCountEquals(2)
    }
}
