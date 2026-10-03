package ru.stohastika.sklyanka.domain.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Правило «когда можно отправлять» (TDD §7.2).
 *
 * Единственное место, где записано разрешение: согласие принято и его версия совпадает
 * с текущим предметом. Всё остальное — молчание, включая сохранённую версию больше
 * текущей (откат релиза).
 */
@DisplayName("Only an accepted consent of the current material version allows sending")
class AnalyticsConsentTest {

    @Test
    fun `the default state is not asked and forbids sending`() {
        val consent = AnalyticsConsent.NOT_ASKED

        assertFalse(consent.sendingAllowed(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `an accepted consent of the current version allows sending`() {
        val consent = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)

        assertTrue(consent.sendingAllowed(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `an accepted consent of an older version stays silent until the decision is made again`() {
        val consent = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL - 1)

        assertFalse(consent.sendingAllowed(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `an accepted consent of a newer version stays silent too, so a rollback cannot resume sending`() {
        val consent = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL + 1)

        assertFalse(consent.sendingAllowed(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `a declined consent forbids sending at the current version as well`() {
        val consent = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)

        assertFalse(consent.sendingAllowed(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `not asked needs a decision`() {
        assertTrue(AnalyticsConsent.NOT_ASKED.needsDecision(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `a decision of the current version does not need to be asked again`() {
        val accepted = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        val declined = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)

        assertFalse(accepted.needsDecision(AnalyticsConsentVersion.MATERIAL))
        assertFalse(declined.needsDecision(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `an outdated material version asks again whatever the old decision was`() {
        val accepted = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL - 1)
        val declined = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL - 1)

        assertTrue(accepted.needsDecision(AnalyticsConsentVersion.MATERIAL))
        assertTrue(declined.needsDecision(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `the first ask is named as the first launch`() {
        val source = AnalyticsConsent.NOT_ASKED.pendingSource(AnalyticsConsentVersion.MATERIAL)

        assertEquals(ConsentSource.FIRST_LAUNCH, source)
    }

    @Test
    fun `a stale decision is named as a policy update`() {
        val accepted = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL - 1)
        val declined = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL - 1)

        assertEquals(ConsentSource.POLICY_UPDATE, accepted.pendingSource(AnalyticsConsentVersion.MATERIAL))
        assertEquals(ConsentSource.POLICY_UPDATE, declined.pendingSource(AnalyticsConsentVersion.MATERIAL))
    }

    @Test
    fun `a current decision needs no show and has no source`() {
        val accepted = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        val declined = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)

        assertNull(accepted.pendingSource(AnalyticsConsentVersion.MATERIAL))
        assertNull(declined.pendingSource(AnalyticsConsentVersion.MATERIAL))
    }
}
