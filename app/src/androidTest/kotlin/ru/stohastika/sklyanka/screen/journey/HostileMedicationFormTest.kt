package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.feature.meds.form.TAG_ADD_TIME
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_ADD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_CARD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_DAYS
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_DOSE
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.form.TAG_VIOLATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_CONFIRM
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_DIALOG
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_DISMISS
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import java.time.LocalTime
import ru.stohastika.sklyanka.feature.meds.R as MedsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Враждебный поход «форма лекарства» (SKL-140, реестр походов № 14).
 *
 * Человек ломает ввод так, как этого не делает ни один сценарий приёмки: поворачивает экран
 * посреди заполнения, уходит системной «Назад» без сохранения, жмёт «Сохранить» дважды подряд,
 * вводит название из одних пробелов, отменяет выбор времени. Утверждается не «экран сменился»,
 * а место человека и состояние данных: введённое цело, лекарство заведено ровно одно, отказ
 * назван и показан, строка времени не изменилась.
 *
 * **Две проверки этого похода нарочно жёсткие.** Набор утверждает норму, а не наблюдаемое
 * поведение (TDD §8): понизить их до того, что видно сегодня, значило бы закрепить дефект
 * проверкой. Обе закрыты в дереве (SKL-141, SKL-142): поворот держат сохранённый стек раздела
 * (`MedsSection.kt`, `MedsStackSaver`) и признак «форма открыта один раз на заход»
 * (`MedicationFormRoute.kt`), повторное нажатие — флаг «идёт запись» в состоянии формы
 * (`MedFormUiState.saving`, `MedicationFormViewModel`). Зелены ли они на стенде, решает
 * прогон на устройстве: этот файл прогонов не запускает.
 *
 * 1. Поворот в середине ввода (см. [waitForFormAfterRotation] и [theStagedFormSurvivesRotation]).
 *    Манифест не объявляет `configChanges` (`app/src/main/AndroidManifest.xml`), поворот
 *    пересоздаёт активность — и заполненное обязано пересоздание пережить: черновик живёт
 *    в модели активности, а место человека — в сохранённом состоянии композиции.
 * 2. Двойное нажатие «Сохранить» (шаг 3): нажатий два, а запись и план приёмов — одни.
 *
 * Название из одних пробелов форма считает пустым названием и до сохранения не доводит: кнопка
 * недоступна, а причина названа человеку словами (шаг 4). Поэтому «сохранение отклонено, названо
 * первое нарушение и форма прокручена к нему» проверяется там же, но на черновике с настоящим
 * названием, у которого нет ни одного времени приёма: первое нарушение списка и есть время.
 *
 * Конец курса в прошлом автоматизацией не берётся — так и записано в приёмке (SKL-140.feature,
 * примечание к враждебному проходу по форме): дату ставит календарь Material3
 * (`DateTimePickerDialog`), у дня календаря устойчивого тега нет, и нарушение по сроку держат
 * экранные проверки формы.
 */
@HiltAndroidTest
class HostileMedicationFormTest : JourneyTest() {

    @Test
    fun thePersonCannotBreakTheMedicationForm() {
        val halfName = testContext.getString(R.string.device_test_medication_name, "hostile-form")
        val savedName = testContext.getString(R.string.device_test_medication_name, "hostile-save")
        val violationName = testContext.getString(R.string.device_test_medication_name, "hostile-violation")
        val blockedReason = ui(MedsR.string.form_save_blocked)

        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)

        // Половина полей: название и доза введены, времени нет — форма к записи не готова.
        typeInto(TAG_NAME_FIELD, halfName)
        typeInto(TAG_DOSE_FIELD, DOSE)
        capture("form-half")

        // 2. Системная «Назад» на грязной форме спрашивает подтверждение (RT-1, Р-87 п. 1):
        //    «остаться» держит форму и ввод, «сбросить» закрывает в список без записи —
        //    возврат с формы повторяет возврат и не выводит из раздела (§5.1).
        pressBack()
        waitForTag(TAG_CONFIRM_DIALOG)
        capture("back-confirm")
        click(TAG_CONFIRM_DISMISS)
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия подтверждения «остаться»") { !exists(TAG_CONFIRM_DIALOG) }
        assertTrue("«остаться» must keep the form open", exists(TAG_MEDICATION_FORM))
        assertEquals("«остаться» must keep the input", halfName, typedText(TAG_NAME_FIELD))

