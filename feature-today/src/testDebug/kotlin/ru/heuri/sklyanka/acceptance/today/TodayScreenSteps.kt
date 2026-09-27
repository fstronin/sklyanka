package ru.heuri.sklyanka.acceptance.today

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.steps.dateOf
import ru.heuri.sklyanka.acceptance.steps.medicationNamed
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.heuri.sklyanka.feature.today.TAG_LANE_HEADER
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalTime

/**
 * Экранные шаги приёмки «Сегодня» (SKL-08, группа `@screen`; заведены SKL-26).
 *
 * Состояние экрана собирает настоящий домен из мира сценария ([TodayStand]), а проверки
 * задают вопросы собранной композиции. Выдуманное состояние проверяло бы разметку,
 * а приёмка задачи говорит о продукте: какие приёмы попали в сутки, как они разложены
 * по времени и что сказано человеку числом.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodayScreenSteps(private val world: AcceptanceWorld) {

    // --- Действия --------------------------------------------------------------

    @Когда("^человек открывает экран «Сегодня» (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonOpensTodayOnThatDate(date: String, time: String) {
        world.clock.moveTo(world.dateOf(date).atTime(LocalTime.parse(time)))
    }

    @Когда("^человек открывает экран «Сегодня» в (\\d{2}:\\d{2})$")
    fun thePersonOpensTodayAt(time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
    }

    @Когда("^человек открывает экран «Сегодня» (.+?) в (\\d{2}:\\d{2}) в профиле «([^»]+)»$")
    fun thePersonOpensTodayInThatProfile(date: String, time: String, profile: String) {
        world.profiles.use(profile)
        world.clock.moveTo(world.dateOf(date).atTime(LocalTime.parse(time)))
    }

    // --- Шапка и ленты ---------------------------------------------------------

    @Тогда("^в шапке показана дата «([^»]+)» и заголовок «([^»]+)»$")
    fun theHeaderShowsThatDateAndTitle(date: String, title: String) {
        val header = texts(TAG_SCREEN_HEADER)
        // Шапка набирает дату и надпись капителью — это типографика, а не другой текст.
        assertTrue(header.any { it.equals(date, ignoreCase = true) }, "no date «$date» in the header: $header")
        assertTrue(header.any { it.equals(title, ignoreCase = true) }, "no title «$title» in the header: $header")
    }

    @Тогда("^приёмы разложены по группам времени «([^»]+)», «([^»]+)» и «([^»]+)»$")
    fun theIntakesAreLaidOutInThoseTimeGroups(first: String, second: String, third: String) {
        val wanted = listOf(first, second, third)
        val shown = lanes().map { it.first() }
        assertEquals(wanted, shown, "the lanes of the day must be exactly $wanted, in this order")
    }

    @Тогда("^у группы «([^»]+)» справа стоит метка «([^»]+)»$")
    fun theGroupCarriesThatLabel(time: String, label: String) {
        assertEquals(label, laneLabelOf(time), "the label of the lane $time")
    }

    @Тогда("^у группы «([^»]+)» вместо метки времени суток стоит метка «([^»]+)»$")
    fun theGroupCarriesTheNowLabel(time: String, label: String) {
        assertEquals(label, laneLabelOf(time), "the label of the current lane $time among ${lanes()}")
    }

    @Тогда("^у групп «([^»]+)» и «([^»]+)» метки «([^»]+)» нет$")
    fun thoseGroupsDoNotCarryThatLabel(first: String, second: String, label: String) {
        listOf(first, second).forEach { time ->
            assertFalse(laneLabelOf(time) == label, "the lane $time must not be marked «$label»")
        }
    }

    // --- Прогресс дня ----------------------------------------------------------

    @Тогда("^под шапкой показана подпись «([^»]+)»$")
    fun theCaptionUnderTheHeaderIs(caption: String) {
        val shown = texts(TAG_DAY_PROGRESS)
        assertTrue(shown.any { it == caption }, "the day progress must read «$caption», it reads $shown")
    }

    @Тогда("^приём «([^»]+)» в это число не входит ни числителем, ни знаменателем$")
    fun thatOnDemandIntakeIsCountedNowhere(name: String) {
        // Приём по потребности в плане суток не существует вовсе (ПР-4): проверяется не
        // «его не видно в подписи», а что и план, и принятое посчитаны без него.
        val medication = world.medications.get(world.medicationNamed(name))
        assertTrue(medication.maxPerDay != null, "«$name» must be an on-demand medication")
        val progress = TodayStand.progress(world)
        val scheduled = scheduledOfTheDay().filterNot { it.medicationId == medication.id }
        assertEquals(scheduled.size, progress.planned, "the plan of the day must not count on-demand intakes")
        assertEquals(
            scheduled.count { it.status == IntakeStatus.TAKEN },
            progress.taken,
            "the taken count of the day must not count on-demand intakes",
        )
    }

    @Тогда("^в подписи прогресса дня этот приём не учтён$")
    fun theForeignIntakeIsNotCounted() {
        assertEquals(
            scheduledOfTheDay().size,
            TodayStand.progress(world).planned,
            "the day progress must count the active profile only (ПРФ-10)",
        )
    }

    /** Приёмы по расписанию активного профиля на сутки сценария. */
    private fun scheduledOfTheDay() = world.intakes.ofProfile(world.profiles, world.profiles.active)
        .filter { it.scheduledAt.toLocalDate() == world.clock.today() }
        .filter { intake ->
            world.medications.byId(intake.medicationId)?.scheduleMode == ScheduleMode.SCHEDULED
        }

    // --- Состав списка ---------------------------------------------------------

    @Тогда("^в списке нет приёма «([^»]+)»$")
    fun theListHasNoIntakeOfThatMedication(name: String) {
        val medication = world.medications.get(world.medicationNamed(name))
        val shown = TodayStand.shownMedicationIds(world)
        assertFalse(
            medication.id in shown,
            "an intake of another profile must never appear on the screen (ПРФ-10)",
        )
    }

    // --- Обвязка ---------------------------------------------------------------

    /** Метка справа от ленты указанного времени. */
    private fun laneLabelOf(time: String): String? = lanes().firstOrNull { it.first() == time }?.last()

    /** Ленты собранного экрана: первым текстом время, последним — метка справа. */
    private fun lanes(): List<List<String>> = onScreen { test ->
        test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> textsOf(node) }
    }

    private fun texts(tag: String): List<String> = onScreen { test ->
        test.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> textsOf(node) }
    }

    /** Весь текст узла и его поддерева, в порядке разметки. */
    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun <T> onScreen(question: (ComposeUiTest) -> T): T {
        val state = TodayStand.state(world)
        var answer: T? = null
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state, onIntent = {}) } }
            answer = question(this)
        }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }
}
