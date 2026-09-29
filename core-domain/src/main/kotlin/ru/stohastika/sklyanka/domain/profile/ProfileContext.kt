package ru.stohastika.sklyanka.domain.profile

import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.domain.model.Profile

/**
 * В чьём профиле сейчас работает приложение.
 *
 * Единственный источник контекста и для запросов, и для оформления экрана (`TDD.md §5.1`).
 * Двух независимых источников здесь быть не может: разойдясь, они дадут экран
 * с индикацией одного профиля и данными другого — отказ хуже отсутствия индикации.
 */
data class ProfileContext(
    val id: String,
    val name: String,
    /** Цвет ARGB из ограниченного набора дизайн-системы (ПРФ-15); палитру знает `core-ui`. */
    val color: Int,
    val isSelf: Boolean,
    val mirrorReminders: Boolean,
) {

    /**
     * Нужны ли шесть признаков чужого профиля (ПРФ-11).
     *
     * Вычисляется из `is_self`, а не хранится отдельным полем (ПРФ-16): отдельное поле
     * можно выставить в `false` у чужого профиля, и защита отключится одним значением.
     */
    val showsForeignIndication: Boolean get() = !isSelf

    companion object {
        fun of(profile: Profile): ProfileContext = ProfileContext(
            id = profile.id,
            name = profile.name,
            color = profile.color,
            isSelf = profile.isSelf,
            mirrorReminders = profile.mirrorReminders,
        )
    }
}

/**
 * Поставщик контекста активного профиля.
 *
 * Порт домена: реализация живёт в `core-data` и берёт идентификатор из постоянных
 * настроек приложения (ПРФ-17), а имя и цвет — из базы.
 */
interface ProfileContextProvider {

    /** Активный профиль, меняющийся вместе с переключением. */
    val current: Flow<ProfileContext>

    /** Активный профиль здесь и сейчас — для разового чтения вне композиции. */
    suspend fun snapshot(): ProfileContext?
}
