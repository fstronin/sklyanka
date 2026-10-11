package ru.stohastika.sklyanka.screen.journey

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.feature.history.TAG_BREAKDOWN
import ru.stohastika.sklyanka.feature.history.TAG_BREAKDOWN_BAR
import ru.stohastika.sklyanka.feature.history.TAG_CALENDAR
import ru.stohastika.sklyanka.feature.history.TAG_DAY
import ru.stohastika.sklyanka.feature.history.TAG_DAY_GRID
import ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN
import ru.stohastika.sklyanka.feature.history.TAG_INSIGHT
import ru.stohastika.sklyanka.feature.history.TAG_LEGEND
import ru.stohastika.sklyanka.feature.history.TAG_MONTH_LABEL
import ru.stohastika.sklyanka.feature.history.TAG_PREVIOUS_MONTH
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_ACTION
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_CONTENT
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_DATA_NOTE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_EMPTY
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_FORMAT
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_PERIOD
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_PROFILE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SAVE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHARE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHEET
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SUCCESS
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_TAKEN
import ru.stohastika.sklyanka.feature.history.TAG_SUMMARY
import ru.stohastika.sklyanka.feature.history.TAG_TILE_DAYS_WITHOUT_MISSES
import ru.stohastika.sklyanka.feature.history.TAG_TILE_MISSES
import ru.stohastika.sklyanka.feature.history.TAG_TILE_ON_TIME
import ru.stohastika.sklyanka.feature.history.TAG_WEEKDAYS
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import javax.inject.Inject
import ru.stohastika.sklyanka.feature.history.R as HistoryR
import ru.stohastika.sklyanka.feature.settings.R as SettingsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Поход «История и отчёт врачу» (SKL-140, поход реестра № 6).
 *
 * Человек заходит в историю со строки настроек, листает месяц назад и читает картину
 * сентября: приверженность за период, календарь месяца, разбивку по лекарствам и строку
 * о самом пропускаемом приёме. Затем открывает шторку отчёта врачу, выбирает период
 * и доходит до собранного состава — того, что именно он отдаёт врачу, — после чего
 * возвращается туда, откуда пришёл.
 *
 * **Передача отчёта остаётся человеку.** «Передать» отдаёт документ системному обмену:
 * `SystemDoctorReportTransfer` выкладывает PDF в каталог кеша приложения и открывает
 * выбор получателя намерением `ACTION_SEND`, а системные диалоги вне набора походов
 * (TDD §8, §13). Поэтому поход утверждает то, что наблюдаемо: шторка открыта, состав
 * собран по выбранному периоду, передачи ещё не было — и закрывает шторку её собственным
 * действием. Кнопка передачи живёт только в шторке: на экране истории её нет.
 *
 * **Предыстория кладётся портом продукта.** Планировщик разворачивает расписание только
 * вперёд от сегодняшнего дня (`GenerationWindow.of`), и сентября в его окне нет: записи
 * прошлого месяца приходят через `IntakeRepository.insertMissing` — тем же путём, что
 * и кадры магазина. Ожидающие приёмы сентября переводит в «пропущено без отметки» проход
 * по просроченным (ПР-1), который идёт при открытии «Сегодня», поэтому ожидания похода
 * считаются по записям, а не по константам: экран обязан говорить то же, что база.
 *
 * Месяц истории — сентябрь: он прошлый от управляемого «сегодня» (3 октября 2026),
 * и в нём же лежат приёмы, по которым собирается отчёт за пресет «Прошлый месяц».
 */
@HiltAndroidTest
class HistoryAndReportJourneyTest : JourneyTest() {

    /** Записи сентября кладутся портом продукта — тем же, что у кадров магазина (SKL-22). */
    @Inject
    lateinit var intakes: IntakeRepository

    @Test
    fun thePersonReviewsTheHistoryAndPreparesTheDoctorReport() {
        val name = testContext.getString(R.string.device_test_medication_name, MEDICATION_SUFFIX)
        seedSeptember(name)
        val awaiting = AWAITING_DAYS.sumOf { day ->
            dayIntakes(SEPTEMBER.atDay(day)).count { it.status == IntakeStatus.PENDING }
        }
        assertEquals("the prehistory must hold awaiting September intakes", AWAITING_DAYS.count(), awaiting)

        launchApp()
        awaitRoot()
        openHistoryFromSettings()

        click(TAG_PREVIOUS_MONTH)
        waitForTag(TAG_CALENDAR)
        val september = label(HistoryR.string.history_month_pattern, SEPTEMBER, capitalise = true)
        waitUntil(SCREEN_TIMEOUT_MS, "the September history") { nodeText(TAG_MONTH_LABEL) == september }

        val records = (1..SEPTEMBER.lengthOfMonth()).flatMap { day -> dayIntakes(SEPTEMBER.atDay(day)) }
        assertAdherenceAgainstRecords(name, records)
        assertCalendarOfSeptember()
        assertMissedPatternIsNamed()
        capture("history-september")

        openReportSheetWithPreviousMonth()
        assertReportIsComposed(records)
        assertNothingIsHandedOver()
        capture("report-ready")

        dismissSheet()
        pressBack()
        waitUntil(SCREEN_TIMEOUT_MS, "the return to the screen the history was opened from") {
            exists(TAG_SETTINGS_SCREEN) && !exists(TAG_HISTORY_SCREEN)
        }
        capture("back-in-settings")
    }

