package ru.stohastika.sklyanka.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.data.log.RoomOperationLog
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.io.File
import java.time.Instant

/**
 * Переход схемы целиком: копия, миграция, след, проверки (`ADR-0012` пп. 3, 4, SKL-94).
 *
 * Открытие идёт **тем же путём, что в продукте**: подготовка (копия) — до `build()`, открытие
 * с миграциями — `withForeignKeysEnabled()`, след и проверки — `DatabaseMaintenance`. Room
 * в тесте настоящий, база предыдущей версии — настоящий файл с настоящей схемой версии 8
 * из ассетов: подделка открытия проверяла бы пересказ, а не продукт.
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseUpgradeTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SklyankaDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val time = FixedTimeSource(Instant.parse("2026-09-25T08:00:00Z"))

    @Before
    fun cleanUp() {
        deleteEverything()
    }

    @After
    fun cleanDown() {
        deleteEverything()
    }

    @Test
    fun aMigrationLeavesACopyATraceWithoutMedicalDataAndIntactRows() = runBlocking {
        helper.createDatabase(SklyankaDatabase.NAME, 8).use { old ->
            old.execSQL(INSERT_PROFILE)
            old.execSQL(INSERT_MEDICATION)
            old.execSQL(INSERT_INTAKE)
        }

        val upgrade = DatabaseUpgrade(context, time)
        assertEquals(DatabaseState.Ready, upgrade.state())

        val outcome = upgrade.prepare()
        assertTrue("the preparation must take a copy: $outcome", outcome is DatabaseUpgradeOutcome.CopyTaken)

        val copy = requireNotNull(MigrationCopyStore(context).current()) { "the copy was not taken" }
        assertEquals("the copy is identified by the version it was taken from", "sklyanka.db.v8", copy.name)

        val database = openAndMaintain(upgrade)
        try {
            assertRowsAreIntact(database)

            val log = RoomOperationLog(database.opLogDao(), time)
            val records = log.recent(LOOKBACK)

            val trace = records.first { it.event == OpEvent.MIGRATION_APPLIED }
            assertEquals("the version from", "8", trace.before)
            assertEquals("the version to", "9", trace.after)
            assertTrue(
                "the copy fact must be in the trace: ${trace.detail}",
                trace.detail.orEmpty().contains("copy=yes"),
            )
            assertTrue(
                "the duration must be in the trace: ${trace.detail}",
                trace.detail.orEmpty().contains("durationMs="),
            )
            assertNull("the trace carries no intake", trace.intakeId)
            assertNull("the trace carries no medication", trace.medicationId)
            assertNull("the trace carries no profile", trace.profileId)
            assertFalse(
                "no medication name may appear in the trace (NFR-19)",
                trace.detail.orEmpty().contains(MEDICATION_NAME),
            )

            val check = records.first { it.event == OpEvent.INTEGRITY_CHECKED }
            assertEquals("an intact database leaves no findings", "quickCheck=ok;foreignKeys=0", check.detail)
            assertEquals("the check after the migration", "migration", check.source)
        } finally {
            database.close()
        }
    }

    @Test
    fun aDatabaseOfTheCurrentVersionNeedsNeitherACopyNorATrace() = runBlocking {
        helper.createDatabase(SklyankaDatabase.NAME, SklyankaDatabase.VERSION).close()

        val upgrade = DatabaseUpgrade(context, time)
        assertEquals(DatabaseUpgradeOutcome.NoMigration, upgrade.prepare())
        assertNull("nothing to copy", MigrationCopyStore(context).current())

        val database = openAndMaintain(upgrade)
        try {
            val log = RoomOperationLog(database.opLogDao(), time)
            assertTrue(
                "no migration trace may exist without a migration",
                log.recent(LOOKBACK).none { it.event == OpEvent.MIGRATION_APPLIED },
            )
        } finally {
            database.close()
        }
    }

    /** Открытие тем же путём, что в продукте: сначала миграции, потом след и проверки. */
    private suspend fun openAndMaintain(upgrade: DatabaseUpgrade): SklyankaDatabase {
        val database = Room.databaseBuilder(context, SklyankaDatabase::class.java, SklyankaDatabase.NAME)
            .addMigrations(*SklyankaMigrations.ALL)
            .build()
            .withForeignKeysEnabled()
        DatabaseMaintenance(database, RoomOperationLog(database.opLogDao(), time), time, upgrade).onStart()
        return database
    }

    private fun assertRowsAreIntact(database: SklyankaDatabase) {
        val sqlite = database.openHelper.writableDatabase
        assertEquals("the profile is intact", 1, countOf(sqlite, "profile"))
        assertEquals("the medication is intact", 1, countOf(sqlite, "medication"))
        assertEquals("the intake is intact", 1, countOf(sqlite, "intake"))
        assertEquals(
            "the medication name is not rewritten by the migration",
            MEDICATION_NAME,
            textOf(sqlite, "SELECT `name` FROM `medication` WHERE `id` = 'medication-1'"),
        )
    }

    private fun countOf(database: SupportSQLiteDatabase, table: String): Int =
        database.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun textOf(database: SupportSQLiteDatabase, sql: String): String? = database.query(sql).use { cursor ->
        cursor.moveToFirst()
        cursor.getString(0)
    }

    private fun deleteEverything() {
        context.deleteDatabase(SklyankaDatabase.NAME)
        context.deleteDatabase(TEST_DB)
        File(context.noBackupFilesDir, MigrationCopyStore.DIRECTORY).deleteRecursively()
        val path = context.getDatabasePath(SklyankaDatabase.NAME)
        File(path.path + "-wal").delete()
        File(path.path + "-shm").delete()
    }

    private companion object {
        const val TEST_DB = "upgrade-test.db"
        const val LOOKBACK = 20
        const val MEDICATION_NAME = "med-a"

        const val INSERT_PROFILE = "INSERT INTO `profile` " +
            "(`id`, `name`, `color`, `is_self`, `mirror_reminders`, `self_slot`) " +
            "VALUES ('profile-1', 'owner', 0, 1, 0, 'self')"

        const val INSERT_MEDICATION = "INSERT INTO `medication` " +
            "(`id`, `profile_id`, `name`, `form`, `shape`, `dose_amount`, `dose_unit`, `strength`, " +
            "`strength_unit`, `food_relation`, `schedule_mode`, `max_per_day`, `times`, `start_date`, " +
            "`end_date`, `stock_left`, `stock_threshold_days`, `archived_at`, `archive_reason`) " +
            "VALUES ('medication-1', 'profile-1', '$MEDICATION_NAME', 'TABLET', NULL, '1', 'tablet', NULL, " +
            "NULL, 'ANY', 'SCHEDULED', NULL, '08:00', '2026-09-12', NULL, NULL, NULL, NULL, NULL)"

        const val INSERT_INTAKE = "INSERT INTO `intake` " +
            "(`id`, `medication_id`, `scheduled_at`, `schedule_slot`, `status`, `acted_at`, `source`, " +
            "`actor_profile_id`) " +
            "VALUES ('intake-1', 'medication-1', '2026-09-12T08:00', '08:00', 'PENDING', NULL, NULL, NULL)"
    }
}
