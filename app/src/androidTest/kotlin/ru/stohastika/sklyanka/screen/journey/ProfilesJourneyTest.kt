package ru.stohastika.sklyanka.screen.journey

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_CONFIRM
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_DONE
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_HISTORY
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_MEDICATIONS
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_RESULT_TITLE
import ru.stohastika.sklyanka.feature.settings.profile.TAG_DELETE_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.screen.support.DeviceTestGraph
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_CONFIRM_WORD_FIELD
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_LABEL
import ru.stohastika.sklyanka.ui.component.TAG_PROFILE_STRIPE
import ru.stohastika.sklyanka.ui.token.ProfileColor
import java.math.BigDecimal
import ru.stohastika.sklyanka.feature.settings.R as SettingsR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Поход «Чужой профиль» (SKL-140, реестр походов № 8).
 *
 * Человек уходит в профиль того, за кем присматривает, — и обязан на каждом экране видеть,
 * что он не в своём: цветная полоса, надпись «Профиль · Имя» и несворачиваемый баннер
 * избыточны намеренно (ПРФ-11…ПРФ-13). Данные при этом не смешиваются: приёмы одного
 * профиля не видны в другом, и это свойство хранилища, а не экрана (ПРФ-4).
 *
 * Поход идёт до конца: человек возвращается в свой профиль, а затем удаляет чужой —
 * словом-подтверждением (§5.7) — и профиль исчезает вместе с лекарствами и историей.
 * Утверждается это на данных (репозиторий, приёмы профиля, журнал операций), а не на виде
 * экрана: экран мог закрыться, а строки в базе остаться.
 *
 * Экрана создания профиля в продукте нет (ПРФ-9, ОВ-3), поэтому чужой профиль и его
 * лекарство сеет сам поход: владельца — тестовый граф, чужого — репозиторий профилей
 * (см. [JourneyProfileGraph]).
 */
@HiltAndroidTest
class ProfilesJourneyTest : JourneyTest() {

