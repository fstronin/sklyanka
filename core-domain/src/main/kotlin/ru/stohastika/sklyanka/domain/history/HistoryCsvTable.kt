package ru.stohastika.sklyanka.domain.history

import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.stock.StockLedger
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Статус записи в выгрузке истории (`docs/norm/04-behavior.md` §5.5, Р-85 п. 3).
 *
 * Ровно четыре исхода, и это не упрощение, а правило: **несостоявшихся записей в файле нет**.
 * Приём, время которого ещё не наступило, ни принят, ни пропущен — исход ему не приписан,
 * и строка о нём была бы утверждением о том, чего человек ещё не сделал.
 *
 * Слова статусов живут в каталоге строк, а не здесь (НФТ-42): домен называет исход,
 * а поверхность — его слова.
 */
enum class HistoryCsvStatus {

    /** Приём по расписанию отмечен принятым. */
    TAKEN,

    /** Запись «по потребности»: назначенного времени у неё нет (ПР-4). */
    TAKEN_ON_DEMAND,

    /** Пропуск, отмеченный человеком: он был и решил не принимать. */
    SKIPPED_BY_PERSON,

    /** Пропуск без отметки: приложение перевело его по истечении окна (ПР-1). */
    MISSED_WITHOUT_MARK,
}

/**
 * Строка выгрузки истории — значениями, а не словами (SKL-119).
 *
 * Числа, даты и дозы приходят из домена, слова — из каталога строк: колонки «Дата» или
 * «Вовремя» в шести языках пишет поверхность, а домен про языки не знает (НФТ-42, ADR-0007).
 * Поэтому строка и проверяется на JVM без Android: правила видны целиком, а вид — на стенде.
 *
 * [plannedTime] пусто у записи «по потребности»: назначенного времени у неё нет, и подставить
 * туда момент создания приёма значило бы назвать планом то, что планом не является (ПР-4).
 *
 * [onTime] заполнено **только у «принято»**: у пропуска сравнивать нечего, а у приёма
 * «по потребности» нет назначенного времени, с которым сравнивают (Р-21, Р-27).
 */
data class HistoryCsvRow(
    val date: LocalDate,
    val plannedTime: LocalTime?,
    val medication: String,
    /** Форма выпуска: ею называется единица дозы, если код единицы пуст или незнаком (МГ-6). */
    val form: MedicationForm,
    val dose: BigDecimal,
    val doseUnit: String,
    val strength: BigDecimal?,
    val strengthUnit: String?,
    val status: HistoryCsvStatus,
    /**
     * Момент отметки в поясе человека; `null` — отметки не было.
     *
     * У автоматического пропуска («пропущено — без отметки») ячейка **пуста**: `actedAt` такой
     * записи несёт момент перевода по истечении окна (ПР-1), а это не отметка человека — на экране
     * время отметки печатается только у «принято», и файл говорит то же, что экран.
     */
    val actedAt: LocalDateTime?,
    val onTime: Boolean?,
)

/**
 * Таблица выгрузки истории за период: профиль, период и строки (SKL-119).
 *
 * Один профиль — активный (ПРФ-23, ПР-10): строк другого профиля в таблице не бывает
 * по построению, а не по фильтру на выходе. Удалённое лекарство в неё не попадает (ПР-14),
 * снятая запись — тоже: и то и другое отсекает выборка истории.
 */
data class HistoryCsvTable(val profileName: String, val window: HistoryWindow, val rows: List<HistoryCsvRow>)

/**
 * Таблица выгрузки истории — тем же расчётом, что экран и отчёт (SKL-119, Р-85 п. 3).
 *
 * Профиль берётся из контекста активного профиля: запроса выгрузки чужого профиля
 * в продукте не существует — не «не используется», а отсутствует (ПР-10, ПРФ-23).
 * Приёмы — те же, что участвуют в картине периода (одно правило с приверженностью
 * и отчётом), включая архивированные курсы: архив придуман ровно для того, чтобы
 * завершённый курс остался в истории (ПР-11).
 */
