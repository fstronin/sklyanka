package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioProfileManagement
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.appearance.SetAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.SetIconVariantUseCase
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildExtendedSectionsUseCase
import ru.stohastika.sklyanka.domain.history.BuildHistoryCsvTableUseCase
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.profile.CreateProfileOutcome
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.profile.ProfileCreationGate
import ru.stohastika.sklyanka.domain.widget.WidgetSetupGate
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаги приёмки SKL-153 — бесплатный продукт: прежние платные возможности открыты (Р-86).
 *
 * Правила не пересказываются: установку виджета решает настоящий `WidgetSetupGate`, профили —
 * `ProfileCreationGate`, оформление записывают настоящие `SetAccentThemeUseCase` и
 * `SetIconVariantUseCase`, отчёт и CSV собирают настоящие `BuildExtendedSectionsUseCase` и
 * `BuildHistoryCsvTableUseCase`. Платной границы в домене больше нет: покупки, подписки
 * и магазина в продукте не существует, и сценарий проверяет именно это — возможность
 * работает без всякого доступа.
 *
 * **Чего эти шаги не проверяют.** Вёрстку поверхностей держат экранные прогоны задач
 * SKL-117…121; здесь проверяется, что прежние платные возможности проходят своими
 * доменными сценариями.
 */
class Sk153FreeSteps(private val world: AcceptanceWorld) {

    private var installedWidgets = 0

    private var sections: ExtendedReportSections = ExtendedReportSections.NONE

    private var csvRows = 0

    private var appliedThemes: Int = 0

    private var appliedIcons: Int = 0

    // --- Сложное расписание ------------------------------------------------------------------

    /**
     * Курс циклами заводит общий шаг SKL-117 (`Sk117ScheduleSteps`): он пишет курс настоящим
     * `SaveMedicationUseCase`. Здесь проверяется только то, чего в SKL-117 нет, — что курс
     * с приёмами существует и никакой оплаты для этого не понадобилось.
     */
    @Тогда("^курс «([^»]+)» сохранён$")
    fun theComplexCourseIsSaved(name: String) {
        assertTrue(world.medications.has(name), "the saved course is missing from the world")
        assertTrue(world.intakes.of(name).isNotEmpty(), "the saved course must produce intakes")
    }

    // --- Виджеты ------------------------------------------------------------------------------

    @Когда("^человек добавляет виджет «([^»]+)»$")
    fun thePersonInstallsAWidget(type: String) {
        val needsMedication = type == MEDICATION_WIDGET
        assertTrue(
            WidgetSetupGate.canComplete(
                medicationRequired = needsMedication,
                medicationChosen = needsMedication,
            ),
            "the widget «$type» must install",
        )
        installedWidgets++
    }

    @Тогда("^все три виджета установлены$")
    fun allThreeWidgetsAreInstalled() {
        assertEquals(TYPES_OF_WIDGETS, installedWidgets, "every widget type must install")
    }

    // --- Отчёт и выгрузка ---------------------------------------------------------------------

    @Когда("^человек собирает расширенный отчёт и выгрузку истории за август—сентябрь 2026$")
    fun thePersonBuildsTheExtendedReportAndCsv() {
        val profileName = world.profiles.active
        world.medications.add(
            profile = world.profiles,
            profileName = profileName,
            name = MEDICATION,
            times = listOf(MORNING),
            startDate = WINDOW.from,
            endDate = WINDOW.to,
        )
        WINDOW.dates.forEach { day -> world.intakes.markTaken(MEDICATION, day.atTime(MORNING)) }
        val calculator = AdherenceCalculator(OnTimeRule.TwoHours)
        sections = runBlocking {
            BuildExtendedSectionsUseCase(
                activeProfile = world.profileSession.activeStore,
                history = world.history,
                calculator = calculator,
                missedPattern = MissedPatternRule(),
                time = world.clock.time,
            )(WINDOW)
        }
        val table = runBlocking {
            BuildHistoryCsvTableUseCase(
                activeProfile = world.profileSession.activeStore,
                profiles = world.repositories.profileStore,
                history = world.history,
                onTimeRule = OnTimeRule.TwoHours,
                time = world.clock.time,
            )(WINDOW)
        }
        csvRows = table?.rows?.size ?: 0
    }

