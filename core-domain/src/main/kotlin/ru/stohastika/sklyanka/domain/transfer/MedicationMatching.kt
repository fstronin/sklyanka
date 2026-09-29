package ru.stohastika.sklyanka.domain.transfer

import ru.stohastika.sklyanka.domain.model.Medication

/**
 * Что именно различается у совпавшей пары (ПЕР-20).
 *
 * Перечисление, а не готовая строка: текст различия собирает экран из ресурсов (НФТ-42),
 * а решение «что считать различием» предметное и живёт в домене. Различия не влияют
 * на признание совпадения — они нужны человеку, чтобы решение было осознанным.
 */
enum class MatchDifference {
    SCHEDULE_MODE,
    TIMES,
    FORM,
    SHAPE,
    DOSE_AMOUNT,
    FOOD_RELATION,
    PERIOD,
    STOCK,
    MAX_PER_DAY,
}

/**
 * Решение человека по одному совпадению (ПЕР-21).
 *
 * Массового «применить ко всем» здесь нет и не появится: именно на этом шаге человек
 * распоряжается своими медицинскими данными, и скорость тут не ценность (ПЕР-22).
 */
enum class MatchDecision(val code: String) {
    /** Запись устройства не меняется; приёмы файла привязываются к ней. */
    KEEP_MINE("keep_mine"),

    /** Поля записи устройства заменяются значениями файла; приёмы файла идут к ней же. */
    TAKE_FROM_FILE("take_from_file"),

    /** Создаётся второе лекарство с новым идентификатором; приёмы файла идут к нему. */
    ADD_SEPARATELY("add_separately"),

    /** Запись устройства не меняется; приёмы файла отбрасываются. */
    DO_NOT_LOAD("do_not_load"),
    ;

    companion object {
        /**
         * Решение до выбора человека.
         *
         * «Оставить моё» — единственный безопасный выбор по умолчанию: он ничего
         * не переписывает и ничего не теряет, а историю файла сохраняет.
         */
        val DEFAULT: MatchDecision = KEEP_MINE
    }
}

/**
 * Совпадение по названию и дозировке внутри одного сопоставленного профиля (ПЕР-20).
 *
 * @property device запись устройства
 * @property file запись файла
 * @property differences чем записи различаются — для карточки решения
 */
data class MedicationMatch(val device: Medication, val file: Medication, val differences: List<MatchDifference>)

/**
 * Итог поиска совпадений: совпавшие пары и записи файла, которых на устройстве нет.
 *
 * Несовпавшие добавляются без вопросов (ПЕР-15 п. 5) — спрашивать о них нечего.
 */
data class MedicationMatching(val matches: List<MedicationMatch>, val unmatched: List<Medication>)

/**
 * Поиск совпадений лекарств (ПЕР-20).
 *
 * Профиль приёмника берётся из готовой карты сопоставления профилей: ключ, собранный
 * по профилю файла, склеил бы лекарства разных людей.
 *
 * Запись файла, чей профиль в карте не нашёлся, здесь молча пропускается: это битая
 * ссылка, и её ловит проверка снимка до всякого слияния (ПЕР-28) — дублировать отказ
 * в двух местах значит получить два разных представления о том, что такое целый файл.
 */
class FindMedicationMatches {

    operator fun invoke(
        devices: List<Medication>,
        files: List<Medication>,
        profiles: ProfileMatching,
    ): MedicationMatching {
        val deviceByKey = devices.groupBy { it.matchKey() }
        val matches = mutableListOf<MedicationMatch>()
        val unmatched = mutableListOf<Medication>()
        // Одна запись устройства не может совпасть с двумя записями файла: иначе два
        // решения человека спорили бы за одну и ту же строку базы.
        val taken = mutableSetOf<String>()

        files.forEach { file ->
            val targetProfile = profiles.deviceProfileId(file.profileId)
            if (targetProfile == null) return@forEach
            val candidate = deviceByKey[file.matchKey(targetProfile)]
                ?.firstOrNull { it.id !in taken }
            if (candidate == null) {
                unmatched += file
            } else {
                taken += candidate.id
                matches += MedicationMatch(candidate, file, differencesOf(candidate, file))
            }
        }
        return MedicationMatching(matches = matches, unmatched = unmatched)
    }

    /** Различия пары. Порядок фиксирован, чтобы карточка решения не переставляла строки. */
    private fun differencesOf(device: Medication, file: Medication): List<MatchDifference> = buildList {
        if (device.scheduleMode != file.scheduleMode) add(MatchDifference.SCHEDULE_MODE)
        if (device.times != file.times) add(MatchDifference.TIMES)
        if (device.form != file.form) add(MatchDifference.FORM)
        if (device.shape != file.shape) add(MatchDifference.SHAPE)
        if (device.doseAmount.compareTo(file.doseAmount) != 0) add(MatchDifference.DOSE_AMOUNT)
        if (device.foodRelation != file.foodRelation) add(MatchDifference.FOOD_RELATION)
        if (device.startDate != file.startDate || device.endDate != file.endDate) add(MatchDifference.PERIOD)
        if (!sameStock(device, file)) add(MatchDifference.STOCK)
        if (device.maxPerDay != file.maxPerDay) add(MatchDifference.MAX_PER_DAY)
    }

    /** Остаток сравнивается по значению, а не по объекту: «9» и «9.0» — один остаток. */
    private fun sameStock(device: Medication, file: Medication): Boolean {
        val mine = device.stockLeft
        val theirs = file.stockLeft
        return when {
            mine == null && theirs == null -> true
            mine == null || theirs == null -> false
            else -> mine.compareTo(theirs) == 0
        }
    }
}
