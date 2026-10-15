package ru.stohastika.sklyanka.domain.pro

/**
 * Товар подписки: месяц или год (Р-38, Р-68 п. 1).
 *
 * Идентификаторы неизменяемы после создания в консоли и едины для магазинов; в магазин
 * уходит идентификатор выбранного человеком периода, а не «подписка вообще».
 */
enum class ProProductId(val storeId: String) {
    MONTHLY("sklyanka.pro.monthly"),
    YEARLY("sklyanka.pro.yearly"),
    ;

    companion object {

        /** Товар по идентификатору магазина; `null` — магазин назвал товар, которого мы не знаем. */
        fun ofStoreId(storeId: String): ProProductId? = entries.firstOrNull { it.storeId == storeId }
    }
}

/**
 * Цена магазина — готовая строка для человека и сопоставимая величина для правила выгоды.
 *
 * Строка приходит с валютой, скидкой и региональным значением (Р-41): число цены в интерфейсе
 * не собирается и не хардкодится (Р-68 п. 3). Микроединицы нужны ровно одному месту —
 * сравнению «год дешевле» (Р-68 п. 5): проценты и пересчёт «сколько это в месяц» норма
 * не выводит (Р-78 п. 3).
 */
data class StorePrice(val text: String, val amountMicros: Long, val currencyCode: String)

/** Товар в том виде, в каком его называет магазин: идентификатор, имя для человека, цена. */
data class ProProduct(val id: ProProductId, val title: String, val price: StorePrice)
