package ru.heuri.sklyanka.ui.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Габариты компонентов из `docs/spec/08-design-system.md` §3.3 и §4.
 *
 * Зачем отдельный объект рядом с [SklyankaSpacing]. Отступ и габарит — разные вещи:
 * отступ берётся из девятишаговой шкалы, а высота кнопки, толщина рамки и размер
 * бегунка переключателя заданы каталогом компонентов поимённо и в шкалу не ложатся.
 * Заводить их «по месту» нельзя — литерал размера вне пакета токенов валит сборку
 * (НФТ-47), и это правильно: тогда высота основной кнопки разойдётся по пяти экранам.
 */
object SklyankaSizes {

    /**
     * Минимальная цель нажатия (§3.3, НФТ-29).
     *
     * Ровно 44 dp, а не 48 dp из Material: система задаёт 44, и расхождение в четыре
     * точки на плотной вёрстке макета заметно.
     */
    val MinTouchTarget: Dp = 44.dp

    /** Полоса профиля вверху экрана (§4.10). */
    val ProfileStripeHeight: Dp = 6.dp

    /** Плашка иконки лекарства в карточке приёма и её содержимое (§4.1). */
    val IconPlate: Dp = 40.dp
    val IconPlateGlyph: Dp = 20.dp

    /** Круглая кнопка отметки в карточке приёма (§4.1). */
    val MarkButton: Dp = 44.dp
    val MarkButtonGlyph: Dp = 18.dp

    /** Высота основной кнопки (§4.3: 52–54 dp). */
    val PrimaryButtonHeight: Dp = 52.dp

    /** Высота второстепенной кнопки и кнопки внутри карточки (§4.3: 44–52 dp). */
    val SecondaryButtonHeight: Dp = 44.dp

    /** Боковое поле подписи кнопки. */
    val ButtonPadding: Dp = 28.dp
    val QuietButtonPadding: Dp = 8.dp

    /** Высота чипа-кнопки — не меньше цели нажатия (§4.2). */
    val ChipHeight: Dp = 44.dp
    val ChipPadding: Dp = 14.dp

    /** Поле ввода (§4.4): обычное и компактное числовое. */
    val FieldHeight: Dp = 50.dp
    val CompactFieldHeight: Dp = 42.dp
    val FieldPadding: Dp = 14.dp

    /** Переключатель (§4.5): габарит 52×32, бегунок 26. */
    val SwitchWidth: Dp = 52.dp
    val SwitchHeight: Dp = 32.dp
    val SwitchThumb: Dp = 26.dp
    val SwitchPadding: Dp = 3.dp

    /**
     * Наименьшая ширина текстовой колонки в ряду «иконка — текст — действие».
     *
     * Ниже неё ряд перестраивается и действие уезжает под текст (§6.3). Порог задан
     * шириной, а не масштабом шрифта: порог по `fontScale` врёт при длинном названии
     * лекарства — оно переносит строку и на обычном масштабе.
     */
    val MinTextColumn: Dp = 96.dp

    /** Полоса прогресса (§4.7): высота 7–8 dp. */
    val ProgressHeight: Dp = 8.dp

    /** Знак в пустом состоянии, плоская редукция (§4.8). */
    val EmptyStateMark: Dp = 92.dp

    /** Баннер (§4.6): внутреннее поле 11–16 dp. */
    val BannerPaddingVertical: Dp = 11.dp
    val BannerPaddingHorizontal: Dp = 14.dp
    val BannerIcon: Dp = 17.dp

    /** Шапка экрана (§4.9): верхний отступ 26–30 dp. */
    val HeaderTopPadding: Dp = 30.dp
    val HeaderTopPaddingProfile: Dp = 24.dp
    val HeaderBottomPadding: Dp = 14.dp

    /** Штрих и разрыв пунктирной границы блока «по потребности» (§4.1). */
    val DashOn: Dp = 6.dp
    val DashOff: Dp = 5.dp

    /** Обычная граница и граница элемента, требующего действия (§4.1, §4.2). */
    val BorderThin: Dp = 1.dp
    val BorderAccent: Dp = 1.5.dp

    /** Разделитель между блоками. */
    val DividerThickness: Dp = 1.dp

    /**
     * Потолок масштаба системного шрифта для [SklyankaType.Display] (§6.3).
     *
     * Применяется только к 64 sp на полноэкранном напоминании, где время физически
     * не помещается на 390 dp. Остальная типографика масштабируется свободно:
     * ограничение масштаба — это отъём у человека того, что система ему дала.
     */
    const val DISPLAY_MAX_FONT_SCALE: Float = 1.3f

    /**
     * Масштаб, выше которого подпись вкладки нижней панели скрывается (§6.3, НФТ-30).
     *
     * Иконка с `contentDescription` остаётся: скрывается дубль, а не смысл.
     */
    const val LABEL_HIDE_FONT_SCALE: Float = 1.3f
}
