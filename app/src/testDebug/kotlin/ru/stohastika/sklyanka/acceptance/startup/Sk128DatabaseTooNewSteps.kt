package ru.stohastika.sklyanka.acceptance.startup

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.data.room.DatabaseState
import ru.stohastika.sklyanka.data.room.SklyankaDatabase
import ru.stohastika.sklyanka.data.room.databaseStateOf
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.platform.reminder.ReminderAlarms
import ru.stohastika.sklyanka.reminder.AlarmReceiver
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartupFrames
import ru.stohastika.sklyanka.startup.TAG_DATABASE_TOO_NEW
import ru.stohastika.sklyanka.startup.startupFrame
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаги приёмки отказа от работы на базе новее установленной сборки (SKL-128, Р-44).
 *
 * База новее воспроизводится **настоящим файлом базы** с завышенным `PRAGMA user_version`:
 * читатель версии в стенде не подделывается — подделка проверяла бы пересказ правила,
 * а не тот же код, что спрашивает продукт. Сработавший будильник подаётся настоящим
 * [AlarmReceiver], а сообщение человеку — настоящее уведомление приложения: приёмка
 * смотрит на него через тень `NotificationManager` Robolectric.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class Sk128DatabaseTooNewSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Назван ли сценарием файл базы новее: без этого признака сценарий идёт на первой установке. */
    private var databaseIsNewer = false

    // --- Предыстория ---------------------------------------------------------------------------

    @Допустим("^база на устройстве новее установленной сборки$")
    fun theDatabaseIsNewer() {
        val path = context.getDatabasePath(SklyankaDatabase.NAME)
        path.parentFile?.mkdirs()
        path.delete()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            // Ровно то состояние, которое оставляет более новая сборка: схема новее,
            // чем знает эта. Данные не нужны: читать их всё равно нельзя.
            database.execSQL("PRAGMA user_version = ${SklyankaDatabase.VERSION + 1}")
        }
        databaseIsNewer = true
        assertEquals(
            DatabaseState.Newer,
            databaseStateOf(context),
            "the reader must see the newer database",
        )
    }

    // --- Действия ------------------------------------------------------------------------------

    @Когда("^приложение открывается$")
    fun theAppOpens() = ensureScenarioDatabase()

    /**
     * Файл базы приводится к тому, что назвал сценарий.
     *
     * Файл, оставленный предыдущим сценарием, не должен переезжать в следующий: сценарий
     * без данности «база новее» идёт по первой установке, где файла ещё нет.
     */
    private fun ensureScenarioDatabase() {
        if (!databaseIsNewer) context.getDatabasePath(SklyankaDatabase.NAME).delete()
    }

    @Когда("^в (\\d{2}:\\d{2}) срабатывает будильник напоминания об этом приёме$")
    fun theAlarmFires(time: String) {
        val intake = world.intakes.currentOrScheduled()
        assertEquals(
            LocalTime.parse(time),
            intake.scheduledAt.toLocalTime(),
            "the scenario names the moment of the intake",
        )
        // Настоящий приёмник с настоящим дополнением: решение о судьбе срабатывания
        // принимает продукт, а не стенд.
        AlarmReceiver().onReceive(
            context,
            Intent(ReminderAlarms.ACTION_INTAKE)
                .putExtra(ReminderAlarms.EXTRA_INTAKE_ID, intake.id)
                .putExtra(ReminderAlarms.EXTRA_ATTEMPT, 0),
        )
    }

    // --- Наблюдения: кадр запуска ---------------------------------------------------------------

    @Тогда("^показан блокирующий экран «([^»]+)»$")
    fun theBlockingScreenIsShown(title: String) {
        ask { test ->
            test.onNodeWithTag(TAG_DATABASE_TOO_NEW).assertExists()
            test.onNodeWithText(title).assertExists()
        }
    }

    @И("^на экране сказано, что данные на устройстве не тронуты$")
    fun theScreenSaysTheDataIsKept() {
        val expected = context.getString(R.string.database_too_new_data_kept)
        ask { test -> test.onNodeWithText(expected).assertExists() }
    }

    @Но("^экрана «Сегодня» в приложении нет$")
    fun theTodayScreenIsAbsent() {
        ask { test ->
            test.onAllNodesWithTag(TAG_FIRST_SCREEN).assertCountEquals(0)
        }
    }

    @Тогда("^блокирующего экрана нет$")
    fun theBlockingScreenIsAbsent() {
        ask { test -> test.onAllNodesWithTag(TAG_DATABASE_TOO_NEW).assertCountEquals(0) }
    }

    // --- Наблюдения: будильник -------------------------------------------------------------------

    @Тогда("^отметок приёма не появилось$")
    fun noIntakeWasMarked() {
        val marked = world.intakes.of(world.medications.last).filter { it.status == IntakeStatus.TAKEN }
        assertTrue(marked.isEmpty(), "the fired alarm must not create a mark: $marked")
    }

    @И("^карточка приёма не показана$")
    fun noCardWasShown() {
        assertTrue(world.reminders.shownCards.isEmpty(), "no intake card may be shown")
    }

    @Но("^человеку сказано обновить приложение$")
    fun thePersonIsAskedToUpdate() {
        val manager: NotificationManager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        val shown = shadowOf(manager).allNotifications
        val titles = shown.map { it.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() }
        assertTrue(
            titles.contains(context.getString(R.string.database_too_new_notification_title)),
            "the update notice must be shown: $titles",
        )
        // И это не карточка приёма: полноэкранного намерения у сообщения нет, приёма
        // за ним тоже — лживого напоминания в сборке не появляется (П-7).
        assertTrue(shown.all { it.fullScreenIntent == null }, "the update notice must not open a card")
    }

    // --- Обвязка ---------------------------------------------------------------------------------

    /**
     * Кадр запуска собирается настоящим правилом [startupFrame] с настоящим экраном отказа.
     *
     * Первый экран в стенде — заглушка с тегом: настоящий `LaunchRouting` поднял бы граф,
     * а он читает базу, которую в этом состоянии читать нельзя. Заглушка отвечает ровно
     * на вопрос сценария «первого экрана нет».
     */
    private fun ask(question: (ComposeUiTest) -> Unit) {
        ensureScenarioDatabase()
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    StartupFrames(
                        frame = startupFrame(
                            destination = StartDestination.Today,
                            onboardingUnseen = false,
                            onboardingDone = true,
                            today = TodayUiState.Empty(LocalDate.of(2026, 9, 25)),
                            databaseTooNew = databaseStateOf(context) == DatabaseState.Newer,
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

    private companion object {
        /** Тег заглушки первого экрана: сценарий спрашивает, показан ли первый экран вообще. */
        const val TAG_FIRST_SCREEN = "first_screen_placeholder"
    }
}
