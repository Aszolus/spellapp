package com.spellapp.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CatalogMetadataEntity::class,
        CatalogRecordEntity::class,
        CatalogSpellIndexEntity::class,
        CatalogUuidIndexEntity::class,
        CatalogLinkEntity::class,
        CatalogBuilderAssetEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao

    companion object {
        private const val DATABASE_NAME = "spellapp-catalog.db"
        private const val ASSET_PATH = "catalog/catalog.db"

        @Volatile
        private var INSTANCE: CatalogDatabase? = null

        fun create(context: Context): CatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    DATABASE_NAME,
                )
                    .createFromAsset(ASSET_PATH)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
