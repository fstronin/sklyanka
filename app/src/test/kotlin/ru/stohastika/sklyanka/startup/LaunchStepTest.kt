package ru.stohastika.sklyanka.startup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Порядок первого запуска (SKL-165, АН-1, ПМ-25).
 *
 * Правило проверяется без активности: экран согласия стоит после диалога политики
 * и до онбординга разрешений, а ярлык «Добавить лекарство» остаётся сильнее самопоказа.
 */
class LaunchStepTest {

    @Test
    fun `the analytics question comes before the reliability onboarding`() {
        assertEquals(
            LaunchStep.ANALYTICS_CONSENT,
            launchStep(
                analyticsConsentUnseen = true,
                onboardingDestination = true,
                onboardingUnseen = true,
                onboardingDone = false,
                addMedicationRequested = false,
            ),
        )
    }

    @Test
    fun `a decided question lets the onboarding start`() {
        assertEquals(
            LaunchStep.ONBOARDING,
            launchStep(
                analyticsConsentUnseen = false,
                onboardingDestination = true,
                onboardingUnseen = true,
                onboardingDone = false,
                addMedicationRequested = false,
            ),
        )
    }

    @Test
    fun `a refusal does not gate anything and the app opens Today`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                analyticsConsentUnseen = false,
                onboardingDestination = false,
                onboardingUnseen = false,
                onboardingDone = true,
                addMedicationRequested = false,
            ),
        )
    }

    @Test
    fun `the add medication shortcut beats the analytics question`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                analyticsConsentUnseen = true,
                onboardingDestination = true,
                onboardingUnseen = true,
                onboardingDone = false,
                addMedicationRequested = true,
            ),
        )
    }

    @Test
    fun `an onboarding already seen is not shown again`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                analyticsConsentUnseen = false,
                onboardingDestination = true,
                onboardingUnseen = true,
                onboardingDone = true,
                addMedicationRequested = false,
            ),
        )
    }

    @Test
    fun `a healthy start neither asks nor shows the onboarding`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                analyticsConsentUnseen = false,
                onboardingDestination = false,
                onboardingUnseen = false,
                onboardingDone = false,
                addMedicationRequested = false,
            ),
        )
    }
}
