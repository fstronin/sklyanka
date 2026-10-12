package ru.stohastika.sklyanka.navigation

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Место в разделе «Лекарства» переживает пересоздание: сохранённое состояние стека (SKL-142).
 *
 * Стек раздела сохраняется строкой, потому что `MedsDestination` — не `Parcelable`, а поворот
 * пересобирает композицию целиком: невосстановленное место означало бы, что человек после
 * поворота оказывается в списке, а не там, где был.
 *
 * Проверяется договор сохранения, а не разметка: что сохранено, то и восстанавливается —
 * для каждого места в разделе, включая карточку и правку с их идентификаторами.
 */
class MedsStackSaverTest {

    @Test
    fun everyPlaceInTheSectionSurvivesTheRoundTrip() {
        val stack = stateListOf(
            MedsDestination.List,
            MedsDestination.Archive,
            MedsDestination.Card(MEDICATION_ID),
            MedsDestination.Edit(MEDICATION_ID),
            MedsDestination.NewMedication,
        )

        val restored = restore(requireNotNull(save(stack)))

        assertEquals(stack.toList(), restored)
    }

    @Test
    fun anUnreadablePlaceFallsBackToTheListOfMedications() {
        val restored = restore(listOf("not-a-place-of-the-section"))

        assertEquals(listOf(MedsDestination.List), restored)
    }

    private fun save(stack: SnapshotStateList<MedsDestination>): Any? = with(MedsStackSaver) { SAVER_SCOPE.save(stack) }

    private fun restore(encoded: Any): List<MedsDestination> =
        with(MedsStackSaver) { requireNotNull(restore(encoded)).toList() }

    /** Список как состояние композиции: `rememberSaveable` хранит именно его. */
    private fun stateListOf(vararg places: MedsDestination) = places.toList().toMutableStateList()

    private companion object {
        const val MEDICATION_ID = "med-rotation"

        /** Область сохранения: в композиции её даёт `rememberSaveable`; здесь — сам тест. */
        val SAVER_SCOPE = SaverScope { true }
    }
}
