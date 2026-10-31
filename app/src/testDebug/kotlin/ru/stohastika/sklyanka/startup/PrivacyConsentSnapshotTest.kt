package ru.stohastika.sklyanka.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
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
 * Эталон первого запуска с политикой (Р-89): диалог до онбординга, единственная кнопка.
 *
 * Фон под затемнением — роль `background`, а не первый экран: кадр фиксирует сам диалог,
 * а не состояние «Сегодня» или онбординга, которое от запуска к запуску разное.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyConsentSnapshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun thePolicyBeforeTheFirstScreen() {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "screen/privacy-first-launch.png"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = HEIGHT) },
        ) {
            SklyankaTheme {
                Box(modifier = Modifier.fillMaxSize().background(SklyankaTheme.colors.background)) {
                    PrivacyConsentDialog(onOpenPolicy = {}, onAgree = {}, onDecline = {})
                }
            }
        }
    }

    private companion object {
        const val WIDTH = 390
        const val HEIGHT = 844
    }
}
