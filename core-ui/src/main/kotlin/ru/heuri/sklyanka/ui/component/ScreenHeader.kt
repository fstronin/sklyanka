package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType
import java.util.Locale

/**
 * Шапка экрана.
 *
 * Подкраска и надпись «Профиль · Имя» появляются сами: фон берётся из роли
 * `profileHeader`, которая в теме владельца совпадает с обычным фоном, а надпись
 * рисуется только когда передано [profileName]. Экран передаёт имя активного
 * профиля и не решает, показывать ли признак (ПРФ-12).
 *
 * Заголовок не обрезается и переносится на вторую строку при увеличенном системном
 * шрифте: шапка растёт по высоте (§6.3, критерий 5).
 */
@Composable
fun ScreenHeader(
    labels: ScreenHeaderLabels,
    modifier: Modifier = Modifier,
    kind: ScreenHeaderKind = ScreenHeaderKind.Root,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    val profileName = labels.profileName
    val overline = labels.overline
    val foreign = profileName != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.profileHeader)
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = if (foreign) SklyankaSizes.HeaderTopPaddingProfile else SklyankaSizes.HeaderTopPadding,
                bottom = SklyankaSizes.HeaderBottomPadding,
            )
            .testTag(TAG_SCREEN_HEADER),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            if (kind != ScreenHeaderKind.Root && onBack != null) {
                BackAction(kind, onBack)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
            ) {
                when {
                    foreign -> Text(
                        text = stringResource(R.string.profile_label, profileName.orEmpty()).uppercased(),
                        style = SklyankaType.Overline,
                        color = colors.accent,
                        modifier = Modifier.testTag(TAG_PROFILE_LABEL),
                    )

                    overline != null -> Text(
                        text = overline.uppercased(),
                        style = SklyankaType.OverlineDate,
                        color = colors.textMuted,
                    )
                }
                Text(
                    text = labels.title,
                    style = if (kind == ScreenHeaderKind.Modal) SklyankaType.BodyStrong else SklyankaType.TitleScreen,
                    color = colors.text,
                )
            }
            trailing?.invoke()
        }
        if (foreign) {
            // Нижняя граница появляется только у подкрашенной шапки: на обычном фоне
            // она отделяла бы шапку от того же самого цвета.
            HeaderDivider()
        }
    }
}

@Composable
private fun BackAction(kind: ScreenHeaderKind, onBack: () -> Unit) {
    val label = when (kind) {
        ScreenHeaderKind.Modal -> stringResource(R.string.action_cancel)
        else -> stringResource(R.string.action_back)
    }
    QuietButton(text = label, onClick = onBack, muted = kind == ScreenHeaderKind.Modal)
}

@Composable
private fun HeaderDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.line),
    )
}

/**
 * Верхний регистр меток задаётся здесь, а не `uppercase()` по месту на экране.
 *
 * `TextStyle` в Compose не умеет `text-transform`, поэтому правило §2.2 («ВЕРХНИЙ
 * РЕГИСТР») исполняется преобразованием — но ровно в одном месте дизайн-системы и
 * с учётом локали: `uppercase()` без локали ломается на второй локали (НФТ-43).
 */
private fun String.uppercased(): String = uppercase(Locale.getDefault())

const val TAG_SCREEN_HEADER = "screen_header"
const val TAG_PROFILE_LABEL = "profile_label"
