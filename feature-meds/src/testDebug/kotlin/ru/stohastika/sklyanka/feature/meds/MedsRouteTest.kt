package ru.stohastika.sklyanka.feature.meds

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.feature.meds.list.TakePrnAction
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Круглая кнопка списка поднимает форму наружу, а не открывает её здесь (SKL-150).
 *
 * Форма нового лекарства — место в стеке раздела (`docs/norm/04-behavior.md` §5.1: список,
 * архив, новая форма, карточка, правка), и поднимает это место тот, кто собирает маршруты,
 * то есть `app`. Пока открытость формы жила флагом этого маршрута, стек раздела оставался
 * на списке, и системная «Назад» с формы уводила человека из раздела.
 *
 * Прежняя проверка этого файла держала защёлку на чтении активного профиля (SKL-57): форма
 * жила флагом маршрута, и приход свежего состояния списка закрывал только что открытую форму.
 * Формы здесь больше нет — её открытость принадлежит стеку раздела, и состояние списка на неё
 * не влияет вовсе: класс дефекта исчез по построению, а не спрятан от проверки. Что осталось
 * проверяемым на этом шве — что маршрут не заводит форму себе и передаёт намерение списка
 * вызывающему: иначе форма вернулась бы флагом, и дефект вернулся бы вместе с ней.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class MedsRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val undoActions = UndoableActionStore(clock)

    /** Просьбы открыть форму, пришедшие вызывающему: их число и есть проверка. */
    private var formRequests = 0

    private val list = MedicationListViewModel(
        activeProfile = activeProfile,
        medications = medications,
        intakes = intakes,
        time = clock,
        takePrn = TakePrnAction {},
        undoDelete = UndoDeleteMedicationUseCase(medications, RecordingPlanner()),
        undoActions = undoActions,
        analytics = AnalyticsTestKit(CoroutineScope(Dispatchers.Unconfined)).recorder,
    )

    private fun show() {
        composeRule.setContent {
            SklyankaTheme {
                MedsRoute(
                    listViewModel = list,
                    onOpenMedicationCard = {},
                    onOpenArchive = {},
                    onOpenNewMedication = { formRequests++ },
                )
            }
        }
    }

    @Test
    fun theAddButtonAsksTheCallerForTheForm() {
        show()

        composeRule.onNodeWithTag(TAG_ADD_MEDICATION).performClick()
        composeRule.waitForIdle()

        assertEquals("the add button must ask the route caller for the form", 1, formRequests)
        composeRule.onNodeWithTag(TAG_MEDICATION_LIST).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_MEDICATION_FORM).assertCountEquals(0)
    }
}
