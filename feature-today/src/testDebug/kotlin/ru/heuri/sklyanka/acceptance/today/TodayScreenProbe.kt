package ru.heuri.sklyanka.acceptance.today

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.feature.today.TAG_LANE_HEADER
import ru.heuri.sklyanka.feature.today.TodayIntent
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.feature.today.TodayUiState
import ru.heuri.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд главного экрана для шагов приёмки: собранная композиция и намерения человека.
 *
 * Состояние собирает настоящий домен из мира сценария ([TodayStand]), а нажатия уходят
 * в домен тем же путём, что в продукте: экран отдаёт [TodayIntent], стенд применяет его
 * к миру. Ни кнопка, ни переход не решаются в приёмке заново — иначе проверялась бы
 * копия правила, а не правило.
 *
 * Каждая проверка собирает композицию заново: она живёт внутри блока
 * `runComposeUiTest`, а шаги Gherkin — отдельные вызовы, и держать блок открытым между
 * ними нечем. Действие и проверка после него видят состояние мира до и после действия
 * соответственно.
 *
 * Имена лекарств в модели сценария — позиции `med-N` (НФТ-19), а сценарий называет
 * лекарство по-русски. Поэтому узел карточки ищется по имени модели: [displayName]
 * переводит одно в другое.
 */
@OptIn(ExperimentalTestApi::class)
internal object TodayScreenProbe {

    /**
     * Ручные переключения текущего сценария и его мир.
     *
     * Мир сценария создаётся заново, а вместе с ним начинается и новый набор
     * переключений: смена мира — единственный признак нового сценария, который
     * виден стенду.
     */
    private var lastWorld: AcceptanceWorld? = null
    private var toggles = TodayScreenToggles()

    /** Состояние, которое продукт показал бы сейчас. */
    fun state(world: AcceptanceWorld): TodayUiState = TodayStand.state(world, togglesFor(world))

    private fun togglesFor(world: AcceptanceWorld): TodayScreenToggles {
        if (lastWorld !== world) {
            lastWorld = world
            toggles = TodayScreenToggles()
        }
        return toggles
    }

    /** Собрать экран и задать вопрос его композиции. */
    fun <T> ask(world: AcceptanceWorld, question: (ComposeUiTest) -> T): T {
        var answer: T? = null
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state(world), onIntent = {}) } }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /**
     * Действие человека, о котором говорит сценарий: экран отдаёт намерения,
     * стенд применяет их к миру.
     *
     * @return намерения, которые экран успел отдать: проверка «нажатие не отметило
     *   приём второй раз» — это утверждение о том, чего среди них не оказалось
     */
    fun act(world: AcceptanceWorld, action: (ComposeUiTest) -> Unit): List<TodayIntent> {
        val intents = mutableListOf<TodayIntent>()
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state(world), onIntent = intents::add) } }
            action(this)
            waitForIdle()
        }
        intents.forEach { apply(world, it) }
        return intents
    }

    /** Имя лекарства в модели экрана: сценарий зовёт «Магнием B6», карточка — `med-3`. */
    fun displayName(world: AcceptanceWorld, medication: String): String =
        world.medications.get(world.medicationNamed(medication)).name

    /**
     * Карточка названного лекарства.
     *
     * Имя ищется среди потомков, а не у самого узла: карточка — не узел слияния
     * семантики, и текст её строк остаётся у детей — так матчер годится и для
     * немёрженого дерева, которым читаются теги внутри карточки.
     */
    fun card(world: AcceptanceWorld, medication: String): SemanticsMatcher =
        hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(displayName(world, medication)))

    /** Узел внутри карточки названного лекарства. */
    fun insideCard(world: AcceptanceWorld, medication: String): SemanticsMatcher =
        hasAnyAncestor(card(world, medication))

    /** Ленты собранного экрана: первым текстом время, последним — метка справа. */
    fun lanes(test: ComposeUiTest): List<List<String>> = test
        .onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { textsOf(it) }

    /** Метка справа от ленты указанного времени: «Утро», «Вечер» или «Сейчас». */
    fun laneLabelOf(test: ComposeUiTest, time: String): String? = lanes(test).firstOrNull { it.first() == time }?.last()

    /**
     * Карточка лекарства стоит под разделителем указанного времени.
     *
     * Проверка по границам, а не по тегу: разделитель — сосед карточки в разметке,
     * общего родителя у них нет, и принадлежность видна только расположением.
     */
    fun assertCardInLane(world: AcceptanceWorld, test: ComposeUiTest, medication: String, time: String) {
        val card = test.onNode(card(world, medication), useUnmergedTree = true).fetchSemanticsNode()
        val lanes = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val header = lanes.first { time in textsOf(it) }
        check(card.boundsInRoot.top >= header.boundsInRoot.top) {
            "the card of $medication must sit under its lane $time"
        }
        val nextLane = lanes.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
        if (nextLane != null) {
            check(card.boundsInRoot.top < nextLane) { "the card of $medication must sit above the next lane" }
        }
    }

    /** Тексты всех узлов с тегом, в порядке разметки. */
    fun texts(test: ComposeUiTest, tag: String): List<String> = test
        .onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { textsOf(it) }

    /** Весь текст узла и его поддерева, в порядке разметки. */
    fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun apply(world: AcceptanceWorld, intent: TodayIntent) {
        when (intent) {
            is TodayIntent.MarkTaken -> mark(world, intent.intakeId, MarkResult.TAKEN)
            is TodayIntent.MarkSkipped -> mark(world, intent.intakeId, MarkResult.SKIPPED)
            is TodayIntent.MarkGroupTaken -> world.markGroup(intent.intakeIds, world.clock.nowLocal())
            is TodayIntent.TakePrn -> world.pressTakeOnDemand(nameOf(world, intent.medicationId))

            // Свёртка и разворот — зрительная часть состояния: в продукте её ведёт
            // `TodayViewModel` (`TDD.md §5.2`), в стенде — сам стенд.
            is TodayIntent.ExpandGroup -> toggles = toggles.copy(expandedGroups = toggles.expandedGroups + intent.time)
            is TodayIntent.ToggleSection -> toggles = toggles.copy(
                toggledSections = if (intent.partOfDay in toggles.toggledSections) {
                    toggles.toggledSections - intent.partOfDay
                } else {
                    toggles.toggledSections + intent.partOfDay
                },
            )

            TodayIntent.TogglePrnBlock -> toggles = toggles.copy(prnCollapsed = !toggles.prnCollapsed)

            // Переходы наружу состояние экрана не меняют: их разбирает маршрут, и
            // приёмка «Сегодня» их не проверяет.
            else -> Unit
        }
    }

    private fun mark(world: AcceptanceWorld, intakeId: String, result: MarkResult) {
        world.markFromScreen(intakeId, world.clock.nowLocal(), result)
    }

    private fun nameOf(world: AcceptanceWorld, medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }
}
