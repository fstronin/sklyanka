package ru.stohastika.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File

/**
 * Кадры акцентных тем и тёмной среды на экране «Сегодня» (SKL-120, §2.5, §9).
 *
 * Четыре темы в двух средах — восемь кадров. Один и тот же день, одна и та же вёрстка,
 * меняется только оформление: кадр отвечает на вопрос «видно ли тему», а не «как выглядит
 * приём». Состояние берётся из тех же заглушек, что у остальных снимков экрана.
 *
 * Светлые кадры держат тему как она есть, тёмные — роли §9: в тёмной среде акцент светлее,
 * и «олива» там не та же краска, что днём.
 *
 * Стенд — имитация Android, а не EMUI: кадр говорит, что тема доехала до экрана,
 * и ничего не говорит о том, как он выглядит на настоящем устройстве.
 */
@RunWith(AndroidJUnit4::class)
class TodayThemeSnapshotTest {

    @Test
    fun oliveLight() = capture(AccentTheme.OLIVE, dark = false)

    @Test
    fun oliveDark() = capture(AccentTheme.OLIVE, dark = true)

    @Test
    fun indigoLight() = capture(AccentTheme.INDIGO, dark = false)

    @Test
    fun indigoDark() = capture(AccentTheme.INDIGO, dark = true)

    @Test
    fun pineLight() = capture(AccentTheme.PINE, dark = false)

    @Test
    fun pineDark() = capture(AccentTheme.PINE, dark = true)

    @Test
    fun amethystLight() = capture(AccentTheme.AMETHYST, dark = false)

    @Test
    fun amethystDark() = capture(AccentTheme.AMETHYST, dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(theme: AccentTheme, dark: Boolean) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        val environment = if (dark) DARK else LIGHT
        captureRoboImage(
            file = File(directory, "$GROUP/theme-${theme.key}-$environment.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = HEIGHT)
            },
        ) {
            Surface(theme = theme, dark = dark) {
                TodayScreen(state = TodayFixtures.plainDay(markedByYou = true), onIntent = {})
            }
        }
    }

    @Composable
    private fun Surface(theme: AccentTheme, dark: Boolean, content: @Composable () -> Unit) {
        SklyankaTheme(theme = theme, dark = dark) {
            Box(modifier = Modifier.fillMaxWidth().background(SklyankaTheme.colors.background)) {
                content()
            }
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390
        const val HEIGHT = 844
        const val LIGHT = "light"
        const val DARK = "dark"
    }
}
