package ru.stohastika.sklyanka.ui.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.size
import ru.stohastika.sklyanka.ui.theme.ProfileAccent
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import java.io.File

/**
 * Общая обвязка эталонных снимков (SKL-24, `TDD.md §6`).
 *
 * Путь эталона собирается от каталога, который задала конвенция сборки, а не от
 * рабочего каталога прогона: относительный путь разрешается по рабочему каталогу, и это
 * ровно тот способ получить эталоны в неожиданном месте, который потом не объяснить.
 */
internal object Snapshots {

    /** Ширина макетов «Склянки»: 390 точек. */
    const val WIDTH = 390

    /** Высота по содержимому: снимок состояния не обязан быть размером с экран. */
    private const val WRAP = 0

    private val outputDirectory: File by lazy {
        val configured = requireNotNull(System.getProperty("roborazzi.output.dir")) {
            "roborazzi.output.dir is not set: the screen test convention must be applied to this module"
        }
        File(configured)
    }

    /** `<модуль>/screenshots/<группа>/<состояние>.png`. */
    fun golden(group: String, name: String): File = File(outputDirectory, "$group/$name.png")

    @OptIn(ExperimentalRoborazziApi::class)
    fun options(widthDp: Int = WIDTH, heightDp: Int = WRAP, fontScale: Float = 1f): RoborazziComposeOptions =
        RoborazziComposeOptions {
            size(widthDp = widthDp, heightDp = heightDp)
            fontScale(fontScale)
        }
}

/**
 * Поверхность снимка: тема, фон и поля вокруг содержимого.
 *
 * Компонент снимается на фоне своей среды, а не на прозрачном полотне: половина
 * проверяемого здесь — именно то, как поверхность и компонент соотносятся по цвету.
 */
@Composable
internal fun SnapshotSurface(
    profile: ProfileAccent = ProfileAccent.Owner,
    dark: Boolean = false,
    content: @Composable () -> Unit,
) {
    SklyankaTheme(profile = profile, dark = dark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SklyankaTheme.colors.background)
                .padding(SklyankaSpacing.Block),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        ) {
            content()
        }
    }
}
