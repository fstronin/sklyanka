package ru.heuri.sklyanka.domain.transfer

import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.Profile

/**
 * Всё состояние устройства одним значением.
 *
 * Читается один раз перед построением плана и больше не перечитывается: решения
 * принимаются по снимку состояния, а не по базе, к которой по ходу кто-то может
 * дописать строку (ПЕР-27).
 */
data class DeviceState(val profiles: List<Profile>, val medications: List<Medication>, val intakes: List<Intake>) {
    val counts: TransferCounts get() = TransferCounts.of(profiles, medications, intakes)

    companion object {
        val EMPTY = DeviceState(emptyList(), emptyList(), emptyList())
    }
}
