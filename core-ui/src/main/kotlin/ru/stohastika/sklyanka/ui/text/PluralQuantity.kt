package ru.stohastika.sklyanka.ui.text

import java.math.BigDecimal

/**
 * Количество, которым выбирается форма слова для числа (`docs/norm/06-design.md` §5.3).
 *
 * Целому числу отвечает само число: платформа выбирает форму `plurals` по нему —
 * «1 таблетка», «2 таблетки», «5 таблеток», «0 таблеток», «21 таблетка».
 *
 * Дробному числу отвечает «2». Платформа выбирает форму `plurals` только по ЦЕЛОМУ числу
 * (`Resources.getQuantityString`), и форму дроби — `other` в русском — через это выбрать
 * нельзя. «2» несёт ту же форму, что и дробь: в русском — родительный единственного
 * («0,5 таблетки», он же `other`), в остальных каталогах — множественное («0.5 tablets»,
 * тоже `other`). Отсюда условие нормы: у дозовых семейств `few` и `other` несут одно слово,
 * а `other` присутствует (неполный каталог стережёт приёмка SKL-62).
 *
 * Ноль остаётся нулём: «0 таблеток» — множественное родительное, а не дробь.
 */
fun pluralQuantityOf(amount: BigDecimal): Int =
    if (amount.remainder(BigDecimal.ONE).signum() == 0) amount.toInt() else FRACTION_QUANTITY

/** Количество, которым печатается форма дроби: «2» — см. [pluralQuantityOf]. */
private const val FRACTION_QUANTITY = 2
