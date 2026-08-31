package com.cadence.android.roomdb.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SavedTrackEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        QueueSessionEntity::class,
        QueueItemEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class CadenceDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun libraryDao(): LibraryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun queueDao(): QueueDao

    companion object {
        const val NAME = "cadence.db"

        /**
         * v1 -> v2: add the saved-tracks and play-history tables (Phase 5B).
         * Explicit migrations preserve existing data (ADR-0003: local database is
         * the product — never destructively rebuild it).
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1 -> v2: carry cached display metadata on playlist_tracks
                // (ADR-0003 CACHE COLUMNS) — table already exists at v1.
                db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `title` TEXT")
                db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `artist` TEXT")
                db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `duration_ms` INTEGER")
                db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `artwork_url` TEXT")
                // v1 -> v2: add the saved-tracks and play-history tables.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `provider_id` TEXT NOT NULL,
                        `native_id` TEXT NOT NULL,
                        `saved_at` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT,
                        `duration_ms` INTEGER,
                        `artwork_url` TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_tracks_provider_id_native_id` " +
                        "ON `saved_tracks` (`provider_id`, `native_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_tracks_saved_at` ON `saved_tracks` (`saved_at`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `play_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `provider_id` TEXT NOT NULL,
                        `native_id` TEXT NOT NULL,
                        `played_at` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT,
                        `duration_ms` INTEGER,
                        `artwork_url` TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_play_history_played_at` ON `play_history` (`played_at`)",
                )
            }
        }

        /**
         * v2 -> v3: add the downloads table (Phase 5C).
         * Forward-only migration — existing data preserved (ADR-0003).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `provider_id` TEXT NOT NULL,
                        `native_id` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `progress_bytes` INTEGER NOT NULL DEFAULT 0,
                        `total_bytes` INTEGER,
                        `file_size_bytes` INTEGER,
                        `local_uri` TEXT,
                        `error_reason` TEXT,
                        `error_message` TEXT,
                        `title` TEXT NOT NULL,
                        `artist` TEXT,
                        `duration_ms` INTEGER,
                        `artwork_url` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_downloads_provider_id_native_id` " +
                        "ON `downloads` (`provider_id`, `native_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_downloads_state` ON `downloads` (`state`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_downloads_created_at` ON `downloads` (`created_at`)",
                )
            }
        }

        /**
         * v3 -> v4: add the durable queue session + items (Phase 5D, ADR-0003).
         * Forward-only migration — existing data preserved.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `queue_session` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `kind` TEXT NOT NULL,
                        `continuation_serialized` TEXT,
                        `current_index` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `queue_item` (
                        `session_id` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `provider_id` TEXT NOT NULL,
                        `native_id` TEXT NOT NULL,
                        `title` TEXT,
                        `artist` TEXT,
                        `artwork_url` TEXT,
                        `added_reason` TEXT NOT NULL,
                        PRIMARY KEY(`session_id`, `position`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): CadenceDatabase =
            Room.databaseBuilder(context, CadenceDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build()

        /** In-memory instance for unit tests (no on-disk file). */
        internal fun createInMemory(context: Context): CadenceDatabase =
            Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
                .build()
    }
}
