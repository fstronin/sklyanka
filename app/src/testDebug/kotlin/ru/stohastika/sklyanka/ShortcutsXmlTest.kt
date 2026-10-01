package ru.stohastika.sklyanka

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Состав статических ярлыков (SKL-80, ОВ-96).
 *
 * Проверяется исходный `app/src/main/res/xml/shortcuts.xml`: список ярлыков рисует
 * лаунчер, и подмена состава на стенде ничего не доказывает. Подписи при этом
 * разрешаются настоящими ресурсами сборки — иначе тест не заметил бы пропавшую строку.
 */
@RunWith(AndroidJUnit4::class)
// Пустое приложение вместо продуктового: тесту нужны ресурсы сборки, а не граф Hilt
// и не первый запуск, который тянет базу и перепланирование.
@Config(application = Application::class)
class ShortcutsXmlTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val document by lazy {
        val root = requireNotNull(System.getProperty("sklyanka.repo.root")) {
            "sklyanka.repo.root is not set: the screen-test convention is missing"
        }
        val file = File(root, "app/src/main/res/xml/shortcuts.xml")
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private val shortcuts: List<Element> get() = elementsNamed("shortcut")

    private fun Element.android(name: String): String = getAttribute("android:$name")

    private fun elementsNamed(name: String): List<Element> {
        val nodes = document.getElementsByTagName(name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun shortcutIds(): List<String> = shortcuts.map { it.android("shortcutId") }

    @Test
    fun `there is exactly one shortcut`() {
        assertEquals(listOf("add_medication"), shortcutIds())
    }

    @Test
    fun `the shortcut launches the add medication action in MainActivity`() {
        val shortcut = shortcuts.single()
        val intents = shortcut.getElementsByTagName("intent")
        assertEquals(1, intents.length)

        val intent = intents.item(0) as Element
        assertEquals(TASK_ACTION, intent.android("action"))
        assertEquals("ru.stohastika.sklyanka.MainActivity", intent.android("targetClass"))
        assertEquals("ru.stohastika.sklyanka", intent.android("targetPackage"))
    }

    @Test
    fun `the shortcut carries the app sign and both labels`() {
        val shortcut = shortcuts.single()
        assertEquals("true", shortcut.android("enabled"))
        assertEquals("@drawable/ic_notification_mark", shortcut.android("icon"))
        assertEquals("@string/add_medication_shortcut_label", shortcut.android("shortcutShortLabel"))
        assertEquals("@string/add_medication_shortcut_long_label", shortcut.android("shortcutLongLabel"))
    }

    @Test
    fun `the labels resolve to the Russian captions`() {
        assertEquals("Добавить лекарство", context.getString(R.string.add_medication_shortcut_label))
        assertEquals("Новое лекарство", context.getString(R.string.add_medication_shortcut_long_label))
    }

    @Test
    fun `the service shortcuts are gone`() {
        assertFalse("permissions_onboarding" in shortcutIds())
        assertFalse("reminders_diagnostics" in shortcutIds())
        assertEquals(0, resourceId("permissions_shortcut_long_label"))
        assertEquals(0, resourceId("diagnostics_shortcut_long_label"))
    }

    private fun resourceId(name: String): Int = context.resources.getIdentifier(name, "string", context.packageName)

    private companion object {
        const val TASK_ACTION = "ru.stohastika.sklyanka.action.ADD_MEDICATION"
    }
}
