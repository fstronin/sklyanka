package ru.stohastika.sklyanka.feature.widgets

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetType
import java.io.File

/**
 * Объявление трёх плит платформе (SKL-118, §5.11, §5.12).
 *
 * Проверяется то, что человек видит в системном списке и выбирает в нём: имя типа
 * и то, что настраиваемый провайдер опознаётся как свой тип. Имена типов в списке —
 * требование нормы (§5.11: имя типа стоит в трёх местах, и системный список — одно из них),
 * а связь «провайдер → тип» решает, предвыберется ли в настройке тот тип, который человек
 * выбрал: без неё выбравший «по лекарству» увидел бы отмеченным «Сегодня».
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class WidgetProvidersSetupTest {

    @Test
    fun everyPlateIsNamedInTheSystemList() {
        val manifest = moduleFile("src/main/AndroidManifest.xml")

        WidgetType.entries.forEach { type ->
            assertTrue(
                "${type.name} must carry its own label: three identical names in the picker",
                manifest.contains("android:label=\"@string/${labelName(type)}\""),
            )
        }
    }

    @Test
    fun everyProviderInfoCarriesTheNameOfItsType() {
        WidgetType.entries.forEach { type ->
            val info = moduleFile("src/main/res/xml/${infoName(type)}")
            assertTrue(
                "${type.name} provider info must name the same type as its receiver",
                info.contains("@string/${labelName(type)}"),
            )
        }
    }

    @Test
    fun theProviderNameTellsTheType() {
        assertEquals(WidgetType.TODAY, WidgetType.ofProvider(RECEIVER_PREFIX + "TodayWidgetReceiver"))
        assertEquals(WidgetType.WEEK, WidgetType.ofProvider(RECEIVER_PREFIX + "WeekWidgetReceiver"))
        assertEquals(WidgetType.MEDICATION, WidgetType.ofProvider(RECEIVER_PREFIX + "MedicationWidgetReceiver"))
        assertEquals(null, WidgetType.ofProvider("ru.stohastika.sklyanka.OtherReceiver"))
        assertEquals(null, WidgetType.ofProvider(null))
    }

    /** Файл модуля читается от корня репозитория: его задаёт конвенция экранных проверок. */
    private fun moduleFile(relative: String): String {
        val root = requireNotNull(System.getProperty("sklyanka.repo.root")) { "sklyanka.repo.root is not set" }
        return File(File(root, "feature-widgets"), relative).readText()
    }

    private fun labelName(type: WidgetType): String = when (type) {
        WidgetType.TODAY -> "widget_type_today"
        WidgetType.WEEK -> "widget_type_week"
        WidgetType.MEDICATION -> "widget_type_medication"
    }

    private fun infoName(type: WidgetType): String = when (type) {
        WidgetType.TODAY -> "today_widget_info.xml"
        WidgetType.WEEK -> "week_widget_info.xml"
        WidgetType.MEDICATION -> "medication_widget_info.xml"
    }

    private companion object {
        const val RECEIVER_PREFIX = "ru.stohastika.sklyanka.feature.widgets.receiver."
    }
}
