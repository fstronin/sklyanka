package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_CAPTION
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_CARD
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_EMPTY
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_NOTICE
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_RESTORE
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_SCREEN
import ru.stohastika.sklyanka.feature.meds.archive.TAG_GROUP_FINISHED
import ru.stohastika.sklyanka.feature.meds.archive.TAG_GROUP_MANUAL
import ru.stohastika.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_HINT
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET
import ru.stohastika.sklyanka.feature.meds.card.TAG_FINISHED_OFFER
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_FIELD
import java.time.LocalDate
import ru.stohastika.sklyanka.feature.meds.R as MedsR

/**
 * Поход «Архив, удаление и возврат» (SKL-140, реестр походов № 5).
 *
 * Три фазы одного пути человека:
 *
 * 1. «Убрано вручную»: курс «Магний B6» уходит в архив кнопкой «В архив» — на «Сегодня» приёма
 *    по нему нет, план на будущее снят, в журнале один след архивации; возврат восстанавливает
 *    расписание от текущей даты и не переписывает вчерашнюю историю (ПР-11, НАП-31).
 * 2. «Курс завершён»: курс, дошедший до даты окончания, уходит в архив подтверждением
 *    предложения «Курс закончился» — карточка несёт итог курса («принято N из M», ЭКР-8).
 *    Возврат такого курса отказан, и отказ назван словами «Период приёма закончился…»: курс
 *    остаётся в архиве, молчаливого возврата не происходит (ОВ-14).
 * 3. Мягкое удаление: «Витамин D» удаляется с подтверждением словом — лекарство скрыто
 *    в списке, на «Сегодня» и в архиве, мягкость видна по журналу (ПМ-24). А у курса с отметкой
 *    «принято» действия удаления нет вовсе (ИНВ-22).
 *
 * «Напоминаний по архивному лекарству не поставлено» держится снятием плана и записью
 * `MEDICATION_ARCHIVED`: будильники ставятся на существующие приёмы ближайших 48 часов,
 * а у архивного курса приёмов не осталось. Зеркало будильников и настоящее срабатывание
 * стенду недоступны: право точных будильников объявлено у продукта, а не у тестового пакета,
 * поэтому `AlarmManager` отказывает («lost permission to set exact alarms!») и зеркало пусто
 * всегда; право тестовому пакету не выдаётся намеренно — будильники стенда стоят в прошлом
 * для часов устройства, и система отдала бы их чужому походу. Это испытания И-1…И-16
 * и ручной прогон на устройстве.
 *
 * Сохранность строк приёмов удалённого лекарства (ПР-14) держит проверка слоя данных —
 * `MedicationDeletionTest.theIntakeRowsSurviveTheDeletion` в `core-data/src/androidTest`
 * («PR-14: the intake row must stay»). Поход утверждает скрытость на экранах и мягкость
 * удаления по журналу: выборки дня и периода отсекают удалённое лекарство (`deleted_at IS NULL`),
 * а чтения его приёмов в стенде нет — уровни разведены, а не продублированы.
 *
 * **Названный дефект продукта (поход красный до правки, Р-58).** Фазы 2 и 3 открывают карточку
 * после того, как предыдущая была закрыта архивацией. `MedicationCardViewModel` живёт на
 * активности (`MainActivity.medCard`) и хранит `closed` прошлой карточки до конца сеанса,
 * а `MedicationCardRoute` читает `state` раньше, чем эффект `open()` сбросит флаг: `collectAsState`
 * отдаёт последнее значение `StateFlow`, и по устаревшему `closed = true` маршрут закрывает только
 * что открытую карточку. Человек видит мигание и снова список; второе касание открывает карточку
 * (устаревший флаг к тому моменту сброшен). Тот же класс дефекта уже ловили и чинили у экрана
 * разрешений (`PermissionsRoute.start()`, SKL-40: «устаревший исход прошлого ухода не должен
 * закрывать экран»). Здесь утверждение написано по норме §5.3 «Лекарства, карточка, архив, форма»
 * (`docs/v2/10-behavior.md`) — касание строки списка открывает карточку, — и потому красное:
 * поход не подстраивается под текущее поведение продукта.
 *
 * Сценарий приёмки называет курсы «Магний B6» и «Витамин D»; на образе их играют нейтральные
 * имена стенда — `device_test_medication_archive` и `device_test_medication_deletion`,
 * а завершённый курс — имя стенда с ролью. Русский текст живёт в ресурсах (НФТ-42).
 */
