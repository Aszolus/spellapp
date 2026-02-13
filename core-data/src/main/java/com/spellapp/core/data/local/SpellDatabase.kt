package com.spellapp.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SpellEntity::class,
        CharacterEntity::class,
        PreparedSlotEntity::class,
        FocusStateEntity::class,
        SessionEventEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SpellDatabase : RoomDatabase() {
    abstract fun spellDao(): SpellDao
    abstract fun characterDao(): CharacterDao
    abstract fun preparedSlotDao(): PreparedSlotDao
    abstract fun focusStateDao(): FocusStateDao
    abstract fun sessionEventDao(): SessionEventDao

    companion object {
        private const val DATABASE_NAME = "spellapp.db"
        @Volatile
        private var INSTANCE: SpellDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `characters` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `level` INTEGER NOT NULL,
                        `characterClass` TEXT NOT NULL,
                        `keyAbility` TEXT NOT NULL,
                        `spellDc` INTEGER NOT NULL,
                        `spellAttackModifier` INTEGER NOT NULL,
                        `legacyTerminologyEnabled` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_characters_name` ON `characters` (`name`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prepared_slots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER NOT NULL,
                        `rank` INTEGER NOT NULL,
                        `slotIndex` INTEGER NOT NULL,
                        `preparedSpellId` TEXT,
                        `isExpended` INTEGER NOT NULL,
                        FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_prepared_slots_characterId` ON `prepared_slots` (`characterId`)",
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_prepared_slots_characterId_rank_slotIndex`
                    ON `prepared_slots` (`characterId`, `rank`, `slotIndex`)
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_states` (
                        `characterId` INTEGER NOT NULL,
                        `currentPoints` INTEGER NOT NULL,
                        `maxPoints` INTEGER NOT NULL,
                        PRIMARY KEY(`characterId`),
                        FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_states_characterId` ON `focus_states` (`characterId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `spellId` TEXT,
                        `spellRank` INTEGER,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `metadataJson` TEXT NOT NULL,
                        FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_events_characterId` ON `session_events` (`characterId`)",
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_session_events_characterId_createdAtEpochMillis`
                    ON `session_events` (`characterId`, `createdAtEpochMillis`)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE `prepared_slots`
                    ADD COLUMN `trackKey` TEXT NOT NULL DEFAULT 'primary'
                    """.trimIndent(),
                )
                db.execSQL(
                    "DROP INDEX IF EXISTS `index_prepared_slots_characterId_rank_slotIndex`",
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_prepared_slots_characterId_trackKey_rank_slotIndex`
                    ON `prepared_slots` (`characterId`, `trackKey`, `rank`, `slotIndex`)
                    """.trimIndent(),
                )
            }
        }

        fun create(context: Context): SpellDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context,
                    SpellDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
