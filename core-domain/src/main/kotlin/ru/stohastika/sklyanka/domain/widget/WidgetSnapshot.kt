package ru.stohastika.sklyanka.domain.widget

import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.today.IntakePresentation
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Снимок виджета домашнего экрана (SKL-118, `TDD.md §3`).
 *
 * Виджет не считает ничего своего: разметка Glance не проверяется сценарной приёмкой, и
 * потому всё, что виджет показывает и решает, собирается здесь, в домене, — снимком. Тогда
 * приёмочные сценарии проверяют снимок (состав дня, отметка, пустота, свежесть), а слой
 * Glance остаётся тонким: «нарисовать снимок».
 *
 * Снимок не несёт ни цен, ни слов о подписке: виджет — лечебная поверхность, и платность
 * живёт на экране настройки (ПР-22, Р-22, `TDD.md §5`, ИИ-4).
 */
interface WidgetSnapshot {

    /** Сутки, к которым относится снимок. Всегда есть: по нему виджет проверяет свежесть. */
    val date: LocalDate

    /**
     * Компактная индикация чужого профиля — имя и цвет (ПР-24, ПРФ-12, ПРФ-15).
     *
     * `null` у профиля владельца: у него индикации нет (ПРФ-16, ПРФ-17).
     */
    val profile: WidgetProfileIndication?

    /**
     * Снимок нарисован только в свои сутки (П-7, `TDD.md §3`).
     *
     * Вчерашний день как сегодняшний не рисуется: до перерисовки виджет показывает тихое
     * состояние, а не прошлые данные.
     */
    fun isFresh(today: LocalDate): Boolean = date == today
}

/** Приёмы дня активного профиля — то, что показывает виджет «Сегодня». */
data class WidgetDaySnapshot(
    override val date: LocalDate,
    /** Приёмы по расписанию, по возрастанию времени — тот же состав, что на экране «Сегодня». */
    val items: List<WidgetIntake>,
    /**
     * Сколько активных лекарств у профиля вообще — вместе с «по потребности».
     *
     * Различает два тихих состояния, которые нельзя слить: «приёмы бывают, сегодня их нет»
     * и «лекарств нет вовсе» (§5.11, слова состояний ОВ-34). Считает их тот же домен,
     * что и экран «Сегодня» ([ru.stohastika.sklyanka.domain.today.TodayAgenda]).
     */
    val activeMedications: Int,
    override val profile: WidgetProfileIndication?,
) : WidgetSnapshot {

    /**
     * Приёмов на день нет вовсе.
     *
     * Тихое состояние, а не пустота-поломка: виджет говорит «на сегодня ничего», и только.
     * Признак выведен из состава, а не хранится отдельным полем, — иначе он однажды
     * разошёлся бы с ним.
     */
    val empty: Boolean get() = items.isEmpty()
}

/** Семь последних дней — то, что показывает виджет «Неделя». */
data class WidgetWeekSnapshot(
    /** Сутки, к которым привязано окно: сегодняшние. */
    override val date: LocalDate,
    /**
     * Счётчик недели по правилу ЭКР-6 — тот же, что у блока «Последние 7 дней» (§5.2).
     *
     * Числитель — только завершившиеся сутки, знаменатель — приёмы всех семи дней;
     * `NoRecords` — завершённых дней с приёмами не было, и тогда вместо счётчика стоит
     * фраза, а полосы кружков нет вовсе (§5.2, §5.11).
     */
    val counter: WeekCounter,
    val days: List<WidgetDaySummary>,
    override val profile: WidgetProfileIndication?,
) : WidgetSnapshot

/**
 * Один день полосы недели.
 *
 * [state] — состояние дня словарём §5.2 ([DayState] истории), а не второй его редакцией:
 * по нему рисуется кружок (форма и роль), слова состояния берутся из подписей для чтения
 * с экрана. [taken] и [planned] нужны счётчику и чтению с экрана; план — приёмы
 * по расписанию (ПР-4).
 */
data class WidgetDaySummary(val date: LocalDate, val state: DayState, val taken: Int, val planned: Int)

/** Приёмы одного лекарства — то, что показывает виджет «По лекарству». */
data class WidgetMedicationSnapshot(
    override val date: LocalDate,
    val medicationId: String,
    val name: String,
    val items: List<WidgetIntake>,
    override val profile: WidgetProfileIndication?,
) : WidgetSnapshot {

    /** У выбранного лекарства на сегодня приёмов нет — тихое состояние. */
    val empty: Boolean get() = items.isEmpty()
}

/**
 * Один приём в снимке.
 *
 * Не `IntakeItem` экрана: виджету не нужны ни группировка по времени, ни прогресс дня,
 * ни предупреждение об остатке, а разметке Glance нельзя отдать доменную пару «приём
 * и лекарство» — поверхность обязана быть плоской. Доза — снимок приёма, а не доза
 * лекарства (ПР-19, SKL-117).
 */
data class WidgetIntake(
    val intakeId: String,
    val time: LocalTime,
    val medicationName: String,
    val dose: WidgetDose,
    /**
     * Вид карточки приёма — то же правило домена, что у экрана «Сегодня» (ПР-1, ПР-2).
     *
     * Разметка по нему рисует строку: у наступившего — слово «Сейчас» и залитая кнопка,
     * у будущего — контурная, у прошедшего без отметки — «Пропущено» без кнопок (§5.11).
     * Своего правила состояния строка виджета не заводит.
     */
    val presentation: IntakePresentation,
) {

    /** Отмечен принятым — такой приём строки на плите не занимает (§5.11). */
    val taken: Boolean get() = presentation == IntakePresentation.TAKEN

    /** Отметить можно только ожидающий приём (ПР-1, ПР-2). */
    val markable: Boolean get() = presentation.isMarkable
}

/**
 * Доза приёма так, как её называет подпись: «2 таблетки», «1 таблетка · 50 мкг» (§5.11).
 *
 * Единица — кодом, а не словом: русского текста в домене нет (НФТ-42), а склонение
 * и перевод живут в ресурсах. Слова собирает общий форматтер `core-ui` (`DoseText`),
 * тот же, что у экрана «Сегодня»: второй редакции формата дозы в продукте нет.
 */
data class WidgetDose(
    val amount: BigDecimal,
    /** Код единицы дозы (`DoseUnit.code`) или пусто — незнакомая запись (МГ-6). */
    val unit: String,
    /** Дозировка препарата и её единица: показываются только вместе (ИНВ-5). */
    val strength: BigDecimal?,
    val strengthUnit: String?,
)

/** Имя и цвет чужого профиля — без слов «чужой» и «подопечный» (ПР-24, ПРФ-12). */
data class WidgetProfileIndication(val name: String, val color: Int)
