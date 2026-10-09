package ru.stohastika.sklyanka.data.room

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Цепочка переходов с любой выпущенной версии (`ADR-0012` п. 1, SKL-94).
 *
 * Пошаговые тесты миграций отвечают на вопрос «переносит ли строки этот переход». Здесь
 * вопрос другой и его не задаёт больше никто: доедет ли база, **выпущенная год назад и ни
 * разу не обновлявшаяся**, до текущей схемы — через всю цепочку, а не через один шаг.
 * Схема каждой версии берётся из ассетов, то есть из закоммиченных снимков (МГ-1).
 *
 * Версия 1 — технический скелет без продуктовых данных: её единственная таблица-маркер
 * удаляется первой миграцией намеренно, терять там нечего (`ADR-0012` п. 2, МГ-2).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется.
 */
@RunWith(AndroidJUnit4::class)
class MigrationChainTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SklyankaDatabase::class.java,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyReleasedVersionCarriesItsRowsIntoTheCurrentSchema() {
        (FIRST_DATA_VERSION until SklyankaDatabase.VERSION).forEach { version ->
            val name = "chain-v$version.db"
            context.deleteDatabase(name)
            helper.createDatabase(name, version).use { old ->
                old.execSQL(
                    "INSERT INTO `profile` (`id`, `name`, `color`, `is_self`, `mirror_reminders`, `self_slot`) " +
                        "VALUES ('profile-1', 'owner-$version', 0, 1, 0, 'self')",
                )
            }

            helper.runMigrationsAndValidate(
                name,
                SklyankaDatabase.VERSION,
                true,
                *SklyankaMigrations.ALL,
            ).use { migrated ->
                migrated.query("SELECT `name` FROM `profile` WHERE `id` = 'profile-1'").use { cursor ->
                    assertTrue("version $version must carry the profile row", cursor.moveToFirst())
                    assertEquals(
                        "the row of version $version is not replaced or lost",
                        "owner-$version",
                        cursor.getString(0),
                    )
                }
                migrated.query("PRAGMA foreign_key_check").use { cursor ->
                    assertEquals("version $version must leave no broken references", 0, cursor.count)
                }
            }
            context.deleteDatabase(name)
        }
    }

    private companion object {
        /** Первая версия с продуктовыми данными: до неё схема была техническим скелетом. */
        const val FIRST_DATA_VERSION = 2
    }
}
