package ru.heuri.sklyanka.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.heuri.sklyanka.ui.theme.ProfileAccent
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.ProfileColor
import ru.heuri.sklyanka.ui.token.SklyankaSpacing

/** Ширина макетов: 390 точек, целевые устройства — телефоны. */
const val PREVIEW_WIDTH = 390

/**
 * Один и тот же компонент в трёх темах подряд.
 *
 * Профиль владельца и чужой профиль рядом — это и есть проверка критериев 1 и 2:
 * разницу видно на одном снимке, а не в переписке о том, поменялся ли цвет.
 */
@Composable
fun PreviewEnvironments(content: @Composable () -> Unit) {
    Column {
        PreviewSurface(profile = ProfileAccent.Owner, dark = false, content = content)
        PreviewSurface(
            profile = ProfileAccent.Foreign(ProfileColor.Terracotta),
            dark = false,
            content = content,
        )
        PreviewSurface(profile = ProfileAccent.Owner, dark = true, content = content)
    }
}

@Composable
private fun PreviewSurface(profile: ProfileAccent, dark: Boolean, content: @Composable () -> Unit) {
    SklyankaTheme(profile = profile, dark = dark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SklyankaTheme.colors.background)
                .padding(SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        ) {
            content()
        }
    }
}
