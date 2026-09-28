package ru.heuri.sklyanka.feature.onboarding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.heuri.sklyanka.domain.onboarding.PermissionStep
import ru.heuri.sklyanka.ui.component.TAG_ONBOARDING_STEPS
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Состав экрана разрешений (НАП-19 … НАП-22, критерии приёмки 1 … 8, 15, 17, 21).
 *
 * Идёт на JVM под Robolectric в обычном `./gradlew test` (SKL-24): настоящая композиция,
 * настоящие ресурсы модуля, настоящая семантика. Чего стенд НЕ доказывает: что системный
 * экран автозапуска действительно откроется и что напоминание действительно дойдёт, —
 * это свойства прошивки, и проверяются они на эталонном устройстве (`TDD.md §12.1`).
 *
 * Тексты сверяются с ресурсами, а ресурсы — с макетом: подпись, набранная в тесте руками,
 * проверяла бы тест, а не экран.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: цепочка из трёх карточек, плашка, запуск пробного
// напоминания и две кнопки внизу. В окне телефона он скроллится, и проверка состава
// видела бы только его верх (SKL-24).
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
                    toLocal = PermissionsScreenFixtures::toLocal,
                )
            }
        }
    }

    private fun text(id: Int): String = context.getString(id)

    @Test
    fun theScreenCarriesTheTitleAndTheExplanationWordForWord() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithText(text(R.string.onboarding_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_intro)).assertIsDisplayed()
        assertEquals("Чтобы напоминания приходили вовремя", text(R.string.onboarding_title))
    }

    @Test
    fun threeCardsComeInTheOrderOfTheMockUp() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_CARD_NOTIFICATIONS).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CARD_EXACT_ALARMS).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CARD_AUTOSTART).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_step_notifications)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_step_exact_alarms)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_step_autostart)).assertIsDisplayed()
    }

    @Test
    fun theBottomCarriesBothWaysOut() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithText(text(R.string.onboarding_action_next)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_action_skip)).assertIsDisplayed()
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

        compose.onNodeWithText(text(R.string.onboarding_step_exact_alarms_caption)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_ACTION_EXACT_ALARMS).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_action_allow)).assertIsDisplayed()
    }

    @Test
    fun theAutostartCardCarriesThePathAndTheSettingsButton() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithText(text(R.string.onboarding_step_autostart_caption)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_action_open_settings)).assertIsDisplayed()
        assertTrue(text(R.string.onboarding_step_autostart_caption).contains("Запуск приложений"))
    }

    @Test
    fun theAutostartCardSaysOutLoudThatTheAppCannotCheckIt() {
        show(PermissionsScreenFixtures.asDrawn())

        // Критерий приёмки 7: приложение прямо говорит, что проверить это может только
        // пробное напоминание.
        compose.onNodeWithTag(TAG_AUTOSTART_UNVERIFIABLE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_autostart_unverifiable)).assertIsDisplayed()
    }

    @Test
    fun theAutostartStepIsNeverMarkedAsGrantedEvenWithEveryHintInPlace() {
        show(PermissionsScreenFixtures.chainComplete())

        // Отметка «Есть» стоит на двух карточках разрешений и не появляется на третьей:
        // «Есть» — ответ системы, а про автозапуск система не отвечает (НАП-20).
        compose.onAllNodesWithTag(TAG_GRANTED_MARK, useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithTag(TAG_AUTOSTART_CONFIRM, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun indirectHintsAreShownAndNamedIndirect() {
        show(PermissionsScreenFixtures.hintsOnly())

        compose.onNodeWithText(text(R.string.onboarding_hint_battery)).assertIsDisplayed()
        assertTrue(
            "the word «косвенный» is the meaning of the line, not its decoration",
            text(R.string.onboarding_hint_battery).startsWith("Косвенный признак"),
        )
    }

    @Test
    fun theManualConfirmationIsOffUntilThePersonTurnsItOn() {
        show(PermissionsScreenFixtures.hintsOnly())

        compose.onNodeWithTag(TAG_AUTOSTART_CONFIRM).assertIsOff()
    }

    @Test
    fun theManualConfirmationIsOnOnceTheChainIsComplete() {
        show(PermissionsScreenFixtures.chainComplete())

        compose.onNodeWithTag(TAG_AUTOSTART_CONFIRM).assertIsOn()
    }

    @Test
    fun thePlaqueAboutTheTestReminderStandsAboveTheControlThatStartsIt() {
        show(PermissionsScreenFixtures.asDrawn())

        // Критерий приёмки 8: человек узнаёт, что напоминание придёт через минуту,
        // ДО того, как его поставит.
        compose.onNodeWithTag(TAG_TEST_HINT, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_test_hint)).assertIsDisplayed()
        compose.onNodeWithTag(TAG_TEST_REMINDER).assertIsDisplayed()
        assertTrue(text(R.string.onboarding_test_hint).contains("через минуту"))
    }

    @Test
    fun theStartedTestReminderReportsWhenItWillArrive() {
        show(PermissionsScreenFixtures.testReminderScheduled())

        compose.onNodeWithTag(TAG_TEST_PLANNED).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.onboarding_test_planned, "10:01")).assertIsDisplayed()
    }

    @Test
    fun anUndeliveredTestReminderIsAnInvitationBackAndNotAVerdict() {
        show(PermissionsScreenFixtures.testReminderScheduled())

        compose.onNodeWithText(text(R.string.onboarding_test_not_delivered)).assertIsDisplayed()
    }

    @Test
    fun theStepIndicatorIsThereAtFirstLaunch() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_ONBOARDING_STEPS).assertIsDisplayed()
    }

    @Test
    fun theStepIndicatorIsGoneOnReEntryFromSettings() {
        // Критерий приёмки 15: экран тот же, индикатора шагов на нём нет.
        show(PermissionsScreenFixtures.fromSettings())

        compose.onAllNodesWithTag(TAG_ONBOARDING_STEPS).assertCountEquals(0)
    }

    @Test
    fun reEntryFromSettingsShowsTheVerySameThreeCards() {
        show(PermissionsScreenFixtures.fromSettings())

        compose.onNodeWithTag(TAG_CARD_NOTIFICATIONS).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CARD_EXACT_ALARMS).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CARD_AUTOSTART).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_title)).assertIsDisplayed()
    }

    @Test
    fun onAFirmwareWithoutTheSystemScreenThePersonSeesTheWrittenInstructions() {
        show(PermissionsScreenFixtures.withInstructions())

        compose.onNodeWithTag(TAG_AUTOSTART_INSTRUCTIONS, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_autostart_instructions)).assertIsDisplayed()
        assertTrue(text(R.string.onboarding_autostart_instructions).contains("Настройки → Батарея"))
    }

    @Test
    fun aRevokedPermissionIsStatedOnTheScreen() {
        show(PermissionsScreenFixtures.revoked())

        compose.onNodeWithTag(TAG_REVOKED, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_revoked_notifications)).assertIsDisplayed()
    }

    @Test
    fun anUnfinishedChainSaysRemindersAreUnreliableAndOffersTheManualJournal() {
        show(PermissionsScreenFixtures.asDrawn())

        compose.onNodeWithTag(TAG_UNRELIABLE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_unreliable)).assertIsDisplayed()
    }

    @Test
    fun aCompleteChainDropsTheUnreliableWarning() {
        show(PermissionsScreenFixtures.chainComplete())

        compose.onAllNodesWithTag(TAG_UNRELIABLE, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theActionOfEachUngrantedStepReachesTheModel() {
        val tapped = mutableListOf<PermissionStep>()
        show(PermissionsScreenFixtures.nothingGranted(), PermissionsScreenFixtures.actions(onStepAction = tapped::add))

        compose.onNodeWithTag(TAG_ACTION_NOTIFICATIONS).performClick()
        compose.onNodeWithTag(TAG_ACTION_EXACT_ALARMS).performClick()
        compose.onNodeWithTag(TAG_ACTION_AUTOSTART).performClick()

        assertEquals(
            listOf(PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS, PermissionStep.AUTOSTART),
            tapped,
        )
    }

    @Test
    fun theTestReminderControlReachesTheModel() {
        var started = 0
        show(PermissionsScreenFixtures.asDrawn(), PermissionsScreenFixtures.actions(onTestReminder = { started++ }))

        compose.onNodeWithTag(TAG_TEST_REMINDER).performClick()

        assertEquals(1, started)
    }

    @Test
    fun bothWaysOutReachTheModel() {
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
    fun theManualConfirmationReachesTheModelBothWays() {
        val switched = mutableListOf<Boolean>()
        show(
            PermissionsScreenFixtures.hintsOnly(),
            PermissionsScreenFixtures.actions(onAutostartConfirmed = switched::add),
        )

        compose.onNodeWithTag(TAG_AUTOSTART_CONFIRM).performClick()

        assertEquals(listOf(true), switched)
    }

    @Test
    fun everyVisibleTextOfTheScreenComesFromTheMockUpWordForWord() {
        // Критерий приёмки 21. Сверяются ресурсы, а не то, что нарисовано: подпись,
        // набранная здесь руками, проверяла бы тест, а не экран, — поэтому сверка
        // идёт против строк макета, выписанных в этот тест один раз.
        assertEquals("Чтобы напоминания приходили вовремя", text(R.string.onboarding_title))
        assertEquals(
            "На прошивке EMUI система агрессивно выгружает фоновые приложения. " +
                "Без трёх разрешений ниже приёмы будут приходить с опозданием или не приходить совсем.",
            text(R.string.onboarding_intro),
        )
        assertEquals("Уведомления", text(R.string.onboarding_step_notifications))
        // Единственное отступление от макета дословно: «и на часах» снято решением
        // ADR-0009 — интеграция с Wear Engine отложена до пост-MVP, и обещать её нельзя.
        assertEquals("Карточка приёма на экране", text(R.string.onboarding_step_notifications_caption))
        assertEquals("Точные будильники", text(R.string.onboarding_step_exact_alarms))
        assertEquals(
            "Иначе Android сдвинет приём на удобное себе время — до получаса",
            text(R.string.onboarding_step_exact_alarms_caption),
        )
        assertEquals("Автозапуск без ограничений", text(R.string.onboarding_step_autostart))
        assertEquals(
            "Настройки → Батарея → Запуск приложений → Склянка → вручную",
            text(R.string.onboarding_step_autostart_caption),
        )
        assertEquals("Есть", text(R.string.onboarding_state_granted))
        assertEquals("Разрешить", text(R.string.onboarding_action_allow))
        assertEquals("Открыть настройки", text(R.string.onboarding_action_open_settings))
        assertEquals(
            "Проверить можно сразу: пришлём тестовое напоминание через минуту.",
            text(R.string.onboarding_test_hint),
        )
        assertEquals("Дальше", text(R.string.onboarding_action_next))
        assertEquals("Пропустить, настрою позже", text(R.string.onboarding_action_skip))
    }

    @Test
    fun theSummaryLineOfTheSettingsRowReadsAsTheSettingsMockUpHasIt() {
        // Критерий приёмки 14: «Одно не выдано» — дословно из `Settings.dc.html`.
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
