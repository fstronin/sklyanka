package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.graphics.Color
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
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.today.GroupMarkOutcome
import ru.stohastika.sklyanka.feature.today.TAG_LANE_HEADER
import ru.stohastika.sklyanka.feature.today.TodayIntent
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalTime

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

    /** Уход с «Сегодня»: подтверждение гаснет тем же `onScreenLeft`, что в продукте (ОВ-32). */
    fun leaveScreen() {
        clearConfirmation()
    }

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

    /** Собрать экран в оформлении чужого профиля и задать вопрос его композиции (ПРФ-11). */
    fun <T> askInProfile(world: AcceptanceWorld, accent: ProfileAccent, question: (ComposeUiTest) -> T): T {
        var answer: T? = null
        runComposeUiTest {
            setContent {
                SklyankaTheme(profile = accent) { TodayScreen(state = state(world), onIntent = {}) }
            }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }

    /** Акцент темы в названном оформлении — так же, как его читает кнопка (ПРФ-11). */
    fun accentIn(accent: ProfileAccent): Color {
        var captured: Color? = null
        runComposeUiTest {
            setContent { SklyankaTheme(profile = accent) { captured = SklyankaTheme.colors.accent } }
        }
        return requireNotNull(captured) { "the theme did not provide the accent" }
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

    /** Тексты всех узлов с тегом на собранном экране — без открытия блока `runComposeUiTest` у шага. */
    fun textsOnScreen(world: AcceptanceWorld, tag: String): List<String> = ask(world) { test -> texts(test, tag) }

    /** Весь текст узла и его поддерева, в порядке разметки. */
    fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    /**
     * Применить намерение экрана к миру.
     *
     * Окно отмены публикуется тем же предикатом `isCompleted` и той же формой текста,
     * что у `TodayViewModel` (SKL-30): стенд не пересказывает правило, а повторяет
     * единственную его последовательность — отметить, затем открыть окно.
     */
    fun apply(world: AcceptanceWorld, intent: TodayIntent) {
        when (intent) {
            is TodayIntent.MarkTaken -> {
                clearConfirmation()
                mark(world, intent.intakeId, MarkResult.TAKEN)
            }

            is TodayIntent.MarkSkipped -> {
                clearConfirmation()
                mark(world, intent.intakeId, MarkResult.SKIPPED)
            }

            is TodayIntent.MarkGroupTaken -> {
                clearConfirmation()
                markTheGroup(world, intent.intakeIds)
            }

            is TodayIntent.UndoMark -> {
                clearConfirmation()
                undo(world, intent.intakeIds)
            }

            is TodayIntent.TakePrn -> {
                clearConfirmation()
                world.pressTakeOnDemand(nameOf(world, intent.medicationId))
            }

            // Подтверждение, свёртка и разворот — зрительная часть состояния: в продукте
            // её ведёт `TodayViewModel` (`TDD.md §5.2`), в стенде — сам стенд.
            is TodayIntent.ArmGroupMark -> {
                // Арм — первое действие сценария «подтверждение снимается»: снимок
                // остатков берётся до него, им проверяется «остатки не изменились».
                world.rememberState()
                armConfirmation(world, intent.time)
            }

            TodayIntent.DismissGroupConfirm -> clearConfirmation()

            is TodayIntent.ToggleSection -> {
                clearConfirmation()
                toggles = toggles.copy(
                    toggledSections = if (intent.partOfDay in toggles.toggledSections) {
                        toggles.toggledSections - intent.partOfDay
                    } else {
                        toggles.toggledSections + intent.partOfDay
                    },
                )
            }

            TodayIntent.TogglePrnBlock -> {
                clearConfirmation()
                toggles = toggles.copy(prnCollapsed = !toggles.prnCollapsed)
            }

            // Переходы наружу состояние экрана не меняют: их разбирает маршрут, и
            // приёмка «Сегодня» их не проверяет.
            else -> Unit
        }
    }

    /**
     * Арм батча: подтверждение с зеркальным дедлайном.
     *
     * Десять секунд — срок `TodayViewModel` (`CONFIRM_WINDOW_MS`); сам таймер в стенде
     * не крутится, его заменяет сравнение с часами сценария ([TodayStand.state]).
     */
    private fun armConfirmation(world: AcceptanceWorld, time: LocalTime) {
        toggles = toggles.copy(
            confirming = time,
            confirmingUntil = world.clock.nowLocal().plusSeconds(CONFIRM_WINDOW_SECONDS),
        )
        // Проверки «ни один из трёх не отмечен» стоят сразу после арма: домену группа
        // ещё не предъявлена, и стенд запоминает её ожидающие приёмы сам (ОВ-32).
        world.intakes.lastArmedIds = world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter {
                it.isPending &&
                    it.scheduledAt.toLocalDate() == world.clock.today() &&
                    it.scheduledAt.toLocalTime() == time
            }
            .map { it.id }
    }

    /** Снятие подтверждения молча, как в `TodayViewModel`: приёмы не трогаются (ОВ-32). */
    private fun clearConfirmation() {
        toggles = toggles.copy(confirming = null, confirmingUntil = null)
    }

    private fun mark(world: AcceptanceWorld, intakeId: String, result: MarkResult) {
        world.markFromScreen(intakeId, world.clock.nowLocal(), result)
        val outcome = world.intakes.outcomes.last()
        // Отброшенная отметка — не новое действие человека: окно не открывается,
        // и срок снэкбара идёт от единственной состоявшейся (приёмка, строка 203).
        if (!outcome.isCompleted) return
        world.undoMarks.publish(
            intakeIds = listOf(intakeId),
            result = outcome.intake?.status?.let(MarkResult::of) ?: result,
            medicationNames = listOfNotNull(medicationNameOfIntake(world, intakeId)),
        )
    }

    private fun markTheGroup(world: AcceptanceWorld, intakeIds: List<String>) {
        when (val outcome = world.markGroup(intakeIds, world.clock.nowLocal())) {
            is GroupMarkOutcome.Applied -> if (outcome.changedIntakeIds.isNotEmpty()) {
                world.undoMarks.publish(
                    intakeIds = outcome.changedIntakeIds,
                    result = MarkResult.TAKEN,
                    medicationNames = outcome.changedIntakeIds.mapNotNull { medicationNameOfIntake(world, it) },
                )
            }

            is GroupMarkOutcome.Rejected -> Unit
        }
    }

    /** «Отменить»: окно закрывается синхронно, как в `TodayViewModel` (SKL-30). */
    private fun undo(world: AcceptanceWorld, intakeIds: List<String>) {
        world.undoMarks.clear()
        world.undo(intakeIds, world.clock.nowLocal())
    }

    private fun medicationNameOfIntake(world: AcceptanceWorld, intakeId: String): String? {
        val medicationId = world.intakes.get(intakeId)?.medicationId ?: return null
        return world.medications.byId(medicationId)?.name
    }

    private fun nameOf(world: AcceptanceWorld, medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    /** Срок подтверждения — зеркало `TodayViewModel.CONFIRM_WINDOW_MS` (TDD §14.2). */
    private const val CONFIRM_WINDOW_SECONDS = 10L
}
