package ru.heuri.sklyanka.feature.settings

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
 * Нижняя панель экрана настроек: те же три вкладки, активная — «Настройки»
 * (`Settings.dc.html`).
 *
 * Панель повторена, а не разделена с экраном «Сегодня», по правилу направления
 * зависимостей: `feature-*` друг от друга не зависят, а компонентом дизайн-системы панель
 * не является — в каталоге компонентов её нет. Поднимать её в `core-ui` только ради второго
 * экрана значит объявить компонентом системы то, что ею не объявлено
 * (`docs/spec/08-design-system.md` §8).
 *
 * Подпись вкладки скрывается на большом системном масштабе шрифта, значок остаётся
 * с подписью для чтения с экрана: скрывается дубль, а не смысл (§6.3, НФТ-30, НФТ-31).
 */
@Composable
internal fun SettingsBottomBar(onIntent: (SettingsIntent) -> Unit, modifier: Modifier = Modifier) {
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
                .padding(horizontal = SklyankaSpacing.Screen, vertical = SklyankaSpacing.Default),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab(
                label = stringResource(R.string.settings_tab_today),
                selected = false,
                onClick = { onIntent(SettingsIntent.OpenToday) },
                icon = { color -> CalendarGlyph(color) },
            )
            Tab(
                label = stringResource(R.string.settings_tab_meds),
                selected = false,
                onClick = { onIntent(SettingsIntent.OpenMedications) },
                icon = { color -> MedicationGlyph(color) },
            )
            Tab(
                label = stringResource(R.string.settings_tab_settings),
                selected = true,
                onClick = {},
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
