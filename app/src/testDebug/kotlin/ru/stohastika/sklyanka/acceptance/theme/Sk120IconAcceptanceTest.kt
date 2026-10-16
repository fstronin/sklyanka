package ru.stohastika.sklyanka.acceptance.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowApplicationPackageManager
import org.w3c.dom.Element
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.platform.icon.LauncherIconSwitch
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Экранные шаги приёмки SKL-120 — два варианта иконки и тема окна (§2.4, §9).
 *
 * Стенд поднимает **настоящий манифест сборки** и настоящий `PackageManager`: варианты
 * иконки объявлены двумя `activity-alias`, и переключает их тот же
 * [LauncherIconSwitch], что у продукта. Значок в системном лончере стенд не рисует —
 * его показывает только устройство, — но проверяет всё, чем лончер пользуется: какой
 * вариант включён, какой значок и какое имя он несёт, и не пропадает ли ярлык.
 *
 * **Имя в лончере от варианта не зависит** (В-120-2): у обоих алиасов `android:label` —
 * `app_name`, и это проверяется по манифесту, а не по коду: переименование в лончере
 * сломало бы узнавание ярлыка, и заметить это в отрисовке нельзя.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class Sk120IconSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val switch = LauncherIconSwitch(context)

    private val manifest: Element by lazy { parseManifest() }

    @Когда("^на стенде лончера сверяют варианты иконки$")
    fun theIconVariantsAreCompared() {
        assertEquals("exactly two icon variants must be declared (§2.4)", 2, aliases().size)
    }

    @Тогда("^на стенде у каждого варианта свой значок, а имя в лончере одно$")
    fun everyVariantCarriesItsOwnIconAndTheSameName() {
        val icons = aliases().map { it.android("icon") }
        assertEquals("the variants must carry different icons (§2.4)", 2, icons.distinct().size)
        val labels = aliases().map { it.android("label") }.distinct()
        assertEquals("the launcher name must not change with the variant (В-120-2)", listOf("@string/app_name"), labels)
        assertEquals("Склянка", context.getString(R.string.app_name))
    }

    @Тогда("^на стенде включён ровно один вариант$")
    fun exactlyOneVariantIsEnabled() {
        // Состояние **установки**: каким приложение приходит после установки, а не каким
        // его сделал вызов продукта. Включён холодный вариант — он и есть базовый (Р-56).
        assertEquals(
            "the base variant must arrive enabled after install (§2.4)",
            "true",
            alias(ALIAS_ICE).android("enabled"),
        )
        assertEquals(
            "the paid variant must arrive disabled: it is turned on by the person's choice",
            "false",
            alias(ALIAS_WARM).android("enabled"),
        )
    }

    @Когда("^на стенде лончера выбран тёплый вариант иконки$")
    fun theWarmVariantIsChosen() {
        switch.apply(IconVariant.WARM)
    }

    @Тогда("^на стенде лончер показывает тёплый значок$")
    fun theLauncherShowsTheWarmIcon() {
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, enabled(ALIAS_WARM))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, enabled(ALIAS_ICE))
        assertEquals(
            "the enabled variant must be the warm icon (§2.4)",
            "@mipmap/ic_launcher_warm",
            alias(ALIAS_WARM).android("icon"),
        )
    }

    /**
     * Переключение не перезапускает процесс (`DONT_KILL_APP`).
     *
     * Флаг — то, чем платформа отличает «сменить значок» от «убить приложение»; стенд
     * проверяет флаг, который получила система, а не удовольствие от того, что процесс жив.
     */
    @Тогда("^на стенде переключение не требует перезапуска процесса$")
    fun theSwitchDoesNotKillTheProcess() {
        val flags = shadowOf().getComponentEnabledSettingFlags(ComponentName(context, ALIAS_WARM))
        assertEquals(
            "the switch must ask the platform not to kill the app (§2.4)",
            PackageManager.DONT_KILL_APP,
            flags and PackageManager.DONT_KILL_APP,
        )
    }

    @Тогда("^на стенде ярлык «Добавить лекарство» тот же$")
    fun theShortcutIsTheSameForBothVariants() {
        // Сам ярлык: действие, цель и слова читаются из `shortcuts.xml`, а вариантов
        // у списка ярлыков не бывает — файл один, и оба алиаса ссылаются на него.
        val shortcuts = File(repositoryRoot(), SHORTCUTS_FILE).readText()
        assertTrue("the shortcut must carry the add-medication action (SKL-80)", SHORTCUT_ACTION in shortcuts)
        assertTrue("the shortcut must launch the product's root", SHORTCUT_TARGET in shortcuts)
        assertEquals(
            "the shortcut words are the product's words, not the variant's",
            listOf("Добавить лекарство"),
            listOf(context.getString(R.string.add_medication_shortcut_label)),
        )
        aliases().forEach { alias ->
            val meta = (0 until alias.getElementsByTagName("meta-data").length)
                .map { alias.getElementsByTagName("meta-data").item(it) as Element }
                .singleOrNull { it.android("name") == SHORTCUTS_META }
            assertTrue(
                "each variant must point at the same shortcut list (SKL-80): ${alias.android("name")}",
                meta != null && meta.android("resource") == "@xml/shortcuts",
            )
        }
    }

    @Тогда("^на стенде уведомление несёт тот же знак$")
    fun theNotificationCarriesTheSameMark() {
        val mark = context.resources.getIdentifier("ic_notification_mark", "drawable", context.packageName)
        assertEquals(
            "the notification mark must be its own resource, not a launcher icon (§2.4)",
            R.drawable.ic_notification_mark,
            mark,
        )
        val reminder = File(repositoryRoot(), REMINDER_NOTIFIER).readText()
        assertTrue(
            "the reminder notification must paint the same mark the stand checked",
            reminder.contains("setSmallIcon(R.drawable.ic_notification_mark)"),
        )
        // Знак уведомления называет сам продукт — символом ресурса, а не значком лончера:
        // поэтому вариант иконки его не касается вовсе, и проверка сверяет это с кодом
        // напоминания, а не с вторым чтением того же идентификатора.
        assertNotEquals("the launcher icon must not be the notification mark", R.mipmap.ic_launcher, mark)
        assertNotEquals("the paid launcher icon must not be the notification mark", R.mipmap.ic_launcher_warm, mark)
    }

    // --- Тема окна ------------------------------------------------------------------

    @Когда("^на стенде приложения сверяют тему окна$")
    fun theWorkingThemeIsDeclared() {
        assertNotNull("the working theme must be declared", workingTheme(dark = false))
        assertNotNull("the working theme must be declared in the night resources too", workingTheme(dark = true))
    }

    /**
     * Force Dark выключен у **рабочей** темы в обеих средах (§9).
     *
     * Проверяется атрибут стиля `Theme.Sklyanka`, а не подстрока по файлу: запрет, написанный
     * в чужом стиле, приложение не защищает. Цвета назначены токенами, и системная инверсия
     * нарисовала бы поверх наших ролей вторую тему.
     */
    @Тогда("^на стенде обе среды запрещают принудительное затемнение$")
    fun bothEnvironmentsForbidForceDark() {
        listOf(false, true).forEach { dark ->
            val environment = if (dark) "dark" else "light"
            val theme = requireNotNull(workingTheme(dark)) { "no working theme in the $environment resources" }
            val forceDark = theme.getElementsByTagName("item")
                .let { items -> (0 until items.length).map { items.item(it) as Element } }
                .singleOrNull { it.getAttribute("name") == FORCE_DARK }
            assertNotNull(
                "the working theme must declare $FORCE_DARK in the ${if (dark) "dark" else "light"} environment",
                forceDark,
            )
            assertEquals("force dark must be off (§9)", "false", forceDark?.textContent)
        }
    }

    @Тогда("^на стенде тёмная среда окна объявлена ресурсами ночи$")
    fun theDarkWindowThemeIsDeclaredInTheNightResources() {
        assertTrue(
            "the night resources must exist: app/src/main/res/values-night/themes.xml",
            File(repositoryRoot(), "app/src/main/res/values-night/themes.xml").isFile,
        )
        assertTrue(
            "the night working theme must name the dark background (§9)",
            requireNotNull(workingTheme(dark = true)).textContent.contains(DARK_BACKGROUND_REF),
        )
        assertEquals(
            "the dark background of the window is #181712 (§9)",
            NIGHT_BACKGROUND,
            context.getColor(
                context.resources.getIdentifier("environment_dark_background", "color", context.packageName),
            ),
        )
    }

    // --- Манифест и ресурсы ---------------------------------------------------------

    private fun notificationMark(): Int =
        context.resources.getIdentifier("ic_notification_mark", "drawable", context.packageName)

    /** Стиль рабочей темы `Theme.Sklyanka` из файла темы нужной среды. */
    private fun workingTheme(dark: Boolean): Element? {
        val qualifier = if (dark) "values-night" else "values"
        val file = File(repositoryRoot(), "app/src/main/res/$qualifier/themes.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val styles = document.getElementsByTagName("style")
        // У стиля имя — атрибут без пространства имён (`<style name="…">`), в отличие от
        // компонентов манифеста, где оно пишется как `android:name`.
        return (0 until styles.length).map { styles.item(it) as Element }
            .singleOrNull { it.getAttribute("name") == WINDOW_THEME }
    }

    private fun enabled(alias: String): Int =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, alias))

    private fun alias(fqcn: String): Element = aliases().single {
        it.android("name").endsWith(fqcn.substringAfterLast('.'))
    }

    private fun aliases(): List<Element> {
        val nodes = manifest.getElementsByTagName("activity-alias")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun parseManifest(): Element {
        val file = File(repositoryRoot(), "app/src/main/AndroidManifest.xml")
        assertTrue("the manifest is missing: ${file.path}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return document.documentElement
    }

    private fun repositoryRoot(): String = requireNotNull(System.getProperty(REPO_ROOT_PROPERTY)) {
        "$REPO_ROOT_PROPERTY is not set: the screen test convention is missing"
    }

    private fun Element.android(name: String): String = getAttribute("android:$name")

    /** Тень менеджера пакетов: она помнит флаги, с которыми просили сменить состояние компонента. */
    private fun shadowOf(): ShadowApplicationPackageManager =
        Shadow.extract(context.packageManager) as ShadowApplicationPackageManager

    private companion object {
        const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"
        const val ALIAS_ICE = "ru.stohastika.sklyanka.MainActivityIce"
        const val ALIAS_WARM = "ru.stohastika.sklyanka.MainActivityWarm"
        const val SHORTCUTS_META = "android.app.shortcuts"
        const val WINDOW_THEME = "Theme.Sklyanka"
        const val FORCE_DARK = "android:forceDarkAllowed"
        const val SHORTCUTS_FILE = "app/src/main/res/xml/shortcuts.xml"
        const val SHORTCUT_ACTION = "ru.stohastika.sklyanka.action.ADD_MEDICATION"
        const val SHORTCUT_TARGET = "ru.stohastika.sklyanka.MainActivity"
        const val REMINDER_NOTIFIER =
            "app/src/main/kotlin/ru/stohastika/sklyanka/platform/notification/ReminderNotifier.kt"
        const val DARK_BACKGROUND_REF = "@color/environment_dark_background"

        /** #181712 — тёмная олива нормы (§9): значение, а не свойство. */
        const val NIGHT_BACKGROUND = 0xFF181712.toInt()
    }
}

/**
 * Экранный прогон приёмки SKL-120 — иконка и тема окна.
 *
 * Модуль `app`: здесь живёт манифест с двумя `activity-alias`, ресурсы обоих вариантов
 * и `LauncherIconSwitch` (ADR-0007 — реализация платформенного шва принадлежит `app`).
 * Группа `@screen and (@icon or @window)` отсекает сценарии других стендов: у настроек,
 * плиты, токенов и документа двери свои.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk120IconAcceptanceTest {

    @Test
    fun theIconAndWindowScenariosOfSk120RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-120"

        const val TAGS = "@screen and (@icon or @window)"
    }
}
