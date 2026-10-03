package ru.stohastika.sklyanka.domain.undo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/**
 * Последнее действие человека, которое ещё можно отменить.
 *
 * Одна ячейка на всё приложение (ОВ-124, ОВ-105, SKL-30/43): новое действие с отменой
 * заменяет текущее сообщение, окна не выстраиваются в очередь. Имена лекарств нужны
 * только тексту снэкбара; в базу они не попадают — магазин живёт в памяти и умирает
 * вместе с процессом: после перезапуска отменять нечего.
 */
sealed interface UndoableAction {
    /** Конец окна отмены: после него действие необратимо через интерфейс. */
    val expiresAt: Instant
}

/** Последняя отметка приёма, которую ещё можно отменить (SKL-30). */
data class UndoableMark(
    val intakeIds: List<String>,
    /** Исход **записанной** отметки: текст обязан говорить то же, что карточка. */
    val result: MarkResult,
    /** Имена лекарств для текста; у отметки группы текст счётный, имена не читаются. */
    val medicationNames: List<String>,
    override val expiresAt: Instant,
) : UndoableAction

/** Последнее мягкое удаление лекарства, которое ещё можно вернуть (ПМ-24, ОВ-124). */
data class UndoableDeletion(
    val medicationId: String,
    /** Имя для текста снэкбара «%1$s — лекарство удалено». */
    val medicationName: String,
    override val expiresAt: Instant,
) : UndoableAction

/**
 * Носитель «последнего действия» — одна ячейка между карточкой напоминания, «Сегодня»,
 * «Лекарствами» и «Архивом».
 *
 * Живёт в `core-domain`, потому что экранные модули друг от друга не зависят (ADR-0007),
 * а видят все только домен. Чистый Kotlin поверх [StateFlow] и [TimeSource]: без Android
 * и без Hilt — провайдер собирается в `app`.
 *
 * Пишут три точки: «Сегодня» и карточка напоминания — отметку, карточка лекарства —
 * удаление. Каждая новая публикация заменяет предыдущую: ячейка одна, очереди нет.
 */
class UndoableActionStore(private val time: TimeSource) {

    private val cell = MutableStateFlow<UndoableAction?>(null)

    val current: StateFlow<UndoableAction?> = cell.asStateFlow()

    /** Открыть окно отмены отметки на [WINDOW] от сейчас. */
    fun publishMark(intakeIds: List<String>, result: MarkResult, medicationNames: List<String>) {
        cell.value = UndoableMark(
            intakeIds = intakeIds,
            result = result,
            medicationNames = medicationNames,
            expiresAt = time.now().plus(WINDOW),
        )
    }

    /** Открыть окно отмены удаления на [WINDOW] от сейчас (ОВ-124). */
    fun publishDeletion(medicationId: String, medicationName: String) {
        cell.value = UndoableDeletion(
            medicationId = medicationId,
            medicationName = medicationName,
            expiresAt = time.now().plus(WINDOW),
        )
    }

    /** Последнее действие, если его срок ещё не истёк; иначе `null`. */
    fun live(): UndoableAction? = cell.value?.takeIf { time.now().isBefore(it.expiresAt) }

    /** Конец окна: уход с экрана, истечение срока или нажатие «Отменить». */
    fun clear() {
        cell.value = null
    }

    /**
     * Истечение срока действия [action]: не стирает замену, опубликованную позже.
     *
     * Таймер прошлого действия не имеет права погасить окно нового — сравнение идёт
     * с той самой ячейкой, которую он когда-то показывал.
     */
    fun clearIf(action: UndoableAction) {
        if (cell.value == action) cell.value = null
    }

    companion object {
        /** Середина коридора 4–6 секунд: человек 60+ успевает заметить (ОВ-81/ОВ-6). */
        val WINDOW: Duration = Duration.ofSeconds(5)
    }
}
