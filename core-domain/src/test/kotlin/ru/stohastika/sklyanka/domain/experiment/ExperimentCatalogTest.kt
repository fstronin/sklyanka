package ru.stohastika.sklyanka.domain.experiment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Каталог экспериментов закрыт (ПМ-20/ПМ-26, SKL-83 TDD §6).
 *
 * Тест перечисляет первую волну явно: новое имя или новый вариант появляются здесь
 * только вместе с решением продакт-менеджера, а не «сами» из кода.
 */
class ExperimentCatalogTest {

    @Test
    fun `the first wave names exactly two text experiments`() {
        assertEquals(
            setOf("TODAY_EMPTY_STATE", "SETTINGS_TRANSFER_HINT"),
            ExperimentId.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `variant keys are a closed list with the base first`() {
        assertEquals(
            listOf("BASE", "A", "B", "C"),
            VariantKey.entries.map { it.name },
        )
    }

    @Test
    fun `every experiment keeps the base variant as its default`() {
        ExperimentId.entries.forEach { experiment ->
            assertEquals(VariantKey.BASE, experiment.base, "the default of ${experiment.name} must be base")
            assertTrue(
                VariantKey.BASE in experiment.variants,
                "${experiment.name} must declare the base variant",
            )
        }
    }

    @Test
    fun `every declared variant belongs to the closed key list`() {
        ExperimentId.entries.forEach { experiment ->
            assertTrue(
                experiment.variants.all { it in VariantKey.entries },
                "${experiment.name} declares a variant outside VariantKey",
            )
        }
    }

    @Test
    fun `the first wave experiments allow only the base and one alternative`() {
        assertEquals(setOf(VariantKey.BASE, VariantKey.A), ExperimentId.TODAY_EMPTY_STATE.variants)
        assertEquals(setOf(VariantKey.BASE, VariantKey.A), ExperimentId.SETTINGS_TRANSFER_HINT.variants)
    }
}