    @Тогда("^расширенный состав и выгрузка собраны$")
    fun theExtendedReportAndCsvAreBuilt() {
        assertTrue(!sections.isEmpty, "the extended composition must have at least one section")
        assertTrue(csvRows > 0, "the history export must contain the taken intakes")
    }

    // --- Оформление ---------------------------------------------------------------------------

    @Когда("^человек выбирает каждый акцент и обе иконки$")
    fun thePersonChoosesEveryAppearance() {
        val preferences = InMemoryAppearance()
        val themes = SetAccentThemeUseCase(preferences)
        val icons = SetIconVariantUseCase(preferences)
        runBlocking {
            AccentTheme.entries.forEach { theme ->
                themes(theme)
                assertEquals(theme, preferences.accentTheme.first(), "the chosen accent must be stored")
                appliedThemes++
            }
            IconVariant.entries.forEach { variant ->
                icons(variant)
                assertEquals(variant, preferences.iconVariant.first(), "the chosen icon must be stored")
                appliedIcons++
            }
        }
    }

    @Тогда("^все четыре акцента и обе иконки применились$")
    fun everyAppearanceOptionApplied() {
        assertEquals(AccentTheme.entries.size, appliedThemes, "every accent must be selectable")
        assertEquals(IconVariant.entries.size, appliedIcons, "both icon variants must be selectable")
    }

    // --- Профили ------------------------------------------------------------------------------

    @Когда("^человек заводит профили до пятого$")
    fun thePersonCreatesProfilesUpToFive() {
        val palette = ScenarioProfileManagement.PALETTE.toList()
        while (world.profiles.all().size < ProfileCreationGate.LIMIT) {
            val created = world.profiles.all().size
            assertEquals(
                ProfileCreationDecision.Allowed,
                ProfileCreationGate.decide(created),
                "profile ${created + 1} must be allowed",
            )
            val outcome = runBlocking {
                world.profileManagement.create(
                    name = "Подопечный ${created + 1}",
                    color = palette[created % palette.size],
                    mirrorReminders = false,
                )
            }
            assertTrue(outcome is CreateProfileOutcome.Created, "profile ${created + 1}: $outcome")
        }
    }

    @Тогда("^заведено пять профилей, а шестой закрыт пределом$")
    fun fiveProfilesAndTheLimit() {
        assertEquals(ProfileCreationGate.LIMIT, world.profiles.all().size, "the guard must fill the list to its limit")
        assertEquals(
            ProfileCreationDecision.BlockedByLimit,
            ProfileCreationGate.decide(world.profiles.all().size),
            "the sixth profile is closed by the product limit, not by payment",
        )
    }

    // --- Помощники ----------------------------------------------------------------------------

    /** Настройки оформления в памяти: страж проверяет запись выбора, а не платформенное хранилище. */
    private class InMemoryAppearance : AppearancePreferences {

        private val accents = MutableStateFlow(AccentTheme.DEFAULT)

        private val icons = MutableStateFlow(IconVariant.DEFAULT)

        override val accentTheme: Flow<AccentTheme> get() = accents

        override val iconVariant: Flow<IconVariant> get() = icons

        override suspend fun setAccentTheme(theme: AccentTheme) {
            accents.value = theme
        }

        override suspend fun setIconVariant(variant: IconVariant) {
            icons.value = variant
        }
    }

    private companion object {

        const val MEDICATION = "Магний"

        const val MEDICATION_WIDGET = "По лекарству"

        const val TYPES_OF_WIDGETS = 3

        val MORNING: LocalTime = LocalTime.of(8, 0)

        val WINDOW: HistoryWindow = HistoryWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30))
    }
}
