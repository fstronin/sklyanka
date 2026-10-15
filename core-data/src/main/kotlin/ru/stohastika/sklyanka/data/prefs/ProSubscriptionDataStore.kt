package ru.stohastika.sklyanka.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Последний ответ магазина о подписке — в постоянных настройках приложения (`ADR-0015` п. 1).
 *
 * Хранилище то же, что у остальных настроек устройства (`DataModule.providePreferences`),
 * и по той же причине: подписка — свойство не данных человека, а устройства. В Room она
 * не едет намеренно: файл переноса не должен нести состояние покупки (ПЕР-37), а строка
 * в базе означала бы, что однажды её туда же положат и в выгрузку.
 *
 * **Срока давности у записи нет.** Ключ хранит последний ответ магазина целиком, и никакое
 * время проверки в него не пишется: с любым сроком доступ отнимался бы у того, кто просто
 * не выходил в интернет, — то есть отказ чужого сервиса закрывал бы учёт (П-6, Р-68 п. 7).
 * Принятый риск назван решением честно: человек, отменивший подписку и не выходящий в сеть,
 * остаётся с доступом.
 *
 * Неудачная проверка сюда не попадает вовсе: пишутся только `None` и `Active`, поэтому
 * «магазин молчал» не стирает прошлый ответ.
 */
@Singleton
class ProSubscriptionDataStore @Inject constructor(private val preferences: DataStore<Preferences>) :
    ProSubscriptionCache {

    /** `null` — подписку ещё ни разу не проверяли (или ответа магазина не было ни одного). */
    override suspend fun read(): ProSubscription? = parse(preferences.data.first()[SUBSCRIPTION])

    override suspend fun write(subscription: ProSubscription) {
        preferences.edit { mutable ->
            mutable[SUBSCRIPTION] = encode(subscription)
        }
    }

    private fun encode(subscription: ProSubscription): String = when (subscription) {
        ProSubscription.None -> NONE
        is ProSubscription.Active -> active(subscription)
    }

    /**
     * Запись активной подписки: товар и день «до».
     *
     * Товар может быть неизвестен (восстановление без названного продукта) — тогда поле
     * остаётся пустым, и цену продления по нему не взять, а доступ он не отменяет.
     */
    private fun active(subscription: ProSubscription.Active): String = buildString {
        append(ACTIVE)
        append(SEPARATOR)
        append(subscription.productId?.storeId.orEmpty())
        append(SEPARATOR)
        append(subscription.until.toEpochDay())
    }

    /**
     * Разбор записи.
     *
     * Незнакомая или испорченная строка читается как «ответа не было», а не как ошибка:
     * подписка — не данные человека, и сломать из-за неё запуск приложения нельзя.
     * Идентификатор товара, которого больше нет в перечислении, читается как отсутствующий:
     * цену продления по нему всё равно не взять, а доступ он не отменяет.
     */
    private fun parse(stored: String?): ProSubscription? = when {
        stored == null -> null
        stored == NONE -> ProSubscription.None
        else -> activeOf(stored)
    }

    /** Разбор записи активной подписки: товар и день «до». */
    private fun activeOf(stored: String): ProSubscription? {
        val parts = stored.split(SEPARATOR)
        val active = parts.size == ACTIVE_PARTS && parts[0] == ACTIVE
        val until = parts.getOrNull(2)?.toLongOrNull()?.let(LocalDate::ofEpochDay)
        return if (active && until != null) {
            ProSubscription.Active(ProProductId.ofStoreId(parts[1]), until)
        } else {
            null
        }
    }

    private companion object {

        val SUBSCRIPTION = stringPreferencesKey("pro_subscription")

        const val NONE = "none"

        const val ACTIVE = "active"

        const val SEPARATOR = '|'

        const val ACTIVE_PARTS = 3
    }
}
