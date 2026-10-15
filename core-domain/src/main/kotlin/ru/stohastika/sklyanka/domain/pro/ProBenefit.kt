package ru.stohastika.sklyanka.domain.pro

/**
 * Состав «Склянки Про» — шесть возможностей офлайн-связки (Р-16, `docs/norm/01-product.md` §5.2).
 *
 * Порядок объявления — порядок нормы состава: сложные расписания → виджеты → расширенный
 * отчёт врачу → акцент и варианты иконки → экспорт истории → профили сверх двух. Порядок
 * не переставляется под вкус экрана: витрина называет одни и те же шесть выгод в одном
 * порядке во всех состояниях, где пункта-повода нет (Р-75 п. 3).
 */
enum class ProBenefit {
    SCHEDULES,
    WIDGETS,
    REPORT,
    ACCENT,
    EXPORT,
    PROFILES,
}

/**
 * Точка выбора платной функции — то, из-за чего человек увидел витрину (Р-67 п. 2).
 *
 * Норма различает входы по месту: форма лекарства (расписания), история (отчёт), настройки.
 * У входа «настройки» пункта-повода нет вовсе — строка блока «Склянка Про» ведёт в витрину
 * из общего списка, и там действует фиксированный порядок состава (Р-75 п. 3).
 *
 * Задачи 117–121 добавляют свои входы (виджеты, экспорт, акцент); значения перечисления
 * описывают ровно те входы, которые нормированы сегодня, чтобы «пункт-повод» не называл
 * поверхность, которой в продукте нет.
 */
enum class ProPoint(private val contextual: ProBenefit?) {

    /** Строка блока «Склянка Про» в настройках (Р-67 п. 6). */
    SETTINGS(null),

    /** Расписание в форме лекарства (Р-67 п. 2). */
    MEDICATION_FORM(ProBenefit.SCHEDULES),

    /**
     * Добавление виджета домашнего экрана (Р-16, Р-34, ПР-23).
     *
     * Закрыта установка нового виджета и добавление типов, а не отметка из уже стоящего
     * (Р-32). Поэтому точка входа — экран настройки виджета, а не сама поверхность:
     * на домашнем экране у виджета нет ни цен, ни слов о подписке (ИИ-4).
     */
    WIDGETS(ProBenefit.WIDGETS),

    /** История и отчёт врачу (Р-67 п. 2). */
    HISTORY(ProBenefit.REPORT),
    ;

    /**
     * Шесть выгод в порядке показа: пункт-повод первым, остальные — в порядке нормы состава.
     *
     * Контекстный пункт список не удваивает — он стоит в нём один раз (Р-75 п. 3).
     */
    fun benefitOrder(): List<ProBenefit> = contextual
        ?.let { first -> listOf(first) + ProBenefit.entries.filterNot { it == first } }
        ?: ProBenefit.entries.toList()

    /** У какой выгоды стоит строка примера; `null` — примера нет ни у одной (Р-76 п. 2). */
    fun exampleOwner(): ProBenefit? = contextual
}

/**
 * Строка выгоды на витрине: короткое имя и, у пункта-повода, строка примера.
 *
 * Полных формулировок с примерами у всех шести выгод нет — они отменены правкой Р-76 п. 2;
 * состав возможности называет только тот пункт, из-за которого витрину открыли.
 */
data class ProBenefitRow(val benefit: ProBenefit, val name: ProNotice, val example: ProNotice?)

/** Строки состава в порядке показа для этого входа. */
fun ProPoint.benefitRows(): List<ProBenefitRow> = benefitOrder().map { benefit ->
    ProBenefitRow(
        benefit = benefit,
        name = benefit.notice,
        example = if (exampleOwner() == benefit) benefit.example else null,
    )
}

/** Короткое имя выгоды (`pro_benefit_*_short`, `docs/norm/06-design.md` §10). */
private val ProBenefit.notice: ProNotice
    get() = when (this) {
        ProBenefit.SCHEDULES -> ProNotice.BENEFIT_SCHEDULES
        ProBenefit.WIDGETS -> ProNotice.BENEFIT_WIDGETS
        ProBenefit.REPORT -> ProNotice.BENEFIT_REPORT
        ProBenefit.ACCENT -> ProNotice.BENEFIT_ACCENT
        ProBenefit.EXPORT -> ProNotice.BENEFIT_EXPORT
        ProBenefit.PROFILES -> ProNotice.BENEFIT_PROFILES
    }

/** Строка примера; сегодня она есть только у расписаний (`pro_benefit_schedules_example`). */
private val ProBenefit.example: ProNotice?
    get() = when (this) {
        ProBenefit.SCHEDULES -> ProNotice.BENEFIT_SCHEDULES_EXAMPLE
        else -> null
    }
