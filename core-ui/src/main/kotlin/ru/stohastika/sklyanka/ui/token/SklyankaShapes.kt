package ru.stohastika.sklyanka.ui.token

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Радиусы. Семь ступеней из `docs/spec/08-design-system.md` §3.1.
 *
 * В макетах четырнадцать разных радиусов; ступени, отличающиеся на пиксель, системой
 * не являются и сведены к ближайшей. Значение ступени и её форма живут рядом, чтобы
 * компонент не собирал `RoundedCornerShape` из числа по месту.
 */
object SklyankaShapes {

    /** Клетка календаря месяца. */
    val SmallRadius: Dp = 9.dp

    /** Малая кнопка высотой 36–38 dp, вкладка внутри сегмента, малое поле. */
    val CompactRadius: Dp = 12.dp

    /** Кнопка в карточке приёма, чип формы выпуска, плашка иконки 40×40. */
    val ButtonRadius: Dp = 13.dp

    /** Поле ввода, строка времени, баннер. */
    val MediumRadius: Dp = 14.dp

    /** Основная кнопка экрана, карточка-группа настроек, плитка показателя. */
    val LargeRadius: Dp = 16.dp

    /** Карточка приёма, карточка лекарства, карточка раздела. */
    val CardRadius: Dp = 18.dp

    /** Чип, переключатель, аватар, полоса прогресса, круглая кнопка отметки. */
    val PillRadius: Dp = 999.dp

    val Small: RoundedCornerShape = RoundedCornerShape(SmallRadius)
    val Compact: RoundedCornerShape = RoundedCornerShape(CompactRadius)
    val Button: RoundedCornerShape = RoundedCornerShape(ButtonRadius)
    val Medium: RoundedCornerShape = RoundedCornerShape(MediumRadius)
    val Large: RoundedCornerShape = RoundedCornerShape(LargeRadius)
    val Card: RoundedCornerShape = RoundedCornerShape(CardRadius)
    val Pill: RoundedCornerShape = RoundedCornerShape(PillRadius)

    /** Все ступени — опора теста шкалы. */
    val allRadii: List<Dp>
        get() = listOf(
            SmallRadius,
            CompactRadius,
            ButtonRadius,
            MediumRadius,
            LargeRadius,
            CardRadius,
            PillRadius,
        )
}
