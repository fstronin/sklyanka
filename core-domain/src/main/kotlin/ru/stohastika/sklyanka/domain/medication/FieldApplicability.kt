package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import java.math.BigDecimal

/**
 * Какие блоки формы существуют при текущем черновике (`TDD.md §5.2`).
 *
 * Чистая функция от черновика, а не состояние экрана: те же правила применимости
 * действуют при загрузке файла переноса данных (SKL-19), где формы нет вовсе.
 */
data class FieldApplicability(
    /** «Вид таблетки»: только для «таблетки» (ПР-9, ИНВ-16, ОВ-88). */
    val shapeVisible: Boolean,
    /** «Приёмов в сутки» и «Время приёма»: только при «по расписанию» (ПР-8). */
    val timesVisible: Boolean,
    /** «Не чаще N раз в сутки»: только при «по потребности» (ПР-7, ИНВ-17). */
    val maxPerDayVisible: Boolean,
    /**
     * «Связь с едой»: только при «по расписанию» (ОВ-83).
     *
     * Отдельным признаком, а не переиспользованием [timesVisible]: блоков, зависящих
     * от режима, теперь два, и связь с едой скрыта не потому, что скрыто время,
     * а потому, что приём «по потребности» ею не управляет.
     */
    val foodRelationVisible: Boolean,
    /** Строка «Окончание» активна, когда «Принимать бессрочно» выключено. */
    val endDateEnabled: Boolean,
    /** Порог остатка действует, когда «Напомнить о покупке» включено. */
    val stockThresholdEnabled: Boolean,
    /** Единица дозы и остатка — производная формы выпуска. */
    val doseUnit: DoseUnit,
)

/** Применимость блоков для этого черновика. */
fun MedicationDraft.applicability(): FieldApplicability = FieldApplicability(
    shapeVisible = form.allowsShape,
    timesVisible = scheduleMode == ScheduleMode.SCHEDULED,
    maxPerDayVisible = scheduleMode == ScheduleMode.PRN,
    foodRelationVisible = scheduleMode == ScheduleMode.SCHEDULED,
    endDateEnabled = endDate != null,
    stockThresholdEnabled = stockThresholdDays != null,
    doseUnit = doseUnit,
)

/**
 * Черновик без неприменимых значений.
 *
 * Неприменимое значение именно **сбрасывается**, а не прячется вместе с блоком:
 * спрятанный, но сохранённый вид таблетки уедет в базу и нарушит ИНВ-16 — причём
 * незаметно для человека, который видел, как блок исчез, и решил, что значения больше нет.
 *
 * Вызывается после каждого изменения черновика, а не только перед сохранением:
 * иначе между сменой формы выпуска и нажатием «Сохранить» состояние формы и состояние
 * черновика расходятся, и экран показывает одно, а сохраняет другое.
 */
fun MedicationDraft.normalized(): MedicationDraft = copy(
    shape = if (form.allowsShape) shape else null,
    times = if (scheduleMode == ScheduleMode.SCHEDULED) times else emptyList(),
    maxPerDay = if (scheduleMode == ScheduleMode.PRN) maxPerDay else null,
)
// Вид расписания здесь не трогается намеренно: при переключении на «по потребности» он
// **сохраняется** в черновике и возвращается при возвращении к «по расписанию» — та же
// конвенция, что у связи с едой (§5.10). В запись вид не уезжает: у курса «по потребности»
// вида нет (ИНВ-25, ПР-7), и сохранить Daily вместо него даёт [effectiveSchedule].

/**
 * Вид расписания, который уйдёт в запись.
 *
 * У курса «по потребности» вида нет вовсе (ИНВ-25, ПР-7): приёмы рождаются по нажатию,
 * и вид у такого курса не значит ничего. Неприменимое значение **сбрасывается здесь**,
 * а не в [normalized], чтобы собранное назначение переживало переключение режима в форме.
 */
val MedicationDraft.effectiveSchedule: ScheduleRule
    get() = if (scheduleMode == ScheduleMode.SCHEDULED) schedule else ScheduleRule.Daily

/**
 * Доза, которая уйдёт в запись и участвует в проверках.
 *
 * У ступенчатого вида дозу несёт этап (ПР-19, ИНВ-24), и поле «Доза на один приём»
 * не показывается вовсе (§5.10): доза курса — доза **первого** этапа. Её печатает
 * превью, ею считается списание, и ею же проверяется ИНВ-4 — отдельного поля, которое
 * человек не видит, быть не может. У остальных видов это введённая человеком доза.
 */
val MedicationDraft.effectiveDoseAmount: BigDecimal?
    get() = when {
        scheduleMode == ScheduleMode.SCHEDULED && schedule is ScheduleRule.Staged ->
            (schedule as ScheduleRule.Staged).stages.firstOrNull()?.doseAmount

        else -> doseAmount
    }

/**
 * Показывается ли поле «Доза на один приём» (§5.3, §5.10).
 *
 * У ступенчатого вида на «по расписанию» поле уступает место этапам; у «по потребности»
 * вид не действует (ИНВ-25), и доза нужна всегда.
 */
val MedicationDraft.dosePerIntakeVisible: Boolean
    get() = !(scheduleMode == ScheduleMode.SCHEDULED && schedule is ScheduleRule.Staged)
