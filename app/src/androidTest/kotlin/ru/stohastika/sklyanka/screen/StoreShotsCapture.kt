package ru.stohastika.sklyanka.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.feature.history.TAG_CALENDAR
import ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN
import ru.stohastika.sklyanka.feature.meds.card.TAG_COURSE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.card.TAG_SCHEDULE_BLOCK
import ru.stohastika.sklyanka.feature.meds.card.TAG_STOCK_BLOCK
import ru.stohastika.sklyanka.feature.meds.form.TAG_ADD_TIME
import ru.stohastika.sklyanka.feature.meds.form.TAG_DOSE_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_MEDICATION_FORM
import ru.stohastika.sklyanka.feature.meds.form.TAG_NAME_FIELD
import ru.stohastika.sklyanka.feature.meds.form.TAG_TIME_ROW
import ru.stohastika.sklyanka.feature.meds.list.TAG_ADD_MEDICATION
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.feature.reminder.ReminderActivity
import ru.stohastika.sklyanka.feature.reminder.TAG_REMINDER
import ru.stohastika.sklyanka.feature.today.TAG_DAY_PROGRESS
import ru.stohastika.sklyanka.reminder.ReminderOutageAnnouncer
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import android.graphics.Color as AndroidColor
import ru.stohastika.sklyanka.feature.today.R as TodayR
import ru.stohastika.sklyanka.test.R as TestR

/**
 * Кадры и значок для карточки магазина (SKL-22, §8–9 `store-listing.md`).
 *
 * Материалы магазина снимаются с **настоящего приложения** на эмуляторе, а не рисуются
 * макетом: `captureToImage` берёт окно композиции, тестовый Hilt-граф сеет осмысленные
 * данные — препараты «Магний B6», «Витамин D3», «Омега-3», принятый и ожидаемый приёмы,
 * история за месяц. Ни «Стенда», ни «0 из 0», ни аналитики, ни «Про» в кадрах нет
 * (`store-listing.md` §9): релиз 0.1.0 их не содержит.
 *
 * Класс стоит рядом с экранными проверками устройства и идёт той же группой `ui`
 * (`config.sh`): кадр — свидетельство, а не эталон, и сверять его не с чем (TDD §6).
 * Значок — настоящий adaptive icon приложения: подложка и передний план кладутся
 * в квадрат без маски-круга, как того требует консоль.
 *
 * Кадры ложатся во внутренний каталог приложения и забираются `screenshots.sh`
 * на хост (`build/store-assets`). Ничего не коммитится.
 */
@HiltAndroidTest
class StoreShotsCapture : DeviceScreenTest() {

    /** Приёмы для истории месяца: тем же репозиторием, что у продукта, но за прошлые даты. */
    @Inject
    lateinit var intakes: IntakeRepository

    /**
     * Баннер перерыва — свойство пустого журнала тестового графа, а не продукта:
     * на живом устройстве сработавшие напоминания оставляют записи. Для кадра он
     * не нужен, поэтому объявление снимается тем же способом, что у человека.
     */
    @Inject
    lateinit var outageAnnouncer: ReminderOutageAnnouncer

    /**
     * Проверка «кадр снят под той же локалью, что просили».
     *
     * Приложение берёт язык интерфейса из системы (`InterfaceLocale`), а не из аргумента,
     * поэтому смена языка — это смена локали образа (как для ru-RU). Аргумент `storeLocale`
     * не переключает язык, а **удостоверяет** его: прогон под не той локалью падает, и
     * английские кадры не ложатся молча в каталог `screens-es`.
     */
    @Before
    fun requireRequestedStoreLocale() {
        val requested = InstrumentationRegistry.getArguments().getString(STORE_LOCALE_ARG) ?: return
        val active = targetContext.interfaceLocale.language
        check(active == requested) {
            "Store frames requested for «$requested», but the application renders «$active»: " +
                "run the image (and the test) under the matching system locale"
        }
    }

