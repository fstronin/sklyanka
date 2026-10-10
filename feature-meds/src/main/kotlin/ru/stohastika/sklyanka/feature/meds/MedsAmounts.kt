package ru.stohastika.sklyanka.feature.meds

import java.math.BigDecimal

/**
 * Число из текста числового поля: «0,5» → 0.5, пустое и неразбираемое → null.
 *
 * Запятая — десятичный разделитель русской раскладки, и «0,5» обязано означать половину,
 * а не отказ «введите число». Правило одно на форму и карточку: им разбирает поле ввода
 * ViewModel и им же берётся число для подписи единицы, иначе поле и подпись разошлись бы —
 * человек набрал «2», а слово сказало бы, что число не названо.
 */
internal fun amountOf(text: String): BigDecimal? =
    text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
