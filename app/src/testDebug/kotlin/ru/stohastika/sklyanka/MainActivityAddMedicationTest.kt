package ru.stohastika.sklyanka

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import ru.stohastika.sklyanka.navigation.MedsDestination
import ru.stohastika.sklyanka.navigation.MedsStack
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Запуск `MainActivity` ярлыком «Добавить лекарство» (SKL-80, TDD §7.2).
 *
 * Полный запуск Activity в этом контейнере недоступен: граф Hilt поднимается только
 * на устройстве, а тестового приложения Hilt у модуля нет. Поэтому проверяется контракт
 * запуска ровно в тех местах, где он записан и где ошибка была бы видна: манифест
 * (`singleTask` — иначе ярлык поднял бы вторую копию корня) и стек раздела (закрытие
 * формы ведёт в список).
 *
 * Маршрут запуска — «форма поверх раздела „Лекарства“» — здесь не проверяется: он
 * принадлежит правилу `RootNavigation.openFromLaunch` и живёт одним объявлением
 * в `AddMedicationLaunchTest` (там же его негативные случаи). Вторая копия того же
 * утверждения ловила бы только свою опечатку.
 */
class MainActivityAddMedicationTest {

    private val manifest: Element by lazy {
        val root = requireNotNull(System.getProperty("sklyanka.repo.root")) {
            "sklyanka.repo.root is not set: the screen-test convention is missing"
        }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "app/src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        (0 until activities.length)
            .map { activities.item(it) as Element }
            .first { it.getAttribute("android:name") == ".MainActivity" }
    }

    @Test
    fun `the launcher activity is single task`() {
        assertEquals("singleTask", manifest.getAttribute("android:launchMode"))
        assertEquals("true", manifest.getAttribute("android:exported"))
    }

    @Test
    fun `closing the form leads to the medication list`() {
        val stack = MedsStack(
            mutableStateListOf(MedsDestination.List, MedsDestination.NewMedication),
        )

        assertEquals(MedsDestination.NewMedication, stack.current)
        assertTrue(stack.pop())
        assertEquals(MedsDestination.List, stack.current)
    }
}