    @Test
    fun thePersonVisitsAForeignProfileAndRemovesIt() {
        seedMedication(
            id = OWNER_MEDICATION_ID,
            name = ownerMedication,
            times = listOf(DeviceTestClock.scheduledTime),
        )
        val ward = seedWardProfile(wardName)
        seedMedicationFor(ward.id, WARD_MEDICATION_ID, wardMedication)
        planIntakes()
        DeviceTestSeed.planIntakes(targetContext, ward.id)

        launchApp()
        awaitRoot()

        // В своём профиле признаков чужого нет вовсе: их отсутствие — тоже сигнал (ПРФ-16).
        openSettings()
        assertTrue("the owner profile must show no foreign stripe", !exists(TAG_PROFILE_STRIPE))
        assertTrue("the owner profile must show no foreign label", !exists(TAG_PROFILE_LABEL))
        capture("settings-own")

        // Переключение — из настроек: строка чужого профиля названа его именем.
        switchToProfile(wardName)
        waitUntil(SCREEN_TIMEOUT_MS, "признаков чужого профиля") { foreignIndicationsShown() }
        capture("settings-ward")

        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$wardMedication» на «Сегодня»") { textExists(wardMedication) }
        assertTrue("the owner medication must stay invisible in the foreign profile", !textExists(ownerMedication))
        assertTrue("the foreign profile must be indicated on the day", foreignIndicationsShown())
        capture("today-ward")

        // Возврат в свой профиль: подсказки снимаются, чужое лекарство пропадает.
        openSettings()
        switchToProfile(ownerRow)
        waitUntil(SCREEN_TIMEOUT_MS, "снятия признаков чужого профиля") { !foreignIndicationsShown() }
        assertTrue("the foreign stripe must be gone in the owner profile", !exists(TAG_PROFILE_STRIPE))
        capture("settings-own-again")

        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$ownerMedication» на «Сегодня»") { textExists(ownerMedication) }
        assertTrue("the foreign medication must stay invisible in the owner profile", !textExists(wardMedication))

        // Удалить можно ровно того, в чьём контексте человек находится (ОВ-7): поход
        // заходит в чужой профиль снова — так делает и человек, вернувшийся к своим приёмам.
        openSettings()
        switchToProfile(wardName)
        clickRow(deleteRow)
        waitForTag(TAG_DELETE_SCREEN)
        // Числа потерь приходят потоком: сначала дожидаемся их, потом судим. Чужое
        // лекарство одно, а история непуста — это утверждение о том, что будет стёрто.
        val lostMedications = targetContext.getString(SettingsR.string.profile_delete_medications, 1, 0)
        waitUntil(SCREEN_TIMEOUT_MS, "числа лекарств на экране удаления") {
            exists(TAG_DELETE_MEDICATIONS) && nodeText(TAG_DELETE_MEDICATIONS) == lostMedications
        }
        assertEquals(
            "the delete screen must name the single medication that will be lost",
            lostMedications,
            nodeText(TAG_DELETE_MEDICATIONS),
        )
        assertTrue(
            "the delete screen must count a non-empty history",
            nodeText(TAG_DELETE_HISTORY) != targetContext.getString(SettingsR.string.profile_delete_history, 0),
        )
        capture("delete-confirm")

        // Слово набирается с подписи-образца: до него подтверждающая кнопка недоступна (§5.7).
        typeInto(TAG_CONFIRM_WORD_FIELD, targetContext.getString(SettingsR.string.profile_delete_word))
        scrollToAndClick(TAG_DELETE_CONFIRM)
        waitForTag(TAG_DELETE_RESULT_TITLE)
        capture("delete-result")
        click(TAG_DELETE_DONE)
        waitForTag(TAG_SETTINGS_SCREEN)

        // Профиль исчез вместе с лекарством и историей: последнее — утверждение о базе.
        // Сначала дожидаемся, что список профилей собран: пустой список тоже «не содержит»
        // чужого профиля, и без этой опоры утверждение было бы зелёным при любом поведении.
        waitUntil(SCREEN_TIMEOUT_MS, "списка профилей после удаления") { textExists(ownerRow) }
        assertTrue("the foreign profile must be gone from settings", !textExists(wardName))
        assertTrue("no foreign profile must remain to switch to", !textExists(deleteRow))
        assertTrue("the foreign history must be erased with the profile", wardIntakes().isEmpty())
        assertNull(
            "the foreign medication must be erased with the profile",
            runBlocking { testGraph().medications().get(WARD_MEDICATION_ID) },
        )
        assertTrue("the owner intakes must survive the deletion", dayIntakes().isNotEmpty())
        assertTrue("the switch must be in the operation log", opLogHas(OpEvent.PROFILE_SWITCHED))
        assertTrue("the deletion must be in the operation log", opLogHas(OpEvent.PROFILE_DELETED))

        // Активным стал свой профиль: на «Сегодня» снова своё лекарство, чужого нет.
        openToday()
        waitUntil(SCREEN_TIMEOUT_MS, "лекарства «$ownerMedication» после удаления") { textExists(ownerMedication) }
        assertTrue("the deleted profile must leave no medication on the day", !textExists(wardMedication))
        capture("today-after")

        // Удаление переживает выход из приложения: чужого профиля нет и после перезапуска.
        relaunchApp()
        awaitRoot()
        openSettings()
        waitUntil(SCREEN_TIMEOUT_MS, "списка профилей после перезапуска") { textExists(ownerRow) }
        assertTrue("the deleted profile must not come back after a relaunch", !textExists(wardName))
    }

    /** Лекарство профиля: тот же доменный черновик, что у формы, — инварианты те же (ИНВ-4). */
    private fun seedMedicationFor(profileId: String, id: String, name: String) {
        val draft = MedicationDraft.new(profileId = profileId, today = DeviceTestClock.today.minusDays(1)).copy(
            name = name,
            doseAmount = BigDecimal.ONE,
            times = listOf(DeviceTestClock.scheduledTime),
        )
        DeviceTestSeed.medication(targetContext, draft.toMedication(id))
    }

