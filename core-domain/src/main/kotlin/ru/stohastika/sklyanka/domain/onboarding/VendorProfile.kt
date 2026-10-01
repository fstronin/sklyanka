package ru.stohastika.sklyanka.domain.onboarding

/**
 * Прошивка, под которую строится цепочка шагов надёжности (ПЛ-6, ПЛ-18).
 *
 * Домен не знает ни `Build.MANUFACTURER`, ни имён пакетов: он работает с абстрактным
 * профилем, а определение вендора живёт на платформенном порту ([VendorProfileReader]).
 * Иначе знание «Huawei зовётся com.huawei.systemmanager» расползлось бы из платформы
 * в правила показа шагов.
 */
enum class VendorProfile {
    /** Huawei/EMUI: вендорская цепочка EMUI применима. */
    EMUI,

    /** Всё остальное: честно показываются только общие шаги Android. */
    GENERIC,
}

/**
 * Определение прошивки.
 *
 * Читается на платформе по `Build.MANUFACTURER`/`BRAND` и наличию системного менеджера
 * Huawei: на EMUI, где производитель назван иначе или бренд переименован (Honor), менеджер
 * всё равно присутствует, и это надёжнее строки производителя.
 */
interface VendorProfileReader {
    fun profile(): VendorProfile
}
