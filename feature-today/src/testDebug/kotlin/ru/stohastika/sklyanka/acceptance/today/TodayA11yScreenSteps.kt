package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.today.TAG_GROUP_MARK_ALL
import ru.stohastika.sklyanka.ui.component.TAG_MARK_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_MISSED_STATUS
import ru.stohastika.sklyanka.ui.component.TAG_PRIMARY_BUTTON
import ru.stohastika.sklyanka.ui.component.TAG_TAKEN_STATUS
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Экранные шаги приёмки доступности «Сегодня» (SKL-08, группа `@a11y`; заведены
 * SKL-26).
 *
 * Подпись кнопки без текста и подпись статуса собирает дизайн-система из ресурсов
 * (НФТ-30, НФТ-33); шаг читает собранную композицию. Лекарства, которых нет в фоне
 * сценария, заводятся Предысторией: структура называет четыре разных названия,
 * и каждое обязано дойти до семантики.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayA11yScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var focusedMedication: String = ""
    private var spoken: String = ""

    // --- Кнопки без текста -----------------------------------------------------

    @Допустим("^на экране «Сегодня» (.+?) показан приём «([^»]+)»$")
    fun thatIntakeIsShownOnTheScreen(date: String, medication: String) {
        world.clock.moveTo(world.dateOf(date).atTime(world.clock.nowLocal().toLocalTime()))
        focusedMedication = ensurePendingIntake(medication)
        TodayScreenProbe.ask(world) { test ->
            test.onNode(TodayScreenProbe.card(world, focusedMedication), useUnmergedTree = true).fetchSemanticsNode()
        }
    }

    @Когда("^программа чтения с экрана читает кнопку отметки этого приёма$")
    fun theScreenReaderReadsTheMarkButton() {
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNode(
                hasTestTag(TAG_MARK_BUTTON) and TodayScreenProbe.insideCard(world, focusedMedication),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            spoken = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
        }
    }

    @Тогда("^она произносит «([^»]+)»$")
    fun itSaysThat(phrase: String) {
        // Продукт подставляет в шаблон позицию лекарства (`med-N`), сценарий называет
        // человека по имени: сверяется шаблон, а имя подставляется тем, которым экран
        // его называет (НФТ-19, НФТ-30).
        val model = TodayScreenProbe.displayName(world, focusedMedication)
        val product = context.getString(UiR.string.cd_mark_intake, model)
        assertEquals("the screen reader must speak the label of the button", product, spoken)
        val prefix = product.substringBefore(model)
        val suffix = product.substringAfter(model)
        assertTrue(
            "the scenario must name the same template with the medication in its case: «$phrase»",
            phrase.startsWith(prefix) &&
                phrase.endsWith(suffix) &&
                phrase.removePrefix(prefix).removeSuffix(suffix).isNotBlank(),
        )
    }

    // --- Вопрос подтверждения читается словами (НФТ-30/31, ОВ-32) ---------------------------

    @Тогда("^программа чтения объявляет вопрос «([^»]+)»$")
    fun theScreenReaderAnnouncesTheQuestion(question: String) {
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNode(
                hasTestTag(TAG_PRIMARY_BUTTON) and hasAnyAncestor(hasTestTag(TAG_GROUP_MARK_ALL)),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            // Вопрос временный и фокус чтения не переводится: объявляется Polite,
            // и только в подтверждении (08-design-system.md §4.3).
            assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
            assertTrue(
                "the announced question must be «$question»: ${TodayScreenProbe.textsOf(node)}",
                question in TodayScreenProbe.textsOf(node),
            )
        }
    }

    // --- Статус без цвета ------------------------------------------------------

    @Когда("^человек смотрит на группу «([^»]+)» в (\\d{2}:\\d{2})$")
    fun thePersonLooksAtTheGroupAt(group: String, time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        TodayScreenProbe.ask(world) { }
    }

    @Тогда("^у принятого приёма показано фактическое время отметки со значком$")
    fun theTakenIntakeShowsTheMomentOfTheMark() {
        // Значок галочки — отрисовка того же состояния, что несёт тег статуса: в семантике
        // остаются состояние и фактическое время отметки (НФТ-33).
        val intake = intakeWithStatus(IntakeStatus.TAKEN)
        val expected = requireNotNull(intake.actedAt) { "a taken intake must carry its moment" }
            .atZone(world.clock.zone)
            .toLocalTime()
            .format(TIME)
        TodayScreenProbe.ask(world) { test ->
            val status = test.onNode(
                hasTestTag(
                    TAG_TAKEN_STATUS,
                ) and TodayScreenProbe.insideCard(world, scenarioNameOf(intake.medicationId)),
                useUnmergedTree = true,
            )
            val texts = TodayScreenProbe.textsOf(status.fetchSemanticsNode())
            assertTrue("the taken card must show the moment $expected of the mark: $texts", expected in texts)
        }
    }

    @Тогда("^у пропущенного приёма статус подписан текстом$")
    fun theMissedIntakeStatusIsWrittenOut() {
        TodayScreenProbe.ask(world) { test ->
            val status = test.onNode(
                hasTestTag(TAG_MISSED_STATUS) and
                    TodayScreenProbe.insideCard(
                        world,
                        scenarioNameOf(intakeWithStatus(IntakeStatus.MISSED).medicationId),
                    ),
                useUnmergedTree = true,
            )
            val texts = TodayScreenProbe.textsOf(status.fetchSemanticsNode()).filter { it.isNotBlank() }
            assertTrue("the missed card must spell its status out: $texts", texts.isNotEmpty())
        }
    }

    @Тогда("^различить их можно без восприятия цвета$")
    fun theTwoStatesAreToldApartWithoutColour() {
        // Принятый и пропущенный различаются текстом: у одного — время отметки, у другого —
        // подпись статуса. Цвет не участвует, и монохромный режим ничего не меняет (НФТ-33).
        val taken = statusTexts(IntakeStatus.TAKEN)
        val missed = statusTexts(IntakeStatus.MISSED)
        assertTrue("the taken status must be written out: $taken", taken.isNotBlank())
        assertTrue("the missed status must be written out: $missed", missed.isNotBlank())
        assertNotEquals("the two states must differ by text, not by colour", taken, missed)
    }

    // --- Обвязка ---------------------------------------------------------------

    private fun statusTexts(status: IntakeStatus): String {
        val tag = if (status == IntakeStatus.TAKEN) TAG_TAKEN_STATUS else TAG_MISSED_STATUS
        var text = ""
        TodayScreenProbe.ask(world) { test ->
            val node = test.onNode(
                hasTestTag(tag) and
                    TodayScreenProbe.insideCard(world, scenarioNameOf(intakeWithStatus(status).medicationId)),
                useUnmergedTree = true,
            )
            text = TodayScreenProbe.textsOf(node.fetchSemanticsNode()).filter { it.isNotBlank() }.joinToString(" ")
        }
        return text
    }

    /** Приём группы «08:00» в названном состоянии: у сценария их ровно по одному. */
    private fun intakeWithStatus(status: IntakeStatus): Intake = world.intakes
        .ofProfile(world.profiles, world.profiles.active)
        .single { it.scheduledAt.toLocalTime() == GROUP_TIME && it.status == status }

    private fun scenarioNameOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    /**
     * Лекарство структурного примера и его ожидающийся приём.
     *
     * Фон объявляет не все названия структуры: «Креон» и «Фолиевая кислота» заводятся
     * здесь — с приёмом впереди текущего момента, чтобы карточка показывала кнопку отметки.
     */
    private fun ensurePendingIntake(medication: String): String {
        val known = RussianNames.resolve(medication, world.medications.names())
        if (known == null) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = listOf(AHEAD),
            )
            world.plan()
        }
        val name = known ?: medication
        val today = world.clock.today()
        if (world.intakes.pendingOf(name).none { it.scheduledAt.toLocalDate() == today }) {
            world.intakes.pending(name, today.atTime(AHEAD))
        }
        return name
    }

    private companion object {
        val GROUP_TIME: LocalTime = LocalTime.of(8, 0)

        /** Время приёма заведённого структурой лекарства: впереди момента сценария. */
        val AHEAD: LocalTime = LocalTime.of(22, 0)

        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
