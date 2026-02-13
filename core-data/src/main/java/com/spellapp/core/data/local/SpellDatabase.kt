package com.spellapp.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SpellEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SpellDatabase : RoomDatabase() {
    abstract fun spellDao(): SpellDao

    companion object {
        private const val DATABASE_NAME = "spellapp.db"
        @Volatile
        private var INSTANCE: SpellDatabase? = null

        fun create(context: Context): SpellDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context,
                    SpellDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { INSTANCE = it }
            }
        }
    }
}
