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
 * схема данных «Склянки»; версия 3 — признак дублирования напоминаний на часы у лекарства
 * (SKL-10, ЧАС-15). Тест доказывает заодно, что экспорт схемы действительно работает:
 * без файлов в `core-data/schemas` помощник не смог бы создать базу предыдущей версии.
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
            *SklyankaMigrations.ALL,
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
            *SklyankaMigrations.ALL,
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

        // МГ-1 требует схему на каждую версию, а не только на текущую: без снимка
        // промежуточной версии её миграцию нечем проверить на реальной базе (МГ-3).
        val missing = (1..SklyankaDatabase.VERSION).map { "$it.json" }.filterNot { it in exported }
        assertTrue("schema files are missing: $missing", missing.isEmpty())
    }

    /**
     * Версия 2 → 3 на реальной базе с данными (МГ-3).
     *
     * Проверяется то, ради чего миграция и делалась неразрушающей: строка лекарства и его
     * приём остаются на месте, а новая колонка получает значение по умолчанию.
     */
    @Test
    fun theWatchMirrorColumnArrivesWithoutTouchingAnyRow() {
        helper.createDatabase(TEST_DB, 2).use { old ->
            old.execSQL(INSERT_PROFILE)
            old.execSQL(INSERT_MEDICATION)
            old.execSQL(INSERT_INTAKE)
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            SklyankaDatabase.VERSION,
            true,
            *SklyankaMigrations.ALL,
        )

        migrated.use { db ->
            db.query("SELECT `mirror_to_watch` FROM `medication` WHERE `id` = 'medication-1'").use { cursor ->
                assertTrue("the medication row must survive the migration", cursor.moveToFirst())
                assertEquals("existing courses mirror to the watch by default", 1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM `intake`").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no intake record may be lost (MG-2)", 1, cursor.getInt(0))
            }
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { it.count > 0 }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /**
         * Строки версии 2 записываются SQL, а не сущностями Room.
         *
         * Сущности уже описывают версию 3, и вставить ими строку в базу предыдущей версии
         * нельзя — именно поэтому тест миграции всегда пишет данные вручную.
         */
        const val INSERT_PROFILE = "INSERT INTO `profile` " +
            "(`id`, `name`, `color`, `is_self`, `mirror_reminders`, `self_slot`) " +
            "VALUES ('profile-1', 'owner', 0, 1, 0, 'self')"

        const val INSERT_MEDICATION = "INSERT INTO `medication` " +
            "(`id`, `profile_id`, `name`, `form`, `shape`, `dose_amount`, `dose_unit`, `strength`, " +
            "`strength_unit`, `food_relation`, `schedule_mode`, `max_per_day`, `times`, `start_date`, " +
            "`end_date`, `stock_left`, `stock_threshold_days`, `archived_at`, `archive_reason`) " +
            "VALUES ('medication-1', 'profile-1', 'med-a', 'TABLET', NULL, '1', 'tablet', NULL, NULL, " +
            "'ANY', 'SCHEDULED', NULL, '08:00', '2026-09-12', NULL, NULL, NULL, NULL, NULL)"

        const val INSERT_INTAKE = "INSERT INTO `intake` " +
            "(`id`, `medication_id`, `scheduled_at`, `schedule_slot`, `status`, `acted_at`, `source`, " +
            "`actor_profile_id`) " +
            "VALUES ('intake-1', 'medication-1', '2026-09-12T08:00', '08:00', 'PENDING', NULL, NULL, NULL)"
    }
}
