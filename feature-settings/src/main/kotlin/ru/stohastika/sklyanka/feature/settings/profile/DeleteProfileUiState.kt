package ru.stohastika.sklyanka.feature.settings.profile

import ru.stohastika.sklyanka.domain.profile.ProfileLosses
import java.time.Instant
import java.time.ZoneId

/**
 * Шаг экрана удаления профиля (ОВ-7, SKL-36).
 *
 * Перечисление, а не булев «удалено»: у шага два состояния и оба показываются
 * по-разному — подтверждение с числами и итог с кнопкой «Готово».
 */
enum class DeleteProfileStep {

    /** Подтверждение: числа потерь, предупреждение, слово и опасная кнопка. */
    CONFIRM,

    /** Итог: «Профиль удалён», «Активным стал ваш профиль». */
    RESULT,
}

/**
 * Отказ выгрузки файла переноса (НФТ-11: отказ не бывает тихим).
 *
 * Не отказ удаления: на этом шаге ничего ещё не стёрто. Отдельный тип, а не переносный
 * `TransferFailure`: тот говорит о загрузке файла, а здесь человек только сохраняет копию.
 */
sealed interface DeleteProfileFailure {

    /** Места не хватило — проверено до начала записи (ПЕР-31, та же выгрузка). */
    data object NoSpace : DeleteProfileFailure

    /** Запись сорвалась: готового файла в выбранном месте нет (ПЕР-32). */
    data object ExportFailed : DeleteProfileFailure

    /**
     * Системного выбора файлов на устройстве нет.
     *
     * Выбор места делает чужая активность, и на устройстве без неё запрос — отказ,
     * а не исключение: приложение, падающее по нажатию кнопки, теряет доверие целиком.
     */
    data object NoFilePicker : DeleteProfileFailure
}

/**
 * Состояние экрана удаления профиля.
 *
 * Живёт в ViewModel, как у экрана переноса: набранное слово и факт выгрузки обязаны
 * пережить поворот, а между сборками стенда приёмки `rememberSaveable` терялся бы.
 * Имя профиля сохраняется и после удаления: итог называет стёртый профиль, а активным
 * в этот момент уже стал владелец.
 */
data class DeleteProfileUiState(
    val step: DeleteProfileStep = DeleteProfileStep.CONFIRM,
    /** Идентификатор удаляемого профиля: активного и чужого (ОВ-7). */
    val profileId: String = "",
    val profileName: String = "",
    val losses: ProfileLosses = ProfileLosses(medications = 0, archived = 0, intakes = 0),
    val word: String = "",
    val exportFileName: String = "",
    /** Момент успешного сохранения файла; null — выгрузки ещё не было. */
    val exportedAt: Instant? = null,
    val failure: DeleteProfileFailure? = null,
    /** Идёт выгрузка или удаление: повторное нажатие в это время не проходит. */
    val busy: Boolean = false,
    /**
     * Пояс устройства: нужен, чтобы показать момент сохранения файла.
     *
     * Приходит из инжектируемых часов, а не из системных: прямые обращения к часам
     * запрещены и в экранах (НФТ-48).
     */
    val zone: ZoneId = ZoneId.of("UTC"),
)
