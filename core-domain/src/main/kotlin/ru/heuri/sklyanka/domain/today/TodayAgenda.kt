package ru.heuri.sklyanka.domain.today

import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.stock.StockWarning
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Приёмы одних суток активного профиля, разложенные по правилам экрана «Сегодня».
 *
 * Собирается в домене, а не во `ViewModel`: группировка, метка «Сейчас», состав
 * прогресса и выбор самого срочного предупреждения об остатке — предметные решения,
 * и проверяться они обязаны на JVM без Android (`TDD.md §5.1`).
 */
data class TodayAgenda(
    val date: LocalDate,
    val layout: TodayLayout,
    /** Лекарства без расписания — отдельный блок, а не часть лент (ПР-4). */
    val prn: List<PrnEntry>,
    val progress: DayProgress,
    /** Одно, самое срочное (ПР-13); пусто, когда ни один порог не сработал. */
    val stockWarning: StockNotice?,
    /** Сколько активных лекарств у профиля вообще — вместе с «по потребности». */
    val activeMedications: Int,
) {

    /**
     * Первый запуск: у профиля нет ни одного активного лекарства.
     *
     * Только в этом случае показывается пустое состояние `Empty.dc.html`. Спецификация
     * описывает его условие двумя фразами, и вторая уточняет первую: «если лекарства
     * есть, но на сегодня приёмов нет — показывать этот экран нельзя»
     * (`docs/spec/03-screens.md`, «Сегодня — пусто»). Поэтому условие — отсутствие
     * лекарств, а не отсутствие приёмов.
     */
    val isFirstRun: Boolean get() = activeMedications == 0

    /**
     * Лекарства есть, а на сегодня показывать нечего: курс начинается завтра либо
     * приёмы приходятся на другие дни недели.
     *
     * Отдельное состояние, а не пустота (ОВ-34): текст и макет для него не согласованы,
     * поэтому экран показывает тихую подпись, а не призыв завести первое лекарство —
     * человеку, у которого лекарства уже заведены, он сообщал бы неправду.
     */
    val hasNothingToday: Boolean get() = !isFirstRun && layout.isEmpty && prn.isEmpty()
}

/**
 * Один приём в ленте вместе с лекарством, к которому он относится.
 *
 * Хранится пара доменных объектов, а не набор готовых подписей: дозы, склонения и
 * связь с едой собираются из ресурсов (НФТ-42, НФТ-44), и домен, в котором они
 * оказались бы строками, пришлось бы переводить.
 */
data class IntakeItem(
    val intake: Intake,
    val medication: Medication,
    val presentation: IntakePresentation,
    /**
     * Фактическое время отметки местными часами — «08:05» на карточке.
     *
     * Считается здесь, а не на экране: момент отметки хранится в UTC (ПР-3), а перевод
     * его в местное время требует часового пояса, который экрану брать неоткуда —
     * системные часы ему запрещены (НФТ-48).
     */
    val markedAt: LocalTime?,
) {
    val id: String get() = intake.id
    val medicationId: String get() = medication.id

    /** Отметить с экрана можно только то, что ещё ожидается (ПР-1, ПР-2). */
    val isMarkable: Boolean get() = presentation.isMarkable
}

/**
 * Вид карточки приёма (`docs/spec/03-screens.md`, «Сегодня»).
 *
 * Четыре, а не три: четвёртый — просроченный приём. В макете он не нарисован, и вид
 * ему назначен спецификацией словами: приглушённая карточка без кнопок отметки
 * со статусом, подписанным текстом (ОВ-35).
 */
enum class IntakePresentation {
    /** Отмечен принятым: галочка и фактическое время отметки. */
    TAKEN,

    /** Ожидается, время наступило: «Принято» и «Пропустить» в строку. */
    DUE_NOW,

    /** Ожидается, время впереди: круглая кнопка-галочка. */
    UPCOMING,

    /** Пропущен осознанно или без отметки: приглушённый вид, кнопок нет. */
    MISSED,
    ;

    val isMarkable: Boolean get() = this == DUE_NOW || this == UPCOMING
}

/**
 * Группа приёмов одного времени — лента макета `Main.dc.html`.
 *
 * Группа существует и для одного приёма: заголовок с временем стоит над каждой лентой,
 * а «одновременных приёмов больше одного» — это признак групповой карточки, а не
 * повод иметь две разные структуры.
 */
data class TimeGroup(
    val time: LocalTime,
    val partOfDay: PartOfDay,
    /** Метка «Сейчас» вместо метки времени суток (`Main.dc.html`). */
    val isNow: Boolean,
    val items: List<IntakeItem>,
) {
    val markable: List<IntakeItem> get() = items.filter { it.isMarkable }

    /** Все приёмы группы отмечены — группа «сделана». */
    val isMarked: Boolean get() = items.none { it.isMarkable }
}

/**
 * Лекарство «по потребности» в своём блоке (`Main.dc.html`).
 *
 * Записи «ожидается» у такого режима не бывает (ПР-4), поэтому здесь лекарство,
 * а не приём: приём рождается в момент нажатия «Принять».
 */
data class PrnEntry(
    val medication: Medication,
    /** Ограничение приёмов в сутки; пусто — ограничения нет (ПР-7). */
    val maxPerDay: Int?,
    val takenToday: Int,
    /** Кнопка «Принять» доступна, пока суточное ограничение не выбрано (ИНВ-17). */
    val canTake: Boolean,
) {
    val medicationId: String get() = medication.id
}

/**
 * Прогресс дня (ЭКР-1, ЭКР-2).
 *
 * [planned] и [taken] считают только приёмы по расписанию: у приёма «по потребности»
 * плана нет, и включать его в знаменатель нечестно — доля «принято» тогда зависела бы
 * от того, сколько раз человеку понадобилось обезболивающее.
 */
data class DayProgress(
    val taken: Int,
    val planned: Int,
    /** Число активных лекарств по расписанию — второе число подписи плотного дня (ЭКР-2). */
    val medicationCount: Int,
) {

    /** Доля заполнения полосы. День без плана рисуется пустой полосой, а не делением на ноль. */
    val fraction: Float get() = if (planned == 0) 0f else taken.toFloat() / planned.toFloat()

    /** Полоса прогресса показывается, только когда есть что считать. */
    val hasPlan: Boolean get() = planned > 0
}

/**
 * Предупреждение об остатке, готовое к показу плашкой (ПР-13).
 *
 * Само предупреждение считает SKL-12 — отбор лекарств, прогноз запаса и выбор самого
 * срочного живут в `domain.stock`, и второго их определения здесь нет намеренно.
 * Экрану не хватает только единицы остатка: «осталось 9 таблеток» и «осталось 9 капсул»
 * склоняются по-разному, а единица известна лекарству, а не предупреждению.
 */
data class StockNotice(val warning: StockWarning, val doseUnit: DoseUnit) {
    val medicationId: String get() = warning.medicationId
    val medicationName: String get() = warning.medicationName
    val unitsLeft: BigDecimal get() = warning.unitsLeft
    val daysLeft: Int get() = warning.daysLeft
}
