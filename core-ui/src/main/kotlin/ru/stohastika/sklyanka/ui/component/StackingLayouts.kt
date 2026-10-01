package ru.stohastika.sklyanka.ui.component

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
 *
 * Раскладка видна за пределами модуля (SKL-20). До экрана настроек она была внутренней:
 * ею пользовались карточка приёма и баннер, оба из `core-ui`. Строка настроек с подписью
 * слева и значением справа — ровно тот же ряд, и второй его экземпляр, написанный по
 * месту, разошёлся бы с этим на первой же правке. Правило §6.3 — свойство дизайн-системы,
 * а не свойство карточки приёма.
 */
@Composable
fun StackingRow(
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
 * Пара равных кнопок, встающая в столбец, когда подписи перестают помещаться
 * в равные половины (§6.3).
 *
 * Во встроенном виде доли равны — как `weight(1f)` в макете напоминания
 * (`design/screens/Reminder.dc.html`, `flex-grow: 1`). Как только полной подписи
 * кнопки перестаёт хватать половины строки, кнопки встают друг под друга во всю
 * ширину: подпись не переносится и не рвётся.
 *
 * Порог считается по полной ширине подписи, а не по самому длинному слову: подпись,
 * разложенная на две строки, человеком читается как поломка кнопки — ровно это
 * и увидел пилот («Отложить 15 мин» на двух строках, SKL-61).
 */
@Composable
fun StackingEqualButtons(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(first, second),
        modifier = modifier.fillMaxWidth(),
    ) { (firstMeasurables, secondMeasurables), constraints ->
        val gapPx = gap.roundToPx()
        val firstMeasurable = firstMeasurables.first()
        val secondMeasurable = secondMeasurables.first()
        // Полная ширина подписи без измерения: `measure()` у Measurable вызывается
        // ровно один раз — при окончательной укладке ниже.
        val firstFull = firstMeasurable.maxIntrinsicWidth(0)
        val secondFull = secondMeasurable.maxIntrinsicWidth(0)
        val available = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            firstFull + gapPx + secondFull
        }
        val stacked = maxOf(firstFull, secondFull) * 2 + gapPx > available
        val share = ((available - gapPx) / 2).coerceAtLeast(0)

        val firstPlaceable: Placeable
        val secondPlaceable: Placeable
        if (stacked) {
            firstPlaceable = firstMeasurable.measure(Constraints.fixedWidth(available))
            secondPlaceable = secondMeasurable.measure(Constraints.fixedWidth(available))
        } else {
            firstPlaceable = firstMeasurable.measure(Constraints.fixedWidth(share))
            secondPlaceable = secondMeasurable.measure(Constraints.fixedWidth(share))
        }

        val height = if (stacked) {
            firstPlaceable.height + gapPx + secondPlaceable.height
        } else {
            maxOf(firstPlaceable.height, secondPlaceable.height)
        }
        layout(available, height) {
            firstPlaceable.place(0, 0)
            if (stacked) {
                secondPlaceable.place(0, firstPlaceable.height + gapPx)
            } else {
                secondPlaceable.place(firstPlaceable.width + gapPx, 0)
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
