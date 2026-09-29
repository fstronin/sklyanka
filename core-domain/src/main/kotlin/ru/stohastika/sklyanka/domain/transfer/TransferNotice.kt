package ru.stohastika.sklyanka.domain.transfer

/**
 * То, что перенеслось не полностью, но загрузку не сорвало (ПЕР-12, МГ-6, ПЕР-30).
 *
 * Отдельный тип рядом с [TransferReject] намеренно: отказ — это «файл не применён вовсе»,
 * замечание — «применён, но вот здесь подставлено значение по умолчанию». Смешать их
 * значит либо ронять загрузку на незнакомой форме выпуска, либо молча терять данные.
 *
 * Замечания показываются человеку в отчёте (ПЕР-15 п. 7) и дублируются в журнал.
 * В журнал уходит только код и идентификатор записи: название лекарства подставляет
 * экран, читая его из уже загруженного снимка (НФТ-19).
 */
sealed interface TransferNotice {

    /** Код для журнала: без значений и без названий. */
    val code: String

    /**
     * Незнакомое значение перечисления заменено значением по умолчанию (МГ-6).
     *
     * @property fieldName имя поля файла, например `form`
     * @property medicationId запись, к которой относится замена; null — замена в настройках
     */
    data class UnknownEnum(val fieldName: String, val medicationId: String? = null) : TransferNotice {
        // Имя поля названо fieldName, а не field: внутри getter'а `field` — это ссылка
        // на backing field, и подстановка в шаблон строки перестаёт компилироваться.
        override val code: String get() = "unknown_enum:$fieldName"
    }

    /** Звук из файла на этом устройстве не нашёлся, взят звук по умолчанию (ПЕР-12). */
    data object SoundNotFound : TransferNotice {
        override val code: String get() = "sound_not_found"
    }

    /** Файл создан более старой версией формата и прочитан через миграцию (ПЕР-30). */
    data class OldFormat(val fileVersion: Int) : TransferNotice {
        override val code: String get() = "old_format:$fileVersion"
    }

    /**
     * Запись истории из файла не применена, потому что тот же приём уже есть (ПЕР-23).
     *
     * Число таких записей человек видит в отчёте: без него «загрузил 3184 приёма,
     * добавилось 12» выглядит потерей данных, а не отсутствием дублей.
     */
    data class DuplicateIntakes(val count: Int) : TransferNotice {
        override val code: String get() = "duplicate_intakes:$count"
    }
}
