package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.meds.card.TAG_ARCHIVE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_HINT
import ru.stohastika.sklyanka.feature.meds.card.TAG_DELETE_SHEET
import ru.stohastika.sklyanka.feature.meds.card.TAG_MEDICATION_CARD_SCREEN
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_CARD
import ru.stohastika.sklyanka.feature.meds.list.TAG_MEDICATION_LIST
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_FIELD
import ru.stohastika.sklyanka.feature.meds.R as MedsR

/**
 * F — мягкое удаление лекарства и возврат (SKL-99, ОВ-124, TDD §14).
 *
 * Путь человека целиком на настоящем графе: карточка → «Удалить» → шторка → снэкбар
 * на списке → «Отменить» → курс снова в активных. Вторая проверка утверждает границу:
 * после отметки «принято» действия «Удалить» на карточке нет вовсе — ни кнопки,
 * ни пояснения.
 */
@HiltAndroidTest
class MedicationDeletionDeviceTest : DeviceScreenTest() {

    private val name: String get() = testContext.getString(R.string.device_test_medication_deletion)

    @Test
    fun theDeletedMedicationComesBackWithUndo() {
        seedMedication(id = DELETED_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()
        openMedications()
        scrollTo(TAG_MEDICATION_LIST, TAG_MEDICATION_CARD)
        click(TAG_MEDICATION_CARD)

        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        // Действие удаления — нижний блок карточки, вне прокручиваемой колонки:
        // прокручивать к нему нечего, он виден всегда. Кадр берётся после того,
        // как доступное удаление утверждено: тихая Danger-кнопка и пояснение условия.
        waitForTag(TAG_DELETE)
        waitForTag(TAG_DELETE_HINT)
        capture("med-card-deletable")

        click(TAG_DELETE)
        waitForTag(TAG_DELETE_SHEET)
        // Шторка рисуется самим приложением: у диалога своего окна больше нет, а затемнение —
        // роль `scrim` поверх экрана (§2.3). Поэтому кадр берётся у окна приложения целиком —
        // иначе свидетельства затемнения не осталось бы вовсе.
        capture("delete-sheet")

        // Удаление подтверждается вводом слова (§5.7): до него подтверждающая кнопка
        // недоступна, и нажатие ничего бы не сделало.
        val word = targetContext.getString(MedsR.string.medication_delete_word)
        typeInto(TAG_CONFIRM_WORD_FIELD, word, scroll = false)
        click(TAG_DELETE_CONFIRM)
        waitForTag(TAG_MEDICATION_LIST)
        val deletedText = targetContext.getString(MedsR.string.meds_deleted_snackbar, name)
        waitUntil(SCREEN_TIMEOUT_MS, "снэкбара удаления") { textExists(deletedText) }
        capture("meds-deleted")

        val undo = targetContext.getString(MedsR.string.meds_undo_action)
        compose.onNodeWithText(undo, useUnmergedTree = true).performClick()
        waitUntil(SCREEN_TIMEOUT_MS, "возврата курса «$name»") { textExists(name) }
        capture("meds-undo-deletion")
    }

    @Test
    fun afterATakenIntakeTheDeleteActionIsGone() {
        seedMedication(id = TAKEN_ID, name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()
        val intake = requireNotNull(
            DeviceTestSeed.dayIntakes(targetContext, ownerProfile.id, DeviceTestClock.today).firstOrNull(),
        ) { "the seeded course must have an intake today" }
        DeviceTestSeed.markTaken(targetContext, intake.id)

        launchApp()
        awaitRoot()
        openMedications()
        scrollTo(TAG_MEDICATION_LIST, TAG_MEDICATION_CARD)
        click(TAG_MEDICATION_CARD)

        waitForTag(TAG_MEDICATION_CARD_SCREEN)
        waitForTag(TAG_ARCHIVE)
        waitUntil(SCREEN_TIMEOUT_MS, "отсутствия действия удаления") { !exists(TAG_DELETE) }
        waitUntil(SCREEN_TIMEOUT_MS, "отсутствия пояснения условия") { !exists(TAG_DELETE_HINT) }
        capture("med-card-after-taken")
    }

    private companion object {
        const val DELETED_ID = "dev-med-deletion"
        const val TAKEN_ID = "dev-med-deletion-taken"
    }
}
