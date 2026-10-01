package ru.stohastika.sklyanka.navigation

/**
 * Действие ярлыка быстрого доступа «Добавить лекарство» (SKL-80, ОВ-96).
 *
 * Единственное место, где записано имя действия: ярлык (`shortcuts.xml`) и разбор
 * входящего намерения обязаны называть одно и то же, и разъехаться они не могут.
 * Разбор — чистая функция без `Intent`, поэтому проверяется на JVM без Activity.
 */
object AddMedicationLaunch {

    /** Действие намерения ярлыка; значение продублировано в `res/xml/shortcuts.xml`. */
    const val ACTION: String = "ru.stohastika.sklyanka.action.ADD_MEDICATION"

    /** Пришло ли намерение ярлыка: только точное совпадение действия, `null` — нет. */
    fun matches(action: String?): Boolean = action == ACTION
}
