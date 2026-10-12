package ru.stohastika.sklyanka.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Таблица классификации проверяется здесь целиком, как в приёмке SKL-112: на устройстве
 * строку `Build` не подменить, а ошибка классификации означает, что человеку не покажут
 * шаги, без которых напоминания не дойдут.
 *
 * Тест идёт на JVM с обычными строками — это и есть доказательство чистоты функции:
 * ни `Build`, ни `Context` ей не нужны.
 */
@DisplayName("The firmware profile is read from the manufacturer, the brand and the vendor manager")
class VendorClassifierTest {

    @Test
    fun `the acceptance table classifies every profile without a live device`() {
        val cases = listOf(
            Case("HUAWEI", "HUAWEI", systemManager = true, expected = VendorProfile.EMUI),
            Case("honor", "HONOR", systemManager = true, expected = VendorProfile.EMUI),
            Case("Xiaomi", "Xiaomi", systemManager = false, expected = VendorProfile.XIAOMI),
            Case("Xiaomi", "Redmi", systemManager = false, expected = VendorProfile.XIAOMI),
            Case("Xiaomi", "POCO", systemManager = false, expected = VendorProfile.XIAOMI),
            Case("samsung", "samsung", systemManager = false, expected = VendorProfile.SAMSUNG),
            Case("SAMSUNG", "Galaxy", systemManager = false, expected = VendorProfile.SAMSUNG),
            Case("Tecno", "TECNO", systemManager = false, expected = VendorProfile.GENERIC),
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                VendorClassifier.classify(case.manufacturer, case.brand, case.systemManager),
                "${case.manufacturer}/${case.brand}/manager=${case.systemManager}",
            )
        }
    }

    @Test
    fun `Honor without the EMUI manager still carries the Huawei chain`() {
        // Решение владельца: Honor — вендорская семья Huawei, и его прошивка выгружает фон
        // так же. Точные адреса разделов MagicOS подтверждает сеанс на устройстве,
        // а непопадание адреса закрыто запасной инструкцией (НАП-19).
        assertEquals(VendorProfile.EMUI, VendorClassifier.classify("HONOR", "HONOR", systemManagerPresent = false))
    }

    @Test
    fun `the vendor manager outweighs the strings because builds rename the brand`() {
        // Переименованный `Build` — обычное дело (Honor): менеджер EMUI на месте означает,
        // что вендорские разделы существуют, и это сильнее строки.
        assertEquals(
            VendorProfile.EMUI,
            VendorClassifier.classify("Xiaomi", "POCO", systemManagerPresent = true),
        )
    }

    @Test
    fun `an unfamiliar firmware is classified as generic and gets no vendor steps`() {
        listOf("Realme", "Nothing", "OnePlus", "motorola", "OPPO", "vivo").forEach { manufacturer ->
            assertEquals(
                VendorProfile.GENERIC,
                VendorClassifier.classify(manufacturer, manufacturer.uppercase(), systemManagerPresent = false),
                manufacturer,
            )
        }
    }

    @Test
    fun `missing strings are not a guess about someone else's firmware`() {
        assertEquals(VendorProfile.GENERIC, VendorClassifier.classify(null, null, systemManagerPresent = false))
        assertEquals(VendorProfile.GENERIC, VendorClassifier.classify("", "", systemManagerPresent = false))
    }

    private data class Case(
        val manufacturer: String?,
        val brand: String?,
        val systemManager: Boolean,
        val expected: VendorProfile,
    )
}
