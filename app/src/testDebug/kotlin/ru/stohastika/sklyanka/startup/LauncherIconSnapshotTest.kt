package ru.stohastika.sklyanka.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.appearance.IconVariant
import ru.stohastika.sklyanka.ui.component.FlatMark
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.MarkEnvironment
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.flatMarkColor
import ru.stohastika.sklyanka.ui.token.markTileBackground
import java.io.File

/**
 * Кадры двух вариантов иконки: базового холодного и тёплого «Про» (SKL-120, §2.4).
 *
 * Каждый вариант показан дважды: в размере плиты дизайн-системы (40 dp — тот, каким значок
 * виден в выборе оформления) и увеличенным вчетверо, чтобы разницу красок было видно
 * на ревью. Рисует кадр те же краски и ту же геометрию, что и ресурсы иконки
 * (`markTileBackground` / `flatMarkColor`): второго набора цветов знака не заводится.
 *
 * Чего кадр не показывает: как значок сложит лончер. Подложку, передний план и маску
 * собирает система по `mipmap/ic_launcher*`, и ступень «48 dp на домашнем экране»
 * снимается устройством — на JVM-стенде её нет.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIconSnapshotTest {

    @Test
    fun theIceVariant() = capture(IconVariant.ICE)

    @Test
    fun theWarmVariant() = capture(IconVariant.WARM)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(variant: IconVariant) {
        val directory = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the frame test must run through the screen test convention"
        }
        captureRoboImage(
            file = File(directory, "$GROUP/icon-${variant.key}.png"),
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = WIDTH, heightDp = HEIGHT)
            },
        ) {
            SklyankaTheme {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SklyankaTheme.colors.background)
                        .padding(SklyankaSpacing.Block),
                    verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
                ) {
                    MarkPlate(variant, SklyankaSizes.MarkerTile)
                    MarkPlate(variant, SklyankaSizes.MarkerTile * LARGE_FACTOR)
                }
            }
        }
    }

    @Composable
    private fun MarkPlate(variant: IconVariant, size: androidx.compose.ui.unit.Dp) {
        Box(
            modifier = Modifier
                .size(size)
                .background(markTileBackground(variant, MarkEnvironment.Light), SklyankaShapes.Card),
            contentAlignment = Alignment.Center,
        ) {
            FlatMark(
                color = flatMarkColor(variant, MarkEnvironment.Light),
                size = size * GLYPH_SHARE,
            )
        }
    }

    private companion object {
        const val GROUP = "screen"
        const val WIDTH = 390
        const val HEIGHT = 844

        /** Увеличение для ревью: краски вариантов различаются глазом при четырёх крат. */
        const val LARGE_FACTOR = 4

        /** Доля знака в плитке: та же, что у миниатюры выбора иконки (§5.17). */
        const val GLYPH_SHARE = 0.5f
    }
}
