package ru.stohastika.sklyanka.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.schedule.PlanReason

/**
 * Соответствие причин перепланирования будильников причинам генерации приёмов (НАП-11).
 *
 * Проверяется на JVM, потому что цена ошибки здесь невидима на устройстве: забытая причина
 * не ломает ничего сразу, а окно приёмов просто перестаёт пополняться — и напоминания
 * кончаются через неделю, когда разбираться будет уже не по чему.
 */
class ReminderPlanReasonsTest {

    @Test
    fun `every reason either maps to a generation reason or is explicitly excluded`() {
        val excluded = setOf(
            RescheduleReason.ALARM_FIRED,
            RescheduleReason.PROFILE_SET_CHANGED,
            RescheduleReason.EXACT_ALARM_GRANTED,
            RescheduleReason.LOCKED_BOOT,
        )
        RescheduleReason.entries.forEach { reason ->
            val mapped = ReminderPlanReasons.intakeReason(reason)
            if (reason in excluded) {
                assertNull("$reason must not generate intakes", mapped)
            } else {
                assertNotNull("$reason must have a generation reason", mapped)
            }
        }
    }

    @Test
    fun `a fired alarm does not start a pass over all profiles`() {
        // Окно приёмов на семь суток вперёд уже есть; повторный проход в приёмнике стоит
        // времени, которого у приёмника нет (НАП-13), а очередь будильников пополняет
        // сама пересборка окна (НФТ-7).
        assertNull(ReminderPlanReasons.intakeReason(RescheduleReason.ALARM_FIRED))
    }

    @Test
    fun `granting the exact alarm right generates no intakes`() {
        // Выдача права меняет только очередь будильников; окно приёмов уже сгенерировано,
        // и лишний проход по всем профилям в приёмнике стоит времени, которого у него нет.
        assertNull(ReminderPlanReasons.intakeReason(RescheduleReason.EXACT_ALARM_GRANTED))
    }

    @Test
    fun `unlocking regenerates intakes like a boot and locked boot generates nothing`() {
        // Разблокировка — та же генерация, что после перезагрузки (устройство могло быть
        // выключено дольше горизонта); до разблокировки работает только путь по снимку.
        assertEquals(PlanReason.BOOT, ReminderPlanReasons.intakeReason(RescheduleReason.USER_UNLOCKED))
        assertNull(ReminderPlanReasons.intakeReason(RescheduleReason.LOCKED_BOOT))
    }

    @Test
    fun `events of the schedule map to the generation reasons of SKL-06`() {
        assertEquals(PlanReason.BOOT, ReminderPlanReasons.intakeReason(RescheduleReason.BOOT))
        assertEquals(PlanReason.TIMEZONE_CHANGED, ReminderPlanReasons.intakeReason(RescheduleReason.TIMEZONE_CHANGED))
        assertEquals(PlanReason.TIME_CHANGED, ReminderPlanReasons.intakeReason(RescheduleReason.TIME_CHANGED))
        assertEquals(PlanReason.MEDICATION_SAVED, ReminderPlanReasons.intakeReason(RescheduleReason.SCHEDULE_EDITED))
        assertEquals(PlanReason.MEDICATION_ADDED, ReminderPlanReasons.intakeReason(RescheduleReason.MEDICATION_ADDED))
        assertEquals(
            PlanReason.MEDICATION_ARCHIVED,
            ReminderPlanReasons.intakeReason(RescheduleReason.MEDICATION_ARCHIVED),
        )
        assertEquals(
            PlanReason.MEDICATION_RESTORED,
            ReminderPlanReasons.intakeReason(RescheduleReason.MEDICATION_UNARCHIVED),
        )
        // SKL-99: удаление и возврат удаления — такие же события перепланирования (НАП-11, ПМ-24).
        assertEquals(
            PlanReason.MEDICATION_DELETED,
            ReminderPlanReasons.intakeReason(RescheduleReason.MEDICATION_DELETED),
        )
        assertEquals(
            PlanReason.MEDICATION_UNDELETED,
            ReminderPlanReasons.intakeReason(RescheduleReason.MEDICATION_UNDELETED),
        )
        assertEquals(PlanReason.ANCHOR_ALARM, ReminderPlanReasons.intakeReason(RescheduleReason.ANCHOR))
        assertEquals(PlanReason.MANUAL_CHECK, ReminderPlanReasons.intakeReason(RescheduleReason.MANUAL))
        assertEquals(PlanReason.DATA_IMPORTED, ReminderPlanReasons.intakeReason(RescheduleReason.DATA_IMPORTED))
        assertEquals(PlanReason.APP_START, ReminderPlanReasons.intakeReason(RescheduleReason.APP_START))
    }

    @Test
    fun `changes of one medication map back to the reminder reasons of NAP-11`() {
        assertEquals(
            RescheduleReason.MEDICATION_ADDED,
            ReminderPlanReasons.rescheduleReason(PlanReason.MEDICATION_ADDED),
        )
        assertEquals(
            RescheduleReason.SCHEDULE_EDITED,
            ReminderPlanReasons.rescheduleReason(PlanReason.MEDICATION_SAVED),
        )
        assertEquals(
            RescheduleReason.MEDICATION_ARCHIVED,
            ReminderPlanReasons.rescheduleReason(PlanReason.MEDICATION_ARCHIVED),
        )
        assertEquals(
            RescheduleReason.MEDICATION_UNARCHIVED,
            ReminderPlanReasons.rescheduleReason(PlanReason.MEDICATION_RESTORED),
        )
        assertEquals(
            RescheduleReason.MEDICATION_DELETED,
            ReminderPlanReasons.rescheduleReason(PlanReason.MEDICATION_DELETED),
        )
        assertEquals(
            RescheduleReason.MEDICATION_UNDELETED,
            ReminderPlanReasons.rescheduleReason(PlanReason.MEDICATION_UNDELETED),
        )
    }

    @Test
    fun `a generation reason that does not change one medication refuses loudly`() {
        // Через порт перепланирования одного лекарства проходят только причины изменения
        // одного лекарства; всё остальное — ошибка программиста, и молчаливая подмена
        // причины запрещена (НАП-14): журнал напоминаний по ней разбирают, почему
        // напоминание не пришло.
        for (reason in setOf(
            PlanReason.APP_START,
            PlanReason.BOOT,
            PlanReason.DATA_IMPORTED,
            PlanReason.MANUAL_CHECK,
        )) {
            assertThrows(IllegalStateException::class.java) {
                ReminderPlanReasons.rescheduleReason(reason)
            }
        }
    }
}
