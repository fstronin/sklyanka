package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.feature.meds.form.TAG_ADD_TIME
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_SAVE_BUTTON
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.form.TAG_WEEKDAYS_ROW
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.screen.support.DeviceTestStores
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CHIP
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.feature.meds.R as MedsR

/**
 * Поход «Недельная схема» (SKL-117 §5.9, реестр SKL-140 № 22).
 *
 * Человек заводит курс, который принимают не каждый день: вид расписания выбирается строкой
 * («По дням недели»), дни — тремя чипами (понедельник, среда, пятница), — и сохраняет.
 * Поход утверждает **назначение**, а не экран: приёмы стоят по понедельникам, средам
 * и пятницам, в остальные дни окна генерации приёмов нет, а ближайшее напоминание
 * поставлено на приём выбранного дня недели, а не на ближайший календарный день.
 *
 * Часы управляемые: суббота 3 октября 2026, 09:01 (Europe/Moscow). Окно генерации — семь
 * суток от сегодняшнего дня (`IntakeGenerationPolicy`: глубина 7), и оно даёт ровно три
 * приёма: понедельник 5-го, среда 7-го и пятница 9-го. Суббота, воскресенье, вторник
 * и четверг остаются пустыми — по ним и проверяется «в остальные дни приёмов нет».
 *
 * Два состояния стенда, названные здесь честно, а не обойдённые:
 *
 * 1. **Недельная схема из двух и более дней — платное создание** (ПР-20). Сценарий приёмки
 *    начинается словами «подписка “Склянка Про” активна» (SKL-117.feature, `@weekdays @happy`),
 *    поэтому поход подкладывает в кэш подписки тот же ответ, что дал бы магазин (ключ и формат
 *    — из `ProSubscriptionDataStore`, тем же приёмом, что поход «Витрина “Склянка Про”»):
 *    без доступа сохранение закрыто, и поход проверял бы витрину, а не расписание.
 * 2. **Поставленный будильник на стенде не наблюдается.** Право точных будильников тестовому
 *    пакету не выдано, `AlarmManager` отказывает, `AlarmGateway` глотает отказ, и зеркало
 *    будильников пусто — это состояние стенда, названное в `SKL-140/TDD.md` §8.1 (и там же
 *    решено не патчить). Наблюдаемое здесь — запись пересборки окна в журнале напоминаний
 *    (`ReminderEvent.RESCHEDULED`): что продукт запланировал и на какой приём. Настоящее
 *    срабатывание будильника держат испытания оснастки и человек, и поход его не имитирует (П-7).
 */
@HiltAndroidTest
class MedicationScheduleJourneyTest : JourneyTest() {

