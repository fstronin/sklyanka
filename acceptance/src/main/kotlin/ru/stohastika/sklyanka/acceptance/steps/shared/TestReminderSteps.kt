package ru.stohastika.sklyanka.acceptance.steps.shared

import io.cucumber.java.ru.И
import io.cucumber.java.ru.Но
import org.junit.jupiter.api.Assertions.assertEquals
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld

/**
 * Общий словарь: пробное напоминание не оставляет следа (НАП-22).
 *
 * Эти строки пишут две задачи: SKL-13 проверяет ими диагностику («Пробное напоминание
 * приходит через минуту и не трогает историю»), SKL-14 — экран разрешений («Пробное
 * напоминание не оставляет следа в истории приёмов»). Объявлены они по одному разу здесь,
 * потому что два объявления одного текста погасили бы прогон целиком.
 *
 * Проверки читают снимок, снятый действием сценария ([AcceptanceWorld.rememberState],
 * [AcceptanceWorld.rememberAdherence]): шаг, который сам снимает снимок, ничего не проверяет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class TestReminderSteps(private val world: AcceptanceWorld) {

    /** Ни одна запись о приёме не появилась: хранилище приёмов то же, что было до действия. */
    @Но("^записи о приёме не появляется$")
    fun noIntakeAppears() {
        assertEquals(
            world.before,
            world.intakes.all().associateBy { it.id },
            "the action must not write a single intake (НАП-22)",
        )
    }

    /** Приверженность за названный день не изменилась — считается настоящим доменом (SKL-18). */
    @И("^приверженность за (.+?) не меняется$")
    fun theAdherenceDoesNotChange(date: String) {
        val before = requireNotNull(world.adherenceBefore) {
            "the scenario action must call rememberAdherence before changing anything"
        }
        assertEquals(before, world.adherenceOn(world.dateOf(date)), "the adherence of $date must stay")
    }

    /** Ни один остаток упаковки не изменился: списания не было ни у одного лекарства. */
    @И("^остаток ни одной упаковки не меняется$")
    fun noStockChanges() {
        world.medications.names().forEach { name ->
            assertEquals(
                world.medications.stockBefore(name),
                world.medications.stockOf(name),
                "the stock of $name must stay",
            )
        }
    }
}
