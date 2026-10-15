package ru.stohastika.sklyanka.feature.meds.list

import androidx.compose.runtime.Immutable
import ru.stohastika.sklyanka.domain.medication.TrailingLine
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.feature.meds.form.ScheduleKind
import java.time.LocalDate
import java.time.LocalTime

/** Две позиции сегментного переключателя списка. */
enum class MedsTab { ACTIVE, ARCHIVE }

/**
 * Состояние экрана «Лекарства».
 *
 * [activeCount] считает только курсы по расписанию (ЭКР-3): лекарство «по потребности»
 * в [items] есть, а в счётчике его нет. Правило живёт в домене — тем же числом
 * пользуется экран «Сегодня».
 */
@Immutable
data class MedsUiState(
    val tab: MedsTab = MedsTab.ACTIVE,
    val activeCount: Int = 0,
    val archiveCount: Int = 0,
    val items: List<MedicationCardUi> = emptyList(),
    /** Пока профиль и его лекарства не прочитаны, пустое состояние показывать рано. */
    val loading: Boolean = true,
    /** Окно «Отменить» после удаления лекарства (ОВ-124); `null` — отменять нечего. */
    val deletion: UndoableDeletion? = null,
) {
    /** Пустой список активных показывает призыв добавить лекарство, а не пустоту. */
    val isEmpty: Boolean get() = !loading && items.isEmpty()
}

/**
 * Карточка лекарства в списке.
 *
 * Полями, а не готовыми строками: склонения и единицы собираются из ресурсов (НФТ-44),
 * и состояние экрана, набитое русским текстом, пришлось бы собирать в модуле, где
 * русского текста нет вовсе (НФТ-42).
 */
@Immutable
data class MedicationCardUi(
    val medicationId: String,
    val title: String,
    val form: MedicationForm,
    /** Вид таблетки для силуэта «форма + вид»; у форм без вида — `null` (ОВ-88). */
    val shape: TabletShape?,
    /** Дозировка «50» и её единица «мкг»: единица — из закрытого списка (Р-45), печатается словом языка. */
    val strength: String?,
    val strengthUnit: String?,
    /** Число приёмов в сутки: «1 раз в сутки», «2 раза в сутки». */
    val timesPerDay: Int,
    /** Чипы времени в строке расписания: «08:00», «20:00». */
    val times: List<LocalTime>,
    /**
     * Вид расписания: у сложных видов в слоте частоты стоит его имя (§5.10, ПР-15).
     *
     * У ежедневного вида частота считается по числу приёмов; у остальных «Ежедневно»
     * было бы неправдой, и слот называет вид.
     */
    val scheduleKind: ScheduleKind = ScheduleKind.DAILY,
    /** Остаток либо период приёма — одновременно они не показываются (ЭКР-4). */
    val trailingLine: TrailingLine,
    /** Заполнено только у лекарства «по потребности» (ЭКР-5). */
    val asNeeded: AsNeededCardUi?,
)

/**
 * Чем карточка «по потребности» отличается от карточки с расписанием (ЭКР-5).
 *
 * Отдельным значением, а не тремя необязательными полями карточки: три поля рядом
 * позволяют собрать «ограничение есть, а чипа нет» — карточку, которой в макете нет.
 */
@Immutable
data class AsNeededCardUi(
    val maxPerDay: Int,
    /** «последний приём — 19 сентября»; пусто, если лекарство ещё не принимали. */
    val lastIntakeDate: LocalDate?,
    /** Кнопка «Принять» недоступна, когда суточное ограничение исчерпано (ПР-7). */
    val takeEnabled: Boolean,
)
