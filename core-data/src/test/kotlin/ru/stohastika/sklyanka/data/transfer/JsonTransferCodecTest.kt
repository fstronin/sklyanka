package ru.stohastika.sklyanka.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import ru.stohastika.sklyanka.domain.transfer.ReminderSound
import ru.stohastika.sklyanka.domain.transfer.TransferDecoded
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferRejectReason
import java.time.ZoneId

/**
 * Формат файла переноса: круговой цикл, опознание, версии, незнакомые значения
 * (ПЕР-5 … ПЕР-12, ПЕР-28 … ПЕР-30, МГ-5, МГ-6).
 *
 * Тесты идут на JVM и прогоняются по-настоящему: разбор файла — чистая логика, устройство
 * ей не нужно, а именно здесь решается, потеряет человек свою историю или нет.
 */
class JsonTransferCodecTest {

    private val time = FixedTimeSource(TransferFixtures.EXPORTED_AT, ZoneId.of("Europe/Moscow"))
    private val codec = JsonTransferCodec(time)

    @Test
    fun `a snapshot survives the round trip through json`() {
        val original = TransferFixtures.snapshot()

        val decoded = codec.decode(codec.encode(original))

        val restored = (decoded as TransferDecoded.Loaded).snapshot
        assertEquals(original.profiles, restored.profiles)
        assertEquals(original.medications, restored.medications)
        assertEquals(original.intakes, restored.intakes)
        assertEquals(original.settings, restored.settings)
        assertEquals(original.exportedAt, restored.exportedAt)
        assertEquals(original.counts, restored.counts)
        assertTrue(decoded.notices.isEmpty())
    }

    @Test
    fun `the file carries the marker and the format version`() {
        val text = codec.encode(TransferFixtures.snapshot())

        assertTrue(text.contains("\"${TransferFormat.MARKER}\""))
        assertTrue(text.contains("\"format_version\": ${TransferFormat.CURRENT_VERSION}"))
    }

    @Test
    fun `the archive is a state of the medication, not a section of its own`() {
        val text = codec.encode(TransferFixtures.snapshot())

        assertTrue(text.contains("\"archived_at\""))
        assertTrue("the file must carry no archive section", !text.contains("\"archive\":"))
    }

    @Test
    fun `the schedule lives inside the medication`() {
        val text = codec.encode(TransferFixtures.snapshot())

        assertTrue(text.contains("\"times\""))
        assertTrue("the file must carry no schedules section", !text.contains("\"schedules\""))
    }

    @Test
    fun `a known package size is written into the file`() {
        val text = codec.encode(TransferFixtures.snapshot())

        assertTrue(text.contains("\"package_size\": 60"))
    }

    @Test
    fun `an unknown package size is omitted from the file`() {
        val original = TransferFixtures.snapshot()
        val snapshot = original.copy(
            medications = original.medications.map { medication -> medication.copy(packageSize = null) },
        )

        val text = codec.encode(snapshot)

        // Остальные пустые поля пишутся как null (encodeDefaults), а размер — опускается:
        // так требует решение по ОВ-45.
        assertTrue("an unknown size must not be written as null", !text.contains("package_size"))
    }

    @Test
    fun `a file without the package size reads it as unknown and says nothing`() {
        val original = TransferFixtures.snapshot()
        val text = codec.encode(
            original.copy(
                medications = listOf(TransferFixtures.active().copy(packageSize = null)),
            ),
        )
        assertTrue(!text.contains("package_size"))

        val decoded = codec.decode(text) as TransferDecoded.Loaded

        assertTrue(
            "a file without the field means an unknown size",
            decoded.snapshot.medications.all { it.packageSize == null },
        )
        assertTrue("an absent optional field is not a loss and needs no notice", decoded.notices.isEmpty())
    }

    @Test
    fun `dates are calendar dates and moments carry the offset`() {
        val text = codec.encode(TransferFixtures.snapshot())

        assertTrue(text.contains("\"start_date\": \"2026-02-12\""))
        assertTrue(text.contains("\"exported_at\": \"2026-10-05T21:14:03+03:00\""))
    }

    @Test
    fun `a file that is not json at all is rejected`() {
        val reject = rejectOf("this is not json")

        assertEquals(TransferRejectReason.NOT_JSON, reject)
    }

    @Test
    fun `a truncated export is rejected as unparsable`() {
        val text = codec.encode(TransferFixtures.snapshot())

        assertEquals(TransferRejectReason.NOT_JSON, rejectOf(text.take(text.length / 2)))
    }

    @Test
    fun `valid json without the marker is not an export of this app`() {
        assertEquals(TransferRejectReason.MISSING_MARKER, rejectOf("""{"hello":"world"}"""))
    }

    @Test
    fun `a foreign marker is rejected`() {
        val text = codec.encode(TransferFixtures.snapshot())
            .replace(TransferFormat.MARKER, "someone-else-export")

        assertEquals(TransferRejectReason.FOREIGN_MARKER, rejectOf(text))
    }

