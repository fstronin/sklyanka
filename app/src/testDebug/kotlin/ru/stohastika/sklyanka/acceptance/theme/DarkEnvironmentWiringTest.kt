package ru.stohastika.sklyanka.acceptance.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.darkColors

/**
 * Среда интерфейса следует системной настройке (SKL-120, Р-23, §9).
 *
 * Проверяется **проводка**, а не подставленное значение: сигнал системы читается тем же
 * `isSystemInDarkTheme()`, каким его читает каркас приложения, и попадает в тот же
 * `SklyankaTheme(dark = …)`. Под ночным квалификатором среда обязана стать тёмной —
 * то есть «Допустим в системе включена тёмная тема» в сценариях означает именно это.
 *
 * **Названный предел.** Тест исполняет место проводки (сигнал → тема), но сам `MainActivity`
 * под JVM не поднимается: ему нужны Hilt, база и восстановление напоминаний, и это цена
 * запуска всего продукта ради двух строк. Две строки `val interfaceDark = isSystemInDarkTheme()`
 * и `SklyankaTheme(dark = interfaceDark)` в `MainActivity` держатся ревью, а полный путь
 * до кадра — походом на устройстве. Предел назван в `docs/tasks/SKL-120/TODO.md`.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "night")
class DarkEnvironmentWiringTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theInterfaceEnvironmentFollowsTheSystemSetting() {
        assertTrue(
            "the night qualifier must reach the stand: ${RuntimeEnvironment.getQualifiers()}",
            RuntimeEnvironment.getQualifiers().contains("night"),
        )
        val seen = mutableListOf<Pair<Boolean, Int>>()
        runComposeUiTest {
            setContent {
                val interfaceDark = isSystemInDarkTheme()
                SklyankaTheme(dark = interfaceDark) {
                    seen += interfaceDark to SklyankaTheme.colors.background.toArgb()
                }
            }
        }
        assertEquals("the system must be in the dark environment", listOf(true), seen.map { it.first })
        assertEquals(
            "the composed roles must be the dark ones of §9",
            listOf(darkColors().background.toArgb()),
            seen.map { it.second },
        )
    }
}
