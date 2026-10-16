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
 * Кадры блока «Оформление» и его диалогов (SKL-120, §5.17).
 *
 * Три состояния: выбор темы, выбор иконки и тот же выбор темы при системном шрифте 200 % —
 * строки диалога растут по высоте, а образец и миниатюра 40 dp не растут и не сжимаются
 * (§6.3). Сам блок «Оформление» в две строки входит в общий кадр экрана настроек
 * (`SettingsScreenSnapshotTest`): снимать его отдельно значило бы показать не ту поверхность,
 * какую видит человек.
 *
 * Стенд — имитация Android, а не EMUI: кадр говорит, что вёрстка не изменилась,
 * и ничего не говорит о том, как экран выглядит на настоящем устройстве.
 */
@RunWith(AndroidJUnit4::class)
class Sk120AppearanceSnapshotTest {

    @Test
    fun theThemePicker() = capture("settings-theme-dialog") {
        SettingsScreen(state = SettingsScreenFixtures.state().copy(themePickerVisible = true), onIntent = {})
    }

    @Test
    fun theIconPicker() = capture("settings-icon-dialog") {
        SettingsScreen(state = SettingsScreenFixtures.state().copy(iconPickerVisible = true), onIntent = {})
    }

    @Test
    fun theThemePickerAtDoubleFontScale() = capture("settings-theme-dialog-font-200", fontScale = SCALE_200) {
        SettingsScreen(state = SettingsScreenFixtures.state().copy(themePickerVisible = true), onIntent = {})
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
