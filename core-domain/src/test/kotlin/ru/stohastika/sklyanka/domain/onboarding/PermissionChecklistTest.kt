package ru.stohastika.sklyanka.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("The three-step chain counts the autostart step and never calls it verified")
class PermissionChecklistTest {

    private val readAt: Instant = Instant.parse("2026-09-25T10:00:00Z")

    private fun checklist(
        notifications: PermissionGrant = PermissionGrant.Granted(readAt),
        exactAlarms: PermissionGrant = PermissionGrant.Granted(readAt),
        autostart: AutostartState = AutostartState(confirmedByUser = false),
    ) = PermissionChecklist(notifications, exactAlarms, autostart)

    @Test
    fun `the three steps come in the fixed order of REMINDER-19`() {
        assertEquals(
            listOf(PermissionStep.NOTIFICATIONS, PermissionStep.EXACT_ALARMS, PermissionStep.AUTOSTART),
            checklist().steps.map { it.first },
        )
    }

    @Test
    fun `the chain is not complete while the autostart step is unconfirmed`() {
        // Оба разрешения Android выданы, но цепочка из трёх шагов пройдена на двух.
        val chain = checklist()

        assertFalse(chain.allResolved, "two of three steps is not a passed chain")
        assertEquals(1, chain.unresolvedCount)
        assertEquals(listOf(PermissionStep.AUTOSTART), chain.unresolved)
    }

    @Test
    fun `the chain is complete only when the person confirmed the autostart step too`() {
        val chain = checklist(autostart = AutostartState(confirmedByUser = true))

        assertTrue(chain.allResolved)
        assertEquals(0, chain.unresolvedCount)
        assertFalse(chain.remindersUnreliable)
    }

    @Test
    fun `one missing permission is counted as one and not as none`() {
        val chain = checklist(
            exactAlarms = PermissionGrant.Denied(canAsk = false),
            autostart = AutostartState(confirmedByUser = true),
        )

        assertEquals(1, chain.unresolvedCount)
        assertEquals(listOf(PermissionStep.EXACT_ALARMS), chain.unresolved)
    }

    @Test
    fun `a granted permission carries the moment it was read from the system`() {
        // «Выдано» — утверждение о прошлом длиной в один показ экрана: разрешение отзывают
        // в системных настройках, и момент чтения показывает, насколько свежи данные.
        val state = checklist().notifications

        assertEquals(PermissionGrant.Granted(readAt), state)
    }

    @Test
    fun `no combination of hints makes the autostart step verified by the system`() {
        val everything = AutostartState(confirmedByUser = true, hints = AutostartHint.entries.toSet())

        assertFalse(everything.verifiedBySystem, "REMINDER-20: the app never verifies EMUI autostart")
        assertEquals(AutostartHint.entries.size, everything.hints.size)
    }

    @Test
    fun `the autostart state is not a permission grant and has no granted variant`() {
        // Главное утверждение дизайна: ложно-зелёный третий шаг невозможен по типу,
        // а не по дисциплине вызова (TDD §5.1).
        val autostart: StepState = AutostartState(confirmedByUser = true)

        assertFalse(autostart is PermissionGrant, "the autostart step must not reuse the permission type")

        // Исчерпывающий when вместо рефлексии: третий вариант разрешения перестал бы
        // компилироваться здесь, а подставить сюда AutostartState нельзя — у него
        // другой тип. То есть «выдано» для шага автозапуска не выражается вовсе.
        val variants = listOf(PermissionGrant.Granted(readAt), PermissionGrant.Denied(canAsk = true)).map { grant ->
            when (grant) {
                is PermissionGrant.Granted -> "Granted"
                is PermissionGrant.Denied -> "Denied"
            }
        }
        assertEquals(listOf("Granted", "Denied"), variants)
    }

    @Test
    fun `the step count comes from the enumeration and not from a literal`() {
        assertEquals(PermissionStep.entries.size, PermissionChecklist.STEP_COUNT)
        assertEquals(3, PermissionChecklist.STEP_COUNT)
    }

    @Test
    fun `the permissions screen is the second of three onboarding steps as the mock-up shows it`() {
        // ОВ-51: первый шаг в макетах не нарисован, и приложение его не придумывает.
        assertEquals(3, OnboardingFlow.TOTAL_STEPS)
        assertEquals(2, OnboardingFlow.PERMISSIONS_STEP)
    }

    @Test
    fun `the step indicator belongs to onboarding and not to the screen`() {
        assertTrue(OnboardingSource.FIRST_LAUNCH.showsStepIndicator)
        assertFalse(OnboardingSource.SETTINGS.showsStepIndicator, "re-entry from settings shows no indicator")
        assertFalse(OnboardingSource.TODAY_WARNING.showsStepIndicator)
    }

    @Test
    fun `an unresolved chain is what raises the unreliable-reminders warning`() {
        assertTrue(checklist(notifications = PermissionGrant.Denied(canAsk = true)).remindersUnreliable)
        assertTrue(checklist().remindersUnreliable)
    }

    @Test
    fun `the chain has exactly three steps and Do Not Disturb is not one of them`() {
        // Критерий приёмки 19, НАП-17: доступ к политике «Не беспокоить» запрашивается
        // отдельно, на экране настроек напоминаний, и в цепочку онбординга не входит.
        // Проверяется составом перечисления: шаг, которого нет, запросить нечем.
        assertEquals(3, PermissionStep.entries.size)
        assertFalse(PermissionStep.entries.any { it.name.contains("DND") })
        assertFalse(PermissionStep.entries.any { it.name.contains("POLICY") })
    }

    @Test
    fun `the state of one named step is readable without walking the list`() {
        val chain = checklist(notifications = PermissionGrant.Denied(canAsk = true))

        assertEquals(PermissionGrant.Denied(canAsk = true), chain.state(PermissionStep.NOTIFICATIONS))
        assertEquals(PermissionGrant.Granted(readAt), chain.state(PermissionStep.EXACT_ALARMS))
        assertEquals(AutostartState(confirmedByUser = false), chain.state(PermissionStep.AUTOSTART))
    }
}
