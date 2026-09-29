package ru.stohastika.sklyanka.acceptance.history

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransactor
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildDoctorReportUseCase
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.ObserveAdherenceUseCase
import ru.stohastika.sklyanka.domain.history.TransferDoctorReportUseCase
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.intake.UndoMarkUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.today.MarkIntakeGroupUseCase
import ru.stohastika.sklyanka.domain.today.ObserveTodayAgendaUseCase
import ru.stohastika.sklyanka.feature.history.HistoryViewModel
import ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN
import ru.stohastika.sklyanka.feature.history.TextDoctorReportRenderer
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.feature.settings.SettingsIntent
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.today.TodayNavigation
import ru.stohastika.sklyanka.feature.today.TodayRoute
import ru.stohastika.sklyanka.feature.today.TodayViewModel
import ru.stohastika.sklyanka.navigation.HistoryOrigin
import ru.stohastika.sklyanka.navigation.HistorySection
import ru.stohastika.sklyanka.platform.report.SystemDoctorReportTransfer
import ru.stohastika.sklyanka.ui.component.TAG_PROGRESS_LINK
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги входов в историю и возврата (SKL-18 и SKL-31, группа `@composition`).
 *
 * Модуль выбран по правилу `TDD.md §4.3`: сценарии говорят и о другом экране, и об
 * истории, а оба экрана существуют одновременно только в `app`. Здесь собирается
 * настоящая связка продукта: строка «История приёмов» экрана настроек отдаёт
 * `OpenHistory` (вход из настроек, SKL-20), строка прогресса дня на «Сегодня» —
 * `OpenHistory` через `TodayRoute` (вход с «Сегодня», ОВ-84), а `HistorySection`
 * с местом входа ведёт возврат туда же, откуда пришли.
 *
 * Место входа — значение [origin], а не вывод из того, какой экран был отрисован
 * последним: примеры структуры «Возврат из истории ведёт туда, откуда её открыли»
 * различаются только им.
 *
 * Сам `HistoryViewModel` — настоящий: история обязана быть собрана из мира сценария
 * (`world.history`), а не подставлена заглушкой. Сквозной путь через вкладки нижней панели
 * остаётся работой `MainActivity`: стенд собирает те же звенья по отдельности.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryNavigationSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resources = context.withInterfaceLocale().resources

    /** Место входа текущего сценария: «из настроек» — умолчание, «с „Сегодня“» — шаг входа. */
    private var origin: HistoryOrigin = HistoryOrigin.SETTINGS

    private var leftWith: HistoryOrigin? = null

    @Допустим("^человек открыл историю с «Сегодня»$")
    fun thePersonOpenedHistoryFromToday() {
        origin = HistoryOrigin.TODAY
        // Тот же вход, что в ветке `historyFromToday` корня приложения: `TodayRoute`
        // отдаёт `OpenHistory`, маршрут сменяет «Сегодня» на историю с местом входа TODAY.
        val today = todayViewModel()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runComposeUiTest {
                setContent {
                    Pinned {
                        var historyFromToday by remember { mutableStateOf(false) }
                        if (historyFromToday) {
                            HistorySection(
                                viewModel = historyViewModel(),
                                origin = HistoryOrigin.TODAY,
                                onLeave = {},
                            )
                        } else {
                            TodayRoute(
                                viewModel = today,
                                navigation = TodayNavigation(openHistory = { historyFromToday = true }),
                            )
                        }
                    }
                }
                onNodeWithTag(TAG_PROGRESS_LINK, useUnmergedTree = true).performClick()
                waitForIdle()
                // Нажатие не просто родило намерение: маршрут действительно показал историю.
                onNodeWithTag(TAG_HISTORY_SCREEN, useUnmergedTree = true).assertExists()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Когда("^он нажимает «Назад»$")
    fun hePressesBack() {
        val viewModel = historyViewModel()
        runComposeUiTest {
            setContent {
                Pinned {
                    HistorySection(
                        viewModel = viewModel,
                        origin = origin,
                        onLeave = { left -> leftWith = left },
                    )
                }
            }
            onNodeWithContentDescription(resources.getString(UiR.string.cd_back)).performClick()
            waitForIdle()
        }
    }

    @Тогда("^он возвращается в настройки$")
    fun heReturnsToSettings() {
        assertEquals(
            "the return must lead to the place the history was opened from (ПРФ-11)",
            HistoryOrigin.SETTINGS,
            leftWith,
        )
        // Вход действительно существует: строка «История приёмов» на экране настроек
        // отдаёт то же намерение, которое `SettingsSection` превращает в этот маршрут.
        val intents = mutableListOf<SettingsIntent>()
        runComposeUiTest {
            setContent {
                Pinned {
                    SettingsScreen(state = SettingsUiState(), onIntent = intents::add)
                }
            }
            onNodeWithText(resources.getString(R.string.settings_history)).performClick()
            waitForIdle()
        }
        assertTrue(
            "the settings screen must offer the entry into the history: $intents",
            intents.any { it is SettingsIntent.OpenHistory },
        )
    }

    @Тогда("^он возвращается на «Сегодня»$")
    fun heReturnsToToday() {
        assertEquals(
            "the return must lead to the place the history was opened from (ОВ-84)",
            HistoryOrigin.TODAY,
            leftWith,
        )
        // Вход со «Сегодня» проверен шагом открытия: клик по строке прогресса дня
        // действительно сменил экран на историю.
    }

    /** Тот же маршрут, что у продукта: настоящий ViewModel на мире сценария. */
    private fun historyViewModel(): HistoryViewModel = HistoryViewModel(
        observeAdherence = ObserveAdherenceUseCase(
            activeProfile = world.profileSession.activeStore,
            history = world.history,
            calculator = AdherenceCalculator(),
            time = world.clock.time,
            log = world.log,
        ),
        buildReport = BuildDoctorReportUseCase(
            activeProfile = world.profileSession.activeStore,
            profiles = world.repositories.profileStore,
            history = world.history,
            calculator = AdherenceCalculator(),
            time = world.clock.time,
            log = world.log,
        ),
        transferReport = TransferDoctorReportUseCase(
            transfer = SystemDoctorReportTransfer(context),
            activeProfile = world.profileSession.activeStore,
            log = world.log,
        ),
        renderer = TextDoctorReportRenderer(context),
        missedPattern = MissedPatternRule(),
        time = world.clock.time,
    )

    /** Настоящий `TodayViewModel` над миром сценария — тем же графом, что в продукте. */
    private fun todayViewModel(): TodayViewModel {
        val time = world.clock.time
        val markIntake = MarkIntakeUseCase(
            world.repositories.intakeStore,
            world.repositories.medicationStore,
            world.repositories.profileStore,
            time,
            world.reminders.markSideEffects(),
        )
        return TodayViewModel(
            observeAgenda = ObserveTodayAgendaUseCase(
                activeProfile = world.profileSession.activeStore,
                intakes = world.repositories.intakeStore,
                medications = world.repositories.medicationStore,
                sweepMissed = SweepMissedIntakesUseCase(
                    world.repositories.intakeStore,
                    world.intakes.window,
                    time,
                    world.log,
                ),
                stockWarnings = StockWarningForProfileUseCase(world.repositories.medicationStore, time, world.log),
                time = time,
                build = BuildTodayAgenda(),
            ),
            markIntake = markIntake,
            markGroup = MarkIntakeGroupUseCase(
                markIntake,
                ScenarioTransactor(world.intakes, world.medications),
                time,
                world.log,
            ),
            createPrn = CreatePrnIntakeUseCase(
                world.repositories.intakeStore,
                world.repositories.medicationStore,
                world.repositories.profileStore,
                time,
            ),
            undoMark = UndoMarkUseCase(
                world.repositories.intakeStore,
                world.repositories.medicationStore,
                world.repositories.profileStore,
            ),
            undoMarks = world.undoMarks,
            time = time,
            readReliability = ReadReminderReliabilityUseCase(world.system),
        )
    }

    /** Язык интерфейса закреплён: шаги зовут строки по-русски при любом телефоне (НФТ-43). */
    @androidx.compose.runtime.Composable
    private fun Pinned(content: @androidx.compose.runtime.Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
            // Системный выбор места живёт в `HistoryRoute` и требует владельца реестра
            // результатов: в стенде его даёт заглушка платформенного порта, потому что
            // сценарий говорит о возврате, а не о передаче отчёта.
            LocalActivityResultRegistryOwner provides NoResultRegistryOwner,
        ) {
            SklyankaTheme { content() }
        }
    }

    /**
     * Владелец реестра результатов без результата.
     *
     * Порт платформы, а не правило продукта: `rememberLauncherForActivityResult`
     * регистрируется у него при сборке маршрута, а сам выбор места сценарий не открывает.
     */
    private object NoResultRegistryOwner : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }
}
