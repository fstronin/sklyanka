package ru.stohastika.sklyanka.domain.profile

/** Чем закончится попытка завести ещё один профиль. */
sealed interface ProfileCreationDecision {

    data object Allowed : ProfileCreationDecision

    /** Пять профилей: предел продукта, а не платная граница (ПРФ-32, ПРФ-34). */
    data object BlockedByLimit : ProfileCreationDecision
}

/**
 * Предел на **создание** профиля (ПРФ-32, ПРФ-34).
 *
 * Предел пяти — продуктовое ограничение, а не часть платной модели: он закрыт всегда,
 * когда профилей уже пять, и причина называется словами — витрина не открывается,
 * продавать здесь нечего (Р-86, П-7).
 *
 * Замка здесь нет: закрыто **завершение создания**, а не открытие формы, — человек имеет
 * право открыть мастер и увидеть, почему дальше нельзя (П-7).
 */
object ProfileCreationGate {

    /** Предел пяти профилей (ПРФ-32): причина — обозримый список и различимая палитра. */
    const val LIMIT = 5

    /**
     * @param created сколько профилей заведено прямо сейчас.
     * @return [ProfileCreationDecision.BlockedByLimit] при [LIMIT] и больше; иначе
     *   [ProfileCreationDecision.Allowed].
     */
    fun decide(created: Int): ProfileCreationDecision =
        if (created >= LIMIT) ProfileCreationDecision.BlockedByLimit else ProfileCreationDecision.Allowed
}