    @Test
    fun storeIconIsWrittenAsASquare() {
        val icon = requireNotNull(targetContext.getDrawable(R.mipmap.ic_launcher)) {
            "the application adaptive icon must exist"
        }
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.TRANSPARENT)
        if (icon is AdaptiveIconDrawable) {
            icon.background?.let { layer -> drawLayer(layer, canvas) }
            icon.foreground?.let { layer -> drawLayer(layer, canvas) }
        } else {
            drawLayer(icon, canvas)
        }
        writePng(File(targetContext.filesDir, ICON_PATH), bitmap)
    }

    @Test
    fun storeToday() {
        seedCatalog()
        planIntakes()
        markTaken(MED_MAGNESIUM_ID, MORNING)

        launchApp()
        awaitRoot()
        suppressOutageBanner()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «${medicationName(TestR.string.store_medication_magnesium)}»") {
            textExists(medicationName(TestR.string.store_medication_magnesium))
        }
        capture(STEP_TODAY)
    }

    @Test
    fun storeMedicationList() {
        seedCatalog()
        planIntakes()

        launchApp()
        awaitRoot()
        suppressOutageBanner()
        openMedications()
        waitUntil(SCREEN_TIMEOUT_MS, "списка из трёх лекарств") {
            textExists(medicationName(TestR.string.store_medication_omega))
        }
        capture(STEP_MEDS)
    }

    @Test
    fun storeMedicationCard() {
        seedCatalog()
        planIntakes()

        launchApp()
        awaitRoot()
        suppressOutageBanner()
        openMedications()

        val name = medicationName(TestR.string.store_medication_magnesium)
        scrollTo(TAG_MEDICATION_LIST, TAG_MEDICATION_CARD)
        compose.onNode(
            hasText(name) and hasAnyAncestor(hasTestTag(TAG_MEDICATION_CARD)),
            useUnmergedTree = true,
        ).performClick()

        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitForTag(TAG_SCHEDULE_BLOCK)
        waitForTag(TAG_COURSE_BLOCK)
        waitForTag(TAG_STOCK_BLOCK)
        capture(STEP_CARD)
    }

    @Test
    fun storeHistoryForTheMonth() {
        DeviceTestClock.advanceTo(LocalDateTime.of(2026, 10, HISTORY_TODAY, 9, 1))
        seedCatalog(startDate = LocalDate.of(2026, 10, 1))
        planIntakes()
        seedPastIntakes()

        launchApp()
        awaitRoot()
        suppressOutageBanner()
        openHistoryFromToday()
        waitForTag(TAG_HISTORY_SCREEN)
        waitForTag(TAG_CALENDAR)
        capture(STEP_HISTORY)
    }

    @Test
    fun storeFilledMedicationForm() {
        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_ADD_MEDICATION)
        waitForTag(TAG_MEDICATION_FORM)

        typeInto(TAG_NAME_FIELD, medicationName(TestR.string.store_medication_magnesium))
        typeInto(TAG_DOSE_FIELD, DOSE)
        scrollToAndClick(TAG_ADD_TIME)
        waitForTag(TAG_TIME_ROW)

        waitForTag(TAG_NAME_FIELD).performScrollTo()
        compose.waitForIdle()
        capture(STEP_FORM)
    }

    @Test
    fun storeReminderCard() {
        val name = medicationName(TestR.string.store_medication_magnesium)
        seedMedication(id = MED_MAGNESIUM_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchScreen(
            ReminderActivity.intentFor(targetContext, ownerProfile.id, DeviceTestClock.scheduledAt),
        )
        awaitActivity(ReminderActivity::class.java)
        waitForTag(TAG_REMINDER)
        waitUntil(SCREEN_TIMEOUT_MS, "состава карточки «$name»") { textExists(name) }
        capture(STEP_REMINDER)
    }

    // --- Сид данных -------------------------------------------------------------

    private fun seedCatalog(startDate: LocalDate = DeviceTestClock.today.minusDays(20)) {
        seedMedication(
            id = MED_MAGNESIUM_ID,
            name = medicationName(TestR.string.store_medication_magnesium),
            times = listOf(MORNING),
            startDate = startDate,
            endDate = startDate.plusDays(COURSE_DAYS),
            stockLeft = BigDecimal(MAGNESIUM_STOCK_LEFT),
            packageSize = BigDecimal(MAGNESIUM_PACKAGE),
        )
        seedMedication(
            id = MED_VITAMIN_D_ID,
            name = medicationName(TestR.string.store_medication_vitamin_d),
            times = listOf(EXPECTED),
            startDate = startDate,
            stockLeft = BigDecimal(VITAMIN_STOCK_LEFT),
            packageSize = BigDecimal(VITAMIN_PACKAGE),
        )
        seedMedication(
            id = MED_OMEGA_ID,
            name = medicationName(TestR.string.store_medication_omega),
            times = listOf(EVENING),
            startDate = startDate,
            endDate = startDate.plusDays(OMEGA_COURSE_DAYS),
            stockLeft = BigDecimal(OMEGA_STOCK_LEFT),
            packageSize = BigDecimal(OMEGA_PACKAGE),
        )
    }

    private fun markTaken(medicationId: String, time: LocalTime) {
        val intake = DeviceTestSeed.dayIntakes(targetContext, ownerProfile.id, DeviceTestClock.today)
            .firstOrNull { it.medicationId == medicationId && it.scheduledAt.toLocalTime() == time }
            ?: error("No intake seeded for $medicationId at $time")
        DeviceTestSeed.markTaken(targetContext, intake.id)
    }

    private fun suppressOutageBanner() {
        runBlocking { outageAnnouncer.refresh() }
        outageAnnouncer.acknowledge()
        compose.waitForIdle()
    }

    /**
     * История за прошедшие дни октября: у каждого препарата свой рисунок пропусков,
     * чтобы сводка и разбивка «по лекарствам» были содержательными, а не одинаковыми.
     */
    private fun seedPastIntakes() {
        val rows = buildList {
            for (day in 1..HISTORY_YESTERDAY) {
                val date = LocalDate.of(2026, 10, day)
                add(pastIntake(MED_MAGNESIUM_ID, date, MORNING, statusFor(day, MAGNESIUM_MISSED, MAGNESIUM_SKIPPED)))
                add(pastIntake(MED_VITAMIN_D_ID, date, EXPECTED, statusFor(day, VITAMIN_MISSED, emptySet())))
                add(pastIntake(MED_OMEGA_ID, date, EVENING, statusFor(day, emptySet(), OMEGA_SKIPPED)))
            }
        }
        runBlocking { intakes.insertMissing(rows) }
    }

    private fun statusFor(day: Int, missed: Set<Int>, skipped: Set<Int>): IntakeStatus = when (day) {
        in missed -> IntakeStatus.MISSED
        in skipped -> IntakeStatus.SKIPPED
        else -> IntakeStatus.TAKEN
    }

    private fun pastIntake(medicationId: String, date: LocalDate, time: LocalTime, status: IntakeStatus): Intake {
        val scheduledAt = LocalDateTime.of(date, time)
        return Intake(
            id = "store-intake-$medicationId-$date",
            medicationId = medicationId,
            scheduledAt = scheduledAt,
            status = status,
            actedAt = actedAt(scheduledAt, status),
            source = sourceOf(status),
            actorProfileId = null,
        )
    }

    private fun actedAt(scheduledAt: LocalDateTime, status: IntakeStatus) = when (status) {
        IntakeStatus.PENDING -> null
        IntakeStatus.TAKEN -> scheduledAt.plusMinutes(3)
        IntakeStatus.SKIPPED -> scheduledAt.plusMinutes(10)
        IntakeStatus.MISSED -> scheduledAt.plusHours(4)
    }?.atZone(DeviceTestClock.zone)?.toInstant()

    private fun sourceOf(status: IntakeStatus) = when (status) {
        IntakeStatus.PENDING -> null
        IntakeStatus.MISSED -> IntakeSource.AUTO
        IntakeStatus.TAKEN, IntakeStatus.SKIPPED -> IntakeSource.PHONE
    }

    // --- Навигация и запись -----------------------------------------------------

    private fun openHistoryFromToday() {
        val label = targetContext.getString(TodayR.string.today_history_link)
        compose.onNode(
            hasText(label) and hasAnyAncestor(hasTestTag(TAG_DAY_PROGRESS)),
            useUnmergedTree = true,
        ).performClick()
        waitForTag(TAG_HISTORY_SCREEN)
    }

    private fun drawLayer(layer: Drawable, canvas: Canvas) {
        layer.setBounds(0, 0, ICON_PX, ICON_PX)
        layer.draw(canvas)
    }

    private fun writePng(file: File, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    private fun medicationName(resource: Int): String = testContext.getString(resource)

    private companion object {
        const val ICON_PX = 512
        const val PNG_QUALITY = 100
        const val ICON_PATH = "store-assets/icon-512.png"

        /** Аргумент прогона: язык, под которым снимается набор (`-e storeLocale es`). */
        const val STORE_LOCALE_ARG = "storeLocale"

        const val STEP_TODAY = "store-01-today"
        const val STEP_MEDS = "store-02-meds"
        const val STEP_CARD = "store-03-med-card"
        const val STEP_HISTORY = "store-04-history"
        const val STEP_FORM = "store-05-med-form"
        const val STEP_REMINDER = "store-06-reminder"

        const val MED_MAGNESIUM_ID = "store-med-magnesium"
        const val MED_VITAMIN_D_ID = "store-med-vitamin-d"
        const val MED_OMEGA_ID = "store-med-omega"

        val MORNING: LocalTime = LocalTime.of(8, 0)
        val EXPECTED: LocalTime = LocalTime.of(8, 30)
        val EVENING: LocalTime = LocalTime.of(20, 0)

        const val DOSE = "1"
        const val COURSE_DAYS = 30L
        const val OMEGA_COURSE_DAYS = 60L
        const val MAGNESIUM_STOCK_LEFT = "9"
        const val MAGNESIUM_PACKAGE = "60"
        const val VITAMIN_STOCK_LEFT = "30"
        const val VITAMIN_PACKAGE = "90"
        const val OMEGA_STOCK_LEFT = "45"
        const val OMEGA_PACKAGE = "60"

        const val HISTORY_TODAY = 27
        const val HISTORY_YESTERDAY = 26

        val MAGNESIUM_MISSED = setOf(5, 12, 19)
        val MAGNESIUM_SKIPPED = setOf(26)
        val VITAMIN_MISSED = setOf(7, 14)
        val OMEGA_SKIPPED = setOf(3)
    }
}
