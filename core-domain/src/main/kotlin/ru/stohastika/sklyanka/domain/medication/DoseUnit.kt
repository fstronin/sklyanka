package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.MedicationForm

/**
 * Единица дозы — производная от формы выпуска (`docs/norm/04-behavior.md` §1.1, решение Р-45).
 *
 * Человеку единица не даётся на редактирование: её называет форма выпуска, и другого ответа
 * на вопрос «чем меряют дозу» у приложения нет. Значение «доза» (`dose`) убрано: оно было
 * не единицей измерения, а заглушкой — под ним сходились впрыск из флакона и укол ампулой,
 * а капли числились миллилитрами наравне с сиропом.
 *
 * Латинские коды, а не русские слова: в домене русского текста нет вовсе (НФТ-42),
 * склонение и перевод — дело ресурсов экрана. Хранится в `Medication.doseUnit` именно
 * [code], а не порядковый номер: номер ломается при вставке значения в середину списка
 * и нечитаем в файле переноса данных (МГ-6).
 */
enum class DoseUnit(val code: String) {
    TABLET("tablet"),
    CAPSULE("capsule"),
    MILLILITRE("millilitre"),
    DROP("drop"),
    SPRAY("spray"),
    INJECTION("injection"),
    ;

    companion object {
        fun ofCode(code: String): DoseUnit? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Единица дозы по форме выпуска.
 *
 * Таблица «форма → единица» — единственное место, где эта связь записана (§1.1). Сироп
 * меряют миллилитрами, капли — каплями; раньше они сходились в одну единицу, и остаток
 * «60 мл» у капель называл не то, что стоит в упаковке.
 */
fun MedicationForm.doseUnit(): DoseUnit = when (this) {
    MedicationForm.TABLET -> DoseUnit.TABLET
    MedicationForm.CAPSULE -> DoseUnit.CAPSULE
    MedicationForm.SYRUP -> DoseUnit.MILLILITRE
    MedicationForm.DROPS -> DoseUnit.DROP
    MedicationForm.SPRAY -> DoseUnit.SPRAY
    MedicationForm.INJECTION -> DoseUnit.INJECTION
}