    /** Сентябрьская предыстория: два времени в сутки — иначе строке-выводу не с чем сравнивать. */
    private fun seedSeptember(name: String) {
        seedMedication(
            id = MEDICATION_ID,
            name = name,
            times = listOf(MORNING, EVENING),
            startDate = SEPTEMBER.atDay(1),
            endDate = SEPTEMBER.atEndOfMonth(),
        )
        val rows = generateSequence(SEPTEMBER.atDay(1)) { day -> day.plusDays(1) }
            .takeWhile { day -> !day.isAfter(SEPTEMBER.atEndOfMonth()) }
            .flatMap { date -> listOf(intake(date, MORNING), intake(date, EVENING)) }
            .toList()
        val inserted = runBlocking { intakes.insertMissing(rows) }
        assertEquals("the September records must be seeded whole", rows.size, inserted)
    }

    /**
     * Приём сентября в том состоянии, в каком его оставил человек.
     *
     * Рисунок пропусков задан умыслом: семь вечерних приёмов подряд пропущены — у них
     * есть явный лидер, и строка-вывод называет вечернее время; одно утро пропущено
     * осознанно; два последних утра остались ожидающими; остальное принято в пределах
     * окна «вовремя» (Р-21: ±2 часа от назначенного времени).
     */
    private fun intake(date: LocalDate, time: LocalTime): Intake {
        val status = statusOn(date, time)
        val pending = status == IntakeStatus.PENDING
        val scheduledAt = LocalDateTime.of(date, time)
        val markedAt = scheduledAt.plusMinutes(MARKED_MINUTES_AFTER).atZone(DeviceTestClock.zone).toInstant()
        // Автоматический пропуск ставит приложение, остальные отметки — человек с телефона.
        val source = when {
            pending -> null
            status == IntakeStatus.MISSED -> IntakeSource.AUTO
            else -> IntakeSource.PHONE
        }
        return Intake(
            id = "$MEDICATION_ID-$date-$time",
            medicationId = MEDICATION_ID,
            scheduledAt = scheduledAt,
            status = status,
            actedAt = if (pending) null else markedAt,
            source = source,
            actorProfileId = null,
        )
    }

    /** Состояние приёма: вечерние пропуски, одно пропущенное осознанно утро, два ожидающих. */
    private fun statusOn(date: LocalDate, time: LocalTime): IntakeStatus = when {
        time == EVENING && date.dayOfMonth <= EVENING_MISSED_UNTIL_DAY -> IntakeStatus.MISSED
        time == MORNING && date.dayOfMonth == SKIPPED_MORNING_DAY -> IntakeStatus.SKIPPED
        time == MORNING && date.dayOfMonth in AWAITING_DAYS -> IntakeStatus.PENDING
        else -> IntakeStatus.TAKEN
    }

