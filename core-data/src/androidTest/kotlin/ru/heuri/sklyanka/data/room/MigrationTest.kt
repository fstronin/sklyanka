package ru.heuri.sklyanka.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Миграции на реальной базе предыдущей версии (МГ-3).
 *
 * Версия 1 — скелет SKL-02 с единственной технической таблицей-маркером; версия 2 —
 * схема данных «Склянки». Тест доказывает заодно, что экспорт схемы действительно
 * работает: без файлов в `core-data/schemas` помощник не смог бы создать базу версии 1.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SklyankaDatabase::class.java,
    )

    @Test
    fun theSkeletonDatabaseMigratesIntoTheDataSchema() {
        helper.createDatabase(TEST_DB, 1).use { old ->
            old.execSQL("INSERT INTO `database_marker` (`id`) VALUES (1)")
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            SklyankaDatabase.VERSION,
            true,
            SklyankaMigrations.MIGRATION_1_2,
        )

        migrated.use { db ->
            assertTrue(tableExists(db, "profile"))
            assertTrue(tableExists(db, "medication"))
            assertTrue(tableExists(db, "intake"))
            assertTrue(tableExists(db, "op_log"))
            assertTrue("the technical marker table must not survive", !tableExists(db, "database_marker"))
        }
    }

    @Test
    fun migrationLeavesNoBrokenReferences() {
        helper.createDatabase(TEST_DB, 1).close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            SklyankaDatabase.VERSION,
            true,
            SklyankaMigrations.MIGRATION_1_2,
        )

        migrated.use { db ->
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals("foreign_key_check must come back empty", 0, cursor.count)
            }
        }
    }

    @Test
    fun everySchemaVersionIsExportedIntoTheRepository() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schemaFolder = requireNotNull(SklyankaDatabase::class.qualifiedName)
        val exported = assets.list(schemaFolder).orEmpty().toList()

        assertTrue("schema 1.json is missing", "1.json" in exported)
        assertTrue("schema 2.json is missing", "2.json" in exported)
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { it.count > 0 }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
