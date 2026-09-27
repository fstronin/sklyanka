package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Две раскладки, живущие по одному правилу: **строку нельзя сжимать уже её самого
 * длинного слова**.
 *
 * Правило — исполняемая часть НФТ-31 («при масштабе шрифта до 200 % ни один текст не
 * обрезается и не перекрывается») и §6.3 дизайн-системы («ряд при нехватке ширины
 * переносит действие под текст»). Сжатая ниже своего слова колонка не переносит
 * строку, а разрывает слово: «Принято» превращается в «При/нят/о», а «заканчивается»
 * — в «заканчивае/тся». Человек с крупным системным шрифтом читает именно это.
 *
 * Порог берётся измерением, а не порогом по `fontScale` (`TDD.md SKL-05 §6.3`): порог
 * по масштабу врёт при длинном названии лекарства — оно перестаёт помещаться и на
 * обычном масштабе. Минимальная внутренняя ширина (`minIntrinsicWidth`) у текста —
 * это ровно ширина его самого длинного слова, то есть та граница, за которой перенос
 * становится разрывом.
 */

/**
 * Ряд «значок — текст — действие», переносящий действие под текст.
 *
 * Значок и действие необязательны: отсутствующий слот не занимает ни ширины, ни
 * зазора. Колонка текста не опускается ниже [minTextWidth] и ниже своего самого
 * длинного слова; когда столько места нет, действие уезжает под текст, а не отбирает
 * у него ширину.
 */
@Composable
internal fun StackingRow(
    leading: @Composable () -> Unit,
    text: @Composable () -> Unit,
    action: @Composable () -> Unit,
    gap: Dp,
    minTextWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(leading, text, action),
        modifier = modifier.fillMaxWidth(),
    ) { (leadingMeasurables, textMeasurables, actionMeasurables), constraints ->
        val gapPx = gap.roundToPx()
        val loose = Constraints()
        val lead = leadingMeasurables.firstOrNull()?.measure(loose)
        val act = actionMeasurables.firstOrNull()?.measure(loose)
        val leadWidth = lead.widthOrZero()
        val actionWidth = act.widthOrZero()
        val leadGap = if (leadWidth == 0) 0 else gapPx
        val actionGap = if (actionWidth == 0) 0 else gapPx
        val textMeasurable = textMeasurables.first()
        val floor = maxOf(minTextWidth.roundToPx(), textMeasurable.longestWord())

        // Ширина может прийти неограниченной (горизонтальная прокрутка): тогда ряд
        // занимает нужное содержимому, а не Constraints.Infinity точек.
        val available = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            leadWidth + leadGap + floor + actionGap + actionWidth
        }
        val inlineTextWidth = available - leadWidth - leadGap - actionGap - actionWidth
        val stacked = inlineTextWidth < floor && actionWidth > 0
        val textWidth = if (stacked) available - leadWidth - leadGap else inlineTextWidth
        val textPlaceable = textMeasurable.measure(Constraints(maxWidth = textWidth.coerceAtLeast(0)))

        val rowHeight = maxOf(
            lead.heightOrZero(),
            textPlaceable.height,
            if (stacked) 0 else act.heightOrZero(),
        )
        val height = if (stacked) rowHeight + gapPx + act.heightOrZero() else rowHeight

        layout(available, height) {
            lead?.place(0, centered(rowHeight, lead.height))
            textPlaceable.place(leadWidth + leadGap, centered(rowHeight, textPlaceable.height))
            if (stacked) {
                act?.place(leadWidth + leadGap, rowHeight + gapPx)
            } else {
                act?.place(available - act.width, centered(rowHeight, act.height))
            }
        }
    }
}

/**
 * Пара кнопок, встающая в столбец, когда подписи не помещаются рядом.
 *
 * Пока обе подписи помещаются в строку, раскладка та же, что у обычного ряда:
 * второстепенная кнопка занимает нужное подписи, основная — остаток. Как только
 * остатка перестаёт хватать на самое длинное слово подписи, кнопки встают друг под
 * друга во всю ширину: подпись главного действия экрана не рвётся и не обрезается
 * (§6.3, «кнопка „Принято“ не сжимается»).
 */
@Composable
internal fun StackingButtons(
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(primary, secondary),
        modifier = modifier.fillMaxWidth(),
    ) { (primaryMeasurables, secondaryMeasurables), constraints ->
        val gapPx = gap.roundToPx()
        val primaryMeasurable = primaryMeasurables.first()
        val secondaryMeasurable = secondaryMeasurables.first()
        val primaryFloor = primaryMeasurable.longestWord()
        val secondaryFloor = secondaryMeasurable.longestWord()
        val available = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            primaryFloor + gapPx + secondaryFloor
        }
        val stacked = primaryFloor + gapPx + secondaryFloor > available

        val primaryPlaceable: Placeable
        val secondaryPlaceable: Placeable
        if (stacked) {
            primaryPlaceable = primaryMeasurable.measure(Constraints.fixedWidth(available))
            secondaryPlaceable = secondaryMeasurable.measure(Constraints.fixedWidth(available))
        } else {
            // Второстепенная кнопка берёт нужное подписи, но не отбирает у основной
            // её собственное слово; основная занимает остаток — как `weight(1f)`.
            secondaryPlaceable = secondaryMeasurable
                .measure(Constraints(maxWidth = (available - gapPx - primaryFloor).coerceAtLeast(0)))
            primaryPlaceable = primaryMeasurable
                .measure(Constraints.fixedWidth((available - gapPx - secondaryPlaceable.width).coerceAtLeast(0)))
        }

        val height = if (stacked) {
            primaryPlaceable.height + gapPx + secondaryPlaceable.height
        } else {
            maxOf(primaryPlaceable.height, secondaryPlaceable.height)
        }
        layout(available, height) {
            primaryPlaceable.place(0, 0)
            if (stacked) {
                secondaryPlaceable.place(0, primaryPlaceable.height + gapPx)
            } else {
                secondaryPlaceable.place(available - secondaryPlaceable.width, 0)
            }
        }
    }
}

/**
 * Ширина самого длинного слова содержимого.
 *
 * У текста минимальная внутренняя ширина — это ширина, ниже которой строка перестаёт
 * переноситься по пробелам и начинает рваться посреди слова. Высота на неё не влияет,
 * поэтому передаётся ноль.
 */
private fun Measurable.longestWord(): Int = minIntrinsicWidth(0)

/**
 * Отступ сверху при выравнивании по центру ряда.
 *
 * Округление, а не деление нацело: так же считает `Alignment.CenterVertically`, и
 * ряд, собранный этой раскладкой, встаёт туда же, куда вставал обычный `Row`.
 */
private fun centered(space: Int, size: Int): Int = (space - size + 1) / 2

private fun Placeable?.widthOrZero(): Int = this?.width ?: 0

private fun Placeable?.heightOrZero(): Int = this?.height ?: 0
