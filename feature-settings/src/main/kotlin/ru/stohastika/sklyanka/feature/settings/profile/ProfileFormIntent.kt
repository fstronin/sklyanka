package ru.stohastika.sklyanka.feature.settings.profile

/**
 * Что человек сделал в форме профиля (SKL-121).
 *
 * Переходов наружу здесь нет: уход с экрана решает `app` (системный «Назад» и успешное
 * сохранение), а форма сообщает о нём событием ViewModel.
 */
sealed interface ProfileFormIntent {

    /**
     * Открыть форму.
     *
     * Режим приходит намерением, а не конструктором ViewModel: ViewModel живёт в Activity
     * и общая у мастера и правки, а править можно любого профиля — контекст активного
     * здесь не ответ (ПРФ-19).
     */
    data class Open(val mode: ProfileFormMode) : ProfileFormIntent

    /** Человек набрал имя; пустое закрывает кнопку и называет причину (ПРФ-9). */
    data class NameChanged(val text: String) : ProfileFormIntent

    /** Выбран образец цвета из закрытого набора (ПРФ-15). */
    data class ColourChosen(val colour: Int) : ProfileFormIntent

    /** Переключён вопрос о дублировании (Р-29). */
    data class MirrorChanged(val enabled: Boolean) : ProfileFormIntent

    /**
     * Завершение: создать либо сохранить.
     *
     * На платной границе это же нажатие открывает витрину, а не создаёт профиль:
     * причина над кнопкой называет, что произойдёт (§5.13, Р-84 п. 2).
     */
    data object Submit : ProfileFormIntent
}
