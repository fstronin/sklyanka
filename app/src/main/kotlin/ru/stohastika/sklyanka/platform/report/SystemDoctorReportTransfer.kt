package ru.stohastika.sklyanka.platform.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.stohastika.sklyanka.domain.history.DoctorReportTransfer
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportShare
import ru.stohastika.sklyanka.domain.history.ReportShareLauncher
import ru.stohastika.sklyanka.domain.history.ReportShareResult
import ru.stohastika.sklyanka.domain.history.ReportTarget
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Передача отчёта врачу средствами системы (SKL-33, НФТ-17, НФТ-18).
 *
 * Сети здесь нет ни в одном пути, и появиться ей неоткуда: приложение не объявляет
 * разрешения `INTERNET` (ПЛ-3, НФТ-15), а оба пути — системный обмен и системный выбор
 * места — исполняются самой системой.
 *
 * **Обмен отдаёт файл, а не текст.** Отчёт существует в PDF (ОВ-15), и текстовое
 * намерение отдало бы получателю набор символов без документа. Файл ложится в приватный
 * каталог кеша приложения и открывается получателю через `FileProvider` только на чтение:
 * разрешений на внешнее хранилище приложение не запрашивает, в общедоступный каталог
 * ничего не попадает (НФТ-17).
 */
@Singleton
class SystemDoctorReportTransfer @Inject constructor(@ApplicationContext private val context: Context) :
    DoctorReportTransfer {

    /**
     * Выложить отчёт и отдать его системному выбору получателя.
     *
     * Исход «передан» означает «отдан выбранному приложению»: пока системный выбор
     * открыт, приложению не сообщается, выберет ли человек получателя и доведёт ли он
     * дело до конца. Поэтому успех ставится не в момент запуска диалога, а по решению
     * [launcher] — и доставку не обещает.
     */
    override suspend fun share(document: ReportDocument, launcher: ReportShareLauncher): ReportTransferOutcome =
        withContext(Dispatchers.Main) {
            val uri = try {
                withContext(Dispatchers.IO) { stage(document) }
            } catch (failure: IOException) {
                return@withContext ReportTransferOutcome.Failed(failure.javaClass.simpleName)
            }
            when (launcher.launch(ReportShare(uri.toString(), document.fileName, document.mediaType))) {
                ReportShareResult.CHOSEN -> ReportTransferOutcome.Shared
                ReportShareResult.DISMISSED -> ReportTransferOutcome.Cancelled
                ReportShareResult.UNAVAILABLE -> ReportTransferOutcome.Failed(NO_TARGET)
            }
        }

    /**
     * Записать отчёт в документ, созданный системным выбором места.
     *
     * Содержимое собирается целиком до открытия цели — по той же причине, что и в
     * переносе данных: наполовину записанный документ не должен выглядеть готовым
     * отчётом. Сорванная запись снимает цель за собой.
     */
    override suspend fun save(document: ReportDocument, target: ReportTarget): ReportTransferOutcome =
        withContext(Dispatchers.IO) {
            val staging = File(context.cacheDir, STAGING_NAME)
            try {
                staging.writeBytes(document.bytes)
                val output =
                    checkNotNull(context.contentResolver.openOutputStream(Uri.parse(target.handle), WRITE_MODE)) {
                        "The system did not open the chosen document for writing"
                    }
                output.use { stream -> staging.inputStream().use { it.copyTo(stream) } }
                ReportTransferOutcome.Saved
            } catch (failure: IOException) {
                discard(target)
                ReportTransferOutcome.Failed(failure.javaClass.simpleName)
            } catch (failure: SecurityException) {
                // Доступ к выбранному документу отозван системой: для человека это тот же
                // «отчёт не сохранён», и молчать об этом нельзя.
                discard(target)
                ReportTransferOutcome.Failed(failure.javaClass.simpleName)
            } catch (failure: IllegalStateException) {
                discard(target)
                ReportTransferOutcome.Failed(failure.javaClass.simpleName)
            } finally {
                staging.delete()
            }
        }

    /**
     * Положить документ в каталог передачи.
     *
     * Имя документа сохраняется: получатель видит осмысленное `sklyanka-report-ГГГГ-ММ.pdf`.
     * Прежние файлы из каталога убираются: в нём лежит только последний отчёт, и копии
     * медицинских данных не накапливаются (НФТ-18).
     */
    private fun stage(document: ReportDocument): Uri {
        val directory = File(context.cacheDir, REPORT_DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { file -> file.delete() }
        val file = File(directory, document.fileName)
        file.writeBytes(document.bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Снять недописанный документ.
     *
     * Документ человека остаётся пустым или исчезает: готового отчёта в выбранном месте
     * после отказа быть не должно — наполовину записанный файл выглядит целым.
     * Удаление может не получиться (документ уже удалён снаружи), и это не повод ронять
     * отказ: человеку уже сказано, что отчёт не сохранён.
     */
    private fun discard(target: ReportTarget) {
        runCatching { context.contentResolver.delete(Uri.parse(target.handle), null, null) }
    }

    private companion object {
        const val WRITE_MODE = "wt"
        const val STAGING_NAME = "doctor-report.staging"
        const val REPORT_DIRECTORY = "reports"
        const val NO_TARGET = "no_activity_for_share"
    }
}
