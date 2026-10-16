package ru.stohastika.sklyanka.domain.profile

/** Чем закончится попытка завести ещё один профиль. */
sealed interface ProfileCreationDecision {

    data object Allowed : ProfileCreationDecision

    /** Два профиля и больше при отсутствии активного доступа (ПРФ-33, Р-84 пп. 1–3). */
    data object BlockedByPro : ProfileCreationDecision

    /** Пять профилей: предел не продаётся, поэтому он закрыт при любой подписке (ПРФ-32, ПРФ-34). */
    data object BlockedByLimit : ProfileCreationDecision
}

/**
 * Граница «Про» и предел на **создание** профиля (ПРФ-32, ПРФ-33, ПРФ-34, Р-84 пп. 1–3).
 *
 * Граница идёт по **числу заведённых** профилей, а не по подписке: без активного доступа
 * создание закрыто, если на устройстве уже два профиля или больше, и удаление лишнего
 * этого не отменяет — пока заведённых не станет меньше двух, добавление остаётся платным.
 * Второй профиль бесплатен всегда (ПРФ-33), поэтому порог и стоит на двух.
 *
 * Предел пяти — отдельное правило, а не часть платного гейта: при пяти профилях добавление
 * закрыто при любой подписке, и этот исход не продаётся — причина называется словами,
 * витрина не открывается (ПРФ-32, ПРФ-34, П-7). Поэтому [BlockedByLimit] проверяется первым:
 * докупленный доступ не должен выглядеть как открытая дверь в тупик.
 *
 * Замка здесь нет: закрыто **завершение создания**, а не открытие формы, — человек имеет
 * право открыть мастер и увидеть, что именно покупает (П-7).
 */
object ProfileCreationGate {

    /** Предел пяти профилей (ПРФ-32): причина — обозримый список и различимая палитра. */
    const val LIMIT = 5

    /** Сколько профилей база разрешает всегда: владелец и один, за которым присматривают (ПРФ-33). */
    const val FREE = 2

    /**
     * @param created сколько профилей заведено прямо сейчас.
     * @param includesPro есть ли активный доступ «Склянка Про».
     * @return [ProfileCreationDecision.BlockedByLimit] при [LIMIT] и больше; иначе
     *   [ProfileCreationDecision.BlockedByPro] при [FREE] и больше без доступа; иначе
     *   [ProfileCreationDecision.Allowed].
     */
    fun decide(created: Int, includesPro: Boolean): ProfileCreationDecision = when {
        created >= LIMIT -> ProfileCreationDecision.BlockedByLimit
        created >= FREE && !includesPro -> ProfileCreationDecision.BlockedByPro
        else -> ProfileCreationDecision.Allowed
    }
}
