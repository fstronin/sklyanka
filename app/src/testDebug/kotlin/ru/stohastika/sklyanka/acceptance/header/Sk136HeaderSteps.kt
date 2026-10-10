package ru.stohastika.sklyanka.acceptance.header

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherencePeriod
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.ReportComposition
import ru.stohastika.sklyanka.feature.history.ReportOutcome
import ru.stohastika.sklyanka.feature.history.ReportUi
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHARE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHEET
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SUCCESS
import ru.stohastika.sklyanka.feature.meds.form.FormInputs
import ru.stohastika.sklyanka.feature.meds.form.FormMode
import ru.stohastika.sklyanka.feature.meds.form.MedFormUiState
import ru.stohastika.sklyanka.feature.meds.form.MedicationFormScreen
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckSheet
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_SHEET
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_START
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog
import ru.stohastika.sklyanka.ui.component.TAG_COMPACT_TITLE
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import ru.stohastika.sklyanka.ui.component.TAG_PINNED_HEADER_BAR
import ru.stohastika.sklyanka.ui.component.TAG_QUIET_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.profile.ProfileChrome
import ru.stohastika.sklyanka.ui.profile.ProfileChromeState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.lightColors
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import ru.stohastika.sklyanka.feature.history.R as HistoryR
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки SKL-136 о сетке слотов модальной шапки и её закреплении.
 *
 * Живут в `app`, потому что сценарии говорят сразу о нескольких поверхностях разных
 * модулей (форма лекарства, шторка проверки доставки, шторка отчёта врачу) и о диалоге
 * времени: единственное место, где всё это существует одновременно, — `app`
 * (`SKL-26/TDD.md §4.3`).
 *
 * **Р-54 отменил часть Р-53.** Обязательного слова «Отмена» у модальной шапки экрана
 * больше нет: форма лекарства стала вложенной шапкой без возврата (SKL-137), шторка
 * проверки доставки вышла без действия, и сетку слотов с типографикой теперь носит
 * поверхность, у которой действие есть — шторка отчёта врачу. Утверждения задачи
 * перевешены на неё, правило §7 осталось тем же; закрепление шапки формы проверяется
 * на новой форме (ряд держит компактное название, действия в нём нет).
 *
 * Стенд пересобирает композицию на каждое утверждение: сессия Compose живёт внутри блока,
 * а шаги Gherkin — отдельные вызовы, и держать блок открытым между ними нечем (приём
 * `ProfileIndicationSteps`). Поэтому «Когда» запоминает наблюдение, а «Тогда» собирает
 * экран и спрашивает у него.
 *
 * Предыстория сценариев («активен профиль», «человек открыл форму нового лекарства»,
 * «сейчас …», «человек открывает диалог времени приёма») — общий словарь приёмки, а не этот
 * файл: строки уже объявлены однажды и повторять их нельзя (`StepDictionaryRulesTest`).
 *
 * Чего стенд не видит и почему это записано, а не обойдено:
 * - **чернила слова против узла цели нажатия.** `clickable` в Compose сливает потомков в узел
 *   кнопки, и в объединённом дереве «Отмена» — узел 44 dp, а не слово. Там, где проверяются
 *   линия и поле слова, берётся необъединённое дерево (`useUnmergedTree`); там, где важно
 *   само действие, проверка идёт по нему же — действие и его подпись здесь одно;
 * - **подкраска шапки и нижняя линия** нарисованы фоном и семантики не несут; проверяется
 *   то, что подкраска не снята (роль темы отличается от фона), и что полоса ряда на месте.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class Sk136HeaderSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // =======================================================================================
    // 1. Строка модальной шапки: одна линия, одно поле (Р-53, Р-54)
    // =======================================================================================

    @Тогда("^слово «Отмена» и название шторки стоят на одной горизонтальной линии$")
    fun theCancelAndTheSheetTitleShareOneLine() {
        runComposeUiTest {
            setContent { Stand { reportSheet() } }
            waitForIdle()
            // Экран «История» под шторкой носит ту же подпись «Отчёт врачу» (действие,
            // открывающее отчёт), и по всему дереву узлов два: берётся шторка.
            val sheet = sheetOf()
            val cancel = inSheet(sheet, cancelWord()).single()
            val title = inSheet(sheet, reportTitle()).single()
            val lineShift = abs(cancel.boundsInRoot.center.y - title.boundsInRoot.center.y)
            assertTrue(
                "the cancel word and the title must stand on one horizontal line, " +
                    "but their centres differ by $lineShift px",
                lineShift <= LINE_TOLERANCE,
            )
            assertTrue(
                "the cancel word must stand left of the title",
                cancel.boundsInRoot.left < title.boundsInRoot.left,
            )
        }
    }

    /**
     * Поле слова — то же, где начинается содержимое модальной поверхности.
     *
     * У шторки своего тега шапки нет (её строку рисует `ModalHeaderRow`): поле берётся
     * от левого края шторки плюс боковое поле §4. Второй ориентир — блок действий: он стоит
     * прямо в полотне шторки и начинается на том же боковом поле. Карточка состава для этого
     * не годится: у неё своё поле, и её строка начинается глубже полотна на 16 dp.
     */
    @И("^слово «Отмена» стоит на левом поле шапки — там же, где начинается содержимое шторки$")
    fun theCancelStandsOnTheSideField() {
        runComposeUiTest {
            setContent { Stand { reportSheet() } }
            waitForIdle()
            val sheet = sheetOf()
            val cancel = inSheet(sheet, cancelWord()).single()
            val content = onNodeWithTag(TAG_REPORT_SHARE, useUnmergedTree = true).fetchSemanticsNode()
            val field = sheet.boundsInRoot.left + sideFieldPx()
            assertTrue(
                "the cancel word must start on the sheet's side field at $field px, " +
                    "but starts at ${cancel.boundsInRoot.left} px",
                abs(cancel.boundsInRoot.left - field) <= LINE_TOLERANCE,
            )
            assertTrue(
                "the sheet content must start on the same field: the actions block is at " +
                    "${content.boundsInRoot.left} px, the word at ${cancel.boundsInRoot.left} px",
                abs(content.boundsInRoot.left - cancel.boundsInRoot.left) <= LINE_TOLERANCE,
            )
        }
    }

    // =======================================================================================
    // 2. Пустой слот не заполняется ничем (Р-53, V-15)
    // =======================================================================================

    /**
     * Р-54 отменил часть Р-53: обязательного слова «Отмена» у модальной шапки экрана
     * больше нет, форма лекарства вышла из вида `Modal`, и сетку слотов теперь носит
     * шторка отчёта врачу — поверхность, у которой действие есть. Утверждение перевешено
     * на неё; само правило (пустой слот не заполняется ничем) не изменилось.
     */
    @Тогда("^справа от названия в шапке нет ни действия, ни заглушки$")
    fun theModalSlotRightOfTheTitleIsEmpty() {
        runComposeUiTest {
            setContent { Stand { reportSheet() } }
            waitForIdle()
            val sheet = sheetOf()
            // Тихая кнопка в шторке одна — ведущая «Отмена»: заглушки в замыкающем слоте нет.
            val inHeader = quietButtons().filter { sheet.boundsInRoot.contains(it.boundsInRoot.center) }
            assertEquals("the modal header must keep exactly the leading action", 1, inHeader.size)
            val title = inSheet(sheet, reportTitle()).single()
            assertTrue(
                "the leading action must stand left of the title: action ${inHeader.single().boundsInRoot}, " +
                    "title ${title.boundsInRoot}",
                inHeader.single().boundsInRoot.left <= title.boundsInRoot.left,
            )
            // Название тянется до правого поля: за ним ничего не стоит.
            val rightField = sheet.boundsInRoot.right - sideFieldPx()
            assertTrue(
                "the title must reach the right field at $rightField px, " +
                    "but ends at ${title.boundsInRoot.right} px",
                abs(title.boundsInRoot.right - rightField) <= LINE_TOLERANCE,
            )
        }
    }

    // =======================================================================================
    // 3. Закрепление шапки формы без действия (Р-53, Р-54, §7)
    // =======================================================================================

    @Когда("^человек прокручивает содержимое формы до конца$")
    fun thePersonScrollsTheFormToTheEnd() {
        // Прокрутка выполняется в «Тогда»: сессия композиции между шагами не живёт.
    }

    /**
     * Р-54: в закреплённом ряду формы нет ни возврата, ни действия.
     *
     * У формы нет возврата (выход — системный «Назад») и нет действия в шапке (сохранение —
     * нижняя кнопка, Д-3), поэтому ведущему слоту ряда нечего показывать. Раньше на любом
     * уровне прокрутки в ряду стояло слово «Отмена»; утверждение изменилось вместе с решением.
     */
    @И("^действия в закреплённом ряду формы нет$")
    fun thePinnedRowCarriesNoAction() {
        runComposeUiTest {
            setContent { Stand { form() } }
            val bar = scrollFormUntilPinned()
            val quiet = quietButtons().filter { bar.contains(it.boundsInRoot.center) }
            val chevrons = onAllNodesWithContentDescription(backLabel(), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { bar.contains(it.boundsInRoot.center) }
            assertTrue(
                "the pinned row $bar must carry no action: quiet=$quiet, chevrons=${chevrons.map { it.boundsInRoot }}",
                quiet.isEmpty() && chevrons.isEmpty(),
            )
        }
    }

    @И("^над прокруткой стоит закреплённый ряд с названием формы$")
    fun thePinnedRowCarriesTheFormTitle() {
        runComposeUiTest {
            setContent { Stand { form() } }
            val bar = scrollFormUntilPinned()
            val title = onNodeWithTag(TAG_COMPACT_TITLE, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the compact title must stand inside the pinned row $bar, but is at ${title.boundsInRoot}",
                bar.contains(title.boundsInRoot.center),
            )
            val texts = title.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            assertEquals(
                "the pinned row must carry the form title",
                formTitle(),
                texts.joinToString(" ").trim(),
            )
        }
    }

    @Тогда("^подкраска шапки и баннер о том, что это не ваши приёмы, остались$")
    fun theForeignProfileSignsStay() {
        var tinted = false
        val banner = context.withInterfaceLocale(RUSSIAN).getString(UiR.string.profile_banner, FOREIGN.name)
        runComposeUiTest {
            setContent {
                Stand(chrome = foreignChrome()) {
                    val colors = SklyankaTheme.colors
                    tinted = colors.profileHeader != colors.background
                    form()
                }
            }
            val bar = scrollFormUntilPinned()
            assertTrue("a foreign profile must tint the header (ПРФ-11)", tinted)
            // Баннер чужого профиля несворачиваем (ПРФ-13): после порога он стоит ниже полосы
            // ряда и остаётся на экране — иначе индикация снималась бы ради чистоты экрана.
            val node = onNodeWithText(banner, useUnmergedTree = true)
            node.assertExists()
            node.assertIsDisplayed()
            assertTrue("the pinned row must keep its band $bar", bar.height > 0f)
        }
    }

    // =======================================================================================
    // 4. Шторка проверки доставки: ни действия в шапке, ни отмены в теле (Р-53, Р-54)
    // =======================================================================================

    @Допустим("^человек открыл проверку доставки$")
    fun thePersonOpenedTheDeliveryCheck() {
        runComposeUiTest {
            setContent { Stand { deliveryCheckSheet() } }
            waitForIdle()
            onNodeWithTag(TAG_CHECK_SHEET, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    /**
     * Р-53: в теле шторки нет второго способа закрыть её.
     *
     * Раньше вторым способом было слово «Отмена» в шапке, и шаг проверял, что тело его
     * не повторяет. Р-54 снял слово и из шапки: выход дают системный «Назад» и подложка,
     * и в теле шторки остаётся одна кнопка запуска проверки.
     */
    @Но("^тихой «Отмены» в теле шторки нет$")
    fun theBodyHasNoSecondCancel() {
        runComposeUiTest {
            setContent { Stand { deliveryCheckSheet() } }
            waitForIdle()
            onAllNodesWithText(cancelWord(), useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag(TAG_CHECK_START, useUnmergedTree = true).assertExists()
        }
    }

    // =======================================================================================
    // 5. Шторка отчёта врачу: подтверждение в замыкающем слоте (Р-53, V-15)
    // =======================================================================================

    @Допустим("^человек открыл отчёт врачу$")
    fun thePersonOpenedTheDoctorReport() {
        runComposeUiTest {
            setContent { Stand { historyWithReport(outcome = null) } }
            waitForIdle()
            onNodeWithTag(TAG_REPORT_SHEET, useUnmergedTree = true).assertExists()
        }
    }

    @Когда("^отчёт отправлен$")
    fun theReportWasSent() {
        // Отправка — состояние содержимого шторки: разметка читается в «Тогда».
    }

    @Тогда("^подтверждение стоит в шапке шторки справа$")
    fun theConfirmationStandsOnTheRightOfTheHeader() {
        runComposeUiTest {
            setContent { Stand { historyWithReport(outcome = ReportOutcome.SHARED) } }
            waitForIdle()
            val sheet = onNodeWithTag(TAG_REPORT_SHEET, useUnmergedTree = true).fetchSemanticsNode()
            // Имя действия «Отчёт врачу» носит и кнопка на экране под шторкой: узлы берутся
            // по границам шторки, а не по всему дереву, иначе проверка нашла бы обе.
            val action = inSheet(sheet, doneWord()).single()
            val title = inSheet(sheet, reportTitle()).single()
            assertTrue(
                "the confirmation must stand right of the title: action ${action.boundsInRoot}, " +
                    "title ${title.boundsInRoot}",
                action.boundsInRoot.left >= title.boundsInRoot.right,
            )
            // Подтверждение — действие шапки: оно выше содержимого успеха, а не в нём.
            val success = onNodeWithTag(TAG_REPORT_SUCCESS, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "the confirmation must stand in the header, above the success content",
                action.boundsInRoot.bottom <= success.boundsInRoot.top + LINE_TOLERANCE,
            )
        }
    }

    /**
     * Заглушки справа от названия нет (V-15): в шапке на успехе ровно одно действие, а
     * название начинается с левого поля — ведущий слот пуст, и заглушки в нём тоже нет.
     */
    @Но("^невидимой заглушки справа от названия в шапке нет$")
    fun theInvisibleStubIsGone() {
        runComposeUiTest {
            setContent { Stand { historyWithReport(outcome = ReportOutcome.SHARED) } }
            waitForIdle()
            val sheet = onNodeWithTag(TAG_REPORT_SHEET, useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("the header must keep exactly one action", 1, inSheet(sheet, doneWord()).size)
            assertEquals("the success header must carry no cancel", 0, inSheet(sheet, cancelWord()).size)
            val title = inSheet(sheet, reportTitle()).single()
            val field = sheet.boundsInRoot.left + sideFieldPx()
            assertTrue(
                "the empty leading slot must stay empty: the title must start at $field px, " +
                    "but starts at ${title.boundsInRoot.left} px",
                abs(title.boundsInRoot.left - field) <= LINE_TOLERANCE,
            )
        }
    }

    // =======================================================================================
    // 6. Одна типографика на модальных поверхностях (ОВ-146, Р-53, Р-54)
    // =======================================================================================

    /**
     * Р-54 перевёл ведущее действие с формы на шторку отчёта врачу: у формы действия
     * в шапке больше нет, и слово «Отмена» носит та поверхность, у которой оно есть.
     * Начертание и цвет не изменились — одно на всех модальных поверхностях.
     */
    @Тогда("^ведущее действие шапки набрано 15/400 приглушённым цветом$")
    fun theHeaderActionKeepsTheMutedRole() {
        runComposeUiTest {
            setContent { Stand { reportSheet() } }
            waitForIdle()
            val style = styleOf(inSheet(sheetOf(), cancelWord()).single())
            assertEquals(SklyankaType.BodyDefault.fontWeight, style.fontWeight)
            assertEquals(SklyankaType.BodyDefault.fontSize.value, style.fontSize.value, FONT_TOLERANCE)
            assertEquals(lightColors().textMuted, style.color)
        }
    }

    @И("^название шапки набрано 15/600 основным цветом$")
    fun theHeaderTitleKeepsTheStrongRole() {
        runComposeUiTest {
            setContent { Stand { reportSheet() } }
            waitForIdle()
            val style = styleOf(inSheet(sheetOf(), reportTitle()).single())
            assertEquals(SklyankaType.BodyDefaultStrong.fontWeight, style.fontWeight)
            assertEquals(SklyankaType.BodyDefaultStrong.fontSize.value, style.fontSize.value, FONT_TOLERANCE)
            assertEquals(lightColors().text, style.color)
        }
    }

    // =======================================================================================
    // 7. Границы правила: диалог времени строки шапки не получает (Р-53)
    // =======================================================================================

    @Тогда("^«Отмена» и подтверждение диалога стоят в кнопочном ряду$")
    fun theDialogKeepsItsButtonRow() {
        runComposeUiTest {
            setContent { Stand { timeDialog() } }
            waitForIdle()
            val cancel = onNodeWithText(cancelWord(), useUnmergedTree = true).fetchSemanticsNode()
            val done = onNodeWithText(doneWord(), useUnmergedTree = true).fetchSemanticsNode()
            val dialog = onNodeWithTag(TAG_DATE_TIME_PICKER, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "both dialog actions must stand inside the dialog ${dialog.boundsInRoot}",
                dialog.boundsInRoot.contains(cancel.boundsInRoot.center) &&
                    dialog.boundsInRoot.contains(done.boundsInRoot.center),
            )
            assertTrue(
                "the dialog actions must share one button row: " +
                    "${cancel.boundsInRoot} vs ${done.boundsInRoot}",
                abs(cancel.boundsInRoot.center.y - done.boundsInRoot.center.y) <= LINE_TOLERANCE,
            )
        }
    }

    @Но("^строки шапки с названием в диалоге нет$")
    fun theDialogHasNoHeaderRow() {
        runComposeUiTest {
            setContent { Stand { timeDialog() } }
            waitForIdle()
            onAllNodesWithTag(TAG_SCREEN_HEADER, useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // =======================================================================================
    // Наблюдения и стенд
    // =======================================================================================

    /** Отмена словом берётся из ресурса: слово интерфейса задачей не меняется. */
    private fun cancelWord(): String = string(UiR.string.action_cancel)

    private fun doneWord(): String = string(UiR.string.action_done)

    private fun formTitle(): String = string(MedsR.string.form_title_new)

    private fun reportTitle(): String = string(HistoryR.string.history_report_title)

    private fun backLabel(): String = string(UiR.string.cd_back)

    /**
     * Прокрутка формы до порога: ряд обязан показать компактное название внутри своих границ.
     *
     * Порог — геометрия (§7), поэтому шаг прокручивает до наблюдаемого состояния, а не
     * «на N пикселей»: сколько свитков до него, зависит от высоты окна стенда. Ориентир —
     * компактное название: после Р-54 ведущего действия в ряду формы нет, и прежний
     * ориентир (слово «Отмена» в ряду) исчез вместе с ним.
     */
    private fun ComposeUiTest.scrollFormUntilPinned(): Rect {
        repeat(SCROLL_SWIPES) {
            onAllNodes(hasScrollAction(), useUnmergedTree = true).onFirst().performTouchInput { swipeUp() }
            waitForIdle()
            val titles = onAllNodesWithTag(TAG_COMPACT_TITLE, useUnmergedTree = true).fetchSemanticsNodes()
            onAllNodesWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .firstOrNull { rect -> titles.any { rect.contains(it.boundsInRoot.center) } }
                ?.let { return it }
        }
        val rows = onAllNodesWithTag(TAG_PINNED_HEADER_BAR, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        throw AssertionError("the pinned row never took the compact title: rows=$rows")
    }

    private fun ComposeUiTest.quietButtons(): List<SemanticsNode> =
        onAllNodesWithTag(TAG_QUIET_BUTTON, useUnmergedTree = true).fetchSemanticsNodes()

    /** Шторка отчёта: под ней стоит тот же экран целиком, и узлы берутся по её границам. */
    private fun ComposeUiTest.sheetOf(): SemanticsNode =
        onNodeWithTag(TAG_REPORT_SHEET, useUnmergedTree = true).fetchSemanticsNode()

    /** Текстовые узлы внутри названной части экрана: под шторкой стоит тот же экран целиком. */
    private fun ComposeUiTest.inSheet(sheet: SemanticsNode, text: String): List<SemanticsNode> =
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { sheet.boundsInRoot.contains(it.boundsInRoot.center) }

    /** Разрешённый стиль подписи: им проверяются роли действия шапки (ОВ-146, §7). */
    private fun ComposeUiTest.styleOf(node: SemanticsNode): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        fun collect(current: SemanticsNode) {
            current.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            current.children.forEach(::collect)
        }
        collect(node)
        return requireNotNull(layouts.firstOrNull()) { "no text layout under the step word" }.layoutInput.style
    }

    /** Боковое поле экрана в пикселях: берётся у плотности стенда, а не числом. */
    private fun ComposeUiTest.sideFieldPx(): Float = with(density) { SIDE_FIELD.toPx() }

    @Composable
    private fun form() {
        MedicationFormScreen(
            state = formState(),
            onIntent = {},
            onPickDate = {},
            onPickTime = { _, _ -> },
        )
    }

    private fun formState(): MedFormUiState = MedFormUiState.of(
        draft = MedicationDraft(
            id = null,
            profileId = world.profiles.get(world.profiles.active).id,
            startDate = world.clock.today(),
        ),
        mode = FormMode.Create,
        inputs = FormInputs(),
    )

    /** Шторка отчёта врачу до отправки: единственная модальная поверхность с действием шапки (Р-54). */
    @Composable
    private fun reportSheet() {
        historyWithReport(outcome = null)
    }

    @Composable
    private fun deliveryCheckSheet() {
        DeliveryCheckSheet(
            onStart = {},
            onDismiss = {},
        )
    }

    @Composable
    private fun timeDialog() {
        DateTimePickerDialog(
            title = string(MedsR.string.form_dialog_time),
            initial = LocalTime.of(8, 0),
            is24Hour = true,
            onDismiss = {},
            onConfirm = {},
        )
    }

    @Composable
    private fun historyWithReport(outcome: ReportOutcome?) {
        HistoryScreen(state = reportState(outcome), onIntent = {})
    }

    /** Отчёт врачу открыт: шторка показывает состав, исход — состояние её содержимого. */
    private fun reportState(outcome: ReportOutcome?): HistoryUiState {
        val month = YearMonth.from(world.clock.today())
        val profile = world.profiles.get(world.profiles.active)
        return HistoryUiState(
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
            reportOutcome = outcome,
        )
    }

    private fun foreignChrome(): ProfileChromeState = ProfileChromeState.Foreign(FOREIGN.name, FOREIGN.colour)

    private fun string(id: Int, locale: Locale = RUSSIAN): String = context.withInterfaceLocale(locale).getString(id)

    /**
     * Стенд: язык интерфейса сценария и оформление профиля.
     *
     * Язык закреплён, потому что сценарии записаны по-русски, а телефон стенда мог быть
     * любым (НФТ-43).
     */
    @Composable
    private fun Stand(
        locale: Locale = RUSSIAN,
        chrome: ProfileChromeState = ProfileChromeState.Owner,
        content: @Composable () -> Unit,
    ) {
        val pinned = LocalContext.current.withInterfaceLocale(locale)
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
            LocalDensity provides Density(density = base.density, fontScale = base.fontScale),
        ) {
            ProfileChrome(chrome) { content() }
        }
    }

    private companion object {
        /** Допуск по линии и полю: пиксели единичной плотности стенда. */
        const val LINE_TOLERANCE = 1f

        const val FONT_TOLERANCE = 0.01f

        const val SCROLL_SWIPES = 8

        /** Тексты состава отчёта: стенд говорит о слотах шапки, а не о содержимом документа. */
        const val REPORT_PERIOD = "1 - 30 September 2026"
        const val REPORT_TAKEN = "10 / 10"

        /** Боковое поле экрана (§4): 20 dp. */
        val SIDE_FIELD = 20.dp

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")

        val FOREIGN = ForeignProfile("Мама", ProfileColor.Terracotta)
    }

    /**
     * Чужой профиль стенда: имя и цвет придуманы здесь, а не взяты из Предыстории.
     *
     * Мир профилей заводит чужой профиль с `color = 0` (`ScenarioProfiles`), поэтому
     * подкраску шапки и баннер о чужом профиле стенд показывает на именованном цвете.
     */
    private data class ForeignProfile(val name: String, val colour: ProfileColor)
}
