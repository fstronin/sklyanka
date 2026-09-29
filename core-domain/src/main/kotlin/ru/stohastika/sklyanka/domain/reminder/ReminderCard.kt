package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.model.MedicationForm
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Содержимое полноэкранного напоминания (НАП-29, макет `design/screens/Reminder.dc.html`).
 *
 * Тип существует отдельно от [ReminderIntake] и в журнал не передаётся ни разу: здесь есть
 * название лекарства и дозировка, а журнал показывается на экране и выгружается файлом
 * (НФТ-19, НФТ-51). Граница между «что планируем» и «что показываем» — не стиль, а способ
 * выполнить запрет на медицинские данные в журнале конструкцией.
 *
 * Готовых строк здесь нет: «2 таблетки · 48 мг» собирается экраном из ресурсов
 * с числительными (НФТ-42, НФТ-44).
 */
data class ReminderCard(
    val slot: ReminderSlot,
    val scheduledAt: LocalDateTime,
    val items: List<ReminderCardItem>,
    /** Карточка ушла на часы — чип «Продублировано на часы» (ЧАС-13, реализация SKL-16). */
    val mirroredToWatch: Boolean,
    /** Профиль владельца: у чужого профиля карточка несёт индикацию (ПРФ-8, SKL-15). */
    val profileIsSelf: Boolean,
    val profileName: String,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/** Одна строка карточки: лекарство, доза и дозировка. */
data class ReminderCardItem(
    val intakeId: String,
    val medicationId: String,
    val medicationName: String,
    val form: MedicationForm,
    val doseAmount: BigDecimal,
    val doseUnit: String,
    val strength: BigDecimal?,
    val strengthUnit: String?,
)
