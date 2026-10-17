package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioProfileManagement
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearanceOutcome
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
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.pro.ComplexScheduleGate
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.profile.CreateProfileOutcome
import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision
import ru.stohastika.sklyanka.domain.profile.ProfileCreationGate
import ru.stohastika.sklyanka.domain.widget.WidgetSetupGate
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Шаги приёмки SKL-153 — бесплатный продукт: доступ есть, платных признаков нет (Р-86).
 *
 * Правила не пересказываются: сложный курс закрывает настоящий `ComplexScheduleGate`,
 * установку виджета — `WidgetSetupGate`, профили — `ProfileCreationGate`, оформление —
 * `AppearanceGate` через настоящие сценарии домена, отчёт и CSV собирают настоящие
 * `BuildExtendedSectionsUseCase` и `BuildHistoryCsvTableUseCase`. Мир даёт только доступ
 * бесплатной сборки ([ru.stohastika.sklyanka.acceptance.world.ScenarioAccess]): кэш отвечает
 * `Active(productId = null, until = LocalDate.MAX)`, поэтому гейты проходят без изменения
 * доменных правил (`docs/tasks/SKL-153/TDD.md` §3).
 *
 * **Чего эти шаги не проверяют.** Вёрстку и кадры настроек, формы, отчёта и виджета держат
 * экранные прогоны задач SKL-120 и SKL-121; здесь проверяется механизм, которым платные
 * признаки скрыты в `free`: `LocalProUiEnabled` по умолчанию `false`, `ProMark` при нём
 * не рисует ничего, блок «Склянка Про» добавляется только под флагом, а `app/src/main`
 * и `app/src/free` не знают `feature-pro`. Это страж сборки, а не пересказ её вида.
 */
@Suppress("TooManyFunctions")
class Sk153FreeSteps(private val world: AcceptanceWorld) {

    private var complexSaved = false

    private var installedWidgets = 0

    private var sections: ExtendedReportSections = ExtendedReportSections.NONE

    private var csvRows = 0

    private var appliedThemes: Int = 0

    private var appliedIcons: Int = 0

    private var markSource = ""

    private var flagSource = ""

    private var settingsSource = ""

    private var appMainSources: List<Source> = emptyList()

    private var appFreeSources: List<Source> = emptyList()

    private var featureSurfaceSources: List<Source> = emptyList()

    // --- Шов доступа -------------------------------------------------------------------------

    @Тогда("^доступ «Про» в бесплатной сборке активен бессрочно$")
    fun theFreeAccessIsAlwaysActive() {
        val status = world.access.status()
        assertTrue(status.included, "the free build must give access without any purchase")
        val active = status.subscription as? ProSubscription.Active
        assertNotNull(active, "the free access must be an active subscription, not ${status.subscription}")
        assertEquals(LocalDate.MAX, active?.until, "the free access has no end date")
        assertNull(active?.productId, "the free access must not come from a store product")
    }

    // --- Сложное расписание ------------------------------------------------------------------