    /**
     * История со строки настроек: `Данные → История приёмов` (SKL-20, HistoryOrigin.SETTINGS).
     *
     * Метка строки внутренняя для `feature-settings`, а строка экрана — тот же адрес:
     * она приходит из ресурса и не зависит от того, как модуль назвал свой узел.
     */
    private fun openHistoryFromSettings() {
        openSettings()
        val row = targetContext.getString(SettingsR.string.settings_history)
        compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(TAG_SETTINGS_SCREEN)),
            useUnmergedTree = true,
        ).performScrollToNode(hasText(row))
        compose.onNodeWithText(row).performClick()
        waitForTag(TAG_HISTORY_SCREEN)
    }

    /**
     * Приверженность и разбивка против записей человека.
     *
     * Ожидания считаются по записям, а не берутся константами: смысл похода в том, что
     * экран и данные говорят одно и то же. Плитка «принято вовремя» названа теми же
     * числами — сколько приёмов наступило и сколько отмечено вовремя.
     */
    private fun assertAdherenceAgainstRecords(name: String, records: List<Intake>) {
        waitForTag(TAG_SUMMARY)
        waitForTag(TAG_TILE_DAYS_WITHOUT_MISSES)
        waitForTag(TAG_TILE_MISSES)
        waitForTag(TAG_TILE_ON_TIME)
        val misses = records.count { it.status == IntakeStatus.MISSED || it.status == IntakeStatus.SKIPPED }
        val daysWithoutMisses = records.groupBy { it.scheduledAt.toLocalDate() }.values
            .count { day -> day.all { record -> record.status == IntakeStatus.TAKEN } }
        val expectedMisses = pluralDescription(HistoryR.plurals.cd_history_misses, misses)
        val expectedDays = pluralDescription(HistoryR.plurals.cd_history_days_without_misses, daysWithoutMisses)
        waitUntil(SCREEN_TIMEOUT_MS, "the September summary of the records") {
            val missesShown = tileDescription(TAG_TILE_MISSES)
            val daysShown = tileDescription(TAG_TILE_DAYS_WITHOUT_MISSES)
            missesShown == expectedMisses && daysShown == expectedDays
        }
        val taken = records.count { it.status == IntakeStatus.TAKEN }
        val due = records.count { it.status != IntakeStatus.PENDING }
        val onTime = tileDescription(TAG_TILE_ON_TIME).orEmpty()
        assertTrue("the on-time tile must name $taken taken intakes: $onTime", onTime.contains(taken.toString()))
        assertTrue("the on-time tile must name $due due intakes: $onTime", onTime.contains(due.toString()))

        waitForTag(TAG_BREAKDOWN)
        val takenPhrase = targetContext.resources.getQuantityString(
            UiR.plurals.intakes_taken_of_total,
            records.size,
            taken,
            records.size,
        )
        val expected = targetContext.getString(HistoryR.string.cd_history_medication, name, takenPhrase)
        val shown = compose.onNodeWithTag(TAG_BREAKDOWN_BAR, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
        assertEquals("the breakdown must read the records of the course", expected, shown)
    }

    /** Календарь сентября: клетка на каждый день, исход дня назван словами (НФТ-33). */
    private fun assertCalendarOfSeptember() {
        waitForTag(TAG_WEEKDAYS)
        waitForTag(TAG_DAY_GRID)
        waitForTag(TAG_LEGEND)
        val days = dayDescriptions()
        assertEquals("the calendar must hold a cell per day of September", SEPTEMBER.lengthOfMonth(), days.size)
        val fifteenthLabel = label(HistoryR.string.history_date_pattern, SEPTEMBER.atDay(15))
        val thirdLabel = label(HistoryR.string.history_date_pattern, SEPTEMBER.atDay(3))
        val fifteenth = days.firstOrNull { description -> description.startsWith(fifteenthLabel) }
        val third = days.firstOrNull { description -> description.startsWith(thirdLabel) }
        val allTaken = ui(HistoryR.string.history_legend_all_taken)
        val partial = ui(HistoryR.string.history_legend_partial)
        assertTrue("the fifteenth must be named all taken: $fifteenth", fifteenth.orEmpty().contains(allTaken))
        assertTrue("the third must be named partial: $third", third.orEmpty().contains(partial))
    }

    /** Строка-вывод названа: вечерний приём пропускался семь раз против одного утреннего. */
    private fun assertMissedPatternIsNamed() {
        waitForTag(TAG_INSIGHT)
        val evening = label(HistoryR.string.history_time_pattern, EVENING)
        assertEquals(
            "the line must name the most missed intake",
            targetContext.getString(HistoryR.string.history_insight_evening, evening),
            nodeText(TAG_INSIGHT),
        )
    }

    /**
     * Шторка отчёта на прошлый месяц.
     *
     * Кнопки передачи на экране истории нет вовсе — она живёт в шторке (ОВ-125), и до
     * нажатия «Отчёт врачу» её не существует. Текущий месяц записей не имеет, поэтому
     * состав сначала говорит об этом сам, а пресет «Прошлый месяц» собирает сентябрь.
     */
    private fun openReportSheetWithPreviousMonth() {
        assertFalse("no transfer control on the history screen", exists(TAG_REPORT_SHARE))
        click(TAG_REPORT_ACTION)
        waitForTag(TAG_REPORT_SHEET)
        waitForTag(TAG_REPORT_CONTENT)
        assertTrue("the empty current month must say so", exists(TAG_REPORT_EMPTY))
        compose.onNodeWithText(ui(HistoryR.string.history_report_period_previous_month)).performClick()
        waitUntil(SCREEN_TIMEOUT_MS, "the September composition") {
            exists(TAG_REPORT_TAKEN) && !exists(TAG_REPORT_EMPTY)
        }
    }

    /** Состав отчёта: имя профиля, период, принятые приёмы и формат — то, что он отдаёт. */
    private fun assertReportIsComposed(records: List<Intake>) {
        val profile = textsUnder(TAG_REPORT_PROFILE)
        assertTrue("the report must name the profile: $profile", ownerProfile.name in profile)
        val period = textsUnder(TAG_REPORT_PERIOD)
        assertTrue("the report must name the period: $period", period.isNotEmpty())
        val taken = textsUnder(TAG_REPORT_TAKEN).joinToString(" ")
        val takenCount = records.count { it.status == IntakeStatus.TAKEN }.toString()
        val plannedCount = records.size.toString()
        assertTrue("the report must state $takenCount taken intakes: $taken", taken.contains(takenCount))
        assertTrue("the report must state $plannedCount planned intakes: $taken", taken.contains(plannedCount))
        val format = textsUnder(TAG_REPORT_FORMAT)
        val pdfFormat = ui(HistoryR.string.history_report_format_value)
        assertTrue("the report must name the PDF format: $format", pdfFormat in format)
        waitForTag(TAG_REPORT_DATA_NOTE)
        waitForTag(TAG_REPORT_SHARE)
        waitForTag(TAG_REPORT_SAVE)
    }

    /**
     * Передачи ещё не было: файла отчёта на диске нет.
     *
     * Документ выкладывается системному обмену ровно в момент «Передать»
     * (`SystemDoctorReportTransfer` кладёт PDF в каталог кеша `reports`), поэтому пустой
     * каталог и есть наблюдаемое: ни одна передача не случилась без человека.
     */
    private fun assertNothingIsHandedOver() {
        assertFalse("no success state before the hand-over", exists(TAG_REPORT_SUCCESS))
        val staged = File(targetContext.cacheDir, REPORT_DIRECTORY).listFiles().orEmpty().map { it.name }
        assertEquals("the report must not be handed over yet", emptyList<String>(), staged)
    }

    /**
     * Закрыть шторку её собственным действием.
     *
     * До успешной передачи ведущее действие шапки — «Отмена», после успеха его место
     * занимает «Готово»; успех приходит только по решению системного выбора получателя,
     * а он автоматизации недоступен (TDD §8, §13), и поход останавливается на собранном
     * составе, не уводя приложение в системный диалог.
     *
     * Исход передачи — «Отчёт сформирован» → «Передан» и файл отчёта в каталоге кеша —
     * походом не проверяется: «Передать» поднимает системный выбор получателя намерением
     * `ACTION_SEND`, а системные диалоги вне набора (TDD §8, §13). Поход останавливается
     * на собранном составе и на кнопке передачи, которая живёт только в шторке; сама
     * передача остаётся человеку.
     */
    private fun dismissSheet() {
        compose.onNodeWithText(ui(UiR.string.action_cancel)).performClick()
        waitUntil(SCREEN_TIMEOUT_MS, "the report sheet closes") { !exists(TAG_REPORT_SHEET) }
    }

    /**
     * Строка из шаблона ресурса в локали конфигурации: те же названия месяцев и даты,
     * что видит человек (НФТ-43). Прописная ставится тем же преобразованием, что на экране.
     */
    private fun label(@StringRes pattern: Int, value: TemporalAccessor, capitalise: Boolean = false): String {
        val locale = targetContext.resources.configuration.locales[0]
        val text = DateTimeFormatter.ofPattern(targetContext.getString(pattern), locale).format(value)
        return if (capitalise) text.replaceFirstChar { letter -> letter.titlecase(locale) } else text
    }

    /** Подпись плитки для чтения с экрана: число плитки живёт только в ней (НФТ-30). */
    private fun tileDescription(tag: String): String? {
        if (!exists(tag)) return null
        return compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
    }

    /** Подписи клеток календаря: состояние дня названо словами, а не только цветом (НФТ-33). */
    private fun dayDescriptions(): List<String> = compose.onAllNodesWithTag(TAG_DAY, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { node ->
            val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            description.firstOrNull().orEmpty()
        }

    /** Тексты узла и его потомков: строка состава — пара «подпись — значение», не один узел. */
    private fun textsUnder(tag: String): List<String> =
        textsOf(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } +
            node.children.flatMap { child -> textsOf(child) }

    /** Подпись плитки тем же склонением, что у экрана: «8 пропусков за месяц». */
    private fun pluralDescription(@PluralsRes plurals: Int, count: Int): String =
        targetContext.resources.getQuantityString(plurals, count, count)

    private companion object {
        const val MEDICATION_SUFFIX = "history"
        const val MEDICATION_ID = "jny-history-med"

        /** Каталог кеша, куда продукт выкладывает отчёт перед системным обменом. */
        const val REPORT_DIRECTORY = "reports"

        const val EVENING_MISSED_UNTIL_DAY = 7
        const val SKIPPED_MORNING_DAY = 20
        const val MARKED_MINUTES_AFTER = 5L

        /** Утра, оставшиеся ожидающими: предыстория «принятые, пропущенные и ожидающие». */
        val AWAITING_DAYS = 28..29

        val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val EVENING: LocalTime = LocalTime.of(20, 0)
    }
}
