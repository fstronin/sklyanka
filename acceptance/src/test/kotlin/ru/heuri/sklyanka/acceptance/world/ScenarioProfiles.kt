package ru.heuri.sklyanka.acceptance.world

import ru.heuri.sklyanka.domain.model.Profile

/**
 * Профили сценария и тот из них, что открыт на телефоне.
 *
 * Владелец телефона — **первый заведённый в сценарии профиль**. Правило одно на всю
 * приёмку: раньше одна задача считала владельцем первый профиль, а вторая — профиль
 * с именем «Я», и одна и та же строка Предыстории означала в них разное.
 *
 * Имена профилей приходят из Gherkin и остаются ключами сценария: в модель уходит
 * идентификатор (НФТ-19).
 */
class ScenarioProfiles {

    private val byName = linkedMapOf<String, Profile>()

    /** Профиль, открытый на телефоне. Отметка с часов его не меняет (ПРФ-22). */
    var active: String = ""
        private set

    fun use(name: String) {
        get(name)
        active = name
    }

    /** Профиль по имени; неизвестный заводится на месте — его заводит сама строка сценария. */
    fun get(name: String): Profile = byName.getOrPut(name) {
        Profile(
            id = "profile-${byName.size + 1}",
            name = name,
            color = 0,
            // Первый заведённый профиль — владелец телефона; остальные чужие, и
            // напоминания по ним зеркалятся ему (ПРФ-12).
            isSelf = byName.isEmpty(),
            mirrorReminders = byName.isNotEmpty(),
        )
    }

    fun all(): List<Profile> = byName.values.toList()

    fun byId(profileId: String): Profile? = byName.values.firstOrNull { it.id == profileId }

    fun self(): Profile? = byName.values.firstOrNull { it.isSelf }
}
