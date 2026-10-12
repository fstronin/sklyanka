package ru.stohastika.sklyanka.domain.reminder

import java.time.Duration
import java.time.Instant

/**
 * Окно планирования будильников: от «сейчас» на фиксированный горизонт (НАП-6, НАП-7).
 *
 * Почему окно, а не весь курс: будильники всё равно не переживают перезагрузку, система
 * ограничивает их число на приложение, а каждое редактирование расписания требует снять
 * и переставить всё затронутое. Длинный горизонт даёт не надёжность, а тысячи поставленных
 * срабатываний и класс ошибок «осиротевший будильник от приёма, которого больше нет»
 * (`docs/norm/04-behavior.md` §2).
 *
 * Границы — моменты (UTC), а не локальное время: будильник ставится на момент, и именно
 * поэтому смена часового пояса обязана пересобрать окно (НАП-11). Локальное время приёма
 * переводится в момент отбором, а не окном.
 */
data class ReminderWindow(val from: Instant, val toInclusive: Instant) {

    init {
        require(!toInclusive.isBefore(from)) { "reminder window must not end before it starts" }
    }

    /**
     * Попадает ли момент в окно.
     *
     * Обе границы включительные. Приём ровно на дальней границе планируется: исключать его
     * значило бы отдать его следующей пересборке, которой может не случиться, — а цена
     * ошибки здесь ровно одно пропущенное напоминание (НАП-1).
     */
    operator fun contains(moment: Instant): Boolean = !moment.isBefore(from) && !moment.isAfter(toInclusive)

    companion object {
        /** НАП-7: 48 часов. Обоснование размера — `docs/norm/04-behavior.md` §2. */
        val HORIZON: Duration = Duration.ofHours(48)

        fun startingAt(now: Instant): ReminderWindow = ReminderWindow(now, now.plus(HORIZON))
    }
}
