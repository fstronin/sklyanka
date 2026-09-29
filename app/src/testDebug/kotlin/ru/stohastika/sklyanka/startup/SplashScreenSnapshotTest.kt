package ru.stohastika.sklyanka.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Эталонный снимок заставки (SKL-24, критерий 5).
 *
 * Заставка — первое, что человек видит, и единственный экран, который нельзя
 * посмотреть иначе как запуском приложения. Снимок фиксирует её состав: знак,
 * название, подпись, издатель — и отсутствие индикатора загрузки.
 */
@RunWith(AndroidJUnit4::class)
class SplashScreenSnapshotTest {

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun theSplash() {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        captureRoboImage(
            file = File(directory, "screen/splash.png"),
            roborazziComposeOptions = RoborazziComposeOptions { size(widthDp = WIDTH, heightDp = HEIGHT) },
        ) {
            SplashScreen()
        }
    }

    private companion object {
        const val WIDTH = 390
        const val HEIGHT = 844
    }
}
