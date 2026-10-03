package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Продуктовые пределы хранения журнала (ПМ-30, НФТ-51).
 *
 * Числа проверяются отдельно от сценария ротации: сценарий обязан соблюдать пределы,
 * а не объявлять их; расхождение здесь — это разошедшийся продукт, а не дефект теста.
 */
class ReminderLogRetentionTest {

    @Test
    fun `the product keeps ninety days and two megabytes`() {
        val retention = ReminderLogRetention.DEFAULT

        assertEquals(Duration.ofDays(90), retention.maxAge, "NFR-51 allows no less than 30 days; PM-30 sets 90")
        assertEquals(Duration.ofDays(7), retention.protectedWindow, "PM-30: the last seven days are untouchable")
        assertEquals(2L * 1024 * 1024, retention.maxBytes, "NFR-51 caps the journal at two megabytes")
    }

    @Test
    fun `the age limit stays above the protected window`() {
        // Возрастная подрезка обязана начинаться позже неприкосновенного окна: иначе
        // «удаляем старше N суток» и «не трогаем последние 7 суток» противоречили бы.
        assertTrue(
            ReminderLogRetention.MAX_AGE > ReminderLogRetention.PROTECTED_WINDOW,
            "age limit ${ReminderLogRetention.MAX_AGE} must exceed ${ReminderLogRetention.PROTECTED_WINDOW}",
        )
    }

    @Test
    fun `the protected window matches the on time summary window`() {
        // Окно неприкосновенности существует ради сводки «вовремя за 7 дней» (НФТ-51):
        // разъехавшись, эти два числа молча ослепили бы диагностику.
        assertEquals(
            ReminderOnTimeRule.WINDOW,
            ReminderLogRetention.PROTECTED_WINDOW,
            "the protected window must be the diagnostic window",
        )
    }
}
