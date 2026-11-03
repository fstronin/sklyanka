package ru.stohastika.sklyanka.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.io.File

/**
 * Эталонный кадр экрана согласия на аналитику (SKL-165, TDD §11.5).
 *
 * Один кадр: у экрана одно состояние — вопрос, два действия и короткое пояснение.
 * Кадр лежит в общем каталоге эталонов `screenshots/screen`, рядом с кадрами экрана
 * разрешений, поэтому ревью видит новый экран вместе с продуктом.
 *
 * Высота — окно телефона, а не длинный лист: экран согласия не прокручивается,
 * и лишняя высота показала бы пустоту под кнопками.
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsConsentScreenSnapshotTest {

    @Test
    fun asTheProductShowsIt() = capture("analytics-consent") {
        AnalyticsConsentScreen(onAllow = {}, onDecline = {})
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/$name.png"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = HEIGHT) },
        ) {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390
        const val HEIGHT = 844
    }
}