    /** Чужой профиль репозиторием: цвет — из закрытого набора палитры (ПРФ-15). */
    private fun seedWardProfile(name: String): Profile {
        val ward = Profile(
            id = WARD_PROFILE_ID,
            name = name,
            color = ProfileColor.Terracotta.argb,
            isSelf = false,
            mirrorReminders = true,
        )
        runBlocking { profileGraph().profiles().save(ward) }
        return ward
    }

    /** Переключение профиля строкой настроек: строки чужого профиля адресуются именем (ПРФ-26). */
    private fun switchToProfile(rowText: String) = clickRow(rowText)

    /**
     * Три признака чужого профиля, которые видит человек: полоса, надпись и баннер
     * (ПРФ-11). Шестой признак — подпись «отметили вы» — принадлежит списку приёмов.
     */
    private fun foreignIndicationsShown(): Boolean =
        exists(TAG_PROFILE_STRIPE) && exists(TAG_PROFILE_LABEL) && textExists(bannerText)

    /** Строка списка адресуется текстом: её собственный тег внутренний для `feature-settings`. */
    private fun clickRow(text: String) {
        waitUntil(SCREEN_TIMEOUT_MS, "строки «$text»") { textExists(text) }
        compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** Приёмы чужого профиля за сегодня: после удаления их нет ни одной. */
    private fun wardIntakes() = DeviceTestSeed.dayIntakes(targetContext, WARD_PROFILE_ID, DeviceTestClock.today)

    /** Продуктовые чтения похода: те же репозитории, что у экранов (TDD §6.2). */
    private fun testGraph(): DeviceTestGraph =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, DeviceTestGraph::class.java)

    private fun profileGraph(): JourneyProfileGraph =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, JourneyProfileGraph::class.java)

    private val ownerMedication: String get() = name("profiles-owner")
    private val wardMedication: String get() = name("profiles-ward-med")

    /**
     * Имя чужого профиля.
     *
     * Своей строки у стенда нет: имя собирается форматом имени лекарства
     * (`device_test_medication_name`). Заводить ресурс имени профиля значило бы добавить
     * строку сразу в шесть каталогов тестового APK ради одного имени — стенд живёт
     * нейтральными именами, и здесь оно нейтрально.
     */
    private val wardName: String get() = name("profiles-ward")

    private fun name(suffix: String): String = testContext.getString(R.string.device_test_medication_name, suffix)

    /** Строка владельца в списке профилей называется «Мои лекарства», а не его именем. */
    private val ownerRow: String get() = targetContext.getString(SettingsR.string.settings_profile_self)

    /** Строка удаления называет того самого профиля, в чьём контексте человек находится (ОВ-7). */
    private val deleteRow: String
        get() = targetContext.getString(SettingsR.string.settings_profile_delete, wardName)

    /** Несворачиваемый баннер чужого профиля — второй признак, словами (ПРФ-13). */
    private val bannerText: String get() = targetContext.getString(UiR.string.profile_banner, wardName)

    private companion object {
        const val OWNER_MEDICATION_ID = "journey-profiles-owner"
        const val WARD_PROFILE_ID = "journey-profiles-ward"
        const val WARD_MEDICATION_ID = "journey-profiles-ward-med"
    }
}

/**
 * Точка входа похода к репозиторию профилей (SKL-140).
 *
 * Создания профиля в продукте нет (ПРФ-9 не реализован), а сида чужого профиля нет в каркасе:
 * поэтому подопечного заводит сам поход — этой приватной точкой входа, поверх настоящего
 * доменного пути. Каркас и продукт не трогаются: поход не имеет права менять общий сид
 * ради одного своего шага.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface JourneyProfileGraph {

    fun profiles(): ProfileRepository
}
