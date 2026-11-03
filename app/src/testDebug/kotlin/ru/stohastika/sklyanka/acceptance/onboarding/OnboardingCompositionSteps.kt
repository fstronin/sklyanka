package ru.stohastika.sklyanka.acceptance.onboarding

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.settings.SettingsAppProbe
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.onboarding.TAG_ALL_CONFIGURED
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_APP_STARTUP
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_BATTERY_SAVING
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_EXACT_ALARMS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_NOTIFICATIONS
import ru.stohastika.sklyanka.feature.onboarding.TAG_CARD_RECENTS_PIN
import ru.stohastika.sklyanka.feature.onboarding.TAG_NEXT
import ru.stohastika.sklyanka.feature.onboarding.TAG_OUTAGE_WARNING
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.reminder.TAG_WARNING
import ru.stohastika.sklyanka.feature.today.TAG_RELIABILITY_BANNER
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.startup.SplashRoute
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartReason
import ru.stohastika.sklyanka.startup.StartupConditions
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.ui.component.TAG_ONBOARDING_STEPS
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

    @И("^пять карточек в порядке: «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)»$")
    @Suppress("LongParameterList")
    fun fiveCardsInOrder(first: String, second: String, third: String, fourth: String, fifth: String) {
        assertTrue(permissionsShowsTag(TAG_CARD_NOTIFICATIONS), "the notifications card must be on the screen")
        assertTrue(permissionsShowsTag(TAG_CARD_EXACT_ALARMS), "the exact alarms card must be on the screen")
        assertTrue(permissionsShowsTag(TAG_CARD_APP_STARTUP), "the app startup card must be on the screen")
        assertTrue(permissionsShowsTag(TAG_CARD_BATTERY_SAVING), "the battery saving card must be on the screen")
        assertTrue(permissionsShowsTag(TAG_CARD_RECENTS_PIN), "the recents pin card must be on the screen")
        val texts = permissionsTexts()
        val indices = listOf(first, second, third, fourth, fifth).map { title ->
            requireNotNull(
                texts.indexOfFirst {
                    it == title
                }.takeIf { it >= 0 },
            ) { "«$title» is not on the screen: $texts" }
        }
        assertEquals(indices.sorted(), indices, "the cards must follow the fixed order of ОВ-106")
    }

    /**
     * Кнопки нижнего блока — дословно своими словами.
     *
     * Выражение называет их именно так, как их называет экран разрешений: шаг проверяет
     * этот экран и не вправе отвечать за кнопки другого экрана (SKL-165 добавил второй
     * нижний блок — на экране согласия, и у него свой шаг).
     */
    @И("^внизу кнопки «Дальше» и «Пропустить, настрою позже»$")
    fun theFooterButtons() {
        assertTrue(permissionsShowsTag(TAG_NEXT), "the «Дальше» button must be on the screen")
        assertTrue(permissionsShowsTag(TAG_SKIP), "the «Пропустить, настрою позже» button must be on the screen")
        assertTrue(permissionsShows("Дальше"), "the «Дальше» button must say exactly that")
        assertTrue(
            permissionsShows("Пропустить, настрою позже"),
            "the «Пропустить, настрою позже» button must say exactly that",
        )
    }

    // --- Пропуск онбординга --------------------------------------------------------------------

    @Когда("^человек нажимает «Пропустить, настрою позже»$")
    fun thePersonSkipsTheOnboarding() {
        OnboardingAppProbe.open(world, OnboardingSource.FIRST_LAUNCH)
        // Пропуск — в нижнем блоке, вне прокрутки: прокручивать до него нечего.
        OnboardingAppProbe.actPermissions(world) { onNodeWithTag(TAG_SKIP).performClick() }
        assertEquals(Unit, OnboardingAppProbe.exit(world), "skip leads to Today, not further (НАП-21)")
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

    // --- SKL-84: баннеры ведут одним шагом к нужному месту (ОВ-107) -----------------------------

    @Тогда("^на экране «Настроить надёжность» виден индикатор шагов онбординга$")
    fun theStepIndicatorIsVisibleOnTheSetup() {
        OnboardingAppProbe.askPermissions(world) {
            assertTrue(
                tagsIn(onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_ONBOARDING_STEPS),
                "the first launch keeps the onboarding step indicator",
            )
        }
    }

    @Допустим("^человек находится на экране «Настроить надёжность» (.+?) в (\\d{2}:\\d{2})$")
    fun heIsOnTheSetupScreen(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        OnboardingAppProbe.open(world, OnboardingSource.SETTINGS)
    }

    @Когда("^1 октября 2026 в 11:08 человек видит сообщение о перерыве и нажимает «Исправить»$")
    fun heSeesTheOutageAndPressesFix() {
        world.clock.moveTo(world.localMoment("1 октября 2026", "11:08"))
        OnboardingAppProbe.pressFixInTheOutageNotice(world)
    }

    @Когда("^1 октября 2026 в 11:08 человек нажимает «Исправить» в сообщении о перерыве$")
    fun hePressesFixInTheOutageNotice() {
        heSeesTheOutageAndPressesFix()
    }

    @Тогда("^открывается экран «Настроить надёжность»$")
    fun theSetupScreenOpens() {
        OnboardingAppProbe.askPermissions(world) {
            assertTrue(
                tagsIn(onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_PERMISSIONS_SCREEN),
                "the setup screen must be opened",
            )
        }
    }

    @И("^экран показывает шаг «([^»]+)» — первый незакрытый в цепочке$")
    fun theSetupFocusesTheFirstUnresolved(title: String) {
        val expected = when (title) {
            "Уведомления" -> PermissionStep.NOTIFICATIONS
            "Точные будильники" -> PermissionStep.EXACT_ALARMS
            "Запуск приложений" -> PermissionStep.APP_STARTUP
            "Энергосбережение" -> PermissionStep.BATTERY_SAVING
            else -> PermissionStep.RECENTS_PIN
        }
        val state = OnboardingAppProbe.state(world)
        assertEquals(expected, state.focusStep, "the focus must be computed after reading the system")
        assertEquals(expected, state.checklist?.firstUnresolved, "the focus must be the first unresolved step")
    }

    @И("^никакого промежуточного экрана между сообщением и настройкой не появляется$")
    fun noIntermediateScreenAppears() {
        // Нажатие открыло экран настройки сразу: состояние после него — прочитанный чеклист,
        // а не снимок диагностики; промежуточных переходов в маршруте нет (ОВ-107).
        assertNotNull(OnboardingAppProbe.state(world).checklist, "the setup screen must have read the system")
    }

    @Тогда("^на экране «Настроить надёжность» стоит плашка предупреждения$")
    fun theWarningPlaqueIsShown() {
        OnboardingAppProbe.askPermissions(world) {
            assertTrue(
                tagsIn(onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_OUTAGE_WARNING),
                "the outage entry must show the warning plaque",
            )
        }
    }

    @И("^эта плашка называет перерыв — напоминания не приходили с 1 октября 2026, 07:30$")
    fun thePlaqueNamesTheOutageMoment() {
        assertNotNull(world.outageSince, "the scenario must name the outage moment")
        OnboardingAppProbe.askPermissions(world) {
            assertTrue(
                texts().any { it.contains("напоминания не приходили с 1 октября, 07:30") },
                "the plaque must name the moment of the outage: ${texts()}",
            )
        }
    }

    @Но("^надписи «Всё настроено» без предупреждения на экране нет$")
    fun thePlainConfiguredPlaqueIsGone() {
        OnboardingAppProbe.askPermissions(world) {
            assertFalse(
                tagsIn(onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_ALL_CONFIGURED),
                "the warning plaque replaces the neutral one",
            )
        }
    }

    @Когда("^1 октября 2026 в 11:08 человек нажимает «Настроить» в предупреждении на «Сегодня»$")
    fun hePressesConfigureOnToday() {
        world.clock.moveTo(world.localMoment("1 октября 2026", "11:08"))
        // Тот же экран с источником TODAY_WARNING, что открывает `MainActivity`.
        OnboardingAppProbe.open(world, OnboardingSource.TODAY_WARNING)
    }

    @Когда("^он выдаёт разрешение в системном экране и возвращается в приложение$")
    fun heGrantsThePermissionAndReturns() {
        world.system.grant(ReminderPermission.NOTIFICATIONS)
        // Состояние перечитывается у системы по возвращении, без перезапуска (НФТ-52).
        OnboardingAppProbe.model(world).refresh()
    }

    @Тогда("^карточка «Уведомления» отмечена «Есть» без перезапуска приложения$")
    fun theNotificationsCardIsGrantedWithoutRestart() {
        OnboardingAppProbe.askPermissions(world) {
            onNodeWithTag(TAG_CARD_NOTIFICATIONS, useUnmergedTree = true).assertIsDisplayed()
            assertTrue(
                texts().contains(string(OnboardingR.string.onboarding_state_granted)),
                "the card must be marked «Есть»: ${texts()}",
            )
        }
    }

    @И("^баннер ненадёжности на «Сегодня» гаснет при следующем входе на экран$")
    fun theTodayReliabilityBannerIsGone() {
        OnboardingAppProbe.askToday(world) { test, _ ->
            assertFalse(
                tagsIn(test.onRoot(useUnmergedTree = true).fetchSemanticsNode()).contains(TAG_RELIABILITY_BANNER),
                "nothing is missing any more, so there is no banner",
            )
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
