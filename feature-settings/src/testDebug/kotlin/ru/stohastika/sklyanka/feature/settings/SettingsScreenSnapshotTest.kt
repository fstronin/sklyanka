package ru.stohastika.sklyanka.feature.settings

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File

/**
 * Эталонные снимки экрана настроек (SKL-24).
 *
 * Три состояния, а не одно: одно разрешение не выдано — состояние, близкое к макету;
 * всё выдано — то, как экран выглядит в собранном продукте; и тот же экран при системном
 * шрифте 200 %, где вёрстка обязана расти, а не обрезаться (НФТ-31). Строки часов
 * на снимках нет по решению `ADR-0009`.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenSnapshotTest {

    @Test
    fun theScreenAsTheMockupDrawsIt() = capture("settings") {
        SettingsScreen(state = SettingsScreenFixtures.state(), onIntent = {})
    }

    @Test
    fun theScreenAsTheBuiltProductShowsIt() = capture("settings-product") {
        SettingsScreen(state = SettingsScreenFixtures.state(denied = 0), onIntent = {})
    }

    @Test
    fun theScreenAtDoubleFontScale() = capture("settings-font-200", fontScale = SCALE_200) {
        SettingsScreen(state = SettingsScreenFixtures.state(), onIntent = {})
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = HEIGHT)
                fontScale(fontScale)
            },
        ) {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390
        const val HEIGHT = 1400
        const val SCALE_200 = 2.0f
    }
}
