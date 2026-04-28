package com.spellapp

import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import com.spellapp.core.data.local.CatalogDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogDatabaseAssetTest {
    @Test
    fun bundledCatalogDatabase_opensAndContainsSpellIndex() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = CatalogDatabase.create(context)

        val dao = database.catalogDao()
        assertEquals("1", dao.getMetadataValue("catalog_schema_version"))
        assertTrue(dao.getSpellIndexCount() > 1_000)
        assertEquals("Force Barrage", dao.getSpellDetail("force-barrage")?.name)

        withContext(Dispatchers.IO) {
            database.query(
                SimpleSQLiteQuery(
                    "SELECT record_count FROM catalog_builder_assets WHERE name = ?",
                    arrayOf("classes"),
                ),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getInt(0) > 20)
            }
        }
    }
}
