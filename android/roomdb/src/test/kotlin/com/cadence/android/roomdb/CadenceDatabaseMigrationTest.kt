package com.cadence.android.roomdb

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cadence.android.roomdb.data.CadenceDatabase
import com.cadence.android.roomdb.data.PlaylistEntity
import com.cadence.core.database.LibraryRepository
import com.cadence.core.database.LibraryTrackMeta
import com.cadence.core.database.QueueItem
import com.cadence.core.database.QueueItemMeta
import com.cadence.core.database.QueueKind
import com.cadence.core.database.QueueSession
import com.cadence.core.model.ProviderId
import com.cadence.core.model.TrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the explicit v1 -> v2 migration: existing playlist data is preserved
 * (ADR-0003 — the local DB is the product, never destructively rebuilt) and the
 * new saved-tracks / play-history tables are created and usable.
 */
@RunWith(RobolectricTestRunner::class)
class CadenceDatabaseMigrationTest {

    @Test
    fun `v1 to v2 preserves playlists and adds library tables`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Build a v1 database with a playlist already present.
        val dbName = "migration-test.db"
        context.deleteDatabase(dbName)
        val v1 = Room.databaseBuilder(context, CadenceDatabase::class.java, dbName)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
        v1.playlistDao().insertPlaylist(
            PlaylistEntity(id = "p1", title = "Saved Playlist", description = "d", createdAt = 1, updatedAt = 2),
        )
        v1.playlistDao().insertTrack(
            com.cadence.android.roomdb.data.PlaylistTrackEntity(
                playlistId = "p1",
                position = 0,
                providerId = "demo",
                nativeId = "old-ref",
                addedReason = "USER",
            ),
        )
        v1.close()

        // Reopen at v2 via the explicit migration.
        val v2 = Room.databaseBuilder(context, CadenceDatabase::class.java, dbName)
            .addMigrations(CadenceDatabase.MIGRATION_1_2)
            .build()

        // Playlist data preserved.
        val playlist = v2.playlistDao().getPlaylist("p1")
        assertEquals("Saved Playlist", playlist!!.title)
        // Pre-existing playlist track survives migration with nullable cache columns.
        assertEquals(listOf("old-ref"), v2.playlistDao().listTracks("p1").map { it.nativeId })

        // New library tables work.
        val repo: LibraryRepository = RoomLibraryRepository.forTesting(
            database = v2,
            dispatcher = Dispatchers.Default,
        )
        repo.save(TrackRef(ProviderId("demo"), "t1"), LibraryTrackMeta(title = "Track 1"))
        repo.recordPlay(TrackRef(ProviderId("demo"), "t2"), LibraryTrackMeta(title = "Track 2"))
        assertEquals(1, repo.savedCount())
        assertEquals(listOf("t2"), repo.observeHistory().first().map { it.ref.nativeId })

        v2.close()
        context.deleteDatabase(dbName)
    }

    /**
     * Verifies the v3 -> v4 migration (Phase 5D): a genuine v3 database (built by
     * hand at user_version=3, containing a download row) is migrated forward by
     * [CadenceDatabase.MIGRATION_3_4] — the download survives and the queue
     * tables are created and usable through [QueueDao].
     */
    @Test
    fun `v3 to v4 preserves downloads and adds queue tables`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbName = "migration-v3-4.db"
        context.deleteDatabase(dbName)

        // Build the v3 schema by hand (all tables that exist at version 3).
        val raw = context.openOrCreateDatabase(dbName, android.content.Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE `playlists` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        raw.execSQL("CREATE INDEX `index_playlists_updated_at` ON `playlists` (`updated_at`)")
        raw.execSQL(
            "CREATE TABLE `playlist_tracks` (`playlist_id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`provider_id` TEXT NOT NULL, `native_id` TEXT NOT NULL, `added_reason` TEXT NOT NULL, " +
                "`title` TEXT, `artist` TEXT, `duration_ms` INTEGER, `artwork_url` TEXT, " +
                "PRIMARY KEY(`playlist_id`, `position`), FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        raw.execSQL(
            "CREATE INDEX `index_playlist_tracks_provider_id_native_id` " +
                "ON `playlist_tracks` (`provider_id`, `native_id`)",
        )
        raw.execSQL(
            "CREATE TABLE `saved_tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`provider_id` TEXT NOT NULL, `native_id` TEXT NOT NULL, `saved_at` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `artist` TEXT, `duration_ms` INTEGER, `artwork_url` TEXT)",
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX `index_saved_tracks_provider_id_native_id` " +
                "ON `saved_tracks` (`provider_id`, `native_id`)",
        )
        raw.execSQL("CREATE INDEX `index_saved_tracks_saved_at` ON `saved_tracks` (`saved_at`)")
        raw.execSQL(
            "CREATE TABLE `play_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`provider_id` TEXT NOT NULL, `native_id` TEXT NOT NULL, `played_at` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `artist` TEXT, `duration_ms` INTEGER, `artwork_url` TEXT)",
        )
        raw.execSQL("CREATE INDEX `index_play_history_played_at` ON `play_history` (`played_at`)")
        raw.execSQL(
            "CREATE TABLE `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`provider_id` TEXT NOT NULL, `native_id` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                "`progress_bytes` INTEGER NOT NULL DEFAULT 0, `total_bytes` INTEGER, " +
                "`file_size_bytes` INTEGER, `local_uri` TEXT, `error_reason` TEXT, `error_message` TEXT, " +
                "`title` TEXT NOT NULL, `artist` TEXT, `duration_ms` INTEGER, `artwork_url` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        )
        raw.execSQL(
            "CREATE UNIQUE INDEX `index_downloads_provider_id_native_id` " +
                "ON `downloads` (`provider_id`, `native_id`)",
        )
        raw.execSQL("CREATE INDEX `index_downloads_state` ON `downloads` (`state`)")
        raw.execSQL("CREATE INDEX `index_downloads_created_at` ON `downloads` (`created_at`)")
        raw.execSQL(
            "INSERT INTO `downloads` (`provider_id`, `native_id`, `state`, `title`, `created_at`, `updated_at`) " +
                "VALUES ('demo', 'dl-1', 'COMPLETED', 'Kept Song', 1, 2)",
        )
        raw.execSQL("PRAGMA user_version = 3")
        raw.close()

        // Reopen at v4 via the explicit migration.
        val v4 = Room.databaseBuilder(context, CadenceDatabase::class.java, dbName)
            .addMigrations(CadenceDatabase.MIGRATION_3_4)
            .build()

        // Download preserved across the migration.
        assertEquals("Kept Song", v4.downloadDao().getByRef("demo", "dl-1")!!.title)

        // Queue tables are usable.
        val store = RoomQueueStore.forTesting(v4)
        store.save(
            QueueSession(
                kind = QueueKind.PLAYLIST,
                continuationSerialized = "My Mix",
                currentIndex = 0,
                tracks = listOf(
                    QueueItem(
                        position = 0,
                        ref = TrackRef(ProviderId("demo"), "a"),
                        meta = QueueItemMeta(title = "A"),
                    ),
                ),
            ),
        )
        assertEquals(QueueKind.PLAYLIST, store.get()!!.kind)

        v4.close()
        context.deleteDatabase(dbName)
    }
}
