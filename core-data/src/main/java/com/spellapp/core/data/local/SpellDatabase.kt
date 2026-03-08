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
        CharacterBuildIdentityEntity::class,
        CharacterBuildOptionEntity::class,
        KnownSpellEntity::class,
        PreparedSlotEntity::class,
        CastingTrackEntity::class,
        FocusStateEntity::class,
        SessionEventEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class SpellDatabase : RoomDatabase() {
    abstract fun spellDao(): SpellDao
    abstract fun characterDao(): CharacterDao
    abstract fun characterBuildIdentityDao(): CharacterBuildIdentityDao
    abstract fun characterBuildOptionDao(): CharacterBuildOptionDao
    abstract fun knownSpellDao(): KnownSpellDao
    abstract fun preparedSlotDao(): PreparedSlotDao
    abstract fun castingTrackDao(): CastingTrackDao
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `casting_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER NOT NULL,
                        `trackKey` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceId` TEXT NOT NULL,
                        `progressionType` TEXT NOT NULL,
                        FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_casting_tracks_characterId` ON `casting_tracks` (`characterId`)",
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_casting_tracks_characterId_trackKey`
                    ON `casting_tracks` (`characterId`, `trackKey`)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_build_identity` (
                        `characterId` INTEGER NOT NULL,
                        `ancestryId` TEXT,
                        `heritageId` TEXT,
                        `backgroundId` TEXT,
                        PRIMARY KEY(`characterId`),
                        FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `character_build_options` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER NOT NULL,
                        `optionType` TEXT NOT NULL,
                        `optionId` TEXT NOT NULL,
                        `levelAcquired` INTEGER,
                        `metadataJson` TEXT NOT NULL,
                        FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_character_build_options_characterId`
                    ON `character_build_options` (`characterId`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_character_build_options_characterId_optionType`
                    ON `character_build_options` (`characterId`, `optionType`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_character_build_options_characterId_optionType_optionId`
                    ON `character_build_options` (`characterId`, `optionType`, `optionId`)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `known_spells` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER NOT NULL,
                        `trackKey` TEXT NOT NULL,
                        `spellId` TEXT NOT NULL,
                        FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_known_spells_characterId`
                    ON `known_spells` (`characterId`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_known_spells_characterId_trackKey_spellId`
                    ON `known_spells` (`characterId`, `trackKey`, `spellId`)
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
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
