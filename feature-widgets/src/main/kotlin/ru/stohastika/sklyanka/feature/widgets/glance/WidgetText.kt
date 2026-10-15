package ru.stohastika.sklyanka.feature.widgets.glance

import android.content.res.Resources
import android.text.format.DateFormat
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.widget.WidgetDose
import ru.stohastika.sklyanka.feature.widgets.R
import ru.stohastika.sklyanka.ui.text.DoseText
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import ru.stohastika.sklyanka.ui.R as Ui

/**
 * Слова плиты — из ресурсов (НФТ-42), склонения — из `plurals` (НФТ-44).
 *
 * В разметке виджета нет ни одной склейки слов: у плиты нет доступа к `stringResource`
 * Compose, поэтому тексты собираются здесь через `Resources`. Формулировки, которые уже
 * есть у продукта, переиспользуются: счётчик — `intakes_taken_of_total`, слово пропуска —
 * `status_missed`, надпись профиля — `profile_label`, доступное имя кнопки отметки —
 * `cd_mark_intake` (второго словаря терминов не заводится, §10).
 */
internal object WidgetText {

    /** Имя типа: шапка плиты и подпись строки на экране настройки (§5.11, §5.12). */
    fun subject(resources: Resources, type: WidgetType): String = resources.getString(type.label)

    /** «Принято 2 из 5 приёмов» — то же число словами, что у полосы дня в приложении (§5.2). */
    fun counter(resources: Resources, taken: Int, planned: Int): String =
        resources.getQuantityString(Ui.plurals.intakes_taken_of_total, planned, taken, planned)

    /** Счётчик недели или фраза вместо него, когда завершённых дней с приёмами не было (§5.2). */
    fun weekCounter(resources: Resources, counter: WeekCounter): String = when (counter) {
        is WeekCounter.Fraction -> counter(resources, counter.taken, counter.planned)
        WeekCounter.NoRecords -> resources.getString(R.string.widget_week_empty)
    }

    /** Слово состояния дня для чтения с экрана (§6.2): цвет кружка смысла не несёт один. */
    fun dayState(resources: Resources, state: DayState): String = resources.getString(
        when (state) {
            DayState.ALL_TAKEN -> R.string.widget_day_state_taken
            DayState.PARTIAL -> R.string.widget_day_state_partial
            DayState.MISSED -> R.string.widget_day_state_missed
            DayState.AHEAD -> R.string.widget_day_state_today
            DayState.NO_INTAKES -> R.string.widget_day_state_none
            DayState.BEFORE_HISTORY -> R.string.widget_day_state_before_history
        },
    )

    /** Подпись дня полосы: дата от системы и слово состояния — одним узлом (§6.2). */
    fun dayLabel(resources: Resources, date: LocalDate, state: DayState, zone: ZoneId): String {
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, DATE_SKELETON)
        val moment = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val text = DateFormat.format(pattern, moment).toString()
        return resources.getString(R.string.cd_widget_day, text, dayState(resources, state))
    }

    /** Доза приёма словами: единица и склонение — общим форматтером продукта (§5.11). */
    fun dose(resources: Resources, dose: WidgetDose): String {
        val unit = DoseUnit.ofCode(dose.unit)
        return if (unit == null) {
            dose.amount.plain()
        } else {
            DoseText.line(resources, dose.amount, unit, dose.strength, dose.strengthUnit)
        }
    }

    fun now(resources: Resources): String = resources.getString(R.string.widget_now)

    fun missed(resources: Resources): String = resources.getString(Ui.string.status_missed)

    fun profileLabel(resources: Resources, name: String): String = resources.getString(Ui.string.profile_label, name)

    /** Доступное имя кнопки отметки: называет приём, иначе в списке из пяти карточек она неразличима. */
    fun markIntake(resources: Resources, medicationName: String): String =
        resources.getString(Ui.string.cd_mark_intake, medicationName)

    fun openInApp(resources: Resources): String = resources.getString(R.string.cd_widget_open)

    fun dayDone(resources: Resources): String = resources.getString(R.string.widget_day_done)

    fun dayEmpty(resources: Resources): String = resources.getString(R.string.widget_day_empty)

    fun noMedications(resources: Resources): String = resources.getString(R.string.widget_no_medications)

    /** «1», а не «1.00»: доза введена человеком и показывается так, как он её ввёл. */
    private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

    /** Скелет для системы: шаблон даты подписи дня даёт платформа, а не строка в коде (НФТ-43). */
    private const val DATE_SKELETON = "EEEEdMMMM"
}

/**
 * Три типа виджетов и их имена (§5.11, Р-34).
 *
 * Имя типа стоит в трёх местах и второго набора слов не заводит: подпись строки на экране
 * настройки, имя в системном списке виджетов и — у «Сегодня» и «Недели» — шапка плиты.
 * У «по лекарству» шапка называет лекарство, а тип называют два других места.
 */
enum class WidgetType(val label: Int) {
    TODAY(R.string.widget_type_today),
    WEEK(R.string.widget_type_week),
    MEDICATION(R.string.widget_type_medication),
    ;

    companion object {

        /**
         * Тип плиты по имени приёмника, который ставит платформа (§5.12).
         *
         * Идентификатор виджета у конфигурационной активности один, а тип определяет
         * провайдер: без этого правила человек, выбрав «По лекарству», увидел бы отмеченным
         * «Сегодня» и получил плиту без привязки. Имена приёмников — единственное место,
         * где эта связь записана, и живёт она рядом с типами, а не в экране.
         */
        fun ofProvider(className: String?): WidgetType? = entries.firstOrNull { it.receiverName == className }

        private val WidgetType.receiverName: String
            get() = "ru.stohastika.sklyanka.feature.widgets.receiver." + when (this) {
                TODAY -> "TodayWidgetReceiver"
                WEEK -> "WeekWidgetReceiver"
                MEDICATION -> "MedicationWidgetReceiver"
            }
    }
}
