// Вкладка — перечисление рядом с панелью, а не отдельный файл: имя файла даёт компонент,
// а объяснение, почему тип живёт здесь, — в его KDoc ниже.
@file:Suppress("MatchingDeclarationName")

package ru.stohastika.sklyanka.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaMotion
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.pressedTint

/**
 * Три вкладки нижней панели (§4.13, ОВ-91).
 *
 * Вкладка — не состояние компонента каталога, а его навигационная идентичность:
 * у панели нет четырёх состояний «выбрана/не выбрана × вкладка». Поэтому перечисление
 * живёт рядом с панелью, как и написано в `SKL-71/TDD.md §4`, а не в словаре состояний
 * `ComponentStates.kt`, по которому SKL-24 требует эталонный снимок на каждое значение.
 * Вид выбранной вкладки закреплён снимками корневых экранов (группа `screen` в `feature-` модулях).
 */
enum class BottomBarTab { Today, Meds, Settings }

/**
 * Нижняя панель на три вкладки — общий компонент дизайн-системы (§4.13, ОВ-91).
 *
 * Живёт в `core-ui`, а не на каждом экране: панель нужна корням всех трёх вкладок,
 * и вторая её копия разошлась бы с первой на первой же правке. Панель не знает ни
 * о маршрутах, ни о намерениях экранов — соответствие вкладки и действия задаёт тот,
 * кто её собирает.
 *
 * Фон рисуется до нижнего паддинга, поэтому поверхность панели продолжается под
 * системной навигационной панелью, а ряд вкладок стоит выше неё (SKL-71 §4, SKL-72).
 * Собственное нижнее поле — не меньше экранного отступа, чтобы на конфигурациях без
 * системной навигации панель не липла к краю.
 *
 * Подпись вкладки скрывается на большом системном масштабе шрифта, а значок остаётся
 * с подписью для чтения с экрана: скрывается дубль, а не смысл (§6.3, НФТ-30).
 */
@Composable
fun BottomBar(selected: BottomBarTab, onSelect: (BottomBarTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .testTag(TAG_BOTTOM_BAR),
    ) {
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
                    start = SklyankaSpacing.Screen,
                    end = SklyankaSpacing.Screen,
                    top = SklyankaSpacing.Default,
                    bottom = maxOf(bottomInset, SklyankaSpacing.Screen),
                ),
        ) {
            Tab(
                label = stringResource(R.string.tab_today),
                selected = selected == BottomBarTab.Today,
                onClick = { onSelect(BottomBarTab.Today) },
                icon = { color -> CalendarGlyph(color) },
                modifier = Modifier.weight(1f),
            )
            Tab(
                label = stringResource(R.string.tab_meds),
                selected = selected == BottomBarTab.Meds,
                onClick = { onSelect(BottomBarTab.Meds) },
                icon = { color -> MedicationPictogram(color) },
                modifier = Modifier.weight(1f),
            )
            Tab(
                label = stringResource(R.string.tab_settings),
                selected = selected == BottomBarTab.Settings,
                onClick = { onSelect(BottomBarTab.Settings) },
                icon = { color -> GearGlyph(color) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Невыбранная вкладка — TextMuted, а не TextTertiary: третий уровень текста
    // не проходит AA на поверхности (3,56–3,80, §6.1 вывод 1).
    val base = if (selected) colors.accent else colors.textMuted
    val showPressed = pressed && !selected
    val animation = tabTint(base = base, pressed = showPressed)
    val tint by animateColorAsState(
        targetValue = animation.target,
        animationSpec = animation.spec,
        label = "tabTint",
    )
    val showLabel = LocalDensity.current.fontScale <= SklyankaSizes.LABEL_HIDE_FONT_SCALE
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !selected,
                onClick = onClick,
            )
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

internal data class TabTint(val target: Color, val spec: AnimationSpec<Color>)

internal fun tabTint(base: Color, pressed: Boolean): TabTint = TabTint(
    target = if (pressed) pressedTint(base) else base,
    spec = if (pressed) {
        tween(SklyankaMotion.DurationFast, easing = SklyankaMotion.EasingEnter)
    } else {
        tween(SklyankaMotion.DurationFast, easing = SklyankaMotion.EasingExit)
    },
)

const val TAG_BOTTOM_BAR = "bottom_bar"
