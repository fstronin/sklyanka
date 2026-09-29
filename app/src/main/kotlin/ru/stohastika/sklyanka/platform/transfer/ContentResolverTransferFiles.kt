package ru.stohastika.sklyanka.platform.transfer

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.ImportSource
import ru.stohastika.sklyanka.domain.transfer.SafetyCopy
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Файловые операции переноса поверх системного выбора места (НФТ-17, ПЕР-32, ПЕР-36).
 *
 * Разрешений на внешнее хранилище приложение не запрашивает: место выбирает система
 * по просьбе человека, а приложение получает ссылку на уже созданный документ. Саму
 * ссылку никуда не записываем — приложение не ведёт учёта того, куда ушёл файл (ПЕР-36).
 *
 * **Атомарность записи** (ПЕР-32) здесь устроена так, как её позволяет устроить системный
 * документ. Переименовать чужой документ приложение не может, поэтому порядок другой:
 * содержимое сначала пишется целиком во временный файл внутреннего хранилища, и только
 * полностью записанный временный файл переливается в документ человека. Сорванная запись
 * удаляет документ (см. [discard]), и файла с целевым именем в выбранном месте не остаётся.
 * Наполовину записанный файл не должен выглядеть готовой резервной копией — это главный
 * способ потерять данные, думая, что они сохранены.
 */
@Singleton
class ContentResolverTransferFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeSource,
) : TransferFiles {

    /** Страховочная копия лежит во внутреннем хранилище: другим приложениям она недоступна. */
    private val safetyCopyFile: File get() = File(context.filesDir, TransferFormat.SAFETY_COPY_NAME)

    override fun freeSpaceBytes(): Long = context.filesDir.usableSpace

    override suspend fun write(target: ExportTarget, content: String) {
        withContext(Dispatchers.IO) {
            val staging = File(context.cacheDir, STAGING_NAME)
            try {
                staging.writeText(content)
                // Из полностью записанного временного файла — в документ человека. Если этот
                // шаг сорвётся, документ снимет discard, и готового файла не останется.
                val output = context.contentResolver.openOutputStream(Uri.parse(target.handle), WRITE_MODE)
                requireNotNull(output) { "The system did not open the chosen document for writing" }
                output.use { stream -> staging.inputStream().use { it.copyTo(stream) } }
            } finally {
                staging.delete()
            }
        }
    }

    override suspend fun discard(target: ExportTarget) {
        withContext(Dispatchers.IO) {
            // Удаление может не получиться — например, документ уже удалён снаружи.
            // Это не повод ронять отказ выгрузки: человеку уже сказано, что она не состоялась.
            runCatching { context.contentResolver.delete(Uri.parse(target.handle), null, null) }
        }
    }

    override suspend fun read(source: ImportSource): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(source.handle))
            ?.use { it.reader(Charsets.UTF_8).readText() }
            ?: error("The system did not open the chosen document for reading")
    }

    override suspend fun writeSafetyCopy(content: String): SafetyCopy = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, SAFETY_STAGING_NAME)
        staging.writeText(content)
        // Переименование внутри одного хранилища атомарно: либо копия целая, либо её нет.
        // Постоянное имя — это и есть решение о судьбе копии: она перезаписывается
        // при следующей замене и не копится (ПЕР-36).
        val target = safetyCopyFile
        target.delete()
        if (!staging.renameTo(target)) {
            staging.copyTo(target, overwrite = true)
            staging.delete()
        }
        SafetyCopy(name = target.name, sizeBytes = target.length(), createdAt = time.now())
    }

    override fun safetyCopy(): SafetyCopy? {
        val file = safetyCopyFile
        if (!file.isFile) return null
        return SafetyCopy(
            name = file.name,
            sizeBytes = file.length(),
            // Момент берётся у файловой системы, а не у часов: копия могла быть сделана
            // в прошлом запуске.
            createdAt = Instant.ofEpochMilli(file.lastModified()),
        )
    }

    private companion object {
        const val STAGING_NAME = "transfer-export.tmp"
        const val SAFETY_STAGING_NAME = "transfer-safety.tmp"

        /**
         * Режим `wt` — «write, truncate».
         *
         * Без усечения повторная запись в уже существующий документ оставила бы хвост
         * прошлого содержимого, и файл перестал бы быть валидным JSON.
         */
        const val WRITE_MODE = "wt"
    }
}
