package ru.stohastika.sklyanka.domain.reminder.port

import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderIntake
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import java.time.LocalDateTime

/**
 * Данные, нужные планировщику напоминаний.
 *
 * Отдельный порт, а не метод `IntakeRepository`: тот запрещает выборку приёмов нескольких
 * профилей сразу (ПР-10), и запрет верен для экранов. Планировщику нужна ровно такая
 * выборка — набор будильников не зависит от активного профиля и складывается из приёмов
 * своего профиля и профилей с включённым дублированием (НАП-12, ПРФ-6). Право на
 * межпрофильное чтение объявлено здесь явно, вместе с причиной, а не просочилось
 * в общий интерфейс приёмов.
 *
 * Отбор при этом остаётся в домене: порт отдаёт **кандидатов** с признаками, а пять условий
 * проверяет `WindowIntakeSelector` — иначе правило переехало бы в SQL и перестало
 * проверяться на JVM.
 */
interface ReminderPlanSource {

    /**
     * Ожидающиеся приёмы по расписанию, назначенные в пределах локального интервала.
     *
     * Границы — локальное время (`scheduled_at` хранится локальным, НФТ-34), обе
     * включительные: приём ровно на границе окна планируется.
     */
    suspend fun candidates(from: LocalDateTime, toInclusive: LocalDateTime): List<ReminderIntake>

    suspend fun intake(intakeId: String): ReminderIntake?

    /** Ожидающиеся приёмы того же слота — строки общей карточки (НАП-36). */
    suspend fun pendingInSlot(slot: ReminderSlot): List<ReminderIntake>
}

/**
 * Содержимое карточки напоминания.
 *
 * Отделено от [ReminderPlanSource] намеренно: здесь появляются название лекарства
 * и дозировка, то есть медицинские данные. Планировщик и журнал этого порта не видят —
 * запрет НФТ-19 держится составом зависимостей, а не внимательностью (`TDD.md §11.2`).
 */
interface ReminderCardSource {

    suspend fun card(slot: ReminderSlot): ReminderCard?
}
