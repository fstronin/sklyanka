package ru.stohastika.sklyanka.acceptance.deletion

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.today.MedicationCardFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.DeleteOutcome
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.card.MedCardIntent
import ru.stohastika.sklyanka.feature.meds.card.MedicationCardScreen
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.navigation.AddMedicationLaunch
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.RootNavigation
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_INPUT
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.feature.today.R as TodayR

/**
 * Композиционные шаги приёмки SKL-99: карточка, открытая с «Сегодня», и ярлык (ОВ-124).
 *
 * Прогон идёт в `app`, потому что сценарию нужны оба экрана сразу: карточка живёт
 * в `feature-meds`, «Сегодня» — в `feature-today` (SKL-26, `TDD.md §4.3`). Композиция
 * собирается по состоянию из мира, а удаление и возврат применяются настоящими доменными
 * сценариями. Уход с экрана шаг воспроизводит точкой выхода — жизненный цикл композиции
 * между шагами стенд не воспроизводит (приём SKL-30/`TDD.md §15.5`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class DeletionCompositionSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var focused: String = ""
    private var fromToday = false
    private var cardClosed = false
    private var shortcutOpenedTheForm = false

    // --- Предыстория ---------------------------------------------------------------------

    @Допустим("^человек открыл карточку «([^»]+)» с «Сегодня»$")
    fun thePersonOpenedTheCardFromToday(medication: String) {
        focused = medication
        fromToday = true
        openCard()
    }

    @Допустим("^человек удалил лекарство «([^»]+)» из карточки$")
    fun thePersonAlreadyDeletedFromTheCard(medication: String) {
        focused = medication
        val outcome = world.deleteMedicationWithWindow(medication)
        assertTrue("the deletion must take effect: $outcome", outcome is DeleteOutcome.Deleted)
        cardClosed = true
    }

    @Допустим("^живое окно удаления показано на экране$")
    fun theLiveDeletionWindowIsShown() {
        assertTrue("the cell must carry the deletion", world.undoActions.live() != null)
        composeToday { test ->
            test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertExists()
        }
    }

    // --- Действия ------------------------------------------------------------------------

    @Когда("^человек удаляет лекарство «([^»]+)» из карточки$")
    fun thePersonDeletesFromTheCard(medication: String) {
        focused = medication
        openCard()
        deleteFromTheCard()
    }

    @Когда("^приходит действие ярлыка «([^»]+)»$")
    fun theShortcutArrives(label: String) {
        assertEquals("Добавить лекарство", label)
        val navigation = RootNavigation().apply { openMeds(MedsDestination.List) }
        shortcutOpenedTheForm = navigation.openFromLaunch(AddMedicationLaunch.ACTION, world.undoActions.live())
    }

    @Когда("^человек уходит с экрана$")
    fun thePersonLeavesTheScreen() {
        // Уход — точка выхода `onScreenLeft` маршрута: окно закрывается, данные остаются.
        world.undoActions.clear()
    }

    @Когда("^человек нажимает «Отменить» в снэкбаре удаления$")
    fun thePersonUndoesInTheSnackbar() {
        val action = context.getString(TodayR.string.today_undo_action)
        composeToday { test ->
            test.onNodeWithText(action, useUnmergedTree = true).performClick()
        }
    }

    // --- Проверки ------------------------------------------------------------------------

    @Тогда("^снова показан «Сегодня»$")
    fun todayIsShownAgain() {
        // Человек возвращается туда, откуда открыл карточку: состояние «Сегодня»
        // из мира собирается и предъявляется.
        assertTrue("the card must close", cardClosed)
        assertTrue("the scenario opens the card from Today", fromToday)
        composeToday { test ->
            test.onNodeWithTag(ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN, useUnmergedTree = true)
                .assertExists()
        }
    }

    @Тогда("^приёма «([^»]+)» на «Сегодня» нет$")
    fun thereIsNoIntakeToday(medication: String) {
        val name = displayName(medication)
        composeToday { test ->
            test.onAllNodesWithText(name, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^приём «([^»]+)» снова на «Сегодня»$")
    fun theIntakeIsBackToday(medication: String) {
        val name = displayName(medication)
        composeToday { test ->
            test.onNodeWithText(name, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^форма нового лекарства не открывается$")
    fun theFormDoesNotOpen() {
        assertFalse("the shortcut must not open the form over the live window", shortcutOpenedTheForm)
    }

    @Тогда("^живое окно удаления остаётся на экране$")
    fun theLiveWindowStays() {
        assertTrue("the cell must still carry the deletion", world.undoActions.live() != null)
        composeToday { test ->
            test.onNodeWithTag(TAG_SNACKBAR, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^живого окна удаления нет$")
    fun thereIsNoLiveWindow() {
        assertNull(world.undoActions.live())
    }

    @Тогда("^вернуть лекарство через интерфейс нельзя$")
    fun theMedicationCannotBeReturned() {
        assertTrue("ПМ-24: the record stays deleted", world.medications.get(focused).isDeleted)
        assertNull(world.undoActions.live())
    }

    // --- Обвязка -------------------------------------------------------------------------

    private fun openCard() {
        val medication = cardMedication()
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationCardScreen(
                        state = MedicationCardFromWorld.state(world, medication),
                        onIntent = {},
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_MEDICATION_CARD_SCREEN, useUnmergedTree = true).assertExists()
        }
    }

    /**
     * Удаление с карточки целиком: шторка открыта, слово набрано, «Удалить» нажата.
     *
     * Шторка подтверждения сравнивает введённое слово с образцом из ресурса (ПР-14, §5.7),
     * поэтому стенд набирает его так же, как человек: без слова кнопка недоступна, и нажатие
     * проверяло бы разметку, а не продукт.
     */
    private fun deleteFromTheCard() {
        val medication = cardMedication()
        val intents = mutableListOf<MedCardIntent>()
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    MedicationCardScreen(
                        state = MedicationCardFromWorld.state(world, medication, deleteConfirmation = true),
                        onIntent = intents::add,
                    )
                }
            }
            waitForIdle()
            onNodeWithTag(TAG_TEXT_FIELD_INPUT, useUnmergedTree = true)
                .performTextReplacement(context.getString(R.string.medication_delete_word))
            waitForIdle()
            onNodeWithTag(TAG_DELETE_CONFIRM, useUnmergedTree = true).performClick()
            waitForIdle()
        }
        intents.forEach { applyCardIntent(medication.id, it) }
    }

    private fun applyCardIntent(medicationId: String, intent: MedCardIntent) {
        when (intent) {
            MedCardIntent.DeleteConfirmed -> {
                val medication = requireNotNull(world.medications.byId(medicationId))
                val outcome = kotlinx.coroutines.runBlocking {
                    DeleteMedicationUseCase(
                        medications = world.repositories.medicationStore,
                        intakes = world.repositories.intakeStore,
                        planner = world.planner(),
                    )(medicationId)
                }
                assertTrue("the deletion must take effect: $outcome", outcome is DeleteOutcome.Deleted)
                world.undoActions.publishDeletion(medicationId = medicationId, medicationName = medication.name)
                cardClosed = true
            }

            else -> Unit
        }
    }

    /** «Сегодня»: экран собирается из мира, намерения применяются к нему же. */
    private fun composeToday(action: (androidx.compose.ui.test.ComposeUiTest) -> Unit) {
        val intents = mutableListOf<TodayIntent>()
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    TodayScreen(state = TodayFromWorld.state(world), onIntent = intents::add)
                }
            }
            waitForIdle()
            action(this)
            waitForIdle()
        }
        intents.forEach(::applyTodayIntent)
    }

    private fun applyTodayIntent(intent: TodayIntent) {
        when (intent) {
            is TodayIntent.UndoDeletion -> {
                world.undoActions.clear()
                world.undoDeleteMedication(displayNameKey(intent.medicationId))
            }

            is TodayIntent.UndoMark -> Unit

            else -> Unit
        }
    }

    private fun cardMedication() = world.medications.get(world.medicationNamed(focused))

    private fun displayName(medication: String): String = world.medications.get(world.medicationNamed(medication)).name

    private fun displayNameKey(medicationId: String): String = world.medications.names()
        .first { world.medications.get(it).id == medicationId }
}
