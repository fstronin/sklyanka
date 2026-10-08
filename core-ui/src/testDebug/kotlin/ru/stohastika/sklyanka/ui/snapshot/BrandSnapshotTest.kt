package ru.stohastika.sklyanka.ui.snapshot

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.component.AdherenceDay
import ru.stohastika.sklyanka.ui.component.AdherenceDayState
import ru.stohastika.sklyanka.ui.component.AdherenceWeek
import ru.stohastika.sklyanka.ui.preview.LockupGallery
import ru.stohastika.sklyanka.ui.preview.MarkGallery
import ru.stohastika.sklyanka.ui.preview.TypographyGallery

/**
 * Эталоны знака, логотипа, типографической шкалы и недельной полосы (SKL-24).
 *
 * Закрывает пункт 7 `docs/archive/tasks/SKL-21/TODO.md`, пропущенный тогда со словами
 * «в проекте нет снимочного стенда». Геометрия знака правится только осознанно:
 * лишняя точка в кольце валит сверку.
 *
 * Лестница знака снимается одним листом, а не по ступени на снимок: смысл ступеней —
 * в том, что предельная остаётся тем же знаком рядом с крупной, и увидеть это можно
 * только на одном снимке.
 */
@RunWith(AndroidJUnit4::class)
class BrandSnapshotTest {

    @Test
    fun theMarkLadder() = capture("mark-ladder") { MarkGallery() }

    @Test
    fun theLockup() = capture("lockup") { LockupGallery() }

    @Test
    fun theTypographyScale() = capture("typography-scale", heightDp = SCALE_HEIGHT) { TypographyGallery() }

    /** Недельная полоса со всеми состояниями дня сразу (`AdherenceDayState`). */
    @Test
    fun theAdherenceWeek() = capture("adherence-week") {
        AdherenceWeek(
            days = AdherenceDayState.entries.mapIndexed { index, state ->
                AdherenceDay(
                    label = (index + 1).toString(),
                    state = state,
                    description = "day-${index + 1}-${state.name.lowercase()}",
                )
            },
        )
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, heightDp: Int = 0, content: @Composable () -> Unit) {
        captureRoboImage(
            file = Snapshots.golden(GROUP, name),
            roborazziComposeOptions = Snapshots.options(heightDp = heightDp),
        ) {
            SnapshotSurface { content() }
        }
    }

    private companion object {
        const val GROUP = "brand"

        /** Шкала длиннее экрана: двадцать один стиль подряд. */
        const val SCALE_HEIGHT = 1800
    }
}
