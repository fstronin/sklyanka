package ru.stohastika.sklyanka.feature.settings

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
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.ui.component.TAG_LIST_PICKER_OPTION
import ru.stohastika.sklyanka.ui.component.TAG_LIST_PICKER_SELECTED
import ru.stohastika.sklyanka.ui.component.TAG_SWITCH_ROW
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

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
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
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

    /**
     * SKL-78, ОВ-98, Р-52: у строки значения нет, заголовок называет экран, в который она
     * ведёт, а подпись — то, что на нём настраивается.
     *
     * Прежнее фальшивое «Мягкий сигнал» снято: такого звука не существует, оба канала
     * звучат системными сигналами.
     */
    @Test
    fun theSoundRowCarriesNoValueAndNamesTheSystemSettings() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_sound_caption), useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText(SOUND_VALUE).assertCountEquals(0)
    }

    @Test
    fun theRemindersBlockShowsTheStoredValues() {
        show(SettingsScreenFixtures.state(reminders = ReminderSettingsValues(persistentEnabled = true)))

        composeRule.onNodeWithText(string(R.string.settings_sound_caption), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_persistent_caption), useUnmergedTree = true).assertExists()
        val snooze = context.resources.getQuantityString(
            R.plurals.settings_snooze_minutes,
            ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES,
            ReminderSettingsValues.DEFAULT_SNOOZE_MINUTES,
        )
        composeRule.onNodeWithText(snooze, useUnmergedTree = true).assertExists()
    }

    /** SKL-78, ОВ-98, Р-52: строка перехода ведёт в системный экран уведомлений приложения. */
    @Test
    fun theSoundRowLeadsToTheSystemNotificationSettings() {
        val intents = mutableListOf<SettingsIntent>()
        show(onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_SOUND_ROW).performClick()

        assertEquals(listOf(SettingsIntent.OpenSound), intents)
    }

    /** SKL-78, ОВ-98: «Отложить на» открывает выбор интервала. */
    @Test
    fun theSnoozeRowOpensTheIntervalPicker() {
        val intents = mutableListOf<SettingsIntent>()
        show(onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_SNOOZE_ROW).performClick()

        assertEquals(listOf(SettingsIntent.OpenSnooze), intents)
    }

    /**
     * SKL-78, ОВ-98: выбор — диалог из ровно пяти значений, выбор применяется сразу.
     *
     * Выбранное отмечено галочкой и состоянием `selected`, а не одним цветом (НФТ-30).
     */
    @Test
    fun theSnoozeDialogOffersTheWholeSetAndAppliesTheChoiceImmediately() {
        val intents = mutableListOf<SettingsIntent>()
        show(SettingsScreenFixtures.state(snoozePickerVisible = true), onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_SNOOZE_DIALOG).assertExists()
        assertEquals(SET_SIZE, composeRule.onAllNodesWithTag(TAG_LIST_PICKER_OPTION).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(TAG_LIST_PICKER_SELECTED, useUnmergedTree = true).assertExists()

        composeRule.onNodeWithText(snoozeLabel(THIRTY)).performClick()

        assertEquals(listOf(SettingsIntent.SetSnooze(THIRTY)), intents)
    }

    /** SKL-78: закрытие диалога не меняет настройку — «Отмена» шлёт отмену. */
    @Test
    fun cancellingTheSnoozeDialogChangesNothing() {
        val intents = mutableListOf<SettingsIntent>()
        show(SettingsScreenFixtures.state(snoozePickerVisible = true), onIntent = intents::add)

        composeRule
            .onNodeWithText(context.getString(ru.stohastika.sklyanka.ui.R.string.action_cancel))
            .performClick()

        assertEquals(listOf(SettingsIntent.DismissSnooze), intents)
    }

    /**
     * `ОВ-11`: «Тихие часы» из макета в продукт не идут.
     *
     * Переключатель на экране один — настойчивое напоминание; тихих часов среди строк нет.
     * Проверяется составом строк, а не отсутствием текста: строки нет ни в ресурсах,
     * ни в коде, и искать её по названию значило бы проверять опечатку.
     */
    @Test
    fun theOnlySwitchOnTheScreenIsThePersistentReminder() {
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
     * Строки «Часы» на экране нет — отдельной проверкой.
     *
     * Это решение Р-3 (SKL-106): часовой шов вырезан из сборки, и подпись
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
     * ОВ-7: строка удаления — только у активного чужого профиля и последней в разделе.
     *
     * «Последняя» проверяется положением: строка стоит за разделителем перед ней —
     * как в решении по ОВ-7.
     */
    @Test
    fun theForeignActiveProfileOffersDeletionAsTheLastRowOfTheBlock() {
        val intents = mutableListOf<SettingsIntent>()
        show(SettingsScreenFixtures.state(activeIsSelf = false), onIntent = intents::add)

        val title = context.getString(R.string.settings_profile_delete, SettingsScreenFixtures.FOREIGN_NAME)
        composeRule.onNodeWithText(title, useUnmergedTree = true).assertExists()
        val divider = composeRule.onNodeWithTag(TAG_DELETE_DIVIDER).fetchSemanticsNode()
        val delete = composeRule.onNodeWithTag(TAG_DELETE_PROFILE_ROW).fetchSemanticsNode()
        assertTrue("the deletion row must stand behind a divider", delete.boundsInRoot.top > divider.boundsInRoot.top)

        composeRule.onNodeWithTag(TAG_DELETE_PROFILE_ROW).performClick()
        assertEquals(listOf(SettingsIntent.DeleteProfile), intents)
    }

    /** ПРФ-28: у своего профиля строки удаления нет вовсе. */
    @Test
    fun theOwnProfileHasNoDeletionRow() {
        show()

        composeRule.onAllNodesWithTag(TAG_DELETE_PROFILE_ROW).assertCountEquals(0)
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

    /**
     * Строка «Экспорт истории» на месте и ведёт в шторку выгрузки (SKL-119, Р-35).
     *
     * Выгрузка CSV доступна всем: строка ведёт в шторку и ничего не спрашивает.
     */
    @Test
    fun theExportRowIsOnTheScreenAndLeadsToTheExportSheet() {
        val intents = mutableListOf<SettingsIntent>()
        show(onIntent = intents::add)

        composeRule.onNodeWithTag(TAG_EXPORT_ROW).performClick()

        assertEquals(listOf(SettingsIntent.OpenHistoryExport), intents)
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

    private fun snoozeLabel(minutes: Int): String =
        context.resources.getQuantityString(R.plurals.settings_snooze_minutes, minutes, minutes)

    private companion object {
        const val TWO = 2
        const val SCALE_200 = 2.0f
        const val SET_SIZE = 5
        const val THIRTY = 30

        /** Прежнее фальшивое значение строки, снятое ОВ-98. Литерал намеренный. */
        const val SOUND_VALUE = "Мягкий сигнал"

        /**
         * Заголовок вырезанной строки часов. Литерал намеренный: ресурс удалён вместе
         * с швом (Р-3, SKL-106), и проверить его отсутствие иначе нечем.
         */
        const val WATCH_ROW_TITLE = "Часы"

        /** Округление плотности может отнять доли точки; одной точки запаса хватает. */
        val TOLERANCE = SklyankaSizes.BorderThin
    }
}
