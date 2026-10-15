package ru.stohastika.sklyanka.ui.text

import android.content.res.Resources
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.ui.R
import java.math.BigDecimal

/**
 * Доза приёма и дозировка препарата словами: «2 таблетки», «1 таблетка · 50 мкг»
 * (`docs/norm/06-design.md` §5.11).
 *
 * Формат поднят в дизайн-систему, а не оставлен на экране «Сегодня», потому что ту же
 * подпись несёт строка приёма в Glance-виджете. Вторая редакция разошлась бы с первой
 * на первой же правке формулировки и назвала бы ту же дозу иначе в ленте и в виджете.
 *
 * Зависимости от Compose-контекста нет намеренно: виджет собирает разметку без
 * приложения-хоста, и `@Composable`-ресурсы ему недоступны. Единственный вход — [Resources].
 */
object DoseText {

    /**
     * «2 таблетки», «1 таблетка · 50 мкг» — доза приёма и, если есть, дозировка препарата.
     *
     * [strength] и [strengthUnit] необязательны: у лекарства без дозировки доза остаётся
     * одна, и висящего разделителя не появляется. Дозировка печатается, только когда задано
     * её число; единица без числа смысла не несёт.
     */
    fun line(
        resources: Resources,
        amount: BigDecimal,
        unit: DoseUnit,
        strength: BigDecimal? = null,
        strengthUnit: String? = null,
    ): String {
        val separator = resources.getString(R.string.dose_separator)
        val parts = buildList {
            add(dose(resources, amount, unit))
            strength?.let { value -> add(strengthText(resources, value, strengthUnit)) }
        }
        return parts.joinToString(separator)
    }

    /**
     * Дозировка с единицей словом закрытого списка (Р-45); незнакомый код — как есть.
     *
     * Код приходит из записи: своя форма пишет код закрытого списка, чужая единица — из файла
     * переноса данных или из более старой версии, и подменять её своей нельзя (МГ-6).
     */
    fun strength(resources: Resources, value: BigDecimal, unitCode: String?): String =
        strengthText(resources, value, unitCode)

    /** «1 таблетка», «2 таблетки», «0,5 таблетки» — форму слова даёт [pluralQuantityOf]. */
    private fun dose(resources: Resources, amount: BigDecimal, unit: DoseUnit): String =
        resources.getQuantityString(unit.pluralRes(), pluralQuantityOf(amount), amount.plain())

    /** «50 мкг»: число и слово единицы; число — без хвостовых нулей («1», а не «1.00»). */
    private fun strengthText(resources: Resources, value: BigDecimal, unitCode: String?): String =
        resources.getString(R.string.dose_strength, value.plain(), unitWord(resources, unitCode))

    /** Слово единицы дозировки или сам код, если он не из закрытого списка; `null` — пусто. */
    private fun unitWord(resources: Resources, code: String?): String {
        if (code == null) return ""
        return StrengthUnit.ofCode(code)?.let { resources.getString(it.labelRes()) } ?: code
    }

    /** Слово единицы дозировки из закрытого списка (Р-45). */
    private fun StrengthUnit.labelRes(): Int = when (this) {
        StrengthUnit.MILLIGRAM -> R.string.strength_unit_milligram
        StrengthUnit.MICROGRAM -> R.string.strength_unit_microgram
        StrengthUnit.GRAM -> R.string.strength_unit_gram
        StrengthUnit.MILLILITRE -> R.string.strength_unit_millilitre
        StrengthUnit.INTERNATIONAL_UNIT -> R.string.strength_unit_international_unit
        StrengthUnit.PERCENT -> R.string.strength_unit_percent
    }

    /** Семейство форм дозы для единицы: своя пара `plurals` на каждую (§1.1, Р-45). */
    private fun DoseUnit.pluralRes(): Int = when (this) {
        DoseUnit.TABLET -> R.plurals.dose_tablets
        DoseUnit.CAPSULE -> R.plurals.dose_capsules
        DoseUnit.MILLILITRE -> R.plurals.dose_millilitres
        DoseUnit.DROP -> R.plurals.dose_drops
        DoseUnit.SPRAY -> R.plurals.dose_sprays
        DoseUnit.INJECTION -> R.plurals.dose_injections
    }

    /** «1», а не «1.00»: доза введена человеком и показывается так, как он её ввёл. */
    private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()
}
