package ru.heuri.sklyanka.platform.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.heuri.sklyanka.domain.history.DoctorReportTransfer
import ru.heuri.sklyanka.domain.history.ReportDocument
import ru.heuri.sklyanka.domain.history.ReportTarget
import ru.heuri.sklyanka.domain.history.ReportTransferOutcome
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Передача отчёта врачу средствами системы (SKL-18, НФТ-17, НФТ-18).
 *
 * Сети здесь нет ни в одном пути, и появиться ей неоткуда: приложение не объявляет
 * разрешения `INTERNET` (ПЛ-3, НФТ-15), а оба пути — системный обмен и системный выбор
 * места — исполняются самой системой.
 *
 * **Почему обмен идёт текстом, а не файлом через `FileProvider`.** Формат артефакта
 * не подтверждён (ОВ-15). Провайдер файлов — это запись во внешне доступный каталог и
 * `grantUriPermission` чужому приложению, то есть решение о том, где лежит документ
 * с медицинскими данными. Принимать его до подтверждения формата нельзя: сохранение
 * файлом уже работает через системный выбор места, где место выбирает человек, и файл
 * не остаётся в общедоступном каталоге (НФТ-17). Текстовый обмен ничего не оставляет
 * на диске вовсе.
 */
@Singleton
class SystemDoctorReportTransfer @Inject constructor(@ApplicationContext private val context: Context) :
    DoctorReportTransfer {

    /**
     * Отдать отчёт системному обмену.
     *
     * Исход «передан» означает «передан системе»: какое приложение человек выберет в
     * системном диалоге и доведёт ли он дело до конца, приложению не сообщается, и
     * выдавать это за подтверждённую отправку нельзя.
     */
    override suspend fun share(document: ReportDocument): ReportTransferOutcome = withContext(Dispatchers.Main) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = document.mediaType
            putExtra(Intent.EXTRA_TITLE, document.fileName)
            putExtra(Intent.EXTRA_TEXT, document.content)
        }
        // Диалог запускается из контекста приложения, а не из активности: отчёт
        // формируется в ViewModel, и активности у неё нет.
        val chooser = Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(chooser) }.fold(
            onSuccess = { ReportTransferOutcome.Shared },
            onFailure = { ReportTransferOutcome.Failed(NO_TARGET) },
        )
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
                staging.writeText(document.content)
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
        const val NO_TARGET = "no_activity_for_share"
    }
}
