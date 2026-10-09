package ru.stohastika.sklyanka.data.room

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Объёмный прогон миграции: база года приёма переходит на текущую схему (НФТ-9, SKL-94).
 *
 * Наполнение — по НФТ-9: 4 профиля, 30 лекарств, 4 000 приёмов. Смысл отдельного прогона
 * в том, что маленький тест миграции проверяет **правильность**, а не цену: на объёме
 * видно и потерю строк, и то, что переход не оставляет висячих ссылок. Контрольная сумма
 * строк снимается до перехода и сверяется после — «сколько строк» не отличает потерю
 * от подмены, а сумма длин полей отличает.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MigrationVolumeTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SklyankaDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aYearOfIntakesComesThroughTheMigrationUntouched() {
        context.deleteDatabase(TEST_DB)

        var before = ""
        var intakesBefore = 0
        helper.createDatabase(TEST_DB, FROM_VERSION).use { old ->
            fillYear(old)
            before = checksum(old)
            intakesBefore = countOf(old)
        }
        assertEquals("the filling must be a year of intakes", INTAKES, intakesBefore)

        helper.runMigrationsAndValidate(
            TEST_DB,
            SklyankaDatabase.VERSION,
            true,
            *SklyankaMigrations.ALL,
        ).use { migrated ->
            assertEquals("the number of intakes is unchanged", INTAKES, countOf(migrated))
            assertEquals("the row checksum is unchanged", before, checksum(migrated))
            migrated.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals("no broken references may survive the migration", 0, cursor.count)
            }
        }

        context.deleteDatabase(TEST_DB)
    }

    /** Наполнение года приёма: 4 профиля, 30 лекарств, 4 000 приёмов (НФТ-9). */
    private fun fillYear(database: SupportSQLiteDatabase) {
        database.beginTransaction()
        try {
            (0 until PROFILES).forEach { index ->
                database.execSQL(
                    "INSERT INTO `profile` (`id`, `name`, `color`, `is_self`, `mirror_reminders`, `self_slot`) " +
                        "VALUES ('profile-$index', 'profile-$index', $index, ${if (index == 0) 1 else 0}, 0, " +
                        "${if (index == 0) "'self'" else "NULL"})",
                )
            }
            (0 until MEDICATIONS).forEach { index ->
                database.execSQL(
                    "INSERT INTO `medication` (`id`, `profile_id`, `name`, `form`, `dose_amount`, `dose_unit`, " +
                        "`food_relation`, `schedule_mode`, `times`, `start_date`, `stock_left`) " +
                        "VALUES ('medication-$index', 'profile-${index % PROFILES}', 'med-$index', 'TABLET', " +
                        "'1', 'tablet', 'ANY', 'SCHEDULED', '08:00', '2026-01-01', '30')",
                )
            }
            (0 until INTAKES).forEach { index ->
                // Момент приёма различается по дню: у таблицы есть уникальность
                // «лекарство + плановое время + слот», и 4 000 приёмов тридцати лекарств
                // обязаны иметь различные моменты, иначе наполнение не запишется вовсе.
                val day = FIRST_DAY.plusDays((index / MEDICATIONS).toLong())
                database.execSQL(
                    "INSERT INTO `intake` (`id`, `medication_id`, `scheduled_at`, `schedule_slot`, " +
                        "`status`, `acted_at`) " +
                        "VALUES ('intake-$index', 'medication-${index % MEDICATIONS}', '${day}T08:00', '08:00', " +
                        "'${if (index % 7 == 0) "MISSED" else "TAKEN"}', ${1000 + index})",
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun countOf(database: SupportSQLiteDatabase): Int =
        database.query("SELECT COUNT(*) FROM `intake`").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    /** Контрольное значение строк: число и сумма длин полей — «сколько» не отличает подмену. */
    private fun checksum(database: SupportSQLiteDatabase): String = database.query(
        "SELECT COUNT(*) || ':' || COALESCE(SUM(LENGTH(`id`) + LENGTH(`medication_id`) + " +
            "LENGTH(`scheduled_at`) + LENGTH(`status`) + COALESCE(`acted_at`, 0)), 0) FROM `intake`",
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getString(0)
    }

    private companion object {
        const val TEST_DB = "volume-migration.db"

        /** Версия, с которой начинается переход: текущая выпущенная схема до SKL-127. */
        const val FROM_VERSION = 8

        const val PROFILES = 4
        const val MEDICATIONS = 30
        const val INTAKES = 4_000

        /** Первый день наполнения: моменты приёмов разводятся по дням от него. */
        val FIRST_DAY: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
