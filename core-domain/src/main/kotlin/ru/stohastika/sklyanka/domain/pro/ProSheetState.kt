package ru.stohastika.sklyanka.domain.pro

/**
 * Товар на витрине в том виде, в каком его рисует поверхность: имя периода и цена магазина.
 *
 * Строка цены с периодом собирается на экране из двух готовых строк — цены магазина
 * и короткой формы периода (`docs/norm/06-design.md` §5.9). Формат-ресурса с числом нет:
 * числа в ресурсах нет вовсе (Р-68 п. 3).
 */
data class ProPeriodOffer(val id: ProProductId, val title: String, val price: StorePrice)

/**
 * Раскрытие обязательств магазина до покупки (Р-73 п. 2).
 *
 * Свёрнутый вид — две читаемые строки, раскрытый — полный текст; согласие даётся самим
 * нажатием «Оформить», отдельной галочки нет. Когда цены продления нет (магазин недоступен),
 * строки с ценой в раскрытии не показываются: назвать цену словами без магазина нечем,
 * а подставить туда последнюю известную цену запрещено (Р-68 п. 4).
 */
data class ProDisclosure(val collapsed: List<ProNotice>, val expanded: List<ProNotice>) {

    /** Есть ли что раскрывать: раскрытый текст обязан отличаться от свёрнутого. */
    val expandable: Boolean get() = expanded != collapsed

    companion object {

        /** Строки раскрытия для показа: цена продления известна, если карточки периодов пришли. */
        fun of(priceKnown: Boolean): ProDisclosure = if (priceKnown) {
            ProDisclosure(
                collapsed = listOf(ProNotice.RENEW_AUTO, ProNotice.RENEW_TERMS_SHORT),
                expanded = listOf(
                    ProNotice.RENEW_AUTO,
                    ProNotice.RENEW_PRICE,
                    ProNotice.RENEW_CHARGE,
                    ProNotice.RENEW_CANCEL,
                ),
            )
        } else {
            ProDisclosure(
                collapsed = listOf(ProNotice.RENEW_AUTO, ProNotice.RENEW_CANCEL),
                expanded = listOf(ProNotice.RENEW_AUTO, ProNotice.RENEW_CHARGE, ProNotice.RENEW_CANCEL),
            )
        }
    }
}

/**
 * Состояние шторки витрины — всё, что поверхность показывает, без единого решения на её стороне.
 *
 * Шторка одна на все состояния (Р-67 п. 3): «не оформлена», «активна» и «истекла» — это
 * одно и то же состояние с разной подпиской, а не три поверхности. Сборка модели живёт
 * в домене, потому что приёмка сценариев SKL-122 исполняется без Android
 * (`docs/tasks/SKL-122/TDD.md §2`), а правила вида «кнопка ждёт выбора периода» и «утверждение
 * о выгоде года показывается, только пока верно» — правила продукта, а не разметки.
 */
data class ProSheetState(
    val visible: Boolean = false,
    /**
     * Витрина открывается: намерение уже есть, ответа магазина ещё нет.
     *
     * Отдельный признак, а не раннее `visible`: пока магазин не ответил, показывать нечего
     * (цен нет, состояние подписки не прочитано), но поверхность уже «обещана» человеку —
     * и возврат обязан уйти ей, а не увести на «Сегодня» с витриной поверх чужого экрана
     * (`docs/tasks/SKL-122/TDD.md` §8).
     */
    val opening: Boolean = false,
    val point: ProPoint = ProPoint.SETTINGS,
    val subscription: ProSubscription = ProSubscription.None,
    val offers: ProOffers = ProOffers.Unavailable,
    val selected: ProProductId? = null,
    val busy: Boolean = false,
    val expanded: Boolean = false,
    val notice: ProNotice? = null,
) {

    /** Шесть выгод в порядке показа: пункт-повод первым, если витрину открыла платная функция. */
    val benefits: List<ProBenefitRow> get() = point.benefitRows()

    /**
     * Состояние «активна» — это и есть экран управления автопродлением (Р-73 п. 1):
     * продающего блока у него нет, но состав «Про» остаётся.
     */
    val management: Boolean get() = subscription is ProSubscription.Active

    /** Магазин не ответил: цен нет, выбора периода нет, покупка недоступна (Р-68 п. 4). */
    val storeUnavailable: Boolean get() = offers is ProOffers.Unavailable

    val periods: List<ProPeriodOffer>
        get() = (offers as? ProOffers.Available)
            ?.products
            .orEmpty()
            .map { ProPeriodOffer(id = it.id, title = it.title, price = it.price) }

    /** Утверждение о выгоде года: показывается, только пока оно верно по текущим ценам (Р-68 п. 5). */
    val yearlyCheaper: Boolean
        get() = !storeUnavailable &&
            ProPriceRules.yearlyIsCheaper(product(ProProductId.MONTHLY), product(ProProductId.YEARLY))

    /** Период при открытии не выбран: «Оформить» недоступна, пока человек не выбрал (Р-75 п. 1). */
    val buyEnabled: Boolean
        get() = visible && !management && !storeUnavailable && selected != null && !busy

    val buyBusy: Boolean get() = busy && !management

    /** Цена продления — товар, из которого пришла покупка; без него цену брать не из чего. */
    val renewalPrice: StorePrice?
        get() = (subscription as? ProSubscription.Active)?.productId?.let { product(it)?.price }

    /**
     * Строки раскрытия магазина в текущем виде.
     *
     * В состоянии «активна» раскрытия нет вовсе: слова нужны до покупки, а покупки здесь нет
     * (Р-73 п. 1); цена продления и способ отмены названы строкой цены и строкой отмены.
     */
    val disclosureRows: List<ProNotice>
        get() = when {
            !visible || management -> emptyList()
            expanded -> disclosure.expanded
            else -> disclosure.collapsed
        }

    /**
     * Цена, которую называют строки раскрытия, — цена **выбранного** периода.
     *
     * Пока период не выбран, цены продления назвать нечем: у человека перед глазами две цены,
     * и подставить в слова одну из них значило бы сказать «продление 990 ₽ / год» тому, кто
     * собирается платить помесячно. Поэтому до выбора раскрытие говорит только то, что верно
     * без цены: подписка продлевается автоматически, отменить можно в магазине.
     */
    val disclosurePrice: StorePrice? get() = selected?.let { product(it)?.price }

    val disclosure: ProDisclosure get() = ProDisclosure.of(priceKnown = !management && disclosurePrice != null)

    /** Тихая строка «Подробнее»; в раскрытом виде её нет — раскрывать больше нечего. */
    val disclosureMore: ProNotice?
        get() {
            val collapsible = visible && !management && disclosure.expandable
            return if (collapsible && !expanded) ProNotice.DISCLOSURE_MORE else null
        }

    /** «Условия и политика» — документ, а не шаг покупки; стоит в обоих состояниях. */
    val termsRow: ProNotice? get() = if (visible) ProNotice.TERMS_ROW else null

    private fun product(id: ProProductId): ProProduct? =
        (offers as? ProOffers.Available)?.products?.firstOrNull { it.id == id }

    companion object {

        /** Закрытая шторка: до открытия поверхность не показывает ничего. */
        val closed: ProSheetState = ProSheetState()
    }
}
