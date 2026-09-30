package ru.stohastika.sklyanka.feature.history

import android.content.Context
import androidx.activity.result.contract.ActivityResultContracts
import ru.stohastika.sklyanka.domain.history.ReportDocument

/**
 * Есть ли кому показать системный выбор места для этого документа (SKL-46).
 *
 * `ActivityResultContracts.CreateDocument` строит намерение и запускает его как есть:
 * если обработчика в устройстве нет, запуск завершается `ActivityNotFoundException`
 * ещё до системного диалога, и человек видит падение вместо «Отчёт не сохранён».
 * Проверка до запуска превращает этот исход в честный отказ.
 *
 * Намерение-проба собирается тем же контрактом и с тем же именем файла, что и запуск:
 * проверяемое и запускаемое намерения обязаны совпадать по действию, категории и типу
 * документа, иначе проверка отвечала бы про другое намерение.
 *
 * С Android 11 запросы видимости пакетов фильтруются: чтобы `resolveActivity` видел
 * системный выбор места там, где он есть, действие объявлено в `<queries>` манифеста
 * приложения. Без этого объявления проверка объявила бы сохранение недоступным на любом
 * телефоне — починка одного отказа ценой другого.
 */
internal fun canCreateDocument(context: Context, document: ReportDocument): Boolean {
    val probe = ActivityResultContracts.CreateDocument(document.mediaType)
        .createIntent(context, document.fileName)
    return probe.resolveActivity(context.packageManager) != null
}
