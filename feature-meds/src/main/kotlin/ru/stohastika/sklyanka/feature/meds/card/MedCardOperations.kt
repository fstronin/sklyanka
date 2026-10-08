package ru.stohastika.sklyanka.feature.meds.card

import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.stock.RefillStockUseCase
import javax.inject.Inject

/**
 * Операции карточки лекарства одним значением.
 *
 * Не «обёртка ради краткости»: это полный список того, что карточка умеет менять —
 * архивация, возврат из архива, пополнение остатка и мягкое удаление. Собранные в одном
 * типе, они читаются списком, и пропуск новой операции в графе становится заметным;
 * отдельные параметры в конструкторе ViewModel путают местами уже при чтении.
 *
 * Сами сценарии живут в домене и об Hilt не знают (НФТ-46) — здесь только их состав.
 */
class MedCardOperations @Inject constructor(
    val archive: ArchiveMedicationUseCase,
    val restore: RestoreMedicationUseCase,
    val refillStock: RefillStockUseCase,
    /** Мягкое удаление ошибочно заведённой записи (SKL-99, ПМ-24). */
    val delete: DeleteMedicationUseCase,
)
