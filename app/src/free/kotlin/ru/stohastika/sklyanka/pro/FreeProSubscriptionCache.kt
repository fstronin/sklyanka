package ru.stohastika.sklyanka.pro

import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Доступ «Про» в бесплатной сборке (Р-86, `ADR-0016` п. 3).
 *
 * Продукт бесплатен, поэтому доменные гейты состава обязаны пропускать всё, что раньше
 * было платным. Кэш — единственное место, где доступ становится «есть»: гейты и экраны
 * продолжают читать [ProSubscriptionCache] и `ProStatus.included`, и ни одно правило
 * продукта не меняется.
 *
 * Срок `LocalDate.MAX` выбран не как «подписка до бесконечности», а как «доступ бессрочен»:
 * у бесплатной сборки нет магазина, который мог бы назвать дату, и нет проверки, которая
 * могла бы её просрочить. Строка о дате показывается только в блоке «Склянка Про», а он
 * при выключенной метке интерфейса не выводится вовсе (`LocalProUiEnabled`).
 *
 * Запись ничего не меняет: писать в этот кэш некому — покупка живёт только в `pro`,
 * и бесплатная сборка не содержит ни витрины, ни магазина.
 */
@Singleton
class FreeProSubscriptionCache @Inject constructor() : ProSubscriptionCache {

    override suspend fun read(): ProSubscription = ProSubscription.Active(
        productId = null,
        until = LocalDate.MAX,
    )

    override suspend fun write(subscription: ProSubscription) = Unit
}
