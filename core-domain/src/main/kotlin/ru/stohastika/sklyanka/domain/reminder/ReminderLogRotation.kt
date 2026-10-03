package ru.stohastika.sklyanka.domain.reminder

/**
 * Итог ротации журнала: сколько записей удалено и по какому порогу (ПМ-30).
 *
 * Числа — единственное, что попадает в журнал: ни идентификаторов, ни названий
 * (НФТ-19). Формат нагрузки — пары `ключ=значение` через `;`, как у среды измерения:
 * в `core-domain` нет JSON, а в CSV строка обязана читаться глазами. Нулевые пороги
 * в нагрузку не пишутся: запись о ротации создаётся только когда что-то удалено.
 */
data class ReminderLogRotation(val removedByAge: Int, val removedBySize: Int) {

    /** Сколько записей удалено всего. */
    val removed: Int get() = removedByAge + removedBySize

    /** Нагрузка записи `ROTATED`: `age=12;size=345`; при нуле удалённого — пустая строка. */
    fun encode(): String = listOfNotNull(
        removedByAge.takeIf { it > 0 }?.let { "$AGE$EQUAL$it" },
        removedBySize.takeIf { it > 0 }?.let { "$SIZE$EQUAL$it" },
    ).joinToString(SEPARATOR.toString())

    companion object {
        private const val SEPARATOR = ';'
        private const val EQUAL = '='
        private const val AGE = "age"
        private const val SIZE = "size"
        private val KEYS = setOf(AGE, SIZE)

        /**
         * Разбор нагрузки; `null` — строка не является записью ротации (МГ-6).
         *
         * Отсутствующий порог читается как ноль: запись, подрезанная в CSV или
         * оставленная более старой сборкой, не имеет права уронить чтение. Чужие
         * ключи и нечисловые значения — отказ, а не молчаливый ноль.
         */
        @Suppress("ReturnCount")
        fun parse(value: String?): ReminderLogRotation? {
            if (value.isNullOrEmpty()) return null
            val removed = mutableMapOf<String, Int>()
            value.split(SEPARATOR).forEach { pair ->
                val separator = pair.indexOf(EQUAL)
                if (separator <= 0) return null
                val key = pair.substring(0, separator)
                if (key !in KEYS || key in removed) return null
                removed[key] = pair.substring(separator + 1).toIntOrNull() ?: return null
            }
            return ReminderLogRotation(
                removedByAge = removed[AGE] ?: 0,
                removedBySize = removed[SIZE] ?: 0,
            )
        }
    }
}
