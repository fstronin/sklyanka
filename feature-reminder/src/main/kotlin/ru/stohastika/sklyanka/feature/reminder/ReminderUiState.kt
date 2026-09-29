package ru.stohastika.sklyanka.feature.reminder

import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Что показывает полноэкранное напоминание.
 *
 * [Closed] — не «пусто», а «отмечать больше нечего»: приём отмечен с часов или с экрана
 * «Сегодня», пока карточка была открыта, и она обязана закрыться сама (НАП-32).
 */
sealed interface ReminderUiState {

    data object Loading : ReminderUiState

    data class Card(
        val scheduledAt: LocalDateTime,
        val items: List<ReminderRow>,
        val snoozeMinutes: Int,
        val mirroredToWatch: Boolean,
        /** Имя чужого профиля; у владельца пусто — индикации нет, потому что нечего (ПРФ-16). */
        val foreignProfileName: String?,
    ) : ReminderUiState

    data object Closed : ReminderUiState
}

/** Одна строка карточки: лекарство, доза и дозировка. */
data class ReminderRow(
    val intakeId: String,
    val name: String,
    val form: MedicationForm,
    val doseAmount: BigDecimal,
    val strength: BigDecimal?,
    val strengthUnit: String?,
)

/**
 * Действия карточки напоминания (НАП-30).
 *
 * Собраны в один тип, а не переданы шестью параметрами: строковые и общие действия
 * различаются только областью применения, и путать их местами при вызове — ровно та
 * ошибка, которую не видно ни на экране, ни в тесте вёрстки.
 */
data class ReminderActions(
    /** «Принято» на строке общей карточки — ровно один приём (НАП-36). */
    val onTaken: (String) -> Unit,
    /** «Пропустить» на строке общей карточки. */
    val onSkipped: (String) -> Unit,
    /** «Принято» внизу карточки — все неотмеченные приёмы слота. */
    val onTakenAll: () -> Unit,
    val onSkippedAll: () -> Unit,
    /** Откладывание переносит напоминание всего слота: карточка одна (НАП-30). */
    val onSnooze: () -> Unit,
)

/**
 * Карточка домена в состояние экрана.
 *
 * Отдельная функция, а не метод ViewModel: перевод — чистое правило, и проверяется он
 * на JVM. Имя чужого профиля появляется только у чужого профиля: у владельца индикации
 * нет, потому что показывать нечего (ПРФ-16).
 */
internal fun ReminderCard.toUiState(snoozeMinutes: Int): ReminderUiState.Card = ReminderUiState.Card(
    scheduledAt = scheduledAt,
    items = items.map { item ->
        ReminderRow(
            intakeId = item.intakeId,
            name = item.medicationName,
            form = item.form,
            doseAmount = item.doseAmount,
            strength = item.strength,
            strengthUnit = item.strengthUnit,
        )
    },
    snoozeMinutes = snoozeMinutes,
    mirroredToWatch = mirroredToWatch,
    foreignProfileName = profileName.takeIf { !profileIsSelf },
)
