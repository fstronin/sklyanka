package ru.stohastika.sklyanka.acceptance.today

import androidx.compose.ui.test.ExperimentalTestApi
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.feature.today.TAG_RELIABILITY_BANNER

/**
 * Экранные шаги приёмки баннера ненадёжности SKL-84 (группа `@screen`).
 *
 * «Сегодня» собирается из мира настоящим доменом ([TodayStand]): причина ненадёжности —
 * тот же `ReminderReliability` из четырёх состояний, что и в продукте, поэтому сценарий
 * проверяет правило старшинства, а не собственный пересказ (ОВ-107).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TodaySk84Steps(private val world: AcceptanceWorld) {

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек открывает «Сегодня»$")
    fun heOpensTodayAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        heOpensToday()
    }

    @Когда("^человек открывает «Сегодня»$")
    fun heOpensToday() {
        world.sweep(SweepReason.APP_START)
        TodayScreenProbe.ask(world) { }
    }

    @Тогда("^баннер ненадёжности называет причину «([^»]+)»$")
    fun theBannerNamesOneSeniorReason(reason: String) {
        val texts = TodayScreenProbe.textsOnScreen(world, TAG_RELIABILITY_BANNER)
        assertTrue(
            texts.any { it == reason },
            "the banner must name «$reason», one senior reason of four: $texts",
        )
    }

    @Тогда("^баннера ненадёжности на экране нет$")
    fun thereIsNoReliabilityBanner() {
        // Перерыв сам по себе строку в баннер не добавляет (ОВ-107): баннер говорит
        // о состоянии настроек, а не о событии с датой.
        val texts = TodayScreenProbe.textsOnScreen(world, TAG_RELIABILITY_BANNER)
        assertTrue(texts.isEmpty(), "the reliability banner must be gone: $texts")
    }
}