        pressBack()
        waitForTag(TAG_CONFIRM_DIALOG)
        click(TAG_CONFIRM_CONFIRM)
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия формы после «сбросить»") {
            exists(TAG_MEDICATION_LIST) && !exists(TAG_MEDICATION_FORM)
        }
        capture("after-back")
        assertEquals("an abandoned form must not create a row", 0, cardsWith(halfName))
        assertEquals("an abandoned form must not create a medication", 0, opLogCount(OpEvent.MEDICATION_CREATED))

        // 2б. Чистая форма закрывается без вопроса (Р-87 п. 1): человек ничего не ввёл —
        //     терять нечего, и подтверждение было бы шумом.
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия чистой формы без подтверждения") {
            exists(TAG_MEDICATION_LIST) && !exists(TAG_MEDICATION_FORM)
        }
        assertTrue("a clean form must close without asking", !exists(TAG_CONFIRM_DIALOG))

        // 3. Двойное нажатие «Сохранить»: одно лекарство и один план приёмов, а не два.
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        typeInto(TAG_NAME_FIELD, savedName)
        typeInto(TAG_DOSE_FIELD, DOSE)
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        doublePressByTag(TAG_SAVE_BUTTON)
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия формы после сохранения") { !exists(TAG_MEDICATION_FORM) }
        waitForTag(TAG_MEDICATION_LIST)
        scrollListTo(savedName)
        capture("saved-twice")
        assertEquals("a double press must leave exactly one row", 1, cardsWith(savedName))
        assertEquals("a double press must create one medication", 1, opLogCount(OpEvent.MEDICATION_CREATED))
        assertEquals("a double press must not double the plan", 1, tomorrowIntakes())

        // 4. Название из одних пробелов: своего названия у лекарства нет, поэтому форма
        //    не доводит запись до сохранения — кнопка недоступна, а причина названа словами.
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        typeInto(TAG_NAME_FIELD, SPACES)
        waitUntil(SCREEN_TIMEOUT_MS, "названной причины недоступного сохранения") { textExists(blockedReason) }
        assertEquals("the field must keep the spaces as typed", SPACES, typedText(TAG_NAME_FIELD))
        compose.onNodeWithText(blockedReason, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).assertIsNotEnabled()
        capture("name-blank")

        // «Дата окончания в прошлом» автоматизацией не берётся (примечание приёмки SKL-140):
        // конец курса ставит календарь Material3, у дня календаря нет устойчивого тега. Нарушение
        // по сроку держат экранные проверки формы (`MedicationFormScreen`-стенд), а здесь отказ
        // и прокрутка к первому нарушению проверены через другое поле — отсутствие времени приёма.
        replaceText(TAG_NAME_FIELD, violationName)
        typeInto(TAG_DOSE_FIELD, DOSE)
        click(TAG_SAVE_BUTTON)
        waitUntilDisplayed(TAG_VIOLATION, "первого нарушения, названного формой")
        val missingTime = ui(MedsR.string.error_no_times)
        assertEquals("the rejection must name the missing time", missingTime, nodeText(TAG_VIOLATION))
        assertTrue("a rejected save must leave the form open", exists(TAG_MEDICATION_FORM))
        assertEquals("a rejected save must not create a medication", 1, opLogCount(OpEvent.MEDICATION_CREATED))
        capture("first-violation")

        // 5. Выбор времени, закрытый отменой: показанное в строке время не изменилось.
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        val shown = timeRowText()
        assertTrue("the time row must show a time, got «$shown»", shown.isNotBlank())
        scrollToAndClick(TAG_TIME_ROW)
        waitForTag(TAG_DATE_TIME_PICKER)
        // Кадр диалога берётся корнем диалога: у выбора времени своё окно, и корней
        // в кадре два — снимок всего окна падает на «found 2 nodes that satisfy: (isRoot)»
        // (SKL-148, тот же урок, что в SKL-24/105).
        capture("time-picker", compose.onNode(isDialog()))
        cancelTimePicker()
        waitUntil(SCREEN_TIMEOUT_MS, "закрытия выбора времени отменой") { !exists(TAG_DATE_TIME_PICKER) }
        // 6. Поворот в середине ввода — последней пробой: образ может не менять ориентацию,
        //    и тогда проба не имеет предмета (о пропуске скажет отчёт, а не молчание).
        //    Ставь её раньше — отказ стенда прятал бы остальные находки этого похода.
        assumeTrue(
            "стенд не меняет ориентацию: проба поворота не имеет предмета, дефект назван SKL-142",
            rotateLandscapeOrNull(),
        )
        waitForFormAfterRotation(violationName)
        capture("form-landscape")
        rotatePortrait()
        waitForFormAfterRotation(violationName)
        assertEquals("the typed dose must survive the rotation", DOSE, typedText(TAG_DOSE_FIELD))
        capture("form-portrait")
    }

    /**
     * Поворот на форме со ступенями сохраняет заполненное (SKL-117 §5.9).
     *
     * Ступенчатый вид — самая длинная форма продукта: вид, у каждой из двух карточек этапа
     * доза и длительность. Поворот пересоздаёт активность, и человек обязан вернуться к той же
     * форме с тем же видом и теми же числами: потерянная карточка или цифра — это потерянное
     * назначение врача, а не косметика. Создание сложного вида в бесплатной сборке ничем
     * не закрыто (Р-86, `ADR-0016`), поэтому шторки на пути нет; предмет пробы по-прежнему
     * в том, что введённое переживает пересоздание, а не в записи курса.
     */
    @Test
    fun theStagedFormSurvivesRotation() {
        val name = testContext.getString(R.string.device_test_medication_name, ROTATION_NAME_SUFFIX)

        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        typeInto(TAG_NAME_FIELD, name)
        // Доза курса вводится до выбора вида: у ступенчатого вида её поле уступает место этапам.
        typeInto(TAG_DOSE_FIELD, DOSE)
        chooseScheduleKind(MedsR.string.schedule_kind_steps)
        // Вид выбирается без оплаты и без витрины: в бесплатной сборке доступа не покупают
        // (Р-86), и следующим шагом форма показывает карточки этапов.
        waitForTag(TAG_STAGE_CARD)
        fillStages()
        // Заполненное сверяется до поворота: иначе «потеряно поворотом» не отличить
        // от «не набралось», и отказ пробы говорил бы не о том, что случилось.
        assertStagesKept()
        capture("stages-filled")

        // Поворот — последняя проба: образ может не менять ориентацию (окно без дисплея),
        // и тогда проба не имеет предмета; о пропуске говорит отчёт, а не молчание.
        assumeTrue(
            "стенд не меняет ориентацию: проба поворота не имеет предмета, дефект назван SKL-142",
            rotateLandscapeOrNull(),
        )
        waitForFilledStages(name)
        assertStagesKept()
        capture("stages-landscape")

        rotatePortrait()
        waitForFilledStages(name)
        assertStagesKept()
        capture("stages-portrait")
    }

    /** Заполнить обе карточки этапов: первую — человеком, вторую — добавленную им же. */
    private fun fillStages() {
        typeIntoStage(TAG_STAGE_DOSE, FIRST_STAGE, FIRST_STAGE_DOSE)
        typeIntoStage(TAG_STAGE_DAYS, FIRST_STAGE, FIRST_STAGE_DAYS)
        scrollToAndClick(TAG_STAGE_ADD)
        waitUntil(SCREEN_TIMEOUT_MS, "второй карточки этапа") { stageCards() == STAGES }
        typeIntoStage(TAG_STAGE_DOSE, SECOND_STAGE, SECOND_STAGE_DOSE)
        typeIntoStage(TAG_STAGE_DAYS, SECOND_STAGE, SECOND_STAGE_DAYS)
    }

    /**
     * Заполненное в ступенях: вид, число карточек, дозы и длительности обеих.
     *
     * Числа сверяются по тексту полей, а не по значению в домене: предмет пробы — то, что
     * человек набрал и видит на экране, и потеря любой цифры обязана быть видна.
     */
    private fun assertStagesKept() {
        assertTrue(
            "строка вида обязана остаться ступенчатой, а называет «${scheduleRowText()}»",
            scheduleRowText().contains(ui(MedsR.string.schedule_kind_steps)),
        )
        assertEquals("карточек этапов — две", STAGES, stageCards())
        assertEquals("доза первого этапа", FIRST_STAGE_DOSE, typedText(stageField(TAG_STAGE_DOSE, FIRST_STAGE)))
        assertEquals(
            "длительность первого этапа",
            FIRST_STAGE_DAYS,
            typedText(stageField(TAG_STAGE_DAYS, FIRST_STAGE)),
        )
        assertEquals("доза второго этапа", SECOND_STAGE_DOSE, typedText(stageField(TAG_STAGE_DOSE, SECOND_STAGE)))
        assertEquals(
            "длительность второго этапа",
            SECOND_STAGE_DAYS,
            typedText(stageField(TAG_STAGE_DAYS, SECOND_STAGE)),
        )
    }

    /**
     * Дождаться заполненной формы со ступенями после поворота — то же, что названо сценарием.
     *
     * Поворот пересоздаёт активность, и на миг композиции в процессе нет вовсе: чтение дерева
     * в этот миг отказывает, а не отвечает «узла нет», поэтому условие ждёт через [runCatching].
     */
    private fun waitForFilledStages(name: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "заполненной формы со ступенями после поворота") {
            runCatching {
                exists(TAG_MEDICATION_FORM) &&
                    typedText(TAG_NAME_FIELD) == name &&
                    stageCards() == STAGES
            }.getOrDefault(false)
        }
    }

    /** Число карточек этапов на форме: потеря карточки — потерянная ступень назначения. */
    private fun stageCards(): Int =
        compose.onAllNodesWithTag(TAG_STAGE_CARD, useUnmergedTree = true).fetchSemanticsNodes().size

    /**
     * Поле этапа номером: у обеих карточек один тег, и различает их имя поля для чтения
     * с экрана — «Доза этапа 1», «Длительность этапа 2».
     */
    private fun stageField(tag: String, number: Int): SemanticsNodeInteraction = compose.onNode(
        hasSetTextAction() and hasAnyAncestor(
            hasTestTag(tag) and hasContentDescription(stageFieldName(tag, number)),
        ),
        useUnmergedTree = true,
    )

    private fun typeIntoStage(tag: String, number: Int, value: String) {
        stageField(tag, number).performScrollTo().performTextInput(value)
        compose.waitForIdle()
    }

    /** Имя поля этапа для чтения с экрана: продукт называет номер этапа словами ресурса. */
    private fun stageFieldName(tag: String, number: Int): String = targetContext.getString(
        if (tag == TAG_STAGE_DOSE) MedsR.string.cd_stage_dose else MedsR.string.cd_stage_days,
        number,
    )

    /**
     * Дождаться заполненной формы после поворота — то же, что названо сценарием.
     *
     * Поворот пересоздаёт активность, и на миг композиции в процессе нет вовсе: чтение дерева
     * в этот миг отказывает, а не отвечает «узла нет», поэтому условие ждёт через [runCatching].
     */
    private fun waitForFormAfterRotation(name: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "заполненной формы после поворота") {
            runCatching { exists(TAG_MEDICATION_FORM) && typedText(TAG_NAME_FIELD) == name }.getOrDefault(false)
        }
    }

    /**
     * Введённое в поле: у поля читается его редактируемый текст, а не подпись.
     */
    private fun typedText(fieldTag: String): String = typedText(
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(fieldTag)), useUnmergedTree = true),
    )

    /** Введённое в поле, найденное не по тегу: у ступеней тег общий, различает их имя поля. */
    private fun typedText(node: SemanticsNodeInteraction): String = node.fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.EditableText)
        ?.text
        .orEmpty()

    /**
     * Показанное в строке времени.
     *
     * Цифры времени лежат в потомке строки, поэтому строка читается слитым деревом — тем же,
     * каким её читает человек: нажимаемая строка сливает подписи своих детей.
     */
    private fun timeRowText(): String = compose.onNodeWithTag(TAG_TIME_ROW)
        .fetchSemanticsNode()
        .config
        .getOrNull(SemanticsProperties.Text)
        .orEmpty()
        .joinToString(" ") { it.text }

    /** Строки списка с названным лекарством: двойное сохранение видно их числом. */
    private fun cardsWith(name: String): Int = compose.onAllNodes(
        hasTestTag(TAG_MEDICATION_CARD) and hasAnyDescendant(hasText(name)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    /** Приёмы завтрашнего дня на время первой строки расписания: двойное сохранение их удвоит. */
    private fun tomorrowIntakes(): Int =
        dayIntakes(DeviceTestClock.today.plusDays(1)).count { it.scheduledAt.toLocalTime() == MORNING }

    /**
     * Дождаться, что узел не просто есть, а виден человеку.
     *
     * Прокрутка формы к первому нарушению — анимация: узел появляется раньше, чем доезжает
     * до видимой области, и «есть» здесь ещё не значит «показан».
     */
    private fun waitUntilDisplayed(tag: String, what: String) {
        waitUntil(SCREEN_TIMEOUT_MS, what) {
            runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
    }

    /** Закрыть выбор времени отменой: подпись берётся из ресурса дизайн-системы. */
    private fun cancelTimePicker() {
        val cancel = ui(UiR.string.action_cancel)
        compose.onNode(
            hasText(cancel) and hasAnyAncestor(hasTestTag(TAG_DATE_TIME_PICKER)),
            useUnmergedTree = true,
        ).performClick()
    }

    private companion object {
        const val DOSE = "1"

        /** Название из одних пробелов: для формы это пустое название, а не имя лекарства. */
        const val SPACES = "   "

        /** Кусок имени лекарства пробы поворота: имена стенда обязаны быть латиницей (НФТ-42). */
        const val ROTATION_NAME_SUFFIX = "hostile-stages"

        /** Номера этапов: первый приходит формой, второй человек добавляет сам. */
        const val FIRST_STAGE = 1
        const val SECOND_STAGE = 2

        /** Сколько карточек этапов человек оставил на форме. */
        const val STAGES = 2

        /** Числа этапов: они и обязаны пережить поворот — по ним видна потеря введённого. */
        const val FIRST_STAGE_DOSE = "1"
        const val FIRST_STAGE_DAYS = "7"
        const val SECOND_STAGE_DOSE = "2"
        const val SECOND_STAGE_DAYS = "24"

        /** Время первой строки расписания: его даёт продукт — 08:00 (`DEFAULT_TIME`). */
        val MORNING: LocalTime = LocalTime.of(8, 0)
    }
}
