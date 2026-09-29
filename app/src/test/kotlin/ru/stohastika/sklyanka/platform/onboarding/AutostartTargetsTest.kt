package ru.stohastika.sklyanka.platform.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Перебор адресов экрана автозапуска (ПЛ-18).
 *
 * Главная проверка здесь — путь «ни один адрес не открывается»: именно он отрабатывает
 * на неэталонных прошивках, то есть у большинства людей, и на настоящем устройстве его
 * не воспроизвести — там открывается первый же адрес.
 */
class AutostartTargetsTest {

    @Test
    fun `the first openable address wins and the rest are not tried`() {
        val tried = mutableListOf<AutostartTarget>()

        val opened = AutostartTargets.firstOpenable { target ->
            tried += target
            true
        }

        assertEquals(AutostartTargets.EMUI.first(), opened)
        assertEquals(1, tried.size)
    }

    @Test
    fun `addresses are tried in order until one opens`() {
        val tried = mutableListOf<AutostartTarget>()

        val opened = AutostartTargets.firstOpenable { target ->
            tried += target
            target == AutostartTargets.EMUI[2]
        }

        assertEquals(AutostartTargets.EMUI[2], opened)
        assertEquals(AutostartTargets.EMUI, tried)
    }

    @Test
    fun `when no address opens the result is nothing and the fallback path takes over`() {
        val opened = AutostartTargets.firstOpenable { false }

        // Отсутствие экрана — обычный исход на неэталонной прошивке, а не сбой: выше
        // по стеку он превращается в текстовую инструкцию (критерий приёмки 6).
        assertNull(opened)
    }

    @Test
    fun `every known address belongs to the one package whose visibility the manifest declares`() {
        // Видимость объявлена одним пакетом, а не QUERY_ALL_PACKAGES: адрес из другого
        // пакета молча не открылся бы на Android 11 и новее.
        assertTrue(AutostartTargets.EMUI.all { it.packageName == AutostartTargets.HUAWEI_SYSTEM_MANAGER })
        assertEquals(AutostartTargets.HUAWEI_SYSTEM_MANAGER, "com.huawei.systemmanager")
    }

    @Test
    fun `there is more than one known address because EMUI renamed the screen`() {
        assertTrue("one address would mean one firmware version", AutostartTargets.EMUI.size > 1)
        assertEquals(AutostartTargets.EMUI.size, AutostartTargets.EMUI.distinct().size)
    }

    @Test
    fun `an address prints as package and class so a device report names it`() {
        val target = AutostartTargets.EMUI.first()

        assertEquals("${target.packageName}/${target.className}", target.toString())
    }
}
