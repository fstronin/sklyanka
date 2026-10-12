package ru.stohastika.sklyanka.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The vendor steps of a profile are one table: Huawei five, Samsung four, a stranger two")
class VendorStepsTest {

    @Test
    fun `Huawei carries the EMUI trio with the app-startup step first`() {
        assertEquals(
            listOf(PermissionStep.APP_STARTUP, PermissionStep.BATTERY_SAVING, PermissionStep.RECENTS_PIN),
            VendorSteps.of(VendorProfile.EMUI),
        )
    }

    @Test
    fun `Xiaomi carries the same trio under HyperOS words`() {
        // Состав тот же, что у EMUI, но шаг зовётся «Автозапуск»: слова профиля живут
        // в строках, а порядок и состав — здесь (НАП-19).
        assertEquals(
            listOf(PermissionStep.APP_STARTUP, PermissionStep.BATTERY_SAVING, PermissionStep.RECENTS_PIN),
            VendorSteps.of(VendorProfile.XIAOMI),
        )
    }

    @Test
    fun `Samsung has no separate app-startup step`() {
        assertEquals(
            listOf(PermissionStep.BATTERY_SAVING, PermissionStep.RECENTS_PIN),
            VendorSteps.of(VendorProfile.SAMSUNG),
        )
    }

    @Test
    fun `an unknown firmware gets no vendor steps of someone else`() {
        assertTrue(VendorSteps.of(VendorProfile.GENERIC).isEmpty())
    }

    @Test
    fun `no profile names a common step as a vendor one`() {
        val common = listOf(PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS)

        VendorProfile.entries.forEach { profile ->
            val vendor = VendorSteps.of(profile)
            assertTrue(
                vendor.none { it in common },
                "$profile: a common step must not stand in the vendor table",
            )
            assertEquals(vendor.distinct(), vendor, "$profile: a step is named twice")
        }
    }
}
