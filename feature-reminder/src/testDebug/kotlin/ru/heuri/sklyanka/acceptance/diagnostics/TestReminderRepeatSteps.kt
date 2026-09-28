package ru.heuri.sklyanka.acceptance.diagnostics

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.feature.reminder.TAG_TEST_REMINDER

/**
 * Экранные шаги приёмки SKL-14 про повторное пробное напоминание (группа `@diagnostics`).
 *
 * Шаги живут в `feature-reminder`, потому что говорят об экране диагностики: повторная
 * постановка пробного напоминания — его кнопка (НАП-23). Состояние собирает тот же стенд
 * `DiagnosticsStand`, что у SKL-13 и SKL-20: второй копии экрана в приёмке нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class TestReminderRepeatSteps(private val world: AcceptanceWorld) {

    /** Пробное напоминание доступно повторно и в любой момент — из диагностики (НАП-23). */
    @Тогда("^он может запустить пробное напоминание ещё раз$")
    fun thePersonCanStartTheTestReminderAgain() {
        DiagnosticsStand.act(world) { onNodeWithTag(TAG_TEST_REMINDER).performScrollTo().performClick() }
        assertNotNull(
            world.reminders.testReminderAt(),
            "the diagnostics screen must schedule the test reminder again (НАП-23)",
        )
    }

    @И("^в (.+?), (\\d{2}:\\d{2}) оно приходит$")
    fun itArrives(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        assertTrue(world.reminders.deliverTest(), "the test alarm must fire at its minute")
        assertTrue(world.reminders.testReminderShown(), "the test reminder must be presented")
    }
}
