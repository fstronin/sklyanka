package ru.stohastika.sklyanka.platform.onboarding

/**
 * Huawei ли это (`TDD.md §5.1`).
 *
 * Чистая функция, а не Android-обёртка: сценарии классификации проверяются JVM-тестами —
 * на устройстве Honor с переименованным брендом их не воспроизвести, а ошибка здесь
 * означает, что человеку не покажут шаги, без которых напоминания не дойдут.
 *
 * Достаточно одного признака из двух: строки производителя (в любом регистре — сборки
 * пишут «HUAWEI», «Huawei» и «HONOR» по-разному) или наличия системного менеджера EMUI,
 * чья видимость уже объявлена в манифесте ради адресов автозапуска. Так ловятся и сборки,
 * где производитель назван иначе, но менеджер EMUI на месте.
 */
object VendorClassifier {

    fun classify(manufacturer: String?, brand: String?, systemManagerPresent: Boolean): Boolean {
        val isHuawei = listOfNotNull(manufacturer, brand).any { it.contains(HUAWEI, ignoreCase = true) }
        return isHuawei || systemManagerPresent
    }

    private const val HUAWEI = "huawei"
}
