package ru.stohastika.sklyanka.feature.meds

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.list.MedicationListViewModel
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TakePrnAction
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Раздел «Лекарства»: список и форма (SKL-57).
 *
 * Проверяет то, что видит человек: после сохранения первое нажатие «+» открывает форму,
 * и она остаётся открытой. Прогон на JVM (стенд SKL-24), потому что дефект — порядок
 * состояния, а не кадры: на эмуляторе он выглядит мерцанием, здесь — закрытой формой.
 *
 * Чтение активного профиля придержано защёлкой: на настоящем устройстве оно
 * асинхронно (DataStore), и окно между повторным открытием формы и приходом свежего
 * состояния — то место, где старое сохранение закрывало новую форму.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class MedsRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = MedsTestData.clock()
    private val activeProfile = GatedActiveProfile(MedsTestData.PROFILE_ID)
    private val medications = FakeMedications()
    private val intakes = FakeIntakes()
    private val profiles = FakeProfiles()
    private val log = RecordingLog()

    private val form = MedicationFormViewModel(
        activeProfile = activeProfile,
        medications = medications,
        saveMedication = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = medications,
            planner = MedicationPlanner { medicationId, reason ->
                PlanIntakesUseCase(
                    profiles = profiles,
                    medications = medications,
                    intakes = intakes,
                    time = clock,
                    log = log,
                    policy = IntakeGenerationPolicy(windowDepthDays = WINDOW_DEPTH_DAYS),
                )(PlanScope.Medication(medicationId), reason)
            },
            transactor = DirectTransactor(),
            log = log,
            newId = { NEW_ID },
        ),
        time = clock,
    )

    private val list = MedicationListViewModel(
        activeProfile = activeProfile,
        medications = medications,
        intakes = intakes,
        time = clock,
        takePrn = TakePrnAction {},
    )

    private fun show() {
        profiles.put(
            Profile(
                id = MedsTestData.PROFILE_ID,
                name = "owner",
                color = 0,
                isSelf = true,
                mirrorReminders = false,
            ),
        )
        composeRule.setContent {
            SklyankaTheme {
                MedsRoute(
                    listViewModel = list,
                    formViewModel = form,
                    onOpenMedicationCard = {},
                    onOpenArchive = {},
                )
            }
        }
    }

    @Test
    fun theFormOpensAgainRightAfterASave() {
        show()

        openForm()
        fillMinimalCourse()
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithTag(TAG_MEDICATION_FORM).fetchSemanticsNodes().isEmpty()
        }

        openForm()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)

        composeRule.onNodeWithTag(TAG_MEDICATION_FORM).assertIsDisplayed()
    }

    /**
     * Нажатие «+» с придержанным чтением профиля.
     *
     * Защёлка отпускается только после того, как у старого состояния была возможность
     * закрыть форму: на устройстве именно это и происходит — форма успевает уйти
     * до прихода свежего состояния.
     */
    private fun openForm() {
        activeProfile.hold()
        composeRule.onNodeWithTag(TAG_ADD_MEDICATION).performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
        activeProfile.release()
        composeRule.waitUntil(timeoutMillis = WAIT_MS) {
            composeRule.onAllNodesWithTag(TAG_MEDICATION_FORM).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Минимально достаточный курс: название, доза и одно время. */
    private fun fillMinimalCourse() {
        composeRule.runOnUiThread {
            form.onIntent(MedFormIntent.NameChanged("med-a"))
            form.onIntent(MedFormIntent.DoseAmountChanged("2"))
            form.onIntent(MedFormIntent.TimeAdded)
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val NEW_ID = "55555555-5555-4555-8555-555555555555"
        const val WINDOW_DEPTH_DAYS = 7
        const val WAIT_MS = 5_000L
        const val SETTLE_MS = 1_000L
    }
}

/**
 * Активный профиль, чтение которого можно придержать.
 *
 * Пока защёлка не отпущена, `current()` не отвечает — ровно как настоящее хранилище,
 * которому нужно время. Поток идентификатора остаётся живым: список подписан на него,
 * и защёлка не должна его останавливать.
 */
private class GatedActiveProfile(private val id: String) : ActiveProfileRepository {

    override val activeProfileId: Flow<String> = MutableStateFlow(id)
    private var gate: CompletableDeferred<Unit>? = null

    fun hold() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun current(): String {
        gate?.await()
        return id
    }

    override suspend fun switchTo(profileId: String) = Unit
}
