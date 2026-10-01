package ru.stohastika.sklyanka.feature.onboarding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.ui.component.TAG_ONBOARDING_STEPS
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDateTime

/**
 * Состав экрана «Настроить надёжность» (НАП-19 … НАП-21, ОВ-106, ОВ-107).
 *
 * Идёт на JVM под Robolectric в обычном `./gradlew test` (SKL-24): настоящая композиция,
 * настоящие ресурсы модуля, настоящая семантика. Чего стенд НЕ доказывает: что системный
 * экран EMUI действительно откроется и что напоминание действительно дойдёт, — это свойства
 * прошивки, и проверяются они на эталонном устройстве (`TDD.md §12.1`).
 *
 * Тексты сверяются с ресурсами, а ресурсы — со спецификацией: подпись, набранная в тесте
 * руками, проверяла бы тест, а не экран.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: цепочка из пяти карточек и футер. В окне телефона он
// скроллится, и проверка состава видела бы только его верх (SKL-24).
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class PermissionsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(state: OnboardingUiState, actions: PermissionsActions = PermissionsScreenFixtures.actions()) {
        compose.setContent {
            SklyankaTheme {
                PermissionsScreen(
                    state = state,
                    actions = actions,
                    toLocal = { LocalDateTime.ofInstant(it, java.time.ZoneId.of("Europe/Moscow")) },
                )
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Прокрутить список к узлу: карточки живут в `LazyColumn` и вне окна не собраны. */
    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(TAG_STEPS_LIST).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun theScreenCarriesTheTitleAndTheExplanationWordForWord() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithText(text(R.string.setup_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_intro)).assertIsDisplayed()
        assertEquals("Настроить надёжность", text(R.string.setup_title))
    }

    @Test
    fun onEmuiTheFiveCardsComeInTheFixedOrder() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_CARD_NOTIFICATIONS).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CARD_EXACT_ALARMS).assertIsDisplayed()
        scrollTo(TAG_CARD_APP_STARTUP)
        compose.onNodeWithTag(TAG_CARD_APP_STARTUP).assertIsDisplayed()
        scrollTo(TAG_CARD_BATTERY_SAVING)
        compose.onNodeWithTag(TAG_CARD_BATTERY_SAVING).assertIsDisplayed()
        scrollTo(TAG_CARD_RECENTS_PIN)
        compose.onNodeWithTag(TAG_CARD_RECENTS_PIN).assertIsDisplayed()
    }

    @Test
    fun onAnyOtherPhoneTheVendorStepsAreAbsent() {
        show(PermissionsScreenFixtures.nonHuawei())

        compose.onNodeWithTag(TAG_CARD_NOTIFICATIONS).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CARD_EXACT_ALARMS).assertIsDisplayed()
        compose.onAllNodesWithTag(TAG_CARD_APP_STARTUP).assertCountEquals(0)
        compose.onAllNodesWithTag(TAG_CARD_BATTERY_SAVING).assertCountEquals(0)
        compose.onAllNodesWithTag(TAG_CARD_RECENTS_PIN).assertCountEquals(0)
        compose.onAllNodesWithTag(TAG_UNVERIFIABLE).assertCountEquals(0)
        compose.onAllNodesWithTag(TAG_CONFIRM_APP_STARTUP).assertCountEquals(0)
    }

    @Test
    fun theBottomCarriesBothWaysOutAtFirstLaunch() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_NEXT).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_action_next)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_action_skip)).assertIsDisplayed()
    }

    @Test
    fun aReEntryCarriesOnlyTheQuietDone() {
        show(PermissionsScreenFixtures.fromSettings())

        compose.onNodeWithTag(TAG_DONE).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_done)).assertIsDisplayed()
        compose.onAllNodesWithTag(TAG_NEXT).assertCountEquals(0)
        compose.onAllNodesWithTag(TAG_SKIP).assertCountEquals(0)
    }

    @Test
    fun aGrantedCardIsMarkedAndCarriesNoAction() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_GRANTED_MARK, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_state_granted), useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithTag(TAG_ACTION_NOTIFICATIONS).assertCountEquals(0)
    }

    @Test
    fun anUngrantedCardCarriesItsCaptionAndItsAction() {
        show(PermissionsScreenFixtures.asDrawn())

        scrollTo(TAG_CARD_EXACT_ALARMS)
        compose.onNodeWithText(text(R.string.setup_step_exact_alarms_caption)).assertExists()
        compose.onNodeWithTag(TAG_ACTION_EXACT_ALARMS).assertExists()
        compose.onNodeWithText(text(R.string.setup_action_allow), useUnmergedTree = true).assertExists()
    }

    @Test
    fun theNotificationsCardNamesThePermissionIssueFirst() {
        show(PermissionsScreenFixtures.nothingGranted())

        compose.onNodeWithTag(TAG_ISSUE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_issue_notifications)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ACTION_NOTIFICATIONS).assertIsDisplayed()
    }

    @Test
    fun aDisabledChannelIsNamedOnTheNotificationsCard() {
        show(PermissionsScreenFixtures.channelOff())

        compose.onNodeWithText(text(R.string.setup_issue_channel)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ACTION_NOTIFICATIONS).assertIsDisplayed()
    }

    @Test
    fun aMissingFullScreenRightIsNamedOnlyWhenEverythingElseIsInOrder() {
        show(PermissionsScreenFixtures.fullScreenOff())

        compose.onNodeWithText(text(R.string.setup_issue_full_screen)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ACTION_NOTIFICATIONS).assertIsDisplayed()
    }

    @Test
    fun theVendorCardCarriesThePathAndTheSettingsButton() {
        show(PermissionsScreenFixtures.asDrawn())

        scrollTo(TAG_CARD_APP_STARTUP)
        compose.onNodeWithText(
            text(R.string.setup_step_app_startup_caption),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_step_app_startup_action), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ACTION_APP_STARTUP).assertIsDisplayed()
    }

    @Test
    fun theVendorCardSaysOutLoudThatTheAppCannotCheckIt() {
        show(PermissionsScreenFixtures.asDrawn())

        scrollTo(TAG_CARD_APP_STARTUP)
        compose.onNodeWithText(
            text(R.string.setup_unverifiable_app_startup),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_unverifiable_battery_saving), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun theRecentsPinHasNoButtonButHasTheInstructionAndTheSwitch() {
        show(PermissionsScreenFixtures.asDrawn())

        scrollTo(TAG_CARD_RECENTS_PIN)
        compose.onAllNodesWithTag(TAG_ACTION_RECENTS_PIN, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(
            text(R.string.setup_step_recents_pin_caption),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CONFIRM_RECENTS_PIN).assertIsOff()
    }

    @Test
    fun aVendorStepIsNeverMarkedAsGrantedEvenWithEveryHintInPlace() {
        show(PermissionsScreenFixtures.hintsOnly())

        // «Есть» — ответ системы, а про настройки EMUI система не отвечает (НАП-20).
        compose.onAllNodesWithTag(TAG_GRANTED_MARK, useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithTag(TAG_CONFIRM_BATTERY_SAVING).assertIsOff()
    }

    @Test
    fun indirectHintsAreShownAndNamedIndirectOnTheBatteryStep() {
        show(PermissionsScreenFixtures.hintsOnly())

        scrollTo(TAG_CARD_BATTERY_SAVING)
        compose.onNodeWithText(text(R.string.setup_hint_battery)).assertIsDisplayed()
        assertTrue(
            "the word «косвенный» is the meaning of the line, not its decoration",
            text(R.string.setup_hint_battery).startsWith("Косвенный признак"),
        )
    }

    @Test
    fun aConfirmedVendorStepIsMarkedByThePersonAndHasNoGrantedMark() {
        show(PermissionsScreenFixtures.chainComplete())

        scrollTo(TAG_CARD_APP_STARTUP)
        // Подтверждение стоит на всех трёх вендорских шагах, а «Есть» — только на двух
        // разрешениях: слово человека и ответ системы не подменяют друг друга (НАП-20).
        compose.onAllNodesWithTag(TAG_CONFIRMED_MARK, useUnmergedTree = true).assertCountEquals(3)
        compose.onAllNodesWithTag(TAG_GRANTED_MARK, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun theStepIndicatorIsThereAtFirstLaunch() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_ONBOARDING_STEPS).assertIsDisplayed()
    }

    @Test
    fun theStepIndicatorIsGoneOnReEntry() {
        show(PermissionsScreenFixtures.fromSettings())

        compose.onAllNodesWithTag(TAG_ONBOARDING_STEPS).assertCountEquals(0)
    }

    @Test
    fun onAFirmwareWithoutTheSystemScreenThePersonSeesTheWrittenInstructions() {
        show(PermissionsScreenFixtures.withInstructions())

        scrollTo(TAG_CARD_APP_STARTUP)
        compose.onNodeWithTag(TAG_INSTRUCTIONS, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_fallback_app_startup), useUnmergedTree = true).assertIsDisplayed()
        assertTrue(text(R.string.setup_fallback_app_startup).contains("Настройки → Батарея"))
    }

    @Test
    fun aRevokedPermissionIsStatedOnTheScreen() {
        show(PermissionsScreenFixtures.revoked())

        scrollTo(TAG_REVOKED)
        compose.onNodeWithTag(TAG_REVOKED, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_revoked_notifications)).assertIsDisplayed()
    }

    @Test
    fun aClosedChainShowsTheNeutralPlaqueAndNoControls() {
        show(PermissionsScreenFixtures.allConfigured())

        compose.onNodeWithTag(TAG_ALL_CONFIGURED, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_all_configured)).assertIsDisplayed()
        // В состоянии «Всё настроено» кнопок и переключателей у шагов нет (критерий 7).
        listOf(
            TAG_ACTION_NOTIFICATIONS,
            TAG_ACTION_EXACT_ALARMS,
            TAG_ACTION_APP_STARTUP,
            TAG_ACTION_BATTERY_SAVING,
            TAG_CONFIRM_APP_STARTUP,
            TAG_CONFIRM_BATTERY_SAVING,
            TAG_CONFIRM_RECENTS_PIN,
        ).forEach { tag ->
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun theOutageEntryShowsTheWarningPlaqueWithTheMoment() {
        show(PermissionsScreenFixtures.allConfiguredWithOutage())

        compose.onNodeWithTag(TAG_OUTAGE_WARNING, useUnmergedTree = true).assertIsDisplayed()
        // Момент перерыва читается в поясе человека: 05:30 UTC — это 08:30 в Москве.
        compose.onNodeWithText(text(R.string.setup_outage_warning, "1 октября, 08:30"), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun theActionOfEachUngrantedStepReachesTheModel() {
        val tapped = mutableListOf<PermissionStep>()
        show(PermissionsScreenFixtures.nothingGranted(), PermissionsScreenFixtures.actions(onStepAction = tapped::add))

        compose.onNodeWithTag(TAG_ACTION_NOTIFICATIONS).performClick()
        compose.onNodeWithTag(TAG_ACTION_EXACT_ALARMS).performClick()
        scrollTo(TAG_ACTION_APP_STARTUP)
        compose.onNodeWithTag(TAG_ACTION_APP_STARTUP).performClick()
        scrollTo(TAG_ACTION_BATTERY_SAVING)
        compose.onNodeWithTag(TAG_ACTION_BATTERY_SAVING).performClick()

        assertEquals(
            listOf(
                PermissionStep.NOTIFICATIONS,
                PermissionStep.EXACT_ALARMS,
                PermissionStep.APP_STARTUP,
                PermissionStep.BATTERY_SAVING,
            ),
            tapped,
        )
    }

    @Test
    fun bothWaysOutReachTheModelAtFirstLaunch() {
        var next = 0
        var skip = 0
        show(
            PermissionsScreenFixtures.asDrawn(),
            PermissionsScreenFixtures.actions(onNext = { next++ }, onSkip = { skip++ }),
        )

        compose.onNodeWithTag(TAG_NEXT).performClick()
        compose.onNodeWithTag(TAG_SKIP).performClick()

        assertEquals(1, next)
        assertEquals(1, skip)
    }

    @Test
    fun theQuietDoneReachesTheModelOnAReEntry() {
        var done = 0
        show(
            PermissionsScreenFixtures.fromSettings(),
            PermissionsScreenFixtures.actions(onDone = { done++ }),
        )

        compose.onNodeWithTag(TAG_DONE).performClick()

        assertEquals(1, done)
    }

    @Test
    fun theManualConfirmationReachesTheModelWithItsStep() {
        val switched = mutableListOf<Pair<PermissionStep, Boolean>>()
        show(
            PermissionsScreenFixtures.hintsOnly(),
            PermissionsScreenFixtures.actions(onManualConfirmed = { step, value -> switched += step to value }),
        )

        scrollTo(TAG_CONFIRM_BATTERY_SAVING)
        compose.onNodeWithTag(TAG_CONFIRM_BATTERY_SAVING).performClick()

        assertEquals(listOf(PermissionStep.BATTERY_SAVING to true), switched)
    }

    /** Системный индикатор будильника объясняется там, где выдаётся право (НАП-42, ОВ-110). */
    @Test
    fun theGrantedExactAlarmsStepExplainsTheStatusBarIndicator() {
        show(PermissionsScreenFixtures.chainComplete())

        compose.onNodeWithTag(TAG_CARD_EXACT_ALARMS).assertIsDisplayed()
        compose.onNodeWithTag(TAG_EXACT_ALARMS_INDICATOR).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.setup_exact_alarms_indicator)).assertIsDisplayed()
    }

    @Test
    fun theIndicatorExplanationWaitsUntilTheRightIsGranted() {
        // Макет рисует уведомления выданными, а точные будильники — нет: объяснять
        // ещё не выданный индикатор нечего.
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_EXACT_ALARMS_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun everyVisibleTextOfTheScreenComesFromTheSpecificationWordForWord() {
        // Сверяются ресурсы, а не то, что нарисовано: подпись, набранная здесь руками,
        // проверяла бы тест, а не экран, — поэтому сверка идёт против строк спецификации,
        // выписанных в этот тест один раз.
        assertEquals("Настроить надёжность", text(R.string.setup_title))
        assertEquals(
            "На прошивке EMUI система агрессивно выгружает фоновые приложения. " +
                "Без настроек ниже приёмы будут приходить с опозданием или не приходить совсем.",
            text(R.string.setup_intro),
        )
        assertEquals("Уведомления", text(R.string.setup_step_notifications))
        // Единственное отступление от спецификации дословно: «и на часах» снято решением
        // ADR-0009 — интеграция с Wear Engine отложена до пост-MVP, и обещать её нельзя.
        assertEquals("Карточка приёма на экране", text(R.string.setup_step_notifications_caption))
        assertEquals("Уведомления отключены", text(R.string.setup_issue_notifications))
        assertEquals("Канал уведомлений отключён", text(R.string.setup_issue_channel))
        assertEquals("Не разрешены полноэкранные уведомления", text(R.string.setup_issue_full_screen))
        assertEquals("Точные будильники", text(R.string.setup_step_exact_alarms))
        assertEquals(
            "Иначе Android сдвинет приём на удобное себе время — до получаса",
            text(R.string.setup_step_exact_alarms_caption),
        )
        assertEquals("Запуск приложений", text(R.string.setup_step_app_startup))
        assertEquals(
            "Настройки → Батарея → Запуск приложений → Склянка → Управлять вручную",
            text(R.string.setup_step_app_startup_caption),
        )
        assertEquals(
            "Включите «Автозапуск», «Косвенный запуск» и «Работу в фоне»",
            text(R.string.setup_step_app_startup_action),
        )
        assertEquals("Энергосбережение", text(R.string.setup_step_battery_saving))
        assertEquals("Закрепление в недавних", text(R.string.setup_step_recents_pin))
        assertEquals(
            "Откройте список недавних приложений, потяните карточку «Склянки» вниз и нажмите появившийся замок",
            text(R.string.setup_step_recents_pin_caption),
        )
        assertEquals("Я настроил запуск приложений", text(R.string.setup_manual_confirm_app_startup))
        assertEquals("Я снял ограничения энергосбережения", text(R.string.setup_manual_confirm_battery_saving))
        assertEquals("Склянка закреплена в недавних", text(R.string.setup_manual_confirm_recents_pin))
        assertEquals("Подтверждено вами", text(R.string.setup_confirmed_by_user))
        assertEquals("Всё настроено", text(R.string.setup_all_configured))
        assertEquals("Готово", text(R.string.setup_done))
        assertEquals("Дальше", text(R.string.setup_action_next))
        assertEquals("Пропустить, настрою позже", text(R.string.setup_action_skip))
        assertEquals("Разрешить", text(R.string.setup_action_allow))
        assertEquals("Открыть настройки", text(R.string.setup_action_open_settings))
    }

    @Test
    fun theSummaryLineOfTheSettingsRowReadsAsTheSettingsMockUpHasIt() {
        // Строка настроек складывает непройденные шаги через plurals (НФТ-44).
        assertEquals(
            "Одно не выдано",
            context.resources.getQuantityString(R.plurals.onboarding_summary_unresolved, 1, 1),
        )
        assertEquals(
            "2 не выдано",
            context.resources.getQuantityString(R.plurals.onboarding_summary_unresolved, 2, 2),
        )
    }
}
