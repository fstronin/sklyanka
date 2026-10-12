package ru.stohastika.sklyanka.platform.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.onboarding.VendorSteps

/**
 * Перебор адресов системных экранов производителей (ПЛ-6, ПЛ-18).
 *
 * Главная проверка здесь — путь «ни один адрес не открывается»: именно он отрабатывает
 * на неэталонных прошивках, то есть у большинства людей, и на настоящем устройстве его
 * не воспроизвести — там открывается первый же адрес. Рядом — правило «адресу шага
 * соответствует пакет этой прошивки»: адрес чужого пакета молча не открылся бы на Android 11
 * и новее, потому что его видимость не объявлена.
 */
class SystemScreenTargetsTest {

    @Test
    fun `the first openable address wins and the rest are not tried`() {
        val tried = mutableListOf<SystemScreenTarget>()

        val opened = SystemScreenTargets.firstOpenable(SystemScreenTargets.EMUI_APP_STARTUP) { target ->
            tried += target
            true
        }

        assertEquals(SystemScreenTargets.EMUI_APP_STARTUP.first(), opened)
        assertEquals(1, tried.size)
    }

    @Test
    fun `addresses are tried in order until one opens`() {
        val tried = mutableListOf<SystemScreenTarget>()

        val opened = SystemScreenTargets.firstOpenable(SystemScreenTargets.EMUI_APP_STARTUP) { target ->
            tried += target
            target == SystemScreenTargets.EMUI_APP_STARTUP[2]
        }

        assertEquals(SystemScreenTargets.EMUI_APP_STARTUP[2], opened)
        assertEquals(SystemScreenTargets.EMUI_APP_STARTUP, tried)
    }

    @Test
    fun `when no address opens the result is nothing and the fallback path takes over`() {
        val opened = SystemScreenTargets.firstOpenable(SystemScreenTargets.EMUI_APP_STARTUP) { false }

        // Отсутствие экрана — обычный исход на неэталонной прошивке, а не сбой: выше
        // по стеку он превращается в текстовую инструкцию (критерий приёмки 6).
        assertNull(opened)
    }

    @Test
    fun `the addresses of a step belong to the packages of its own firmware`() {
        // Видимость пакетов объявлена манифестом точечно, а не QUERY_ALL_PACKAGES: адрес
        // чужого пакета молча не открылся бы на Android 11 и новее.
        val emui = SystemScreenTargets.EMUI_APP_STARTUP + SystemScreenTargets.EMUI_BATTERY_SAVING
        assertTrue(emui.all { it.packageName == SystemScreenTargets.HUAWEI_SYSTEM_MANAGER })

        assertTrue(
            SystemScreenTargets.XIAOMI_APP_STARTUP.all { it.packageName == SystemScreenTargets.XIAOMI_SECURITY_CENTER },
        )
        assertTrue(
            SystemScreenTargets.XIAOMI_BATTERY_SAVING.all { it.packageName == SystemScreenTargets.XIAOMI_POWER_KEEPER },
        )
        val samsung = listOf(SystemScreenTargets.SAMSUNG_DEVICE_CARE, SystemScreenTargets.SAMSUNG_DEVICE_CARE_CN)
        assertTrue(SystemScreenTargets.SAMSUNG_BATTERY_SAVING.all { it.packageName in samsung })
    }

    @Test
    fun `every profile gets addresses exactly for the steps its chain carries`() {
        // Таблица профиля и таблица адресов не расходятся: шаг без адреса (закрепление
        // в недавних) — законное состояние, а шаг с чужим адресом — молчаливая ошибка.
        VendorProfile.entries.forEach { profile ->
            val chain = VendorSteps.of(profile)
            PermissionStep.entries.forEach { step ->
                val addresses = SystemScreenTargets.targets(profile, step)
                if (step !in chain) {
                    val why = "$profile: the step $step has no place in the chain of this profile"
                    assertTrue(why, addresses.isEmpty())
                }
            }
        }

        // Закрепление в недавних системного экрана не имеет ни на одной прошивке.
        VendorProfile.entries.forEach { profile ->
            assertTrue(SystemScreenTargets.targets(profile, PermissionStep.RECENTS_PIN).isEmpty())
        }
    }

    @Test
    fun `the vendor steps that carry a chain have an address to open`() {
        // У каждого вендорского шага, кроме закрепления в недавних, есть хотя бы один адрес:
        // иначе кнопка «Открыть настройки» была бы только запасной инструкцией.
        VendorProfile.entries.forEach { profile ->
            VendorSteps.of(profile)
                .filter { it != PermissionStep.RECENTS_PIN }
                .forEach { step ->
                    assertTrue(
                        "$profile/$step: the step has no address at all",
                        SystemScreenTargets.targets(profile, step).isNotEmpty(),
                    )
                }
        }
    }

    @Test
    fun `there is more than one known EMUI address because the firmware renamed the screens`() {
        assertTrue("one address would mean one firmware version", SystemScreenTargets.EMUI_APP_STARTUP.size > 1)
        assertTrue(
            "the battery screen was renamed between EMUI versions too",
            SystemScreenTargets.EMUI_BATTERY_SAVING.size > 1,
        )
        assertEquals(
            SystemScreenTargets.EMUI_APP_STARTUP.size,
            SystemScreenTargets.EMUI_APP_STARTUP.distinct().size,
        )
        assertEquals(
            SystemScreenTargets.EMUI_BATTERY_SAVING.size,
            SystemScreenTargets.EMUI_BATTERY_SAVING.distinct().size,
        )
    }

    @Test
    fun `the two steps lead to different system screens on every profile`() {
        // Запуск приложений и энергосбережение — разные разделы: адреса не пересекаются.
        listOf(VendorProfile.EMUI, VendorProfile.XIAOMI).forEach { profile ->
            val startup = SystemScreenTargets.targets(profile, PermissionStep.APP_STARTUP).toSet()
            val battery = SystemScreenTargets.targets(profile, PermissionStep.BATTERY_SAVING).toSet()

            assertTrue("$profile: the two steps name one and the same address", (startup intersect battery).isEmpty())
        }
    }

    @Test
    fun `only the screen that needs the app identity declares it`() {
        // Экран энергосбережения MIUI показывает страницу приложения только с его пакетом
        // в extras; остальные экраны открываются сами, и лишние extras им не передаются.
        assertTrue(SystemScreenTargets.XIAOMI_BATTERY_SAVING.all { it.carriesAppIdentity })

        val all = VendorProfile.entries.flatMap { profile ->
            PermissionStep.entries.flatMap { step -> SystemScreenTargets.targets(profile, step) }
        }
        assertTrue(
            all.filter { it.carriesAppIdentity }.toSet() == SystemScreenTargets.XIAOMI_BATTERY_SAVING.toSet(),
        )
    }

    @Test
    fun `an address prints as package and class so a device report names it`() {
        val target = SystemScreenTargets.EMUI_APP_STARTUP.first()

        assertEquals("${target.packageName}/${target.className}", target.toString())
    }
}
