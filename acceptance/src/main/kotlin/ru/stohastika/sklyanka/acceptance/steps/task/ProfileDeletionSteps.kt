package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.profile.DeleteProfileOutcome

/**
 * Шаги удаления профиля (SKL-36, ОВ-7): доменный прогон на JVM.
 *
 * Сценарий исполняет настоящий `DeleteProfileUseCase` над миром приёмки; шаги только
 * готовят данность, зовут действие и читают след. Снятие будильников проверяется
 * сравнением **набора до удаления** с набором после: по одним идентификаторам отсутствие
 * будильника у исчезнувшего приёма выглядело бы снятым всегда — и проверка молчала бы
 * даже тогда, когда пересборка не сработала.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class ProfileDeletionSteps(private val world: AcceptanceWorld) {

    private var profileId: String = ""
    private var profileName: String = ""
    private var medicationIds: List<String> = emptyList()

    /** Имена лекарств профиля на момент снимка: после стирания их в мире уже нет. */
    private var medicationNames: List<String> = emptyList()
    private var medicationsBefore: Int = 0
    private var intakesBefore: Int = 0
    private var armedBefore: Set<String> = emptySet()

    @Когда("^человек подтверждает удаление профиля «([^»]+)»$")
    fun thePersonConfirmsTheDeletion(name: String) {
        remember(name)
        val outcome = world.profileDeletion.confirm(name)
        assertTrue(outcome is DeleteProfileOutcome.Deleted, "the deletion must happen: $outcome")
    }

    @Когда("^человек пытается удалить профиль «([^»]+)»$")
    fun thePersonTriesToDeleteTheProfile(name: String) {
        remember(name)
        assertEquals(
            DeleteProfileOutcome.OwnProfile,
            world.profileDeletion.confirm(name),
            "the owner profile must be refused (ПРФ-28)",
        )
    }

    @Тогда("^профиль «([^»]+)» удалён$")
    fun theProfileIsDeleted(name: String) {
        assertEquals(name, profileName, "the scenario must confirm the deletion of «$name» first")
        assertNull(profileByName(name), "«$name» must not exist after the deletion")
    }

    @Тогда("^его лекарства, архивные курсы и записи истории удалены$")
    fun itsMedicationsAndHistoryAreGone() {
        assertTrue(medicationIds.isNotEmpty(), "the scenario must name at least one medication of the profile")
        assertEquals(0, profileMedicationsNow(), "every medication of the profile must be gone")
        assertEquals(0, profileIntakesNow(), "every history record of the profile must be gone")
    }

    @Тогда("^из списка профилей он исчез$")
    fun theProfileIsGoneFromTheList() {
        assertTrue(
            world.profiles.all().none { it.id == profileId },
            "the profile must be gone from the list: ${world.profiles.all().map { it.id }}",
        )
    }

    @Тогда("^активным стал профиль «([^»]+)»$")
    fun theActiveProfileBecame(name: String) {
        assertEquals(name, world.profileSession.context().name, "the owner profile must become active (ПРФ-31)")
    }

    @Тогда("^активным остаётся «([^»]+)»$")
    fun theActiveProfileStays(name: String) {
        assertEquals(name, world.profileSession.context().name, "the active profile must not change")
    }

    @Тогда("^будильники приёмов профиля «([^»]+)» сняты$")
    fun theAlarmsOfTheProfileAreCancelled(name: String) {
        assertTrue(armedBefore.isNotEmpty(), "the scenario must arm a reminder of «$name» before the deletion")
        val stillArmed = world.reminders.scheduled().keys intersect armedBefore
        assertTrue(stillArmed.isEmpty(), "the alarms of «$name» must be cancelled, but $stillArmed remain (ПРФ-30)")
    }

    @Тогда("^в журнале появляется запись об удалении профиля$")
    fun theJournalCarriesTheDeletion() {
        assertTrue(
            world.log.events.contains(OpEvent.PROFILE_DELETED),
            "the journal must carry the deletion: ${world.log.events}",
        )
    }

    @Тогда("^в записи об удалении нет ни названия лекарства, ни имени профиля$")
    fun theDeletionRecordCarriesNoMedicalData() {
        val record = requireNotNull(world.log.records.lastOrNull { it.event == OpEvent.PROFILE_DELETED }) {
            "the journal must carry the deletion: ${world.log.events}"
        }
        val text = listOfNotNull(
            record.profileId,
            record.medicationId,
            record.intakeId,
            record.before,
            record.after,
            record.source,
            record.detail,
        ).joinToString(" ")
        (medicationNames + profileName).forEach { name ->
            assertFalse(text.contains(name), "the journal must not carry «$name» (НФТ-19): $text")
        }
    }

    @Тогда("^профиль «([^»]+)» остаётся вместе со своими данными$")
    fun theProfileStaysWithItsData(name: String) {
        assertEquals(name, profileName, "the scenario must try to delete «$name» first")
        assertNotNull(profileByName(name), "«$name» must stay after the refused deletion (ПРФ-28)")
        assertEquals(medicationsBefore, profileMedicationsNow(), "the medication count must not change")
        assertEquals(intakesBefore, profileIntakesNow(), "the history count must not change")
    }

    /** Снимок того, что будет стёрто, и того, что стоит в системе до удаления. */
    private fun remember(name: String) {
        profileName = name
        val profile = world.profiles.get(name)
        profileId = profile.id
        val owned = world.medications.all().filter { it.profileId == profile.id }
        medicationIds = owned.map { it.id }
        medicationNames = owned.map { it.name }
        medicationsBefore = owned.size
        intakesBefore = world.intakes.all().count { it.medicationId in medicationIds }
        armedBefore = world.reminders.scheduled().keys
            .filter { intakeId -> world.intakes.get(intakeId)?.medicationId in medicationIds }
            .toSet()
    }

    private fun profileByName(name: String): String? = world.profiles.all().firstOrNull { it.name == name }?.id

    private fun profileMedicationsNow(): Int = world.medications.all().count { it.profileId == profileId }

    private fun profileIntakesNow(): Int = world.intakes.all().count { it.medicationId in medicationIds }
}
