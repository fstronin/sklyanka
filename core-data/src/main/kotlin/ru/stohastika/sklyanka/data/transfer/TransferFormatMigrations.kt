package ru.stohastika.sklyanka.data.transfer

import kotlinx.serialization.json.JsonObject

/**
 * Один шаг миграции формата файла: `from` → `from + 1` (ПЕР-30).
 *
 * Преобразование идёт над **разобранной структурой** и до применения к базе: миграция,
 * встроенная в чтение записей, растворилась бы в мапперах, и через две версии никто
 * не смог бы сказать, какой файл эта сборка ещё умеет читать.
 */
internal interface TransferFormatMigration {

    /** Версия, с которой шаг умеет читать. */
    val from: Int

    fun migrate(document: JsonObject): JsonObject
}

/**
 * Цепочка миграций снизу.
 *
 * **Список пуст, и это не заглушка.** Выпущена одна версия формата (ПЕР-7,
 * [ru.stohastika.sklyanka.domain.transfer.TransferFormat.CURRENT_VERSION] = 1), поэтому файла
 * более старой версии физически не существует, и первый шаг появится вместе со второй
 * версией формата. Механизм заведён сейчас, потому что этого требует ОВ-65: повышение
 * версии формата обязано быть отличимо от порчи файла, а отличимо оно только тогда,
 * когда есть куда мигрировать.
 *
 * Работоспособность самой цепочки проверяется тестом на подставном шаге: иначе первая
 * настоящая миграция окажется и первым запуском этого кода.
 */
internal object TransferFormatMigrations {

    val ALL: List<TransferFormatMigration> = emptyList()

    /**
     * Проводит документ от [fromVersion] до [toVersion].
     *
     * @return преобразованный документ либо null, если шага для какой-то версии нет.
     *   Null означает «эта сборка такой старый файл прочитать не умеет» — отдельный,
     *   объяснимый человеку отказ, а не отказ «файл битый».
     */
    fun migrate(
        document: JsonObject,
        fromVersion: Int,
        toVersion: Int,
        steps: List<TransferFormatMigration> = ALL,
    ): JsonObject? {
        var current = document
        var version = fromVersion
        while (version < toVersion) {
            val step = steps.firstOrNull { it.from == version } ?: return null
            current = step.migrate(current)
            version++
        }
        return current
    }
}
