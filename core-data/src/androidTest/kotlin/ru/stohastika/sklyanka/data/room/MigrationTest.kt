package ru.stohastika.sklyanka.data.room

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
 * схема данных «Склянки»; версия 3 добавила лекарству признак дублирования напоминаний
 * на часы (SKL-10, ЧАС-15); версия 4 — журнал напоминаний (SKL-13); версия 5 — размер
 * упаковки у лекарства (SKL-34, ОВ-45); версия 6 — таблица отложенных срабатываний
 * (SKL-35, ОВ-75); версия 7 — момент мягкого удаления лекарства (SKL-99, ПМ-24);
 * версия 8 вырезала часовой шов обратно (Р-3, SKL-106): обе колонки часового шва уходят,
 * таблицы перестраиваются; версия 9 добавила приёму признак снятия записи «по потребности»
 * (Р-46, SKL-127); версия 10 добавила лекарству вид расписания, а приёму — дозу за приём
 * (SKL-117, ПР-15, ПР-19); версия 11 добавила очередь клиентских событий, а версия 12 её
 * убрала — продукт работает без сети. Тест доказывает заодно, что экспорт схемы
 * действительно работает: без файлов
 * в `core-data/schemas` помощник не смог бы создать базу предыдущей версии.
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

        // Проверяется ровно шаг 2 → 3: arrival колонки на своей версии, сверка со схемой 3.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            SklyankaMigrations.MIGRATION_2_3,
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

    /**
     * Версия 4 → 5 на реальной базе с данными (МГ-3, ОВ-45).
     *
     * Старое лекарство не получает выдуманного размера упаковки: колонка nullable,
     * и `NULL` — это ровно состояние «размер не задан», при котором карточка показывает
     * «осталось N» без полосы. Ни строка лекарства, ни история приёмов не трогаются.
     */
    @Test
    fun thePackageSizeArrivesAsUnknownWithoutTouchingAnyRow() {
        helper.createDatabase(TEST_DB, 4).use { old ->
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
            db.query("SELECT `package_size`, `stock_left` FROM `medication` WHERE `id` = 'medication-1'")
                .use { cursor ->
                    assertTrue("the medication row must survive the migration", cursor.moveToFirst())
                    assertTrue("an old course must have no package size", cursor.isNull(0))
                    assertTrue("the stock must stay untouched", cursor.isNull(1))
                }
            db.query("SELECT COUNT(*) FROM `intake`").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no intake record may be lost (MG-2)", 1, cursor.getInt(0))
            }
        }
    }

    /**
     * Версия 5 → 6 на реальной базе с данными (МГ-3, ОВ-75).
     *
     * Таблица отложенных срабатываний появляется рядом с существующими, не трогая их:
     * ни строка лекарства, ни история приёмов не меняются. Внешний ключ объявлен
     * с `ON DELETE CASCADE` — запись уходит вместе с приёмом, что проверяется отдельно:
     * без каскада удаление приёма при правке расписания оставило бы откладывание,
     * которому больше нечего ждать.
     */
    @Test
    fun theDeferredReminderTableArrivesAndFollowsItsIntake() {
        helper.createDatabase(TEST_DB, 5).use { old ->
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
            assertTrue("the deferred reminder table must appear", tableExists(db, "deferred_reminder"))
            db.query("SELECT COUNT(*) FROM `intake`").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no intake record may be lost (MG-2)", 1, cursor.getInt(0))
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                "INSERT INTO `deferred_reminder` (`intake_id`, `fire_at`, `attempt`) " +
                    "VALUES ('intake-1', 1000, 1)",
            )
            db.execSQL("DELETE FROM `intake` WHERE `id` = 'intake-1'")
            db.query("SELECT COUNT(*) FROM `deferred_reminder`").use { cursor ->
                cursor.moveToFirst()
                assertEquals("the reminder must follow its intake", 0, cursor.getInt(0))
            }
        }
    }

    /**
     * Версия 6 → 7 на реальной базе с данными (МГ-3, ПМ-24, SKL-99).
     *
     * Старое лекарство не получает выдуманного удаления: колонка nullable, `NULL` —
     * ровно состояние «удаления не было». Ни строка лекарства с полями архива,
     * ни история приёмов не трогаются: добавление колонки не перестраивает таблицу.
     */
    @Test
    fun theDeletedAtColumnArrivesWithoutTouchingAnyRow() {
        helper.createDatabase(TEST_DB, 6).use { old ->
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
            db.query(
                "SELECT `deleted_at`, `archived_at`, `archive_reason` FROM `medication` WHERE `id` = 'medication-1'",
            )
                .use { cursor ->
                    assertTrue("the medication row must survive the migration", cursor.moveToFirst())
                    assertTrue("an old course must have no deletion moment", cursor.isNull(0))
                    assertTrue("the archive fields must stay untouched", cursor.isNull(1) && cursor.isNull(2))
                }
            db.query("SELECT COUNT(*) FROM `intake`").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no intake record may be lost (MG-2)", 1, cursor.getInt(0))
            }
        }
    }

    /**
     * Версия 7 → 8 на реальной базе с данными (МГ-3, Р-3, SKL-106).
     *
     * Часовой шов вырезан: `mirror_to_watch` у лекарства и `watch_result` у журнала
     * уходят через перестроение таблиц. Строки не имеют права потерять ни там, ни там:
     * лекарство возвращается со своим остатком и нетронутыми полями архива, журнал —
     * со своей записью показа, колонок шва в схеме уже нет.
     */
    @Test
    fun theWatchColumnsLeaveWithoutTouchingAnyRow() {
        helper.createDatabase(TEST_DB, 7).use { old ->
            old.execSQL(INSERT_PROFILE)
            old.execSQL(INSERT_MEDICATION_WITH_STOCK)
            old.execSQL(INSERT_INTAKE)
            old.execSQL(INSERT_REMINDER_LOG)
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            SklyankaDatabase.VERSION,
            true,
            *SklyankaMigrations.ALL,
        )

        migrated.use { db ->
            db.query(
                "SELECT `stock_left`, `archived_at`, `archive_reason` FROM `medication` WHERE `id` = 'medication-1'",
            )
                .use { cursor ->
                    assertTrue("the medication row must survive the rebuild", cursor.moveToFirst())
                    assertEquals("the stored values must come through untouched", "9", cursor.getString(0))
                    assertTrue("the archive fields must stay untouched", cursor.isNull(1) && cursor.isNull(2))
                }
            db.query("SELECT `event`, `attempt` FROM `reminder_log` WHERE `intake_id` = 'intake-1'")
                .use { cursor ->
                    assertTrue("the journal row must survive the rebuild", cursor.moveToFirst())
                    assertEquals("SHOWN", cursor.getString(0))
                    assertEquals(0, cursor.getInt(1))
                }
            assertTrue(
                "the watch column must be gone from medication",
                !columnExists(db, "medication", "mirror_to_watch"),
            )
            assertTrue(
                "the watch column must be gone from reminder_log",
                !columnExists(db, "reminder_log", "watch_result"),
            )
            db.query("SELECT COUNT(*) FROM `intake`").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no intake record may be lost (MG-2)", 1, cursor.getInt(0))
            }
        }
    }

    /**
     * Версия 8 → 9 на реальной базе с данными (МГ-3, Р-46, SKL-127).
     *
     * Признак снятия записи «по потребности» появляется колонкой, а не перестройкой
     * таблицы и не новым значением перечислителя статуса: индексы, внешние ключи и строки
     * истории не трогаются, а прежние приёмы читаются как неснятые — `NULL` и означает
     * «не снято».
     */
    @Test
    fun theRevokedAtColumnArrivesWithoutTouchingAnyRow() {
        helper.createDatabase(TEST_DB, 8).use { old ->
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
            db.query("SELECT `status`, `revoked_at` FROM `intake` WHERE `id` = 'intake-1'").use { cursor ->
                assertTrue("the intake row must survive the migration", cursor.moveToFirst())
                assertEquals("the status must stay untouched", "PENDING", cursor.getString(0))
                assertTrue("an old record must read as not revoked", cursor.isNull(1))
            }
            db.query("SELECT COUNT(*) FROM `medication`").use { cursor ->
                cursor.moveToFirst()
                assertEquals("no medication row may be lost (MG-2)", 1, cursor.getInt(0))
            }
        }
    }

    /**
     * Версия 9 → 10 на реальной базе с данными (МГ-3, SKL-117).
     *
     * Обе колонки приходят пустыми, и пустое значение осмысленно: `schedule_rule` `NULL`
     * читается как ежедневное расписание, `dose_amount` `NULL` — как «доза берётся
     * у лекарства». Ничего не пересчитывается: строка лекарства, её времена и приём
     * прошлого остаются ровно теми же (критерий приёмки 8).
     */
    @Test
    fun theScheduleRuleAndTheDoseSnapshotArriveWithoutTouchingAnyRow() {
        helper.createDatabase(TEST_DB, 9).use { old ->
            old.execSQL(INSERT_PROFILE)
            old.execSQL(INSERT_MEDICATION)
            old.execSQL(INSERT_INTAKE)
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            SklyankaMigrations.MIGRATION_9_10,
        )

        migrated.use { db ->
            db.query("SELECT `times`, `schedule_rule` FROM `medication` WHERE `id` = 'medication-1'")
                .use { cursor ->
                    assertTrue("the medication row must survive the migration", cursor.moveToFirst())
                    assertEquals("the times must stay untouched", "08:00", cursor.getString(0))
                    assertTrue("an old course reads as an every-day schedule", cursor.isNull(1))
                }
            db.query("SELECT `status`, `scheduled_at`, `dose_amount` FROM `intake` WHERE `id` = 'intake-1'")
                .use { cursor ->
                    assertTrue("the intake row must survive the migration", cursor.moveToFirst())
                    assertEquals("the status must stay untouched", "PENDING", cursor.getString(0))
                    assertEquals("the moment must stay untouched", "2026-09-12T08:00", cursor.getString(1))
                    assertTrue("an old intake takes the dose of its medication", cursor.isNull(2))
                }
        }
    }

    /**
     * Версия 11 → 12 на реальной базе с данными (МГ-3).
     *
     * Очередь клиентских событий уходит целиком: продукт больше не отправляет событий,
     * и держать их без потребителя незачем. Профиль, лекарство и приём остаются ровно
     * теми же — миграция не переписывает ни одной продуктовой строки.
     */
    @Test
    fun theEventQueueTableLeavesWithoutTouchingAnyRow() {
        helper.createDatabase(TEST_DB, 11).use { old ->
            old.execSQL(INSERT_PROFILE)
            old.execSQL(INSERT_MEDICATION)
            old.execSQL(INSERT_INTAKE)
            old.execSQL(
                "INSERT INTO `ANALYTICS_EVENT` (`name`, `properties`, `scope`, `occurred_at`) " +
                    "VALUES ('screen_view', 'screen=today', 'product', 1000)",
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            SklyankaDatabase.VERSION,
            true,
            SklyankaMigrations.MIGRATION_11_12,
        )

        migrated.use { db ->
            assertTrue("the event queue table must be gone", !tableExists(db, "ANALYTICS_EVENT"))
            db.query("SELECT `times`, `schedule_rule` FROM `medication` WHERE `id` = 'medication-1'")
                .use { cursor ->
                    assertTrue("the medication row must survive the migration", cursor.moveToFirst())
                    assertEquals("the times must stay untouched", "08:00", cursor.getString(0))
                    assertTrue("the schedule of an old course must stay untouched", cursor.isNull(1))
                }
            db.query("SELECT `status`, `dose_amount` FROM `intake` WHERE `id` = 'intake-1'").use { cursor ->
                assertTrue("the intake row must survive the migration", cursor.moveToFirst())
                assertEquals("the status must stay untouched", "PENDING", cursor.getString(0))
                assertTrue("the dose of an old intake must stay untouched", cursor.isNull(1))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals("foreign_key_check must come back empty", 0, cursor.count)
            }
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
            .use { it.count > 0 }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("SELECT name FROM pragma_table_info('$table')").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) == column) return true
            }
        }
        return false
    }

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

        val INSERT_MEDICATION = insertMedication("NULL")

        /** Тот же посев, но с ненулевым остатком: перестроение обязана донести значения. */
        val INSERT_MEDICATION_WITH_STOCK = insertMedication("'9'")

        private fun insertMedication(stockLeft: String) = "INSERT INTO `medication` " +
            "(`id`, `profile_id`, `name`, `form`, `shape`, `dose_amount`, `dose_unit`, `strength`, " +
            "`strength_unit`, `food_relation`, `schedule_mode`, `max_per_day`, `times`, `start_date`, " +
            "`end_date`, `stock_left`, `stock_threshold_days`, `archived_at`, `archive_reason`) " +
            "VALUES ('medication-1', 'profile-1', 'med-a', 'TABLET', NULL, '1', 'tablet', NULL, NULL, " +
            "'ANY', 'SCHEDULED', NULL, '08:00', '2026-09-12', NULL, $stockLeft, NULL, NULL, NULL)"

        const val INSERT_INTAKE = "INSERT INTO `intake` " +
            "(`id`, `medication_id`, `scheduled_at`, `schedule_slot`, `status`, `acted_at`, `source`, " +
            "`actor_profile_id`) " +
            "VALUES ('intake-1', 'medication-1', '2026-09-12T08:00', '08:00', 'PENDING', NULL, NULL, NULL)"

        /** Запись показа в журнал версии 7: с колонкой часового шва, которую миграция снимает. */
        const val INSERT_REMINDER_LOG = "INSERT INTO `reminder_log` " +
            "(`occurred_at`, `event`, `intake_id`, `medication_id`, `profile_id`, `scheduled_at`, " +
            "`fired_at`, `deviation_seconds`, `channel`, `delivered`, `attempt`, `watch_result`) " +
            "VALUES (1759125600000, 'SHOWN', 'intake-1', 'medication-1', 'profile-1', " +
            "1759125560000, 1759125600000, 40, 'reminders_regular', 1, 0, 'sent')"
    }
}
