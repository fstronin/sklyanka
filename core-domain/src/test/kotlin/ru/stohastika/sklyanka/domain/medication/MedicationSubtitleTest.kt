package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import java.math.BigDecimal

/** Подпись титула карточки: «Таблетка круглая с риской · 48 мг · до еды» (ЭКР-7). */
class MedicationSubtitleTest {

    @Test
    fun `a tablet carries all four facts`() {
        val subtitle = MedicationSubtitle.of(Cards.scheduled())

        assertEquals(MedicationForm.TABLET, subtitle.form)
        assertEquals(TabletShape.ROUND_SCORED, subtitle.shape)
        assertEquals(BigDecimal("48"), subtitle.strength)
        assertEquals("mg", subtitle.strengthUnit)
        assertEquals(FoodRelation.BEFORE_MEAL, subtitle.foodRelation)
    }

    @Test
    fun `a form without a tablet shape gets a shorter subtitle`() {
        val subtitle = MedicationSubtitle.of(
            Cards.scheduled(form = MedicationForm.SPRAY, shape = TabletShape.ROUND),
        )

        // ПР-9: вид таблетки применим только к таблетке и капсуле, и в подпись спрея
        // он не попадает даже если по недосмотру записан в лекарство.
        assertNull(subtitle.shape)
    }

    @Test
    fun `a capsule keeps its shape`() {
        val subtitle = MedicationSubtitle.of(Cards.scheduled(form = MedicationForm.CAPSULE))

        assertEquals(TabletShape.ROUND_SCORED, subtitle.shape)
    }

    @Test
    fun `no relation to food means nothing is said about food`() {
        val subtitle = MedicationSubtitle.of(Cards.scheduled(foodRelation = FoodRelation.ANY))

        assertNull(subtitle.foodRelation)
    }

    @Test
    fun `strength shows up only together with its unit`() {
        val medication = Cards.scheduled().copy(strength = BigDecimal("48"), strengthUnit = null)

        val subtitle = MedicationSubtitle.of(medication)

        assertNull(subtitle.strength)
        assertNull(subtitle.strengthUnit)
    }
}
