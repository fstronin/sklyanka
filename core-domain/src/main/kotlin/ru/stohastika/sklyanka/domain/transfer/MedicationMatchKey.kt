package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.model.Medication

/**
 * Ключ совпадения лекарств (ПЕР-20): нормализованное название плюс дозировка препарата.
 *
 * Именно `strength` + `strength_unit`, а не `dose_amount`: дозировка — свойство препарата
 * («по 48 мг»), доза на приём — решение человека («пью две таблетки»). «Магний B6 48 мг»
 * и «Магний B6 48 мг» — одно лекарство, даже если на одном устройстве человек пьёт одну
 * таблетку, а на другом две.
 *
 * Дозировка приводится к нормальному виду числа, иначе «48» и «48.0» из двух разных
 * сборок перестанут быть одной дозировкой. Единица нормализуется как название: «МГ»,
 * «мг » и «мг» — одна единица. Свободного ввода единицы больше нет (Р-45, SKL-126):
 * форма пишет код закрытого списка, поэтому нормализация отвечает только за записи,
 * пришедшие из чужих файлов и из более старых версий.
 */
data class MedicationMatchKey(
    val profileId: String,
    val name: String,
    val strength: String?,
    val strengthUnit: String?,
)

/**
 * Ключ совпадения в пределах профиля.
 *
 * @param profileId профиль **приёмника**: сопоставление идёт внутри уже сопоставленного
 *   профиля (ПЕР-20), и ключ, собранный по профилю файла, склеил бы лекарства разных людей.
 */
fun Medication.matchKey(profileId: String = this.profileId): MedicationMatchKey = MedicationMatchKey(
    profileId = profileId,
    name = TransferNames.normalize(name),
    // stripTrailingZeros приводит 48.0 и 48 к одному виду; toPlainString убирает
    // экспоненциальную запись, в которую stripTrailingZeros уводит круглые числа.
    strength = strength?.stripTrailingZeros()?.toPlainString(),
    strengthUnit = strengthUnit?.let(TransferNames::normalize),
)
