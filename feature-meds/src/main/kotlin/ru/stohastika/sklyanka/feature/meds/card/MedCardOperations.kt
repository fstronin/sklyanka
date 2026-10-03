package ru.stohastika.sklyanka.feature.meds.card

import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.DeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ToggleWatchMirrorUseCase
import ru.stohastika.sklyanka.domain.stock.RefillStockUseCase
import javax.inject.Inject

/**
 * Четыре операции карточки лекарства одним значением.
 *
 * Не «обёртка ради краткости»: это полный список того, что карточка умеет менять —
 * архивация, возврат из архива, тумблер часов и пополнение остатка. Собранные в одном
 * типе, они читаются списком, и пропуск новой операции в графе становится заметным;
 * четыре отдельных параметра в конструкторе ViewModel путают местами уже при чтении.
 *
 * Сами сценарии живут в домене и об Hilt не знают (НФТ-46) — здесь только их состав.
 */
class MedCardOperations @Inject constructor(
    val archive: ArchiveMedicationUseCase,
    val restore: RestoreMedicationUseCase,
    val watchMirror: ToggleWatchMirrorUseCase,
    val refillStock: RefillStockUseCase,
    /** Мягкое удаление ошибочно заведённой записи (SKL-99, ПМ-24). */
    val delete: DeleteMedicationUseCase,
)
