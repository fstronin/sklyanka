package ru.stohastika.sklyanka.acceptance.header

import android.content.Context
import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransactor
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.MedicationListRules
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.ReportComposition
import ru.stohastika.sklyanka.feature.history.ReportUi
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_PROFILE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHEET
import ru.stohastika.sklyanka.feature.meds.form.FormInputs
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormRoute
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormViewModel
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.list.MedicationCardUi
import ru.stohastika.sklyanka.feature.meds.list.MedicationListScreen
import ru.stohastika.sklyanka.feature.meds.list.MedsTab
import ru.stohastika.sklyanka.feature.meds.list.MedsUiState
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckSheet
import ru.stohastika.sklyanka.ui.component.LocalScreenTopInset
import ru.stohastika.sklyanka.ui.component.TAG_COMPACT_TITLE
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_CONFIRM
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_DIALOG
import ru.stohastika.sklyanka.ui.component.TAG_PINNED_HEADER_BAR
import ru.stohastika.sklyanka.ui.component.TAG_QUIET_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import ru.stohastika.sklyanka.feature.history.R as HistoryR
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-137: форма лекарства без действия в шапке, крупный заголовок,
 * системный возврат и потолок верхнего поля шапки.
 *
 * Живут в `app`, потому что сценарии говорят сразу о нескольких поверхностях разных модулей
 * (форма лекарства, список, шторка проверки доставки, шторка отчёта врачу): единственное
 * место, где всё это существует одновременно, — `app` (`SKL-26/TDD.md §4.3`).
 *
 * Стенд пересобирает композицию на каждое утверждение: сессия Compose живёт внутри блока,
 * а шаги Gherkin — отдельные вызовы. Поэтому «Когда» запоминает наблюдение, а «Тогда»
 * собирает экран и спрашивает у него.
 *
 * **Верхний инсет подставляется, а не берётся у платформы.** На стенде его нет, и правило
 * «инсет + поле шапки ≤ 56 dp» (§4, Р-54) проверяется подставленным значением через
 * [LocalScreenTopInset] — тем же локом, который сообщает обёртка [ProfileChrome]. Тогда
 * замер «от верхней грани до заголовка» идёт по кадру стенда, а не по устройству.
 *
 * **Системный «Назад» нажимается диспетчером.** Форма своего обработчика не заводит:
 * закрытие даёт обработчик раздела (`MedsSection`). Стенд ставит его плейсхолдер снаружи
 * формы и раньше неё, и перехвати форма возврат — флаг остался бы `false` (образец —
 * `Sk101ReportSheetScreenSteps`).
 *
 * Чего стенд не видит и почему это записано, а не обойдено:
 * - **«действия в шапке нет»** проверяется отсутствием узлов действия (тихих кнопок
 *   и шеврона) в границах шапки: невидимой заглушки у правила нет, и отсутствие действия —
 *   это пустое место, а не погашенный узел;
 * - **крупный кегль** проверяется разрешённым стилем подписи: размер и начертание
 *   `TitleScreen` видны в текстовой раскладке, а «крупный на глаз» — нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk137HeaderSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Верхний инсет, названный Предысторией сценария; null — платформенный. */
    private var inset: Dp? = null

    /** Форма закрылась системным «Назад» без сохранения. */
    private var closedWithoutSaving = false

    /** Форма открыта сейчас: состояние маршрута, которое переживает шаг. */
    private var formOpen = false

    /** Название, введённое в форму: по нему проверяется след в списке. */
    private var draftName: String = ""

    /** Маршрут спросил подтверждение сброса перед закрытием формы (Р-87 п. 1, RT-1). */
    private var dialogAskedConfirmation = false

    /** Детерминированные, но разные идентификаторы сохранений (приём `AcceptanceWorld.save`). */
    private var savedForms = 0

    /** Шторка проверки доставки закрылась системным «Назад». */
    private var sheetClosedByBack = false
    private var checkStarted = false

    /** Черновик формы: заполняется Предысторией, читается собранным экраном. */
    private var draft: MedFormUiState? = null

    // =======================================================================================
    // 1. Шапка формы: крупный заголовок, без действия (Р-54)
    // =======================================================================================

    @Тогда("^заголовок формы набран крупным заголовком экрана$")
    fun theFormTitleIsTheLargeScreenTitle() {
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            waitForIdle()
            val style = styleOf(formTitle())
            assertEquals(
                "the form title must carry the large title weight (Literata 600, §7)",
                SklyankaType.TitleScreen.fontWeight,
                style.fontWeight,
            )
            assertEquals(
                "the form title must carry the large title size (30 sp, §7)",
                SklyankaType.TitleScreen.fontSize.value,
                style.fontSize.value,
                FONT_TOLERANCE,
            )
        }
    }

    @Тогда("^в шапке нет ни действия, ни заглушки$")
    fun theHeaderHasNoAction() {
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            waitForIdle()
            val header = onNodeWithTag(TAG_SCREEN_HEADER, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the form header must carry no action: ${actionsIn(header)}",
                actionsIn(header).isEmpty(),
            )
        }
    }

    @Но("^слова «Отмена» на экране нет$")
    fun theFormKeepsNoCancelWord() {
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            waitForIdle()
            onAllNodesWithText(string(UiR.string.action_cancel), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // =======================================================================================
    // 2. Выход из формы: системный «Назад» (Р-54) и подтверждение сброса (Р-87 п. 1)
    // =======================================================================================

    @Допустим("^человек заполнил название «(.+?)» и форму выпуска «(.+?)»$")
    fun thePersonHasFilledInTheNameAndForm(name: String, form: String) {
        draftName = name
        formOpen = true
        draft = fill(name = name, form = formOf(form))
    }

    @Когда("^человек нажимает системную «Назад» на форме$")
    fun thePersonPressesSystemBackOnTheForm() {
        pressSystemBackOnTheRoute(confirmDiscard = false)
    }

    @Когда("^человек подтверждает сброс на форме$")
    fun thePersonConfirmsTheDiscardOnTheForm() {
        pressSystemBackOnTheRoute(confirmDiscard = true)
        formOpen = false
    }

    @Тогда("^форма спрашивает подтверждение сброса$")
    fun theFormAsksForDiscardConfirmation() {
        assertTrue(
            "the system back on a dirty form must ask for confirmation (RT-1, Р-87 п. 1)",
            dialogAskedConfirmation,
        )
    }

    /**
     * Системная «Назад» нажимается на настоящем маршруте (Р-87 п. 1): перехват грязного
     * черновика и диалог подтверждения — код продукта `MedicationFormRoute`, а стенд даёт
     * лишь владельца диспетчера и записывает наблюдения. Композиция собирается заново на
     * каждое действие (конвенция этого класса), поэтому заполненное Предысторией шаг
     * возвращает на форму сам: название вводом в поле, форму выпуска — намерением модели.
     */
    private fun pressSystemBackOnTheRoute(confirmDiscard: Boolean) {
        val owner = RecordingBackOwner()
        val model = formModel()
        closedWithoutSaving = false
        dialogAskedConfirmation = false
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                    Stand {
                        // Плейсхолдер обработчика раздела: он снаружи маршрута и раньше него,
                        // поэтому форму без перехвата (чистый черновик) закрывает раздел (Р-54).
                        BackHandler(enabled = true) { closedWithoutSaving = true }
                        MedicationFormRoute(
                            viewModel = model,
                            medicationId = null,
                            onDone = { closedWithoutSaving = true },
                            onAbandon = { closedWithoutSaving = true },
                        )
                    }
                }
            }
            settleUntilForm()
            if (draftName.isNotEmpty()) {
                onNode(
                    hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_NAME_FIELD)),
                    useUnmergedTree = true,
                ).performTextInput(draftName)
                settle()
                draft?.let { model.onIntent(MedFormIntent.ReleaseFormChanged(it.draft.form)) }
                settle()
            }
            owner.onBackPressedDispatcher.onBackPressed()
            settle()
            dialogAskedConfirmation = onAllNodesWithTag(TAG_CONFIRM_DIALOG).fetchSemanticsNodes().isNotEmpty()
            if (dialogAskedConfirmation && confirmDiscard) {
                onNodeWithTag(TAG_CONFIRM_CONFIRM).performClick()
                settle()
                dialogAskedConfirmation = onAllNodesWithTag(TAG_CONFIRM_DIALOG).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /** Настоящая модель формы на хранилищах мира сценария (образец — `AcceptanceWorld.save`). */
    private fun formModel(): MedicationFormViewModel = MedicationFormViewModel(
        activeProfile = world.profileSession.activeStore,
        medications = world.repositories.medicationStore,
        saveMedication = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = world.repositories.medicationStore,
            planner = world.planner(),
            transactor = ScenarioTransactor(world.intakes, world.medications),
            log = world.log,
            newId = { "medication-sk137-${++savedForms}" },
        ),
        time = world.clock.time,
        proStatus = ObserveProStatusUseCase(StandProCache, world.clock.time),
        analytics = world.analytics.recorder(CoroutineScope(Dispatchers.Unconfined)),
    )

    /** Прогнать очередь главного looper'а и кадры композиции: маршрут открывает форму корутиной. */
    private fun ComposeUiTest.settle() {
        shadowOf(Looper.getMainLooper()).idle()
        waitForIdle()
    }

    private fun ComposeUiTest.settleUntilForm() {
        repeat(SETTLE_ROUNDS) { settle() }
        assertTrue(
            "the form must be opened by the route",
            onAllNodesWithTag(TAG_MEDICATION_FORM).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Тогда("^форма закрывается и открывается список лекарств$")
    fun theFormClosesAndTheListOpens() {
        assertTrue("the system back must close the form", closedWithoutSaving)
        formOpen = false
        runComposeUiTest {
            setContent { Stand { MedicationListScreen(state = listState(), onIntent = {}) } }
            waitForIdle()
            assertTrue(
                "the medication list must open after the form: ${visibleTexts()}",
                string(MedsR.string.meds_title) in visibleTexts(),
            )
        }
    }

    @И("^лекарство «(.+?)» не появляется в списке$")
    fun theMedicationDoesNotAppearInTheList(name: String) {
        assertTrue(
            "«$name» must not reach the world: ${world.medications.names()}",
            world.medications.all().none { it.name == name },
        )
        runComposeUiTest {
            setContent { Stand { MedicationListScreen(state = listState(), onIntent = {}) } }
            waitForIdle()
            onAllNodesWithTag(TAG_MEDICATION_CARD, useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithText(name, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^форма закрывается без сохранения$")
    fun theFormClosesWithoutSaving() {
        assertFalse("the system back must close the form", formOpen)
        assertTrue(
            "nothing may be saved: ${world.medications.names()}",
            draftName.isEmpty() || world.medications.all().none { it.name == draftName },
        )
    }

    @Когда("^человек снова нажимает «Добавить лекарство»$")
    fun thePersonPressesAddAgain() {
        draft = fill(name = "", form = MedicationForm.TABLET)
        draftName = ""
        formOpen = true
    }

    @Тогда("^открывается форма нового лекарства$")
    fun theNewMedicationFormOpens() {
        assertTrue("the form must be open", formOpen)
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            waitForIdle()
            onNodeWithTag(TAG_NAME_FIELD, useUnmergedTree = true).assertIsDisplayed()
            assertEquals("the new form must start empty", "", enteredName())
        }
    }

    @И("^форма остаётся открытой$")
    fun theFormStaysOpen() {
        assertTrue("the form must stay open after the frame settles", formOpen)
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            waitForIdle()
            onNodeWithTag(TAG_MEDICATION_FORM, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    // =======================================================================================
    // 3. Прокрутка и верхнее поле (Р-54, §7)
    // =======================================================================================

    @Тогда("^заголовка крупным кеглем на экране нет$")
    fun theLargeTitleLeftTheScreen() {
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            val bar = scrollToEnd()
            val visible = titledNodes(formTitle())
                .filterNot { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_COMPACT_TITLE }
                .filter { it.boundsInRoot.bottom > bar.top + LINE_TOLERANCE }
            assertTrue(
                "the large title must leave the screen, but stays below the pinned row $bar: " +
                    "${visible.map { it.boundsInRoot }}",
                visible.isEmpty(),
            )
        }
    }

    @И("^шеврона возврата в закреплённом ряду нет$")
    fun thePinnedRowCarriesNoChevron() {
        runComposeUiTest {
            setContent { Stand { formScreen() } }
            val bar = scrollToEnd()
            val chevrons = onAllNodesWithContentDescription(string(UiR.string.cd_back), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { bar.contains(it.boundsInRoot.center) }
            assertTrue(
                "the pinned row $bar must carry no chevron: ${chevrons.map {
                    it.boundsInRoot
                }}",
                chevrons.isEmpty(),
            )
        }
    }

    @Допустим("^у экрана верхний инсет (.+) dp$")
    fun theScreenHasThatTopInset(value: String) {
        inset = value.replace(',', '.').toFloat().dp
    }

    @Тогда("^от верхней грани экрана до заголовка не больше (\\d+) dp$")
    fun theTitleStandsNoLowerThan(limit: Int) {
        val measured = titleTopDp()
        assertTrue(
            "the sum «inset + header padding» must stay within $limit dp but is $measured dp " +
                "(inset $inset)",
            measured <= limit + TOLERANCE,
        )
    }

    @Тогда("^от верхней грани экрана до заголовка (\\d+) dp$")
    fun theTitleStandsExactlyAt(expected: Int) {
        val measured = titleTopDp()
        assertEquals(
            "24 dp of the status bar plus 30 dp of the header padding is the mock-up height " +
                "(§4, Р-54): measured $measured dp",
            expected.toFloat(),
            measured,
            TOLERANCE,
        )
    }

    // =======================================================================================
    // 4. Шторки (Р-54)
    // =======================================================================================

    @Тогда("^действия в шапке шторки нет$")
    fun theSheetHeaderHasNoAction() {
        runComposeUiTest {
            setContent { Stand { DeliveryCheckSheet(onStart = { checkStarted = true }, onDismiss = {}) } }
            waitForIdle()
            val header = onNodeWithTag(TAG_SCREEN_HEADER, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the sheet header must carry no action: ${actionsIn(header)}",
                actionsIn(header).isEmpty(),
            )
        }
    }

    @Когда("^человек нажимает системную «Назад» в шторке$")
    fun thePersonPressesSystemBackInTheSheet() {
        val owner = RecordingBackOwner()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                    Stand {
                        DeliveryCheckSheet(
                            onStart = { checkStarted = true },
                            // Обработчик возврата — собственный `BackHandler` шторки (Р-54).
                            onDismiss = { sheetClosedByBack = true },
                        )
                    }
                }
            }
            waitForIdle()
            owner.onBackPressedDispatcher.onBackPressed()
            waitForIdle()
        }
    }

    @Тогда("^шторка закрыта системным «Назад»$")
    fun theSheetIsClosedByBack() {
        assertTrue("the system back must close the sheet (Р-54)", sheetClosedByBack)
    }

    @И("^проверка доставки не начата$")
    fun theDeliveryCheckIsNotStarted() {
        assertFalse("closing the sheet must start nothing", checkStarted)
    }

    @Тогда("^ведущее действие шапки шторки — отмена отправки$")
    fun theSheetLeadsWithTheCancel() {
        runComposeUiTest {
            setContent { Stand { reportSheet() } }
            waitForIdle()
            // Экран «История» под шторкой носит ту же подпись «Отчёт врачу» (действие,
            // открывающее отчёт), и по всему дереву узлов два: берётся шторка.
            val sheet = onNodeWithTag(TAG_REPORT_SHEET, useUnmergedTree = true).fetchSemanticsNode()
            val cancel = inSheet(sheet, string(UiR.string.action_cancel)).single()
            val title = inSheet(sheet, reportTitle()).single()
            val composition = onNodeWithTag(TAG_REPORT_PROFILE, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the cancel must stand in the sheet header, above the composition " +
                    "${composition.boundsInRoot}, but is at ${cancel.boundsInRoot}",
                cancel.boundsInRoot.bottom <= composition.boundsInRoot.top + LINE_TOLERANCE,
            )
            assertTrue(
                "the cancel must lead the title: cancel ${cancel.boundsInRoot.left}, title ${title.boundsInRoot.left}",
                cancel.boundsInRoot.left < title.boundsInRoot.left,
            )
        }
    }

    // =======================================================================================
    // Наблюдения и стенд
    // =======================================================================================

    /** Черновик формы: заполняется Предысторией, читается собранным экраном. */
    private fun state(): MedFormUiState = draft ?: fill(name = "", form = MedicationForm.TABLET)

    private fun formTitle(): String = string(MedsR.string.form_title_new)

    private fun reportTitle(): String = string(HistoryR.string.history_report_title)

    /** Стиль подписи из разрешённой раскладки: по нему видно крупный кегль (§6.3, §7). */
    private fun ComposeUiTest.styleOf(text: String): TextStyle {
        val node = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        fun collect(current: SemanticsNode) {
            current.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            current.children.forEach(::collect)
        }
        collect(node)
        return requireNotNull(layouts.firstOrNull()) { "no text layout under the title" }.layoutInput.style
    }

    private fun ComposeUiTest.actionsIn(header: SemanticsNode): List<Rect> {
        val quiet = onAllNodesWithTag(TAG_QUIET_BUTTON, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        val chevrons = onAllNodesWithContentDescription(string(UiR.string.cd_back), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        return (quiet + chevrons).filter { header.boundsInRoot.contains(it.center) }
    }

    private fun ComposeUiTest.titledNodes(title: String): List<SemanticsNode> =
        onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes()

    /** Текстовые узлы внутри шторки: под ней стоит тот же экран целиком. */
    private fun ComposeUiTest.inSheet(sheet: SemanticsNode, text: String): List<SemanticsNode> =
        nodesUnder(sheet).filter { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }

    /**
     * Узлы поддерева, а не по границам: действие «Отчёт врачу» переехало в конец
     * содержимого «Истории» (Р-88) и лежит под шторкой — по границам его подпись
     * читалась бы как вторая подпись шторки.
     */
    private fun nodesUnder(node: SemanticsNode): List<SemanticsNode> =
        listOf(node) + node.children.flatMap(::nodesUnder)

    private fun ComposeUiTest.visibleTexts(): List<String> =
        onAllNodesWithTag(TAG_SCREEN_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> textsOf(node) }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    /** Введённое название: значение лежит у строки ввода внутри поля, а не у самого поля. */
    private fun ComposeUiTest.enteredName(): String =
        onNodeWithTag(TAG_NAME_FIELD, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /**
     * Прокрутка формы до конца: над прокруткой стоит закреплённый ряд, и крупный заголовок
     * ушёл с экрана. Возвращается полоса ряда в координатах корня.
     */
    private fun ComposeUiTest.scrollToEnd(): Rect {
        repeat(SCROLL_SWIPES) {
            onAllNodes(hasScrollAction(), useUnmergedTree = true).onFirst().performTouchInput { swipeUp() }
            waitForIdle()
        }
        val rows = onAllNodesWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true).fetchSemanticsNodes()
        onAllNodesWithTag(TAG_COMPACT_TITLE, useUnmergedTree = true).assertCountEquals(1)
        return rows.map { it.boundsInRoot }.maxByOrNull { it.height }
            ?: throw AssertionError("the form must pin its header when scrolled to the end")
    }

    /** Высота «от верхней грани экрана до заголовка» в подставленном инсете (§4, Р-54). */
    private fun titleTopDp(): Float {
        var measured = 0f
        runComposeUiTest {
            setContent {
                Stand(topInset = inset) {
                    // Обёртка профиля — та же, что в продукте: она резервирует полосу под
                    // системной панелью и сообщает инсет шапке одним и тем же значением.
                    ProfileChrome(ProfileChromeState.Owner) { formScreen() }
                }
            }
            waitForIdle()
            val title = onNodeWithText(formTitle(), useUnmergedTree = true).fetchSemanticsNode()
            measured = with(density) { title.boundsInRoot.top.toDp().value }
        }
        return measured
    }

    private fun fill(name: String, form: MedicationForm): MedFormUiState {
        val draft = MedicationDraft(
            id = null,
            profileId = world.profiles.get(world.profiles.active).id,
            name = name,
            form = form,
            times = listOf(LocalTime.of(8, 0)),
            startDate = world.clock.today(),
        )
        return MedFormUiState.of(draft.normalized(), FormMode.Create, FormInputs())
    }

    private fun formOf(label: String): MedicationForm =
        MedicationForm.entries.firstOrNull { string(formLabel(it)) == label }
            ?: error("the scenario names a form the stand does not know: $label")

    private fun formLabel(form: MedicationForm): Int = when (form) {
        MedicationForm.TABLET -> MedsR.string.med_form_tablet
        MedicationForm.CAPSULE -> MedsR.string.med_form_capsule
        MedicationForm.SPRAY -> MedsR.string.med_form_spray
        MedicationForm.SYRUP -> MedsR.string.med_form_syrup
        MedicationForm.DROPS -> MedsR.string.med_form_drops
        MedicationForm.INJECTION -> MedsR.string.med_form_injection
    }

    /** Список лекарств из мира сценария: тот же состав и те же правила, что показал бы продукт. */
    private fun listState(): MedsUiState {
        val active = world.medications.all().filter { it.archivedAt == null }
        return MedsUiState(
            tab = MedsTab.ACTIVE,
            activeCount = MedicationListRules.activeCount(active),
            items = active.map(::cardOf),
            loading = false,
        )
    }

    private fun cardOf(medication: Medication): MedicationCardUi = MedicationCardUi(
        medicationId = medication.id,
        title = medication.name,
        form = medication.form,
        shape = medication.shape,
        strength = medication.strength?.stripTrailingZeros()?.toPlainString(),
        strengthUnit = medication.strengthUnit,
        timesPerDay = medication.times.size,
        times = medication.times,
        trailingLine = MedicationListRules.trailingLineOf(medication),
        asNeeded = null,
    )

    /** Шторка отчёта врачу до отправки: поверхность, которая после Р-54 носит действие шапки. */
    @Composable
    private fun reportSheet() {
        val month = YearMonth.from(world.clock.today())
        val profile = world.profiles.get(world.profiles.active)
        HistoryScreen(
            state = HistoryUiState(
                month = month,
                period = AdherencePeriod.empty(profile.id, month.atDay(1), month.atEndOfMonth()),
                report = ReportUi(
                    document = ReportDocument(
                        fileName = "sklyanka-report-2026-09.pdf",
                        mediaType = "application/pdf",
                        bytes = ByteArray(0),
                    ),
                    composition = ReportComposition(
                        profile = profile.name,
                        period = REPORT_PERIOD,
                        from = REPORT_PERIOD,
                        to = REPORT_PERIOD,
                        taken = REPORT_TAKEN,
                        format = "PDF",
                    ),
                    preset = ReportPreset.THIS_MONTH,
                    customFrom = month.atDay(1),
                    customTo = month.atEndOfMonth(),
                    today = world.clock.today(),
                ),
                reportOutcome = null,
            ),
            onIntent = {},
        )
    }

    @Composable
    private fun formScreen() {
        MedicationFormScreen(
            state = state(),
            onIntent = {},
            onPickDate = {},
            onPickTime = { _, _ -> },
        )
    }

    private fun string(id: Int, locale: Locale = RUSSIAN): String = context.withInterfaceLocale(locale).getString(id)

    /**
     * Стенд: язык интерфейса сценария и оформление профиля владельца.
     *
     * Язык закреплён, потому что сценарии записаны по-русски, а телефон стенда мог быть
     * любым (НФТ-43). Подставленный инсет уезжает в [LocalScreenTopInset] — его читает
     * обёртка профиля и шапка.
     */
    @Composable
    private fun Stand(topInset: Dp? = null, content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale(RUSSIAN)
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            if (topInset == null) {
                SklyankaTheme { content() }
            } else {
                CompositionLocalProvider(LocalScreenTopInset provides topInset) {
                    SklyankaTheme { content() }
                }
            }
        }
    }

    private companion object {
        /** Допуск в dp: единичная плотность стенда и округление замера по кадру. */
        const val TOLERANCE = 0.5f

        /** Допуск по линии и полю в пикселях единичной плотности стенда. */
        const val LINE_TOLERANCE = 1f

        const val FONT_TOLERANCE = 0.01f

        const val SCROLL_SWIPES = 12

        /** Раундов осаждения достаточно, чтобы корутина открытия формы маршрутом завершилась. */
        const val SETTLE_ROUNDS = 3

        /** Тексты состава отчёта: стенд говорит о слотах шапки, а не о содержимом документа. */
        const val REPORT_PERIOD = "1 - 30 September 2026"
        const val REPORT_TAKEN = "10 / 10"

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}

/**
 * Владелец диспетчера системной «Назад» без Activity (Р-54, образец —
 * `Sk101ReportSheetScreenSteps`).
 *
 * Системный «Назад» нажимается через настоящий диспетчер: обработчик возврата приходит
 * из продукта (раздел для формы, собственная шторка для доставки), а стенд даёт лишь
 * владельца. Владельцем жизненного цикла он же и является: обработчик привязывается
 * к нему и обязан быть в состоянии `RESUMED`, иначе он выключен.
 */
private class RecordingBackOwner :
    OnBackPressedDispatcherOwner,
    LifecycleOwner {

    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }
}

/**
 * Доступ «Про» на стенде: продукт бесплатен (Р-86), и гейт состава в сценариях выхода
 * не участвует. Кэш держится у шагов, а не берётся из сборки: `testDebug` компилируется
 * для обоих флаворов, а `FreeProSubscriptionCache` живёт только в `free`.
 */
private object StandProCache : ProSubscriptionCache {

    override suspend fun read(): ProSubscription = ProSubscription.Active(productId = null, until = LocalDate.MAX)

    override suspend fun write(subscription: ProSubscription) = Unit
}
