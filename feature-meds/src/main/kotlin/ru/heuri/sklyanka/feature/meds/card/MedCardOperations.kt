package ru.heuri.sklyanka.feature.meds.card

import ru.heuri.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.heuri.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.heuri.sklyanka.domain.medication.ToggleWatchMirrorUseCase
import ru.heuri.sklyanka.domain.stock.RefillStockUseCase
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
)
