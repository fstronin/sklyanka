package ru.stohastika.sklyanka.acceptance.startup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.data.room.DatabaseState
import ru.stohastika.sklyanka.data.room.DatabaseUpgrade
import ru.stohastika.sklyanka.data.room.DatabaseVersionReader
import ru.stohastika.sklyanka.data.room.MigrationCopyBudget
import ru.stohastika.sklyanka.data.room.MigrationCopyStore
import ru.stohastika.sklyanka.data.room.databaseState
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.startup.DatabaseBlock
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartupFrames
import ru.stohastika.sklyanka.startup.TAG_DATABASE_NOT_ENOUGH_SPACE
import ru.stohastika.sklyanka.startup.startupFrame
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Шаги приёмки копии перед миграцией и отказа из-за места (SKL-94, `ADR-0012` п. 3).
 *
 * Стенд зовёт **ту же подготовку**, что зовёт сборщик базы (`DatabaseUpgrade.prepare`),
 * и то же правило состояния (`databaseState`): решение о копии и об отказе принимает
 * продукт. Room здесь не поднимается намеренно — файл базы в этих сценариях носитель
 * версии и строк, а не настоящая схема: миграцию целиком (с данными, следом и проверками)
 * проверяют инструментальные тесты, где схемы версий лежат в ассетах.
 *
 * Числа места сценарий называет сам («нужно 50 МБ, свободно 10 МБ»): кончившегося места
 * на стенде не устроить, а правило сравнения — продуктовое и проверяется здесь как правило.
 * Базу версии 8 стенд создаёт настоящим файлом с настоящим `PRAGMA user_version`, как
 * и соседняя задача SKL-128: подделка читателя проверяла бы пересказ, а не продукт.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk94DatabaseUpgradeSteps(@Suppress("UNUSED_PARAMETER") world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Имя файла базы сценария — **своё**, а не имя базы приложения.
     *
     * На стенде рядом живёт граф продукта, который тоже открывает свою базу (`SklyankaApp`
     * под Robolectric собирается при первом обращении): файл приложения сценарию трогать
     * нельзя — подменив его базой «версии 8 без настоящей схемы», стенд сломал бы соседний
     * сценарий, и отказ выглядел бы как дефект продукта.
     */
    private val databaseName = SCENARIO_DATABASE

    /** Числа места, названные сценарием: «нужно, свободно» в мегабайтах. `null` — считать по системе. */
    private var stagedBudget: Pair<Long, Long>? = null

    /** Что показал бы запуск: причина блокировки, если база закрыта (Р-44, `ADR-0012` пп. 3, 7). */
    private var block: DatabaseBlock? = null

    /** Открывал ли сценарий базу: без этого блокировки «нет» и «не считали» неотличимы. */
    private var opened = false

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим("^на устройстве база версии (\\d+) с записью приёма$")
    fun theDatabaseHoldsAnIntake(version: Int) {
        databaseFile().delete()
        copyDirectory().deleteRecursively()
        databaseFile().parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile(), null).use { database ->
            database.execSQL("CREATE TABLE IF NOT EXISTS `intake` (`id` TEXT NOT NULL, `status` TEXT NOT NULL)")
            database.execSQL("INSERT INTO `intake` (`id`, `status`) VALUES ('intake-1', 'TAKEN')")
            database.execSQL("PRAGMA user_version = $version")
        }
        assertEquals(
            version,
            DatabaseVersionReader(context, databaseName).read(),
            "the reader must see the staged version",
        )
        stagedBudget = null
    }

    @Допустим("^в месте для копии лежит прежняя копия версии (\\d+)$")
    fun anOlderCopyIsThere(version: Int) {
        val copy = copyOf(version)
        copy.parentFile?.mkdirs()
        copy.writeText("прежняя копия")
    }

    @Допустим("^копии базы нужно (\\d+) МБ, а свободно (\\d+) МБ$")
    fun theCopyBudgetIs(requiredMegabytes: Long, freeMegabytes: Long) {
        stagedBudget = requiredMegabytes to freeMegabytes
    }

    // --- Действия ------------------------------------------------------------------------------

    @Когда("^приложение открывает базу$")
    fun theAppOpensTheDatabase() {
        val state = stateOfScenario()
        // Подготовка — продуктовый код: копию снимает он, а не стенд.
        DatabaseUpgrade(context, FixedTimeSource(Instant.parse(SCENARIO_INSTANT)), databaseName = databaseName)
            .prepare(state)
        block = DatabaseBlock.of(state)
        opened = true
    }

    /**
     * Состояние базы для сценария.
     *
     * Числа места берутся из сценария, если он их назвал, иначе — из системы тем же кодом,
     * что и продукт (`MigrationCopyBudget.of`): тогда проверяется и настоящее чтение фактов.
     */
    private fun stateOfScenario(): DatabaseState {
        val stored = DatabaseVersionReader(context, databaseName).read()
        val budget = stagedBudget?.let { (required, free) ->
            MigrationCopyBudget(requiredBytes = required * MEGABYTE, freeBytes = free * MEGABYTE)
        } ?: MigrationCopyBudget.onlyWhenPending(context, stored, databaseName = databaseName)
        return databaseState(storedVersion = stored, budget = budget)
    }

    // --- Наблюдения: копия ---------------------------------------------------------------------

    @Тогда("^снята копия базы версии (\\d+)$")
    fun theCopyIsTaken(version: Int) {
        val copy = copyOf(version)
        assertTrue(copy.isFile, "копия базы версии $version должна быть снята: ${copy.path}")
        assertEquals(databaseFile().length(), copy.length(), "копия повторяет базу")
    }

    @И("^прежней копии версии (\\d+) больше нет$")
    fun theOlderCopyIsGone(version: Int) {
        assertFalse(copyOf(version).exists(), "прежняя копия должна уйти: ${copyOf(version).path}")
    }

    @И("^запись приёма на месте$")
    fun theIntakeIsStillThere() {
        // Строка читается из самой базы: копия — то, чем её проверит человек, и целость
        // строки нужна и там, где копии по правилу нет вовсе.
        SQLiteDatabase.openDatabase(databaseFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT COUNT(*) FROM `intake` WHERE `id` = 'intake-1'", null).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0), "запись приёма должна пережить копирование")
            }
        }
    }

    @Тогда("^копии базы нет$")
    fun noCopyExists() {
        assertNull(MigrationCopyStore(context, databaseName).current(), "копии быть не должно: копировать нечего")
    }

    // --- Наблюдения: экран ---------------------------------------------------------------------

    @Тогда("^показан экран нехватки места$")
    fun theNoSpaceScreenIsShown() {
        ask { test ->
            test.onNodeWithTag(TAG_DATABASE_NOT_ENOUGH_SPACE).assertExists()
            test.onNodeWithText(context.getString(R.string.database_no_space_title)).assertExists()
        }
    }

    @И("^на экране названо, что не хватает (\\d+) МБ$")
    fun theScreenNamesMissingMegabytes(megabytes: Long) {
        val expected = context.getString(R.string.database_no_space_missing, megabytes)
        ask { test -> test.onNodeWithText(expected).assertExists() }
    }

    @И("^на экране сказано, что данные не тронуты$")
    fun theScreenSaysTheDataIsKept() {
        val expected = context.getString(R.string.database_no_space_data_kept)
        ask { test -> test.onNodeWithText(expected).assertExists() }
    }

    @Но("^экрана «Сегодня» нет$")
    fun theTodayScreenIsAbsent() {
        ask { test -> test.onAllNodesWithTag(TAG_FIRST_SCREEN).assertCountEquals(0) }
    }

    @Тогда("^экран нехватки места не показан$")
    fun theNoSpaceScreenIsAbsent() {
        ask { test -> test.onAllNodesWithTag(TAG_DATABASE_NOT_ENOUGH_SPACE).assertCountEquals(0) }
    }

    // --- Обвязка ---------------------------------------------------------------------------------

    /**
     * Кадр запуска собирается настоящим правилом [startupFrame] с настоящим экраном.
     *
     * Первый экран в стенде — заглушка с тегом, как у соседней задачи SKL-128: настоящий
     * `LaunchRouting` поднял бы граф, а он читает базу, которую в этом состоянии читать
     * нельзя. Заглушка отвечает ровно на вопрос сценария «первого экрана нет».
     */
    private fun ask(question: (ComposeUiTest) -> Unit) {
        check(opened) { "сценарий ещё не открывал базу: причина неизвестна" }
        val reason = block
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    StartupFrames(
                        frame = startupFrame(
                            destination = StartDestination.Today,
                            onboardingUnseen = false,
                            onboardingDone = true,
                            today = TodayUiState.Empty(LocalDate.of(2026, 9, 25)),
                            block = reason,
                        ),
                    ) {
                        Box(modifier = Modifier.testTag(TAG_FIRST_SCREEN))
                    }
                }
            }
            question(this)
            waitForIdle()
        }
    }

    private fun databaseFile(): File = context.getDatabasePath(databaseName)

    private fun copyDirectory(): File = File(context.noBackupFilesDir, MigrationCopyStore.DIRECTORY)

    private fun copyOf(version: Int): File = File(copyDirectory(), MigrationCopyStore.nameOf(databaseName, version))

    private companion object {
        /** Тег заглушки первого экрана: сценарий спрашивает, показан ли первый экран вообще. */
        const val TAG_FIRST_SCREEN = "first_screen_placeholder"

        /** Своё имя базы у стенда: база приложения сценарию не принадлежит (см. [databaseName]). */
        const val SCENARIO_DATABASE = "skl94.db"

        const val MEGABYTE = 1024L * 1024L

        /** Момент из предыстории сценариев: подготовка берёт время из подменяемого источника. */
        const val SCENARIO_INSTANT = "2026-09-25T08:00:00Z"
    }
}
