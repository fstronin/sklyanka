package ru.stohastika.sklyanka.acceptance.onboarding

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.settings.SettingsAppProbe
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.onboarding.OnboardingExit
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_AUTOSTART
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_NEXT
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.reminder.TAG_WARNING
import ru.stohastika.sklyanka.feature.today.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.startup.SplashRoute
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartReason
import ru.stohastika.sklyanka.startup.StartupConditions
import ru.stohastika.sklyanka.feature.onboarding.R as OnboardingR
import ru.stohastika.sklyanka.feature.reminder.R as ReminderR
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * Экранные шаги приёмки онбординга в композиции `app` (SKL-14, группа `@composition`).
 *
 * Шаги живут в `app`, потому что говорят о первом запуске (решение заставки), пропуске
 * (уход на «Сегодня») и о нескольких экранах сразу: экран разрешений, «Сегодня», настройки
 * и диагностика (`SKL-26/TDD.md §4.3`). Состояния экранов собираются из мира настоящими
 * портами; правило первого запуска — тот же `SplashRoute`, что в `MainActivity`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class OnboardingCompositionSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Первый запуск -------------------------------------------------------------------------

    @Когда("^человек впервые открывает приложение (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensTheAppForTheFirstTime(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        // Решение заставки то же, что у `MainActivity`: первый запуск ведёт на разрешения,
        // и причина записана в журнал запуска (НАП-19).
        val conditions = StartupConditions(
            firstLaunch = true,
            notificationsAllowed = world.system.state(ReminderPermission.NOTIFICATIONS) == PermissionState.GRANTED,
            exactAlarmsAllowed = world.system.state(ReminderPermission.EXACT_ALARMS) == PermissionState.GRANTED,
        )
        assertEquals(StartReason.FirstLaunch, SplashRoute.reasonOf(conditions), "a first launch explains itself")
        assertEquals(StartDestination.Onboarding, SplashRoute.destinationOf(conditions))
        OnboardingAppProbe.open(world, OnboardingSource.FIRST_LAUNCH)
    }

    @Тогда("^он видит экран с заголовком «([^»]+)»$")
    fun heSeesTheTitle(title: String) {
        assertTrue(permissionsShows(title), "the title «$title» must be on the permissions screen")
    }

    @И("^пояснение «([^»]+)»$")
    fun theIntroIsShown(intro: String) {
        assertTrue(permissionsShows(intro), "the intro must be shown word for word (критерий 21)")
    }

    @И("^три карточки в порядке: «([^»]+)», «([^»]+)», «([^»]+)»$")
    fun threeCardsInOrder(first: String, second: String, third: String) {
        assertTrue(permissionsShowsTag(TAG_CARD_NOTIFICATIONS), "the notifications card must be on the screen")
        assertTrue(permissionsShowsTag(TAG_CARD_EXACT_ALARMS), "the exact alarms card must be on the screen")
        assertTrue(permissionsShowsTag(TAG_CARD_AUTOSTART), "the autostart card must be on the screen")
        val texts = permissionsTexts()
        val indices = listOf(first, second, third).map { title ->
            requireNotNull(
                texts.indexOfFirst {
                    it == title
                }.takeIf { it >= 0 },
            ) { "«$title» is not on the screen: $texts" }
        }
        assertEquals(indices.sorted(), indices, "the cards must follow the fixed order of НАП-19")
    }

    @И("^внизу кнопки «([^»]+)» и «([^»]+)»$")
    fun theFooterButtons(final: String, skip: String) {
        assertTrue(permissionsShowsTag(TAG_NEXT), "the «$final» button must be on the screen")
        assertTrue(permissionsShowsTag(TAG_SKIP), "the «$skip» button must be on the screen")
        assertTrue(permissionsShows(final), "the «$final» button must say exactly that")
        assertTrue(permissionsShows(skip), "the «$skip» button must say exactly that")
    }

    // --- Пропуск онбординга --------------------------------------------------------------------

    @Когда("^человек нажимает «Пропустить, настрою позже»$")
    fun thePersonSkipsTheOnboarding() {
        OnboardingAppProbe.open(world, OnboardingSource.FIRST_LAUNCH)
        // Пропуск — в нижнем блоке, вне прокрутки: прокручивать до него нечего.
        OnboardingAppProbe.actPermissions(world) { onNodeWithTag(TAG_SKIP).performClick() }
        assertEquals(OnboardingExit.TODAY, OnboardingAppProbe.exit(world), "skip leads to Today, not further (НАП-21)")
        assertTrue(runBlocking { world.onboarding.seen() }, "the skip must be remembered (НАП-21)")
    }

    @Тогда("^открывается экран «Сегодня»$")
    fun theTodayScreenOpens() {
        OnboardingAppProbe.askToday(world) { test, state ->
            assertTrue(
                tagsIn(test.onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_TODAY_SCREEN),
                "the Today screen must be opened, state: $state",
            )
        }
    }

    @И("^приложением можно пользоваться$")
    fun theAppIsUsable() {
        // «Пользоваться» — значит экран собран целиком: содержимое и нижняя панель на месте.
        var panel = false
        OnboardingAppProbe.askToday(world) { test, _ ->
            val tags = tagsIn(test.onRoot(useUnmergedTree = true).fetchSemanticsNode())
            assertTrue(TAG_TODAY_SCREEN in tags, "the Today screen must be assembled")
            panel = TAG_BOTTOM_BAR in tags
        }
        assertTrue(panel, "the bottom panel must be there — the app is usable, not just open")
    }

    // --- Правда о состоянии напоминаний --------------------------------------------------------

    @Тогда("^строка «Разрешения и автозапуск» показывает, что не всё выдано$")
    fun thePermissionsRowTellsTheTruth() {
        val state = SettingsAppProbe.settingsState(world)
        assertTrue(state.permissions.denied > 0, "the revoked permission must be counted as missing")
        var denied = false
        SettingsAppProbe.askSettings(world) {
            denied =
                texts().contains(string(SettingsR.string.settings_permissions_denied_one))
        }
        assertTrue(denied, "the row must say that something is missing (НФТ-8)")
    }

    @И("^ни один экран приложения не сообщает, что напоминания настроены правильно$")
    fun noScreenClaimsRemindersAreConfigured() {
        // Экраны, которые вообще говорят о состоянии напоминаний: «Сегодня», разрешения,
        // настройки и диагностика. Остальные о разрешениях не высказываются вовсе.
        val allTexts = mutableListOf<String>()
        OnboardingAppProbe.askToday(world) { test, _ -> allTexts += test.texts() }
        OnboardingAppProbe.askPermissions(world) { allTexts += texts() }
        SettingsAppProbe.askSettings(world) { allTexts += texts() }
        OnboardingAppProbe.askDiagnostics(world) { allTexts += texts() }
        val claims = listOf(
            string(SettingsR.string.settings_permissions_granted),
            string(SettingsR.string.settings_permissions_granted_manual),
            string(OnboardingR.string.onboarding_summary_all_resolved),
        )
        val offenders = allTexts.filter { text -> claims.any { claim -> text.contains(claim) } }
        assertEquals(emptyList<String>(), offenders, "no screen may claim the reminders are configured (критерий 17)")
        OnboardingAppProbe.askDiagnostics(world) {
            val warning = string(ReminderR.string.diagnostics_warning_exact_alarms)
            assertTrue(texts().contains(warning), "the diagnostics must say exactly what is wrong (НАП-18)")
            assertTrue(tagsIn(onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_WARNING))
        }
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun permissionsShows(text: String): Boolean = permissionsTexts().contains(text)

    private fun permissionsShowsTag(tag: String): Boolean {
        var found = false
        OnboardingAppProbe.askPermissions(world) {
            found = tagsIn(onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(tag)
        }
        return found
    }

    private fun permissionsTexts(): List<String> {
        var texts: List<String> = emptyList()
        OnboardingAppProbe.askPermissions(world) { texts = texts() }
        return texts
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun androidx.compose.ui.test.ComposeUiTest.texts(): List<String> =
        textsOf(onRoot(useUnmergedTree = true).fetchSemanticsNode())

    private fun tagsIn(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.TestTag).orEmpty().let {
            if (it.isEmpty()) emptyList() else listOf(it)
        } +
            node.children.flatMap(::tagsIn)

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::textsOf)
}
