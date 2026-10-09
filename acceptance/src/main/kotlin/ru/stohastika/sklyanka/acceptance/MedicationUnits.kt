package ru.stohastika.sklyanka.acceptance

import ru.stohastika.sklyanka.domain.medication.StrengthUnit

/**
 * Единицы измерения в шагах приёмки: слово сценария ↔ код домена (Р-45).
 *
 * Сценарий называет единицу так, как её видит человек («мг», «МЕ»), а домен хранит код
 * (`mg`, `iu`). Соответствие живёт одним объектом на всю оснастку: разложенное по шагам,
 * оно расходится, и один шаг принимает «мкг» за количество дозы, а другой — за единицу.
 */
object MedicationUnits {

    /** Слова единиц дозировки из закрытого списка Р-45. */
    val STRENGTH_WORDS: Set<String> = setOf("мг", "мкг", "г", "мл", "МЕ", "%")

    /**
     * Код единицы дозировки по слову сценария.
     *
     * Незнакомое слово возвращается как есть: сценарии переноса называют чужую единицу,
     * пришедшую из файла (`МГ-6`), и подменять её своей оснастка не вправе.
     */
    fun strengthCode(word: String): String = when (word) {
        "мг" -> StrengthUnit.MILLIGRAM.code
        "мкг" -> StrengthUnit.MICROGRAM.code
        "г" -> StrengthUnit.GRAM.code
        "мл" -> StrengthUnit.MILLILITRE.code
        "МЕ" -> StrengthUnit.INTERNATIONAL_UNIT.code
        "%" -> StrengthUnit.PERCENT.code
        else -> word
    }

    /** Единица дозировки по слову сценария; незнакомое слово — ошибка сценария, а не данных. */
    fun strengthUnit(word: String): StrengthUnit = requireNotNull(StrengthUnit.ofCode(strengthCode(word))) {
        "the scenario names an unknown strength unit: «$word»"
    }
}
