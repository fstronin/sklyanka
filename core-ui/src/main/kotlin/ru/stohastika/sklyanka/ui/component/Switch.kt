package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Строка списка с переключателем.
 *
 * Нажимается вся строка, а не сам переключатель: цель нажатия 52×32 меньше нормы по
 * высоте, и попасть в неё пальцем в списке из восьми настроек трудно (критерий 6).
 * Поэтому `toggleable` стоит на строке, а не на бегунке, и переключатель сам по
 * себе нажатия не принимает — иначе в дереве было бы две цели вместо одной.
 */
@Composable
fun SklyankaSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Large)
            .testTag(TAG_SWITCH_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
        ) {
            Text(
                text = label,
                style = SklyankaType.BodyDefault,
                color = if (enabled) colors.text else colors.textDisabled,
            )
            if (caption != null) {
                Text(text = caption, style = SklyankaType.CaptionSmall, color = colors.textMuted)
            }
        }
        SklyankaSwitch(
            state = when {
                !enabled -> SwitchState.Disabled
                checked -> SwitchState.On
                else -> SwitchState.Off
            },
        )
    }
}

/**
 * Сам переключатель: габарит 52×32, бегунок 26 (§4.5).
 *
 * Нажатия не принимает — его принимает строка. Отдельно он существует для каталога
 * и для случаев, когда строку собирает экран.
 */
@Composable
fun SklyankaSwitch(state: SwitchState, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    val track = when (state) {
        SwitchState.On -> colors.accent
        SwitchState.Off -> colors.switchOff
        SwitchState.Disabled -> colors.progressTrack
    }
    val thumb = if (state == SwitchState.Disabled) colors.background else colors.surface
    Box(
        modifier = modifier
            .width(SklyankaSizes.SwitchWidth)
            .height(SklyankaSizes.SwitchHeight)
            .background(track, SklyankaShapes.Pill)
            .padding(SklyankaSizes.SwitchPadding)
            .testTag(TAG_SWITCH),
        contentAlignment = if (state == SwitchState.On) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.SwitchThumb)
                .background(thumb, SklyankaShapes.Pill),
        )
    }
}

const val TAG_SWITCH_ROW = "switch_row"
const val TAG_SWITCH = "switch"
