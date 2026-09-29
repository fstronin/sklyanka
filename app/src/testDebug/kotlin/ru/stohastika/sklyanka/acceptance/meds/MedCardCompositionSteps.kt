package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.ui.component.TAG_INTAKE_CARD
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Шаги сценарной приёмки архивации и экрана «Сегодня» (SKL-10, `@composition`;
 * заведены SKL-26).
 *
 * Прогон идёт в `app`, потому что сценарию нужен экран «Сегодня»: он живёт
 * в `feature-today`, и только здесь доступен одновременно с миром карточки
 * (`SKL-26/TDD.md §4.3`). Состояние «Сегодня» собирает [TodayFromWorld] тем же доменом,
 * каким его собирает продукт: выдуманное состояние проверяло бы разметку.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class MedCardCompositionSteps(private val world: AcceptanceWorld) {

    @Тогда("^на экране «Сегодня» (.+?) приёмов «([^»]+)» нет$")
    fun todayHasNoIntakesOf(date: String, medication: String) {
        val day = world.dateOf(date)
        assertEquals("the screen must show the moment of the scenario", day, world.clock.today())
        val name = world.medicationNamed(medication)
        val model = world.medications.get(name).name
        runComposeUiTest {
            setContent { Pinned { TodayScreen(state = TodayFromWorld.state(world), onIntent = {}) } }
            onAllNodes(hasTestTag(TAG_INTAKE_CARD) and hasAnyDescendant(hasText(model)), useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

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
