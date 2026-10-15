package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.task.Sk117ScheduleSteps
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.form.MedFormIntent
import ru.stohastika.sklyanka.feature.meds.form.ScheduleKind
import ru.stohastika.sklyanka.feature.meds.form.TAG_CYCLE_OFF_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_CYCLE_ON_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MODE_SWITCH
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BLOCKED_PRO
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_SCHEDULE_CARD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SCHEDULE_ROW
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_ADD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_CARD
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_DAYS
import ru.stohastika.sklyanka.feature.meds.form.TAG_STAGE_DOSE
import ru.stohastika.sklyanka.feature.meds.form.TAG_START_DATE
import ru.stohastika.sklyanka.feature.meds.form.TAG_STOCK_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_WEEKDAYS_PRO_NOTE
import ru.stohastika.sklyanka.feature.meds.form.TAG_WEEKDAYS_ROW
import ru.stohastika.sklyanka.ui.component.TAG_CHIP
import ru.stohastika.sklyanka.ui.component.TAG_LIST_PICKER_OPTION
import ru.stohastika.sklyanka.ui.component.TAG_PRO_MARK
import java.time.DayOfWeek

/**
 * Экранные шаги приёмки блока расписания в форме лекарства (SKL-117, группа `@form @screen`).
 *
 * Стенд — тот же [MedsFormStand], что у остальных экранных сценариев формы: своего стенда
 * у вида расписания нет, и второй означал бы второе место, где повторяются правила формы.
 * Строки «человек заполняет форму нового лекарства «Магний»» и «нажимает «Сохранить
 * лекарство»» объявлены в общем артефакте ([Sk117ScheduleSteps]) — словарь шагов не
 * допускает второго объявления. Первая из них приходит сюда доменным черновиком
 * (`formDraft`, [ensureForm]), вторая — крючком `saveOnScreen`: «Когда» остаётся «Когда».
 *
 * Проверяется то, что видит человек, а не копия разметки: метка «Про» ищется в собранных
 * строках списка, ряд дней — по семантике выбора чипов, причина отказа — по узлу над
 * кнопкой. Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk117ScheduleScreenSteps(private val world: AcceptanceWorld, private val scheduleSteps: Sk117ScheduleSteps) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Выбранные человеком дни: проверка отметки читает их, а не повтор разметки. */
    private var chosenWords: Set<String> = emptySet()

    init {
        // Экранный прогон подставляет нажатие на собранной форме: доменный шаг
        // «нажимает «Сохранить лекарство»» остаётся один на все прогоны. Имя и вид курса
        // приезжают не крючком, а доменным черновиком: класс шагов создаётся лениво, и на
        // шаге «человек заполняет форму…» этого класса ещё нет (см. [ensureForm]).
        scheduleSteps.saveOnScreen = { pressSave() }
    }

    // --- Вид расписания: строка и её список ----------------------------------------------

    @Тогда("^строка «Расписание» называет вид «([^»]+)»$")
    fun theScheduleRowNamesTheKind(kind: String) {
        val texts = askForm { test -> textsOf(test, TAG_SCHEDULE_ROW) }
        assertTrue(
            "the schedule row must be signed «${context.getString(R.string.form_label_schedule)}»: $texts",
            context.getString(R.string.form_label_schedule) in texts,
        )
        assertTrue("the schedule row must name «$kind»: $texts", kind in texts)
    }

    @Когда("^человек открывает список видов$")
    fun thePersonOpensTheKindList() {
        // Список открывается в том же проходе композиции, что и читается: открытое
        // состояние живёт в разметке экрана, и отдельная сборка показала бы форму без него.
        val options = askForm { test -> openKindList(test) }
        assertEquals(
            "the kind list must offer the five kinds of the norm",
            ScheduleKind.entries.size,
            options.size,
        )
    }

    @Тогда("^в списке пять видов, и «([^»]+)» отмечен$")
    fun theListMarksThatKind(kind: String) {
        askForm { test ->
            openKindList(test)
            assertEquals(
                "the kind list must offer the five kinds of the norm",
                ScheduleKind.entries.size,
                options(test).size,
            )
            assertTrue(
                "the closed list must offer exactly the five kinds",
                options(test).map { labelOf(it) }.toSet() ==
                    ScheduleKind.entries.map { context.getString(kindLabel(it)) }
                        .toSet(),
            )
            val selected = options(test).filter { selectedFlag(it) }
            assertEquals("exactly one kind must be marked: ${selected.map { labelOf(it) }}", 1, selected.size)
            assertEquals("the marked kind must be «$kind»", kind, labelOf(selected.single()))
        }
    }

    @Тогда("^у видов «([^»]+)», «([^»]+)» и «([^»]+)» стоит метка «Про»$")
    fun thoseKindsCarryTheProMark(first: String, second: String, third: String) {
        askForm { test ->
            openKindList(test)
            val marked = markedLabels(test)
            listOf(first, second, third).forEach { kind ->
                assertTrue("the kind «$kind» must carry the Pro mark: $marked", kind in marked)
            }
        }
    }

    @Но("^у вида «([^»]+)» метки нет$")
    fun thatKindCarriesNoMark(kind: String) {
        askForm { test ->
            openKindList(test)
            val marked = markedLabels(test)
            assertTrue("the kind «$kind» must carry no Pro mark: $marked", kind !in marked)
        }
    }

    // --- Параметры видов ------------------------------------------------------------------

    @Когда("^выбирает вид «([^»]+)»$")
    fun thePersonChoosesTheKind(kind: String) {
        val expected = kindOf(kind)
        val intents = actForm { test ->
            openKindList(test)
            test.onNode(hasTestTag(TAG_LIST_PICKER_OPTION) and hasText(kind)).performClick()
        }
        assertTrue(
            "the kind «$kind» must be chosen from the list: $intents",
            intents.any { it is MedFormIntent.ScheduleKindChanged && it.kind == expected },
        )
        assertEquals(expected, MedsFormStand.open(world).scheduleKind)
    }

    @Тогда("^показан ряд дней недели с надстрочником «([^»]+)»$")
    fun theWeekdayRowIsShown(caption: String) {
        askForm { test ->
            // Надстрочник набирается капителью (`SectionLabel`, §4.1): текст стоит на экране
            // заглавными, и сверяется он без учёта регистра — иначе проверка искала бы строку,
            // которой на экране нет.
            val captions = test.onAllNodes(
                hasText(caption, substring = true, ignoreCase = true),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertTrue(
                "the form must carry the caption «$caption»; texts: ${allTexts(test)}",
                captions.isNotEmpty(),
            )
            assertTrue(
                "the caption «$caption» must be placed: ${captions.map { it.boundsInRoot }}",
                captions.any { it.boundsInRoot.height > 0f },
            )
            test.onAllNodesWithTag(TAG_WEEKDAYS_ROW, useUnmergedTree = true).assertCountEquals(1)
            test.onAllNodes(
                hasTestTag(TAG_CHIP) and hasAnyAncestor(hasTestTag(TAG_WEEKDAYS_ROW)),
                useUnmergedTree = true,
            ).assertCountEquals(DayOfWeek.entries.size)
        }
    }

    @И("^под рядом стоит метка «Про» со словами «([^»]+)»$")
    fun theProNoteStandsUnderTheRow(words: String) {
        askForm { test ->
            val note = test.onNodeWithTag(TAG_WEEKDAYS_PRO_NOTE, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("the note must read «$words»: ${MedsProbe.textsOf(note)}", words in MedsProbe.textsOf(note))
            assertTrue(
                "the note must carry the Pro mark",
                test.onAllNodes(
                    hasTestTag(TAG_WEEKDAYS_PRO_NOTE) and hasAnyDescendant(hasTestTag(TAG_PRO_MARK)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty(),
            )
            val rowBottom = test.onNodeWithTag(TAG_WEEKDAYS_ROW, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.bottom
            assertTrue("the note must stand under the row", note.boundsInRoot.top >= rowBottom)
        }
    }

    @Когда("^человек выбирает дни «([^»]+)», «([^»]+)» и «([^»]+)»$")
    fun thePersonChoosesThoseDays(first: String, second: String, third: String) {
        val words = listOf(first, second, third)
        val intents = actForm { test ->
            // День адресуется словом сценария: именно им чип и читается («среда»),
            // а короткая подпись («Ср») — только вид (НФТ-30, §5.10). Нажатие по слову
            // и есть проверка, что человек выбирает день, а не позицию в ряду.
            words.forEach { word ->
                test.onNodeWithContentDescription(word, useUnmergedTree = true).performClick()
            }
        }
        val days = intents.filterIsInstance<MedFormIntent.WeekdayToggled>().map { it.day }
        assertEquals("every named day must be chosen once: $intents", words.size, days.distinct().size)
        chosenWords = words.toSet()
    }

    @Тогда("^выбранные дни отмечены$")
    fun theChosenDaysAreMarked() {
        assertTrue("the scenario must have chosen days", chosenWords.isNotEmpty())
        askForm { test ->
            val chips = weekdayChips(test)
            assertEquals("the row must show one chip per weekday", DayOfWeek.entries.size, chips.size)
            val words = chips.map { readWord(it) }
            assertTrue("every chip must be readable by its full name: $words", words.none { it.isNullOrBlank() })
            assertEquals("the row must name each weekday once", DayOfWeek.entries.size, words.distinct().size)
            chips.forEach { chip ->
                val word = readWord(chip)
                assertEquals(
                    "the day «$word» must ${if (word in chosenWords) "" else "not "}be marked",
                    word in chosenWords,
                    selectedFlag(chip),
                )
            }
        }
    }

    @И("^под блоком стоят «([^»]+)» и «([^»]+)»$")
    fun thoseBlocksStandUnderTheSchedule(first: String, second: String) {
        askForm { test ->
            val cardBottom = test.onNodeWithTag(TAG_SCHEDULE_CARD, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.bottom
            listOf(first, second).forEach { words ->
                // Надписи блоков набираются капителью (`SectionLabel`, §4.1): сверяются
                // без учёта регистра, иначе проверка искала бы строку, которой на экране нет.
                val nodes = test.onAllNodes(
                    hasText(words, substring = true, ignoreCase = true),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                assertTrue("the form must show «$words»; texts: ${allTexts(test)}", nodes.isNotEmpty())
                assertTrue(
                    "«$words» must stand under the schedule block",
                    nodes.all { it.boundsInRoot.top >= cardBottom },
                )
            }
        }
    }

    // --- Ступени: карточки, поля и крест удаления -----------------------------------------

    @Тогда("^показана карточка «([^»]+)» с полями дозы и длительности$")
    fun theStageCardIsShown(title: String) {
        askForm { test ->
            // Карточка — контейнер без слияния семантики, и подпись «Этап N» лежит в её
            // поддереве: карточка ищется по тегу и тексту внутри, а не совпадением на узле.
            assertTrue("the stage card «$title» must be shown: ${stageTitles(test)}", stageCard(test, title) != null)
            assertTrue(
                "the «$title» card must hold the dose and the duration fields: ${stageTitles(test)}",
                test.onAllNodes(
                    hasTestTag(TAG_STAGE_CARD) and hasAnyDescendant(hasTestTag(TAG_STAGE_DOSE)) and
                        hasAnyDescendant(hasTestTag(TAG_STAGE_DAYS)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @И("^блока «Доза на один приём» на форме нет$")
    fun theDoseBlockIsGone() {
        askForm { test ->
            test.onAllNodesWithTag(TAG_DOSE_FIELD, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @И("^под списком стоит «([^»]+)»$")
    fun thatButtonStandsUnderTheList(label: String) {
        askForm { test ->
            val button = test.onNodeWithTag(TAG_STAGE_ADD, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(
                "«$label» must stand under the stage list: ${MedsProbe.textsOf(button)}",
                label in MedsProbe.textsOf(button),
            )
            val cardBottom = stageCards(test).maxOf { it.boundsInRoot.bottom }
            assertTrue(
                "«$label» must stand under the stage list",
                button.boundsInRoot.top >= cardBottom,
            )
        }
    }

    @Когда("^человек нажимает «Добавить этап»$")
    fun thePersonPressesAddStage() {
        val intents = actForm { test ->
            test.onNodeWithTag(TAG_STAGE_ADD, useUnmergedTree = true).performClick()
        }
        assertTrue("«Добавить этап» must add a stage: $intents", MedFormIntent.StageAdded in intents)
    }

    @Тогда("^показана карточка «([^»]+)»$")
    fun theSecondStageCardIsShown(title: String) {
        askForm { test ->
            assertEquals(
                "the pressed button must add exactly one card: ${stageTitles(test)}",
                STAGES_WITH_DELETE,
                stageCards(test).size,
            )
            assertTrue(
                "the added card «$title» must be shown: ${stageTitles(test)}",
                stageCard(test, title) != null,
            )
        }
    }

    @И("^у обеих карточек есть крест удаления$")
    fun bothCardsHaveTheDeleteCross() {
        askForm { test ->
            assertEquals(
                "the scenario must show two stage cards: ${stageTitles(test)}",
                STAGES_WITH_DELETE,
                stageCards(test).size,
            )
            (1..STAGES_WITH_DELETE).forEach { number ->
                test.onNodeWithContentDescription(context.getString(R.string.cd_stage_delete, number))
                    .assertExists()
            }
        }
    }

    // --- Закрытое создание: длины цикла и причина над кнопкой ------------------------------

    @Когда("^выбирает вид «([^»]+)» и длины «([^»]+)» и «([^»]+)»$")
    fun thePersonChoosesTheKindAndCycleLengths(kind: String, on: String, off: String) {
        val expected = kindOf(kind)
        val intents = actForm { test ->
            openKindList(test)
            test.onNode(hasTestTag(TAG_LIST_PICKER_OPTION) and hasText(kind)).performClick()
            test.waitForIdle()
            // Поле адресуется по действию ввода, а не по тегу контейнера: тег стоит
            // на контейнере поля дизайн-системы, действие — на внутреннем узле.
            test.onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CYCLE_ON_FIELD)),
                useUnmergedTree = true,
            ).performTextInput(on)
            test.onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CYCLE_OFF_FIELD)),
                useUnmergedTree = true,
            ).performTextInput(off)
        }
        assertTrue(
            "the kind «$kind» must be chosen: $intents",
            intents.any { it is MedFormIntent.ScheduleKindChanged && it.kind == expected },
        )
        assertTrue("the intake length «$on» must be typed: $intents", MedFormIntent.CycleOnChanged(on) in intents)
        assertTrue("the pause length «$off» must be typed: $intents", MedFormIntent.CycleOffChanged(off) in intents)
    }

    @Тогда("^над кнопкой сказано «(.+)»$")
    fun theReasonIsStatedAboveTheButton(reason: String) {
        askForm { test ->
            val block = test.onNodeWithTag(TAG_SAVE_BLOCKED_PRO, useUnmergedTree = true)
            block.assertExists()
            assertTrue(
                "the reason must read «$reason»: ${textsOf(test, TAG_SAVE_BLOCKED_PRO)}",
                reason in textsOf(test, TAG_SAVE_BLOCKED_PRO),
            )
            val saveTop = test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.top
            assertTrue(
                "the reason must stand above the save button",
                block.fetchSemanticsNode().boundsInRoot.top < saveTop,
            )
        }
    }

    /**
     * Платной причины над кнопкой нет (SKL-117, критика F5).
     *
     * Подсказка называет то, что произойдёт (§5.10): пока черновик отвергается формой,
     * нажатие назовёт отказ формы, а не покупку, и платная причина молчит — иначе человеку
     * предлагали бы купить там, где он ещё не доделал назначение.
     */
    @Тогда("^платной причины над кнопкой нет$")
    fun thePaidReasonIsNotStated() {
        askForm { test ->
            test.onAllNodesWithTag(TAG_SAVE_BLOCKED_PRO, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // --- Режим «по потребности»: блока расписания нет ---------------------------------------

    @Когда("^выбирает режим «([^»]+)»$")
    fun thePersonChoosesTheMode(mode: String) {
        val intents = actForm { test ->
            test.onNode(
                hasText(mode) and hasAnyAncestor(hasTestTag(TAG_MODE_SWITCH)),
                useUnmergedTree = true,
            ).performClick()
        }
        assertTrue("the mode switch must report «$mode»: $intents", intents.any { it is MedFormIntent.ModeChanged })
    }

    @Тогда("^строки «Расписание» на форме нет$")
    fun theScheduleRowIsGone() {
        askForm { test ->
            test.onAllNodesWithTag(TAG_SCHEDULE_CARD, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Но("^остаток и период показаны$")
    fun theStockAndThePeriodAreShown() {
        askForm { test ->
            test.onNodeWithTag(TAG_STOCK_FIELD).assertIsDisplayed()
            test.onNodeWithTag(TAG_START_DATE).assertIsDisplayed()
        }
    }

    // --- Обвязка --------------------------------------------------------------------------

    /**
     * Открыть стенд из доменного черновика, если форма ещё не открыта.
     *
     * Класс шагов задачи создаётся лениво, и шаг «человек заполняет форму нового лекарства
     * «…»» успевает пройти раньше первого экранного шага. Поэтому стенд подтягивается
     * к тому же черновику тем же путём, что в `MedsPreviewScreenSteps`, — а не крючком,
     * который был бы пуст. Повторно черновик не подтягивается: правки человека (вид, дни,
     * длины цикла) живут в собранном состоянии, и второе подтягивание их стёрло бы.
     */
    private fun ensureForm() {
        if (!MedsFormStand.isOpen(world)) {
            val draft = requireNotNull(scheduleSteps.formDraft) { "the scenario never filled the form" }
            MedsFormStand.showDraft(world, draft)
        }
    }

    /** Вопрос собранной форме: стенд открыт до композиции. */
    private fun <T> askForm(question: (ComposeUiTest) -> T): T {
        ensureForm()
        return MedsFormStand.ask(world, question)
    }

    /** Действие на собранной форме: стенд открыт до композиции. */
    private fun actForm(action: (ComposeUiTest) -> Unit): List<MedFormIntent> {
        ensureForm()
        return MedsFormStand.act(world, action)
    }

    /** Сохранение на собранной форме: нажатие нижней кнопки, как у человека. */
    private fun pressSave() {
        val before = MedsFormStand.open(world)
        val button = mutableListOf<String>()
        val intents = actForm { test ->
            val node = test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).fetchSemanticsNode()
            button += "bounds=${node.boundsInRoot}"
            button += "actions=${node.config.toList().map { it.key.name }}"
            test.onNodeWithTag(TAG_SAVE_BUTTON, useUnmergedTree = true).performClick()
        }
        assertTrue(
            "the save button must report its intention: $intents " +
                "(name=«${before.draft.name}», enabled=${before.saveEnabled}, ${button.joinToString()})",
            MedFormIntent.Save in intents,
        )
    }

    /**
     * Открыть список видов в текущей композиции и вернуть его строки.
     *
     * Ряд «Расписание» открывает диалог выбора вида; открытое состояние живёт в разметке
     * экрана, поэтому открытие и чтение идут в одном проходе композиции.
     */
    private fun openKindList(test: ComposeUiTest): List<SemanticsNode> {
        test.onNodeWithTag(TAG_SCHEDULE_ROW, useUnmergedTree = true).performClick()
        test.waitForIdle()
        return options(test)
    }

    private fun options(test: ComposeUiTest): List<SemanticsNode> =
        test.onAllNodesWithTag(TAG_LIST_PICKER_OPTION, useUnmergedTree = true).fetchSemanticsNodes()

    private fun labelOf(node: SemanticsNode): String =
        requireNotNull(MedsProbe.textsOf(node).firstOrNull()) { "a list option without a label" }

    private fun selectedFlag(node: SemanticsNode): Boolean = node.config.getOrNull(SemanticsProperties.Selected) == true

    private fun markedLabels(test: ComposeUiTest): List<String> = test.onAllNodes(
        hasTestTag(TAG_LIST_PICKER_OPTION) and hasAnyDescendant(hasTestTag(TAG_PRO_MARK)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().map { labelOf(it) }

    private fun textsOf(test: ComposeUiTest, tag: String): List<String> = MedsProbe.texts(test, tag)

    /** Карточки этапов в порядке разметки. */
    private fun stageCards(test: ComposeUiTest): List<SemanticsNode> =
        test.onAllNodesWithTag(TAG_STAGE_CARD, useUnmergedTree = true).fetchSemanticsNodes()

    /** Карточка этапа с названной подписью: «Этап N» лежит в её поддереве. */
    private fun stageCard(test: ComposeUiTest, title: String): SemanticsNode? =
        stageCards(test).firstOrNull { title in MedsProbe.textsOf(it) }

    /** Подписи и значения карточек этапов — для сообщения о падении, а не для проверки. */
    private fun stageTitles(test: ComposeUiTest): List<List<String>> = stageCards(test).map { MedsProbe.textsOf(it) }

    /** Весь текст собранного экрана: сообщение о падении обязано называть, что было на экране. */
    private fun allTexts(test: ComposeUiTest): List<String> =
        test.onAllNodes(hasText("", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { MedsProbe.textsOf(it) }.distinct()

    private fun kindOf(label: String): ScheduleKind =
        ScheduleKind.entries.first { context.getString(kindLabel(it)) == label }

    private fun kindLabel(kind: ScheduleKind): Int = when (kind) {
        ScheduleKind.DAILY -> R.string.schedule_kind_daily
        ScheduleKind.WEEKDAYS -> R.string.schedule_kind_weekdays
        ScheduleKind.INTERVAL -> R.string.schedule_kind_interval
        ScheduleKind.CYCLES -> R.string.schedule_kind_cycle
        ScheduleKind.STAGES -> R.string.schedule_kind_steps
    }

    /** Чипы ряда дней: семь штук, по одному на день недели (§5.10). */
    private fun weekdayChips(test: ComposeUiTest): List<SemanticsNode> = test.onAllNodes(
        hasTestTag(TAG_CHIP) and hasAnyAncestor(hasTestTag(TAG_WEEKDAYS_ROW)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()

    /** Слово, которым чип читается: полная форма дня недели, а не подпись «Ср». */
    private fun readWord(chip: SemanticsNode): String? =
        chip.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()

    private companion object {
        /** Удаляемых карточек в сценарии ровно две: последняя не удаляется (ИНВ-23, §5.10). */
        const val STAGES_WITH_DELETE = 2
    }
}
