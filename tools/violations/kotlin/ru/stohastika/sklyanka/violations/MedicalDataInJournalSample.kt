package ru.stohastika.sklyanka.violations

// Нарушает НФТ-19: в журнал уходит название лекарства и его дозировка.
// Правило — MedicalDataInJournal.

class MedicalDataInJournalSample(private val medication: Medication) {

    fun writeIntake(log: Log) {
        log.record(event = "INTAKE_MARKED", medicationId = medication.name, detail = medication.strengthUnit)
    }

    class Medication(val name: String, val strengthUnit: String)

    interface Log {
        fun record(event: String, medicationId: String, detail: String)
    }
}
