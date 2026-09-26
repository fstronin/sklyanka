package ru.heuri.sklyanka.domain.transfer

import ru.heuri.sklyanka.domain.model.Profile

/**
 * Итог сопоставления профилей (ПЕР-19).
 *
 * @property deviceIdByFileId профиль файла → профиль устройства, с которым он слился
 * @property added профили файла, которых на устройстве нет; идентификаторы им уже выданы новые
 */
data class ProfileMatching(val deviceIdByFileId: Map<String, String>, val added: List<Profile>) {
    /** Профиль приёмника для записи файла; null означает битую ссылку, а не новый профиль. */
    fun deviceProfileId(fileProfileId: String): String? = deviceIdByFileId[fileProfileId]
}

/**
 * Сопоставление профилей до лекарств (ПЕР-19).
 *
 * Порядок не косметический: совпадение лекарств ищется **внутри** сопоставленного профиля
 * (ПЕР-20), и без готовой карты профилей «Магний B6» владельца слился бы с «Магнием B6»
 * мамы — то есть история двух людей смешалась бы (ПР-10).
 *
 * Свой профиль сопоставляется со своим по признаку, а не по имени: их по одному с каждой
 * стороны (ИНВ-1), и человек, назвавший себя на новом телефоне иначе, не должен получить
 * два своих профиля.
 *
 * Цвет и дублирование напоминаний существующего профиля не перетираются: настройки
 * устройства принадлежат устройству. Это не мелочь — выключенное файлом дублирование
 * означает, что владелец перестанет получать напоминания подопечного.
 */
class MatchProfiles(private val ids: TransferIdSource) {

    operator fun invoke(devices: List<Profile>, files: List<Profile>): ProfileMatching {
        val deviceSelf = devices.firstOrNull { it.isSelf }
        val byName = devices.filterNot { it.isSelf }.associateBy { TransferNames.normalize(it.name) }

        val pairs = mutableMapOf<String, String>()
        val added = mutableListOf<Profile>()

        files.forEach { file ->
            val existing = if (file.isSelf) deviceSelf else byName[TransferNames.normalize(file.name)]
            if (existing != null) {
                pairs[file.id] = existing.id
            } else {
                // Свой профиль файла сюда не попадает: своего профиля на устройстве
                // не может не быть (ИНВ-1), а значит, пара ему всегда находится.
                val fresh = file.copy(id = ids.newId(), isSelf = false)
                pairs[file.id] = fresh.id
                added += fresh
            }
        }
        return ProfileMatching(deviceIdByFileId = pairs, added = added)
    }
}
