package ru.stohastika.sklyanka.feature.settings.profile

import ru.stohastika.sklyanka.domain.profile.ProfileCreationDecision

/**
 * Что за форма открыта: мастер добавления или правка заведённого (SKL-121, §5.13–§5.14).
 *
 * Форма одна на оба случая, и это правило, а не экономия: что человек видел при создании,
 * то же видит и при правке (§5.14). Отличий три, и все — слова и состояние полей:
 * заголовок, подпись кнопки и заполненные значения.
 */
sealed interface ProfileFormMode {

    /** «Новый профиль»: имя, цвет, вопрос о дублировании (ПРФ-9). */
    data object Add : ProfileFormMode

    /**
     * «Профиль»: правка заведённого — имя у любого, цвет и дублирование у чужого (ПРФ-27).
     *
     * Идентификатор обязателен и приходит от строки списка: править можно и неактивный
     * профиль, а контекст активного здесь не помощник (ПРФ-19).
     */
    data class Edit(val profileId: String) : ProfileFormMode
}

/**
 * Единственная причина, названная словами над кнопкой (§5.13, правило «по одной за раз»).
 *
 * Отдельный тип, а не набор флагов: норме известны ровно две причины, и вместе они
 * не показываются. Перечисление не даёт собрать «пустое имя и предел сразу».
 */
sealed interface ProfileFormBlock {

    /** Имя не задано: кнопка недоступна, причина «Укажите имя профиля» (ПРФ-9). */
    data object Name : ProfileFormBlock

    /**
     * Профилей пять: добавление закрыто (ПРФ-32, ПРФ-34).
     *
     * Предел — правило продукта, а не платная граница: причина называется только словами,
     * и докупить здесь нечего.
     */
    data object Limit : ProfileFormBlock
}

/**
 * Состояние формы профиля (SKL-121).
 *
 * Живёт в ViewModel: между сборками стенда приёмки набранное имя и выбранный цвет обязаны
 * переживать пересборку композиции (§5.13).
 *
 * @property isSelf правится профиль владельца: у него нет ни блока цвета, ни переключателя
 *   дублирования (ПРФ-26) — цвет владельца не виден нигде (ПРФ-16).
 * @property creation исход гейта на создание; в правке не участвует: предел
 *   закрывает добавление, а не настройку заведённого (ПРФ-32, Р-28).
 */
data class ProfileFormUiState(
    val mode: ProfileFormMode = ProfileFormMode.Add,
    val name: String = "",
    /** Цвет ARGB из закрытого набора (ПРФ-15); в мастере по умолчанию — первый свободный. */
    val colour: Int = 0,
    /** Дублирование напоминаний: при создании включено (Р-29); у владельца не показывается. */
    val mirrorReminders: Boolean = true,
    val isSelf: Boolean = false,
    val creation: ProfileCreationDecision = ProfileCreationDecision.Allowed,
    val saving: Boolean = false,
) {

    /** Блок цвета есть у чужого профиля — и в мастере, и в правке (§5.14). */
    val showsColour: Boolean get() = !isSelf

    /** Вопрос о дублировании задаётся у чужого; у владельца флага нет (ПРФ-26). */
    val showsMirror: Boolean get() = !isSelf

    /**
     * Причина над кнопкой — ровно одна (§5.13).
     *
     * Предел называется первым: он закрывает добавление и не зависит от имени.
     * Дальше — пустое имя.
     */
    val block: ProfileFormBlock?
        get() = when {
            mode is ProfileFormMode.Add && creation == ProfileCreationDecision.BlockedByLimit -> ProfileFormBlock.Limit
            name.isBlank() -> ProfileFormBlock.Name
            else -> null
        }
}
