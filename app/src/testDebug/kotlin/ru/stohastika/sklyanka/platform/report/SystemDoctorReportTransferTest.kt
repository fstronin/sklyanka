package ru.stohastika.sklyanka.platform.report

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportShare
import ru.stohastika.sklyanka.domain.history.ReportShareResult
import ru.stohastika.sklyanka.domain.history.ReportTarget
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import java.io.ByteArrayOutputStream

/**
 * Передача отчёта врачу средствами системы (SKL-33).
 *
 * Проверяется то, что можно проверить без настоящего диалога: намерение обмена несёт
 * PDF-файл с доступом только на чтение, а сохранение пишет в выбранное место байты
 * документа. Сам системный диалог и внешний просмотрщик остаются проверками устройства
 * (`@device`, `@manual`).
 *
 * `Main` подменяется тестовым диспетчером: передача начинается на главном дисплее,
 * а `runTest` на главном потоке Robolectric иначе ждал бы сам себя.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SystemDoctorReportTransferTest {

    private val scheduler = TestCoroutineScheduler()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val transfer = SystemDoctorReportTransfer(application)

    @Before
    fun replaceMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * Выкладка файла и решение системного выбора.
     *
     * Всё в одной проверке намеренно: `FileProvider` кеширует стратегию путей на процесс,
     * а каждый метод Robolectric живёт в своём каталоге — второй метод того же класса
     * получил бы стратегию первого и упал бы на чужом корне.
     */
    @Test
    fun theShareStagesThePdfAndMapsTheChooserDecision() = runTest(scheduler) {
        val presented = mutableListOf<ReportShare>()
        val chosen = transfer.share(document()) { share ->
            presented += share
            ReportShareResult.CHOSEN
        }

        assertEquals(ReportTransferOutcome.Shared, chosen)
        val share = presented.single()
        assertEquals("application/pdf", share.mediaType)
        assertEquals("sklyanka-report-2026-09.pdf", share.fileName)
        val uri = Uri.parse(share.handle)
        assertEquals("content", uri.scheme)
        assertEquals(
            "the authority must belong to the application",
            "${application.packageName}.fileprovider",
            uri.authority,
        )
        assertArrayEquals(
            "the staged file must be the document itself",
            DOCUMENT_BYTES,
            application.contentResolver.openInputStream(uri)?.use { it.readBytes() },
        )

        assertEquals(
            "a dismissed chooser is a cancellation, not success",
            ReportTransferOutcome.Cancelled,
            transfer.share(document()) { ReportShareResult.DISMISSED },
        )
        val unavailable = transfer.share(document()) { ReportShareResult.UNAVAILABLE }
        assertTrue(
            "the refusal must be named, not swallowed: $unavailable",
            unavailable is ReportTransferOutcome.Failed,
        )
        assertNotNull((unavailable as ReportTransferOutcome.Failed).reason)
    }

    @Test
    fun theSaveWritesTheDocumentIntoTheChosenPlace() = runTest(scheduler) {
        val target = ReportTarget("content://ru.stohastika.sklyanka.test/report")
        val written = ByteArrayOutputStream()
        shadowOf(application.contentResolver).registerOutputStream(Uri.parse(target.handle), written)

        val outcome = transfer.save(document(), target)

        assertEquals(ReportTransferOutcome.Saved, outcome)
        assertArrayEquals(DOCUMENT_BYTES, written.toByteArray())
    }

    @Test
    fun aChosenPlaceThatCannotBeOpenedIsAFailureNotSilence() = runTest(scheduler) {
        val target = ReportTarget("content://ru.stohastika.sklyanka.test/missing")

        val outcome = transfer.save(document(), target)

        assertTrue("the refusal must be named, not swallowed: $outcome", outcome is ReportTransferOutcome.Failed)
        assertNotNull((outcome as ReportTransferOutcome.Failed).reason)
    }

    private fun document() = ReportDocument(
        fileName = "sklyanka-report-2026-09.pdf",
        mediaType = "application/pdf",
        bytes = DOCUMENT_BYTES,
    )

    private companion object {
        val DOCUMENT_BYTES = "%PDF-1.4 probe".toByteArray()
    }
}
