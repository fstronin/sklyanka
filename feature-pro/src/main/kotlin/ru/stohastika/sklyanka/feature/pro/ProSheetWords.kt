package ru.stohastika.sklyanka.feature.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import ru.stohastika.sklyanka.domain.pro.ProNotice
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProSheetState
import ru.stohastika.sklyanka.domain.pro.StorePrice
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.ChevronGlyph
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.text.proText
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Нижний блок шторки витрины и тихие строки (SKL-122).
 *
 * Отдельный файл, а не продолжение [ProSheet]: слов магазина до покупки и действий столько же,
 * сколько у самой поверхности, и вместе они перебирают меру читаемости файла
 * (`TooManyFunctions`). Разделение по смыслу, а не по строкам: здесь всё, что человек читает
 * перед нажатием и чем он уходит наружу.
 */

/**
 * Нижний неподвижный блок: слова магазина до покупки и главное действие (Р-73 п. 2, Р-76 п. 3).
 *
 * Согласие даётся самим нажатием «Оформить», отдельной галочки нет, и слова не должны
 * отделяться от кнопки прокруткой — поэтому блок не прокручивается.
 */
@Composable
internal fun BottomBlock(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = SklyankaSpacing.Medium,
                bottom = SklyankaSpacing.Block,
            ),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
    ) {
        BottomWords(state, onIntent)
        state.termsRow?.let { terms ->
            // Документ, а не шаг покупки: «Условия и политика» открывают текст, а не магазин (§5.9).
            ProQuietRow(
                text = proText(terms),
                tag = TAG_PRO_TERMS,
                onClick = { onIntent(ProShowcaseIntent.OpenPolicy) },
                accent = false,
            )
        }
        MainAction(state, onIntent)
    }
}

/**
 * Слова магазина: причина словами, раскрытие до покупки и «Подробнее» (Р-68 п. 6, Р-76 п. 3).
 *
 * Отказ магазина и недоступность называются здесь, у самой кнопки, и отделить их от неё
 * прокруткой нельзя: блок не прокручивается.
 */
@Composable
private fun BottomWords(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    state.notice?.let { notice ->
        Text(
            text = proText(notice),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
            modifier = Modifier.testTag(TAG_PRO_NOTICE),
        )
    }
    val disclosurePrice = withPeriod(state.disclosurePrice, state.selected)
    state.disclosureRows.forEach { row ->
        Text(
            text = if (row == ProNotice.RENEW_PRICE || row == ProNotice.RENEW_TERMS_SHORT) {
                proText(row, disclosurePrice)
            } else {
                proText(row)
            },
            style = SklyankaType.Caption,
            color = colors.textMuted,
        )
    }
    state.disclosureMore?.let { more ->
        QuietButton(
            text = proText(more),
            onClick = { onIntent(ProShowcaseIntent.ExpandDisclosure) },
            // Слово встаёт на боковое поле шторки 20 dp: смещение гасит собственное поле
            // тихой кнопки — иначе тихие строки одной поверхности стоят на разных полях (§5.9).
            modifier = Modifier
                .offset(x = -SklyankaSizes.QuietButtonPadding)
                .testTag(TAG_PRO_MORE),
        )
    }
}

/** Главное действие состояния: «Оформить» на витрине, «Управление подпиской» в «активна». */
@Composable
private fun MainAction(state: ProSheetState, onIntent: (ProShowcaseIntent) -> Unit) {
    if (state.management) {
        ProQuietRow(
            text = proText(ProNotice.ACTION_CANCEL),
            tag = TAG_PRO_CANCEL,
            onClick = { onIntent(ProShowcaseIntent.Manage) },
            accent = true,
        )
        PrimaryButton(
            text = proText(ProNotice.ACTION_MANAGE),
            onClick = { onIntent(ProShowcaseIntent.Manage) },
            state = if (state.busy) ButtonState.Busy else ButtonState.Normal,
            modifier = Modifier.fillMaxWidth().testTag(TAG_PRO_MANAGE),
        )
    } else {
        PrimaryButton(
            // Кнопка одна на оба товара: период выбирает человек, а в магазин уходит
            // идентификатор выбранного (Р-68 п. 1, Р-75 п. 1).
            text = proText(ProNotice.ACTION_BUY),
            onClick = { onIntent(ProShowcaseIntent.Buy) },
            state = when {
                state.buyBusy -> ButtonState.Busy
                state.buyEnabled -> ButtonState.Normal
                else -> ButtonState.Disabled
            },
            modifier = Modifier.fillMaxWidth().testTag(TAG_PRO_BUY),
        )
    }
}

/**
 * «Отменить подписку» и «Условия и политика» — тихие строки, ведущие наружу.
 *
 * Шеврон значит «уйдёшь наружу» (Р-75 п. 2): у действий внутри шторки его нет, и без этого
 * различия строка с ним и строка без него выглядели бы одинаково, обещая разное.
 *
 * Слово набрано `LabelStrong` видом тихой кнопки, но кнопкой не является: цель нажатия
 * у строки одна — сама строка. Вложенная кнопка в нажимаемой строке далала бы два узла
 * на одно действие, и чтение с экрана объявляло бы строку дважды.
 */
@Composable
private fun ProQuietRow(text: String, tag: String, onClick: () -> Unit, accent: Boolean) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minTouchTarget()
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Text(
            text = text,
            style = SklyankaType.LabelStrong,
            color = if (accent) colors.accent else colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        ChevronGlyph(color = colors.iconFaint, size = SklyankaSizes.Chevron)
    }
}

/**
 * «Цена / период» — строка из двух готовых частей: цены магазина и короткой формы периода.
 *
 * Разделитель здесь пунктуация, а не текст: числа в строке нет вовсе, и второй ресурс
 * с ценой не заводится (Р-68 п. 3). Пустая цена сюда не приходит: строки с ценой в раскрытии
 * появляются только вместе с ней (`ProDisclosure.of`), а пустая строка — страховка на случай
 * будущей правки, а не рабочее состояние.
 */
@Composable
private fun withPeriod(price: StorePrice?, period: ProProductId?): String =
    if (price != null && period != null) "${price.text} / ${proText(periodShortName(period))}" else ""