@HiltAndroidTest
class ArchiveAndDeletionJourneyTest : JourneyTest() {

    private val archivedName: String get() = testContext.getString(R.string.device_test_medication_archive)

    private val deletedName: String get() = testContext.getString(R.string.device_test_medication_deletion)

    private val finishedName: String get() = testContext.getString(R.string.device_test_medication_name, FINISHED_ROLE)

    @Test
    fun thePersonArchivesRestoresAndDeletesAMedication() {
        val historyDay = DeviceTestClock.today.minusDays(HISTORY_OFFSET_DAYS)
        val history = seedCoursesWithHistory(historyDay)

        launchApp()
        awaitRoot()
        openMedications()
        assertTrue(
            "курс обязан быть запланирован на будущее до архивации",
            intakeIds(ARCHIVED_ID, FUTURE_DAYS).isNotEmpty(),
        )

        archiveManuallyAndReturn(historyDay, history)
        archiveFinishedCourseAndRefuseTheReturn()
        deleteTheSecondMedication()
    }

    /**
     * Сид похода: ручной курс, завершённый курс и удаляемый — с настоящей историей отметок.
     *
     * История ставится «вчерашним» моментом: окно генерации начинается с текущей даты, и задним
     * числом приёмы не порождаются (НАП-31), поэтому без сдвига часов вчерашней записи не было бы
     * вовсе. Затем часы возвращаются к базовому моменту стенда, и поход идёт сегодняшним днём.
     *
     * @return вчерашний приём ручного курса: по нему проверяется «история не переписана».
     */
    private fun seedCoursesWithHistory(historyDay: LocalDate): Intake {
        val finishedStart = historyDay.minusDays(1)
        DeviceTestClock.advanceTo(finishedStart.atTime(DeviceTestClock.scheduledTime))
        // Завершённый курс: дата окончания вчера, и принят только первый его приём —
        // «принято 1 из 2» отличает итог курса от подписи-даты ручного архива (ЭКР-8).
        seedMedication(
            id = FINISHED_ID,
            name = finishedName,
            times = listOf(DeviceTestClock.scheduledTime),
            startDate = finishedStart,
            endDate = historyDay,
        )
        seedMedication(id = ARCHIVED_ID, name = archivedName, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()
        markTaken(FINISHED_ID, finishedStart)

        DeviceTestClock.advanceTo(historyDay.atTime(DeviceTestClock.scheduledTime))
        val history = markTaken(ARCHIVED_ID, historyDay)
        DeviceTestClock.reset()

        seedMedication(id = DELETED_ID, name = deletedName, times = listOf(DeviceTestClock.scheduledTime))
        return history
    }

    /**
     * Фаза «убрано вручную»: архив, пропавший с «Сегодня» приём, снятый план — и возврат,
     * который восстанавливает расписание от текущей даты, не переписывая историю.
     */
    private fun archiveManuallyAndReturn(historyDay: LocalDate, history: Intake) {
        openMedicationCard(archivedName)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        capture("med-card-active")

        click(TAG_ARCHIVE)
        waitForTag(TAG_MEDICATION_LIST)
        waitUntil(SCREEN_TIMEOUT_MS, "списка без архивного лекарства") { !textExists(archivedName) }
        capture("meds-after-archive")
        assertPlanWasShed()

        todayWithout(archivedName, "today-without-archived")

        openMedications()
        openArchive(archiveCount = 1)
        waitForTag(TAG_ARCHIVE_CARD)
        waitForTag(TAG_GROUP_MANUAL)
        waitForTag(TAG_ARCHIVE_CAPTION)
        // Подпись ручного архива — дата архивации, а не итог курса: подпись определяется
        // причиной архивации (ЭКР-8), и поход ручного пути читает её как «в архиве с».
        val caption = nodeText(TAG_ARCHIVE_CAPTION)
        assertTrue(
            "подпись ручного архива обязана называть дату архивации: $caption",
            caption.startsWith(captionSincePrefix()),
        )
        capture("archive-card")

        click(TAG_ARCHIVE_RESTORE)
        waitForTag(TAG_ARCHIVE_EMPTY)
        capture("archive-empty-after-restore")

        openActiveMedications(activeCount = 3)
        waitUntil(SCREEN_TIMEOUT_MS, "восстановленного расписания") {
            intakeIds(ARCHIVED_ID, FUTURE_DAYS).isNotEmpty()
        }
        assertRestoredSchedule(historyDay, history)
        capture("meds-restored")
        todayWith(archivedName, "today-restored")
    }

    /**
     * Фаза «курс завершён»: человек подтверждает предложение убрать дошедший до даты окончания
     * курс, архив показывает итог курса, а возврат отказан с объяснением — продлить период.
     */
    private fun archiveFinishedCourseAndRefuseTheReturn() {
        openMedications()
        openMedicationCard(finishedName)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitForTag(TAG_FINISHED_OFFER).performScrollTo()
        capture("finished-course-offer")
        confirmTheFinishedCourseOffer()

        openArchive(archiveCount = 1)
        waitForTag(TAG_ARCHIVE_CARD)
        waitForTag(TAG_GROUP_FINISHED)
        waitForTag(TAG_ARCHIVE_CAPTION)
        assertEquals("архив обязан назвать итог курса", outcomeCaption(), nodeText(TAG_ARCHIVE_CAPTION))
        capture("archive-finished-outcome")

        click(TAG_ARCHIVE_RESTORE)
        waitForTag(TAG_ARCHIVE_NOTICE)
        val explanation = targetContext.getString(MedsR.string.card_notice_period_needs_update)
        assertTrue("отказ обязан быть объяснён, а не проглочен: $explanation", textExists(explanation))
        assertTrue("отказанный курс обязан остаться в архиве", exists(TAG_ARCHIVE_CARD))
        assertEquals(
            "молчаливого возврата не происходит: приёмов у курса не прибавилось",
            0,
            intakeIds(FINISHED_ID, PLAN_DAYS).size,
        )
        capture("archive-refused-restore")
    }

    /** Фаза мягкого удаления: слово-подтверждение, скрытость везде и след в журнале (ПМ-24). */
    private fun deleteTheSecondMedication() {
        // Раздел открывается на своём месте: предыдущая фаза закончилась в архиве, и человек
        // возвращается туда же (SKL-146 — место определяется последним экраном раздела).
        // Поэтому шаг утверждает сохранённое место и сам называет переход к активным;
        // ждать здесь список было бы проверкой сброса места, которого продукт не обещает.
        clickMedsTab()
        waitForTag(TAG_ARCHIVE_SCREEN)
        openActiveMedications(activeCount = 2)
        deleteFromCard()
        capture("meds-deleted")

        todayWithout(deletedName, "today-without-deleted")

        openMedications()
        openArchive(archiveCount = 1)
        assertTrue("архив обязан остаться с завершённым курсом", exists(TAG_ARCHIVE_CARD))
        assertTrue("удалённого лекарства в архиве нет", !textExists(deletedName))
        capture("archive-after-deletion")

        openActiveMedications(activeCount = 1)
        assertTakenMedicationCannotBeDeleted()
        capture("med-card-taken")
    }

    /**
     * «Сегодня» без лекарства: экран собирается по активным курсам — архив и удаление скрыты (ПР-11).
     *
     * @param step имя кадра: шагов с этим состоянием в походе два, и свидетельства разные.
     */
    private fun todayWithout(name: String, step: String) {
        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "«Сегодня» без лекарства «$name»") { !textExists(name) }
        capture(step)
    }

