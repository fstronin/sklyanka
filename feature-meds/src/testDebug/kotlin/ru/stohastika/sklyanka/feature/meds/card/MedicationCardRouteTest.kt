package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.stock.RefillStockUseCase
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.feature.meds.DirectTransactor
import ru.stohastika.sklyanka.feature.meds.FakeActiveProfile
import ru.stohastika.sklyanka.feature.meds.FakeIntakes
import ru.stohastika.sklyanka.feature.meds.FakeMedications
import ru.stohastika.sklyanka.feature.meds.MedsTestData
import ru.stohastika.sklyanka.feature.meds.RecordingLog
import ru.stohastika.sklyanka.feature.meds.RecordingPlanner
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Открытие карточки: устаревший исход прошлого ухода её не закрывает (SKL-145).
 *
 * Маршрут держится хозяином экрана — тем же, что в продукте: закрытие карточки маршрутом
 * значит уход из неё, и хозяин снимает её из композиции. Следующий вход — **новая** сборка
 * маршрута, ровно как при открытии лекарства из списка.
 *
 * Дефект был в том, что исход закрытия читался как состояние: `collectAsState` отдаёт
 * последнее значение `StateFlow`, и `closed` прошлого архива закрывал только что открытую
 * карточку — человек видел мигание и оставался в списке. Тот же класс дефекта уже чинили
 * у экрана разрешений (SKL-40, `PermissionsRoute`): исход принадлежит тому уходу, который
 * его поставил.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class MedicationCardRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val undoActions = UndoableActionStore(clock)

    private val model = MedicationCardViewModel(
        activeProfile = activeProfile,
        medications = medications,
        intakes = intakes,
        operations = MedCardOperations(
            archive = ArchiveMedicationUseCase(medications, RecordingPlanner()),
            restore = RestoreMedicationUseCase(
                medications = medications,
                planner = RecordingPlanner(),
                transactor = DirectTransactor(),
                time = clock,
                log = RecordingLog(),
            ),
            refillStock = RefillStockUseCase(medications, RecordingLog()),
            delete = DeleteMedicationUseCase(medications, intakes, RecordingPlanner()),
        ),
        time = clock,
        undoActions = undoActions,
    )

    @Test
    fun anotherCardOpensAfterThePreviousOneWasClosed() {
        seedCourses()
        val host = Host(open = FIRST_ID)
        show(host)
        awaitCard()
        closeTheCard(host)

        composeRule.runOnUiThread { host.open = SECOND_ID }
        settle()

        assertEquals("the outcome of the previous leave must not close another card", 1, host.backs)
        composeRule.onNodeWithTag(TAG_MEDICATION_CARD_SCREEN).assertExists()
    }

    @Test
    fun theSameCardOpensAgainAfterItWasClosed() {
        seedCourses()
        val host = Host(open = FIRST_ID)
        show(host)
        awaitCard()
        closeTheCard(host)

        composeRule.runOnUiThread { host.open = FIRST_ID }
        settle()

        assertEquals("a reopened card must not replay the close of the previous leave", 1, host.backs)
        composeRule.onNodeWithTag(TAG_MEDICATION_CARD_SCREEN).assertExists()
    }

    /** Убрать первую карточку в архив: маршрут закрывает её, хозяин уносит из композиции. */
    private fun closeTheCard(host: Host) {
        composeRule.runOnUiThread { model.onIntent(MedCardIntent.Archive) }
        composeRule.waitUntil(WAIT_MS) { host.backs == 1 }
        composeRule.waitForIdle()
    }

    /** Дать состояниям и анимации сойтись: маршрут пересобирается не мгновенно. */
    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
    }

    private fun awaitCard() {
        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(TAG_MEDICATION_CARD_SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun show(host: Host) {
        composeRule.setContent {
            SklyankaTheme {
                host.open?.let { id ->
                    MedicationCardRoute(
                        medicationId = id,
                        viewModel = model,
                        onBack = {
                            host.backs += 1
                            host.open = null
                        },
                        onEdit = {},
                    )
                }
            }
        }
    }

    private fun seedCourses() {
        medications.put(
            MedsTestData.medication(id = FIRST_ID, times = listOf(MedsTestData.MORNING)),
            MedsTestData.medication(id = SECOND_ID, times = listOf(MedsTestData.MORNING)),
        )
    }

    /**
     * Хозяин экрана карточки: что открыто, сколько раз ушли из карточки.
     *
     * Уход из карточки — снятие её из композиции: ровно это делает стек раздела (`app`),
     * и следующее открытие начинается новой сборкой маршрута.
     */
    private class Host(open: String) {

        var open: String? by mutableStateOf(open)

        var backs: Int = 0
    }

    private companion object {
        const val FIRST_ID = "med-first"
        const val SECOND_ID = "med-second"
        const val WAIT_MS = 5_000L
        const val SETTLE_MS = 1_000L
    }
}
