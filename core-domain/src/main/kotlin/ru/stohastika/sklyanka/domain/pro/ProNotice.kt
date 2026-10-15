package ru.stohastika.sklyanka.domain.pro

/**
 * Слова витрины, которые показывает поверхность.
 *
 * Модель несёт **ключ**, а не текст: строки живут в шести каталогах `core-ui`
 * (`docs/norm/06-design.md` §10, Р-70 п. 3), а домен о ресурсах Android не знает
 * (`ADR-0007`). Отсюда два свойства, которые нужны приёмке: сценарий говорит
 * «причина названа словами» про конкретный ключ, а полнота держится двумя стражами —
 * `ProTextsTest` требует строку в таблице `ProTexts` на каждый ключ домена, а
 * `LocalizationResourcesTest` — непустой перевод на каждый ключ в шести каталогах.
 *
 * В модели нет ни одного числа цены: `%1$s` подставляет готовая строка магазина (Р-68 п. 3).
 */
enum class ProNotice {

    /** `pro_sheet_title` — название шторки и подпись строки блока в настройках. */
    SHEET_TITLE,

    /** `pro_benefit_schedules_short` — короткое имя выгоды. */
    BENEFIT_SCHEDULES,

    /** `pro_benefit_schedules_example` — строка примера у пункта-повода (Р-76 п. 2). */
    BENEFIT_SCHEDULES_EXAMPLE,

    /** `pro_benefit_widgets_short`. */
    BENEFIT_WIDGETS,

    /** `pro_benefit_report_short`. */
    BENEFIT_REPORT,

    /** `pro_benefit_accent_short`. */
    BENEFIT_ACCENT,

    /** `pro_benefit_export_short`. */
    BENEFIT_EXPORT,

    /** `pro_benefit_profiles_short`. */
    BENEFIT_PROFILES,

    /** `pro_period_month` — подпись карточки периода. */
    PERIOD_MONTH,

    /** `pro_period_year`. */
    PERIOD_YEAR,

    /** `pro_period_month_short` — короткая форма периода в строке цены. */
    PERIOD_MONTH_SHORT,

    /** `pro_period_year_short`. */
    PERIOD_YEAR_SHORT,

    /** `pro_year_ribbon` — лента «Выгоднее» над карточкой года (Р-78). */
    YEAR_RIBBON,

    /** `pro_year_cheaper` — плашка «Год дешевле» (Р-68 п. 5). */
    YEAR_CHEAPER,

    /** `pro_action_buy` — главное действие витрины. */
    ACTION_BUY,

    /** `pro_action_restore` — тихая строка восстановления покупки. */
    ACTION_RESTORE,

    /** `pro_action_manage` — главное действие состояния «активна». */
    ACTION_MANAGE,

    /** `pro_action_cancel` — тихая строка «Отменить подписку». */
    ACTION_CANCEL,

    /** `pro_terms_row` — строка «Условия и политика». */
    TERMS_ROW,

    /** `pro_renew_auto` — слова об автопродлении (Р-73 п. 2). */
    RENEW_AUTO,

    /** `pro_renew_terms_short` — цена продления и способ отмены одной строкой. */
    RENEW_TERMS_SHORT,

    /** `pro_renew_price` — цена продления в раскрытом тексте. */
    RENEW_PRICE,

    /** `pro_renew_charge` — день списания и уведомление магазина за 5 дней. */
    RENEW_CHARGE,

    /** `pro_renew_cancel` — способ отмены. */
    RENEW_CANCEL,

    /** `pro_disclosure_more` — тихая строка «Подробнее». */
    DISCLOSURE_MORE,

    /** `pro_state_active_until` — состояние активной подписки и значение строки в настройках. */
    STATE_ACTIVE_UNTIL,

    /** `pro_state_inactive` — значение строки в настройках, когда подписки нет. */
    STATE_INACTIVE,

    /** `pro_store_unavailable` — «магазин недоступен» словами (Р-68 п. 4). */
    STORE_UNAVAILABLE,

    /** `pro_mark` — метка «Про» у элемента платной возможности (Р-67 п. 5). */
    MARK,

    /** `pro_purchase_failed_network` — покупка не прошла: нет связи. */
    PURCHASE_FAILED_NETWORK,

    /** `pro_purchase_failed_unavailable` — магазин недоступен на этом устройстве. */
    PURCHASE_FAILED_UNAVAILABLE,

    /** `pro_purchase_failed_declined` — магазин отклонил покупку. */
    PURCHASE_FAILED_DECLINED,

    /** `pro_purchase_failed_product` — товар недоступен в магазине. */
    PURCHASE_FAILED_PRODUCT,

    /** `pro_purchase_failed_store` — магазин ответил ошибкой. */
    PURCHASE_FAILED_STORE,

    /** `pro_restore_failed` — восстановить покупку не удалось. */
    RESTORE_FAILED,

    /** `pro_restore_nothing_found` — покупок в магазине не нашлось. */
    RESTORE_NOTHING_FOUND,

    /** `pro_purchase_confirming` — магазин подтвердил покупку, живой записи о ней нет. */
    PURCHASE_CONFIRMING,

    /** `pro_restore_not_active` — записи в магазине есть, действующей среди них нет. */
    RESTORE_NOT_ACTIVE,

    /** `pro_manage_failed` — экран подписок магазина не открылся. */
    MANAGE_FAILED,
    ;

    /**
     * Слова для неудачной покупки.
     *
     * Норма витрины этих формулировок не заводила и оставляла их «вместе с прочими словами
     * магазина» (`docs/norm/06-design.md` §5.9): причина обязана называться словами (Р-68 п. 6),
     * и разные причины — разными.
     */
    companion object {

        fun ofPurchaseFailure(failure: ProStoreFailure): ProNotice = when (failure) {
            ProStoreFailure.NETWORK -> PURCHASE_FAILED_NETWORK
            ProStoreFailure.STORE_UNAVAILABLE -> PURCHASE_FAILED_UNAVAILABLE
            ProStoreFailure.PAYMENT_DECLINED -> PURCHASE_FAILED_DECLINED
            ProStoreFailure.PRODUCT_UNAVAILABLE -> PURCHASE_FAILED_PRODUCT
            ProStoreFailure.STORE_ERROR -> PURCHASE_FAILED_STORE
            ProStoreFailure.CONFIRMED_UNREADABLE -> PURCHASE_CONFIRMING
        }
    }
}
