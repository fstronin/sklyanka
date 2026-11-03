package ru.stohastika.sklyanka.feature.meds

import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
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
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormRoute
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Поворот экрана не теряет ни форму, ни введённое в неё (SKL-142, `docs/norm/04-behavior.md` §5.1).
 *
 * Проверяется настоящее пересоздание активности с подменой конфигурации — не подмена
 * композиции стендом: `configurationChange` проводит активность по её жизненному циклу,
 * сохраняет состояние композиции и отдаёт его новой сборке. Так же ведёт себя телефон
 * при повороте.
 *
 * Стенд держит корнем активности саму [MedicationFormRoute]: где форма стоит в разделе —
 * дело того, кто собирает маршруты (`app`, SKL-150), а здесь проверяется то, что принадлежит
 * форме, — пересоздание не затирает черновик. Черновик живёт в ViewModel (она пересоздание
 * переживает), но безусловное открытие затирало его, поэтому форма открывается один раз
 * на заход.
 *
 * Место человека в разделе после поворота держит стек раздела в `app`: сохранение места
 * проверяет `MedsStackSaverTest` (SKL-142), а форму, поднятую круглой кнопкой списка,
 * `MedsFormFromListTest` (SKL-150).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class MedicationFormRotationTest {

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

    @After
    fun tearDownTheStand() {
        controllers.forEach { it.pause().stop().destroy() }
        controllers.clear()
        MedsFormHost.factory = null
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Стенд убирает за собой: брошенная активность мешает чужим проверкам. */
    private val controllers = mutableListOf<ActivityController<MedsFormHost>>()

    private fun host(): ActivityController<MedsFormHost> =
        Robolectric.buildActivity(MedsFormHost::class.java).setup().also { controllers += it }

    @Test
    fun theFormAndTheTypedNameSurviveRotation() {
        MedsFormHost.factory = MedsFormFactory(form)
        val controller = host()
        idle()

        waitForTag(TAG_MEDICATION_FORM)
        composeRule.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NAME_FIELD)),
            useUnmergedTree = true,
        ).performTextInput(NAME)
        composeRule.waitForIdle()
        assertEquals(NAME, typedName())

        rotate(controller)

        composeRule.onNodeWithTag(TAG_MEDICATION_FORM).assertExists()
        assertEquals("what was typed must survive the rotation", NAME, typedName())
    }

    /** Поворот: настоящая смена конфигурации, за которой следует пересоздание активности. */
    private fun rotate(controller: ActivityController<MedsFormHost>) {
        val config = Configuration(controller.get().resources.configuration)
        config.orientation = Configuration.ORIENTATION_LANDSCAPE
        controller.configurationChange(config)
        controller.visible()
        idle()
        composeRule.waitForIdle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(WAIT_MS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Введённое в поле названия: у поля читается его редактируемый текст, а не подпись. */
    private fun typedName(): String = composeRule.onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NAME_FIELD)),
        useUnmergedTree = true,
    ).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private companion object {
        const val NAME = "med-rotated"
        const val NEW_ID = "55555555-5555-4555-8555-555555555555"
        const val WINDOW_DEPTH_DAYS = 7
        const val WAIT_MS = 5_000L
    }
}

/**
 * Хозяин экрана: форма лекарства с настоящей моделью активности.
 *
 * Модель приходит из ViewModelStore активности — тем же путём, каким она живёт в продукте,
 * и потому пересоздание её переживает.
 */
class MedsFormHost : ComponentActivity() {

    val form: MedicationFormViewModel by viewModels { requireNotNull(factory) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SklyankaTheme {
                MedicationFormRoute(viewModel = form, medicationId = null, onDone = {})
            }
        }
    }

    companion object {
        var factory: ViewModelProvider.Factory? = null
    }
}

/** Фабрика стенда: отдаёт модель из того же мира, что и проверка. */
class MedsFormFactory(private val form: MedicationFormViewModel) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(MedicationFormViewModel::class.java) -> form as T
        else -> error("unexpected view model: ${modelClass.name}")
    }
}
