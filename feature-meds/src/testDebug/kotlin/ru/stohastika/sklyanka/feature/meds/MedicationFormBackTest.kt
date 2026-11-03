package ru.stohastika.sklyanka.feature.meds

import android.os.Looper
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_DIALOG

/**
 * Системная «Назад» на форме лекарства спрашивает подтверждение, только если черновик грязный
 * (RT-1, Р-87 п. 1, `docs/norm/04-behavior.md` §5.1).
 *
 * Проверяется на настоящей активности с настоящим `OnBackPressedDispatcher`: грязная форма
 * поднимает диалог подтверждения, чистая закрывается молча. Действия диалога («остаться» держит
 * ввод, «сбросить» закрывает без записи) проверяются походом на устройстве
 * (`HostileMedicationFormTest`), а здесь — сам факт перехвата «Назад» по грязности черновика.
 *
 * Хозяин экрана — общий с проверкой поворота ([MedsFormHost]): та же настоящая форма и тот же
 * боевой путь открытия, поэтому перехват «Назад» проверяется на том же маршруте, что живёт
 * в продукте.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class MedicationFormBackTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val clock = MedsTestData.clock()
    private val activeProfile = FakeActiveProfile()
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
        proStatus = ObserveProStatusUseCase(FakeProCache(), clock),
        analytics = AnalyticsTestKit(CoroutineScope(Dispatchers.Unconfined)).recorder,
    )

    private val controllers = mutableListOf<ActivityController<MedsFormHost>>()

    @After
    fun tearDownTheStand() {
        controllers.forEach { runCatching { it.destroy() } }
        controllers.clear()
        // Диалог подтверждения живёт своим окном и анимируется вне часов правила: после
        // уничтожения активности на главном лупере остаются его задачи, и следующий
        // Compose-тест в том же процессе никогда не видит покоя (`AppNotIdle`). Гасим
        // луппер, как это уже делает проверка поворота.
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun theSystemBackOnADirtyFormAsksBeforeDiscarding() {
        val controller = host()
        idle()
        waitForTag(TAG_MEDICATION_FORM)
        typeIntoName(NAME)

        controller.get().onBackPressedDispatcher.onBackPressed()
        idle()

        // Грязная форма перехватывает «Назад»: активность жива, а маршрут поднял диалог
        // подтверждения. Дерево семантики после открытия диалога на этом стенде не читают:
        // окно диалога анимируется вне часов правила, и любое чтение узла (оно же ждёт
        // покоя) не заканчивается — артефакт оснастки, а не продукта. Сам диалог и его
        // действия проверяет поход на устройстве (`HostileMedicationFormTest`), а здесь —
        // перехват: до правки системная «Назад» завершала активность молча (RT-1).
        assertFalse("a dirty form must intercept the system back", controller.get().isFinishing)
    }

    @Test
    fun theSystemBackOnACleanFormClosesWithoutAsking() {
        val controller = host()
        idle()
        waitForTag(TAG_MEDICATION_FORM)

        controller.get().onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertEquals("a clean form must close without a dialog", 0, dialogNodes().size)
    }

    private fun dialogNodes() = composeRule.onAllNodesWithTag(TAG_CONFIRM_DIALOG).fetchSemanticsNodes()

    private fun host(): ActivityController<MedsFormHost> {
        MedsFormHost.factory = MedsFormFactory(form)
        return Robolectric.buildActivity(MedsFormHost::class.java).setup().also { controllers += it }
    }

    private fun typeIntoName(name: String) {
        composeRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NAME_FIELD)),
            useUnmergedTree = true,
        ).performTextInput(name)
        composeRule.waitForIdle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val NAME = "med-discard"
        const val NEW_ID = "55555555-5555-4555-8555-555555555556"
        const val WINDOW_DEPTH_DAYS = 7
        const val WAIT_MS = 5_000L
    }
}
