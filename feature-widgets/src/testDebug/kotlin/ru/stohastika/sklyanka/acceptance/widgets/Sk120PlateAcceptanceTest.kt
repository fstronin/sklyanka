package ru.stohastika.sklyanka.acceptance.widgets

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.acceptance.run.ScreenAcceptanceRun
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.widget.WidgetProfileIndication
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetLook
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.lightColors

/**
 * Экранные шаги приёмки SKL-120 — плита виджета читает те же роли, что экраны (§5.11, §9).
 *
 * Плита ролей не переопределяет: у владельца её акцент — роль `accent` выбранной темы,
 * у чужого профиля — цвет профиля, и он сильнее темы (§2.2, ПРФ-12). Шаг **разрешает**
 * тот же ресурс, каким плита рисуется, и сверяет его с токеном дизайн-системы: пересказа
 * красок здесь нет.
 *
 * Среда выбирается **квалификатором**: тёмный вариант — тот же ресурс под конфигурацией
 * `night`, каким его видит плита на телефоне с тёмной темой (Р-23). Так проверка не зависит
 * от того, в какой среде идёт сам прогон.
 *
 * Чего этот шаг не проверяет: как плиту срисует лончер. Кадр плиты требует устройства
 * (`WidgetMarkupTest` собирает разметку Glance на JVM, но не снимает её).
 */
class Sk120PlateSteps {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var owner: ColorProvider? = null

    private var foreign: ColorProvider? = null

    @Когда("^на стенде собрана плита владельца под темой «([^»]+)»$")
    fun theOwnerPlateIsBuiltUnderThatTheme(name: String) {
        owner = WidgetLook.accent(profile = null, theme = themeNamed(name))
    }

    @Когда("^на стенде собрана плита чужого профиля под темой «([^»]+)»$")
    fun theForeignPlateIsBuiltUnderThatTheme(name: String) {
        val profile = WidgetProfileIndication(name = "mother", color = ProfileColor.Terracotta.argb)
        foreign = WidgetLook.accent(profile = profile, theme = themeNamed(name))
    }

    @Тогда("^на стенде акцент плиты — акцент «([^»]+)»$")
    fun thePlateAccentIsTheThemeAccent(name: String) {
        assertEquals(
            "the plate must carry the accent of the chosen theme (§2.5, §5.11)",
            lightColors(themeNamed(name)).accent,
            colorOf(requireNotNull(owner), context),
        )
    }

    @Тогда("^на стенде в тёмной среде плита несёт тёмный акцент «([^»]+)»$")
    fun thePlateCarriesTheDarkAccentOfTheTheme(name: String) {
        val theme = themeNamed(name)
        assertEquals(
            "under the dark environment the plate must carry the dark accent of the theme (§9)",
            darkColors(theme).accent,
            colorOf(requireNotNull(owner), nightContext()),
        )
    }

    @Тогда("^на стенде акцент плиты — «([^»]+)»$")
    fun thePlateAccentIsTheProfileColour(name: String) {
        val argb =
            requireNotNull(PROFILE_COLOURS[name]) { "unknown profile colour: $name (known: ${PROFILE_COLOURS.keys})" }
        assertEquals(
            "in a foreign profile the plate carries the profile colour (ПРФ-12)",
            ProfilePalette.of(argb).color,
            colorOf(requireNotNull(foreign), context),
        )
    }

    @Тогда("^на стенде палитра профилей темой не подменяется$")
    fun theProfilePaletteIsNotRepaintedByTheTheme() {
        val underThemes = AccentTheme.entries.map { theme ->
            WidgetLook.accent(
                profile = WidgetProfileIndication(name = "mother", color = ProfileColor.Terracotta.argb),
                theme = theme,
            ).getColor(context)
        }
        assertEquals("the profile palette is a system of its own (Р-31)", 1, underThemes.distinct().size)
        assertEquals(
            "the profile colour must not be the owner accent of any theme",
            0,
            underThemes.count { it == lightColors().accent },
        )
    }

    /** Тот же ресурс под конфигурацией ночи: среда — квалификатор, а не второй набор токенов. */
    private fun nightContext(): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        },
    )

    private fun colorOf(provider: ColorProvider, where: Context): Color = provider.getColor(where)

    private fun themeNamed(name: String): AccentTheme {
        val stem = stem(name)
        return requireNotNull(THEMES.entries.singleOrNull { stem(it.key) == stem }?.value) {
            "unknown accent theme: $name (known: ${THEMES.keys})"
        }
    }

    private fun stem(name: String): String = name.trim().trimEnd { it in CASE_ENDINGS }

    private companion object {

        /** Имена тем §2.5 так, как их называет сценарий. */
        val THEMES: Map<String, AccentTheme> = mapOf(
            "Олива" to AccentTheme.OLIVE,
            "Индиго" to AccentTheme.INDIGO,
            "Хвоя" to AccentTheme.PINE,
            "Аметист" to AccentTheme.AMETHYST,
        )

        /** Падежные окончания имён тем: основа сравнивается без них. */
        val CASE_ENDINGS: CharArray = "аяуюыиеоёймАЯУЮЫИЕОЁЙМ".toCharArray()

        /** Имена цветов профиля из закрытого набора (§2.2): их называет сценарий. */
        val PROFILE_COLOURS: Map<String, Int> = mapOf(
            "Терракота" to ProfileColor.Terracotta.argb,
            "Индиго" to ProfileColor.Indigo.argb,
        )
    }
}

/**
 * Экранный прогон приёмки SKL-120 — плита виджета.
 *
 * Модуль свой, а не общий: роли плиты живут в `feature-widgets`, и ресурсы тёмной среды —
 * тоже (`values-night/widget_colors.xml`). Группа `@screen and @widget` отсекает сценарии
 * других стендов: у блока «Оформление», иконки и документа двери свои.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class Sk120PlateAcceptanceTest {

    @Test
    fun thePlateScenariosOfSk120RunOnTheStand() {
        assertNull(ScreenAcceptanceRun.run(task = TASK, tags = TAGS))
    }

    private companion object {
        const val TASK = "SKL-120"

        const val TAGS = "@screen and @widget"
    }
}
