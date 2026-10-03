package ru.stohastika.sklyanka.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.domain.undo.UndoableMark
import java.time.Instant

/**
 * Разбор действия ярлыка «Добавить лекарство» и маршрут запуска (SKL-80, ОВ-96).
 *
 * Имя действия и решение о маршруте проверяются без Activity: `AddMedicationLaunch`
 * хранит строку действия, `RootNavigation.openFromLaunch` — куда она ведёт. Ошибка здесь
 * стоила бы ярлыка, открывающего не тот экран.
 */
class AddMedicationLaunchTest {

    @Test
    fun `the shortcut action is recognized`() {
        assertTrue(AddMedicationLaunch.matches(AddMedicationLaunch.ACTION))
    }

    @Test
    fun `a foreign action is not the shortcut`() {
        assertFalse(AddMedicationLaunch.matches("android.intent.action.VIEW"))
    }

    @Test
    fun `a missing action is not the shortcut`() {
        assertFalse(AddMedicationLaunch.matches(null))
    }

    @Test
    fun `the shortcut opens the form of a new medication`() {
        val navigation = RootNavigation()

        assertTrue(navigation.openFromLaunch(AddMedicationLaunch.ACTION))

        assertEquals(MedsDestination.NewMedication, navigation.meds)
        assertFalse(navigation.showsToday)
    }

    @Test
    fun `a foreign action leaves the root on today`() {
        val navigation = RootNavigation()

        assertFalse(navigation.openFromLaunch("android.intent.action.VIEW"))

        assertTrue(navigation.showsToday)
    }

    @Test
    fun `a live deletion window keeps the shortcut from opening the form`() {
        // ОВ-124: пока человек может вернуть лекарство, ярлык не уводит его с окна.
        val navigation = RootNavigation()
        navigation.openMeds(MedsDestination.List)

        assertFalse(
            navigation.openFromLaunch(
                AddMedicationLaunch.ACTION,
                liveAction = UndoableDeletion(
                    medicationId = "med-1",
                    medicationName = "med-1",
                    expiresAt = Instant.EPOCH,
                ),
            ),
        )

        assertEquals(MedsDestination.List, navigation.meds)
    }

    @Test
    fun `a live mark window does not block the shortcut`() {
        // Живая отметка уводит на «Сегодня» отдельным правилом (SKL-43), форму не запрещая.
        val navigation = RootNavigation()

        assertTrue(
            navigation.openFromLaunch(
                AddMedicationLaunch.ACTION,
                liveAction = UndoableMark(
                    intakeIds = listOf("i-1"),
                    result = MarkResult.TAKEN,
                    medicationNames = listOf("med-1"),
                    expiresAt = Instant.EPOCH,
                ),
            ),
        )

        assertEquals(MedsDestination.NewMedication, navigation.meds)
    }
}