    @Test
    fun `a file from a newer format version is rejected whole`() {
        val text = codec.encode(TransferFixtures.snapshot())
            .replace(
                "\"format_version\": ${TransferFormat.CURRENT_VERSION}",
                "\"format_version\": ${TransferFormat.CURRENT_VERSION + 1}",
            )

        assertEquals(TransferRejectReason.FUTURE_FORMAT, rejectOf(text))
    }

    @Test
    fun `a format version below one is rejected as invalid, not as a file from the future`() {
        val text = codec.encode(TransferFixtures.snapshot())
            .replace("\"format_version\": ${TransferFormat.CURRENT_VERSION}", "\"format_version\": 0")

        assertEquals(TransferRejectReason.INVALID_VERSION, rejectOf(text))
    }

    @Test
    fun `a missing required section names that section`() {
        val text = codec.encode(TransferFixtures.snapshot()).replace("\"settings\"", "\"whatever\"")

        val decoded = codec.decode(text) as TransferDecoded.Rejected
        assertEquals(TransferRejectReason.MISSING_SECTION, decoded.reject.reason)
        assertEquals("settings", decoded.reject.detail)
    }

    @Test
    fun `a broken history section rejects the file entirely, profiles included`() {
        val text = codec.encode(TransferFixtures.snapshot())
            .replace("\"scheduled_at\": \"2026-10-05T08:00:00+03:00\"", "\"scheduled_at\": \"not a time\"")

        val decoded = codec.decode(text) as TransferDecoded.Rejected
        assertEquals(TransferRejectReason.INVALID_VALUE, decoded.reject.reason)
        assertEquals("intakes.scheduled_at", decoded.reject.detail)
    }

    @Test
    fun `an unknown medication form does not break the load and is reported`() {
        val text = codec.encode(TransferFixtures.snapshot()).replace("\"form\": \"TABLET\"", "\"form\": \"PATCH\"")

        val decoded = codec.decode(text) as TransferDecoded.Loaded
        assertEquals(MedicationForm.TABLET, decoded.snapshot.medications.first().form)
        assertTrue(
            decoded.notices.contains(TransferNotice.UnknownEnum("form", TransferFixtures.ACTIVE_ID)),
        )
    }

    @Test
    fun `an unknown intake status never becomes taken`() {
        val text = codec.encode(TransferFixtures.snapshot()).replace("\"status\": \"TAKEN\"", "\"status\": \"DRUNK\"")

        val decoded = codec.decode(text) as TransferDecoded.Loaded
        assertEquals(IntakeStatus.MISSED, decoded.snapshot.intakes.first().status)
    }

    @Test
    fun `a sound missing on this device falls back to the default and is reported`() {
        val text = codec.encode(TransferFixtures.snapshot())
            .replace("\"sound\": \"heather\"", "\"sound\": \"content://media/42\"")

        val decoded = codec.decode(text) as TransferDecoded.Loaded
        assertEquals(ReminderSound.DEFAULT, decoded.snapshot.settings.reminderSound)
        assertTrue(decoded.notices.contains(TransferNotice.SoundNotFound))
    }

    @Test
    fun `an unknown key at the same format version is an extension, not a broken file`() {
        val text = codec.encode(TransferFixtures.snapshot())
            .replace("\"times\"", "\"watch_reminder\": true,\n      \"times\"")

        assertNotNull(codec.decode(text) as? TransferDecoded.Loaded)
    }

    @Test
    fun `the summary in the file is recomputed from its own content`() {
        val text = codec.encode(TransferFixtures.snapshot()).replace("\"intakes\": 3", "\"intakes\": 999")

        val decoded = codec.decode(text) as TransferDecoded.Loaded
        assertEquals(3, decoded.snapshot.counts.intakes)
    }

    @Test
    fun `history is not shifted into the time zone of the receiving device`() {
        val text = codec.encode(TransferFixtures.snapshot())
        val abroad = JsonTransferCodec(FixedTimeSource(TransferFixtures.EXPORTED_AT, ZoneId.of("Asia/Irkutsk")))

        val decoded = abroad.decode(text) as TransferDecoded.Loaded
        assertEquals(TransferFixtures.SCHEDULED_AT, decoded.snapshot.intakes.first().scheduledAt)
    }

    @Test
    fun `the moment of marking is a moment and survives a change of zone`() {
        val text = codec.encode(TransferFixtures.snapshot())
        val abroad = JsonTransferCodec(FixedTimeSource(TransferFixtures.EXPORTED_AT, ZoneId.of("Asia/Irkutsk")))

        val decoded = abroad.decode(text) as TransferDecoded.Loaded
        assertEquals(TransferFixtures.ACTED_AT, decoded.snapshot.intakes.first().actedAt)
    }

    private fun rejectOf(text: String): TransferRejectReason =
        (codec.decode(text) as TransferDecoded.Rejected).reject.reason
}
