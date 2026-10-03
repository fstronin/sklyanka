package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_CAPTION
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_CARD
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_EMPTY
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_RESTORE
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_SCREEN
import ru.stohastika.sklyanka.feature.meds.archive.TAG_GROUP_MANUAL
import ru.stohastika.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.feature.meds.R as MedsR

/**
 * E — архив и возврат курса (SKL-98, TDD §6).
 *
 * Путь человека целиком: карточка → «В архив» → вкладка «Архив» с группой и подписью →
 * «Вернуть» → пустой архив → курс снова в активных. Вкладки сегмента нажимаются
 * по тексту ресурса с ожидаемым счётчиком: счётчики считает продукт.
 */
@HiltAndroidTest
class ArchiveDeviceTest : DeviceScreenTest() {

    private val name: String get() = testContext.getString(R.string.device_test_medication_archive)

    @Test
    fun theArchivedCourseReturnsToActive() {
        seedMedication(id = MEDICATION_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()
        openMedications()
        click(TAG_MEDICATION_CARD)

        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        click(TAG_ARCHIVE)

        waitForTag(TAG_MEDICATION_LIST)
        clickSegment(archive = true)
        waitForTag(TAG_ARCHIVE_SCREEN)
        waitForTag(TAG_ARCHIVE_CARD)
        waitForTag(TAG_GROUP_MANUAL)
        waitForTag(TAG_ARCHIVE_CAPTION)
        capture("archive-list")

        click(TAG_ARCHIVE_RESTORE)
        waitForTag(TAG_ARCHIVE_EMPTY)
        capture("archive-empty")

        clickSegment(archive = false)
        waitUntil(SCREEN_TIMEOUT_MS, "курса «$name» в активных") { textExists(name) }
        capture("meds-restored")
    }

    /** После архивации одного курса счётчик архива равен единице, после возврата — активных. */
    private fun clickSegment(archive: Boolean) {
        val label = if (archive) {
            targetContext.getString(MedsR.string.meds_tab_archive, 1)
        } else {
            targetContext.getString(MedsR.string.meds_tab_active, 1)
        }
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
    }

    private companion object {
        const val MEDICATION_ID = "dev-med-archive"
    }
}
