package ru.heuri.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.heuri.sklyanka.ui.component.MedicationGlyph
import ru.heuri.sklyanka.ui.modifier.minTouchTarget
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Нижняя панель на три вкладки (`Main.dc.html`).
 *
 * Живёт на экране «Сегодня», а не в дизайн-системе: в каталоге компонентов панели
 * нет, а `feature-meds` её не рисует — там об этом сказано прямо.
 *
 * Подпись вкладки скрывается на большом системном масштабе шрифта, а значок остаётся
 * с подписью для чтения с экрана: скрывается дубль, а не смысл (§6.3, НФТ-30).
 */
@Composable
internal fun TodayBottomBar(onIntent: (TodayIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.surface).testTag(TAG_BOTTOM_BAR)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SklyankaSizes.DividerThickness)
                .background(colors.line),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SklyankaSpacing.Screen,
                    vertical = SklyankaSpacing.Default,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab(
                label = stringResource(R.string.tab_today),
                selected = true,
                onClick = {},
                icon = { color -> CalendarGlyph(color) },
            )
            Tab(
                label = stringResource(R.string.tab_meds),
                selected = false,
                onClick = { onIntent(TodayIntent.OpenMedicationList) },
                icon = { color -> MedicationGlyph(color) },
            )
            Tab(
                label = stringResource(R.string.tab_settings),
                selected = false,
                onClick = { onIntent(TodayIntent.OpenSettings) },
                icon = { color -> GearGlyph(color) },
            )
        }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val colors = SklyankaTheme.colors
    val tint = if (selected) colors.accent else colors.textTertiary
    val showLabel = LocalDensity.current.fontScale <= SklyankaSizes.LABEL_HIDE_FONT_SCALE
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        modifier = Modifier
            .clickable(enabled = !selected, onClick = onClick)
            .minTouchTarget()
            // Подпись есть в обычном масштабе, но на увеличенном она исчезает —
            // значок обязан остаться названным (НФТ-30).
            .semantics { contentDescription = label },
    ) {
        icon(tint)
        if (showLabel) {
            Text(
                text = label,
                style = if (selected) SklyankaType.MicroStrong else SklyankaType.Micro,
                color = tint,
            )
        }
    }
}

const val TAG_BOTTOM_BAR = "today_bottom_bar"