class BuildHistoryCsvTableUseCase(
    private val activeProfile: ActiveProfileRepository,
    private val profiles: ProfileRepository,
    private val history: HistoryRepository,
    private val onTimeRule: OnTimeRule.Agreed,
    private val time: TimeSource,
) {

    /** @return таблица выгрузки или `null`, если активного профиля в базе нет (имя — часть файла). */
    suspend operator fun invoke(window: HistoryWindow): HistoryCsvTable? {
        val profileId = activeProfile.current()
        val profile = profiles.get(profileId) ?: return null
        val slice = history.observe(profileId, window).first()
        val zone = time.zone()
        val medications = slice.medications.associateBy { it.id }
        val rows = countedIntakes(slice, zone)
            .filter { it.status != IntakeStatus.PENDING && it.revokedAt == null }
            .mapNotNull { intake ->
                val medication = medications[intake.medicationId] ?: return@mapNotNull null
                // Файл обещает одно профиля и ни одной скрытой записи — сам, а не только
                // благодаря выборке. Выборка отсекает и удалённое лекарство (ПР-14), и чужой
                // профиль (ПРФ-23), но файл человек отдаёт из рук, и его обещание не имеет
                // права зависеть от того, кто именно его собрал.
                if (medication.isDeleted || medication.profileId != slice.profileId) return@mapNotNull null
                row(intake, medication, onTimeRule, zone)
            }
            .sortedWith(compareBy({ it.date }, { it.plannedTime ?: it.actedAt?.toLocalTime() }, { it.medication }))
        return HistoryCsvTable(profileName = profile.name, window = window, rows = rows)
    }

    private fun row(
        intake: Intake,
        medication: Medication,
        onTimeRule: OnTimeRule.Agreed,
        zone: ZoneId,
    ): HistoryCsvRow {
        val scheduled = medication.scheduleMode == ScheduleMode.SCHEDULED
        val status = statusOf(intake.status, scheduled)
        // Автоматический перевод в «пропущено» оставляет момент исхода, а не отметку человека:
        // в файле он не показывается — иначе колонка «Время отметки» противоречила бы своему
        // статусу «пропущено — без отметки» и экрану (IntakeCard печатает время только у «принято»).
        val actedAt = intake.actedAt
            ?.takeIf { intake.source != IntakeSource.AUTO }
            ?.atZone(zone)
            ?.toLocalDateTime()
        return HistoryCsvRow(
            date = intake.scheduledAt.toLocalDate(),
            plannedTime = intake.scheduledAt.toLocalTime().takeIf { scheduled },
            medication = medication.name,
            form = medication.form,
            // Та же доза, что списана с остатка и напечатана в отчёте (ПР-19): «снимок,
            // иначе доза лекарства». Второй формулировки у дозы быть не может.
            dose = StockLedger.chargedDose(intake.doseAmount, medication.doseAmount),
            doseUnit = medication.doseUnit,
            strength = medication.strength,
            strengthUnit = medication.strengthUnit,
            status = status,
            actedAt = actedAt,
            // Окно «вовремя» — то же правило продукта (Р-21): второго порога в файле нет.
            onTime = if (status == HistoryCsvStatus.TAKEN) {
                actedAt?.let { onTimeRule.isOnTime(intake.scheduledAt, it) }
            } else {
                null
            },
        )
    }

    /**
     * Исход записи словами каталога.
     *
     * «Принято по потребности» — не украшение: у такой записи нет назначенного времени,
     * и назвать её просто «принято» значило бы обещать врачу план, которого не было (ПР-4).
     */
    private fun statusOf(status: IntakeStatus, scheduled: Boolean): HistoryCsvStatus {
        if (status == IntakeStatus.TAKEN && !scheduled) {
            return HistoryCsvStatus.TAKEN_ON_DEMAND
        }
        return when (status) {
            IntakeStatus.TAKEN -> HistoryCsvStatus.TAKEN
            IntakeStatus.SKIPPED -> HistoryCsvStatus.SKIPPED_BY_PERSON
            else -> HistoryCsvStatus.MISSED_WITHOUT_MARK
        }
    }
}
