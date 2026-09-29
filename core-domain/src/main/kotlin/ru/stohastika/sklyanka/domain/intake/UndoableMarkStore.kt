package ru.stohastika.sklyanka.domain.intake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/**
 * Последнее действие человека, которое ещё можно отменить (SKL-30).
 *
 * Имена лекарств нужны только тексту снэкбара: у действия из нескольких приёмов текст
 * счётный, и имена не читаются. В базу они не попадают — магазин живёт в памяти
 * и умирает вместе с процессом: после перезапуска отменять нечего.
 */
data class UndoableMark(
    val intakeIds: List<String>,
    /** Исход **записанной** отметки: текст обязан говорить то же, что карточка. */
    val result: MarkResult,
    val medicationNames: List<String>,
    val expiresAt: Instant,
)

/**
 * Носитель «последнего действия» — одна ячейка между карточкой напоминания и «Сегодня».
 *
 * Живёт в `core-domain`, потому что `feature-today` и `feature-reminder` друг от друга
 * не зависят (ADR-0007), а видят оба только домен. Чистый Kotlin поверх [StateFlow]
 * и [TimeSource]: без Android и без Hilt — провайдер собирается в `app`.
 *
 * Пишут ровно две точки: «Сегодня» после состоявшейся отметки и карточка напоминания,
 * когда её закрыло действие. Вторая публикация заменяет первую: ячейка одна, очереди нет.
 */
class UndoableMarkStore(private val time: TimeSource) {

    private val cell = MutableStateFlow<UndoableMark?>(null)

    val current: StateFlow<UndoableMark?> = cell.asStateFlow()

    /** Открыть окно отмены на [WINDOW] от сейчас. */
    fun publish(intakeIds: List<String>, result: MarkResult, medicationNames: List<String>) {
        cell.value = UndoableMark(
            intakeIds = intakeIds,
            result = result,
            medicationNames = medicationNames,
            expiresAt = time.now().plus(WINDOW),
        )
    }

    /** Последнее действие, если его срок ещё не истёк; иначе `null`. */
    fun live(): UndoableMark? = cell.value?.takeIf { time.now().isBefore(it.expiresAt) }

    /** Конец окна: уход с «Сегодня» или нажатие «Отменить». */
    fun clear() {
        cell.value = null
    }

    /**
     * Истечение срока отметки [mark]: не стирает замену, опубликованную позже.
     *
     * Таймер прошлого действия не имеет права погасить окно нового — сравнение идёт
     * с той самой ячейкой, которую он когда-то показывал.
     */
    fun clearIf(mark: UndoableMark) {
        if (cell.value == mark) cell.value = null
    }

    companion object {
        /** Середина коридора 4–6 секунд: человек 60+ успевает заметить (ОВ-81/ОВ-6). */
        val WINDOW: Duration = Duration.ofSeconds(5)
    }
}
