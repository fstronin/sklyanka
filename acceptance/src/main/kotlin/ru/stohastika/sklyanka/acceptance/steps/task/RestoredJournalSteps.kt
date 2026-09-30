package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.DeferredReason
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent

/**
 * Шаги приёмки SKL-47 — один факт восстановления, одна запись журнала.
 *
 * Гонку двух пересборок сценарий воспроизводит их общим стартовым состоянием: обе
 * считают разницу по одному и тому же пустому набору будильников. Второе стирание —
 * модель того, что пересборка по запуску началась раньше, чем пересборка по перезагрузке
 * поставила будильник; записи базы оно не трогает.
 *
 * Саму пересборку зовёт общий шаг SKL-35 «приложение восстанавливает план напоминаний»:
 * второй копии правила в шагах нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class RestoredJournalSteps(private val world: AcceptanceWorld) {

    @Допустим("^будильники системы стёрты перезагрузкой$")
    fun theSystemAlarmsAreWipedByTheReboot() {
        world.reminders.simulateReboot()
    }

    @И("^пересборка по запуску видит будильники ещё не поставленными$")
    fun theStartupRebuildSeesTheAlarmsNotArmedYet() {
        world.reminders.simulateReboot()
    }

    @Тогда("^в журнале ровно (\\d+) (?:запись|записи|записей) о восстановлении$")
    fun exactlyThatManyRestorationsAreInTheJournal(count: Int) {
        val restored = runBlocking { world.reminders.journal.recent(RECENT_LIMIT) }
            .filter { it.event == ReminderEvent.DEFERRED && it.reason == DeferredReason.RESTORED }
        assertEquals(count, restored.size, "restored entries in the journal")
    }

    private companion object {
        const val RECENT_LIMIT = 100
    }
}
