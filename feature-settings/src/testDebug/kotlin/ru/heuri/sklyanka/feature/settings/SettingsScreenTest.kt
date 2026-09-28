package ru.heuri.sklyanka.feature.settings

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import ru.heuri.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.heuri.sklyanka.ui.component.TAG_SWITCH_ROW
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaSizes

/**
 * Состав экрана «Настройки» и честность его подписей (SKL-20).
 *
 * Идёт на JVM под Robolectric (SKL-24): состав экрана, тексты состояний и цели нажатия
 * настоящего устройства не требуют. Что требует — состояние разрешений после отзыва
 * в системных настройках, — проверяется на устройстве по чек-листу `TDD.md §10`.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: снимок стенда режет его по нижней кромке, и проверка
// состава видела бы не экран, а его верх.
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theScreenIsAssembledFromFiveBlocksInTheOrderOfTheMockup() {
        show()

        // «Настройки» на экране дважды и так и должно быть: заголовок и подпись активной
        // вкладки нижней панели. Одно — где человек находится, другое — как он сюда попал.
        assertEquals(2, composeRule.onAllNodesWithText(string(R.string.settings_title)).fetchSemanticsNodes().size)
        listOf(
            R.string.settings_rubric_profiles,
            R.string.settings_rubric_reminders,
            R.string.settings_rubric_system,
            R.string.settings_rubric_data,
        ).forEach { rubric ->
            composeRule.onNodeWithText(string(rubric).uppercase()).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(TAG_ABOUT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_BOTTOM_BAR).assertIsDisplayed()
    }

    @Test
    fun theOwnProfileComesFirstWithItsLabelAndTheMarkOfTheActiveOne() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_profile_self)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ACTIVE_PROFILE, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_profile_add)).assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_PROFILE_ROW).fetchSemanticsNodes().size)
    }

    /** ПРФ-7: строка чужого профиля несёт число лекарств и состояние дублирования. */
    @Test
    fun theForeignProfileRowStatesTheNumberOfMedicationsAndTheMirroredReminders() {
        show()

        val medications = context.resources.getQuantityString(
            R.plurals.settings_profile_medications,
            SettingsScreenFixtures.FOREIGN_MEDICATIONS,
            SettingsScreenFixtures.FOREIGN_MEDICATIONS,
        )
        val expected = context.getString(
            R.string.settings_profile_subtitle,
            medications,
            string(R.string.settings_profile_mirrored),
        )
        composeRule.onNodeWithText(SettingsScreenFixtures.FOREIGN_NAME, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(expected, useUnmergedTree = true).assertExists()
    }

    @Test
    fun theRemindersBlockShowsTheStoredValues() {
        show(SettingsScreenFixtures.state(reminders = ReminderSettingsValues(persistentEnabled = true)))

        composeRule.onNodeWithText(string(R.string.settings_sound_value), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_persistent_caption), useUnmergedTree = true).assertExists()
        val snooze = context.resources.getQuantityString(
            R.plurals.settings_snooze_minutes,
            ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES,
            ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES,
        )
        composeRule.onNodeWithText(snooze, useUnmergedTree = true).assertExists()
    }

    /**
     * `ОВ-11`: «Тихие часы» из макета в продукт не идут.
     *
     * Проверяется числом строк с переключателем, а не отсутствием текста: строки нет
     * ни в ресурсах, ни в коде, и искать её по названию значило бы проверять опечатку.
     * Переключатель на экране ровно один — настойчивое напоминание; вторым в макете
     * стоят как раз тихие часы.
     */
    @Test
    fun thereIsExactlyOneSwitchOnTheScreenAndItIsNotQuietHours() {
        show()

        assertEquals(1, composeRule.onAllNodesWithTag(TAG_SWITCH_ROW).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(string(R.string.settings_persistent), useUnmergedTree = true).assertExists()
    }

    /** НАП-27: звук один на всё приложение, второго выбора на экране нет. */
    @Test
    fun theSoundIsChosenInOnePlaceOnly() {
        show()

        assertEquals(1, composeRule.onAllNodesWithText(string(R.string.settings_sound)).fetchSemanticsNodes().size)
    }

    /**
     * Строки «Часы Huawei» на экране нет — отдельной проверкой.
     *
     * Это решение `ADR-0009`: интеграция с Wear Engine отложена до пост-MVP, и подпись
     * о связи, которая не может измениться, была бы ложью. Проверка идёт против строки,
     * которой нет ни в одном ресурсе, а не против тега.
     */
    @Test
    fun thereIsNoWatchRowOnTheScreen() {
        show()

        composeRule.onAllNodesWithText(WATCH_ROW_TITLE).assertCountEquals(0)
    }

    @Test
    fun onePermissionMissingIsStatedInWords() {
        show(SettingsScreenFixtures.state(denied = 1))

        composeRule
            .onNodeWithText(string(R.string.settings_permissions_denied_one), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun twoPermissionsMissingAreStatedAsANumber() {
        show(SettingsScreenFixtures.state(denied = TWO))

        val expected = context.getString(R.string.settings_permissions_denied_many, TWO)
        composeRule.onNodeWithText(expected, useUnmergedTree = true).assertExists()
    }

    /** НАП-20: всё выдано — подпись нейтральна и честно называет непроверяемый автозапуск. */
    @Test
    fun allPermissionsGrantedIsANeutralLineThatStillNamesTheAutostart() {
        show(SettingsScreenFixtures.state(denied = 0, manual = 1))

        composeRule
            .onNodeWithText(string(R.string.settings_permissions_granted_manual), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun theDataBlockLeadsToTheHistoryAndToTheTransfer() {
        val intents = mutableListOf<SettingsIntent>()
        show(onIntent = intents::add)

        composeRule.onNodeWithText(string(R.string.settings_transfer_caption), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(TAG_HISTORY_ROW).performClick()
        composeRule.onNodeWithTag(TAG_TRANSFER_ROW).performClick()

        assertEquals(listOf(SettingsIntent.OpenHistory, SettingsIntent.OpenDataTransfer), intents)
    }

    /** Строка «Разрешения и автозапуск» ведёт в диагностику напоминаний (решение по `ОВ-48`). */
    @Test
    fun thePermissionsRowLeadsOnwards() {
        val intents = mutableListOf<SettingsIntent>()
        show(onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_PERMISSIONS_ROW).performClick()

        assertEquals(listOf(SettingsIntent.OpenPermissions), intents)
    }

    @Test
    fun aProfileRowSwitchesTheProfile() {
        val intents = mutableListOf<SettingsIntent>()
        show(onIntent = intents::add)

        composeRule.onAllNodesWithTag(TAG_PROFILE_ROW)[1].performClick()

        assertEquals(listOf(SettingsIntent.SelectProfile("mother")), intents)
    }

    /**
     * Приложение прямо говорит, чем оно не является (`П-4`).
     *
     * Формулировка видна сразу: ни нажатия, ни раскрытия, ни прокрутки длинного текста.
     * Плашка нажатия не принимает вовсе — наружу с экрана вести нечему (НФТ-14).
     */
    @Test
    fun theAboutPlateStatesThatTheApplicationIsNotAMedicalDevice() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_about_vendor), useUnmergedTree = true).assertExists()
        val name = context.getString(R.string.settings_about_app, SettingsScreenFixtures.VERSION)
        composeRule.onNodeWithText(name, useUnmergedTree = true).assertExists()
        assertTrue(
            "the about plate must not be clickable: there is nowhere outside to go",
            composeRule.onAllNodesWithTag(TAG_ABOUT).fetchSemanticsNodes()
                .none { node -> node.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) },
        )
    }

    /** НФТ-29: каждая цель нажатия на экране — не меньше 44 dp, включая строки списков. */
    @Test
    fun everyTouchTargetIsAtLeastTheMinimum() {
        show()

        assertTouchTargets(composeRule)
    }

    /** НФТ-31: при 200 % экран остаётся проходимым, а подпись вкладки уступает значку. */
    @Test
    fun theScreenStaysReachableAtDoubleFontScale() {
        show(fontScale = SCALE_200)

        composeRule.onNodeWithTag(TAG_SETTINGS_SCREEN).assertExists()
        composeRule.onNodeWithTag(TAG_BOTTOM_BAR).assertExists()
        assertTouchTargets(composeRule)
    }

    private fun assertTouchTargets(provider: SemanticsNodeInteractionsProvider) {
        val nodes = provider.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("the screen must contain clickable rows", nodes.isNotEmpty())
        nodes.forEach { node ->
            val density = node.layoutInfo.density
            val width = with(density) { node.size.width.toDp() }
            val height = with(density) { node.size.height.toDp() }
            val minimum = SklyankaSizes.MinTouchTarget - TOLERANCE
            assertTrue(
                "clickable node ${node.id} is $width×$height, below ${SklyankaSizes.MinTouchTarget}",
                width >= minimum && height >= minimum,
            )
        }
    }

    private fun show(
        state: SettingsUiState = SettingsScreenFixtures.state(),
        onIntent: (SettingsIntent) -> Unit = {},
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = androidx.compose.ui.platform.LocalDensity.current.density,
                    fontScale = fontScale,
                ),
            ) {
                SklyankaTheme {
                    SettingsScreen(state = state, onIntent = onIntent)
                }
            }
        }
    }

    private fun string(id: Int): String = context.getString(id)

    private companion object {
        const val TWO = 2
        const val SCALE_200 = 2.0f

        /**
         * Заголовок снятой строки часов. Литерал намеренный: ресурс удалён решением
         * `ADR-0009`, и проверить его отсутствие иначе нечем.
         */
        const val WATCH_ROW_TITLE = "Часы Huawei"

        /** Округление плотности может отнять доли точки; одной точки запаса хватает. */
        val TOLERANCE = SklyankaSizes.BorderThin
    }
}