    @Test
    fun thePersonSetsAWeeklySchedule() {
        val name = testContext.getString(R.string.device_test_medication_name, NAME_SUFFIX)
        seedActiveSubscription()

        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)
        typeInto(TAG_NAME_FIELD, name)
        typeInto(TAG_DOSE_FIELD, DOSE)
        // Время приёма продукт даёт первым нажатием: у нового черновика времён нет,
        // а курс без времён не порождает приёмов вовсе (`ScheduleExpander`).
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)
        capture("form-daily")

        chooseScheduleKind(MedsR.string.schedule_kind_weekdays)
        waitForTag(TAG_WEEKDAYS_ROW)
        assertTrue(
            "строка вида обязана назвать «${ui(MedsR.string.schedule_kind_weekdays)}», " +
                "а называет «${scheduleRowText()}»",
            scheduleRowText().contains(ui(MedsR.string.schedule_kind_weekdays)),
        )
        capture("weekdays-unselected")

        CHOSEN_DAYS.forEach(::clickWeekday)
        assertWeekdaySelection()
        capture("weekdays-selected")

        click(TAG_SAVE_BUTTON)
        waitForTag(TAG_MEDICATION_LIST)
        scrollListTo(name)
        assertTrue("курс недельной схемы обязан появиться в списке лекарств", textExists(name))
        capture("list")

        val intakes = assertIntakesFollowTheWeekdays()
        assertTheReminderIsPlannedForAChosenDay(intakes)
    }

    /**
     * Дни недели выбираются чипами: нажатие — по имени дня, а не по месту в ряду.
     *
     * Чип адресуется полным именем для чтения с экрана (`SklyankaChip`: подпись — короткая
     * форма, имя — полная), и оно же печатается платформенным форматтером той же локалью
     * интерфейса, что у продукта.
     */
    private fun clickWeekday(day: DayOfWeek) {
        weekdayChip(day).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Выбранные дни отмечены, остальные — нет: это состояние черновика, а не вид чипа. */
    private fun assertWeekdaySelection() {
        CHOSEN_DAYS.forEach { day -> weekdayChip(day).assertIsSelected() }
        DayOfWeek.entries.filterNot { day -> day in CHOSEN_DAYS }
            .forEach { day -> weekdayChip(day).assertIsNotSelected() }
    }

    /** Чип дня недели: у ряда дней свой тег-предок, у чипа — имя дня в чтении с экрана. */
    private fun weekdayChip(day: DayOfWeek) = compose.onNode(
        hasTestTag(TAG_CHIP) and hasContentDescription(weekdayName(day)) and
            hasAnyAncestor(hasTestTag(TAG_WEEKDAYS_ROW)),
        useUnmergedTree = true,
    )

    /**
     * Имя дня недели для чтения с экрана: то же, что печатает продукт (`MedsText`).
     *
     * Шаблон берётся из ресурсов продукта, локаль — из обстановки приложения: день недели
     * платформа склоняет сама, и собственный список имён в проверке разошёлся бы с экраном
     * на первой же локали. Неделя-образец та же, что у продукта, — 1 января 2024 года.
     */
    private fun weekdayName(day: DayOfWeek): String {
        val locale = targetContext.resources.configuration.locales[0]
        val pattern = targetContext.getString(MedsR.string.date_pattern_weekday_full)
        return DateTimeFormatter.ofPattern(pattern, locale).format(referenceWeekday(day))
    }

    private fun referenceWeekday(day: DayOfWeek): LocalDate = REFERENCE_WEEK.plusDays((day.value - 1).toLong())

    /**
     * Приёмы стоят в выбранные дни недели и только в них — по всем суткам окна генерации.
     *
     * Читаются моменты приёмов, а не экран: экран «Сегодня» показывает один день, а схема
     * спрашивает про все дни окна. Приёмы уже порождены к закрытию формы (сохранение ждёт
     * генерацию и пересборку), но признак ждётся явно — поход не имеет права на «повезло».
     */
    private fun assertIntakesFollowTheWeekdays(): List<Intake> {
        val chosenDates = windowDates.filter { date -> date.dayOfWeek in CHOSEN_DAYS }
        waitUntil(SCREEN_TIMEOUT_MS, "приёмов недельной схемы") {
            dayIntakes(chosenDates.first()).isNotEmpty()
        }
        val intakes = windowDates.flatMap { date -> dayIntakes(date) }
        assertEquals(
            "приёмы обязаны стоять по одному в каждый выбранный день недели: " +
                intakes.map { it.scheduledAt },
            CHOSEN_DAYS,
            intakes.map { it.scheduledAt.dayOfWeek },
        )
        intakes.forEach { intake ->
            assertEquals(
                "время приёма — время курса: ${intake.scheduledAt}",
                TIME,
                intake.scheduledAt.toLocalTime(),
            )
        }
        windowDates.filterNot { date -> date.dayOfWeek in CHOSEN_DAYS }.forEach { date ->
            assertEquals(
                "в день ${date.dayOfWeek} приёмов по схеме нет, а они есть: " +
                    dayIntakes(date).map { it.scheduledAt },
                0,
                dayIntakes(date).size,
            )
        }
        return intakes
    }

    /**
     * Ближайшее напоминание поставлено на приём выбранного дня недели.
     *
     * Момент берётся из записи пересборки окна: она называет и причину («добавлено лекарство»),
     * и приём, и время — то, что продукт запланировал, независимо от того, приняла ли это
     * система (см. оговорку о зеркале будильников в шапке класса). Подмена схемы на ежедневную
     * видна здесь сразу: тогда ближайшим стал бы воскресный приём, которого у схемы нет.
     */
    private fun assertTheReminderIsPlannedForAChosenDay(intakes: List<Intake>) {
        val rescheduled = awaitRescheduleOfTheSave()
        val nearest = intakes.first()
        assertEquals(
            "пересборка окна обязана быть названа причиной «добавлено лекарство»",
            RescheduleReason.MEDICATION_ADDED.code,
            rescheduled.reason,
        )
        assertEquals(
            "ближайшее напоминание обязано стоять на приём выбранного дня недели",
            nearest.id,
            rescheduled.intakeId,
        )
        assertEquals(
            "момент напоминания — плановый момент приёма: ${nearest.scheduledAt}",
            nearest.scheduledAt.atZone(DeviceTestClock.zone).toInstant(),
            rescheduled.scheduledAt,
        )
        assertEquals(
            "в ближайшее окно напоминаний попадает ровно один приём схемы — понедельничный: " +
                intakes.map { it.scheduledAt },
            1,
            rescheduled.alarmsScheduled,
        )
    }

    /** Последняя пересборка окна, вызванная сохранением курса. */
    private fun awaitRescheduleOfTheSave(): ReminderLogEntry {
        waitUntil(SCREEN_TIMEOUT_MS, "записи пересборки окна после сохранения курса") {
            runCatching {
                DeviceTestSeed.lastJournalEvent(targetContext, ReminderEvent.RESCHEDULED)
                    ?.reason == RescheduleReason.MEDICATION_ADDED.code
            }.getOrDefault(false)
        }
        return requireNotNull(
            DeviceTestSeed.lastJournalEvent(targetContext, ReminderEvent.RESCHEDULED),
        ) { "пересборка окна после сохранения обязана оставить запись" }
    }

    /**
     * Подписка «Про» в кэше: ответ магазина, которого на стенде некому дать (SKL-122).
     *
     * Недельная схема из двух и более дней — платное создание (ПР-20), и без доступа кнопка
     * сохранения открывает витрину вместо записи. Ключ и формат записи повторяют
     * `ProSubscriptionDataStore` (`active|<товар>|<день>`): другого способа поднять состояние
     * «активна» без живого магазина у проверки нет.
     */
    private fun seedActiveSubscription() {
        val until = DeviceTestClock.today.plusDays(ACTIVE_DAYS)
        runBlocking {
            DeviceTestStores.settings(targetContext).edit { preferences ->
                preferences[PRO_SUBSCRIPTION] =
                    "active|${ProProductId.YEARLY.storeId}|${until.toEpochDay()}"
            }
        }
    }

    /** Сутки окна генерации: семь дней, считая сегодняшний. */
    private val windowDates: List<LocalDate>
        get() = (0 until WINDOW_DAYS).map { offset ->
            DeviceTestClock.today.plusDays(offset.toLong())
        }

    private companion object {
        /** Кусок имени лекарства стенда: имя обязано быть латиницей (НФТ-42). */
        const val NAME_SUFFIX = "journey-weekly"

        /** Доза приёма: без неё курс не сохраняется (обязательное поле формы). */
        const val DOSE = "1"

        /** Время приёма, которое форма даёт новому черновику. */
        val TIME: LocalTime = LocalTime.of(8, 0)

        /** Дни недели, выбранные человеком: понедельник, среда, пятница. */
        val CHOSEN_DAYS: List<DayOfWeek> =
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        /** Глубина окна генерации в сутках, считая текущие (`IntakeGenerationPolicy`). */
        const val WINDOW_DAYS = 7

        /** Сколько дней подписки лежит в кэше: состояние «активна» с запасом. */
        const val ACTIVE_DAYS = 30L

        /** Ключ кэша состояния подписки: то же имя, что в `ProSubscriptionDataStore`. */
        val PRO_SUBSCRIPTION = stringPreferencesKey("pro_subscription")

        /** Понедельник недели-образца продукта: 1 января 2024 года (`MedsText`). */
        val REFERENCE_WEEK: LocalDate = LocalDate.of(2024, 1, 1)
    }
}