    /**
     * «Сегодня» с лекарством: приёмы возвращённого из архива курса снова на дне.
     *
     * @param step имя кадра шага — у похода их несколько, и кадры обязаны быть различимы.
     */
    private fun todayWith(name: String, step: String) {
        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "«Сегодня» с лекарством «$name»") { textExists(name) }
        capture(step)
    }

    /**
     * Отметка «принято» на приёме названного курса — история, поставленная настоящим путём.
     *
     * У ручного курса ею закрывается «история не переписана», и она же делает удаление
     * недоступным (ИНВ-22); у завершённого — наполняет знаменатель итога курса.
     */
    private fun markTaken(medicationId: String, day: LocalDate): Intake {
        val planned = requireNotNull(intakeOf(medicationId, day)) { "у курса $medicationId обязан быть приём $day" }
        DeviceTestSeed.markTaken(targetContext, planned.id)
        val taken = requireNotNull(intakeOf(medicationId, day)) { "принятый приём обязан читаться после отметки" }
        assertEquals("отметка обязана быть принята", IntakeStatus.TAKEN, taken.status)
        return taken
    }

    /**
     * Архив снял план на будущее (ПР-11), а в журнале остался один след архивации.
     *
     * Ожидание — часть утверждения: снятие идёт вслед за архивацией, и без него проверка
     * гонялась бы с пересборкой.
     */
    private fun assertPlanWasShed() {
        waitUntil(SCREEN_TIMEOUT_MS, "снятия плана архивного курса") {
            intakeIds(ARCHIVED_ID, FUTURE_DAYS).isEmpty()
        }
        assertEquals(
            "архивация обязана остаться в журнале одним следом",
            1,
            opLog().count { it.event == OpEvent.MEDICATION_ARCHIVED && it.medicationId == ARCHIVED_ID },
        )
    }

    /** Возврат восстановил расписание от текущей даты и не переписал вчерашнюю историю (НАП-31). */
    private fun assertRestoredSchedule(historyDay: LocalDate, historyBefore: Intake) {
        assertEquals(
            "расписание обязано быть восстановлено от текущей даты",
            listOf(DeviceTestClock.scheduledTime),
            dayIntakes(DeviceTestClock.today.plusDays(1))
                .filter { it.medicationId == ARCHIVED_ID }
                .map { it.scheduledAt.toLocalTime() },
        )
        val historyAfter = requireNotNull(intakeOf(ARCHIVED_ID, historyDay)) {
            "вчерашний приём обязан пережить возврат"
        }
        assertEquals("вчерашний приём подменён другой записью", historyBefore.id, historyAfter.id)
        assertEquals("статус вчерашнего приёма переписан", IntakeStatus.TAKEN, historyAfter.status)
        assertEquals("момент отметки переписан", historyBefore.actedAt, historyAfter.actedAt)
        assertEquals(
            "возврат не имеет права порождать приёмы в прошлом",
            1,
            dayIntakes(historyDay).count { it.medicationId == ARCHIVED_ID },
        )
    }

    /** Удаление «Витамина D»: шторка, слово-подтверждение, след в журнале и скрытость. */
    private fun deleteFromCard() {
        openMedicationCard(deletedName)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitForTag(TAG_DELETE)
        waitForTag(TAG_DELETE_HINT)
        capture("med-card-deletable")

        click(TAG_DELETE)
        waitForTag(TAG_DELETE_SHEET)
        capture("delete-sheet")

        // Удаление подтверждается вводом слова (§5.7): до него подтверждающая кнопка
        // недоступна, и нажатие ничего бы не сделало.
        val word = targetContext.getString(MedsR.string.medication_delete_word)
        typeInto(TAG_CONFIRM_WORD_FIELD, word, scroll = false)
        click(TAG_DELETE_CONFIRM)
        waitForTag(TAG_MEDICATION_LIST)

        val snackbar = targetContext.getString(MedsR.string.meds_deleted_snackbar, deletedName)
        waitUntil(SCREEN_TIMEOUT_MS, "снэкбара удаления") { textExists(snackbar) }
        waitUntil(SCREEN_TIMEOUT_MS, "списка без удалённого лекарства") { !textExists(deletedName) }
        assertEquals(
            "мягкое удаление обязано остаться в журнале одним следом",
            1,
            opLog().count { it.event == OpEvent.MEDICATION_DELETED && it.medicationId == DELETED_ID },
        )
    }

    /** У курса с отметкой «принято» действия удаления нет ни кнопкой, ни пояснением (ОВ-124). */
    private fun assertTakenMedicationCannotBeDeleted() {
        openMedicationCard(archivedName)
        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitForTag(TAG_ARCHIVE)
        waitUntil(SCREEN_TIMEOUT_MS, "отсутствия действия удаления") { !exists(TAG_DELETE) }
        waitUntil(SCREEN_TIMEOUT_MS, "отсутствия пояснения условия") { !exists(TAG_DELETE_HINT) }
    }

    /** Вкладка «Архив» лекарств нажимается своей надписью: счётчик считает продукт (ЭКР-8). */
    private fun openArchive(archiveCount: Int) {
        val label = targetContext.getString(MedsR.string.meds_tab_archive, archiveCount)
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
        waitForTag(TAG_ARCHIVE_SCREEN)
    }

    /** Обратно к активным лекарствам: «Активные · N» — та же вкладка с другой стороны. */
    private fun openActiveMedications(activeCount: Int) {
        val label = targetContext.getString(MedsR.string.meds_tab_active, activeCount)
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
        waitForTag(TAG_MEDICATION_LIST)
    }

    /**
     * Подтвердить предложение «Курс закончился».
     *
     * Нажимается действие плашки, а не сама плашка: кликабельна подпись действия, нарисованная
     * тихой кнопкой (§4.6). Поэтому узел ищется по действию нажатия внутри плашки — на карточке
     * рядом стоит вторая «В архив», нижняя кнопка курса, и поиск по слову был бы неоднозначен.
     */
    private fun confirmTheFinishedCourseOffer() {
        compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(TAG_FINISHED_OFFER))).performClick()
        waitForTag(TAG_MEDICATION_LIST)
    }

    /** Идентификаторы приёмов лекарства на названные дни: снимок состояния плана. */
    private fun intakeIds(medicationId: String, days: LongRange): List<String> = days.flatMap { offset ->
        dayIntakes(DeviceTestClock.today.plusDays(offset))
            .filter { it.medicationId == medicationId }
            .map { it.id }
    }

    /** Приём названного лекарства за названный день — история, которую поход отметил «принято». */
    private fun intakeOf(medicationId: String, day: LocalDate): Intake? =
        dayIntakes(day).firstOrNull { it.medicationId == medicationId }

    /**
     * Начало подписи архивной карточки: дату архивации продукт подставляет в неё сам.
     *
     * Разметка даты живёт внутри продукта (`sinceDayAndMonth`), поэтому проверяется не дата,
     * а вид подписи — «в архиве с».
     */
    private fun captionSincePrefix(): String =
        targetContext.getString(MedsR.string.archive_caption_since, CAPTION_DATE_PLACEHOLDER)

    /** Итог завершённого курса: строку собирает продукт — тем же шаблоном и теми же числами. */
    private fun outcomeCaption(): String = targetContext.resources.getQuantityString(
        MedsR.plurals.archive_outcome,
        FINISHED_COURSE_DAYS,
        FINISHED_COURSE_DAYS,
        FINISHED_TAKEN,
        FINISHED_PLANNED,
    )

    private companion object {
        /** Курс, который поход убирает в архив вручную и возвращает: «Магний B6» сценария. */
        const val ARCHIVED_ID = "dev-journey-archive"

        /** Курс, который поход удаляет: «Витамин D» сценария, без единой отметки «принято». */
        const val DELETED_ID = "dev-journey-deletion"

        /** Завершённый курс: дата окончания вчера, поэтому архив покажет итог курса (ЭКР-8). */
        const val FINISHED_ID = "dev-journey-finished"

        /** Роль в имени стенда завершённого курса: своё имя-ресурс ему заводить нечем. */
        const val FINISHED_ROLE = "journey-finished"

        /** Вчерашний день: там лежат отметки «принято», которыми закрыта история курсов. */
        const val HISTORY_OFFSET_DAYS = 1L

        /** Дни от сегодняшнего, на которых видно состояние плана до архивации и после неё. */
        val PLAN_DAYS: LongRange = 0L..2L

        /** Дни, которых архив обязан не планировать: будущее курса. */
        val FUTURE_DAYS: LongRange = 1L..2L

        /** Длина завершённого курса и его итог: два дня, из них принят один приём. */
        const val FINISHED_COURSE_DAYS = 2
        const val FINISHED_TAKEN = 1
        const val FINISHED_PLANNED = 2

        /** Пустая дата в шаблоне подписи: из неё получается начало «в архиве с». */
        const val CAPTION_DATE_PLACEHOLDER = ""
    }
}
