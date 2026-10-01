package ru.stohastika.sklyanka.platform.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перебор адресов системных экранов EMUI (ПЛ-6, ПЛ-18).
 *
 * Главная проверка здесь — путь «ни один адрес не открывается»: именно он отрабатывает
 * на неэталонных прошивках, то есть у большинства людей, и на настоящем устройстве его
 * не воспроизвести — там открывается первый же адрес.
 */
class SystemScreenTargetsTest {

    @Test
    fun `the first openable address wins and the rest are not tried`() {
        val tried = mutableListOf<SystemScreenTarget>()

        val opened = SystemScreenTargets.firstOpenable(SystemScreenTargets.APP_STARTUP) { target ->
            tried += target
            true
        }

        assertEquals(SystemScreenTargets.APP_STARTUP.first(), opened)
        assertEquals(1, tried.size)
    }

    @Test
    fun `addresses are tried in order until one opens`() {
        val tried = mutableListOf<SystemScreenTarget>()

        val opened = SystemScreenTargets.firstOpenable(SystemScreenTargets.APP_STARTUP) { target ->
            tried += target
            target == SystemScreenTargets.APP_STARTUP[2]
        }

        assertEquals(SystemScreenTargets.APP_STARTUP[2], opened)
        assertEquals(SystemScreenTargets.APP_STARTUP, tried)
    }

    @Test
    fun `when no address opens the result is nothing and the fallback path takes over`() {
        val opened = SystemScreenTargets.firstOpenable(SystemScreenTargets.APP_STARTUP) { false }

        // Отсутствие экрана — обычный исход на неэталонной прошивке, а не сбой: выше
        // по стеку он превращается в текстовую инструкцию (критерий приёмки 6).
        assertNull(opened)
    }

    @Test
    fun `every known address belongs to the one package whose visibility the manifest declares`() {
        // Видимость объявлена одним пакетом, а не QUERY_ALL_PACKAGES: адрес из другого
        // пакета молча не открылся бы на Android 11 и новее.
        val allAddresses = SystemScreenTargets.APP_STARTUP + SystemScreenTargets.BATTERY_SAVING
        assertTrue(allAddresses.all { it.packageName == SystemScreenTargets.HUAWEI_SYSTEM_MANAGER })
        assertEquals(SystemScreenTargets.HUAWEI_SYSTEM_MANAGER, "com.huawei.systemmanager")
    }

    @Test
    fun `there is more than one known address because EMUI renamed the screens`() {
        assertTrue("one address would mean one firmware version", SystemScreenTargets.APP_STARTUP.size > 1)
        assertTrue(
            "the battery screen was renamed between EMUI versions too",
            SystemScreenTargets.BATTERY_SAVING.size > 1,
        )
        assertEquals(
            SystemScreenTargets.APP_STARTUP.size,
            SystemScreenTargets.APP_STARTUP.distinct().size,
        )
        assertEquals(
            SystemScreenTargets.BATTERY_SAVING.size,
            SystemScreenTargets.BATTERY_SAVING.distinct().size,
        )
    }

    @Test
    fun `the two steps lead to different system screens`() {
        // Запуск приложений и энергосбережение — разные разделы: адреса не пересекаются.
        assertTrue(
            (SystemScreenTargets.APP_STARTUP.toSet() intersect SystemScreenTargets.BATTERY_SAVING.toSet()).isEmpty(),
        )
    }

    @Test
    fun `an address prints as package and class so a device report names it`() {
        val target = SystemScreenTargets.APP_STARTUP.first()

        assertEquals("${target.packageName}/${target.className}", target.toString())
    }
}
