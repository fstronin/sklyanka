package ru.stohastika.sklyanka.startup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Порядок первого запуска (ПМ-25).
 *
 * Правило проверяется без активности: онбординг разрешений показывается только тогда,
 * когда его требует маршрут запуска, а ярлык «Добавить лекарство» остаётся сильнее
 * самопоказа.
 */
class LaunchStepTest {

    @Test
    fun `a fresh start shows the reliability onboarding`() {
        assertEquals(
            LaunchStep.ONBOARDING,
            launchStep(
                onboardingDestination = true,
                onboardingUnseen = true,
                onboardingDone = false,
                addMedicationRequested = false,
            ),
        )
    }

    @Test
    fun `a seen onboarding goes straight to the root`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                onboardingDestination = true,
                onboardingUnseen = true,
                onboardingDone = true,
                addMedicationRequested = false,
            ),
        )
    }

    @Test
    fun `the add medication shortcut beats the onboarding`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                onboardingDestination = true,
                onboardingUnseen = true,
                onboardingDone = false,
                addMedicationRequested = true,
            ),
        )
    }

    @Test
    fun `a route without the onboarding destination opens the root`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                onboardingDestination = false,
                onboardingUnseen = true,
                onboardingDone = false,
                addMedicationRequested = false,
            ),
        )
    }

    @Test
    fun `a healthy start shows neither the onboarding nor anything else`() {
        assertEquals(
            LaunchStep.ROOT,
            launchStep(
                onboardingDestination = false,
                onboardingUnseen = false,
                onboardingDone = false,
                addMedicationRequested = false,
            ),
        )
    }
}
