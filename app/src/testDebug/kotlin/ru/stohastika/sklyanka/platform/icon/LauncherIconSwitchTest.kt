package ru.stohastika.sklyanka.platform.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowApplicationPackageManager
import org.w3c.dom.Element
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Переключение варианта иконки (SKL-120, §2.4, Р-31).
 *
 * Проверяется то, чем лончер пользуется, и ничего сверх: включён ровно один вариант,
 * у каждого свой значок при одном и том же имени, а процесс при переключении не убивают.
 * «Приложение живо» здесь означает именно `DONT_KILL_APP`: без него смена состояния
 * компонента перезапускает процесс, и человек, выбравший тёплый значок, оказался бы
 * выброшен из приложения на «Сегодня».
 *
 * Чего тест не доказывает: как значок покажет конкретный лончер. Он кэширует иконку
 * и обновляет её, когда сочтет нужным, — это ограничение платформы, названное в §2.4
 * и проверяемое на устройствах матрицы (Р-13).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class LauncherIconSwitchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val switch = LauncherIconSwitch(context)

    @Test
    fun exactlyOneVariantIsEnabled() {
        switch.apply(IconVariant.WARM)

        assertEquals(ENABLED, enabled(ALIAS_WARM))
        assertEquals(DISABLED, enabled(ALIAS_ICE))
        assertEquals(listOf(1), listOf(enabled(ALIAS_ICE), enabled(ALIAS_WARM)).countOfEnabled())
    }

    @Test
    fun theBaseVariantComesBack() {
        switch.apply(IconVariant.WARM)
        switch.apply(IconVariant.ICE)

        assertEquals(ENABLED, enabled(ALIAS_ICE))
        assertEquals(DISABLED, enabled(ALIAS_WARM))
    }

    @Test
    fun thePlatformIsAskedNotToKillTheApp() {
        switch.apply(IconVariant.WARM)

        listOf(ALIAS_ICE, ALIAS_WARM).forEach { alias ->
            val flags = shadow().getComponentEnabledSettingFlags(ComponentName(context, alias))
            assertEquals(
                "switching the icon must not restart the process (§2.4, DONT_KILL_APP)",
                PackageManager.DONT_KILL_APP,
                flags and PackageManager.DONT_KILL_APP,
            )
        }
    }

    @Test
    fun theSameVariantIsNotWrittenTwice() {
        switch.apply(IconVariant.WARM)
        val before = shadow().getComponentEnabledSettingFlags(ComponentName(context, ALIAS_WARM))

        switch.apply(IconVariant.WARM)

        assertEquals(
            "the same state must not be written again on every start (§2.4)",
            before,
            shadow().getComponentEnabledSettingFlags(ComponentName(context, ALIAS_WARM)),
        )
    }

    @Test
    fun theLauncherNameDoesNotChangeWithTheVariant() {
        assertEquals(
            "the launcher name belongs to the product, not to the appearance (В-120-2)",
            listOf("@string/app_name"),
            aliases().map { it.android("label") }.distinct(),
        )
        assertEquals("Склянка", context.getString(ru.stohastika.sklyanka.R.string.app_name))
    }

    @Test
    fun everyVariantCarriesItsOwnIcon() {
        assertEquals(
            listOf("@mipmap/ic_launcher", "@mipmap/ic_launcher_warm"),
            aliases().map { it.android("icon") }.sorted(),
        )
    }

    @Test
    fun theChoiceIsAppliedOnEveryStart() {
        // Состояние компонента переживает перезапуск процесса, но не переустановку:
        // поэтому выбор применяется и на старте, а не только в момент выбора (§2.4).
        switch.apply(IconVariant.WARM)

        LauncherIconSwitch(context).apply(IconVariant.WARM)

        assertEquals(ENABLED, enabled(ALIAS_WARM))
        assertEquals(DISABLED, enabled(ALIAS_ICE))
    }

    private fun List<Int>.countOfEnabled(): List<Int> = listOf(count { it == ENABLED })

    private fun enabled(alias: String): Int =
        context.packageManager.getComponentEnabledSetting(ComponentName(context, alias))

    private fun aliases(): List<Element> {
        val root = requireNotNull(System.getProperty(REPO_ROOT_PROPERTY)) {
            "$REPO_ROOT_PROPERTY is not set: the screen test convention must be applied to this module"
        }
        val file = File(root, MANIFEST)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("activity-alias")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.android(name: String): String = getAttribute("android:$name")

    private fun shadow(): ShadowApplicationPackageManager =
        Shadow.extract(context.packageManager) as ShadowApplicationPackageManager

    private companion object {
        const val REPO_ROOT_PROPERTY = "sklyanka.repo.root"
        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val ALIAS_ICE = "ru.stohastika.sklyanka.MainActivityIce"
        const val ALIAS_WARM = "ru.stohastika.sklyanka.MainActivityWarm"
        const val ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        const val DISABLED = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
