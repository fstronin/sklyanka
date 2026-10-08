package ru.stohastika.sklyanka.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.ui.component.StackingRow
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Рубрика раздела прописными (`Settings.dc.html`).
 *
 * Прописные задаёт токен типографики, а не строка в `strings.xml`: текст в ресурсах
 * записан так, как его читает человек, а начертание — свойство оформления.
 */
@Composable
internal fun SectionRubric(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(interfaceLocale()),
        style = SklyankaType.Overline,
        color = SklyankaTheme.colors.textMuted,
        modifier = modifier.padding(start = SklyankaSpacing.Micro),
    )
}

/** Карточка раздела: белая поверхность с тонкой рамкой, строки внутри без своих полей. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card),
        content = content,
    )
}

/** Разделитель между строками карточки: с боковыми отступами, как в макете. */
@Composable
internal fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.lineOnSurface),
    )
}

/**
 * Строка настроек: заголовок, подпись состояния, шеврон.
 *
 * Нажимается вся строка и цель нажатия доведена до 44 dp (НФТ-29): в списке из десяти
 * строк попадание пальцем в текст — не гарантия.
 *
 * Подпись приходит вместе с цветом, а не вычисляется здесь: «Одно не выдано» акцентным
 * и «GT 6 Pro · подключены» цветом «принято» — это утверждения о состоянии, и решать,
 * какое из них акцентное, обязан тот, кто состояние прочитал.
 */
@Composable
@Suppress("LongParameterList")
internal fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: RowSubtitle? = null,
    /**
     * Текущее значение строки перед шевроном («Отложить на» → «15 минут»).
     *
     * Необязательно: у строки-перехода значения нет вовсе («Звук» ведёт в системные
     * настройки, где живёт выбор звука), и показывать там нечего (ОВ-98).
     */
    value: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minTouchTarget()
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Large),
    ) {
        StackingRow(
            leading = { leading?.invoke() },
            text = { RowText(title, subtitle) },
            action = { RowAction(value, trailing, colors.iconFaint) },
            gap = SklyankaSpacing.Medium,
            minTextWidth = SklyankaSizes.MinTextColumn,
        )
    }
}

/**
 * Слот действия строки: своя графика, либо значение с шевроном, либо только шеврон.
 *
 * Значение набрано приглушённо и стоит перед шевроном: это утверждение о сохранённом
 * состоянии, а не подпись кнопки (ОВ-98).
 */
@Composable
private fun RowAction(value: String?, trailing: (@Composable () -> Unit)?, chevronColor: Color) {
    when {
        trailing != null -> trailing()
        value != null -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            Text(text = value, style = SklyankaType.Label, color = SklyankaTheme.colors.textMuted)
            ChevronGlyph(chevronColor)
        }

        else -> ChevronGlyph(chevronColor)
    }
}

/**
 * Подпись состояния под заголовком строки вместе с её цветом.
 *
 * Текст и цвет одним значением: цвет подписи здесь — не оформление, а утверждение
 * («не выдано» акцентом, «подключены» цветом «принято»), и разойтись с текстом он
 * не должен.
 */
internal data class RowSubtitle(val text: String, val color: Color? = null)

/** Текстовая колонка строки: заголовок и подпись состояния под ним. */
@Composable
private fun RowText(title: String, subtitle: RowSubtitle?) {
    val colors = SklyankaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
        Text(text = title, style = SklyankaType.BodyDefault, color = colors.text)
        if (subtitle != null) {
            Text(
                text = subtitle.text,
                style = SklyankaType.CaptionSmall,
                color = subtitle.color ?: colors.textMuted,
            )
        }
    }
}

/**
 * Кружок с инициалом профиля.
 *
 * Цвет — цвет профиля у чужого и мягкий «принято» у владельца, ровно как у аватара
 * в шапке: своему профилю индикация «чужого» не добавляется (ПРФ-16), но узнаваемым
 * он остаётся.
 */
@Composable
internal fun ProfileInitial(initial: String, color: Color, onColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(SklyankaSizes.IconPlate).background(color, SklyankaShapes.Pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = SklyankaType.LabelStrong,
            color = onColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Строка, которая только показывает значение.
 *
 * Без шеврона и без нажатия намеренно: обещание перехода, за которым перехода нет, хуже
 * отсутствия обещания. Экран выбора звука в первую версию не входит
 * (`docs/v2/00-product.md` §2), а набор значений откладывания задаёт SKL-13 — здесь,
 * по `task.md` («только их место на экране и отображение текущего значения»), стоит
 * текущее значение и ничего больше.
 */
@Composable
internal fun SettingsValueRow(title: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .minTouchTarget()
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Large),
    ) {
        StackingRow(
            leading = {},
            text = { RowText(title, caption?.let(::RowSubtitle)) },
            action = { Text(text = value, style = SklyankaType.Label, color = colors.textMuted) },
            gap = SklyankaSpacing.Medium,
            minTextWidth = SklyankaSizes.MinTextColumn,
        )
    }
}

/** Метки строк — точки опоры экранных проверок. */
const val TAG_SETTINGS_SCREEN = "settings_screen"
internal const val TAG_PROFILE_ROW = "settings_profile_row"
internal const val TAG_ACTIVE_PROFILE = "settings_profile_active"
internal const val TAG_PERMISSIONS_ROW = "settings_permissions_row"
internal const val TAG_ABOUT = "settings_about"
internal const val TAG_ABOUT_SITE = "settings_about_site"
internal const val TAG_SOUND_ROW = "settings_sound_row"

// Публичные метки (SKL-97): на них опираются экранные проверки устройства в модуле app,
// который не видит `internal` чужих модулей.
const val TAG_SNOOZE_ROW = "settings_snooze_row"
const val TAG_SNOOZE_DIALOG = "settings_snooze_dialog"
