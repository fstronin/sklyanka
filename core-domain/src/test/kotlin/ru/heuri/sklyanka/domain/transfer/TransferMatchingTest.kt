package ru.heuri.sklyanka.domain.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Нормализация названия, ключ совпадения и сопоставление профилей (ПЕР-19, ПЕР-20).
 *
 * Это правила, из которых складывается ответ на вопрос «одно это лекарство или два»,
 * и ошибка здесь не заметна глазом: она проявляется слитой историей двух препаратов.
 */
class TransferMatchingTest {

    @Test
    @DisplayName("Name normalisation trims edges, collapses inner spaces and lowers the case")
    fun normalisationFoldsWritingVariants() {
        assertEquals("magnesium b6", TransferNames.normalize("  Magnesium   B6 "))
        assertEquals("magnesium b6", TransferNames.normalize("MAGNESIUM B6"))
    }

    @Test
    @DisplayName("Name normalisation folds the Cyrillic yo into ye")
    fun normalisationFoldsYo() {
        // Кириллица собирается из кодов символов, а не пишется литералом: литерал
        // с кириллицей в Kotlin-коде запрещён (НФТ-42), включая тесты, а правило
        // «ё» → «е» без кириллицы непроверяемо.
        val withYo = cyrillic(YO_WORD)
        val withYe = cyrillic(YE_WORD)
        assertEquals(withYe, TransferNames.normalize(withYo))
    }

    @Test
    @DisplayName("Match key ignores the per-intake dose and folds equal strengths written differently")
    fun matchKeyIgnoresDoseAmount() {
        val mine = Transfers.magnesium().copy(doseAmount = BigDecimal("1"))
        val theirs = Transfers.magnesium().copy(doseAmount = BigDecimal("2"), strength = BigDecimal("48.0"))
        assertEquals(mine.matchKey(), theirs.matchKey())
    }

    @Test
    @DisplayName("Different strength means a different medication")
    fun differentStrengthIsADifferentMedication() {
        val mine = Transfers.magnesium()
        val theirs = Transfers.magnesium().copy(strength = BigDecimal("96"))
        assertTrue(mine.matchKey() != theirs.matchKey())
    }

    @Test
    @DisplayName("The same name in two profiles is not one medication")
    fun matchingStaysInsideOneProfile() {
        val device = listOf(Transfers.magnesium(profileId = Transfers.SELF_ID))
        val file = listOf(Transfers.magnesium(id = "f1", profileId = Transfers.MOTHER_ID))
        val profiles = MatchProfiles(FakeIds())(
            listOf(Transfers.self(), Transfers.mother()),
            listOf(Transfers.self(), Transfers.mother()),
        )

        val matching = FindMedicationMatches()(device, file, profiles)

        assertEquals(0, matching.matches.size)
        assertEquals(1, matching.unmatched.size)
    }

    @Test
    @DisplayName("The self profile pairs with the self profile even under another name")
    fun selfPairsWithSelfRegardlessOfName() {
        val matching = MatchProfiles(FakeIds())(
            devices = listOf(Transfers.self(name = "Owner", id = "device-self")),
            files = listOf(Transfers.self(name = "Another name", id = "file-self")),
        )

        assertEquals("device-self", matching.deviceProfileId("file-self"))
        assertTrue(matching.added.isEmpty())
    }

    @Test
    @DisplayName("Other profiles pair by normalised name and keep the device colour and mirroring")
    fun otherProfilesPairByName() {
        val device = Transfers.mother(id = "device-mother", mirrorReminders = true)
        val file = Transfers.mother(id = "file-mother", mirrorReminders = false)
            .copy(name = "  MOTHER  ", color = 1)

        val matching = MatchProfiles(FakeIds())(
            devices = listOf(Transfers.self(), device),
            files = listOf(Transfers.self(id = "file-self"), file),
        )

        assertEquals("device-mother", matching.deviceProfileId("file-mother"))
        // Существующий профиль в план вовсе не попадает: настройки устройства
        // принадлежат устройству (ПЕР-19).
        assertTrue(matching.added.isEmpty())
    }

    @Test
    @DisplayName("A profile absent on the device is added with a fresh id and is never the self one")
    fun unknownProfileIsAdded() {
        val father = Transfers.mother(id = "file-father").copy(name = "Father", isSelf = false)

        val matching = MatchProfiles(FakeIds())(
            devices = listOf(Transfers.self()),
            files = listOf(Transfers.self(id = "file-self"), father),
        )

        assertEquals(1, matching.added.size)
        assertEquals("new-1", matching.added.single().id)
        assertEquals(false, matching.added.single().isSelf)
        assertEquals("new-1", matching.deviceProfileId("file-father"))
    }

    @Test
    @DisplayName("A file record whose profile did not map is left out of matching")
    fun unmappedProfileYieldsNoMatch() {
        val matching = FindMedicationMatches()(
            devices = listOf(Transfers.magnesium()),
            files = listOf(Transfers.magnesium(id = "f1", profileId = "unknown")),
            profiles = ProfileMatching(emptyMap(), emptyList()),
        )

        assertTrue(matching.matches.isEmpty())
        assertTrue(matching.unmatched.isEmpty())
    }

    @Test
    @DisplayName("Schedule, form, period and stock are shown as differences, not as a mismatch")
    fun differencesAreReportedButDoNotBreakTheMatch() {
        val device = Transfers.magnesium()
        val file = Transfers.magnesium(
            id = "f1",
            times = listOf(Transfers.LATE_MORNING, Transfers.LATE_EVENING),
            stockLeft = BigDecimal("30"),
        )
        val profiles = MatchProfiles(FakeIds())(listOf(Transfers.self()), listOf(Transfers.self()))

        val match = FindMedicationMatches()(listOf(device), listOf(file), profiles).matches.single()

        assertEquals(listOf(MatchDifference.TIMES, MatchDifference.STOCK), match.differences)
    }

    @Test
    @DisplayName("One device record cannot be claimed by two file records")
    fun oneDeviceRecordMatchesOnce() {
        val profiles = MatchProfiles(FakeIds())(listOf(Transfers.self()), listOf(Transfers.self()))

        val matching = FindMedicationMatches()(
            devices = listOf(Transfers.magnesium()),
            files = listOf(Transfers.magnesium(id = "f1"), Transfers.magnesium(id = "f2")),
            profiles = profiles,
        )

        assertEquals(1, matching.matches.size)
        assertEquals("f2", matching.unmatched.single().id)
        assertNull(matching.matches.single().differences.firstOrNull())
    }

    /** Строка из кодов символов: в исходнике кириллицы нет, в значении она есть. */
    private fun cyrillic(codes: IntArray): String = String(codes.map { it.toChar() }.toCharArray())

    private companion object {
        /** «Пирацетам ё»: прописная первая буква и «ё» в конце. */
        val YO_WORD = intArrayOf(
            0x041F, 0x0438, 0x0440, 0x0430, 0x0446, 0x0435, 0x0442, 0x0430, 0x043C, 0x0020, 0x0451,
        )

        /** «пирацетам е» — то же после нормализации: нижний регистр и «ё» → «е». */
        val YE_WORD = intArrayOf(
            0x043F, 0x0438, 0x0440, 0x0430, 0x0446, 0x0435, 0x0442, 0x0430, 0x043C, 0x0020, 0x0435,
        )
    }
}
