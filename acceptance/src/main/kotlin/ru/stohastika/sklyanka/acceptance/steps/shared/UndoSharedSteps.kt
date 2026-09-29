package ru.stohastika.sklyanka.acceptance.steps.shared

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalTime

/**
 * Общий словарь: что стало с приёмом после отмены отметки (SKL-30).
 *
 * Объявлен один раз здесь, а не в шагах задачи: те же строки пишут и доменный прогон,
 * и экранные — «приём снова ожидается» и «все три приёма снова ожидаются» стоят
 * в сценариях «Сегодня», карточки напоминания и композиции. В `steps/task` их не видит
 * прогон `app`: его classpath — общий артефакт плюс шаги `app` (TDD.md §12.3).
 *
 * Проверяется состояние настоящих приёмов мира, а не текст интерфейса: строки снэкбара
 * проверяют экранные шаги модулей.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class UndoSharedSteps(private val world: AcceptanceWorld) {

    @Then("^приём «([^»]+)» снова ожидается$")
    fun theIntakeOfThatMedicationIsPendingAgain(medication: String) {
        assertPending(pendingIntakeOf(medication), "приём «$medication»")
    }

    @Then("^приём «([^»]+)» на (\\d{2}:\\d{2}) снова ожидается$")
    fun theIntakeAtThatTimeIsPendingAgain(medication: String, time: String) {
        val name = world.medicationNamed(medication)
        val intake = world.intakes.of(name).firstOrNull { it.scheduledAt.toLocalTime() == LocalTime.parse(time) }
        assertNotNull(intake, "no intake of $name at $time")
        assertPending(requireNotNull(intake), "приём «$medication» на $time")
    }

    @Then("^приём «([^»]+)» профиля «([^»]+)» снова ожидается$")
    fun theIntakeOfThatProfileIsPendingAgain(medication: String, profile: String) {
        val medicationId = world.medications.get(world.medicationNamed(medication)).id
        val intake = world.intakes.ofProfile(world.profiles, profile).firstOrNull { it.medicationId == medicationId }
        assertNotNull(intake, "no intake of $medication in $profile")
        assertPending(requireNotNull(intake), "приём «$medication» профиля «$profile»")
    }

    @Then("^приём снова ожидается$")
    fun theCurrentIntakeIsPendingAgain() {
        assertPending(world.intakes.current(), "приём")
    }

    @Then("^все три приёма снова ожидаются$")
    fun allThreeIntakesArePendingAgain() {
        assertEquals(GROUP_SIZE, world.lastUndoneIds.size, "the scenario undid ${world.lastUndoneIds}")
        world.lastUndoneIds.forEach { intakeId ->
            assertPending(requireNotNull(world.intakes.get(intakeId)) { "intake $intakeId disappeared" }, "приём")
        }
    }

    @Then("^приём «([^»]+)» остаётся принятым$")
    fun theIntakeOfThatMedicationStaysTaken(medication: String) {
        assertEquals(IntakeStatus.TAKEN, takenIntakeOf(medication).status, "приём «$medication»")
    }

    @Then("^приём «([^»]+)» на (\\d{2}:\\d{2}) остаётся принятым$")
    fun theIntakeAtThatTimeStaysTaken(medication: String, time: String) {
        val name = world.medicationNamed(medication)
        val intake = world.intakes.of(name).firstOrNull { it.scheduledAt.toLocalTime() == LocalTime.parse(time) }
        assertNotNull(intake, "no intake of $name at $time")
        assertEquals(IntakeStatus.TAKEN, requireNotNull(intake).status, "приём «$medication» на $time")
    }

    /**
     * Приём названного лекарства, о котором говорит сценарий без времени.
     *
     * Сначала — приём последней отмены: «снова ожидается» относится именно к нему.
     * Иначе — ожидающийся приём этого лекарства сегодня. Единственным приёмом
     * ограничиваться нельзя: прогон `app` строит «Сегодня» планировщиком, и у лекарства
     * появляется больше одной записи, тогда как доменный прогон заводит ровно одну.
     */
    private fun pendingIntakeOf(medication: String): Intake {
        val name = world.medicationNamed(medication)
        val medicationId = world.medications.get(name).id
        world.lastUndoneIds.mapNotNull { world.intakes.get(it) }
            .firstOrNull { it.medicationId == medicationId }
            ?.let { return it }
        return world.intakes.of(name).first {
            it.isPending && it.scheduledAt.toLocalDate() == world.clock.today()
        }
    }

    /** Приём названного лекарства, который сценарий видит принятым: текущий или сегодняшний. */
    private fun takenIntakeOf(medication: String): Intake {
        val name = world.medicationNamed(medication)
        val medicationId = world.medications.get(name).id
        world.intakes.currentId?.let { world.intakes.get(it) }
            ?.takeIf { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN }
            ?.let { return it }
        return world.intakes.of(name).first {
            it.status == IntakeStatus.TAKEN && it.scheduledAt.toLocalDate() == world.clock.today()
        }
    }

    private fun assertPending(intake: Intake, what: String) {
        assertTrue(intake.isPending, "$what must be pending again, but is ${intake.status}")
    }

    private companion object {
        /** Размер группы одновременных приёмов: сценарий называет ровно три. */
        const val GROUP_SIZE = 3
    }
}
