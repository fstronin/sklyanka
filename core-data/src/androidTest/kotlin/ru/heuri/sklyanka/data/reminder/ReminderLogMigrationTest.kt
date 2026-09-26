package ru.heuri.sklyanka.data.reminder

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.data.room.SklyankaDatabase
import ru.heuri.sklyanka.data.room.SklyankaMigrations

/**
 * Миграция, добавляющая журнал срабатываний (SKL-13, версия 4).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется. Помощник поднимает базу версии 2 — последней, чья схема
 * экспортирована в репозиторий на момент задачи, — и прогоняет по ней всю цепочку:
 * на пути лежит и миграция 2 → 3 соседней задачи (SKL-10), поэтому тест заодно
 * доказывает, что цепочка не разорвана.
 */
@RunWith(AndroidJUnit4::class)
class ReminderLogMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SklyankaDatabase::class.java,
    )

    @Test
    fun theJournalOfRemindersAppearsAndTheHistoryOfIntakesSurvives() {
        helper.createDatabase(TEST_DB, 2).use { old ->
            old.execSQL(
                """
                INSERT INTO `profile` (`id`, `name`, `color`, `is_self`, `mirror_reminders`, `self_slot`)
                VALUES ('profile-1', 'self', 0, 1, 0, 'self')
                """.trimIndent(),
            )
            old.execSQL(
                """
                INSERT INTO `medication` (
                    `id`, `profile_id`, `name`, `form`, `dose_amount`, `dose_unit`,
                    `food_relation`, `schedule_mode`, `times`, `start_date`
                ) VALUES (
                    'med-1', 'profile-1', 'magnesium', 'TABLET', '1', 'tablet',
                    'ANY', 'SCHEDULED', '14:00', '2026-09-01'
                )
                """.trimIndent(),
            )
            old.execSQL(
                """
                INSERT INTO `intake` (`id`, `medication_id`, `scheduled_at`, `schedule_slot`, `status`)
                VALUES ('intake-1', 'med-1', '2026-09-25T14:00', 'S', 'PENDING')
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            SklyankaDatabase.VERSION,
            true,
            *SklyankaMigrations.ALL,
        )

        migrated.use { db ->
            assertTrue("reminder_log must exist after the migration", tableExists(db, "reminder_log"))
            // История приёмов не трогается добавлением таблицы — это и есть причина,
            // по которой миграция обходится одним CREATE TABLE (МГ-2).
            db.query("SELECT id, status FROM intake").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("intake-1", cursor.getString(0))
                assertEquals("PENDING", cursor.getString(1))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals("foreign_key_check must come back empty", 0, cursor.count)
            }
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { it.count > 0 }

    private companion object {
        const val TEST_DB = "reminder-log-migration-test.db"
    }
}
