package ru.stohastika.sklyanka.domain.onboarding

/**
 * Какая это прошивка (`TDD.md §5.2`).
 *
 * Чистая функция, а не Android-обёртка: сценарии классификации проверяются JVM-тестами —
 * на устройстве Honor с переименованным брендом их не воспроизвести, а ошибка здесь
 * означает, что человеку не покажут шаги, без которых напоминания не дойдут.
 *
 * Порядок проверок фиксирован и проверяется тестом: менеджер Huawei → строки Huawei/Honor →
 * строки Xiaomi → строки Samsung → общий профиль.
 *
 * - **Менеджер Huawei важнее строк.** На сборках, где `Build` переименован (Honor), а также
 *   там, где производитель назван иначе, менеджер EMUI всё равно на месте, и это признак
 *   не слабее строки: он означает, что вендорские разделы EMUI существуют.
 * - **Строки читаются без учёта регистра**: сборки пишут «HUAWEI», «Huawei» и «HONOR»
 *   по-разному. Подбренды Xiaomi (`Redmi`, `POCO`) — тот же профиль: у них те же разделы
 *   HyperOS/MIUI.
 * - **«Похоже на прошивку» не гадаем.** Незнакомая строка (Tecno, Realme, Nothing) даёт
 *   общий профиль: чужой вендорской цепочки человеку не показывается.
 */
object VendorClassifier {

    fun classify(manufacturer: String?, brand: String?, systemManagerPresent: Boolean): VendorProfile {
        val marks = listOfNotNull(manufacturer, brand)

        return when {
            // Менеджер важнее строк: он означает, что вендорские разделы EMUI существуют.
            systemManagerPresent -> VendorProfile.EMUI
            marks.any { it.containsAny(HUAWEI, HONOR) } -> VendorProfile.EMUI
            marks.any { it.containsAny(XIAOMI, REDMI, POCO) } -> VendorProfile.XIAOMI
            marks.any { it.containsAny(SAMSUNG) } -> VendorProfile.SAMSUNG
            else -> VendorProfile.GENERIC
        }
    }

    private fun String.containsAny(vararg marks: String): Boolean = marks.any { contains(it, ignoreCase = true) }

    private const val HUAWEI = "huawei"
    private const val HONOR = "honor"
    private const val XIAOMI = "xiaomi"
    private const val REDMI = "redmi"
    private const val POCO = "poco"
    private const val SAMSUNG = "samsung"
}
