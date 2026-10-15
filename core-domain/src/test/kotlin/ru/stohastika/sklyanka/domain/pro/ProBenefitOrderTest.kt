package ru.stohastika.sklyanka.domain.pro

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Порядок выгод и пункт-повод (Р-67 п. 2, Р-75 п. 3, Р-76 п. 2).
 */
class ProBenefitOrderTest {

    @Test
    fun `from the settings the order is the fixed order of the norm`() {
        assertEquals(
            listOf(
                ProBenefit.SCHEDULES,
                ProBenefit.WIDGETS,
                ProBenefit.REPORT,
                ProBenefit.ACCENT,
                ProBenefit.EXPORT,
                ProBenefit.PROFILES,
            ),
            ProPoint.SETTINGS.benefitOrder(),
        )
    }

    @Test
    fun `from the medication form the schedules come first`() {
        assertEquals(ProBenefit.SCHEDULES, ProPoint.MEDICATION_FORM.benefitOrder().first())
    }

    @Test
    fun `from the history the report comes first`() {
        assertEquals(ProBenefit.REPORT, ProPoint.HISTORY.benefitOrder().first())
    }

    @Test
    fun `from the widget setup the widgets come first and are listed once`() {
        val order = ProPoint.WIDGETS.benefitOrder()

        assertEquals(ProBenefit.WIDGETS, order.first())
        assertEquals(ProBenefit.entries.size, order.size)
        assertEquals(order.distinct(), order)
    }

    @Test
    fun `the contextual benefit is listed once`() {
        val order = ProPoint.MEDICATION_FORM.benefitOrder()

        assertEquals(ProBenefit.entries.size, order.size)
        assertEquals(order.distinct(), order)
    }

    @Test
    fun `the example stands only by the contextual benefit`() {
        val rows = ProPoint.MEDICATION_FORM.benefitRows()

        assertEquals(ProNotice.BENEFIT_SCHEDULES_EXAMPLE, rows.single { it.benefit == ProBenefit.SCHEDULES }.example)
        assertEquals(null, rows.single { it.benefit == ProBenefit.WIDGETS }.example)
    }

    @Test
    fun `from the settings no benefit carries an example`() {
        assertEquals(emptyList<ProNotice>(), ProPoint.SETTINGS.benefitRows().mapNotNull { it.example })
    }

    @Test
    fun `every benefit has its own short name`() {
        val names = ProPoint.SETTINGS.benefitRows().map { it.name }

        assertEquals(ProBenefit.entries.size, names.distinct().size)
    }
}
