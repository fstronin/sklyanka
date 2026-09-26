package ru.heuri.sklyanka.domain.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Проверка снимка до применения (ПЕР-28, ИНВ-1, ИНВ-12, критерий 13).
 *
 * Каждый вид нарушения проверяется отдельно: «файл отклонён» без причины неразбираемо,
 * а спасать часть данных из повреждённого файла запрещено.
 */
class ValidateSnapshotTest {

    private val validate = ValidateSnapshot()

    @Test
    @DisplayName("A whole snapshot is accepted")
    fun wholeSnapshotIsAccepted() {
        assertNull(validate(Transfers.snapshot()))
    }

    @Test
    @DisplayName("No self profile rejects the file")
    fun noSelfProfileIsRejected() {
        val snapshot = Transfers.snapshot(
            profiles = listOf(Transfers.mother()),
            medications = listOf(Transfers.magnesium(profileId = Transfers.MOTHER_ID)),
            intakes = emptyList(),
        )

        assertEquals(TransferRejectReason.SELF_PROFILE, validate(snapshot)?.reason)
    }

    @Test
    @DisplayName("Two self profiles reject the file")
    fun twoSelfProfilesAreRejected() {
        val snapshot = Transfers.snapshot(
            profiles = listOf(Transfers.self(), Transfers.self(id = "p3")),
            medications = emptyList(),
            intakes = emptyList(),
        )

        assertEquals(TransferRejectReason.SELF_PROFILE, validate(snapshot)?.reason)
    }

    @Test
    @DisplayName("An intake pointing at a medication that does not exist rejects the file")
    fun brokenIntakeLinkIsRejected() {
        val snapshot = Transfers.snapshot(intakes = listOf(Transfers.taken("i1", medicationId = "nowhere")))

        val reject = validate(snapshot)
        assertEquals(TransferRejectReason.BROKEN_LINK, reject?.reason)
        assertEquals("intakes.medication_id", reject?.detail)
    }

    @Test
    @DisplayName("A medication pointing at a profile that does not exist rejects the file")
    fun brokenMedicationLinkIsRejected() {
        val snapshot = Transfers.snapshot(
            medications = listOf(Transfers.magnesium(profileId = "nowhere")),
            intakes = emptyList(),
        )

        assertEquals("medications.profile_id", validate(snapshot)?.detail)
    }

    @Test
    @DisplayName("A repeated identifier inside one section rejects the file")
    fun duplicateIdentifierIsRejected() {
        val snapshot = Transfers.snapshot(
            medications = listOf(Transfers.magnesium(), Transfers.ibuprofen(id = Transfers.MAGNESIUM_ID)),
            intakes = emptyList(),
        )

        assertEquals(TransferRejectReason.DUPLICATE_ID, validate(snapshot)?.reason)
    }

    @Test
    @DisplayName("A record the medication form would refuse to save is refused here too")
    fun aRecordBreakingTheFormRulesIsRejected() {
        val snapshot = Transfers.snapshot(
            medications = listOf(Transfers.magnesium(times = emptyList())),
            intakes = emptyList(),
        )

        val reject = validate(snapshot)
        assertEquals(TransferRejectReason.INVALID_RECORD, reject?.reason)
        assertEquals("medications:NoIntakeTimes", reject?.detail)
    }

    @Test
    @DisplayName("A dose of zero is refused: the same rule the form applies")
    fun nonPositiveDoseIsRejected() {
        val snapshot = Transfers.snapshot(
            medications = listOf(Transfers.magnesium().copy(doseAmount = BigDecimal.ZERO)),
            intakes = emptyList(),
        )

        assertEquals(TransferRejectReason.INVALID_RECORD, validate(snapshot)?.reason)
    }

    @Test
    @DisplayName("A marked intake without the moment of marking is refused (INV-10)")
    fun markedIntakeWithoutMomentIsRejected() {
        val snapshot = Transfers.snapshot(
            intakes = listOf(Transfers.taken("i1").copy(actedAt = null)),
        )

        val reject = validate(snapshot)
        assertEquals(TransferRejectReason.INVALID_RECORD, reject?.reason)
        assertEquals("intakes:INV-10", reject?.detail)
    }

    @Test
    @DisplayName("The rejection detail names the section and the rule, never a medication name")
    fun rejectionDetailCarriesNoMedicalData() {
        val snapshot = Transfers.snapshot(
            medications = listOf(Transfers.magnesium(times = emptyList())),
            intakes = emptyList(),
        )

        val detail = validate(snapshot)?.logDetail.orEmpty()
        assertEquals(false, detail.contains(Transfers.MAGNESIUM))
    }
}
