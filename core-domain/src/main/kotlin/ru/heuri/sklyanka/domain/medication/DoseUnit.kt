package ru.heuri.sklyanka.domain.medication

import ru.heuri.sklyanka.domain.model.MedicationForm

/**
 * Единица дозы — производная от формы выпуска (`docs/spec/03-screens.md`, «Новое лекарство»).
 *
 * Человеку единица не даётся на редактирование: вопрос «закрытый список или свободный ввод»
 * не решён (ОВ-16), а производная единица оставляет данные, которые потом можно
 * сгруппировать, тогда как свободный ввод — нет.
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
    DOSE("dose"),
    ;

    companion object {
        fun ofCode(code: String): DoseUnit? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Единица дозы по форме выпуска.
 *
 * Спецификация перечисляет ровно четыре единицы — «таблеток», «капсул», «мл», «доз», —
 * поэтому «капли» отнесены к миллилитрам, а «инъекция» к дозам: пятая и шестая единицы
 * появились бы здесь раньше, чем в спецификации, и разошлись бы с ней молча.
 */
fun MedicationForm.doseUnit(): DoseUnit = when (this) {
    MedicationForm.TABLET -> DoseUnit.TABLET
    MedicationForm.CAPSULE -> DoseUnit.CAPSULE
    MedicationForm.SYRUP, MedicationForm.DROPS -> DoseUnit.MILLILITRE
    MedicationForm.SPRAY, MedicationForm.INJECTION -> DoseUnit.DOSE
}
