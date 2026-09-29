package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.TAG_LANE_HEADER
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Шаги сценарной приёмки возврата из архива и «Сегодня» (SKL-11, `@composition`;
 * заведены SKL-26).
 *
 * Прогон идёт в `app`, потому что сценарию нужен экран «Сегодня»: он живёт
 * в `feature-today`, и только здесь доступен одновременно с миром архива
 * (`SKL-26/TDD.md §4.3`). Состояние «Сегодня» собирает [TodayFromWorld] тем же доменом,
 * каким его собирает продукт.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class ArchiveCompositionSteps(private val world: AcceptanceWorld) {

    @Тогда("^приём на (.+?), (\\d{2}:\\d{2}) виден на экране «Сегодня»$")
    fun thatIntakeIsShownOnToday(date: String, time: String) {
        val medication = world.medications.last
        val model = world.medications.get(medication).name
        val moment = world.localMoment(date, time)
        assertTrue(
            "the restore must have created the intake at $moment: ${world.intakes.of(medication)}",
            world.intakes.of(medication).any { it.scheduledAt == moment && it.isPending },
        )
        runComposeUiTest {
            setContent { Pinned { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            assertCardInLane(model, time, this)
        }
    }

    /**
     * Карточка стоит под разделителем указанного времени — как в лентах суток.
     *
     * Карточек у лекарства за сутки может быть несколько (утренняя и вечерняя),
     * поэтому проверяется, что хотя бы одна из них лежит в ленте названного времени.
     */
    private fun assertCardInLane(model: String, time: String, test: ComposeUiTest) {
        val cards = test
            .onAllNodes(hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(model)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("the screen must show a card of «$model»", cards.isNotEmpty())
        val lanes = test.onAllNodesWithTag(TAG_LANE_HEADER, useUnmergedTree = true).fetchSemanticsNodes()
        val header = lanes.first { time in textsOf(it) }
        val nextLane = lanes.map { it.boundsInRoot.top }.filter { it > header.boundsInRoot.top }.minOrNull()
        assertTrue(
            "the card of «$model» must stand under its lane $time: ${cards.map { it.boundsInRoot }}",
            cards.any { card ->
                card.boundsInRoot.top >= header.boundsInRoot.top &&
                    (nextLane == null || card.boundsInRoot.top < nextLane)
            },
        )
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    /** Язык интерфейса закреплён: даты и склонения читаются по-русски при любом телефоне (НФТ-43). */
    @Composable
    private fun Pinned(content: @Composable () -> Unit) {
        val pinned = LocalContext.current.withInterfaceLocale()
        CompositionLocalProvider(
            LocalContext provides pinned,
            LocalConfiguration provides pinned.resources.configuration,
        ) {
            SklyankaTheme { content() }
        }
    }
}
