package ru.stohastika.sklyanka.domain.history

import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.YearMonth

/**
 * Состав отчёта врачу за выбранный период (`TDD.md §9.3`).
 *
 * Отчёт собирается из той же выборки и тем же расчётом, что экран: второй расчёт
 * «для отчёта» однажды разойдётся с первым, и разойдётся незаметно — сравнивать их
 * будет некому.
 *
 * Сети на этом пути нет ни в одном шаге: данные локальные, документ собирается в памяти,
 * передача идёт средствами системы (НФТ-14, НФТ-15).
 */
class BuildDoctorReportUseCase(
    private val activeProfile: ActiveProfileRepository,
    private val profiles: ProfileRepository,
    private val history: HistoryRepository,
    private val calculator: AdherenceCalculator,
    private val time: TimeSource,
    private val log: OperationLog,
) {

    /**
     * @return состав отчёта или `null`, если активного профиля в базе нет — такого
     *   состояния не бывает при работающем приложении (ИНВ-1), но собирать отчёт
     *   без профиля нельзя: имя профиля — часть документа.
     */
    suspend operator fun invoke(month: YearMonth): DoctorReport? {
        val profileId = activeProfile.current()
        val profile = profiles.get(profileId) ?: return null
        val window = HistoryWindow.of(month)
        val slice = history.observe(profileId, window).first()
        val period = calculator.of(slice, window, time.today(), time.zone())
        val report = DoctorReportBuilder.build(
            period = period,
            slice = slice,
            profileName = profile.name,
            generatedAt = time.now(),
            zone = time.zone(),
        )
        // В журнал — период и числа состава, а не содержимое: названий лекарств и имён
        // профилей в журнале нет (НФТ-19), и агрегированных чисел приверженности тоже.
        log.record(
            event = OpEvent.DOCTOR_REPORT_BUILT,
            profileId = profileId,
            after = window.logDetail(),
            detail = "records=${report.intakeCount};medications=${report.medications.size}",
        )
        return report
    }
}

/**
 * Передача готового отчёта средствами системы с записью исхода в журнал.
 *
 * Отдельно от сборки намеренно: сборка — чистый расчёт, передача — единственный шаг,
 * который может не состояться по причинам вне приложения. Жалоба «нажал, и ничего
 * не произошло» разбирается по исходу, а он без журнала не сохраняется нигде.
 */
class TransferDoctorReportUseCase(
    private val transfer: DoctorReportTransfer,
    private val activeProfile: ActiveProfileRepository,
    private val log: OperationLog,
) {

    suspend fun share(document: ReportDocument): ReportTransferOutcome = record(transfer.share(document), document)

    suspend fun save(document: ReportDocument, target: ReportTarget): ReportTransferOutcome =
        record(transfer.save(document, target), document)

    /**
     * Человек закрыл системный выбор места, не выбрав его.
     *
     * След остаётся и здесь, хотя приложение не сделало ничего: без записи «отчёт
     * не сохранён» неотличимо от «сохранение сорвалось», а человеку эти два случая
     * выглядят одинаково (`TDD.md §11`).
     */
    suspend fun cancelled(document: ReportDocument): ReportTransferOutcome =
        record(ReportTransferOutcome.Cancelled, document)

    /**
     * Сохранить отчёт некому: в устройстве нет обработчика системного выбора места.
     *
     * Исход — отказ, а не отмена: системный диалог не открывался вовсе, и человеку
     * говорится то же, что при сорванной записи, — «отчёт не сохранён». След в журнале
     * остаётся по той же причине, что у отмены: без него этот исход неотличим от
     * «человек не дошёл до кнопки» (`TDD.md §11`).
     */
    suspend fun saveUnavailable(document: ReportDocument): ReportTransferOutcome =
        record(ReportTransferOutcome.Failed(NO_SAVE_HANDLER), document)

    private suspend fun record(outcome: ReportTransferOutcome, document: ReportDocument): ReportTransferOutcome {
        val detail = when (outcome) {
            ReportTransferOutcome.Shared -> "shared"
            ReportTransferOutcome.Saved -> "saved"
            ReportTransferOutcome.Cancelled -> "cancelled"
            is ReportTransferOutcome.Failed -> "failed:${outcome.reason}"
        }
        log.record(
            event = OpEvent.DOCTOR_REPORT_TRANSFERRED,
            profileId = activeProfile.current(),
            // Размер вместо содержимого: документ целиком — это медицинские данные.
            detail = "$detail;bytes=${document.sizeBytes}",
        )
        return outcome
    }
}

/**
 * Причина отказа в журнале: обработчика `CREATE_DOCUMENT` в устройстве нет.
 *
 * Строка называет платформенный факт, а не решение продукта: системный диалог
 * не открывался, и запускать его было нечем (SKL-46).
 */
private const val NO_SAVE_HANDLER = "no_activity_for_create_document"