    @Когда("^человек заводит бесплатно курс «([^»]+)» циклами «(\\d+)/(\\d+)» в (\\d{2}):(\\d{2})$")
    fun thePersonStartsAComplexCourseForFree(
        name: String,
        onDays: String,
        offDays: String,
        hour: String,
        minute: String,
    ) {
        val rule = ScheduleRule.Cycles(onDays = onDays.toInt(), offDays = offDays.toInt())
        val included = world.access.status().included
        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(original = null, next = rule, included = included),
            "the free build must not block the creation of a complex schedule",
        )
        val draft = MedicationDraft(
            id = null,
            profileId = world.profiles.get(world.profiles.active).id,
            name = name,
            doseAmount = BigDecimal.ONE,
            times = listOf(LocalTime.of(hour.toInt(), minute.toInt())),
            schedule = rule,
            startDate = world.clock.today(),
        )
        val result = world.save(draft)
        assertTrue(result is SaveResult.Saved, "the complex course must save for free, got $result")
        complexSaved = true
    }

    @Тогда("^курс «([^»]+)» сохранён без оплаты$")
    fun theComplexCourseIsSavedForFree(name: String) {
        assertTrue(complexSaved, "the scenario never saved the course")
        assertTrue(world.medications.has(name), "the saved course is missing from the world")
        assertTrue(
            world.intakes.of(name).isNotEmpty(),
            "the saved course must produce intakes without payment",
        )
    }

    // --- Виджеты ------------------------------------------------------------------------------

    @Когда("^человек добавляет виджет «([^»]+)» без оплаты$")
    fun thePersonInstallsAWidgetForFree(type: String) {
        val needsMedication = type == MEDICATION_WIDGET
        assertTrue(
            WidgetSetupGate.canComplete(
                proIncluded = world.access.status().included,
                medicationRequired = needsMedication,
                medicationChosen = needsMedication,
            ),
            "the widget «$type» must install for free",
        )
        installedWidgets++
    }

    @Тогда("^все три виджета установлены без оплаты$")
    fun allThreeWidgetsAreInstalled() {
        assertEquals(TYPES_OF_WIDGETS, installedWidgets, "every widget type must install for free")
    }

    // --- Отчёт и выгрузка ---------------------------------------------------------------------

    @Когда("^человек собирает без оплаты расширенный отчёт и выгрузку истории за август—сентябрь 2026$")
    fun thePersonBuildsTheExtendedReportAndCsvForFree() {
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

    @Тогда("^расширенный состав и выгрузка собраны без оплаты$")
    fun theExtendedReportAndCsvAreBuilt() {
        assertTrue(world.access.status().included, "the report and export read the same free access")
        assertFalse(sections.isEmpty, "the extended composition must have at least one section")
        assertTrue(csvRows > 0, "the history export must contain the taken intakes")
    }

    // --- Оформление ---------------------------------------------------------------------------

    @Когда("^человек выбирает без оплаты каждый акцент и обе иконки$")
    fun thePersonChoosesEveryAppearanceForFree() {
        val preferences = InMemoryAppearance()
        val themes = SetAccentThemeUseCase(preferences, world.access.statusUseCase)
        val icons = SetIconVariantUseCase(preferences, world.access.statusUseCase)
        runBlocking {
            AccentTheme.entries.forEach { theme ->
                assertEquals(AppearanceOutcome.APPLIED, themes(theme), "the accent «${theme.key}» must apply for free")
                assertEquals(theme, preferences.accentTheme.first(), "the chosen accent must be stored")
                appliedThemes++
            }
            IconVariant.entries.forEach { variant ->
                assertEquals(AppearanceOutcome.APPLIED, icons(variant), "the icon «${variant.key}» must apply for free")
                assertEquals(variant, preferences.iconVariant.first(), "the chosen icon must be stored")
                appliedIcons++
            }
        }
    }

    @Тогда("^все четыре акцента и обе иконки применились без отказа$")
    fun everyAppearanceOptionApplied() {
        assertEquals(AccentTheme.entries.size, appliedThemes, "every accent must be selectable")
        assertEquals(IconVariant.entries.size, appliedIcons, "both icon variants must be selectable")
    }

    // --- Профили ------------------------------------------------------------------------------

    @Когда("^человек заводит без оплаты профили до пятого$")
    fun thePersonCreatesProfilesUpToFiveForFree() {
        val palette = ScenarioProfileManagement.PALETTE.toList()
        while (world.profiles.all().size < ProfileCreationGate.LIMIT) {
            val created = world.profiles.all().size
            assertEquals(
                ProfileCreationDecision.Allowed,
                ProfileCreationGate.decide(created, includesPro = world.access.status().included),
                "profile ${created + 1} must be allowed for free",
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
            ProfileCreationGate.decide(world.profiles.all().size, includesPro = world.access.status().included),
            "the sixth profile is closed by the product limit, not by payment",
        )
    }

    // --- Признаки платности -------------------------------------------------------------------

    @Когда("^проверяют исходники бесплатных поверхностей$")
    fun theFreeSurfaceSourcesAreRead() {
        markSource = textOf(MARK_SOURCE)
        flagSource = textOf(FLAG_SOURCE)
        settingsSource = textOf(SETTINGS_SOURCE)
        appMainSources = sourcesUnder(APP_MAIN)
        appFreeSources = sourcesUnder(APP_FREE)
        featureSurfaceSources = SURFACES.flatMap(::sourcesUnder)
    }

    @Тогда("^метка «Про» не рисуется без флага платного контура$")
    fun theMarkIsHiddenWithoutTheFlag() {
        assertTrue(
            FLAG_DEFAULT.containsMatchIn(flagSource),
            "`LocalProUiEnabled` must default to false: $FLAG_SOURCE",
        )
        assertTrue(
            markSource.contains("LocalProUiEnabled.current"),
            "`ProMark` must consult the flag: $MARK_SOURCE",
        )
        assertTrue(
            MARK_EARLY_RETURN.containsMatchIn(markSource),
            "`ProMark` must render nothing when the flag is false: $MARK_SOURCE",
        )
    }

    @И("^строка «Склянка Про» добавляется только под флагом$")
    fun theProRowIsGuardedByTheFlag() {
        assertTrue(
            settingsSource.contains("LocalProUiEnabled.current"),
            "the settings screen must read the flag: $SETTINGS_SOURCE",
        )
        assertTrue(
            PRO_BLOCK_UNDER_FLAG.containsMatchIn(settingsSource),
            "the «Склянка Про» block must be added only inside the flag branch: $SETTINGS_SOURCE",
        )
    }

    @И("^в бесплатном приложении нет входа в покупку$")
    fun theFreeApplicationHasNoPurchaseEntry() {
        assertTrue(appFreeSources.isNotEmpty(), "the free build sources are missing: $APP_FREE")
        assertFalse(
            appMainSources.any { source -> source.name.startsWith("ProShowcase") },
            "a showcase entry is left in `app/src/main`: ${appMainSources.map { it.path }}",
        )
        (appMainSources + appFreeSources).forEach { source ->
            val purchase = PURCHASE_ENTRY.firstOrNull { it in source.text }
            assertNull(purchase, "the free application still knows «$purchase»: ${source.path}")
        }
        assertTrue(
            appFreeSources.any { source -> DISABLED_HOST.containsMatchIn(source.text) },
            "the free `ProHost` must be a no-op (`enabled = false`): $APP_FREE",
        )
    }

    @И("^на форме, в отчёте и в виджете нет ни слов покупки, ни строки «Склянка Про»$")
    fun theLeavingSurfacesCarryNoPurchaseWords() {
        featureSurfaceSources.forEach { source ->
            val marker = SURFACE_PURCHASE_WORDS.firstOrNull { it in source.text }
            assertNull(marker, "the surface still names «$marker»: ${source.path}")
        }
    }

    // --- Помощники ----------------------------------------------------------------------------

    private fun textOf(relative: String): String {
        val file = File(RepositoryLayout.root, relative)
        require(file.isFile) { "the acceptance guard expects a source file: ${file.path}" }
        return file.readText()
    }

    private fun sourcesUnder(relative: String): List<Source> {
        val root = File(RepositoryLayout.root, relative)
        require(root.isDirectory) { "the acceptance guard expects a source directory: ${root.path}" }
        return root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .map { file ->
                Source(
                    path = file.relativeTo(RepositoryLayout.root).path,
                    name = file.name,
                    text = file.readText(),
                )
            }
            .sortedBy { source -> source.path }
            .toList()
    }

    /** Файл исходников бесплатной поверхности: путь для диагноза и текст для стража. */
    private data class Source(val path: String, val name: String, val text: String)

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

        const val MARK_SOURCE = "core-ui/src/main/kotlin/ru/stohastika/sklyanka/ui/component/ProMark.kt"

        const val FLAG_SOURCE = "core-ui/src/main/kotlin/ru/stohastika/sklyanka/ui/pro/LocalProUiEnabled.kt"

        const val SETTINGS_SOURCE =
            "feature-settings/src/main/kotlin/ru/stohastika/sklyanka/feature/settings/SettingsScreen.kt"

        const val APP_MAIN = "app/src/main/kotlin"

        const val APP_FREE = "app/src/free/kotlin"

        val SURFACES = listOf(
            "feature-meds/src/main/kotlin",
            "feature-history/src/main/kotlin",
            "feature-widgets/src/main/kotlin",
        )

        /** Флаг скрывает метку и строку, пока платный вариант сборки не включит его (Р-86). */
        val FLAG_DEFAULT = Regex("""staticCompositionLocalOf\s*\{\s*false\s*}""")

        /** Метка не рисует ничего без флага: пустой ветки в composable достаточно. */
        val MARK_EARLY_RETURN = Regex("""if\s*\(\s*!LocalProUiEnabled\.current\s*\)\s*return""")

        /** Блок «Склянка Про» добавляется только внутри `if (proUiEnabled)`. */
        val PRO_BLOCK_UNDER_FLAG = Regex("""if\s*\(\s*proUiEnabled\s*\)\s*\{\s*proBlock\(""")

        /** Вход в покупку, которого в бесплатной сборке быть не может. */
        val PURCHASE_ENTRY = listOf(
            "import ru.stohastika.sklyanka.feature.pro",
            "ProShowcaseRoute",
            "ProShowcaseViewModel",
            "ProShowcaseIntent",
            "ProSheetSession",
        )

        /** Признак no-op хоста: бесплатная сборка не открывает витрину. */
        val DISABLED_HOST = Regex("""enabled[^=\n]*=\s*false""")

        /** Слова покупки и строки «Склянка Про», которых не несут форма, отчёт и виджет. */
        val SURFACE_PURCHASE_WORDS = listOf(
            "pro_sheet_title",
            "pro_action_buy",
            "pro_action_restore",
            "pro_action_manage",
            "pro_row_title",
            "pro_period_month",
            "pro_period_year",
            "ProSheetSession",
        )
    }
}
