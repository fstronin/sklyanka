package ru.stohastika.sklyanka.feature.widgets

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.AppearancePreferences
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.widget.WidgetProfileIndication
import ru.stohastika.sklyanka.feature.widgets.di.WidgetEntryPoint
import ru.stohastika.sklyanka.feature.widgets.glance.WidgetLook
import ru.stohastika.sklyanka.ui.token.ProfileColor
import ru.stohastika.sklyanka.ui.token.ProfilePalette
import ru.stohastika.sklyanka.ui.token.darkColors
import ru.stohastika.sklyanka.ui.token.lightColors

/**
 * Акцент плиты следует акцентной теме человека (§5.11, §9, §2.5, SKL-120).
 *
 * Плита ролей не переопределяет: у владельца её акцент — роль `accent` выбранной темы,
 * и это проверяется **разрешением тех же ресурсов**, какими плита рисуется, а не
 * пересказом: значение ресурса сверяется с токеном дизайн-системы. Дубликат цветов
 * в `values/widget_colors.xml` и `values-night/widget_colors.xml` существует вынужденно
 * (у `ColorProvider` Glance 1.1 нет пары «день · ночь»), и эта проверка — тот страж,
 * который не даёт ему разойтись с палитрой.
 *
 * **Среда — вторая проверка, а не второй предмет**: [WidgetThemeAccentNightTest] делает
 * то же самое под квалификатором `-night`, каким среду выбирает продукт (Р-23).
 *
 * **Палитра профилей не тронута** (§2.2, Р-31): в чужом профиле акцент — его цвет, и он
 * одинаков под всеми четырьмя темами. Это и есть «две независимые системы»: тема меняет
 * акцент владельца и не касается профиля, а профиль не касается темы.
 */
@RunWith(AndroidJUnit4::class)
// Язык и плотность — как у стенда разметки: проверке нужны ресурсы, а не слова (НФТ-43а).
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class WidgetThemeAccentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theOwnerAccentIsTheAccentOfTheChosenTheme() {
        AccentTheme.entries.forEach { theme ->
            assertEquals(
                "the plate accent of «${theme.key}» must be the accent of that theme (§2.5)",
                lightColors(theme).accent,
                colorOf(WidgetLook.accent(null, theme)),
            )
        }
    }

    @Test
    fun theForeignProfileAccentIsItsColourUnderEveryTheme() {
        val foreign = WidgetProfileIndication(name = "mother", color = ProfileColor.Terracotta.argb)
        AccentTheme.entries.forEach { theme ->
            assertEquals(
                "under «${theme.key}» the foreign plate must carry the profile colour (§2.2, ПРФ-12)",
                ProfilePalette.of(foreign.color).color,
                colorOf(WidgetLook.accent(foreign, theme)),
            )
        }
    }

    @Test
    fun theForeignProfileAccentIgnoresTheTheme() {
        val foreign = WidgetProfileIndication(name = "mother", color = ProfileColor.Terracotta.argb)
        val underThemes = AccentTheme.entries.map { colorOf(WidgetLook.accent(foreign, it)) }
        assertEquals(
            "the profile palette is a system of its own: the theme must not repaint it (Р-31)",
            1,
            underThemes.distinct().size,
        )
    }

    /** Разрешение ресурса плиты тем же контекстом, каким её рисует стенд лончера. */
    internal fun colorOf(provider: ColorProvider): Color = provider.getColor(context)
}

/**
 * Тот же акцент под системной тёмной средой (§9, Р-23).
 *
 * Среда выбирается квалификатором ресурса, как в продукте, — своего переключателя у неё нет.
 * Отдельным классом, а не переключением квалификатора внутри теста: имитация Android держит
 * конфигурацию на класс, и «вернуть светлую среду обратно» в том же прогоне означало бы
 * проверять её на состоянии, оставленном соседом.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "night")
class WidgetThemeAccentNightTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theOwnerAccentInTheDarkEnvironmentIsTheDarkValueOfTheTheme() {
        AccentTheme.entries.forEach { theme ->
            assertEquals(
                "under «${theme.key}» the night plate must carry the dark accent of that theme (§9)",
                darkColors(theme).accent,
                WidgetLook.accent(null, theme).getColor(context),
            )
            assertNotEquals(
                "the dark accent of «${theme.key}» must differ from its light one: " +
                    "lightness is not transferred to the dark environment (§2.5)",
                lightColors(theme).accent,
                WidgetLook.accent(null, theme).getColor(context),
            )
        }
    }

    @Test
    fun theForeignProfileAccentIsTheSameAsInTheLightEnvironment() {
        val foreign = WidgetProfileIndication(name = "mother", color = ProfileColor.Plum.argb)
        AccentTheme.entries.forEach { theme ->
            assertEquals(
                "the profile colour must not change with the environment: it is a system of its own (Р-31)",
                ProfilePalette.of(foreign.color).color,
                WidgetLook.accent(foreign, theme).getColor(context),
            )
        }
    }
}

/**
 * Плита **узнаёт** тему из настроек, а не только красит переданное (§5.11, Р-23).
 *
 * Первая проверка держит шов: тема приходит в плиту тем же сценарием домена, каким её
 * читает приложение, и объявлена в точке входа виджета — иначе `provideGlance` не смог бы
 * её спросить вовсе. Вторая проводит связь целиком: сохранённый выбор → сценарий домена →
 * акцент плиты.
 *
 * **Названный предел.** Сам `provideGlance` (Glance + граф Hilt) под JVM не поднимается:
 * это цена запуска RemoteViews ради двух строк. Проводку «выбрал человек → плита» держат
 * поход на устройстве и обновление плиты приложением (SKL-118).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h844dp-mdpi")
class WidgetThemeSeamTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theEntryPointAsksTheGraphForTheTheme() {
        val seam = WidgetEntryPoint::class.java.methods.singleOrNull { it.name == SEAM }
        assertNotNull(
            "the widget must ask the graph for the accent theme: ${WidgetEntryPoint::class.java.methods.map {
                it.name
            }}",
            seam,
        )
        assertEquals(
            "the seam must be the same use case the application reads the theme with",
            ObserveAccentThemeUseCase::class.java,
            seam?.returnType,
        )
    }

    @Test
    fun thePlateCarriesTheAccentOfTheStoredTheme() {
        val store = StoredAppearance()
        runBlocking { store.setAccentTheme(AccentTheme.AMETHYST) }
        val theme = runBlocking { ObserveAccentThemeUseCase(store).current() }

        assertEquals(
            "the plate must carry the accent of the theme the person chose (§2.5, §5.11)",
            lightColors(AccentTheme.AMETHYST).accent,
            WidgetLook.accent(profile = null, theme = theme).getColor(context),
        )
    }

    private companion object {
        const val SEAM = "accentTheme"
    }
}

/** Постоянные настройки оформления в памяти: подменяется носитель, а не правило (ADR-0007). */
private class StoredAppearance : AppearancePreferences {

    private val theme = MutableStateFlow(AccentTheme.DEFAULT)

    private val variant = MutableStateFlow(IconVariant.DEFAULT)

    override val accentTheme: Flow<AccentTheme> = theme

    override val iconVariant: Flow<IconVariant> = variant

    override suspend fun setAccentTheme(theme: AccentTheme) {
        this.theme.value = theme
    }

    override suspend fun setIconVariant(variant: IconVariant) {
        this.variant.value = variant
    }
}
