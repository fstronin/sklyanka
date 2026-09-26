package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Баннер — строка-предупреждение в шапке экрана.
 *
 * Значок слева не украшение: он второй носитель смысла рядом с цветом (НФТ-33), и
 * у него есть `contentDescription`, потому что видимого текста у него нет (НФТ-30).
 *
 * Справа допускается ровно одно тихое действие: два действия в строке-предупреждении
 * превращают её в диалог, который человек читать не станет.
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    kind: BannerKind = BannerKind.Warning,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    val background = when (kind) {
        BannerKind.Warning -> colors.accentSoft
        BannerKind.Profile -> colors.surface
        BannerKind.Neutral -> colors.neutralSoft
    }
    val textColor = when (kind) {
        BannerKind.Neutral -> colors.textMuted
        else -> colors.onAccentSoft
    }
    Row(
        modifier = modifier
            .background(background, SklyankaShapes.Medium)
            .padding(
                horizontal = SklyankaSizes.BannerPaddingHorizontal,
                vertical = SklyankaSizes.BannerPaddingVertical,
            )
            .testTag(TAG_BANNER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
    ) {
        when (kind) {
            BannerKind.Warning -> WarningGlyph(
                color = colors.warning,
                modifier = Modifier.semanticsLabel(stringResource(R.string.cd_warning)),
            )

            BannerKind.Profile -> ProfileGlyph(
                color = colors.accent,
                modifier = Modifier.semanticsLabel(stringResource(R.string.cd_profile)),
            )

            BannerKind.Neutral -> Unit
        }
        Text(
            text = text,
            style = SklyankaType.Caption,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            QuietButton(text = actionLabel, onClick = onAction)
        }
    }
}

const val TAG_BANNER = "banner"
